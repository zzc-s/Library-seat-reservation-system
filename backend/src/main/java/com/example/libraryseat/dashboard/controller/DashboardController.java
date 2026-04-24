package com.example.libraryseat.dashboard.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.service.AttendanceService;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.mapper.SeatMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "数据看板接口", description = "座位与借阅统计卡片与趋势图数据")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final AttendanceService attendanceService;
    private final BookMapper bookMapper;
    private final BorrowMapper borrowMapper;

    public DashboardController(SeatMapper seatMapper,
                               ReservationMapper reservationMapper,
                               AttendanceService attendanceService,
                               BookMapper bookMapper,
                               BorrowMapper borrowMapper) {
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
        this.attendanceService = attendanceService;
        this.bookMapper = bookMapper;
        this.borrowMapper = borrowMapper;
    }

    @Operation(summary = "获取首页看板统计卡片数据")
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        LocalDateTime now = LocalDateTime.now();
        long totalSeats = seatMapper.selectCount(null);
        long totalBooks = bookMapper.selectCount(null);
        long borrowableBooks = bookMapper.selectCount(new LambdaQueryWrapper<com.example.libraryseat.book.entity.Book>()
                .eq(com.example.libraryseat.book.entity.Book::getIsBorrowable, true));
        // 统计进行中的预约：包括所有已确认但未结束的预约（ACTIVE 和 CONFIRMED，且结束时间在未来）
        // 这包括：1) 当前正在进行的预约 2) 已确认但还未开始的未来预约
        long reservedNow = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                .ge(Reservation::getEndTime, now));

        // 使用中：预约时段已覆盖当前时间，且已签到、尚未签退（与可视化选座「使用中」一致）
        List<Reservation> inSlot = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                .le(Reservation::getStartTime, now)
                .ge(Reservation::getEndTime, now));
        long occupiedNow = inSlot.stream()
                .filter(r -> attendanceService.hasCheckedIn(r.getId()) && !attendanceService.hasCheckedOut(r.getId()))
                .count();
//计算空闲座位数 = 总座位数 - 已预约数
        long idleNow = Math.max(0, totalSeats - reservedNow);
        long activeBorrows = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, "BORROWED"));

        Map<String, Object> data = new HashMap<>();
        data.put("totalSeats", totalSeats);
        data.put("reservedNow", reservedNow);
        data.put("occupiedNow", occupiedNow);
        data.put("idleNow", idleNow);
        data.put("totalBooks", totalBooks);
        data.put("borrowableBooks", borrowableBooks);
        data.put("activeBorrows", activeBorrows);
        return data;
    }

    @Operation(summary = "获取最近7天预约与借阅趋势数据")
    @GetMapping("/trend")
    public Map<String, Object> trend() {
        // last 7 days reservation counts per day & borrow counts per day
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> reservations = new ArrayList<>();
        List<Map<String, Object>> borrows = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            LocalDateTime start = d.atStartOfDay();
            LocalDateTime end = d.plusDays(1).atStartOfDay();
            long reservationCount = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                    .ge(Reservation::getStartTime, start)
                    .lt(Reservation::getStartTime, end));
            long borrowCount = borrowMapper.selectCount(new LambdaQueryWrapper<Borrow>()
                    .ge(Borrow::getBorrowDate, start)
                    .lt(Borrow::getBorrowDate, end));
            Map<String, Object> itemR = new HashMap<>();
            itemR.put("date", d.toString());
            itemR.put("reservations", reservationCount);
            reservations.add(itemR);

            Map<String, Object> itemB = new HashMap<>();
            itemB.put("date", d.toString());
            itemB.put("borrows", borrowCount);
            borrows.add(itemB);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("reservations", reservations);
        result.put("borrows", borrows);
        return result;
    }
}


