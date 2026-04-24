package com.example.libraryseat.feedback.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.feedback.entity.Feedback;
import com.example.libraryseat.feedback.mapper.FeedbackMapper;
import com.example.libraryseat.notification.service.FeedbackAdminNotificationService;
import com.example.libraryseat.security.EmailService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.websocket.FeedbackWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "反馈与建议接口", description = "用户提交反馈、查看反馈及管理员回复处理")
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {
    
    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final FeedbackWebSocketHandler feedbackWebSocketHandler;
    private final FeedbackAdminNotificationService feedbackAdminNotificationService;
    
    public FeedbackController(FeedbackMapper feedbackMapper, UserMapper userMapper, 
                             EmailService emailService, FeedbackWebSocketHandler feedbackWebSocketHandler,
                             FeedbackAdminNotificationService feedbackAdminNotificationService) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.emailService = emailService;
        this.feedbackWebSocketHandler = feedbackWebSocketHandler;
        this.feedbackAdminNotificationService = feedbackAdminNotificationService;
    }
    
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String username = auth.getName();
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null) {
                return user.getId();
            }
        }
        throw new IllegalStateException("无法获取当前用户ID");
    }
    
    // 获取当前用户的反馈
    @Operation(summary = "获取当前用户的反馈列表")
    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyFeedbacks() {
        Long userId = currentUserId();
        List<Feedback> feedbacks = feedbackMapper.selectList(
            new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getCreatedAt)
        );
        
        List<Map<String, Object>> result = feedbacks.stream().map(feedback -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", feedback.getId());
            item.put("content", feedback.getContent());
            item.put("type", feedback.getType());
            item.put("status", feedback.getStatus());
            item.put("adminReply", feedback.getAdminReply());
            item.put("userReply", feedback.getUserReply());
            item.put("isPrivate", feedback.getIsPrivate() != null ? feedback.getIsPrivate() : false);
            item.put("createdAt", feedback.getCreatedAt());
            item.put("updatedAt", feedback.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // 创建反馈
    @Operation(summary = "提交反馈")
    @PostMapping
    public ResponseEntity<?> createFeedback(@RequestBody Map<String, Object> req) {
        Long userId = currentUserId();
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent((String) req.get("content"));
        feedback.setType((String) req.getOrDefault("type", "OTHER"));
        // 处理隐私设置，默认为false（公开）
        Object isPrivateObj = req.get("isPrivate");
        if (isPrivateObj instanceof Boolean) {
            feedback.setIsPrivate((Boolean) isPrivateObj);
        } else if (isPrivateObj instanceof String) {
            feedback.setIsPrivate(Boolean.parseBoolean((String) isPrivateObj));
        } else {
            feedback.setIsPrivate(false); // 默认公开
        }
        feedback.setStatus("PENDING");
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        
        // 通过 WebSocket 通知管理员有新反馈
        User user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : "用户ID:" + userId;
        feedbackWebSocketHandler.broadcastNewFeedback(
            feedback.getId(), 
            username, 
            feedback.getContent(), 
            feedback.getType()
        );
        
        try {
            feedbackAdminNotificationService.notifyAdminsNewFeedback(
                    feedback.getId(), username, feedback.getContent(), feedback.getType());
        } catch (Exception e) {
            log.warn("写入管理员站内通知失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(Map.of("message", "反馈提交成功"));
    }
    
    // 获取公开反馈（所有用户可见，包括公开反馈和当前用户自己的私有反馈）
    @Operation(summary = "获取公开反馈列表")
    @GetMapping("/public")
    public ResponseEntity<List<Map<String, Object>>> getPublicFeedbacks() {
        Long currentUserId = null;
        try {
            currentUserId = currentUserId();
        } catch (IllegalStateException e) {
            // 用户未登录，只能查看公开反馈
            currentUserId = null;
        }
        
        // 创建final变量供lambda表达式使用
        final Long finalCurrentUserId = currentUserId;
        
        LambdaQueryWrapper<Feedback> queryWrapper = new LambdaQueryWrapper<Feedback>();
        if (finalCurrentUserId != null) {
            // 已登录：显示公开反馈 + 当前用户自己的反馈（包括私有）
            queryWrapper.and(wrapper -> wrapper
                .eq(Feedback::getIsPrivate, false)  // 公开反馈
                .or()
                .eq(Feedback::getUserId, finalCurrentUserId)  // 或当前用户自己的反馈（包括私有）
            );
        } else {
            // 未登录：只显示公开反馈
            queryWrapper.eq(Feedback::getIsPrivate, false);
        }
        queryWrapper.orderByDesc(Feedback::getCreatedAt);
        
        List<Feedback> feedbacks = feedbackMapper.selectList(queryWrapper);
        
        List<Map<String, Object>> result = feedbacks.stream().map(feedback -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", feedback.getId());
            item.put("userId", feedback.getUserId());
            User user = userMapper.selectById(feedback.getUserId());
            if (user != null) {
                item.put("username", user.getUsername());
                item.put("avatarUrl", user.getAvatarUrl());
            }
            item.put("content", feedback.getContent());
            item.put("type", feedback.getType());
            item.put("status", feedback.getStatus());
            item.put("adminReply", feedback.getAdminReply());
            item.put("isPrivate", feedback.getIsPrivate() != null ? feedback.getIsPrivate() : false);
            item.put("createdAt", feedback.getCreatedAt());
            item.put("updatedAt", feedback.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // 获取所有反馈（管理员）
    @Operation(summary = "获取所有反馈（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllFeedbacks() {
        List<Feedback> feedbacks = feedbackMapper.selectList(
            new LambdaQueryWrapper<Feedback>()
                .orderByDesc(Feedback::getCreatedAt)
        );
        
        List<Map<String, Object>> result = feedbacks.stream().map(feedback -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", feedback.getId());
            item.put("userId", feedback.getUserId());
            User user = userMapper.selectById(feedback.getUserId());
            if (user != null) {
                item.put("username", user.getUsername());
            }
            item.put("content", feedback.getContent());
            item.put("type", feedback.getType());
            item.put("status", feedback.getStatus());
            item.put("adminReply", feedback.getAdminReply());
            item.put("userReply", feedback.getUserReply());
            item.put("isPrivate", feedback.getIsPrivate() != null ? feedback.getIsPrivate() : false);
            item.put("createdAt", feedback.getCreatedAt());
            item.put("updatedAt", feedback.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // 管理员回复反馈
    @Operation(summary = "管理员回复反馈")
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> replyFeedback(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        String adminReply = (String) req.get("adminReply");
        if (adminReply == null || adminReply.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "回复内容不能为空"));
        }
        // 与库中已有管理员回复对比：首次回复、或用户追评后管理员再次回复（内容有变化）都应发站内通知
        final String previousAdminReplyNorm =
                feedback.getAdminReply() == null ? "" : feedback.getAdminReply().trim();
        final String newAdminReplyNorm = adminReply.trim();
        final boolean adminReplyTextChanged = !previousAdminReplyNorm.equals(newAdminReplyNorm);

        // 使用 MyBatis-Plus 的 LambdaUpdateWrapper 更新字段
        boolean updateSuccess = false;
        boolean statusUpdateSuccess = false;
        
        try {
            // 先更新 adminReply 和 updatedAt（必须成功）
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Feedback> updateWrapper = 
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            updateWrapper.eq(Feedback::getId, id)
                        .set(Feedback::getAdminReply, adminReply.trim())
                        .set(Feedback::getUpdatedAt, LocalDateTime.now());
            
            // 尝试同时更新状态
            try {
                updateWrapper.set(Feedback::getStatus, "PROCESSED");
                int updated = feedbackMapper.update(null, updateWrapper);
                if (updated > 0) {
                    updateSuccess = true;
                    statusUpdateSuccess = true;
                    feedback.setStatus("PROCESSED");
                    feedback.setAdminReply(adminReply.trim());
                }
            } catch (Exception statusError) {
                // 如果状态更新失败，只更新回复内容
                System.err.println("状态更新失败，但继续更新回复内容。反馈ID: " + id + ", 错误: " + statusError.getMessage());
                updateWrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
                updateWrapper.eq(Feedback::getId, id)
                            .set(Feedback::getAdminReply, adminReply.trim())
                            .set(Feedback::getUpdatedAt, LocalDateTime.now());
                int updated = feedbackMapper.update(null, updateWrapper);
                if (updated > 0) {
                    updateSuccess = true;
                    statusUpdateSuccess = false;
                    feedback.setAdminReply(adminReply.trim());
                }
            }
        } catch (Exception e) {
            System.err.println("更新反馈失败，反馈ID: " + id + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", 
                    "更新反馈失败: " + e.getMessage()));
        }
        
        // 只有更新成功才发送通知
        if (!updateSuccess) {
            return ResponseEntity.status(500).body(Map.of("message", "更新反馈失败"));
        }

        final boolean finalStatusUpdateSuccess = statusUpdateSuccess;
        final Long finalFeedbackId = id;
        final String finalAdminReply = adminReply.trim();
        final Long finalUserId = feedback.getUserId();
        final String finalFeedbackContent = feedback.getContent();

        if (adminReplyTextChanged && finalUserId != null) {
            try {
                feedbackAdminNotificationService.notifyUserAdminReplied(finalUserId, finalFeedbackId, finalAdminReply);
            } catch (Exception e) {
                log.warn("写入用户反馈回复站内通知失败: {}", e.getMessage());
            }
        }
        
        // 先返回成功响应，避免超时
        Map<String, Object> response = new HashMap<>();
        response.put("message", "回复成功");
        response.put("success", true);
        if (!finalStatusUpdateSuccess) {
            response.put("warning", "状态更新失败，但回复内容已保存");
        }
        
        // 异步发送通知（不阻塞响应）
        new Thread(() -> {
            try {
                // 通过 WebSocket 通知所有管理员反馈已回复
                feedbackWebSocketHandler.broadcastFeedbackReply(finalFeedbackId, finalAdminReply);
                if (finalStatusUpdateSuccess) {
                    feedbackWebSocketHandler.broadcastFeedbackStatusUpdate(finalFeedbackId, "PROCESSED");
                }
                
                // 发送邮件通知用户
                User user = userMapper.selectById(finalUserId);
                if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                    String subject = "您的反馈已收到管理员回复";
                    String content = String.format(
                        "尊敬的 %s 用户，\n\n" +
                        "您提交的反馈已收到管理员回复：\n\n" +
                        "反馈内容：%s\n\n" +
                        "管理员回复：%s\n\n" +
                        "感谢您对图书馆的关注与支持！\n\n" +
                        "此邮件由系统自动发送，请勿回复。",
                        user.getUsername(),
                        finalFeedbackContent.length() > 100 
                            ? finalFeedbackContent.substring(0, 100) + "..." 
                            : finalFeedbackContent,
                        finalAdminReply
                    );
                    emailService.sendReminder(user.getEmail(), subject, content);
                }
            } catch (Exception e) {
                // 通知发送失败不影响操作，只记录日志
                System.err.println("发送通知失败: " + e.getMessage());
            }
        }).start();
        
        return ResponseEntity.ok(response);
    }
    
    // 关闭反馈（管理员）
    @Operation(summary = "关闭反馈（管理员）")
    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> closeFeedback(@PathVariable Long id) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        // 确保状态值符合 ENUM 定义：PENDING, PROCESSED, CLOSED
        feedback.setStatus("CLOSED");
        feedback.setUpdatedAt(LocalDateTime.now());
        
        try {
            feedbackMapper.updateById(feedback);
        } catch (Exception e) {
            System.err.println("关闭反馈失败，反馈ID: " + id + ", 当前状态: " + feedback.getStatus() + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", 
                    "关闭反馈失败: " + e.getMessage()));
        }
        
        // 通过 WebSocket 通知所有管理员反馈状态已更新
        feedbackWebSocketHandler.broadcastFeedbackStatusUpdate(id, "CLOSED");
        
        return ResponseEntity.ok(Map.of("message", "反馈已关闭"));
    }
    
    // 用户回复管理员的回复
    @Operation(summary = "用户补充回复")
    @PutMapping("/{id}/user-reply")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> userReply(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        Long userId = currentUserId();
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        
        // 只能回复自己的反馈
        if (!feedback.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "只能回复自己的反馈"));
        }
        
        // 必须有管理员回复才能回复（首次回复时）
        if ((feedback.getUserReply() == null || feedback.getUserReply().trim().isEmpty()) 
            && (feedback.getAdminReply() == null || feedback.getAdminReply().trim().isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("message", "管理员尚未回复，无法回复"));
        }
        
        String userReply = (String) req.get("userReply");
        if (userReply == null || userReply.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "回复内容不能为空"));
        }
        
        // 更新用户回复（追加或覆盖）
        // 如果已有用户回复，追加新的回复（用换行分隔）
        String newUserReply;
        if (feedback.getUserReply() != null && !feedback.getUserReply().trim().isEmpty()) {
            newUserReply = feedback.getUserReply() + "\n\n[继续回复] " + userReply.trim();
        } else {
            newUserReply = userReply.trim();
        }
        
        feedback.setUserReply(newUserReply);
        feedback.setUpdatedAt(LocalDateTime.now());
        // 如果有用户回复，状态改为 PENDING，等待管理员再次回复
        if (!"CLOSED".equals(feedback.getStatus())) {
            feedback.setStatus("PENDING");
        }
        
        try {
            feedbackMapper.updateById(feedback);
        } catch (Exception e) {
            System.err.println("更新用户回复失败，反馈ID: " + id + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", 
                    "回复失败: " + e.getMessage()));
        }
        
        User replier = userMapper.selectById(userId);
        String replierName = replier != null ? replier.getUsername() : "用户ID:" + userId;
        try {
            feedbackWebSocketHandler.broadcastNewFeedback(id, replierName, userReply.trim(), feedback.getType());
        } catch (Exception e) {
            System.err.println("发送 WebSocket 通知失败: " + e.getMessage());
        }
        try {
            feedbackAdminNotificationService.notifyAdminsUserFollowUp(id, replierName, userReply.trim());
        } catch (Exception e) {
            log.warn("写入管理员站内通知失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(Map.of("message", "回复成功"));
    }
    
    // 用户删除自己的反馈
    @Operation(summary = "删除自己的反馈")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        try {
            Long userId = currentUserId();
            Feedback feedback = feedbackMapper.selectById(id);
            if (feedback == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
            }
            
            // 只能删除自己的反馈
            if (!feedback.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("message", "只能删除自己的反馈"));
            }
            
            feedbackMapper.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "反馈已删除"));
        } catch (IllegalStateException e) {
            // 无法获取当前用户ID（未登录或认证失败）
            System.err.println("删除反馈失败：无法获取当前用户ID, 错误: " + e.getMessage());
            return ResponseEntity.status(401).body(Map.of("message", "未登录或登录已过期，请重新登录"));
        } catch (Exception e) {
            System.err.println("删除反馈失败，反馈ID: " + id + ", 错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", 
                    "删除失败: " + e.getMessage()));
        }
    }
}
