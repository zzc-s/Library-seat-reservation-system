package com.example.libraryseat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_notification")
public class GroupNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    @com.baomidou.mybatisplus.annotation.TableField("user_id")
    private Long userId;
    @com.baomidou.mybatisplus.annotation.TableField("group_id")
    private Long groupId;
    private String type; // JOIN_REQUEST, REQUEST_APPROVED, REQUEST_REJECTED
    private String content;
    @com.baomidou.mybatisplus.annotation.TableField("is_read")
    private Boolean isRead;
    @com.baomidou.mybatisplus.annotation.TableField("created_at")
    private LocalDateTime createdAt;
}

