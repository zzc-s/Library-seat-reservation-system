package com.example.libraryseat.group.controller;

import com.example.libraryseat.group.entity.GroupReservation;
import com.example.libraryseat.group.service.StudyGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "学习小组与 协同预约接口", description = "小组创建、加入审批、协同预约及通知")
@RestController
@RequestMapping("/api/groups")
public class StudyGroupController {

    private final StudyGroupService studyGroupService;

    public StudyGroupController(StudyGroupService studyGroupService) {
        this.studyGroupService = studyGroupService;
    }

    @Operation(summary = "创建学习小组")
    @PostMapping
    public Map<String, Object> createGroup(@RequestBody Map<String, Object> req) {
        return studyGroupService.createGroup(req);
    }

    @Operation(summary = "发布学习小组")
    @PostMapping("/{groupId}/publish")
    public Map<String, Object> publishGroup(@PathVariable Long groupId, @RequestBody Map<String, Object> req) {
        return studyGroupService.publishGroup(groupId, req);
    }

    @Operation(summary = "删除学习小组")
    @DeleteMapping("/{groupId}")
    public Map<String, Object> deleteGroup(@PathVariable Long groupId) {
        return studyGroupService.deleteGroup(groupId);
    }

    @Operation(summary = "获取我的学习小组列表")
    @GetMapping
    public List<Map<String, Object>> myGroups() {
        return studyGroupService.myGroups();
    }

    @Operation(summary = "申请加入学习小组")
    @PostMapping("/{groupId}/join")
    public Map<String, Object> requestJoinGroup(@PathVariable Long groupId) {
        return studyGroupService.requestJoinGroup(groupId);
    }

    @Operation(summary = "获取小组入组申请列表")
    @GetMapping("/{groupId}/join-requests")
    public List<Map<String, Object>> getJoinRequests(@PathVariable Long groupId) {
        return studyGroupService.getJoinRequests(groupId);
    }

    @Operation(summary = "批准入组申请")
    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public Map<String, Object> approveJoinRequest(@PathVariable Long groupId, @PathVariable Long requestId) {
        return studyGroupService.approveJoinRequest(groupId, requestId);
    }

    @Operation(summary = "拒绝入组申请")
    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public Map<String, Object> rejectJoinRequest(@PathVariable Long groupId, @PathVariable Long requestId) {
        return studyGroupService.rejectJoinRequest(groupId, requestId);
    }

    @Operation(summary = "获取我的入组申请记录")
    @GetMapping("/my-join-requests")
    public List<Map<String, Object>> getMyJoinRequests() {
        return studyGroupService.getMyJoinRequests();
    }

    @Operation(summary = "创建小组协同预约")
    @PostMapping("/{groupId}/reservations")
    public GroupReservation createGroupReservation(@PathVariable Long groupId,
                                                    @RequestBody Map<String, Object> req) {
        return studyGroupService.createGroupReservation(groupId, req);
    }

    @Operation(summary = "获取小组成员列表")
    @GetMapping("/{groupId}/members")
    public List<Map<String, Object>> getGroupMembers(@PathVariable Long groupId) {
        return studyGroupService.getGroupMembers(groupId);
    }

    @Operation(summary = "获取小组预约列表")
    @GetMapping("/{groupId}/reservations")
    public List<GroupReservation> getGroupReservations(@PathVariable Long groupId) {
        return studyGroupService.getGroupReservations(groupId);
    }

    @Operation(summary = "确认小组协同预约")
    @PostMapping("/{groupId}/reservations/{reservationId}/confirm")
    public Map<String, Object> confirmGroupReservation(@PathVariable Long groupId, @PathVariable Long reservationId) {
        return studyGroupService.confirmGroupReservation(groupId, reservationId);
    }

    @Operation(summary = "检查小组预约状态")
    @GetMapping("/{groupId}/reservations/{reservationId}/check")
    public Map<String, Object> checkGroupReservationStatus(@PathVariable Long groupId, @PathVariable Long reservationId) {
        return studyGroupService.checkGroupReservationStatus(groupId, reservationId);
    }

    @Operation(summary = "补充小组预约信息")
    @PostMapping("/{groupId}/reservations/{reservationId}/supplement")
    public Map<String, Object> supplementGroupReservation(@PathVariable Long groupId, @PathVariable Long reservationId) {
        return studyGroupService.supplementGroupReservation(groupId, reservationId);
    }

    @Operation(summary = "取消小组协同预约")
    @PostMapping("/{groupId}/reservations/{reservationId}/cancel")
    public Map<String, Object> cancelGroupReservation(@PathVariable Long groupId, @PathVariable Long reservationId) {
        return studyGroupService.cancelGroupReservation(groupId, reservationId);
    }

    @Operation(summary = "获取小组通知列表")
    @GetMapping("/notifications")
    public List<Map<String, Object>> getNotifications() {
        return studyGroupService.getNotifications();
    }

    @Operation(summary = "获取未读通知数量")
    @GetMapping("/notifications/unread-count")
    public Map<String, Object> getUnreadCount() {
        return studyGroupService.getUnreadCount();
    }

    @Operation(summary = "标记通知为已读")
    @PostMapping("/notifications/{notificationId}/read")
    public Map<String, Object> markAsRead(@PathVariable Long notificationId) {
        return studyGroupService.markAsRead(notificationId);
    }

    @Operation(summary = "全部标记为已读")
    @PostMapping("/notifications/read-all")
    public Map<String, Object> markAllAsRead() {
        return studyGroupService.markAllAsRead();
    }
}
