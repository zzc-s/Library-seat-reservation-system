package com.example.libraryseat.group.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.libraryseat.group.entity.GroupJoinRequest;
import com.example.libraryseat.group.entity.GroupMember;
import com.example.libraryseat.group.entity.GroupNotification;
import com.example.libraryseat.group.entity.GroupReservation;
import com.example.libraryseat.group.entity.StudyGroup;
import com.example.libraryseat.group.mapper.GroupJoinRequestMapper;
import com.example.libraryseat.group.mapper.GroupMemberMapper;
import com.example.libraryseat.group.mapper.GroupNotificationMapper;
import com.example.libraryseat.group.mapper.GroupReservationMapper;
import com.example.libraryseat.group.mapper.StudyGroupMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.common.util.SecurityUtil;
import com.example.libraryseat.common.BusinessException;
import com.example.libraryseat.reservation.service.PersonalReservationOverlapService;
import com.example.libraryseat.seat.service.SeatStatusService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.websocket.SeatStatusWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StudyGroupService {
    private final StudyGroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final GroupReservationMapper reservationMapper;
    private final GroupJoinRequestMapper joinRequestMapper;
    private final GroupNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final ReservationMapper personalReservationMapper;
    private final SeatStatusService seatStatusService;
    private final PersonalReservationOverlapService personalReservationOverlapService;
    private final SeatStatusWebSocketHandler seatStatusWebSocketHandler;
    private final SecurityUtil securityUtil;

    public StudyGroupService(StudyGroupMapper groupMapper, GroupMemberMapper memberMapper,
                                GroupReservationMapper reservationMapper, GroupJoinRequestMapper joinRequestMapper,
                                GroupNotificationMapper notificationMapper, UserMapper userMapper,
                                ReservationMapper personalReservationMapper, SeatStatusService seatStatusService,
                                PersonalReservationOverlapService personalReservationOverlapService,
                                SeatStatusWebSocketHandler seatStatusWebSocketHandler,
                                SecurityUtil securityUtil) {
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.reservationMapper = reservationMapper;
        this.joinRequestMapper = joinRequestMapper;
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.personalReservationMapper = personalReservationMapper;
        this.seatStatusService = seatStatusService;
        this.personalReservationOverlapService = personalReservationOverlapService;
        this.seatStatusWebSocketHandler = seatStatusWebSocketHandler;
        this.securityUtil = securityUtil;
    }

    @Transactional
    public Map<String, Object> createGroup(Map<String, Object> req) {
        try {
            String name = (String) req.get("name");
            if (name == null || name.trim().isEmpty()) {
                throw new BusinessException("小组名称不能为空");
            }
            String normalizedName = name.trim();
            if (normalizedName.length() > 100) {
                throw new BusinessException("小组名称长度不能超过100个字符");
            }

            // 禁止重名（避免出现多个同名小组导致用户无法区分）
            StudyGroup nameExists = groupMapper.selectOne(
                    new LambdaQueryWrapper<StudyGroup>().eq(StudyGroup::getName, normalizedName));
            if (nameExists != null) {
                throw new BusinessException("小组名称已存在，请换一个名称");
            }
            
            Long leaderId = securityUtil.currentUserId();
            StudyGroup group = new StudyGroup();
            group.setName(normalizedName);
            group.setLeaderId(leaderId);
            group.setIsPublished(false); // 默认未发布
            group.setCreatedAt(LocalDateTime.now());
            
            // 如果提供了预约起始时间，设置它
            if (req.get("reservationStartTime") != null) {
                try {
                    String timeStr = req.get("reservationStartTime").toString();
                    group.setReservationStartTime(LocalDateTime.parse(timeStr));
                } catch (Exception e) {
                    log.warn("解析预约起始时间失败", e);
                }
            }
            
            groupMapper.insert(group);
            
            GroupMember leader = new GroupMember();
            leader.setGroupId(group.getId());
            leader.setUserId(leaderId);
            leader.setRole("LEADER");
            memberMapper.insert(leader);
            
            log.info("用户 {} 创建了小组 {}", leaderId, group.getId());
            pushGroupChanged("group_created", group.getId());
            return Map.of("id", group.getId(), "name", group.getName(), "message", "小组创建成功");
        } catch (IllegalStateException e) {
            log.error("创建小组时获取用户ID失败", e);
            throw BusinessException.forbidden("未登录或登录已过期");
        } catch (Exception e) {
            log.error("创建小组失败", e);
            throw BusinessException.internalError("创建失败：" + e.getMessage());
        }
    }
    
    @Transactional
    public Map<String, Object> publishGroup(Long groupId, Map<String, Object> req) {
        Long userId = securityUtil.currentUserId();
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        
        // 只有组长可以发布
        if (!group.getLeaderId().equals(userId)) {
            throw BusinessException.forbidden("只有组长可以发布小组");
        }
        
        // 检查是否已有协同预约（至少需要有一个协同预约才能发布）
        List<GroupReservation> existingReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED")));
        
        if (existingReservations.isEmpty()) {
            throw new BusinessException(
                    "发布小组前需要先创建协同预约（设置座位和时间），请先创建协同预约后再发布");
        }
        
        // 验证预约起始时间
        LocalDateTime reservationStartTime = null;
        if (req.get("reservationStartTime") != null) {
            try {
                String timeStr = req.get("reservationStartTime").toString();
                reservationStartTime = LocalDateTime.parse(timeStr);
            } catch (Exception e) {
                throw new BusinessException("预约起始时间格式错误");
            }
        } else {
            throw new BusinessException("发布小组需要设置预约起始时间");
        }
        
        // 更新小组状态
        group.setIsPublished(true);
        group.setReservationStartTime(reservationStartTime);
        groupMapper.updateById(group);
        
        log.info("小组 {} 已发布，预约起始时间：{}，已有 {} 个协同预约", groupId, reservationStartTime, existingReservations.size());
        pushGroupChanged("group_published", groupId);
        return Map.of("message", "小组已发布");
    }
    
    @Transactional
    public Map<String, Object> deleteGroup(Long groupId) {
        try {
            Long userId = securityUtil.currentUserId();
            StudyGroup group = groupMapper.selectById(groupId);
            if (group == null) {
                throw BusinessException.notFound("小组不存在");
            }
            
            // 只有组长可以删除
            if (!group.getLeaderId().equals(userId)) {
                log.warn("用户 {} 尝试删除小组 {}，但用户不是组长（组长ID: {}）", userId, groupId, group.getLeaderId());
                throw BusinessException.forbidden("只有组长可以删除小组");
            }
            // 不允许直接删除仍有进行中协同预约的小组，先取消预约再删除
            long activeGroupReservations = reservationMapper.selectCount(
                    new LambdaQueryWrapper<GroupReservation>()
                            .eq(GroupReservation::getGroupId, groupId)
                            .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED"))
                            .ge(GroupReservation::getEndTime, LocalDateTime.now())
            );
            if (activeGroupReservations > 0) {
                throw new BusinessException("当前还有小组成员进行预约中，请先取消协同预约（或等待预约结束）后再删除小组");
            }
            
            // 删除前先处理该小组关联的协同预约与个人预约，避免遗留“孤儿预约”
            List<GroupReservation> groupReservations = reservationMapper.selectList(
                    new LambdaQueryWrapper<GroupReservation>().eq(GroupReservation::getGroupId, groupId));
            int cancelledPersonalCount = 0;
            for (GroupReservation gr : groupReservations) {
                List<Long> seatIds = parseSeatIds(gr.getSeatIds());
                if (seatIds.isEmpty()) {
                    continue;
                }
                // 与该协同预约同座位/同时间段的个人预约统一取消（ACTIVE/CONFIRMED/PENDING）
                List<Reservation> relatedReservations = personalReservationMapper.selectList(
                        new LambdaQueryWrapper<Reservation>()
                                .in(Reservation::getSeatId, seatIds)
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                for (Reservation r : relatedReservations) {
                    r.setStatus("CANCELLED");
                    personalReservationMapper.updateById(r);
                    cancelledPersonalCount++;
                    try {
                        seatStatusService.onReservationCancelled(r.getSeatId());
                    } catch (Exception e) {
                        log.error("删除小组时更新座位状态失败，seatId={}", r.getSeatId(), e);
                    }
                }
            }

            // 删除相关的数据（级联删除由数据库外键处理，但这里显式删除关联数据）
            joinRequestMapper.delete(new LambdaQueryWrapper<GroupJoinRequest>().eq(GroupJoinRequest::getGroupId, groupId));
            notificationMapper.delete(new LambdaQueryWrapper<GroupNotification>().eq(GroupNotification::getGroupId, groupId));
            reservationMapper.delete(new LambdaQueryWrapper<GroupReservation>().eq(GroupReservation::getGroupId, groupId));
            memberMapper.delete(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
            
            // 删除小组
            groupMapper.deleteById(groupId);
            
            log.info("用户 {} 已删除小组 {}，并取消 {} 条关联个人预约", userId, groupId, cancelledPersonalCount);
            pushGroupChanged("group_deleted", groupId);
            return Map.of(
                    "message", "小组已删除",
                    "cancelledPersonalReservations", cancelledPersonalCount
            );
        } catch (IllegalStateException e) {
            log.error("删除小组时获取用户ID失败，小组ID: {}", groupId, e);
            throw BusinessException.forbidden("未登录或登录已过期");
        } catch (Exception e) {
            log.error("删除小组失败，小组ID: {}", groupId, e);
            throw BusinessException.internalError("删除失败：" + e.getMessage());
        }
    }

    public List<Map<String, Object>> myGroups() {
        try {
            Long userId = securityUtil.currentUserId();
            User currentUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername,
                    securityUtil.currentUsername()));
            boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        
        // 获取所有小组
        List<StudyGroup> allGroups = groupMapper.selectList(null);
        LocalDateTime now = LocalDateTime.now();
        
        return allGroups.stream()
                .filter(group -> {
                    // 管理员可以看到所有小组
                    if (isAdmin) return true;
                    // 组长可以看到自己创建的所有小组（包括未发布的）
                    if (group.getLeaderId().equals(userId)) return true;
                    // 其他用户只能看到：已发布 + 到开放时间 + 存在未结束的协同预约（否则小组不应继续对外展示/接收申请）
                    if (Boolean.TRUE.equals(group.getIsPublished()) && group.getReservationStartTime() != null
                            && !now.isBefore(group.getReservationStartTime())) {
                        long activeReservationCount = reservationMapper.selectCount(
                                new LambdaQueryWrapper<GroupReservation>()
                                        .eq(GroupReservation::getGroupId, group.getId())
                                        .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED"))
                                        .ge(GroupReservation::getEndTime, now)
                        );
                        return activeReservationCount > 0;
                    }
                    return false;
                })
                .map(group -> {
            List<GroupMember> allMembers = memberMapper.selectList(
                    new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, group.getId()));

            // 计算“名额/是否已满”：若存在已确认且未结束的协同预约，则容量 = seatIds 数量（取最早一条 CONFIRMED）
            Integer seatCapacity = null;
            Boolean isFull = null;
            GroupReservation activeConfirmed = reservationMapper.selectOne(
                    new LambdaQueryWrapper<GroupReservation>()
                            .eq(GroupReservation::getGroupId, group.getId())
                            .eq(GroupReservation::getStatus, "CONFIRMED")
                            .ge(GroupReservation::getEndTime, now)
                            .orderByAsc(GroupReservation::getStartTime)
                            .last("LIMIT 1"));
            if (activeConfirmed != null) {
                List<Long> seatIds = parseSeatIds(activeConfirmed.getSeatIds());
                seatCapacity = seatIds.size();
                isFull = allMembers.size() >= seatCapacity;
            }
            
            // 查找当前用户在该小组中的角色
            GroupMember myMembership = memberMapper.selectOne(
                    new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupId, group.getId())
                            .eq(GroupMember::getUserId, userId));
            
            // 如果用户是组长，即使不在成员表中，也应该设置为 LEADER
            // 这样可以确保组长能够删除自己创建的小组
            String myRole = null;
            if (myMembership != null) {
                myRole = myMembership.getRole();
            } else if (group.getLeaderId().equals(userId)) {
                // 用户是组长但不在成员表中，设置为 LEADER
                myRole = "LEADER";
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", group.getId());
            result.put("name", group.getName());
            result.put("leaderId", group.getLeaderId());
            result.put("myRole", myRole);
            result.put("memberCount", allMembers.size());
            result.put("seatCapacity", seatCapacity); // 可能为 null（尚无已确认协同预约）
            result.put("isFull", isFull);             // 可能为 null（尚无已确认协同预约）
            result.put("createdAt", group.getCreatedAt());
            result.put("isPublished", Boolean.TRUE.equals(group.getIsPublished()));
            result.put("reservationStartTime", group.getReservationStartTime());
            // 检查是否有待处理的加入申请
            GroupJoinRequest pendingRequest = joinRequestMapper.selectOne(
                    new LambdaQueryWrapper<GroupJoinRequest>()
                            .eq(GroupJoinRequest::getGroupId, group.getId())
                            .eq(GroupJoinRequest::getUserId, userId)
                            .eq(GroupJoinRequest::getStatus, "PENDING"));
            result.put("hasPendingRequest", pendingRequest != null);
            // 显示组长信息
            User leader = userMapper.selectById(group.getLeaderId());
            result.put("leaderName", leader != null ? leader.getUsername() : "未知");
            return result;
        }).collect(Collectors.toList());
        } catch (IllegalStateException e) {
            log.error("获取小组列表时获取用户ID失败", e);
            return List.of(); // 返回空列表而不是抛出异常
        } catch (Exception e) {
            log.error("获取小组列表失败", e);
            return List.of(); // 返回空列表而不是抛出异常
        }
    }

    @Transactional
    public Map<String, Object> requestJoinGroup(Long groupId) {
        Long userId = securityUtil.currentUserId();
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        
        // 检查是否已经是成员
        GroupMember existing = memberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, userId));
        if (existing != null) {
            throw new BusinessException("已经是小组成员");
        }
        
        // 检查是否已有待处理的申请
        GroupJoinRequest existingRequest = joinRequestMapper.selectOne(
                new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getGroupId, groupId)
                        .eq(GroupJoinRequest::getUserId, userId)
                        .eq(GroupJoinRequest::getStatus, "PENDING"));
        if (existingRequest != null) {
            throw new BusinessException("已提交申请，等待组长审批");
        }

        // 若协同预约已结束/不存在未结束预约，则禁止再申请加入（避免结束后仍可申请 + 打扰组长）
        LocalDateTime now = LocalDateTime.now();
        long activeReservationCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED"))
                        .ge(GroupReservation::getEndTime, now)
        );
        if (activeReservationCount == 0) {
            throw new BusinessException("该小组协同预约已结束，暂不可申请加入");
        }

        // 若该小组存在已确认且未结束的协同预约，则在“提交申请”阶段就拦截满员，避免无意义地打扰组长
        GroupReservation activeConfirmed = reservationMapper.selectOne(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .eq(GroupReservation::getStatus, "CONFIRMED")
                        .ge(GroupReservation::getEndTime, now)
                        .orderByAsc(GroupReservation::getStartTime)
                        .last("LIMIT 1"));
        if (activeConfirmed != null) {
            List<Long> seatIds = parseSeatIds(activeConfirmed.getSeatIds());
            long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
            if (seatIds.size() > 0 && memberCount >= seatIds.size()) {
                throw new BusinessException(
                        String.format("该小组成员已满（%d/%d），暂不可申请加入。", memberCount, seatIds.size()));
            }
        }
        
        // 验证小组是否已发布且预约起始时间已到
        if (!Boolean.TRUE.equals(group.getIsPublished())) {
            throw new BusinessException("小组尚未发布，无法申请");
        }
        if (group.getReservationStartTime() != null && now.isBefore(group.getReservationStartTime())) {
            throw new BusinessException("小组预约尚未开始，无法申请");
        }
        
        // 申请人个人预约与小组未结束的协同时段不得交叉（否则批准后无法为其落座）
        List<GroupReservation> activeGroupSlots = reservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED"))
                        .ge(GroupReservation::getEndTime, now));
        for (GroupReservation gr : activeGroupSlots) {
            if (personalReservationOverlapService.hasUserTimeOverlap(
                    userId, gr.getStartTime(), gr.getEndTime(), null)) {
                log.warn("用户 {} 申请加入小组 {} 被拒：与个人预约在时段 {} - {} 重叠",
                        userId, groupId, gr.getStartTime(), gr.getEndTime());
                throw BusinessException.conflict(
                        "该协同小组预约时间与您的原有个人预约冲突，如需协同预约请先取消原有个人预约。");
            }
        }
        
        // 创建加入申请
        GroupJoinRequest request = new GroupJoinRequest();
        request.setGroupId(groupId);
        request.setUserId(userId);
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        joinRequestMapper.insert(request);
        
        // 创建通知给组长
        User applicant = userMapper.selectById(userId);
        String content = String.format("用户 %s 申请加入您的小组 %s", 
                applicant != null ? applicant.getUsername() : "未知用户", group.getName());
        GroupNotification notification = new GroupNotification();
        notification.setUserId(group.getLeaderId());
        notification.setGroupId(groupId);
        notification.setType("JOIN_REQUEST");
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        
        log.info("用户 {} 申请加入小组 {}", userId, groupId);
        pushGroupChanged("join_requested", groupId);
        return Map.of("message", "申请已提交，等待组长审批");
    }
    
    public List<Map<String, Object>> getJoinRequests(Long groupId) {
        Long userId = securityUtil.currentUserId();
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new IllegalStateException("小组不存在");
        }
        
        // 只有组长可以查看申请列表
        if (!group.getLeaderId().equals(userId)) {
            throw new IllegalStateException("只有组长可以查看申请列表");
        }
        
        List<GroupJoinRequest> requests = joinRequestMapper.selectList(
                new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getGroupId, groupId)
                        .in(GroupJoinRequest::getStatus, List.of("PENDING", "APPROVED", "REJECTED", "EXPIRED"))
                        .orderByDesc(GroupJoinRequest::getCreatedAt));
        
        return requests.stream().map(req -> {
            User user = userMapper.selectById(req.getUserId());
            Map<String, Object> result = new HashMap<>();
            result.put("id", req.getId());
            result.put("userId", req.getUserId());
            result.put("username", user != null ? user.getUsername() : "未知");
            result.put("status", req.getStatus());
            result.put("createdAt", req.getCreatedAt());
            result.put("updatedAt", req.getUpdatedAt());
            return result;
        }).collect(Collectors.toList());
    }
    
    @Transactional
    public Map<String, Object> approveJoinRequest(Long groupId, Long requestId) {
        Long userId = securityUtil.currentUserId();
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        
        // 只有组长可以审批
        if (!group.getLeaderId().equals(userId)) {
            throw BusinessException.forbidden("只有组长可以审批申请");
        }
        
        GroupJoinRequest request = joinRequestMapper.selectById(requestId);
        if (request == null || !request.getGroupId().equals(groupId)) {
            throw BusinessException.notFound("申请不存在");
        }
        
        // 检查申请状态
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException("申请已处理，无法重复审批");
        }

        // 检查用户是否已经是成员（避免重复添加）
        GroupMember existingMember = memberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, request.getUserId()));

        // 若小组已有「已确认且未结束」的协同预约，则成员数量不得超过座位数量。
        // 否则会出现“3人抢2个座位”但审批都通过的情况，后续无法为新成员分配座位/创建个人预约。
        List<GroupReservation> confirmedReservationsForCapacityCheck = reservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .eq(GroupReservation::getStatus, "CONFIRMED")
                        .ge(GroupReservation::getEndTime, LocalDateTime.now())
                        .orderByAsc(GroupReservation::getStartTime));

        if (!confirmedReservationsForCapacityCheck.isEmpty()) {
            long currentMemberCount = memberMapper.selectCount(
                    new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
            long nextMemberCount = currentMemberCount + (existingMember == null ? 1 : 0);

            for (GroupReservation gr : confirmedReservationsForCapacityCheck) {
                List<Long> seatIds = parseSeatIds(gr.getSeatIds());
                if (seatIds.size() < nextMemberCount) {
                    throw new BusinessException(
                            String.format("座位数量不足，无法批准更多成员：该小组在 %s - %s 的协同预约仅选择了 %d 个座位，但审批后成员将达到 %d 人。",
                                    gr.getStartTime(), gr.getEndTime(), seatIds.size(), nextMemberCount));
                }
            }
        }

        // 批准申请，添加为成员
        request.setStatus("APPROVED");
        request.setUpdatedAt(LocalDateTime.now());
        joinRequestMapper.updateById(request);

        if (existingMember == null) {
            // 添加为小组成员
            GroupMember member = new GroupMember();
            member.setGroupId(groupId);
            member.setUserId(request.getUserId());
            member.setRole("MEMBER");
            memberMapper.insert(member);
            log.info("用户 {} 已添加到小组 {} 的成员列表", request.getUserId(), groupId);
        } else {
            log.info("用户 {} 已经是小组 {} 的成员，跳过添加", request.getUserId(), groupId);
        }
        
        // 检查是否有已确认的协同预约，如果有，自动为新成员创建个人预约
        List<GroupReservation> confirmedReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .eq(GroupReservation::getStatus, "CONFIRMED")
                        .ge(GroupReservation::getEndTime, LocalDateTime.now()) // 只处理未结束的预约
                        .orderByAsc(GroupReservation::getStartTime));
        
        int autoCreatedCount = 0;
        List<String> autoCreatedDetails = new ArrayList<>();
        
        for (GroupReservation gr : confirmedReservations) {
            try {
        // 解析座位ID列表
        List<Long> seatIds = parseSeatIds(gr.getSeatIds());
                
                // 获取当前所有成员（包括刚加入的新成员）
                List<GroupMember> allMembers = memberMapper.selectList(
                        new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
                
                // 找出已为该预约创建了个人预约的成员
                Set<Long> membersWithReservation = new HashSet<>();
                for (GroupMember m : allMembers) {
                    List<Reservation> existing = personalReservationMapper.selectList(
                            new LambdaQueryWrapper<Reservation>()
                                    .eq(Reservation::getUserId, m.getUserId())
                                    .eq(Reservation::getStartTime, gr.getStartTime())
                                    .eq(Reservation::getEndTime, gr.getEndTime())
                                    .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                    if (!existing.isEmpty()) {
                        membersWithReservation.add(m.getUserId());
                    }
                }
                
                // 为新成员分配座位（优先使用未分配的座位，如果没有则使用已分配的座位）
                Long assignedSeatId = null;
                for (Long seatId : seatIds) {
                    // 检查这个座位是否已被分配给其他成员
                    boolean seatAssigned = false;
                    for (GroupMember m : allMembers) {
                        if (membersWithReservation.contains(m.getUserId())) {
                            List<Reservation> existing = personalReservationMapper.selectList(
                                    new LambdaQueryWrapper<Reservation>()
                                            .eq(Reservation::getUserId, m.getUserId())
                                            .eq(Reservation::getSeatId, seatId)
                                            .eq(Reservation::getStartTime, gr.getStartTime())
                                            .eq(Reservation::getEndTime, gr.getEndTime())
                                            .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                            if (!existing.isEmpty()) {
                                seatAssigned = true;
                                break;
                            }
                        }
                    }
                    if (!seatAssigned) {
                        assignedSeatId = seatId;
                        break;
                    }
                }
                
                // 若没有可分配空位，则不再“强行复用第一个座位”
                // 否则会导致同一时段同座位出现多条个人预约，违背一座一人规则
                if (assignedSeatId == null) {
                    log.warn("小组 {} 在协同预约 {} 时间段 {} - {} 已无可分配空位，无法为新成员 {} 自动创建个人预约",
                            groupId, gr.getId(), gr.getStartTime(), gr.getEndTime(), request.getUserId());
                    continue;
                }

                // 最终兜底：检查“该座位 + 该时间段”是否已存在有效个人预约
                long sameSeatTimeCount = personalReservationMapper.selectCount(
                        new LambdaQueryWrapper<Reservation>()
                                .eq(Reservation::getSeatId, assignedSeatId)
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                if (sameSeatTimeCount > 0) {
                    log.warn("座位 {} 在 {} - {} 已有有效个人预约，跳过为新成员 {} 自动创建",
                            assignedSeatId, gr.getStartTime(), gr.getEndTime(), request.getUserId());
                    continue;
                }
                
                boolean newMemberSlotBusy = assignedSeatId != null
                        && personalReservationOverlapService.hasUserTimeOverlap(
                                request.getUserId(), gr.getStartTime(), gr.getEndTime(), null);
                
                if (!newMemberSlotBusy && assignedSeatId != null) {
                    // 为新成员创建个人预约
                    Reservation reservation = new Reservation();
                    reservation.setUserId(request.getUserId());
                    reservation.setSeatId(assignedSeatId);
                    reservation.setStartTime(gr.getStartTime());
                    reservation.setEndTime(gr.getEndTime());
                    reservation.setStatus("CONFIRMED");
                    reservation.setCreatedAt(LocalDateTime.now());
                    
                    int insertResult = personalReservationMapper.insert(reservation);
                    if (insertResult > 0 && reservation.getId() != null) {
                        autoCreatedCount++;
                        autoCreatedDetails.add(String.format("预约时间: %s - %s, 座位ID: %d, 预约ID: %d", 
                                gr.getStartTime(), gr.getEndTime(), assignedSeatId, reservation.getId()));
                        log.info("✅ 为新成员 {} 自动创建了个人预约 ID: {}, 座位ID: {}, 预约时间: {} - {}", 
                                request.getUserId(), reservation.getId(), assignedSeatId, gr.getStartTime(), gr.getEndTime());
                        
                        // 更新座位状态
                        try {
                            seatStatusService.onReservationCreated(assignedSeatId);
                        } catch (Exception e) {
                            log.error("更新座位状态失败，座位ID: {}", assignedSeatId, e);
                        }
                    } else {
                        log.error("❌ 为新成员 {} 创建个人预约失败，插入结果: {}", request.getUserId(), insertResult);
                    }
                } else if (newMemberSlotBusy) {
                    log.info("新成员 {} 在该时段已有个人预约，跳过自动创建", request.getUserId());
                } else {
                    log.warn("无法为新成员 {} 分配座位，所有座位都已被使用", request.getUserId());
                }
            } catch (Exception e) {
                log.error("为新成员 {} 自动创建预约时出错，预约ID: {}", request.getUserId(), gr.getId(), e);
            }
        }
        
        // 创建通知给申请者
        String content = String.format("您申请加入的小组 %s 已批准", group.getName());
        if (autoCreatedCount > 0) {
            content += String.format("，已自动为您创建了 %d 个个人预约", autoCreatedCount);
        }
        GroupNotification notification = new GroupNotification();
        notification.setUserId(request.getUserId());
        notification.setGroupId(groupId);
        notification.setType("REQUEST_APPROVED");
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        
        log.info("组长 {} 批准了用户 {} 加入小组 {} 的申请，自动创建了 {} 个个人预约", 
                userId, request.getUserId(), groupId, autoCreatedCount);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "申请已批准" + (autoCreatedCount > 0 ? String.format("，已自动创建了 %d 个个人预约", autoCreatedCount) : ""));
        response.put("autoCreatedCount", autoCreatedCount);
        if (!autoCreatedDetails.isEmpty()) {
            response.put("autoCreatedDetails", autoCreatedDetails);
        }
        
        pushGroupChanged("join_approved", groupId);
        return response;
    }
    
    @Transactional
    public Map<String, Object> rejectJoinRequest(Long groupId, Long requestId) {
        Long userId = securityUtil.currentUserId();
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        
        // 只有组长可以审批
        if (!group.getLeaderId().equals(userId)) {
            throw BusinessException.forbidden("只有组长可以审批申请");
        }
        
        GroupJoinRequest request = joinRequestMapper.selectById(requestId);
        if (request == null || !request.getGroupId().equals(groupId)) {
            throw BusinessException.notFound("申请不存在");
        }
        
        // 检查申请状态
        if (!"PENDING".equals(request.getStatus())) {
            throw new BusinessException("申请已处理，无法重复审批");
        }
        
        // 拒绝申请
        request.setStatus("REJECTED");
        request.setUpdatedAt(LocalDateTime.now());
        joinRequestMapper.updateById(request);
        
        // 创建通知给申请者
        String content = String.format("您申请加入的小组 %s 已被拒绝", group.getName());
        GroupNotification notification = new GroupNotification();
        notification.setUserId(request.getUserId());
        notification.setGroupId(groupId);
        notification.setType("REQUEST_REJECTED");
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        
        log.info("组长 {} 拒绝了用户 {} 加入小组 {} 的申请", userId, request.getUserId(), groupId);
        pushGroupChanged("join_rejected", groupId);
        return Map.of("message", "申请已拒绝");
    }
    
    public List<Map<String, Object>> getMyJoinRequests() {
        Long userId = securityUtil.currentUserId();
        List<GroupJoinRequest> requests = joinRequestMapper.selectList(
                new LambdaQueryWrapper<GroupJoinRequest>()
                        .eq(GroupJoinRequest::getUserId, userId)
                        .orderByDesc(GroupJoinRequest::getCreatedAt));
        
        return requests.stream().map(req -> {
            StudyGroup group = groupMapper.selectById(req.getGroupId());
            Map<String, Object> result = new HashMap<>();
            result.put("id", req.getId());
            result.put("groupId", req.getGroupId());
            result.put("groupName", group != null ? group.getName() : "未知小组");
            result.put("status", req.getStatus());
            result.put("createdAt", req.getCreatedAt());
            result.put("updatedAt", req.getUpdatedAt());
            return result;
        }).collect(Collectors.toList());
    }

    @Transactional
    public GroupReservation createGroupReservation(Long groupId,
                                                   Map<String, Object> req) {
        Long userId = securityUtil.currentUserId();
        
        // 检查用户是否在黑名单中
        User user = userMapper.selectById(userId);
        if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
            log.warn("用户 {} 在黑名单中，拒绝协同预约请求", userId);
            throw BusinessException.forbidden("您的账号已被加入黑名单，无法预约座位。如有疑问，请联系管理员。");
        }
        
        GroupMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupId, groupId)
                        .eq(GroupMember::getUserId, userId));
        if (member == null) {
            throw BusinessException.forbidden("不是小组成员");
        }
        // 约束：仅组长可创建协同预约草稿，避免成员误建多条草稿
        if (!"LEADER".equals(member.getRole())) {
            throw BusinessException.forbidden("只有组长可以创建协同预约");
        }
        
        @SuppressWarnings("unchecked")
        List<?> seatIdsRaw = (List<?>) req.get("seatIds");
        // 将前端传递的 Integer 或 Long 统一转换为 Long
        List<Long> seatIds = seatIdsRaw.stream()
                .map(id -> id instanceof Integer ? ((Integer) id).longValue() : ((Number) id).longValue())
                .collect(Collectors.toList());
        
        // 验证：协同预约至少需要2个座位
        if (seatIds.size() < 2) {
            throw new BusinessException("协同预约至少需要选择2个座位");
        }
        
        String startTimeStr = req.get("startTime").toString();
        String endTimeStr = req.get("endTime").toString();
        
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
        
        // 验证时间
        LocalDateTime now = LocalDateTime.now();
        if (!startTime.isAfter(now)) {
            throw new BusinessException("开始时间必须是未来时间");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException("结束时间需晚于开始时间");
        }
        if (java.time.Duration.between(startTime, endTime).toHours() > 4) {
            throw new BusinessException("单次预约不超过4小时");
        }
        
        // 检查每个座位是否有时间冲突（包括个人预约和协同预约）
        log.info("========== 开始检查座位冲突 ==========");
        log.info("🔍 创建协同预约请求：小组ID={}, 座位={}, 时间段 {} - {}", groupId, seatIds, startTime, endTime);
        log.info("🔍 当前时间：{}", LocalDateTime.now());
        
        List<Long> conflictedSeats = new ArrayList<>();
        Map<Long, String> conflictDetails = new HashMap<>();
        
        for (Long seatId : seatIds) {
            log.info("----------------------------------------");
            log.info("🔍 检查座位 {} 的冲突...", seatId);
            
            // 先查询该座位的所有有效预约（用于调试）
            List<Reservation> allReservationsForSeat = personalReservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getSeatId, seatId)
                            .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                            .orderByAsc(Reservation::getStartTime));
            
            log.info("🔍 座位 {} 的所有有效预约数量：{}", seatId, allReservationsForSeat.size());
            for (Reservation r : allReservationsForSeat) {
                log.info("  - 预约ID={}, 用户ID={}, 状态={}, 时间={} - {}", 
                        r.getId(), r.getUserId(), r.getStatus(), r.getStartTime(), r.getEndTime());
                // 手动检查时间重叠
                boolean overlaps = !(r.getEndTime().isBefore(startTime) || r.getStartTime().isAfter(endTime));
                log.info("    时间重叠检查：{} (现有预约结束时间 {} {} 新预约开始时间 {} 且 现有预约开始时间 {} {} 新预约结束时间 {})", 
                        overlaps ? "重叠" : "不重叠",
                        r.getEndTime(), r.getEndTime().isBefore(startTime) ? "<=" : ">", startTime,
                        r.getStartTime(), r.getStartTime().isAfter(endTime) ? ">=" : "<", endTime);
            }
            
            // 检查个人预约冲突（检查所有有效状态的预约，包括该用户自己的）
            // 时间重叠条件：NOT (end_time <= startTime OR start_time >= endTime)
            // 即：end_time > startTime AND start_time < endTime
            List<Reservation> personalConflicts = personalReservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getSeatId, seatId)
                            .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                            .gt(Reservation::getEndTime, startTime)  // 预约结束时间 > 新预约开始时间
                            .lt(Reservation::getStartTime, endTime)); // 预约开始时间 < 新预约结束时间
            
            log.info("🔍 座位 {} 的个人预约冲突检查结果：找到 {} 个冲突", seatId, personalConflicts.size());
            if (!personalConflicts.isEmpty()) {
                for (Reservation c : personalConflicts) {
                    log.error("🔍 冲突详情：预约ID={}, 座位={}, 状态={}, 用户ID={}, 时间={} - {}", 
                            c.getId(), c.getSeatId(), c.getStatus(), c.getUserId(), c.getStartTime(), c.getEndTime());
                }
                conflictedSeats.add(seatId);
                Reservation conflict = personalConflicts.get(0);
                User conflictUser = userMapper.selectById(conflict.getUserId());
                String conflictUsername = conflictUser != null ? conflictUser.getUsername() : "用户ID:" + conflict.getUserId();
                conflictDetails.put(seatId, String.format("座位 %d 已被用户 %s 预约（预约ID: %d, 状态: %s, 时间: %s - %s）", 
                        seatId, conflictUsername, conflict.getId(), conflict.getStatus(), conflict.getStartTime(), conflict.getEndTime()));
                log.error("❌ 创建协同预约冲突：座位 {} 在时间段 {} - {} 已被个人预约 ID: {} 占用（用户: {}, 时间: {} - {}）", 
                        seatId, startTime, endTime, conflict.getId(), conflictUsername, conflict.getStartTime(), conflict.getEndTime());
                continue;
            }
            
            // 检查协同预约冲突（已确认的）
            List<GroupReservation> allGroupReservations = reservationMapper.selectList(
                    new LambdaQueryWrapper<GroupReservation>()
                            .eq(GroupReservation::getStatus, "CONFIRMED")
                            .orderByAsc(GroupReservation::getStartTime));
            
            log.info("🔍 所有已确认的协同预约数量：{}", allGroupReservations.size());
            for (GroupReservation gr : allGroupReservations) {
                if (gr.getSeatIds() != null && !gr.getSeatIds().trim().isEmpty()) {
                    List<Long> grSeatIds = parseSeatIds(gr.getSeatIds());
                    log.info("  - 协同预约ID={}, 小组ID={}, 座位={}, 时间={} - {}", 
                            gr.getId(), gr.getGroupId(), grSeatIds, gr.getStartTime(), gr.getEndTime());
                    if (grSeatIds.contains(seatId)) {
                        boolean overlaps = !(gr.getEndTime().isBefore(startTime) || gr.getStartTime().isAfter(endTime));
                        log.info("    包含座位 {}，时间重叠检查：{}", seatId, overlaps ? "重叠" : "不重叠");
                    }
                }
            }
            
            List<GroupReservation> groupConflicts = reservationMapper.selectList(
                    new LambdaQueryWrapper<GroupReservation>()
                            .eq(GroupReservation::getStatus, "CONFIRMED")
                            .gt(GroupReservation::getEndTime, startTime)  // 预约结束时间 > 新预约开始时间
                            .lt(GroupReservation::getStartTime, endTime)); // 预约开始时间 < 新预约结束时间
            
            log.info("🔍 座位 {} 的协同预约冲突检查结果：找到 {} 个冲突", seatId, groupConflicts.size());
            for (GroupReservation gr : groupConflicts) {
                if (gr.getSeatIds() != null && !gr.getSeatIds().trim().isEmpty()) {
                    List<Long> grSeatIds = parseSeatIds(gr.getSeatIds());
                    if (grSeatIds.contains(seatId)) {
                        conflictedSeats.add(seatId);
                        conflictDetails.put(seatId, String.format("座位 %d 已被协同预约 %d 占用（小组ID: %d, 时间: %s - %s）", 
                                seatId, gr.getId(), gr.getGroupId(), gr.getStartTime(), gr.getEndTime()));
                        log.error("❌ 创建协同预约冲突：座位 {} 在时间段 {} - {} 已被协同预约 ID: {} 占用（小组ID: {}, 时间: {} - {}）", 
                                seatId, startTime, endTime, gr.getId(), gr.getGroupId(), gr.getStartTime(), gr.getEndTime());
                        break;
                    }
                }
            }
            log.info("----------------------------------------");
        }
        
        log.info("========== 冲突检查完成 ==========");
        if (!conflictedSeats.isEmpty()) {
            log.error("❌ 创建协同预约失败：座位 {} 在时间段 {} - {} 有冲突", 
                    conflictedSeats, startTime, endTime);
            log.error("冲突详情：{}", conflictDetails);
            throw BusinessException.conflict("部分座位在该时段已被预约，无法创建");
        }
        
        log.info("✅ 座位冲突检查通过：座位 {} 在时间段 {} - {} 无冲突", seatIds, startTime, endTime);
        
        // 再次验证：在插入前最后检查一次，确保没有遗漏
        log.info("========== 最终检查 ==========");
        for (Long seatId : seatIds) {
            log.info("🔍 最终检查座位 {}...", seatId);
            List<Reservation> finalCheck = personalReservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getSeatId, seatId)
                            .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                            .gt(Reservation::getEndTime, startTime)
                            .lt(Reservation::getStartTime, endTime));
            
            log.info("🔍 最终检查座位 {} 的结果：找到 {} 个冲突", seatId, finalCheck.size());
            if (!finalCheck.isEmpty()) {
                Reservation conflict = finalCheck.get(0);
                log.error("❌ 最终检查发现冲突：座位 {} 在时间段 {} - {} 已被预约 ID: {} 占用（用户: {}, 时间: {} - {}）", 
                        seatId, startTime, endTime, conflict.getId(), conflict.getUserId(), 
                        conflict.getStartTime(), conflict.getEndTime());
                throw BusinessException.conflict("座位在该时段已被预约，无法创建");
            }
        }
        log.info("========== 最终检查通过 ==========");
        
        GroupReservation gr = new GroupReservation();
        gr.setGroupId(groupId);
        gr.setSeatIds(seatIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        gr.setStartTime(startTime);
        gr.setEndTime(endTime);
        gr.setStatus("PENDING");
        gr.setCreatedAt(LocalDateTime.now());
        reservationMapper.insert(gr);
        
        log.info("✅ 小组 {} 创建了协同预约 {}，座位: {}，时间: {} - {}", 
                groupId, gr.getId(), gr.getSeatIds(), startTime, endTime);
        
        pushGroupChanged("group_reservation_created", groupId);
        return gr;
    }

    public List<Map<String, Object>> getGroupMembers(Long groupId) {
        Long userId = securityUtil.currentUserId();
        log.info("获取小组 {} 的成员列表，当前用户ID: {}", groupId, userId);
        
        // 检查小组是否存在
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            log.warn("小组 {} 不存在", groupId);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "小组不存在");
        }
        
        // 获取所有成员
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        log.info("找到 {} 个成员", members.size());
        
        return members.stream().map(m -> {
            User user = userMapper.selectById(m.getUserId());
            Map<String, Object> result = new HashMap<>();
            result.put("userId", m.getUserId());
            result.put("username", user != null ? user.getUsername() : "未知");
            result.put("role", m.getRole());
            return result;
        }).collect(Collectors.toList());
    }

    public List<GroupReservation> getGroupReservations(Long groupId) {
        return reservationMapper.selectList(
                new LambdaQueryWrapper<GroupReservation>()
                        .eq(GroupReservation::getGroupId, groupId)
                        .orderByDesc(GroupReservation::getCreatedAt));
    }
    
    @Transactional
    public Map<String, Object> confirmGroupReservation(Long groupId, Long reservationId) {
        Long userId = securityUtil.currentUserId();
        
        // 只有组长可以确认预约
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        if (!group.getLeaderId().equals(userId)) {
            throw BusinessException.forbidden("只有组长可以确认预约");
        }
        // 约束：必须先发布小组并达到开放时间
        if (!Boolean.TRUE.equals(group.getIsPublished())) {
            throw new BusinessException("小组未发布，不能确认协同预约");
        }
        if (group.getReservationStartTime() == null || LocalDateTime.now().isBefore(group.getReservationStartTime())) {
            throw new BusinessException("未到小组开放时间，不能确认协同预约");
        }
        
        GroupReservation gr = reservationMapper.selectById(reservationId);
        if (gr == null || !gr.getGroupId().equals(groupId)) {
            throw BusinessException.notFound("预约不存在");
        }
        
        if (!"PENDING".equals(gr.getStatus())) {
            throw new BusinessException("该预约已处理，无法重复确认");
        }
        
        // 解析座位ID列表
        List<Long> seatIds = parseSeatIds(gr.getSeatIds());
        
        // 获取所有小组成员
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        
        // 确保组长在成员列表中（如果不在，自动添加）
        GroupMember leaderMember = members.stream()
                .filter(m -> "LEADER".equals(m.getRole()) || m.getUserId().equals(group.getLeaderId()))
                .findFirst()
                .orElse(null);
        
        if (leaderMember == null) {
            // 组长不在成员列表中，自动添加
            GroupMember newLeader = new GroupMember();
            newLeader.setGroupId(groupId);
            newLeader.setUserId(group.getLeaderId());
            newLeader.setRole("LEADER");
            try {
                memberMapper.insert(newLeader);
                members.add(newLeader);
                log.warn("组长 {} 不在成员列表中，已自动添加", group.getLeaderId());
            } catch (Exception e) {
                log.error("自动添加组长到成员列表失败", e);
                // 即使添加失败，也继续流程，因为组长可能已经有其他记录
            }
        }
        
        // 验证成员列表：确保所有成员的用户ID都有效
        List<GroupMember> validMembers = new ArrayList<>();
        for (GroupMember m : members) {
            User user = userMapper.selectById(m.getUserId());
            if (user == null) {
                log.error("成员列表中的用户ID {} 不存在，跳过", m.getUserId());
                continue;
            }
            if (Boolean.TRUE.equals(user.getIsFrozen())) {
                log.warn("成员列表中的用户 {} (ID: {}) 已被冻结，跳过", user.getUsername(), m.getUserId());
                continue;
            }
            validMembers.add(m);
        }
        
        if (validMembers.isEmpty()) {
            log.error("小组 {} 没有有效成员，无法确认预约", groupId);
            throw new BusinessException("小组没有有效成员，无法确认预约");
        }
        
        if (validMembers.size() < members.size()) {
            log.warn("小组 {} 有 {} 个成员，但只有 {} 个有效成员", groupId, members.size(), validMembers.size());
        }
        
        members = validMembers; // 使用验证后的成员列表
        
        // 确保组长在第一位
        members.sort((m1, m2) -> {
            if ("LEADER".equals(m1.getRole()) && !"LEADER".equals(m2.getRole())) {
                return -1; // 组长在前
            }
            if (!"LEADER".equals(m1.getRole()) && "LEADER".equals(m2.getRole())) {
                return 1; // 组长在前
            }
            return 0; // 保持原有顺序
        });
        
        log.info("小组 {} 的成员列表（共 {} 人）：{}", groupId, members.size(), 
                members.stream().map(m -> String.format("用户%d(%s)", m.getUserId(), m.getRole())).collect(Collectors.joining(", ")));
        
        // 严格约束：仅在满员（成员数 == 座位数）时允许确认
        if (members.size() != seatIds.size()) {
            throw new BusinessException(
                    String.format("当前成员数（%d）与座位数（%d）不一致，需满员后才能确认预约", members.size(), seatIds.size()));
        }
        
        // 在创建个人预约前，检查所有座位是否有时间冲突
        // 检查每个座位是否已被任何用户预约（包括该用户自己的其他预约）或其他协同预约占用
        List<Long> conflictedSeats = new ArrayList<>();
        Map<Long, String> seatConflictDetails = new HashMap<>(); // 座位ID -> 冲突详情
        
        for (Long seatId : seatIds) {
            // 检查该座位在该时间段是否已被任何用户预约（个人预约，包括该用户自己的其他预约）
            List<Reservation> conflicts = personalReservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getSeatId, seatId)
                            .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                            .gt(Reservation::getEndTime, gr.getStartTime())  // 预约结束时间 > 新预约开始时间
                            .lt(Reservation::getStartTime, gr.getEndTime())); // 预约开始时间 < 新预约结束时间
            
            if (!conflicts.isEmpty()) {
                conflictedSeats.add(seatId);
                Reservation conflict = conflicts.get(0);
                User conflictUser = userMapper.selectById(conflict.getUserId());
                String conflictUsername = conflictUser != null ? conflictUser.getUsername() : "用户ID:" + conflict.getUserId();
                seatConflictDetails.put(seatId, String.format("座位 %d 已被用户 %s 预约（预约ID: %d, 时间: %s - %s）", 
                        seatId, conflictUsername, conflict.getId(), conflict.getStartTime(), conflict.getEndTime()));
                log.warn("座位 {} 在时间段 {} - {} 已有冲突预约 ID: {}，用户: {}", 
                        seatId, gr.getStartTime(), gr.getEndTime(), conflict.getId(), conflictUsername);
                continue;
            }
            
            // 检查该座位是否被其他协同预约占用（已确认的）
            List<GroupReservation> groupConflicts = reservationMapper.selectList(
                    new LambdaQueryWrapper<GroupReservation>()
                            .ne(GroupReservation::getId, reservationId) // 排除当前协同预约
                            .eq(GroupReservation::getStatus, "CONFIRMED")
                            .gt(GroupReservation::getEndTime, gr.getStartTime())  // 预约结束时间 > 新预约开始时间
                            .lt(GroupReservation::getStartTime, gr.getEndTime())); // 预约开始时间 < 新预约结束时间
            
            for (GroupReservation grConflict : groupConflicts) {
                if (grConflict.getSeatIds() != null && !grConflict.getSeatIds().trim().isEmpty()) {
                    List<Long> grSeatIds = parseSeatIds(grConflict.getSeatIds());
                    if (grSeatIds.contains(seatId)) {
                        conflictedSeats.add(seatId);
                        seatConflictDetails.put(seatId, String.format("座位 %d 已被协同预约 %d 占用（小组ID: %d）", 
                                seatId, grConflict.getId(), grConflict.getGroupId()));
                        log.warn("座位 {} 在时间段 {} - {} 已被协同预约 {} 占用", 
                                seatId, gr.getStartTime(), gr.getEndTime(), grConflict.getId());
                        break;
                    }
                }
            }
        }
        
        // 如果有冲突，返回错误信息
        if (!conflictedSeats.isEmpty()) {
            List<String> conflictMessages = new ArrayList<>();
            for (Long seatId : conflictedSeats) {
                conflictMessages.add(seatConflictDetails.get(seatId));
            }
            log.error("❌ 确认协同预约失败：座位 {} 在时间段 {} - {} 有冲突，详情: {}", 
                    conflictedSeats, gr.getStartTime(), gr.getEndTime(), conflictMessages);
            throw BusinessException.conflict("部分座位在该时段已被预约，无法确认");
        }
        
        log.info("✅ 确认预约前冲突检查通过：座位 {} 在时间段 {} - {} 无冲突", 
                seatIds, gr.getStartTime(), gr.getEndTime());
        
        // 为每个成员创建个人预约
        int createdCount = 0;
        List<String> failedMembers = new ArrayList<>();
        List<String> successMembers = new ArrayList<>();
        
        for (int i = 0; i < members.size() && i < seatIds.size(); i++) {
            GroupMember member = members.get(i);
            User user = userMapper.selectById(member.getUserId());
            String username = user != null ? user.getUsername() : "用户ID:" + member.getUserId();
            
            // 检查该组员是否在黑名单中
            if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
                log.warn("组员 {} ({}) 在黑名单中，跳过为其创建预约", username, member.getUserId());
                failedMembers.add(username + "(黑名单)");
                continue;
            }
            
            try {
                // 检查该用户是否已经有相同时间的预约（避免重复创建）
                List<Reservation> existing = personalReservationMapper.selectList(
                        new LambdaQueryWrapper<Reservation>()
                                .eq(Reservation::getUserId, member.getUserId())
                                .eq(Reservation::getSeatId, seatIds.get(i))
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                
                if (!existing.isEmpty()) {
                    log.info("用户 {} ({}) 已有相同时间的预约 ID: {}，跳过创建", 
                            username, member.getUserId(), existing.get(0).getId());
                    createdCount++;
                    successMembers.add(username);
                    continue;
                }
                
                if (personalReservationOverlapService.hasUserTimeOverlap(
                        member.getUserId(), gr.getStartTime(), gr.getEndTime(), null)) {
                    log.warn("用户 {} ({}) 在该时段已有其他座位的预约，无法加入本组协同座位", username, member.getUserId());
                    failedMembers.add(username + "(该时段已有其他预约)");
                    continue;
                }
                
                // 创建新预约
                Reservation reservation = new Reservation();
                reservation.setUserId(member.getUserId());
                reservation.setSeatId(seatIds.get(i));
                reservation.setStartTime(gr.getStartTime());
                reservation.setEndTime(gr.getEndTime());
                reservation.setStatus("CONFIRMED");
                reservation.setCreatedAt(LocalDateTime.now());
                
                personalReservationMapper.insert(reservation);
                if (reservation.getId() != null) {
                    createdCount++;
                    successMembers.add(username);
                    log.info("✅ 为用户 {} ({}, 角色: {}) 成功创建了个人预约 ID: {}, 座位ID: {}, 预约时间: {} - {}", 
                            username, member.getUserId(), member.getRole(), reservation.getId(), seatIds.get(i), gr.getStartTime(), gr.getEndTime());
                    
                    // 更新座位状态
                    try {
                        seatStatusService.onReservationCreated(seatIds.get(i));
                    } catch (Exception e) {
                        log.error("更新座位状态失败，座位ID: {}", seatIds.get(i), e);
                        // 座位状态更新失败不影响预约创建
                    }
                } else {
                    throw new RuntimeException("插入预约失败，未返回ID");
                }
            } catch (Exception e) {
                log.error("❌ 为用户 {} ({}) 创建个人预约失败，座位ID: {}, 错误: {}", 
                        username, member.getUserId(), seatIds.get(i), e.getMessage(), e);
                failedMembers.add(username + "(ID:" + member.getUserId() + ")");
            }
        }
        
        // 验证创建结果
        if (createdCount == 0) {
            log.error("❌ 没有为任何成员创建个人预约，成员总数: {}", members.size());
            boolean allPersonalTimeOverlap = !failedMembers.isEmpty()
                    && failedMembers.stream().allMatch(s -> s.contains("该时段已有其他预约"));
            if (allPersonalTimeOverlap) {
                throw BusinessException.conflict("该时间段已有其他预约座位，请取消预约原有预约后再协同预约");
            }
            throw BusinessException.internalError("创建个人预约失败，请重试");
        }

        if (createdCount < members.size()) {
            log.warn("只为 {}/{} 个成员创建了个人预约", createdCount, members.size());
            if (!failedMembers.isEmpty()) {
                log.warn("创建失败的成员：{}", String.join(", ", failedMembers));
            }
            log.warn("成功创建的成员：{}", String.join(", ", successMembers));
        } else {
            log.info("成功为所有 {} 个成员创建了个人预约", members.size());
        }

        if (createdCount > 0) {
            gr.setStatus("CONFIRMED");
            reservationMapper.updateById(gr);
            log.info("协同预约 {} 状态已更新为 CONFIRMED", reservationId);
        }

        log.info("组长 {} 确认了小组 {} 的协同预约 {}，为 {}/{} 个成员创建了个人预约",
                userId, groupId, reservationId, createdCount, members.size());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "预约已确认");
        response.put("createdCount", createdCount);
        response.put("totalMembers", members.size());

        if (createdCount < members.size()) {
            response.put("warning", "部分成员预约创建失败，请使用补充创建功能");
            response.put("failedMembers", failedMembers);

            List<Map<String, Object>> memberDetails = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                GroupMember member = members.get(i);
                User user = userMapper.selectById(member.getUserId());
                Map<String, Object> detail = new HashMap<>();
                detail.put("userId", member.getUserId());
                detail.put("username", user != null ? user.getUsername() : "未知");
                detail.put("role", member.getRole());

                List<Reservation> existing = personalReservationMapper.selectList(
                        new LambdaQueryWrapper<Reservation>()
                                .eq(Reservation::getUserId, member.getUserId())
                                .eq(Reservation::getSeatId, i < seatIds.size() ? seatIds.get(i) : null)
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));

                detail.put("hasReservation", !existing.isEmpty());
                if (!existing.isEmpty()) {
                    detail.put("reservationId", existing.get(0).getId());
                }
                memberDetails.add(detail);
            }
            response.put("memberDetails", memberDetails);
        }

        pushGroupChanged("group_reservation_confirmed", groupId);
        return response;
    }

    /**
     * 检查协同预约的成员预约创建情况（调试用）
     */
    public Map<String, Object> checkGroupReservationStatus(Long groupId, Long reservationId) {
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }

        GroupReservation gr = reservationMapper.selectById(reservationId);
        if (gr == null || !gr.getGroupId().equals(groupId)) {
            throw BusinessException.notFound("预约不存在");
        }

        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));

        List<Long> seatIds = parseSeatIds(gr.getSeatIds());

        List<Map<String, Object>> memberStatus = new ArrayList<>();
        for (int i = 0; i < members.size() && i < seatIds.size(); i++) {
            GroupMember member = members.get(i);
            User user = userMapper.selectById(member.getUserId());

            List<Reservation> reservations = personalReservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getUserId, member.getUserId())
                            .eq(Reservation::getSeatId, seatIds.get(i))
                            .eq(Reservation::getStartTime, gr.getStartTime())
                            .eq(Reservation::getEndTime, gr.getEndTime()));

            Map<String, Object> status = new HashMap<>();
            status.put("userId", member.getUserId());
            status.put("username", user != null ? user.getUsername() : "未知");
            status.put("role", member.getRole());
            status.put("seatId", seatIds.get(i));
            status.put("hasReservation", !reservations.isEmpty());
            if (!reservations.isEmpty()) {
                Reservation r = reservations.get(0);
                status.put("reservationId", r.getId());
                status.put("reservationStatus", r.getStatus());
            } else {
                status.put("reservationId", null);
                status.put("reservationStatus", "未创建");
            }
            memberStatus.add(status);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("groupReservationId", reservationId);
        result.put("groupReservationStatus", gr.getStatus());
        result.put("startTime", gr.getStartTime());
        result.put("endTime", gr.getEndTime());
        result.put("seatIds", seatIds);
        result.put("memberCount", members.size());
        result.put("memberStatus", memberStatus);
        return result;
    }

    /**
     * 补充创建缺失成员的个人预约
     * 用于修复之前确认预约时部分成员预约创建失败的情况
     */
    @Transactional
    public Map<String, Object> supplementGroupReservation(Long groupId, Long reservationId) {
        Long userId = securityUtil.currentUserId();
        
        // 只有组长可以操作
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        if (!group.getLeaderId().equals(userId)) {
            throw BusinessException.forbidden("只有组长可以操作");
        }
        
        GroupReservation gr = reservationMapper.selectById(reservationId);
        if (gr == null || !gr.getGroupId().equals(groupId)) {
            throw BusinessException.notFound("预约不存在");
        }
        
        if (!"CONFIRMED".equals(gr.getStatus())) {
            throw new BusinessException("只能为已确认的预约补充创建个人预约");
        }
        
        // 解析座位ID列表
        List<Long> seatIds = parseSeatIds(gr.getSeatIds());
        
        // 获取所有小组成员
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        
        // 确保组长在第一位
        members.sort((m1, m2) -> {
            if ("LEADER".equals(m1.getRole()) && !"LEADER".equals(m2.getRole())) {
                return -1;
            }
            if (!"LEADER".equals(m1.getRole()) && "LEADER".equals(m2.getRole())) {
                return 1;
            }
            return 0;
        });
        
        // 检查每个成员是否已有预约
        int supplementedCount = 0;
        List<String> supplementedMembers = new ArrayList<>();
        List<String> alreadyExists = new ArrayList<>();
        List<String> failedSupplement = new ArrayList<>();
        
        for (int i = 0; i < members.size() && i < seatIds.size(); i++) {
            GroupMember member = members.get(i);
            User user = userMapper.selectById(member.getUserId());
            String username = user != null ? user.getUsername() : "用户ID:" + member.getUserId();
            
            // 检查该组员是否在黑名单中
            if (user != null && Boolean.TRUE.equals(user.getIsBlacklisted())) {
                log.warn("组员 {} ({}) 在黑名单中，跳过补充预约", username, member.getUserId());
                failedSupplement.add(username + "(黑名单)");
                continue;
            }
            
            // 检查是否已有相同时间的预约
            // 注意：这里要检查所有状态，包括 FINISHED，因为可能之前创建过但已完成
            List<Reservation> existing = personalReservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getUserId, member.getUserId())
                            .eq(Reservation::getSeatId, seatIds.get(i))
                            .eq(Reservation::getStartTime, gr.getStartTime())
                            .eq(Reservation::getEndTime, gr.getEndTime()));
            
            // 检查是否有有效的预约（ACTIVE/CONFIRMED/PENDING）
            List<Reservation> validExisting = existing.stream()
                    .filter(r -> List.of("ACTIVE", "CONFIRMED", "PENDING").contains(r.getStatus()))
                    .collect(Collectors.toList());
            
            if (!validExisting.isEmpty()) {
                log.info("用户 {} ({}) 已有有效预约 ID: {}，状态: {}，跳过补充创建", 
                        username, member.getUserId(), validExisting.get(0).getId(), validExisting.get(0).getStatus());
                alreadyExists.add(username + "(ID:" + member.getUserId() + ", 预约ID:" + validExisting.get(0).getId() + ")");
                continue;
            }
            
            if (personalReservationOverlapService.hasUserTimeOverlap(
                    member.getUserId(), gr.getStartTime(), gr.getEndTime(), null)) {
                log.info("用户 {} ({}) 在该时段已有其他预约，跳过补充创建", username, member.getUserId());
                alreadyExists.add(username + "(该时段已有其他预约)");
                continue;
            }
            
            // 如果有已完成或取消的预约，也记录但不创建新的
            if (!existing.isEmpty()) {
                log.warn("用户 {} ({}) 有已完成的预约 ID: {}，状态: {}，将创建新预约", 
                        username, member.getUserId(), existing.get(0).getId(), existing.get(0).getStatus());
            }
            
            // 为缺失的成员创建预约
            try {
                Reservation reservation = new Reservation();
                reservation.setUserId(member.getUserId());
                reservation.setSeatId(seatIds.get(i));
                reservation.setStartTime(gr.getStartTime());
                reservation.setEndTime(gr.getEndTime());
                reservation.setStatus("CONFIRMED");
                reservation.setCreatedAt(LocalDateTime.now());
                
                int insertResult = personalReservationMapper.insert(reservation);
                if (insertResult > 0 && reservation.getId() != null) {
                    supplementedCount++;
                    supplementedMembers.add(username + "(ID:" + member.getUserId() + ", 预约ID:" + reservation.getId() + ")");
                    log.info("✅ 为用户 {} ({}) 补充创建了个人预约 ID: {}, 座位ID: {}, 预约时间: {} - {}", 
                            username, member.getUserId(), reservation.getId(), seatIds.get(i), gr.getStartTime(), gr.getEndTime());
                    
                    // 更新座位状态
                    try {
                        seatStatusService.onReservationCreated(seatIds.get(i));
                    } catch (Exception e) {
                        log.error("更新座位状态失败，座位ID: {}", seatIds.get(i), e);
                    }
                } else {
                    throw new RuntimeException("插入预约失败，返回结果: " + insertResult);
                }
            } catch (Exception e) {
                log.error("❌ 为用户 {} ({}) 补充创建个人预约失败，座位ID: {}, 错误: {}", 
                        username, member.getUserId(), seatIds.get(i), e.getMessage(), e);
                failedSupplement.add(username + "(ID:" + member.getUserId() + ")");
            }
        }
        
        String message = String.format("补充创建完成：为 %d 个成员创建了个人预约", supplementedCount);
        if (!alreadyExists.isEmpty()) {
            message += String.format("，%d 个成员已有预约", alreadyExists.size());
        }
        
        log.info("组长 {} 为小组 {} 的预约 {} 补充创建了 {} 个成员的个人预约，{} 个已有预约，{} 个失败", 
                userId, groupId, reservationId, supplementedCount, alreadyExists.size(), failedSupplement.size());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("supplementedCount", supplementedCount);
        response.put("supplementedMembers", supplementedMembers);
        response.put("alreadyExists", alreadyExists);
        response.put("failedCount", failedSupplement.size());
        response.put("failedMembers", failedSupplement);
        
        pushGroupChanged("group_reservation_supplemented", groupId);
        return response;
    }
    
    /**
     * 取消协同预约（只有组长可以操作）
     * 取消协同预约时，会自动取消所有组员的个人预约
     */
    @Transactional
    public Map<String, Object> cancelGroupReservation(Long groupId, Long reservationId) {
        Long userId = securityUtil.currentUserId();
        
        // 只有组长可以取消协同预约
        StudyGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw BusinessException.notFound("小组不存在");
        }
        if (!group.getLeaderId().equals(userId)) {
            throw BusinessException.forbidden("只有组长可以取消协同预约");
        }
        
        GroupReservation gr = reservationMapper.selectById(reservationId);
        if (gr == null || !gr.getGroupId().equals(groupId)) {
            throw BusinessException.notFound("协同预约不存在");
        }
        
        // 只能取消 PENDING 或 CONFIRMED 状态的预约
        if (!List.of("PENDING", "CONFIRMED").contains(gr.getStatus())) {
            throw new BusinessException(
                    "只能取消待确认或已确认的协同预约，当前状态：" + gr.getStatus());
        }
        
        // 解析座位ID列表
        List<Long> seatIds = parseSeatIds(gr.getSeatIds());
        
        // 获取所有小组成员
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));
        
        // 取消所有组员的个人预约（如果协同预约已确认，组员应该有个人预约）
        // 注意：需要同时取消组长和所有成员的个人预约
        int cancelledCount = 0;
        List<String> cancelledMembers = new ArrayList<>();
        List<String> notFoundMembers = new ArrayList<>();
        
        if ("CONFIRMED".equals(gr.getStatus())) {
            // 只有已确认的协同预约才有组员的个人预约需要取消
            // 首先取消组长的个人预约（组长可能不在成员列表中）
            for (int i = 0; i < seatIds.size(); i++) {
                // 查找组长的个人预约
                List<Reservation> leaderReservations = personalReservationMapper.selectList(
                        new LambdaQueryWrapper<Reservation>()
                                .eq(Reservation::getUserId, userId)
                                .eq(Reservation::getSeatId, seatIds.get(i))
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                
                if (!leaderReservations.isEmpty()) {
                    for (Reservation r : leaderReservations) {
                        r.setStatus("CANCELLED");
                        personalReservationMapper.updateById(r);
                        cancelledCount++;
                        log.info("✅ 已取消组长 {} 的个人预约 ID: {}", userId, r.getId());
                    }
                }
            }
            
            // 然后取消所有成员的个人预约
            for (int i = 0; i < members.size() && i < seatIds.size(); i++) {
                GroupMember member = members.get(i);
                // 跳过组长（已经在上面处理了）
                if (member.getUserId().equals(userId)) {
                    continue;
                }
                
                User user = userMapper.selectById(member.getUserId());
                String username = user != null ? user.getUsername() : "用户ID:" + member.getUserId();
                
                // 查找该成员的个人预约（匹配座位、开始时间、结束时间）
                List<Reservation> memberReservations = personalReservationMapper.selectList(
                        new LambdaQueryWrapper<Reservation>()
                                .eq(Reservation::getUserId, member.getUserId())
                                .eq(Reservation::getSeatId, seatIds.get(i))
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                
                if (!memberReservations.isEmpty()) {
                    for (Reservation r : memberReservations) {
                        r.setStatus("CANCELLED");
                        personalReservationMapper.updateById(r);
                        cancelledCount++;
                        log.info("✅ 已取消成员 {} ({}) 的个人预约 ID: {}", username, member.getUserId(), r.getId());
                    }
                    cancelledMembers.add(username);
                } else {
                    log.warn("⚠️ 成员 {} ({}) 没有找到对应的个人预约（座位ID: {}, 时间: {} - {}）", 
                            username, member.getUserId(), seatIds.get(i), gr.getStartTime(), gr.getEndTime());
                    notFoundMembers.add(username);
                }
            }
        } else if ("PENDING".equals(gr.getStatus())) {
            // 对于 PENDING 状态的协同预约，虽然可能还没有创建个人预约
            // 但为了安全起见，也检查并取消可能存在的个人预约
            // 取消组长的个人预约
            for (int i = 0; i < seatIds.size(); i++) {
                List<Reservation> leaderReservations = personalReservationMapper.selectList(
                        new LambdaQueryWrapper<Reservation>()
                                .eq(Reservation::getUserId, userId)
                                .eq(Reservation::getSeatId, seatIds.get(i))
                                .eq(Reservation::getStartTime, gr.getStartTime())
                                .eq(Reservation::getEndTime, gr.getEndTime())
                                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
                
                if (!leaderReservations.isEmpty()) {
                    for (Reservation r : leaderReservations) {
                        r.setStatus("CANCELLED");
                        personalReservationMapper.updateById(r);
                        cancelledCount++;
                        log.info("✅ 已取消组长 {} 的个人预约 ID: {}（PENDING状态的协同预约）", userId, r.getId());
                    }
                }
            }
        }
        
        // 取消协同预约
        gr.setStatus("CANCELLED");
        reservationMapper.updateById(gr);
        
        log.info("组长 {} 取消了小组 {} 的协同预约 {}，已取消 {} 个成员的个人预约", 
                userId, groupId, reservationId, cancelledCount);
        
        // 给所有组员发送取消预约通知
        User leader = userMapper.selectById(userId);
        String leaderName = leader != null ? leader.getUsername() : "组长";
        // 格式化座位ID显示（将逗号分隔的ID转换为可读格式）
        List<Long> seatIdList = parseSeatIds(gr.getSeatIds());
        String seatIdsStr = seatIdList.stream()
                .map(id -> "座位" + id)
                .collect(Collectors.joining(", "));
        
        for (GroupMember m : members) {
            if (!m.getUserId().equals(userId)) { // 不给组长自己发通知
                String content = String.format("组长 %s 取消了自习小组 %s 的协同预约（座位: %s, 时间: %s - %s）",
                        leaderName, group.getName(), seatIdsStr, gr.getStartTime(), gr.getEndTime());
                
                GroupNotification notification = new GroupNotification();
                notification.setUserId(m.getUserId());
                notification.setGroupId(groupId);
                notification.setType("RESERVATION_CANCELLED");
                notification.setContent(content);
                notification.setIsRead(false);
                notification.setCreatedAt(LocalDateTime.now());
                notificationMapper.insert(notification);
                
                log.info("✅ 已给组员 {} 发送取消协同预约通知", m.getUserId());
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "协同预约已取消");
        response.put("cancelledCount", cancelledCount);
        response.put("cancelledMembers", cancelledMembers);
        if (!notFoundMembers.isEmpty()) {
            response.put("notFoundMembers", notFoundMembers);
            response.put("warning", String.format("%d 个成员没有找到对应的个人预约", notFoundMembers.size()));
        }
        
        pushGroupChanged("group_reservation_cancelled", groupId);
        return response;
    }

    public List<Map<String, Object>> getNotifications() {
        Long userId = securityUtil.currentUserId();
        List<GroupNotification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<GroupNotification>()
                        .eq(GroupNotification::getUserId, userId)
                        .orderByDesc(GroupNotification::getCreatedAt));
        
        return notifications.stream().map(notif -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", notif.getId());
            result.put("groupId", notif.getGroupId());
            result.put("type", notif.getType());
            result.put("content", notif.getContent());
            result.put("isRead", notif.getIsRead());
            result.put("createdAt", notif.getCreatedAt());
            
            // 获取小组信息
            StudyGroup group = groupMapper.selectById(notif.getGroupId());
            result.put("groupName", group != null ? group.getName() : "未知小组");
            
            return result;
        }).collect(Collectors.toList());
    }
    
    public Map<String, Object> getUnreadCount() {
        try {
            Long userId = securityUtil.currentUserId();
            long count = notificationMapper.selectCount(
                    new LambdaQueryWrapper<GroupNotification>()
                            .eq(GroupNotification::getUserId, userId)
                            .eq(GroupNotification::getIsRead, false));
            return Map.of("count", count);
        } catch (IllegalStateException e) {
            log.warn("获取未读通知数量时用户未登录: {}", e.getMessage());
            return Map.of("count", 0);
        } catch (Exception e) {
            log.error("获取未读通知数量失败", e);
            return Map.of("count", 0);
        }
    }
    
    public Map<String, Object> markAsRead(Long notificationId) {
        Long userId = securityUtil.currentUserId();
        GroupNotification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw BusinessException.notFound("通知不存在");
        }
        if (!notification.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权操作此通知");
        }
        
        notification.setIsRead(true);
        notificationMapper.updateById(notification);
        return Map.of("message", "已标记为已读");
    }

    public Map<String, Object> markAllAsRead() {
        Long userId = securityUtil.currentUserId();
        int updated = notificationMapper.update(
                null,
                new UpdateWrapper<GroupNotification>()
                        .set("is_read", true)
                        .eq("user_id", userId)
                        .eq("is_read", false)
        );
        return Map.of("message", "已全部标记为已读", "updated", updated);
    }
//封装广播方法
    private void pushGroupChanged(String action, Long groupId) {
        try {
            seatStatusWebSocketHandler.broadcastGroupChanged(action, groupId);
        } catch (Exception e) {
            log.warn("广播 groupChanged 事件失败: action={}, groupId={}, err={}", action, groupId, e.getMessage());
        }
    }

    /**
     * 解析座位ID字符串为列表
     */
    private List<Long> parseSeatIds(String seatIdsStr) {
        if (seatIdsStr == null || seatIdsStr.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return java.util.Arrays.stream(seatIdsStr.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
