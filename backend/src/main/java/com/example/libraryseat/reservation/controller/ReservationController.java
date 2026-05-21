package com.example.libraryseat.reservation.controller;

import com.example.libraryseat.common.util.SecurityUtil;
import com.example.libraryseat.reservation.dto.CreateReservationRequest;
import com.example.libraryseat.reservation.dto.GroupCreateRequest;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "预约管理接口", description = "个人预约、协同预约创建与取消")
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final SecurityUtil securityUtil;

    public ReservationController(ReservationService reservationService, SecurityUtil securityUtil) {
        this.reservationService = reservationService;
        this.securityUtil = securityUtil;
    }

    @Operation(summary = "获取当前用户的预约列表（自动刷新过期与进行中状态）")
    @GetMapping
    public List<Reservation> myReservations() {
        return reservationService.listByUser(securityUtil.currentUserId());
    }

    @Operation(summary = "创建个人预约")
    @PostMapping
    public Reservation create(@RequestBody CreateReservationRequest req) {
        return reservationService.create(req, securityUtil.currentUserId());
    }

    @Operation(summary = "为多个座位批量创建个人预约（简易协同）")
    @PostMapping("/group")
    public List<Long> createGroup(@RequestBody GroupCreateRequest req) {
        return reservationService.createGroup(req, securityUtil.currentUserId()).stream().toList();
    }

    @Operation(summary = "取消个人预约")
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable("id") Long id) {
        reservationService.cancel(id, securityUtil.currentUserId());
    }
}


