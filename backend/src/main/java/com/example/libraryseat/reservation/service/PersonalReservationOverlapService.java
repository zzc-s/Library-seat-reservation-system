package com.example.libraryseat.reservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人预约与时间段重叠校验：同一用户在 ACTIVE/CONFIRMED/PENDING 下，任意座位不可出现时间段交叉。
 */
@Service
public class PersonalReservationOverlapService {

    public static final List<String> OVERLAPPING_STATUSES = List.of("ACTIVE", "CONFIRMED", "PENDING");

    private final ReservationMapper reservationMapper;

    public PersonalReservationOverlapService(ReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    /**
     * 是否与该用户已有有效个人预约时间段相交（开区间：端点相接不算重叠，与座位冲突逻辑一致）。
     */
    public boolean hasUserTimeOverlap(Long userId, LocalDateTime start, LocalDateTime end, Long excludeReservationId) {
        LambdaQueryWrapper<Reservation> w = new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, OVERLAPPING_STATUSES)
                .gt(Reservation::getEndTime, start)
                .lt(Reservation::getStartTime, end);
        if (excludeReservationId != null) {
            w.ne(Reservation::getId, excludeReservationId);
        }
        return reservationMapper.selectCount(w) > 0;
    }
}
