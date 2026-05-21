package com.example.libraryseat.reservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.group.entity.GroupMember;
import com.example.libraryseat.group.entity.GroupNotification;
import com.example.libraryseat.group.entity.GroupReservation;
import com.example.libraryseat.group.entity.StudyGroup;
import com.example.libraryseat.group.mapper.GroupMemberMapper;
import com.example.libraryseat.group.mapper.GroupNotificationMapper;
import com.example.libraryseat.group.mapper.GroupReservationMapper;
import com.example.libraryseat.group.mapper.StudyGroupMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.websocket.SeatStatusWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReservationGroupNotifyService {

    private final GroupReservationMapper groupReservationMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupNotificationMapper groupNotificationMapper;
    private final StudyGroupMapper studyGroupMapper;
    private final UserMapper userMapper;
    private final ReservationMapper reservationMapper;
    private final SeatStatusWebSocketHandler seatStatusWebSocketHandler;

    public ReservationGroupNotifyService(GroupReservationMapper groupReservationMapper,
                                         GroupMemberMapper groupMemberMapper,
                                         GroupNotificationMapper groupNotificationMapper,
                                         StudyGroupMapper studyGroupMapper,
                                         UserMapper userMapper,
                                         ReservationMapper reservationMapper,
                                         SeatStatusWebSocketHandler seatStatusWebSocketHandler) {
        this.groupReservationMapper = groupReservationMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupNotificationMapper = groupNotificationMapper;
        this.studyGroupMapper = studyGroupMapper;
        this.userMapper = userMapper;
        this.reservationMapper = reservationMapper;
        this.seatStatusWebSocketHandler = seatStatusWebSocketHandler;
    }

    public void notifyGroupOnCancel(Reservation r, Long currentUserId) {
        try {
            List<GroupReservation> matchingGroupReservations = groupReservationMapper.selectList(
                    new LambdaQueryWrapper<GroupReservation>()
                            .eq(GroupReservation::getStartTime, r.getStartTime())
                            .eq(GroupReservation::getEndTime, r.getEndTime())
                            .in(GroupReservation::getStatus, List.of("PENDING", "CONFIRMED", "CANCELLED")));

            for (GroupReservation gr : matchingGroupReservations) {
                if (gr.getSeatIds() == null || gr.getSeatIds().trim().isEmpty()) {
                    continue;
                }
                List<Long> seatIds = parseSeatIds(gr.getSeatIds());
                if (!seatIds.contains(r.getSeatId())) {
                    continue;
                }

                StudyGroup group = studyGroupMapper.selectById(gr.getGroupId());
                if (group == null) {
                    continue;
                }

                GroupMember member = groupMemberMapper.selectOne(
                        new LambdaQueryWrapper<GroupMember>()
                                .eq(GroupMember::getGroupId, gr.getGroupId())
                                .eq(GroupMember::getUserId, currentUserId));

                if (member != null) {
                    User currentUser = userMapper.selectById(currentUserId);
                    String username = currentUser != null ? currentUser.getUsername() : "用户ID:" + currentUserId;
                    String content = String.format("组员 %s 取消了自习小组 %s 的预约（座位ID: %d, 时间: %s - %s）",
                            username, group.getName(), r.getSeatId(), r.getStartTime(), r.getEndTime());
                    insertNotification(group.getLeaderId(), gr.getGroupId(), content);
                    log.info("已给组长 {} 发送取消预约通知（组员 {} 取消了预约 {}）",
                            group.getLeaderId(), username, r.getId());
                } else if (group.getLeaderId().equals(currentUserId)) {
                    List<GroupMember> allMembers = groupMemberMapper.selectList(
                            new LambdaQueryWrapper<GroupMember>()
                                    .eq(GroupMember::getGroupId, gr.getGroupId()));

                    User leader = userMapper.selectById(currentUserId);
                    String leaderName = leader != null ? leader.getUsername() : "组长";

                    for (GroupMember m : allMembers) {
                        if (!m.getUserId().equals(currentUserId)) {
                            String content = String.format("组长 %s 取消了自习小组 %s 的预约（座位ID: %d, 时间: %s - %s）",
                                    leaderName, group.getName(), r.getSeatId(), r.getStartTime(), r.getEndTime());
                            insertNotification(m.getUserId(), gr.getGroupId(), content);
                        }
                    }
                    log.info("已给所有组员发送取消预约通知（组长 {} 取消了预约 {}）", leaderName, r.getId());
                }
                break;
            }

            for (GroupReservation gr : matchingGroupReservations) {
                autoCancelGroupReservationIfAllPersonalInactive(gr);
            }
        } catch (Exception e) {
            log.error("发送取消预约通知失败", e);
        }
    }

    private void insertNotification(Long userId, Long groupId, String content) {
        GroupNotification notification = new GroupNotification();
        notification.setUserId(userId);
        notification.setGroupId(groupId);
        notification.setType("RESERVATION_CANCELLED");
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        groupNotificationMapper.insert(notification);
    }

    private void autoCancelGroupReservationIfAllPersonalInactive(GroupReservation gr) {
        if (gr == null) {
            return;
        }
        if (!List.of("PENDING", "CONFIRMED").contains(gr.getStatus())) {
            return;
        }
        if (gr.getSeatIds() == null || gr.getSeatIds().trim().isEmpty()) {
            return;
        }

        List<Long> seatIds = parseSeatIds(gr.getSeatIds());
        if (seatIds.isEmpty()) {
            return;
        }

        long activePersonalCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getSeatId, seatIds)
                        .eq(Reservation::getStartTime, gr.getStartTime())
                        .eq(Reservation::getEndTime, gr.getEndTime())
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING")));
        if (activePersonalCount > 0) {
            return;
        }

        gr.setStatus("CANCELLED");
        groupReservationMapper.updateById(gr);
        log.info("协同预约 {} 无有效个人预约，已自动置为 CANCELLED（groupId={}）", gr.getId(), gr.getGroupId());
        try {
            seatStatusWebSocketHandler.broadcastGroupChanged("group_reservation_auto_cancelled", gr.getGroupId());
        } catch (Exception e) {
            log.warn("广播自动取消协同预约事件失败: groupId={}, reservationId={}, err={}",
                    gr.getGroupId(), gr.getId(), e.getMessage());
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
