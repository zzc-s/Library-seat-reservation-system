package com.example.libraryseat.violation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("violation")
public class Violation {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("reservation_id")
    private Long reservationId;
    
    private String type; // LATE_CHECKIN, NO_SHOW, OVERTIME, OTHER
    
    private String description;
    
    @TableField("occurred_at")
    private LocalDateTime occurredAt;
    
    private Boolean handled;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}


