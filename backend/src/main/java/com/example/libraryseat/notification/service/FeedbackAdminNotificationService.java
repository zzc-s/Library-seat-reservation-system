package com.example.libraryseat.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.notification.entity.UserNotification;
import com.example.libraryseat.notification.mapper.UserNotificationMapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 反馈相关站内通知：用户提交/追加时通知全体管理员；管理员回复内容更新时通知反馈发起人（与普通用户通知同一套表与接口）
 */
@Service
public class FeedbackAdminNotificationService {

    private static final Map<String, String> TYPE_LABEL = Map.of(
            "FACILITY", "设施问题",
            "SERVICE", "服务问题",
            "BOOK", "图书问题",
            "OTHER", "其他"
    );

    private final UserMapper userMapper;
    private final UserNotificationMapper notificationMapper;

    public FeedbackAdminNotificationService(UserMapper userMapper,
                                            UserNotificationMapper notificationMapper) {
        this.userMapper = userMapper;
        this.notificationMapper = notificationMapper;
    }

    public void notifyAdminsNewFeedback(Long feedbackId, String username, String content, String feedbackType) {
        String preview = truncate(content, 160);
        String typeCn = TYPE_LABEL.getOrDefault(feedbackType, feedbackType != null ? feedbackType : "");
        String body = String.format("反馈 #%d · %s · %s\n%s", feedbackId, username, typeCn, preview);
        insertForAllAdmins("FEEDBACK_NEW", "新用户反馈", body);
    }

    public void notifyAdminsUserFollowUp(Long feedbackId, String username, String replySnippet) {
        String preview = truncate(replySnippet, 160);
        String body = String.format("反馈 #%d · %s 追加了回复\n%s", feedbackId, username, preview);
        insertForAllAdmins("FEEDBACK_FOLLOWUP", "用户追加回复", body);
    }

    /**
     * 管理员回复内容相对库中前值发生变化时，向反馈发起人写入站内通知（含首次回复、用户追评后再次回复）
     */
    public void notifyUserAdminReplied(Long ownerUserId, Long feedbackId, String adminReplySnippet) {
        if (ownerUserId == null) {
            return;
        }
        String preview = truncate(adminReplySnippet, 160);
        String body = String.format("反馈 #%d\n管理员回复：\n%s", feedbackId, preview);
        UserNotification n = new UserNotification();
        n.setUserId(ownerUserId);
        n.setType("FEEDBACK_ADMIN_REPLY");
        n.setTitle("管理员回复了您的反馈");
        n.setContent(body);
        n.setRelatedBookId(null);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
    }

    private void insertForAllAdmins(String type, String title, String content) {
        List<User> admins = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN"));
        LocalDateTime now = LocalDateTime.now();
        for (User admin : admins) {
            UserNotification n = new UserNotification();
            n.setUserId(admin.getId());
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setRelatedBookId(null);
            n.setIsRead(false);
            n.setCreatedAt(now);
            notificationMapper.insert(n);
        }
    }

    private static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "…";
    }
}
