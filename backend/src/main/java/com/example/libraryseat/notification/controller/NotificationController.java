package com.example.libraryseat.notification.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.libraryseat.notification.entity.UserNotification;
import com.example.libraryseat.notification.mapper.UserNotificationMapper;
import com.example.libraryseat.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "系统通知接口", description = "用户通知列表、未读数量与已读管理")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final UserNotificationMapper notificationMapper;
    private final SecurityUtil securityUtil;

    public NotificationController(UserNotificationMapper notificationMapper, SecurityUtil securityUtil) {
        this.notificationMapper = notificationMapper;
        this.securityUtil = securityUtil;
    }

    /**
     * 获取当前用户的通知列表
     */
    @Operation(summary = "获取当前用户的通知列表")
    @GetMapping
    public List<UserNotification> getMyNotifications(
            @RequestParam(required = false) Boolean unreadOnly) {
        Long userId = securityUtil.currentUserId();
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .orderByDesc(UserNotification::getCreatedAt);
        
        if (unreadOnly != null && unreadOnly) {
            wrapper.eq(UserNotification::getIsRead, false);
        }
        
        return notificationMapper.selectList(wrapper);
    }

    /**
     * 获取未读通知数量
     */
    @Operation(summary = "获取未读通知数量")
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount() {
        Long userId = securityUtil.currentUserId();
        long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, false));
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 标记通知为已读
     */
    @Operation(summary = "将单条通知标记为已读")
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        Long userId = securityUtil.currentUserId();
        
        UserNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 确保只能标记自己的通知
        if (!notification.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权操作"));
        }
        
        notification.setIsRead(true);
        notificationMapper.updateById(notification);
        
        log.info("用户 {} 标记通知 {} 为已读", userId, id);
        return ResponseEntity.ok(Map.of("message", "已标记为已读"));
    }

    /**
     * 标记所有通知为已读
     */
    @Operation(summary = "将所有通知标记为已读")
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllAsRead() {
        Long userId = securityUtil.currentUserId();
        
        LambdaUpdateWrapper<UserNotification> wrapper = new LambdaUpdateWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getIsRead, false)
                .set(UserNotification::getIsRead, true);
        
        notificationMapper.update(null, wrapper);
        
        log.info("用户 {} 标记所有通知为已读", userId);
        return ResponseEntity.ok(Map.of("message", "已标记所有通知为已读"));
    }

    /**
     * 删除通知
     */
    @Operation(summary = "删除一条通知")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id) {
        Long userId = securityUtil.currentUserId();
        
        UserNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 确保只能删除自己的通知
        if (!notification.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "无权操作"));
        }
        
        notificationMapper.deleteById(id);
        
        log.info("用户 {} 删除了通知 {}", userId, id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

}
