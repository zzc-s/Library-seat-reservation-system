package com.example.libraryseat.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attendance_log")
public class AttendanceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long reservationId;
    private Long seatId;
    private String action; // CHECK_IN / CHECK_OUT / LEAVE_START / LEAVE_END
    private LocalDateTime occurredAt;
    private String note;
}


