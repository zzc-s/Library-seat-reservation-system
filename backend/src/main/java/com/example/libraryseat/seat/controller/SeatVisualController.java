package com.example.libraryseat.seat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.common.util.SecurityUtil;
import com.example.libraryseat.reservation.dto.CreateReservationRequest;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.reservation.service.ReservationService;
import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import com.example.libraryseat.websocket.SeatStatusWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 可视化选座控制器
 * 使用单数路径 /api/seat 符合RESTful规范
 */
@Slf4j
@Tag(name = "可视化选座接口", description = "平面图选座、状态查询与一键预约")
@RestController
@RequestMapping("/api/seat")
public class SeatVisualController {
    
    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final SeatStatusWebSocketHandler webSocketHandler;
    private final ReservationService reservationService;
    private final SecurityUtil securityUtil;
    
    public SeatVisualController(SeatMapper seatMapper, ReservationMapper reservationMapper,
                                SeatStatusWebSocketHandler webSocketHandler,
                                ReservationService reservationService,
                                SecurityUtil securityUtil) {
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
        this.webSocketHandler = webSocketHandler;
        this.reservationService = reservationService;
        this.securityUtil = securityUtil;
    }
    
    /**
     * 查询指定日期+时段的座位状态列表
     * GET /api/seat/query
     * 
     * @param reserveDate 预约日期，格式：yyyy-MM-dd
     * @param timeSlot 时段，格式：HH:mm-HH:mm，如 "08:00-12:00"
     * @param building 楼栋（可选）
     * @param floor 楼层（可选）
     * @param area 区域（可选）
     * @return 座位列表，包含状态信息
     */
    @Operation(summary = "可视化平面图查询座位状态")
    @GetMapping("/query")
    public Map<String, Object> query(
            @RequestParam String reserveDate,
            @RequestParam String timeSlot,
            @RequestParam(required = false) String building,
            @RequestParam(required = false) Integer floor,
            @RequestParam(required = false) String area) {
        log.info("收到可视化选座查询请求: reserveDate={}, timeSlot={}, building={}, floor={}, area={}", 
                reserveDate, timeSlot, building, floor, area);
        
        try {
            // 解析日期和时段
            String[] timeParts = timeSlot.split("-");
            if (timeParts.length != 2) {
                throw new IllegalArgumentException("时段格式错误，应为 HH:mm-HH:mm");
            }
            LocalDateTime startTime = LocalDateTime.parse(reserveDate + "T" + timeParts[0] + ":00");
            LocalDateTime endTime = LocalDateTime.parse(reserveDate + "T" + timeParts[1] + ":00");
            
            // 查询所有座位列表
            LambdaQueryWrapper<Seat> qw = new LambdaQueryWrapper<>();
            if (building != null && !building.isEmpty()) {
                if (building.endsWith("楼")) {
                    String buildingWithoutLou = building.substring(0, building.length() - 1);
                    qw.and(wrapper -> wrapper.eq(Seat::getBuilding, building).or().eq(Seat::getBuilding, buildingWithoutLou));
                } else {
                    String buildingWithLou = building + "楼";
                    qw.and(wrapper -> wrapper.eq(Seat::getBuilding, building).or().eq(Seat::getBuilding, buildingWithLou));
                }
            }
            if (floor != null) qw.eq(Seat::getFloor, floor);
            if (area != null && !area.isEmpty()) qw.eq(Seat::getArea, area);
            
            List<Seat> allSeats = seatMapper.selectList(qw);
            log.info("查询到 {} 个座位", allSeats.size());
            
            if (allSeats.isEmpty()) {
                log.warn("数据库中没有任何座位数据！请检查：1. 是否执行了schema.sql 2. 数据库中是否有seat表数据");
            }
            
            // 查询该时段已预约的座位ID
            List<Long> seatIds = allSeats.stream().map(Seat::getId).toList();
            Set<Long> reservedSeatIds = new HashSet<>();
            if (!seatIds.isEmpty()) {
                List<Reservation> overlapping = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getSeatId, seatIds)
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                        .gt(Reservation::getEndTime, startTime)
                        .lt(Reservation::getStartTime, endTime));
                reservedSeatIds = overlapping.stream().map(Reservation::getSeatId).collect(Collectors.toSet());
            }
            
            // 创建final变量供lambda表达式使用
            final Set<Long> finalReservedSeatIds = reservedSeatIds;
            
            // 构建返回结果，包含座位状态
            List<Map<String, Object>> seatList = allSeats.stream().map(seat -> {
                Map<String, Object> item = new HashMap<>();
                item.put("id", seat.getId());
                // 生成座位编号：building-floor-label（如：A1-01-001）
                String seatCode = seat.getBuilding() + "-" + 
                    (seat.getFloor() != null ? String.format("%02d", seat.getFloor()) : "00") + "-" + 
                    (seat.getLabel() != null ? seat.getLabel() : String.valueOf(seat.getId()));
                item.put("seatCode", seatCode);
                item.put("building", seat.getBuilding());
                item.put("floor", seat.getFloor());
                item.put("label", seat.getLabel());
                item.put("row", seat.getRowNum() != null ? seat.getRowNum() : 0);
                item.put("col", seat.getColNum() != null ? seat.getColNum() : 0);
                item.put("area", seat.getArea() != null ? seat.getArea() : seat.getZone());
                item.put("hasPower", Boolean.TRUE.equals(seat.getHasPower()));
                item.put("isWindow", Boolean.TRUE.equals(seat.getIsWindow()));
                item.put("zone", seat.getZone());
                
                // 计算座位状态：仅当「查询时段」与库中预约时间重叠时标记为已预约
                if (finalReservedSeatIds.contains(seat.getId())) {
                    item.put("status", 1); // 已预约（数字状态）
                    item.put("statusText", "RESERVED"); // 保留字符串状态
                } else {
                    // 注意：seat 表上的 RESERVED/OCCUPIED 是「当前运营状态」，由 SeatStatusService 维护；
                    // 不能用它否定「其它时段」的可预约性，否则会出现 A 约 10:00–13:00 时，查 13:01–14:00 仍显示已约。
                    String originalStatus = seat.getStatus() != null ? seat.getStatus() : "FREE";
                    if ("BROKEN".equals(originalStatus)) {
                        item.put("status", 3);
                        item.put("statusText", "BROKEN");
                    } else if ("FAULT".equals(originalStatus)) {
                        item.put("status", 4);
                        item.put("statusText", "FAULT");
                    } else if ("IDLE".equals(originalStatus)) {
                        item.put("status", 0);
                        item.put("statusText", "IDLE");
                    } else {
                        item.put("status", 0);
                        item.put("statusText", "FREE");
                    }
                }
                
                return item;
            }).collect(Collectors.toList());
            
            // 按区域分组
            Map<String, List<Map<String, Object>>> seatsByArea = seatList.stream()
                    .collect(Collectors.groupingBy(s -> (String) s.getOrDefault("area", "未分类")));
            
            Map<String, Object> result = new HashMap<>();
            result.put("seats", seatList);
            result.put("seatsByArea", seatsByArea);
            result.put("total", seatList.size());
            result.put("available", seatList.stream()
                    .filter(s -> {
                        Object status = s.get("status");
                        return status instanceof Number && ((Number) status).intValue() == 0;
                    })
                    .count());
            result.put("reserved", finalReservedSeatIds.size());
            
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
    
    /**
     * 提交座位预约
     * POST /api/seat/reserve
     * 
     * @param req 预约请求，包含 seatId, reserveDate, startTime, endTime
     * @return 预约结果
     */
    @Operation(summary = "在可视化平面图上一键预约座位")
    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserve(@RequestBody Map<String, Object> req) {
        Long userId = securityUtil.currentUserId();
        log.info("收到座位预约请求: userId={}, req={}", userId, req);

        Long seatId = Long.valueOf(req.get("seatId").toString());
        LocalDateTime startTime = LocalDateTime.parse((String) req.get("startTime"));
        LocalDateTime endTime = LocalDateTime.parse((String) req.get("endTime"));

        Reservation reservation = reservationService.create(
                new CreateReservationRequest(seatId, startTime, endTime), userId);

        webSocketHandler.broadcastSeatStatusUpdate(seatId, 1, "RESERVED");

        return ResponseEntity.ok(Map.of(
                "message", "预约成功",
                "reservationId", reservation.getId()
        ));
    }
}
