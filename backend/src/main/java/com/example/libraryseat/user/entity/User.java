package com.example.libraryseat.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    // 数据库字段 password_hash
    @com.baomidou.mybatisplus.annotation.TableField("password_hash")
    private String passwordHash;
    private String email;
    private String phone;
    private String role; // USER / ADMIN
    @com.baomidou.mybatisplus.annotation.TableField("is_frozen")
    private Boolean isFrozen;
    @com.baomidou.mybatisplus.annotation.TableField("is_blacklisted")
    private Boolean isBlacklisted;
    @com.baomidou.mybatisplus.annotation.TableField("avatar_url")
    private String avatarUrl;
    @com.baomidou.mybatisplus.annotation.TableField("created_at")
    private LocalDateTime createdAt;
    @com.baomidou.mybatisplus.annotation.TableField("updated_at")
    private LocalDateTime updatedAt;
}


