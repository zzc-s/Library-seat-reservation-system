package com.example.libraryseat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_join_request")
public class GroupJoinRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long userId;
    private String status; // PENDING, APPROVED, REJECTED, EXPIRED
    private LocalDateTime createdAt;
    @com.baomidou.mybatisplus.annotation.TableField("updated_at")
    private LocalDateTime updatedAt;
}

