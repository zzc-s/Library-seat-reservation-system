package com.example.libraryseat.reservation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.service.SeatStatusService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "预约管理接口（管理员）", description = "预约列表查看、审核与手动释放座位")
@RestController
@RequestMapping("/api/admin/reservations")
@PreAuthorize("hasRole('ADMIN')")
public class ReservationAdminController {
    private final ReservationMapper reservationMapper;
    private final UserMapper userMapper;
    private final SeatStatusService seatStatusService;

    public ReservationAdminController(ReservationMapper reservationMapper, UserMapper userMapper, SeatStatusService seatStatusService) {
        this.reservationMapper = reservationMapper;
        this.userMapper = userMapper;
        this.seatStatusService = seatStatusService;
    }

    @Operation(summary = "分页查询预约列表")
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) Long seatId,
                                    @RequestParam(required = false) String userQuery,
                                    @RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "10") Integer size) {
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Reservation::getStatus, status);
        }
        if (seatId != null) {
            wrapper.eq(Reservation::getSeatId, seatId);
        }
        applyUserReservationFilter(wrapper, userQuery);
        wrapper.orderByDesc(Reservation::getCreatedAt);
        
        Page<Reservation> page = new Page<>(current, size);
        Page<Reservation> result = reservationMapper.selectPage(page, wrapper);
        
        // 添加用户名信息
        List<Map<String, Object>> records = result.getRecords().stream().map(r -> {
            User user = userMapper.selectById(r.getUserId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("userId", r.getUserId());
            item.put("username", user != null ? user.getUsername() : "未知");
            item.put("seatId", r.getSeatId());
            item.put("startTime", r.getStartTime());
            item.put("endTime", r.getEndTime());
            item.put("status", r.getStatus());
            item.put("createdAt", r.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("records", records);
        response.put("total", result.getTotal());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        response.put("pages", result.getPages());
        return response;
    }

    /**
     * 用户筛选：纯数字按用户 ID 精确匹配；否则按用户名模糊查询（匹配到的用户 ID 再筛选预约）。
     */
    private void applyUserReservationFilter(LambdaQueryWrapper<Reservation> wrapper, String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return;
        }
        String q = userQuery.trim();
        if (q.matches("^\\d+$")) {
            try {
                long uid = Long.parseLong(q);
                wrapper.eq(Reservation::getUserId, uid);
                return;
            } catch (NumberFormatException ignored) {
                // 超出 Long 范围时按用户名模糊查
            }
        }
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().like(User::getUsername, q));
        List<Long> ids = users.stream().map(User::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(Reservation::getUserId, ids);
        }
    }

    @Operation(summary = "批准预约申请")
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            return ResponseEntity.status(404).body(Map.of("message", "预约不存在"));
        }
        if (!"PENDING".equals(r.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "只能审核待审核的预约"));
        }
        r.setStatus("CONFIRMED");
        reservationMapper.updateById(r);
        return ResponseEntity.ok(Map.of("message", "已批准"));
    }

    @Operation(summary = "拒绝预约申请")
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            return ResponseEntity.status(404).body(Map.of("message", "预约不存在"));
        }
        if (!"PENDING".equals(r.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "只能审核待审核的预约"));
        }
        r.setStatus("CANCELLED");
        reservationMapper.updateById(r);
        return ResponseEntity.ok(Map.of("message", "已拒绝"));
    }

    /**
     * 管理员手动释放座位（提前结束预约）
     * 适用于用户提前离开的情况
     */
    @Operation(summary = "手动释放座位（提前结束预约）")
    @PostMapping("/{id}/release")
    @Transactional
    public ResponseEntity<?> releaseSeat(@PathVariable Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            return ResponseEntity.status(404).body(Map.of("message", "预约不存在"));
        }
        
        // 只能释放进行中或已确认的预约
        if (!List.of("ACTIVE", "CONFIRMED").contains(r.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", 
                "只能释放进行中或已确认的预约，当前状态：" + r.getStatus()));
        }
        
        // 将预约状态改为已取消，释放座位
        r.setStatus("CANCELLED");
        reservationMapper.updateById(r);
        
        // 更新座位状态
        try {
            seatStatusService.onReservationCancelled(r.getSeatId());
            log.info("管理员已手动释放座位，预约ID: {}, 座位ID: {}", id, r.getSeatId());
        } catch (Exception e) {
            log.error("更新座位状态失败，座位ID: {}", r.getSeatId(), e);
            // 即使座位状态更新失败，预约状态已更新，继续返回成功
        }
        
        return ResponseEntity.ok(Map.of("message", "座位已释放"));
    }
}
