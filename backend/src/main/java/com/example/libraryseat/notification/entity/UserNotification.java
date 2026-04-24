package com.example.libraryseat.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_notification")
public class UserNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type; // BOOK_AVAILABLE 等
    private String title;
    private String content;
    private Long relatedBookId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
