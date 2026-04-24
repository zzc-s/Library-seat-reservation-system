package com.example.libraryseat.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    @com.baomidou.mybatisplus.annotation.TableField("user_id")
    private Long userId;
    private String content;
    private String type; // FACILITY / SERVICE / BOOK / OTHER (设施/服务/图书/其他)
    private String status; // PENDING / PROCESSED / CLOSED (待处理/已处理/已关闭)
    @com.baomidou.mybatisplus.annotation.TableField("admin_reply")
    private String adminReply;
    @com.baomidou.mybatisplus.annotation.TableField("user_reply")
    private String userReply; // 用户对管理员回复的回复
    @com.baomidou.mybatisplus.annotation.TableField("is_private")
    private Boolean isPrivate; // TRUE=隐私（仅管理员可见），FALSE=公开
    @com.baomidou.mybatisplus.annotation.TableField("created_at")
    private LocalDateTime createdAt;
    @com.baomidou.mybatisplus.annotation.TableField("updated_at")
    private LocalDateTime updatedAt;
}
