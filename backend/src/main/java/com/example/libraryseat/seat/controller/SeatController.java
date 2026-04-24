package com.example.libraryseat.seat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import com.example.libraryseat.attendance.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "座位查询接口", description = "座位筛选、可视化查询及二维码")
@RestController
@RequestMapping("/api/seats")
public class SeatController {
    private final SeatMapper seatMapper;
    private final ReservationMapper reservationMapper;
    private final QrCodeService qrCodeService;

    public SeatController(SeatMapper seatMapper, ReservationMapper reservationMapper, QrCodeService qrCodeService) {
        this.seatMapper = seatMapper;
        this.reservationMapper = reservationMapper;
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
        log.info("收到座位列表查询请求: building={}, floor={}, hasPower={}, isWindow={}, zone={}, startTime={}, endTime={}", 
                building, floor, hasPower, isWindow, zone, startTime != null ? startTime.toString() : "null", endTime != null ? endTime.toString() : "null");
        LambdaQueryWrapper<Seat> qw = new LambdaQueryWrapper<>();
        if (building != null && !building.isEmpty()) {
            // 支持两种格式的楼栋名称匹配：
            // 1. 如果传入的是"A"，匹配"A"或"A楼"
            // 2. 如果传入的是"A楼"，匹配"A楼"或"A"
            if (building.endsWith("楼")) {
                // 传入的是"A楼"格式，匹配"A楼"或"A"
                String buildingWithoutLou = building.substring(0, building.length() - 1);
                qw.and(wrapper -> wrapper.eq(Seat::getBuilding, building).or().eq(Seat::getBuilding, buildingWithoutLou));
            } else {
                // 传入的是"A"格式，匹配"A"或"A楼"
                String buildingWithLou = building + "楼";
                qw.and(wrapper -> wrapper.eq(Seat::getBuilding, building).or().eq(Seat::getBuilding, buildingWithLou));
            }
        }
        if (floor != null) qw.eq(Seat::getFloor, floor);
        if (hasPower != null) qw.eq(Seat::getHasPower, hasPower);
        if (isWindow != null) qw.eq(Seat::getIsWindow, isWindow);
        if (zone != null) qw.eq(Seat::getZone, zone);
        List<Seat> seats = seatMapper.selectList(qw);
        
        // 如果提供了时间参数，排除在该时间段内已被预约的座位
        if (startTime != null && endTime != null) {
            List<Long> seatIds = seats.stream().map(Seat::getId).toList();
            if (!seatIds.isEmpty()) {
 /////////////////////////////////////////////////////////////////////
                // 查询与指定时间段有重叠的预约
                // 重叠条件：预约的结束时间 > 查询开始时间 AND 预约的开始时间 < 查询结束时间
                List<Reservation> overlapping = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                        .in(Reservation::getSeatId, seatIds)
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED", "PENDING"))
                        .gt(Reservation::getEndTime, startTime)
                        .lt(Reservation::getStartTime, endTime));
                Set<Long> busySeatIds = overlapping.stream().map(Reservation::getSeatId).collect(Collectors.toSet());
                seats = seats.stream().filter(s -> !busySeatIds.contains(s.getId())).collect(Collectors.toList());
                log.info("排除时间冲突后，剩余 {} 个空闲座位（排除了 {} 个座位）", seats.size(), busySeatIds.size());
            }
        }
        
        log.info("查询到 {} 个座位", seats.size());
        return seats;
    }

    /**
     * 查询指定日期+时段的座位状态列表（用于可视化选座）
     * @param reserveDate 预约日期，格式：yyyy-MM-dd
     * @param timeSlot 时段，格式：HH:mm-HH:mm，如 "08:00-12:00"
     * @param building 楼栋（可选）
     * @param floor 楼层（可选）
     * @param area 区域（可选）
     * @return 座位列表，包含状态信息
     */
    @Operation(summary = "查询指定日期与时段的座位状态列表")
    @GetMapping("/query")
    public Map<String, Object> querySeatsForVisualization(
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
            
            // 查询所有座位
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
/////////////////////////////////////////////////////////////
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
                item.put("seatCode", seat.getBuilding() + "-" + seat.getFloor() + "-" + seat.getLabel());
                item.put("building", seat.getBuilding());
                item.put("floor", seat.getFloor());
                item.put("label", seat.getLabel());
                item.put("row", seat.getRowNum() != null ? seat.getRowNum() : 0);
                item.put("col", seat.getColNum() != null ? seat.getColNum() : 0);
                item.put("area", seat.getArea() != null ? seat.getArea() : seat.getZone());
                item.put("hasPower", Boolean.TRUE.equals(seat.getHasPower()));
                item.put("isWindow", Boolean.TRUE.equals(seat.getIsWindow()));
                item.put("zone", seat.getZone());
                
                // 仅当查询时段与预约 overlap 时标 RESERVED；seat 表全局 RESERVED/OCCUPIED 不用于否决其它时段
                if (finalReservedSeatIds.contains(seat.getId())) {
                    item.put("status", "RESERVED");
                } else {
                    String originalStatus = seat.getStatus() != null ? seat.getStatus() : "FREE";
                    if ("BROKEN".equals(originalStatus) || "FAULT".equals(originalStatus)) {
                        item.put("status", originalStatus);
                    } else {
                        item.put("status", "FREE");
                    }
                }
                
                return item;
            }).collect(Collectors.toList());
            
            // 按区域分组
            Map<String, List<Map<String, Object>>> seatsByArea = seatList.stream()
                    .collect(Collectors.groupingBy(s -> (String) s.getOrDefault("area", "未分类")));
           //统计可用座位数量
            Map<String, Object> result = new HashMap<>();
            result.put("seats", seatList);
            result.put("seatsByArea", seatsByArea);
            result.put("total", seatList.size());
            result.put("available", seatList.stream().filter(s -> !"RESERVED".equals(s.get("status")) && !"OCCUPIED".equals(s.get("status")) && !"BROKEN".equals(s.get("status")) && !"FAULT".equals(s.get("status"))).count());
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
     * 获取座位的二维码（供管理员打印）
     * @param seatId 座位ID
     * @return 二维码Base64图片
     */
    @Operation(summary = "生成并获取座位二维码（管理员打印）")
    @GetMapping("/{seatId}/qrcode")
    public ResponseEntity<?> getSeatQrCode(@PathVariable Long seatId) {
        //验证座位是否存在
        try {
            Seat seat = seatMapper.selectById(seatId);
            if (seat == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "座位不存在"));
            }
       //调用QrCodeService生成Base64格式二维码图片
            String qrCodeImage = qrCodeService.generateSeatQrCode(seatId);
       //返回二维码数据和座位信息
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


