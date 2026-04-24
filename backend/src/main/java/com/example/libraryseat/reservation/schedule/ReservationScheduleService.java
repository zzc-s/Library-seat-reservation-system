package com.example.libraryseat.reservation.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.entity.AttendanceLog;
import com.example.libraryseat.attendance.mapper.AttendanceLogMapper;
import com.example.libraryseat.attendance.service.AttendanceService;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import com.example.libraryseat.seat.service.SeatStatusService;
import com.example.libraryseat.security.EmailService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.group.entity.GroupJoinRequest;
import com.example.libraryseat.group.mapper.GroupJoinRequestMapper;
import com.example.libraryseat.violation.entity.Violation;
import com.example.libraryseat.violation.mapper.ViolationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
public class ReservationScheduleService {
    private static final String REMINDER_KEY_PREFIX = "reminder:reservation:";
    
    private final ReservationMapper reservationMapper;
    private final AttendanceLogMapper attendanceLogMapper;
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final SeatMapper seatMapper;
    private final EmailService emailService;
    private final StringRedisTemplate redis;
    private final GroupJoinRequestMapper joinRequestMapper;
    private final AttendanceService attendanceService;
    private final SeatStatusService seatStatusService;

    public ReservationScheduleService(ReservationMapper reservationMapper,
                                      AttendanceLogMapper attendanceLogMapper,
                                      ViolationMapper violationMapper,
                                      UserMapper userMapper,
                                      SeatMapper seatMapper,
                                      EmailService emailService,
                                      StringRedisTemplate redis,
                                      GroupJoinRequestMapper joinRequestMapper,
                                      AttendanceService attendanceService,
                                      SeatStatusService seatStatusService) {
        this.reservationMapper = reservationMapper;
        this.attendanceLogMapper = attendanceLogMapper;
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
        this.seatMapper = seatMapper;
        this.emailService = emailService;
        this.redis = redis;
        this.joinRequestMapper = joinRequestMapper;
        this.attendanceService = attendanceService;
        this.seatStatusService = seatStatusService;
    }

    /**
     * 统一处理：预约开始已满15分钟仍无签到 → 取消预约、释放座位、记 NO_SHOW（幂等）。
     * 供定时任务与用户拉取预约列表时调用，避免仅靠「开始时刻后窄窗口」导致漏检。
     */
    @Transactional
    public int processDueNoShows(LocalDateTime now) {
        LocalDateTime noShowDeadline = now.minusMinutes(15);
        List<Reservation> candidates = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                        .le(Reservation::getStartTime, noShowDeadline)
        );
        int releasedCount = 0;
        for (Reservation r : candidates) {
            if (attendanceService.hasCheckedIn(r.getId())) {
                continue;
            }
            if (applyNoShowForReservation(r, now)) {
                releasedCount++;
            }
        }
        if (releasedCount > 0) {
            log.info("processDueNoShows 共处理 {} 个未到预约", releasedCount);
        }
        return releasedCount;
    }

    /**
     * 对单条预约执行未到处理。若已无需处理返回 false；新记入违规返回 true。
     */
    private boolean applyNoShowForReservation(Reservation r, LocalDateTime now) {
        if (attendanceService.hasCheckedIn(r.getId())) {
            return false;
        }
        long existingViolationCount = violationMapper.selectCount(
                new LambdaQueryWrapper<Violation>()
                        .eq(Violation::getReservationId, r.getId())
                        .eq(Violation::getType, "NO_SHOW")
        );
        if (existingViolationCount > 0) {
            return false;
        }
        if (!"CANCELLED".equals(r.getStatus())) {
            r.setStatus("CANCELLED");
            reservationMapper.updateById(r);
        }
        try {
            seatStatusService.onReservationCancelled(r.getSeatId());
        } catch (Exception e) {
            log.error("更新座位状态失败，座位ID: {}", r.getSeatId(), e);
        }
        Violation violation = new Violation();
        violation.setUserId(r.getUserId());
        violation.setReservationId(r.getId());
        violation.setType("NO_SHOW");
        violation.setDescription(String.format("预约开始后15分钟内未扫码签到也未取消，座位已自动释放。预约时间：%s - %s",
                r.getStartTime(), r.getEndTime()));
        violation.setOccurredAt(r.getStartTime());
        violation.setHandled(false);
        violation.setCreatedAt(now);
        violationMapper.insert(violation);
        log.info("预约 {} 因未签到已自动释放，并记录违规", r.getId());
        return true;
    }

    /**
     * 定时任务：检查预约开始已超过15分钟仍未签到的预约，自动释放并记录违规。每1分钟执行一次。
     */
    @Scheduled(fixedRate = 60000) // 1分钟 = 60000毫秒
    @Transactional
    public void checkAndReleaseUncheckedReservations() {
        LocalDateTime now = LocalDateTime.now();
        log.info("定时检查未到预约（开始时间 <= {}）", now.minusMinutes(15));
        processDueNoShows(now);
    }
    
    /**
     * 检查未签到的预约（可手动调用）
     * 检查开始时间在指定分钟数前的预约，如果未签到则释放并记录违规
     * @param minutesBack 检查过去多少分钟的预约（建议15-30分钟）
     * @return 检查到的违规数量
     */
    @Transactional
    public int checkUncheckedReservations(int minutesBack) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime noShowDeadline = now.minusMinutes(15);
        LocalDateTime earliestStart = now.minusMinutes(Math.max(minutesBack, 15));

        log.info("手动检查未到预约：开始时间在 [{}, {}] 内、已满15分钟窗口、未签到", earliestStart, noShowDeadline);

        List<Reservation> uncheckedReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                        .le(Reservation::getStartTime, noShowDeadline)
                        .ge(Reservation::getStartTime, earliestStart)
        );

        int releasedCount = 0;
        for (Reservation r : uncheckedReservations) {
            if (applyNoShowForReservation(r, now)) {
                releasedCount++;
            }
        }
        if (releasedCount > 0) {
            log.info("手动检查共新增长 {} 条未到违规", releasedCount);
        }
        return releasedCount;
    }
    
    /**
     * 定时任务：提前15分钟提醒即将开始的预约
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void sendUpcomingReservationReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderWindowStart = now.plusMinutes(10); // 10分钟后开始
        LocalDateTime reminderWindowEnd = now.plusMinutes(20);   // 20分钟后结束
        
        log.info("检查即将开始的预约提醒，窗口：{} - {}", reminderWindowStart, reminderWindowEnd);
        
        // 查询即将在10-20分钟内开始的预约
        List<Reservation> upcomingReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                        .ge(Reservation::getStartTime, reminderWindowStart)
                        .le(Reservation::getStartTime, reminderWindowEnd)
        );
        
        for (Reservation r : upcomingReservations) {
            String reminderKey = REMINDER_KEY_PREFIX + "upcoming:" + r.getId();
            // 检查是否已发送过提醒
            if (Boolean.TRUE.equals(redis.hasKey(reminderKey))) {
                continue;
            }
            
            User user = userMapper.selectById(r.getUserId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("用户 {} 没有邮箱，跳过发送提醒", r.getUserId());
                continue;
            }
            
            Seat seat = seatMapper.selectById(r.getSeatId());
            String seatLabel = seat != null ? seat.getLabel() : "座位 #" + r.getSeatId();
            
            long minutesUntilStart = Duration.between(now, r.getStartTime()).toMinutes();
            String subject = "预约提醒：您的座位预约即将开始";
            String content = String.format(
                    "您好 %s，\n\n" +
                    "您的座位预约即将在 %d 分钟后开始：\n\n" +
                    "预约信息：\n" +
                    "- 座位：%s\n" +
                    "- 开始时间：%s\n" +
                    "- 结束时间：%s\n\n" +
                    "请到达座位后，使用手机扫描座位上的二维码进行签到。\n" +
                    "如果您不来，请在预约开始前或开始后15分钟内取消预约，避免占用座位资源。\n\n" +
                    "图书馆座位预约系统",
                    user.getUsername(),
                    minutesUntilStart,
                    seatLabel,
                    r.getStartTime(),
                    r.getEndTime()
            );
            
            if (emailService.sendReminder(user.getEmail(), subject, content)) {
                // 记录已发送提醒，有效期到预约开始后1小时
                long ttlSeconds = Duration.between(now, r.getStartTime().plusHours(1)).getSeconds();
                if (ttlSeconds > 0) {
                    redis.opsForValue().set(reminderKey, "1", Duration.ofSeconds(ttlSeconds));
                }
                log.info("已发送预约提醒邮件，预约ID: {}, 用户: {}", r.getId(), user.getUsername());
            }
        }
    }
    
    /**
     * 定时任务：提醒即将结束的预约需要签退
     * 预约结束前10-20分钟提醒用户签退
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void sendCheckoutReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderWindowStart = now.plusMinutes(10); // 10分钟后结束
        LocalDateTime reminderWindowEnd = now.plusMinutes(20);   // 20分钟后结束
        
        log.info("检查即将结束的预约提醒，窗口：{} - {}", reminderWindowStart, reminderWindowEnd);
        
        // 查询即将在10-20分钟内结束的预约
        List<Reservation> endingReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                        .ge(Reservation::getEndTime, reminderWindowStart)
                        .le(Reservation::getEndTime, reminderWindowEnd)
        );
        
        for (Reservation r : endingReservations) {
            // 只提醒已签到的预约
            if (!attendanceService.hasCheckedIn(r.getId())) {
                continue;
            }
            
            // 如果已签退，跳过
            if (attendanceService.hasCheckedOut(r.getId())) {
                continue;
            }
            
            String reminderKey = REMINDER_KEY_PREFIX + "checkout:" + r.getId();
            // 检查是否已发送过提醒
            if (Boolean.TRUE.equals(redis.hasKey(reminderKey))) {
                continue;
            }
            
            User user = userMapper.selectById(r.getUserId());
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                log.warn("用户 {} 没有邮箱，跳过发送签退提醒", r.getUserId());
                continue;
            }
            
            Seat seat = seatMapper.selectById(r.getSeatId());
            String seatLabel = seat != null ? seat.getLabel() : "座位 #" + r.getSeatId();
            
            long minutesUntilEnd = Duration.between(now, r.getEndTime()).toMinutes();
            String subject = "签退提醒：您的座位预约即将结束";
            String content = String.format(
                    "您好 %s，\n\n" +
                    "您的座位预约即将在 %d 分钟后结束：\n\n" +
                    "预约信息：\n" +
                    "- 座位：%s\n" +
                    "- 开始时间：%s\n" +
                    "- 结束时间：%s\n\n" +
                    "系统将在预约结束后自动为您签退，无需手动操作。\n\n" +
                    "图书馆座位预约系统",
                    user.getUsername(),
                    minutesUntilEnd,
                    seatLabel,
                    r.getStartTime(),
                    r.getEndTime()
            );
            
            if (emailService.sendReminder(user.getEmail(), subject, content)) {
                // 记录已发送提醒，有效期到预约结束后1小时
                long ttlSeconds = Duration.between(now, r.getEndTime().plusHours(1)).getSeconds();
                if (ttlSeconds > 0) {
                    redis.opsForValue().set(reminderKey, "1", Duration.ofSeconds(ttlSeconds));
                }
                log.info("已发送签退提醒邮件，预约ID: {}, 用户: {}", r.getId(), user.getUsername());
            }
        }
    }
    
    /**
     * 定时任务：自动处理已结束但未签退的预约
     * 如果用户已签到但未签退，自动签退
     * 每1分钟执行一次，确保预约结束时立即处理
     */
    @Scheduled(fixedRate = 60000) // 1分钟 = 60000毫秒
    @Transactional
    public void autoCheckoutExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        
        log.info("开始检查已结束的预约（当前时间：{}）", now);
        
        // 查询所有已结束但状态仍为ACTIVE或CONFIRMED的预约（不限制时间窗口，立即处理）
        List<Reservation> expiredReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                        .le(Reservation::getEndTime, now) // 所有已结束的预约
        );
        
        log.info("找到 {} 个已结束的预约待检查", expiredReservations.size());
        
        int processedCount = 0;
        for (Reservation r : expiredReservations) {
            // 检查是否已签到
            if (!attendanceService.hasCheckedIn(r.getId())) {
                // 未签到，跳过（由其他定时任务处理）
                continue;
            }
            
            // 检查是否已签退
            boolean hasCheckedOut = attendanceService.hasCheckedOut(r.getId());
            
            if (hasCheckedOut) {
                // 已签退，更新预约状态为FINISHED（如果还不是）
                if (!"FINISHED".equals(r.getStatus())) {
                    r.setStatus("FINISHED");
                    reservationMapper.updateById(r);
                }
                
                // 更新座位状态
                try {
                    seatStatusService.onCheckOut(r.getSeatId());
                } catch (Exception e) {
                    log.error("更新座位状态失败，座位ID: {}", r.getSeatId(), e);
                }
                continue;
            }
            
            // 已签到但未签退，自动处理
            log.info("预约 {} 已结束但未签退，开始自动处理", r.getId());
            
            // 创建签退记录（使用预约结束时间作为签退时间）
            LocalDateTime checkoutTime = r.getEndTime(); // 使用预约结束时间作为签退时间
            AttendanceLog checkoutLog = new AttendanceLog();
            checkoutLog.setUserId(r.getUserId());
            checkoutLog.setReservationId(r.getId());
            checkoutLog.setSeatId(r.getSeatId());
            checkoutLog.setAction("CHECK_OUT");
            checkoutLog.setOccurredAt(checkoutTime);
            checkoutLog.setNote("系统自动签退（预约结束后未手动签退）");
            attendanceLogMapper.insert(checkoutLog);
            
            // 更新预约状态为FINISHED，并更新签退时间
            r.setStatus("FINISHED");
            r.setCheckOutTime(checkoutTime);
            reservationMapper.updateById(r);
            log.info("自动签退成功，预约ID: {}, 签退时间: {}", r.getId(), checkoutTime);
            
            // 更新座位状态
            try {
                seatStatusService.onCheckOut(r.getSeatId());
            } catch (Exception e) {
                log.error("更新座位状态失败，座位ID: {}", r.getSeatId(), e);
            }
            
            // 不再记录未签退违规，未签退不算违规
            log.info("预约 {} 自动签退处理完成，未记录违规（未签退不算违规）", r.getId());
            
            processedCount++;
        }
        
        if (processedCount > 0) {
            log.info("本次检查共处理 {} 个已结束但未签退的预约", processedCount);
        }
        
        // 批量更新所有座位状态（确保状态同步）
        try {
            seatStatusService.updateAllSeatsStatus();
        } catch (Exception e) {
            log.error("批量更新座位状态失败", e);
        }
    }
    
    /**
     * 定时任务：检查过期的加入小组申请
     * 如果申请当天没有通过，第二天00:00后自动标记为EXPIRED
     * 每小时执行一次（在整点执行）
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行一次
    @Transactional
    public void checkExpiredJoinRequests() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime todayStart = LocalDateTime.of(today, LocalTime.MIN); // 今天的00:00:00
        LocalDateTime yesterdayEnd = todayStart.minusSeconds(1); // 昨天23:59:59
        
        log.info("开始检查过期的加入小组申请，检查时间点：{}", now);
        
        // 查找昨天及之前创建的、状态为PENDING的申请
        List<GroupJoinRequest> expiredRequests = joinRequestMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getStatus, "PENDING")
                        .le(GroupJoinRequest::getCreatedAt, yesterdayEnd)
        );
        
        log.info("找到 {} 个过期的申请", expiredRequests.size());
        
        for (GroupJoinRequest request : expiredRequests) {
            request.setStatus("EXPIRED");
            request.setUpdatedAt(now);
            joinRequestMapper.updateById(request);
            log.info("申请 {} (用户 {} 申请加入小组 {}) 已过期，自动标记为EXPIRED", 
                    request.getId(), request.getUserId(), request.getGroupId());
        }
    }
}
