package com.example.libraryseat.reservation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.group.entity.GroupMember;
import com.example.libraryseat.group.entity.GroupNotification;
import com.example.libraryseat.group.entity.GroupReservation;
import com.example.libraryseat.group.entity.StudyGroup;
import com.example.libraryseat.group.mapper.GroupMemberMapper;
import com.example.libraryseat.group.mapper.GroupNotificationMapper;
import com.example.libraryseat.group.mapper.GroupReservationMapper;
import com.example.libraryseat.group.mapper.StudyGroupMapper;
import com.example.libraryseat.attendance.service.AttendanceService;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.reservation.service.PersonalReservationOverlapService;
import com.example.libraryseat.reservation.schedule.ReservationScheduleService;
import com.example.libraryseat.seat.service.SeatStatusService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.websocket.SeatStatusWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "预约管理接口", description = "个人预约、协同预约创建与取消")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationMapper reservationMapper;
    private final UserMapper userMapper;
    private final GroupReservationMapper groupReservationMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupNotificationMapper groupNotificationMapper;
    private final StudyGroupMapper studyGroupMapper;
    private final SeatStatusService seatStatusService;
    private final AttendanceService attendanceService;
    private final ReservationScheduleService reservationScheduleService;
    private final PersonalReservationOverlapService personalReservationOverlapService;
    private final SeatStatusWebSocketHandler seatStatusWebSocketHandler;

    public ReservationController(ReservationMapper reservationMapper, UserMapper userMapper,
                                 GroupReservationMapper groupReservationMapper,
                                 GroupMemberMapper groupMemberMapper,
                                 GroupNotificationMapper groupNotificationMapper,
                                 StudyGroupMapper studyGroupMapper,
                                 SeatStatusService seatStatusService,
                                 AttendanceService attendanceService,
                                 ReservationScheduleService reservationScheduleService,
                                 PersonalReservationOverlapService personalReservationOverlapService,
                                 SeatStatusWebSocketHandler seatStatusWebSocketHandler) {
        this.reservationMapper = reservationMapper;
        this.userMapper = userMapper;
        this.groupReservationMapper = groupReservationMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupNotificationMapper = groupNotificationMapper;
        this.studyGroupMapper = studyGroupMapper;
        this.seatStatusService = seatStatusService;
        this.attendanceService = attendanceService;
        this.reservationScheduleService = reservationScheduleService;
        this.personalReservationOverlapService = personalReservationOverlapService;
        this.seatStatusWebSocketHandler = seatStatusWebSocketHandler;
    }

    @Operation(summary = "获取当前用户的预约列表（自动刷新过期与进行中状态）")
    @GetMapping
    public List<Reservation> myReservations() {
        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();

        // 先全局处理「开始已超过15分钟仍未签到」的预约，避免仅依赖定时任务窄窗口漏记未到
        reservationScheduleService.processDueNoShows(now);
        
        // 仅当用户已签到时，才把已过期预约标为 FINISHED。未签到条目由 processDueNoShows 记违规并 CANCELLED，
        // 若此处误标 FINISHED，会导致定时任务永远不再处理，用户看不到未到违规
        List<Reservation> expiredReservations = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                .lt(Reservation::getEndTime, now));
        
        for (Reservation r : expiredReservations) {
            if (!attendanceService.hasCheckedIn(r.getId())) {
                continue;
            }
            r.setStatus("FINISHED");
            reservationMapper.updateById(r);
            log.info("预约 {} 已过期且已签到，自动标记为 FINISHED", r.getId());
        }
        
        // 自动将已开始的 CONFIRMED 预约转为 ACTIVE
        List<Reservation> confirmedStarted = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getStatus, "CONFIRMED")
                .le(Reservation::getStartTime, now)
                .ge(Reservation::getEndTime, now));
        
        for (Reservation r : confirmedStarted) {
            r.setStatus("ACTIVE");
            reservationMapper.updateById(r);
            log.info("预约 {} 已开始，自动转为 ACTIVE", r.getId());
        }
        
        // 注意：未签到的预约释放由定时任务 ReservationScheduleService 处理
        // 这里只处理用户自己的预约列表查询
        
        return reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getStartTime));
    }

    public record CreateReq(Long seatId, LocalDateTime startTime, LocalDateTime endTime) {}

    @Operation(summary = "创建个人预约")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateReq req) {
        log.info("收到预约请求: seatId={}, startTime={}, endTime={}", req.seatId(), req.startTime(), req.endTime());
        
        if (req.seatId() == null || req.startTime() == null || req.endTime() == null) {
            log.warn("预约请求参数不完整: seatId={}, startTime={}, endTime={}", req.seatId(), req.startTime(), req.endTime());
            return ResponseEntity.badRequest().body(Map.of("message", "参数不完整"));
        }
        
        // 检查用户是否在黑名单中
        Long userId = currentUserId();
        User user = userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
            log.warn("用户 {} 在黑名单中，拒绝预约请求", userId);
            return ResponseEntity.status(403).body(Map.of("message", "您的账号已被加入黑名单，无法预约座位。如有疑问，请联系管理员。"));
        }
        
        LocalDateTime now = LocalDateTime.now();
        log.debug("当前时间: {}, 请求开始时间: {}, 请求结束时间: {}", now, req.startTime(), req.endTime());
        
        // 验证开始时间必须是未来时间
        if (!req.startTime().isAfter(now)) {
            log.warn("开始时间不是未来时间: 当前时间={}, 请求开始时间={}", now, req.startTime());
            return ResponseEntity.badRequest().body(Map.of("message", "开始时间必须是未来时间，不能预约过去的时间段"));
        }
        // 验证结束时间必须是未来时间
        if (!req.endTime().isAfter(now)) {
            log.warn("结束时间不是未来时间: 当前时间={}, 请求结束时间={}", now, req.endTime());
            return ResponseEntity.badRequest().body(Map.of("message", "结束时间必须是未来时间"));
        }
        
        if (!req.endTime().isAfter(req.startTime())) {
            return ResponseEntity.badRequest().body(Map.of("message", "结束时间需晚于开始时间"));
        }
        if (Duration.between(req.startTime(), req.endTime()).toHours() > 4) {
            return ResponseEntity.badRequest().body(Map.of("message", "单次预约不超过4小时"));
        }
        if (personalReservationOverlapService.hasUserTimeOverlap(userId, req.startTime(), req.endTime(), null)) {
            return ResponseEntity.status(409).body(Map.of("message", "该时间段您已有预约，不能同时占用多个座位"));
        }
        // 检查个人预约冲突（检查所有有效状态的预约，包括该用户自己的）
        long personalCount = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getSeatId, req.seatId())
                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                .gt(Reservation::getEndTime, req.startTime())  // 预约结束时间 > 新预约开始时间
                .lt(Reservation::getStartTime, req.endTime())); // 预约开始时间 < 新预约结束时间
        
        // 检查协同预约冲突（只检查有效状态的协同预约：PENDING 和 CONFIRMED）
        // 排除 CANCELLED、EXPIRED、COMPLETED 状态的协同预约
        List<GroupReservation> groupReservations = groupReservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED"))
                        .gt(GroupReservation::getEndTime, req.startTime())  // 预约结束时间 > 新预约开始时间
                        .lt(GroupReservation::getStartTime, req.endTime())); // 预约开始时间 < 新预约结束时间
        
        log.debug("检查协同预约冲突：找到 {} 个有效状态的协同预约（PENDING 或 CONFIRMED）", groupReservations.size());
        
        // 检查该座位是否在协同预约的座位列表中
        boolean hasGroupConflict = false;
        GroupReservation conflictingReservation = null;
        for (GroupReservation gr : groupReservations) {
            if (gr.getSeatIds() != null && !gr.getSeatIds().trim().isEmpty()) {
                List<Long> seatIds = Arrays.stream(gr.getSeatIds().split(","))
                        .map(String::trim)
                        .map(Long::parseLong)
                        .collect(Collectors.toList());
                if (seatIds.contains(req.seatId())) {
                    hasGroupConflict = true;
                    conflictingReservation = gr;
                    log.warn("座位 {} 在时间段 {} - {} 已被协同预约 {} 占用（状态: {}, 小组ID: {}, 座位列表: {}）", 
                            req.seatId(), req.startTime(), req.endTime(), gr.getId(), gr.getStatus(), gr.getGroupId(), gr.getSeatIds());
                    break;
                }
            }
        }
        
        // 如果发现冲突，记录详细信息以便调试
        if (hasGroupConflict && conflictingReservation != null) {
            log.warn("冲突详情：协同预约 ID={}, 状态={}, 小组ID={}, 座位列表={}, 时间={} - {}", 
                    conflictingReservation.getId(), 
                    conflictingReservation.getStatus(),
                    conflictingReservation.getGroupId(),
                    conflictingReservation.getSeatIds(),
                    conflictingReservation.getStartTime(),
                    conflictingReservation.getEndTime());
        }
        
        if (personalCount > 0 || hasGroupConflict) {
            return ResponseEntity.status(409).body(Map.of("message", "该时段座位已被预约"));
        }
        Reservation r = new Reservation();
        r.setUserId(currentUserId());
        r.setSeatId(req.seatId());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        // 保持向后兼容：默认创建为 ACTIVE，如需审核可改为 PENDING
        r.setStatus("ACTIVE");
        reservationMapper.insert(r);
        
        // 更新座位状态
        try {
            seatStatusService.onReservationCreated(req.seatId());
        } catch (Exception e) {
            log.error("更新座位状态失败，座位ID: {}", req.seatId(), e);
            // 座位状态更新失败不影响预约创建
        }
        
        return ResponseEntity.ok(r);
    }

    public record GroupCreateReq(List<Long> seatIds, LocalDateTime startTime, LocalDateTime endTime) {}

    @Operation(summary = "为多个座位批量创建个人预约（简易协同）")
    @PostMapping("/group")
    @Transactional
    public ResponseEntity<?> createGroup(@RequestBody GroupCreateReq req) {
        if (req.seatIds() == null || req.seatIds().isEmpty() || req.startTime() == null || req.endTime() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "参数不完整"));
        }
        
        // 检查用户是否在黑名单中
        Long userId = currentUserId();
        User user = userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
            log.warn("用户 {} 在黑名单中，拒绝协同预约请求", userId);
            return ResponseEntity.status(403).body(Map.of("message", "您的账号已被加入黑名单，无法预约座位。如有疑问，请联系管理员。"));
        }
        
        LocalDateTime now = LocalDateTime.now();
        // 验证开始时间必须是未来时间
        if (!req.startTime().isAfter(now)) {
            return ResponseEntity.badRequest().body(Map.of("message", "开始时间必须是未来时间，不能预约过去的时间段"));
        }
        // 验证结束时间必须是未来时间
        if (!req.endTime().isAfter(now)) {
            return ResponseEntity.badRequest().body(Map.of("message", "结束时间必须是未来时间"));
        }
        
        if (!req.endTime().isAfter(req.startTime())) {
            return ResponseEntity.badRequest().body(Map.of("message", "结束时间需晚于开始时间"));
        }
        if (Duration.between(req.startTime(), req.endTime()).toHours() > 4) {
            return ResponseEntity.badRequest().body(Map.of("message", "单次预约不超过4小时"));
        }
        if (req.seatIds().stream().distinct().count() > 1) {
            return ResponseEntity.status(409).body(Map.of("message", "同一时段每人只能预约一个座位，请勿同时预约多个座位"));
        }
        if (personalReservationOverlapService.hasUserTimeOverlap(userId, req.startTime(), req.endTime(), null)) {
            return ResponseEntity.status(409).body(Map.of("message", "该时间段您已有预约，不能重复预约"));
        }
        // 并发校验：任一 seatId 存在交叠预约则整体失败（检查 ACTIVE/CONFIRMED/PENDING）
        long overlap = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getSeatId, req.seatIds())
                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                .apply("NOT (end_time <= {0} OR start_time >= {1})", req.startTime(), req.endTime()));
        if (overlap > 0) {
            return ResponseEntity.status(409).body(Map.of("message", "存在与时段冲突的座位，组预约失败"));
        }
        Long uid = currentUserId();
        for (Long seatId : req.seatIds()) {
            Reservation r = new Reservation();
            r.setUserId(uid);
            r.setSeatId(seatId);
            r.setStartTime(req.startTime());
            r.setEndTime(req.endTime());
            r.setStatus("ACTIVE");
            reservationMapper.insert(r);
        }
        // 返回已创建预约ID列表
        List<Reservation> created = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, uid)
                .in(Reservation::getSeatId, req.seatIds())
                .eq(Reservation::getStartTime, req.startTime())
                .eq(Reservation::getEndTime, req.endTime())
                .eq(Reservation::getStatus, "ACTIVE"));
        Set<Long> ids = created.stream().map(Reservation::getId).collect(Collectors.toSet());
        return ResponseEntity.ok(Map.of("reservationIds", ids));
    }

    @Operation(summary = "取消个人预约")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable("id") Long id) {
        try {
            Long currentUserId = currentUserId();
            log.info("用户 {} 尝试取消预约 {}", currentUserId, id);
            
            Reservation r = reservationMapper.selectById(id);
            if (r == null) {
                log.warn("预约 {} 不存在", id);
                return ResponseEntity.status(404).body(Map.of("message", "预约不存在"));
            }
            
            if (!r.getUserId().equals(currentUserId)) {
                log.warn("用户 {} 尝试取消其他用户的预约 {} (预约属于用户 {})", currentUserId, id, r.getUserId());
                return ResponseEntity.status(403).body(Map.of("message", "无权取消此预约，只能取消自己的预约"));
            }
            
            // 允许取消 ACTIVE、CONFIRMED、PENDING 状态的预约
            if (!List.of("ACTIVE", "CONFIRMED", "PENDING").contains(r.getStatus())) {
                log.warn("预约 {} 状态为 {}，无法取消", id, r.getStatus());
                return ResponseEntity.badRequest().body(Map.of("message", "只能取消待审核或进行中的预约，当前状态：" + r.getStatus()));
            }
            
            // 允许取消已开始的预约（组长和组员都可以取消自己的预约）
            r.setStatus("CANCELLED");
            reservationMapper.updateById(r);
            log.info("用户 {} 已成功取消预约 {} (状态: {}, 时间: {} - {})", currentUserId, id, r.getStatus(), r.getStartTime(), r.getEndTime());
            
            // 更新座位状态
            try {
                seatStatusService.onReservationCancelled(r.getSeatId());
            } catch (Exception e) {
                log.error("更新座位状态失败，座位ID: {}", r.getSeatId(), e);
                // 座位状态更新失败不影响取消预约操作
            }
            
            // 检查这个个人预约是否属于自习小组的预约，如果是，给组长发通知
            try {
                // 查找匹配的协同预约（匹配时间、座位）
                List<GroupReservation> matchingGroupReservations = groupReservationMapper.selectList(
                        new LambdaQueryWrapper<GroupReservation>()
                                .eq(GroupReservation::getStartTime, r.getStartTime())
                                .eq(GroupReservation::getEndTime, r.getEndTime())
                                .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED", "CANCELLED")));
                
                for (GroupReservation gr : matchingGroupReservations) {
                    // 检查座位ID是否在协同预约的座位列表中
                    if (gr.getSeatIds() != null && !gr.getSeatIds().trim().isEmpty()) {
                        List<Long> seatIds = Arrays.stream(gr.getSeatIds().split(","))
                                .map(String::trim)
                                .map(Long::parseLong)
                                .collect(Collectors.toList());
                        
                        if (seatIds.contains(r.getSeatId())) {
                            // 找到匹配的协同预约，获取小组信息
                            StudyGroup group = studyGroupMapper.selectById(gr.getGroupId());
                            if (group != null) {
                                // 检查当前用户是否是组员
                                GroupMember member = groupMemberMapper.selectOne(
                                        new LambdaQueryWrapper<GroupMember>()
                                                .eq(GroupMember::getGroupId, gr.getGroupId())
                                                .eq(GroupMember::getUserId, currentUserId));
                                
                                if (member != null) {
                                    // 当前用户是组员，给组长发通知
                                    User currentUser = userMapper.selectById(currentUserId);
                                    String username = currentUser != null ? currentUser.getUsername() : "用户ID:" + currentUserId;
                                    String content = String.format("组员 %s 取消了自习小组 %s 的预约（座位ID: %d, 时间: %s - %s）",
                                            username, group.getName(), r.getSeatId(), r.getStartTime(), r.getEndTime());
                                    
                                    GroupNotification notification = new GroupNotification();
                                    notification.setUserId(group.getLeaderId());
                                    notification.setGroupId(gr.getGroupId());
                                    notification.setType("RESERVATION_CANCELLED");
                                    notification.setContent(content);
                                    notification.setIsRead(false);
                                    notification.setCreatedAt(LocalDateTime.now());
                                    groupNotificationMapper.insert(notification);
                                    
                                    log.info("✅ 已给组长 {} 发送取消预约通知（组员 {} 取消了预约 {}）", 
                                            group.getLeaderId(), username, r.getId());
                                } else if (group.getLeaderId().equals(currentUserId)) {
                                    // 当前用户是组长，给所有组员发通知
                                    List<GroupMember> allMembers = groupMemberMapper.selectList(
                                            new LambdaQueryWrapper<GroupMember>()
                                                    .eq(GroupMember::getGroupId, gr.getGroupId()));
                                    
                                    User leader = userMapper.selectById(currentUserId);
                                    String leaderName = leader != null ? leader.getUsername() : "组长";
                                    
                                    for (GroupMember m : allMembers) {
                                        if (!m.getUserId().equals(currentUserId)) { // 不给组长自己发通知
                                            String content = String.format("组长 %s 取消了自习小组 %s 的预约（座位ID: %d, 时间: %s - %s）",
                                                    leaderName, group.getName(), r.getSeatId(), r.getStartTime(), r.getEndTime());
                                            
                                            GroupNotification notification = new GroupNotification();
                                            notification.setUserId(m.getUserId());
                                            notification.setGroupId(gr.getGroupId());
                                            notification.setType("RESERVATION_CANCELLED");
                                            notification.setContent(content);
                                            notification.setIsRead(false);
                                            notification.setCreatedAt(LocalDateTime.now());
                                            groupNotificationMapper.insert(notification);
                                        }
                                    }
                                    
                                    log.info("✅ 已给所有组员发送取消预约通知（组长 {} 取消了预约 {}）", leaderName, r.getId());
                                }
                            }
                            break; // 找到匹配的协同预约后退出循环
                        }
                    }
                }
                // 方案A：若该协同预约关联的个人预约已全部取消/结束，则自动取消协同预约主记录
                for (GroupReservation gr : matchingGroupReservations) {
                    autoCancelGroupReservationIfAllPersonalInactive(gr);
                }
            } catch (Exception e) {
                log.error("发送取消预约通知失败", e);
                // 通知发送失败不影响取消预约操作
            }
            
            return ResponseEntity.ok(Map.of("message", "已取消"));
        } catch (IllegalStateException e) {
            log.error("获取用户ID失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "获取用户信息失败，请重新登录"));
        } catch (Exception e) {
            log.error("取消预约失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "取消预约失败：" + e.getMessage()));
        }
    }

    private Long currentUserId() {
        // 首先尝试从 request attribute 获取（由 JwtAuthFilter 设置）
        try {
            Object uid = ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                    .getRequest().getAttribute("uid");
            if (uid instanceof Number) {
                return ((Number) uid).longValue();
            }
        } catch (Exception e) {
            log.debug("无法从 request attribute 获取 uid: {}", e.getMessage());
        }
        
        // 如果 request attribute 中没有，从数据库查询
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String username = auth.getName();
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null) {
                log.debug("从数据库查询到用户ID: {} (用户名: {})", user.getId(), username);
                return user.getId();
            }
        }
        
        throw new IllegalStateException("无法获取用户ID，用户: " + (auth != null ? auth.getName() : "unknown"));
    }

    /**
     * 若协同预约对应的个人预约已全部不在有效状态，则自动取消协同预约主记录。
     */
    private void autoCancelGroupReservationIfAllPersonalInactive(GroupReservation gr) {
        if (gr == null) return;
        if (!List.of("PENDING", "CONFIRMED").contains(gr.getStatus())) return;
        if (gr.getSeatIds() == null || gr.getSeatIds().trim().isEmpty()) return;

        List<Long> seatIds = Arrays.stream(gr.getSeatIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (seatIds.isEmpty()) return;

        long activePersonalCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getSeatId, seatIds)
                        .eq(Reservation::getStartTime, gr.getStartTime())
                        .eq(Reservation::getEndTime, gr.getEndTime())
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
        );
        if (activePersonalCount > 0) return;

        gr.setStatus("CANCELLED");
        groupReservationMapper.updateById(gr);
        log.info("协同预约 {} 无有效个人预约，已自动置为 CANCELLED（groupId={}）", gr.getId(), gr.getGroupId());
        try {
            seatStatusWebSocketHandler.broadcastGroupChanged("group_reservation_auto_cancelled", gr.getGroupId());
        } catch (Exception e) {
            log.warn("广播自动取消协同预约事件失败: groupId={}, reservationId={}, err={}", gr.getGroupId(), gr.getId(), e.getMessage());
        }
    }
}


