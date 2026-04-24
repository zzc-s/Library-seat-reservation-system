package com.example.libraryseat.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.entity.AttendanceLog;
import com.example.libraryseat.attendance.mapper.AttendanceLogMapper;
import com.example.libraryseat.book.entity.SeatBookLink;
import com.example.libraryseat.book.mapper.SeatBookLinkMapper;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import com.example.libraryseat.feedback.entity.Feedback;
import com.example.libraryseat.feedback.mapper.FeedbackMapper;
import com.example.libraryseat.group.entity.GroupJoinRequest;
import com.example.libraryseat.group.entity.GroupMember;
import com.example.libraryseat.group.entity.GroupNotification;
import com.example.libraryseat.group.entity.StudyGroup;
import com.example.libraryseat.group.mapper.GroupJoinRequestMapper;
import com.example.libraryseat.group.mapper.GroupMemberMapper;
import com.example.libraryseat.group.mapper.GroupNotificationMapper;
import com.example.libraryseat.group.mapper.StudyGroupMapper;
import com.example.libraryseat.notification.entity.UserNotification;
import com.example.libraryseat.notification.mapper.UserNotificationMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.violation.entity.Violation;
import com.example.libraryseat.violation.mapper.ViolationMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 删除普通用户时的校验与级联清理（管理端删除与自助注销共用）
 */
@Service
public class UserDeletionService {

    private final ReservationMapper reservationMapper;
    private final AttendanceLogMapper attendanceLogMapper;
    private final ViolationMapper violationMapper;
    private final StudyGroupMapper studyGroupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupNotificationMapper groupNotificationMapper;
    private final FeedbackMapper feedbackMapper;
    private final BorrowMapper borrowMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final SeatBookLinkMapper seatBookLinkMapper;
    private final UserMapper userMapper;

    public UserDeletionService(
            ReservationMapper reservationMapper,
            AttendanceLogMapper attendanceLogMapper,
            ViolationMapper violationMapper,
            StudyGroupMapper studyGroupMapper,
            GroupMemberMapper groupMemberMapper,
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupNotificationMapper groupNotificationMapper,
            FeedbackMapper feedbackMapper,
            BorrowMapper borrowMapper,
            UserNotificationMapper userNotificationMapper,
            SeatBookLinkMapper seatBookLinkMapper,
            UserMapper userMapper) {
        this.reservationMapper = reservationMapper;
        this.attendanceLogMapper = attendanceLogMapper;
        this.violationMapper = violationMapper;
        this.studyGroupMapper = studyGroupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupNotificationMapper = groupNotificationMapper;
        this.feedbackMapper = feedbackMapper;
        this.borrowMapper = borrowMapper;
        this.userNotificationMapper = userNotificationMapper;
        this.seatBookLinkMapper = seatBookLinkMapper;
        this.userMapper = userMapper;
    }

    /**
     * 与管理员删除用户相同的「未完成数据」检查：有任一项则不可删账号。
     */
    public List<String> collectDeletionBlockers(long userId) {
        List<String> relatedData = new ArrayList<>();
        long activeReservationCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .notIn(Reservation::getStatus, List.of("FINISHED", "CANCELLED")));
        if (activeReservationCount > 0) {
            relatedData.add("进行中的预约记录(" + activeReservationCount + "条)");
        }
        long activeBorrowCount = borrowMapper.selectCount(
                new LambdaQueryWrapper<Borrow>()
                        .eq(Borrow::getUserId, userId)
                        .ne(Borrow::getStatus, "RETURNED"));
        if (activeBorrowCount > 0) {
            relatedData.add("未归还的借阅记录(" + activeBorrowCount + "条)");
        }
        long groupLeaderCount = studyGroupMapper.selectCount(
                new LambdaQueryWrapper<StudyGroup>().eq(StudyGroup::getLeaderId, userId));
        if (groupLeaderCount > 0) {
            relatedData.add("创建的学习小组(" + groupLeaderCount + "个，需先删除或转移组长)");
        }
        return relatedData;
    }

    /**
     * 清理与用户相关的历史数据并删除 user 行（调用方已保证可删条件）。
     */
    public ResponseEntity<?> purgeRelatedDataAndDeleteUser(long userId) {
        try {
            List<Long> reservationIdsToRemove = reservationMapper.selectList(
                            new LambdaQueryWrapper<Reservation>()
                                    .eq(Reservation::getUserId, userId)
                                    .in(Reservation::getStatus, List.of("FINISHED", "CANCELLED")))
                    .stream().map(Reservation::getId).toList();

            if (!reservationIdsToRemove.isEmpty()) {
                seatBookLinkMapper.delete(
                        new LambdaQueryWrapper<SeatBookLink>()
                                .in(SeatBookLink::getReservationId, reservationIdsToRemove));
                attendanceLogMapper.delete(
                        new LambdaQueryWrapper<AttendanceLog>()
                                .in(AttendanceLog::getReservationId, reservationIdsToRemove));
                violationMapper.delete(
                        new LambdaQueryWrapper<Violation>()
                                .in(Violation::getReservationId, reservationIdsToRemove));
            }

            attendanceLogMapper.delete(
                    new LambdaQueryWrapper<AttendanceLog>().eq(AttendanceLog::getUserId, userId));
            violationMapper.delete(
                    new LambdaQueryWrapper<Violation>().eq(Violation::getUserId, userId));

            reservationMapper.delete(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getUserId, userId)
                            .in(Reservation::getStatus, List.of("FINISHED", "CANCELLED")));

            borrowMapper.delete(
                    new LambdaQueryWrapper<Borrow>()
                            .eq(Borrow::getUserId, userId)
                            .eq(Borrow::getStatus, "RETURNED"));

            groupMemberMapper.delete(
                    new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getUserId, userId));
            groupJoinRequestMapper.delete(
                    new LambdaQueryWrapper<GroupJoinRequest>().eq(GroupJoinRequest::getUserId, userId));
            feedbackMapper.delete(
                    new LambdaQueryWrapper<Feedback>().eq(Feedback::getUserId, userId));
            userNotificationMapper.delete(
                    new LambdaQueryWrapper<UserNotification>()
                            .eq(UserNotification::getUserId, userId));
            groupNotificationMapper.delete(
                    new LambdaQueryWrapper<GroupNotification>()
                            .eq(GroupNotification::getUserId, userId));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message",
                    "删除失败：清理用户相关数据时出错，请查看后端日志"));
        }

        try {
            int deleted = userMapper.deleteById(userId);
            if (deleted > 0) {
                return ResponseEntity.ok(Map.of("message", "用户已删除"));
            }
            return ResponseEntity.badRequest().body(Map.of("message", "删除失败：用户不存在或已被删除"));
        } catch (DataIntegrityViolationException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message",
                    "删除失败：该用户存在关联数据，无法删除。请先清理相关数据后再删除用户。"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message",
                    "删除失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误")));
        }
    }
}
