package com.example.libraryseat.seat.controller;

import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import com.example.libraryseat.seat.service.SeatQueryService;
import com.example.libraryseat.attendance.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "座位查询接口", description = "座位筛选、可视化查询及二维码")
@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatQueryService seatQueryService;
    private final SeatMapper seatMapper;
    private final QrCodeService qrCodeService;

    public SeatController(SeatQueryService seatQueryService, SeatMapper seatMapper, QrCodeService qrCodeService) {
        this.seatQueryService = seatQueryService;
        this.seatMapper = seatMapper;
        this.qrCodeService = qrCodeService;
    }

    @Operation(summary = "按条件筛选可预约座位列表")
    @GetMapping
    public List<Seat> list(@RequestParam(required = false) String building,
                           @RequestParam(required = false) Integer floor,
                           @RequestParam(required = false) Boolean hasPower,
                           @RequestParam(required = false) Boolean isWindow,
                           @RequestParam(required = false) String zone,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return seatQueryService.listAvailableSeats(building, floor, hasPower, isWindow, zone, startTime, endTime);
    }

    @Operation(summary = "查询指定日期与时段的座位状态列表")
    @GetMapping("/query")
    public Map<String, Object> querySeatsForVisualization(
            @RequestParam String reserveDate,
            @RequestParam String timeSlot,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String area) {
        return seatQueryService.querySeatsForVisualization(reserveDate, timeSlot, building, floor, area);
    }

    @Operation(summary = "生成并获取座位二维码（管理员打印）")
    @GetMapping("/{seatId}/qrcode")
    public ResponseEntity<?> getSeatQrCode(@PathVariable Long seatId) {
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "座位不存在"));
        }
        try {
            String qrCodeImage = qrCodeService.generateSeatQrCode(seatId);
            Map<String, Object> result = new HashMap<>();
            result.put("qrCode", qrCodeImage);
            result.put("seatId", seatId);
            result.put("seatLabel", seat.getLabel());
            result.put("building", seat.getBuilding());
            result.put("floor", seat.getFloor());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("生成座位二维码失败，座位ID: {}", seatId, e);
            return ResponseEntity.status(500).body(Map.of("message", "生成二维码失败: " + e.getMessage()));
        }
    }
}


