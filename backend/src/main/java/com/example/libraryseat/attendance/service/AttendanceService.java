package com.example.libraryseat.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.entity.AttendanceLog;
import com.example.libraryseat.attendance.mapper.AttendanceLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 签到签退服务类
 * 处理签到签退相关的业务逻辑
 */
@Slf4j
@Service
public class AttendanceService {
    
    private final AttendanceLogMapper attendanceLogMapper;

    public AttendanceService(AttendanceLogMapper attendanceLogMapper) {
        this.attendanceLogMapper = attendanceLogMapper;
    }
    
    /**
     * 检查预约是否已签到
     */
    public boolean hasCheckedIn(Long reservationId) {
        long count = attendanceLogMapper.selectCount(
                new LambdaQueryWrapper<AttendanceLog>()
                        .eq(AttendanceLog::getReservationId, reservationId)
                        .eq(AttendanceLog::getAction, "CHECK_IN")
        );
        return count > 0;
    }
    
    /**
     * 检查预约是否已签退
     */
    public boolean hasCheckedOut(Long reservationId) {
        long count = attendanceLogMapper.selectCount(
                new LambdaQueryWrapper<AttendanceLog>()
                        .eq(AttendanceLog::getReservationId, reservationId)
                        .eq(AttendanceLog::getAction, "CHECK_OUT")
        );
        return count > 0;
    }
}

