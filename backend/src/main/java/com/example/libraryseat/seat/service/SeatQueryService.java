package com.example.libraryseat.seat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.dto.SeatVO;
import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeatQueryService {

    private static final List<String> EFFECTIVE_STATUSES = List.of("ACTIVE", "CONFIRMED", "PENDING");

    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;

    public SeatQueryService(SeatMapper seatMapper, ReservationMapper reservationMapper) {
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
    }

    public List<Seat> listAvailableSeats(String building, Integer floor, Boolean hasPower,
                                          Boolean isWindow, String zone,
                                          LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<Seat> qw = new LambdaQueryWrapper<>();
        applyBuildingFilter(qw, building);
        if (floor != null) qw.eq(Seat::getFloor, floor);
        if (hasPower != null) qw.eq(Seat::getHasPower, hasPower);
        if (isWindow != null) qw.eq(Seat::getIsWindow, isWindow);
        if (zone != null) qw.eq(Seat::getZone, zone);

        List<Seat> seats = seatMapper.selectList(qw);

        if (startTime != null && endTime != null) {
            seats = excludeReservedSeats(seats, startTime, endTime);
        }

        log.info("查询到 {} 个座位", seats.size());
        return seats;
    }

    public Map<String, Object> querySeatsForVisualization(String reserveDate, String timeSlot,
                                                          String building, Integer floor, String area) {
        try {
            String[] timeParts = timeSlot.split("-");
            if (timeParts.length != 2) {
                throw new IllegalArgumentException("时段格式错误，应为 HH:mm-HH:mm");
            }
            LocalDateTime startTime = LocalDateTime.parse(reserveDate + "T" + timeParts[0] + ":00");
            LocalDateTime endTime = LocalDateTime.parse(reserveDate + "T" + timeParts[1] + ":00");

            LambdaQueryWrapper<Seat> qw = new LambdaQueryWrapper<>();
            applyBuildingFilter(qw, building);
            if (floor != null) qw.eq(Seat::getFloor, floor);
            if (area != null && !area.isEmpty()) qw.eq(Seat::getArea, area);

            List<Seat> allSeats = seatMapper.selectList(qw);
            Set<Long> reservedSeatIds = findReservedSeatIds(allSeats, startTime, endTime);

            List<SeatVO> seatList = allSeats.stream()
                    .map(seat -> toSeatVO(seat, reservedSeatIds))
                    .collect(Collectors.toList());

            Map<String, List<SeatVO>> seatsByArea = seatList.stream()
                    .collect(Collectors.groupingBy(s -> s.area() != null ? s.area() : "未分类"));

            long availableCount = seatList.stream()
                    .filter(s -> "FREE".equals(s.status()))
                    .count();

            Map<String, Object> result = new HashMap<>();
            result.put("seats", seatList);
            result.put("seatsByArea", seatsByArea);
            result.put("total", seatList.size());
            result.put("available", availableCount);
            result.put("reserved", reservedSeatIds.size());
            return result;
        } catch (Exception e) {
            log.error("查询座位状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("seats", List.of());
            error.put("seatsByArea", Map.of());
            error.put("total", 0);
            error.put("available", 0);
            error.put("reserved", 0);
            return error;
        }
    }

    // ---- shared helpers ----

    public void applyBuildingFilter(LambdaQueryWrapper<Seat> qw, String building) {
        if (building == null || building.isEmpty()) return;
        if (building.endsWith("楼")) {
            String without = building.substring(0, building.length() - 1);
            qw.and(w -> w.eq(Seat::getBuilding, building).or().eq(Seat::getBuilding, without));
        } else {
            String with = building + "楼";
            qw.and(w -> w.eq(Seat::getBuilding, building).or().eq(Seat::getBuilding, with));
        }
    }

    public Set<Long> findReservedSeatIds(List<Seat> seats, LocalDateTime startTime, LocalDateTime endTime) {
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();
        if (seatIds.isEmpty()) return Set.of();

        List<Reservation> overlapping = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getSeatId, seatIds)
                .in(Reservation::getStatus, EFFECTIVE_STATUSES)
                .gt(Reservation::getEndTime, startTime)
                .lt(Reservation::getStartTime, endTime));
        return overlapping.stream().map(Reservation::getSeatId).collect(Collectors.toSet());
    }

    private List<Seat> excludeReservedSeats(List<Seat> seats, LocalDateTime startTime, LocalDateTime endTime) {
        Set<Long> busySeatIds = findReservedSeatIds(seats, startTime, endTime);
        List<Seat> result = seats.stream()
                .filter(s -> !busySeatIds.contains(s.getId()))
                .collect(Collectors.toList());
        log.info("排除时间冲突后，剩余 {} 个空闲座位（排除了 {} 个座位）", result.size(), busySeatIds.size());
        return result;
    }

    private SeatVO toSeatVO(Seat seat, Set<Long> reservedSeatIds) {
        String status;
        if (reservedSeatIds.contains(seat.getId())) {
            status = "RESERVED";
        } else {
            String original = seat.getStatus() != null ? seat.getStatus() : "FREE";
            status = ("BROKEN".equals(original) || "FAULT".equals(original)) ? original : "FREE";
        }

        return new SeatVO(
                seat.getId(),
                seat.getBuilding() + "-" + seat.getFloor() + "-" + seat.getLabel(),
                seat.getBuilding(),
                seat.getFloor(),
                seat.getLabel(),
                seat.getRowNum() != null ? seat.getRowNum() : 0,
                seat.getColNum() != null ? seat.getColNum() : 0,
                seat.getArea() != null ? seat.getArea() : seat.getZone(),
                Boolean.TRUE.equals(seat.getHasPower()),
                Boolean.TRUE.equals(seat.getIsWindow()),
                seat.getZone(),
                status
        );
    }
}