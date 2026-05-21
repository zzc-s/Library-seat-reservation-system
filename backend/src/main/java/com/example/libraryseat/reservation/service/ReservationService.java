package com.example.libraryseat.reservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.service.AttendanceService;
import com.example.libraryseat.common.BusinessException;
import com.example.libraryseat.common.enums.ReservationStatus;
import com.example.libraryseat.group.entity.GroupReservation;
import com.example.libraryseat.group.mapper.GroupReservationMapper;
import com.example.libraryseat.reservation.dto.CreateReservationRequest;
import com.example.libraryseat.reservation.dto.GroupCreateRequest;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.reservation.schedule.ReservationScheduleService;
import com.example.libraryseat.seat.service.SeatStatusService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReservationService {

    private static final List<ReservationStatus> EFFECTIVE_STATUSES = ReservationStatus.EFFECTIVE;

    private final ReservationMapper reservationMapper;
    private final UserMapper userMapper;
    private final GroupReservationMapper groupReservationMapper;
    private final SeatStatusService seatStatusService;
    private final AttendanceService attendanceService;
    private final ReservationScheduleService reservationScheduleService;
    private final PersonalReservationOverlapService personalReservationOverlapService;
    private final ReservationGroupNotifyService groupNotifyService;

    public ReservationService(ReservationMapper reservationMapper,
                              UserMapper userMapper,
                              GroupReservationMapper groupReservationMapper,
                              SeatStatusService seatStatusService,
                              AttendanceService attendanceService,
                              ReservationScheduleService reservationScheduleService,
                              PersonalReservationOverlapService personalReservationOverlapService,
                              ReservationGroupNotifyService groupNotifyService) {
        this.reservationMapper = reservationMapper;
        this.userMapper = userMapper;
        this.groupReservationMapper = groupReservationMapper;
        this.seatStatusService = seatStatusService;
        this.attendanceService = attendanceService;
        this.reservationScheduleService = reservationScheduleService;
        this.personalReservationOverlapService = personalReservationOverlapService;
        this.groupNotifyService = groupNotifyService;
    }

    public List<Reservation> listByUser(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        reservationScheduleService.processDueNoShows(now);
        autoFinishExpired(userId, now);
        autoActivateConfirmed(userId, now);

        return reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getStartTime));
    }

    @Transactional
    public Reservation create(CreateReservationRequest req, Long userId) {
        if (req.seatId() == null || req.startTime() == null || req.endTime() == null) {
            throw new BusinessException("参数不完整");
        }

        User user = userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
            throw BusinessException.forbidden("您的账号已被加入黑名单，无法预约座位。如有疑问，请联系管理员。");
        }

        validateTimeRange(req.startTime(), req.endTime());

        if (personalReservationOverlapService.hasUserTimeOverlap(userId, req.startTime(), req.endTime(), null)) {
            throw BusinessException.conflict("该时间段您已有预约，不能同时占用多个座位");
        }

        if (hasSeatConflict(req.seatId(), req.startTime(), req.endTime())) {
            throw BusinessException.conflict("该时段座位已被预约");
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setSeatId(req.seatId());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setStatus(ReservationStatus.ACTIVE.getValue());
        reservationMapper.insert(r);

        updateSeatStatusSafely(req.seatId(), () -> seatStatusService.onReservationCreated(req.seatId()));
        return r;
    }

    @Transactional
    public Set<Long> createGroup(GroupCreateRequest req, Long userId) {
        if (req.seatIds() == null || req.seatIds().isEmpty() || req.startTime() == null || req.endTime() == null) {
            throw new BusinessException("参数不完整");
        }

        User user = userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
            throw BusinessException.forbidden("您的账号已被加入黑名单，无法预约座位。如有疑问，请联系管理员。");
        }

        validateTimeRange(req.startTime(), req.endTime());

        if (req.seatIds().stream().distinct().count() > 1) {
            throw BusinessException.conflict("同一时段每人只能预约一个座位，请勿同时预约多个座位");
        }
        if (personalReservationOverlapService.hasUserTimeOverlap(userId, req.startTime(), req.endTime(), null)) {
            throw BusinessException.conflict("该时间段您已有预约，不能重复预约");
        }

        List<String> effectiveValues = EFFECTIVE_STATUSES.stream().map(ReservationStatus::getValue).toList();
        long overlap = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getSeatId, req.seatIds())
                .in(Reservation::getStatus, effectiveValues)
                .apply("NOT (end_time <= {0} OR start_time >= {1})", req.startTime(), req.endTime()));
        if (overlap > 0) {
            throw BusinessException.conflict("存在与时段冲突的座位，组预约失败");
        }

        for (Long seatId : req.seatIds()) {
            Reservation r = new Reservation();
            r.setUserId(userId);
            r.setSeatId(seatId);
            r.setStartTime(req.startTime());
            r.setEndTime(req.endTime());
            r.setStatus(ReservationStatus.ACTIVE.getValue());
            reservationMapper.insert(r);
        }

        List<Reservation> created = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getSeatId, req.seatIds())
                .eq(Reservation::getStartTime, req.startTime())
                .eq(Reservation::getEndTime, req.endTime())
                .eq(Reservation::getStatus, ReservationStatus.ACTIVE.getValue()));
        return created.stream().map(Reservation::getId).collect(Collectors.toSet());
    }

    @Transactional
    public void cancel(Long id, Long currentUserId) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw BusinessException.notFound("预约不存在");
        }
        if (!r.getUserId().equals(currentUserId)) {
            throw BusinessException.forbidden("无权取消此预约，只能取消自己的预约");
        }
        if (!isEffectiveStatus(r.getStatus())) {
            throw new BusinessException("只能取消待审核或进行中的预约，当前状态：" + r.getStatus());
        }

        r.setStatus(ReservationStatus.CANCELLED.getValue());
        reservationMapper.updateById(r);
        log.info("用户 {} 已取消预约 {}", currentUserId, id);

        updateSeatStatusSafely(r.getSeatId(), () -> seatStatusService.onReservationCancelled(r.getSeatId()));
        groupNotifyService.notifyGroupOnCancel(r, currentUserId);
    }

    // ---- private helpers ----

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (!startTime.isAfter(now)) {
            throw new BusinessException("开始时间必须是未来时间，不能预约过去的时间段");
        }
        if (!endTime.isAfter(now)) {
            throw new BusinessException("结束时间必须是未来时间");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("结束时间需晚于开始时间");
        }
        if (Duration.between(startTime, endTime).toHours() > 4) {
            throw new BusinessException("单次预约不超过4小时");
        }
    }

    private boolean isEffectiveStatus(String status) {
        return EFFECTIVE_STATUSES.stream().anyMatch(s -> s.getValue().equals(status));
    }

    private boolean hasSeatConflict(Long seatId, LocalDateTime startTime, LocalDateTime endTime) {
        List<String> effectiveValues = EFFECTIVE_STATUSES.stream().map(ReservationStatus::getValue).toList();
        long personalCount = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getSeatId, seatId)
                .in(Reservation::getStatus, effectiveValues)
                .gt(Reservation::getEndTime, startTime)
                .lt(Reservation::getStartTime, endTime));

        if (personalCount > 0) return true;

        List<GroupReservation> groupReservations = groupReservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED"))
                        .gt(GroupReservation::getEndTime, startTime)
                        .lt(GroupReservation::getStartTime, endTime));

        for (GroupReservation gr : groupReservations) {
            if (gr.getSeatIds() != null && !gr.getSeatIds().trim().isEmpty()) {
                List<Long> seatIds = parseSeatIds(gr.getSeatIds());
                if (seatIds.contains(seatId)) return true;
            }
        }
        return false;
    }

    private void autoFinishExpired(Long userId, LocalDateTime now) {
        List<Reservation> expired = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, ReservationStatus.ACTIVE.getValue(), ReservationStatus.CONFIRMED.getValue())
                .lt(Reservation::getEndTime, now));

        for (Reservation r : expired) {
            if (!attendanceService.hasCheckedIn(r.getId())) continue;
            r.setStatus(ReservationStatus.FINISHED.getValue());
            reservationMapper.updateById(r);
            log.info("预约 {} 已过期且已签到，自动标记为 FINISHED", r.getId());
        }
    }

    private void autoActivateConfirmed(Long userId, LocalDateTime now) {
        List<Reservation> confirmedStarted = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getStatus, ReservationStatus.CONFIRMED.getValue())
                .le(Reservation::getStartTime, now)
                .ge(Reservation::getEndTime, now));

        for (Reservation r : confirmedStarted) {
            r.setStatus(ReservationStatus.ACTIVE.getValue());
            reservationMapper.updateById(r);
            log.info("预约 {} 已开始，自动转为 ACTIVE", r.getId());
        }
    }

    private void updateSeatStatusSafely(Long seatId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("更新座位状态失败，座位ID: {}", seatId, e);
        }
    }

    private List<Long> parseSeatIds(String seatIdsStr) {
        return Arrays.stream(seatIdsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}