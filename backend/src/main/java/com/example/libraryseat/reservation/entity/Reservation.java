package com.example.libraryseat.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reservation")
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long seatId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // PENDING/CONFIRMED/ACTIVE/CANCELLED/FINISHED
    @TableField("check_in_time")
    private LocalDateTime checkInTime; // 签到时间
    @TableField("check_out_time")
    private LocalDateTime checkOutTime; // 签退时间
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}


