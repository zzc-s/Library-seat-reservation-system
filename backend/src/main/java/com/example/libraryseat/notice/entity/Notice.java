package com.example.libraryseat.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice")
public class Notice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String type; // NORMAL / URGENT / CLOSURE (普通/紧急/闭馆通知)
    @com.baomidou.mybatisplus.annotation.TableField("is_published")
    private Boolean isPublished;
    @com.baomidou.mybatisplus.annotation.TableField("created_at")
    private LocalDateTime createdAt;
    @com.baomidou.mybatisplus.annotation.TableField("updated_at")
    private LocalDateTime updatedAt;
    @com.baomidou.mybatisplus.annotation.TableField("expires_at")
    private LocalDateTime expiresAt;
}
