package com.example.libraryseat.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.entity.AttendanceLog;
import com.example.libraryseat.attendance.mapper.AttendanceLogMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 座位状态管理服务
 * 负责根据预约和签到情况自动更新座位状态
 */
@Slf4j
@Service
public class SeatStatusService {
    
    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final AttendanceLogMapper attendanceLogMapper;
    
    public SeatStatusService(SeatMapper seatMapper, 
                            ReservationMapper reservationMapper,
                            AttendanceLogMapper attendanceLogMapper) {
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
        this.attendanceLogMapper = attendanceLogMapper;
    }
    
    /**
     * 更新座位状态（根据当前预约和签到情况）
     * @param seatId 座位ID
     */
    @Transactional
    public void updateSeatStatus(Long seatId) {
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            log.warn("座位 {} 不存在，无法更新状态", seatId);
            return;
        }
        
        // 如果座位是故障状态，不自动更新
        if ("FAULT".equals(seat.getStatus()) || "BROKEN".equals(seat.getStatus())) {
            log.debug("座位 {} 处于故障状态，不自动更新", seatId);
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 查找当前有效的预约（ACTIVE、CONFIRMED、PENDING状态，且时间范围包含当前时间）
        List<Reservation> activeReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getSeatId, seatId)
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                        .le(Reservation::getStartTime, now)
                        .ge(Reservation::getEndTime, now)
                        .orderByAsc(Reservation::getStartTime)
        );
        
        if (activeReservations.isEmpty()) {
            // 没有当前有效的预约，检查是否有未来的预约
            List<Reservation> futureReservations = reservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getSeatId, seatId)
                            .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                            .gt(Reservation::getStartTime, now)
                            .orderByAsc(Reservation::getStartTime)
                            .last("LIMIT 1")
            );
            
            if (futureReservations.isEmpty()) {
                // 没有任何有效预约，设置为空闲
                if (!"FREE".equals(seat.getStatus()) && !"IDLE".equals(seat.getStatus())) {
                    seat.setStatus("FREE");
                    seatMapper.updateById(seat);
                    log.info("座位 {} 没有有效预约，状态更新为 FREE", seatId);
                }
            } else {
                // 有未来预约，设置为已预约
                if (!"RESERVED".equals(seat.getStatus())) {
                    seat.setStatus("RESERVED");
                    seatMapper.updateById(seat);
                    log.info("座位 {} 有未来预约，状态更新为 RESERVED", seatId);
                }
            }
            return;
        }
        
        // 有当前有效的预约，检查是否已签到
        Reservation currentReservation = activeReservations.get(0);
        boolean hasCheckedIn = attendanceLogMapper.selectCount(
                new LambdaQueryWrapper<AttendanceLog>()
                        .eq(AttendanceLog::getReservationId, currentReservation.getId())
                        .eq(AttendanceLog::getAction, "CHECK_IN")
        ) > 0;
        
        if (hasCheckedIn) {
            // 已签到，设置为使用中
            if (!"OCCUPIED".equals(seat.getStatus())) {
                seat.setStatus("OCCUPIED");
                seatMapper.updateById(seat);
                log.info("座位 {} 有有效预约且已签到，状态更新为 OCCUPIED（预约ID: {}）", seatId, currentReservation.getId());
            }
        } else {
            // 未签到，设置为已预约
            if (!"RESERVED".equals(seat.getStatus())) {
                seat.setStatus("RESERVED");
                seatMapper.updateById(seat);
                log.info("座位 {} 有有效预约但未签到，状态更新为 RESERVED（预约ID: {}）", seatId, currentReservation.getId());
            }
        }
    }
    
    /**
     * 创建预约后更新座位状态
     * @param seatId 座位ID
     */
    @Transactional
    public void onReservationCreated(Long seatId) {
        log.info("预约创建，更新座位 {} 状态", seatId);
        updateSeatStatus(seatId);
    }
    
    /**
     * 取消预约后更新座位状态
     * @param seatId 座位ID
     */
    @Transactional
    public void onReservationCancelled(Long seatId) {
        log.info("预约取消，更新座位 {} 状态", seatId);
        updateSeatStatus(seatId);
    }
    
    /**
     * 签到后更新座位状态
     * @param seatId 座位ID
     */
    @Transactional
    public void onCheckIn(Long seatId) {
        log.info("签到成功，更新座位 {} 状态为 OCCUPIED", seatId);
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            log.warn("座位 {} 不存在，无法更新状态", seatId);
            return;
        }
        
        // 如果座位是故障状态，不自动更新
        if ("FAULT".equals(seat.getStatus()) || "BROKEN".equals(seat.getStatus())) {
            log.debug("座位 {} 处于故障状态，不自动更新", seatId);
            return;
        }
        
        seat.setStatus("OCCUPIED");
        seatMapper.updateById(seat);
        log.info("座位 {} 状态已更新为 OCCUPIED", seatId);
    }
    
    /**
     * 签退后更新座位状态
     * @param seatId 座位ID
     */
    @Transactional
    public void onCheckOut(Long seatId) {
        log.info("签退成功，更新座位 {} 状态", seatId);
        updateSeatStatus(seatId);
    }
    
    /**
     * 批量更新所有座位的状态（定时任务调用）
     */
    @Transactional
    public void updateAllSeatsStatus() {
        log.info("开始批量更新所有座位状态");
        List<Seat> allSeats = seatMapper.selectList(null);
        int updatedCount = 0;
        
        for (Seat seat : allSeats) {
            try {
                String oldStatus = seat.getStatus();
                updateSeatStatus(seat.getId());
                Seat updatedSeat = seatMapper.selectById(seat.getId());
                if (updatedSeat != null && !oldStatus.equals(updatedSeat.getStatus())) {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("更新座位 {} 状态失败", seat.getId(), e);
            }
        }
        
        log.info("批量更新座位状态完成，共更新 {} 个座位", updatedCount);
    }
}

