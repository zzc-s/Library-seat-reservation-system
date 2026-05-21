package com.example.libraryseat.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.libraryseat.feedback.dto.FeedbackRequest;
import com.example.libraryseat.feedback.dto.FeedbackVO;
import com.example.libraryseat.feedback.entity.Feedback;
import com.example.libraryseat.feedback.mapper.FeedbackMapper;
import com.example.libraryseat.notification.service.FeedbackAdminNotificationService;
import com.example.libraryseat.security.EmailService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.websocket.FeedbackWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;
    private final FeedbackWebSocketHandler feedbackWebSocketHandler;
    private final FeedbackAdminNotificationService feedbackAdminNotificationService;

    public FeedbackService(FeedbackMapper feedbackMapper, UserMapper userMapper,
                           EmailService emailService, FeedbackWebSocketHandler feedbackWebSocketHandler,
                           FeedbackAdminNotificationService feedbackAdminNotificationService) {
        this.feedbackMapper = feedbackMapper;
        this.userMapper = userMapper;
        this.emailService = emailService;
        this.feedbackWebSocketHandler = feedbackWebSocketHandler;
        this.feedbackAdminNotificationService = feedbackAdminNotificationService;
    }

    public List<FeedbackVO> getMyFeedbacks(Long userId) {
        List<Feedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreatedAt));
        return toFeedbackVOs(feedbacks);
    }

    public ResponseEntity<?> createFeedback(FeedbackRequest req, Long userId) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(req.content());
        feedback.setType(req.type());
        feedback.setIsPrivate(req.isPrivate());
        feedback.setStatus("PENDING");
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.insert(feedback);

        User user = userMapper.selectById(userId);
        String username = user != null ? user.getUsername() : "用户ID:" + userId;
        feedbackWebSocketHandler.broadcastNewFeedback(feedback.getId(), username, feedback.getContent(), feedback.getType());

        try {
            feedbackAdminNotificationService.notifyAdminsNewFeedback(
                    feedback.getId(), username, feedback.getContent(), feedback.getType());
        } catch (Exception e) {
            log.warn("写入管理员站内通知失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("message", "反馈提交成功"));
    }

    public List<FeedbackVO> getPublicFeedbacks(Long currentUserId) {
        LambdaQueryWrapper<Feedback> qw = new LambdaQueryWrapper<>();
        if (currentUserId != null) {
            qw.and(w -> w.eq(Feedback::getIsPrivate, false).or().eq(Feedback::getUserId, currentUserId));
        } else {
            qw.eq(Feedback::getIsPrivate, false);
        }
        qw.orderByDesc(Feedback::getCreatedAt);

        List<Feedback> feedbacks = feedbackMapper.selectList(qw);
        return toFeedbackVOs(feedbacks);
    }

    public List<FeedbackVO> getAllFeedbacks() {
        List<Feedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>().orderByDesc(Feedback::getCreatedAt));
        return toFeedbackVOs(feedbacks);
    }

    public ResponseEntity<?> replyFeedback(Long id, String adminReply) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        if (adminReply == null || adminReply.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "回复内容不能为空"));
        }

        String previousReply = feedback.getAdminReply() != null ? feedback.getAdminReply().trim() : "";
        String newReply = adminReply.trim();
        boolean replyChanged = !previousReply.equals(newReply);

        LambdaUpdateWrapper<Feedback> uw = new LambdaUpdateWrapper<>();
        uw.eq(Feedback::getId, id)
                .set(Feedback::getAdminReply, newReply)
                .set(Feedback::getStatus, "PROCESSED")
                .set(Feedback::getUpdatedAt, LocalDateTime.now());
        int updated = feedbackMapper.update(null, uw);
        if (updated == 0) {
            return ResponseEntity.status(500).body(Map.of("message", "更新反馈失败"));
        }

        feedback.setStatus("PROCESSED");
        feedback.setAdminReply(newReply);

        if (replyChanged && feedback.getUserId() != null) {
            try {
                feedbackAdminNotificationService.notifyUserAdminReplied(feedback.getUserId(), id, newReply);
            } catch (Exception e) {
                log.warn("写入用户反馈回复站内通知失败: {}", e.getMessage());
            }
        }

        sendReplyNotificationsAsync(id, newReply, feedback.getUserId(), feedback.getContent());
        return ResponseEntity.ok(Map.of("message", "回复成功", "success", true));
    }

    public ResponseEntity<?> closeFeedback(Long id) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        feedback.setStatus("CLOSED");
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.updateById(feedback);
        feedbackWebSocketHandler.broadcastFeedbackStatusUpdate(id, "CLOSED");
        return ResponseEntity.ok(Map.of("message", "反馈已关闭"));
    }

    public ResponseEntity<?> userReply(Long id, String userReply, Long userId) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        if (!feedback.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "只能回复自己的反馈"));
        }
        if ((feedback.getUserReply() == null || feedback.getUserReply().trim().isEmpty())
                && (feedback.getAdminReply() == null || feedback.getAdminReply().trim().isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("message", "管理员尚未回复，无法回复"));
        }
        if (userReply == null || userReply.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "回复内容不能为空"));
        }

        String newUserReply = (feedback.getUserReply() != null && !feedback.getUserReply().trim().isEmpty())
                ? feedback.getUserReply() + "\n\n[继续回复] " + userReply.trim()
                : userReply.trim();

        feedback.setUserReply(newUserReply);
        feedback.setUpdatedAt(LocalDateTime.now());
        if (!"CLOSED".equals(feedback.getStatus())) {
            feedback.setStatus("PENDING");
        }
        feedbackMapper.updateById(feedback);

        User replier = userMapper.selectById(userId);
        String replierName = replier != null ? replier.getUsername() : "用户ID:" + userId;
        try {
            feedbackWebSocketHandler.broadcastNewFeedback(id, replierName, userReply.trim(), feedback.getType());
        } catch (Exception e) {
            log.warn("WebSocket 通知失败: {}", e.getMessage());
        }
        try {
            feedbackAdminNotificationService.notifyAdminsUserFollowUp(id, replierName, userReply.trim());
        } catch (Exception e) {
            log.warn("写入管理员站内通知失败: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("message", "回复成功"));
    }

    public ResponseEntity<?> deleteFeedback(Long id, Long userId) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "反馈不存在"));
        }
        if (!feedback.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("message", "只能删除自己的反馈"));
        }
        feedbackMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "反馈已删除"));
    }

    // ---- private helpers ----

    private List<FeedbackVO> toFeedbackVOs(List<Feedback> feedbacks) {
        Set<Long> userIds = feedbacks.stream().map(Feedback::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        return feedbacks.stream().map(f -> {
            User user = userMap.get(f.getUserId());
            return new FeedbackVO(
                    f.getId(), f.getUserId(),
                    user != null ? user.getUsername() : null,
                    user != null ? user.getAvatarUrl() : null,
                    f.getContent(), f.getType(), f.getStatus(),
                    f.getAdminReply(), f.getUserReply(),
                    f.getIsPrivate() != null ? f.getIsPrivate() : false,
                    f.getCreatedAt(), f.getUpdatedAt());
        }).collect(Collectors.toList());
    }

    private void sendReplyNotificationsAsync(Long feedbackId, String adminReply, Long userId, String feedbackContent) {
        new Thread(() -> {
            try {
                feedbackWebSocketHandler.broadcastFeedbackReply(feedbackId, adminReply);
                feedbackWebSocketHandler.broadcastFeedbackStatusUpdate(feedbackId, "PROCESSED");

                User user = userMapper.selectById(userId);
                if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                    String subject = "您的反馈已收到管理员回复";
                    String truncatedContent = feedbackContent.length() > 100
                            ? feedbackContent.substring(0, 100) + "..." : feedbackContent;
                    String body = String.format(
                            "尊敬的 %s 用户，\n\n您提交的反馈已收到管理员回复：\n\n" +
                            "反馈内容：%s\n\n管理员回复：%s\n\n" +
                            "感谢您对图书馆的关注与支持！\n\n此邮件由系统自动发送，请勿回复。",
                            user.getUsername(), truncatedContent, adminReply);
                    emailService.sendReminder(user.getEmail(), subject, body);
                }
            } catch (Exception e) {
                log.warn("发送回复通知失败: {}", e.getMessage());
            }
        }).start();
    }
}