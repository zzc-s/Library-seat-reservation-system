package com.example.libraryseat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_reservation")
public class GroupReservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String seatIds; // 逗号分隔的座位ID
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // PENDING, CONFIRMED, CANCELLED, EXPIRED, COMPLETED
    private LocalDateTime createdAt;
}
