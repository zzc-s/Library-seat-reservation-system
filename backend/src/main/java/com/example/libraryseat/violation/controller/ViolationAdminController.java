package com.example.libraryseat.violation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.reservation.schedule.ReservationScheduleService;
import com.example.libraryseat.violation.dto.ViolationDto;
import com.example.libraryseat.violation.entity.Violation;
import com.example.libraryseat.violation.mapper.ViolationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "违规管理接口（管理员）", description = "违规列表、统计、导出与黑名单管理")
@RestController
@RequestMapping("/api/admin/violations")
@PreAuthorize("hasRole('ADMIN')")
public class ViolationAdminController {
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;
    private final ReservationScheduleService reservationScheduleService;
    public ViolationAdminController(ViolationMapper violationMapper, UserMapper userMapper,
                                   ReservationScheduleService reservationScheduleService) {
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
        this.reservationScheduleService = reservationScheduleService;
    }

    @Operation(summary = "分页查询违规记录列表")
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "10") Integer size,
                                    @RequestParam(required = false) String userQuery,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) Boolean handled) {
        int c = current != null && current > 0 ? current : 1;
        int s = size != null && size > 0 ? Math.min(size, 100) : 10;
        Page<Violation> page = new Page<>(c, s);
        LambdaQueryWrapper<Violation> qw = new LambdaQueryWrapper<>();
        applyUserViolationFilter(qw, userQuery);
        if (from != null) qw.ge(Violation::getOccurredAt, from.atStartOfDay());
        if (to != null) qw.lt(Violation::getOccurredAt, to.plusDays(1).atStartOfDay());
        if (type != null && !type.isEmpty()) qw.eq(Violation::getType, type);
        if (handled != null) qw.eq(Violation::getHandled, handled);
        qw.orderByDesc(Violation::getOccurredAt);

        Page<Violation> result = violationMapper.selectPage(page, qw);
        List<ViolationDto> records = convertToDtoList(result.getRecords());
        Map<String, Object> body = new HashMap<>();
        body.put("records", records);
        body.put("total", result.getTotal());
        body.put("current", result.getCurrent());
        body.put("size", result.getSize());
        body.put("pages", Math.max(1L, result.getPages()));
        return body;
    }

    /**
     * 用户筛选：纯数字按用户 ID 精确匹配；否则按用户名模糊查询。
     */
    private void applyUserViolationFilter(LambdaQueryWrapper<Violation> qw, String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return;
        }
        String q = userQuery.trim();
        if (q.matches("^\\d+$")) {
            try {
                long uid = Long.parseLong(q);
                qw.eq(Violation::getUserId, uid);
                return;
            } catch (NumberFormatException ignored) {
                // 超出 Long 范围时按用户名模糊查
            }
        }
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().like(User::getUsername, q));
        List<Long> ids = users.stream().map(User::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            qw.apply("1 = 0");
        } else {
            qw.in(Violation::getUserId, ids);
        }
    }

    /**
     * 获取统计信息
     */
    @Operation(summary = "获取违规统计信息")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<Violation> allViolations = violationMapper.selectList(null);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", allViolations.size());
        
        long unhandledCount = allViolations.stream()
                .filter(v -> v.getHandled() == null || !v.getHandled())
                .count();
        stats.put("unhandled", unhandledCount);
        stats.put("handled", allViolations.size() - unhandledCount);
        
        // 按类型统计
        Map<String, Long> typeStats = allViolations.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getType() != null ? v.getType() : "OTHER",
                        Collectors.counting()
                ));
        stats.put("byType", typeStats);
        
        return stats;
    }

    @Operation(summary = "创建违规记录")
    @PostMapping
    public ViolationDto create(@RequestBody Violation v) {
        v.setId(null);
        if (v.getOccurredAt() == null) v.setOccurredAt(LocalDateTime.now());
        if (v.getHandled() == null) v.setHandled(false);
        violationMapper.insert(v);
        return convertToDto(v);
    }

    @Operation(summary = "更新违规记录")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Violation v = violationMapper.selectById(id);
        if (v == null) return ResponseEntity.notFound().build();
        if (body.containsKey("type")) v.setType(String.valueOf(body.get("type")));
        if (body.containsKey("description")) v.setDescription((String) body.get("description"));
        if (body.containsKey("handled")) v.setHandled(Boolean.valueOf(String.valueOf(body.get("handled"))));
        violationMapper.updateById(v);
        return ResponseEntity.ok(convertToDto(v));
    }

    @Operation(summary = "删除违规记录")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        violationMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    /**
     * 批量标记为已处理
     */
    @Operation(summary = "批量标记违规为已处理")
    @PostMapping("/batch-handle")
    public ResponseEntity<?> batchHandle(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择要处理的违规记录"));
        }
        
        int count = 0;
        for (Long id : ids) {
            Violation v = violationMapper.selectById(id);
            if (v != null) {
                v.setHandled(true);
                violationMapper.updateById(v);
                count++;
            }
        }
        
        return ResponseEntity.ok(Map.of("message", "成功处理 " + count + " 条记录", "count", count));
    }

    /**
     * 批量删除
     */
    @Operation(summary = "批量删除违规记录")
    @PostMapping("/batch-delete")
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择要删除的违规记录"));
        }
        
        int count = 0;
        for (Long id : ids) {
            Violation v = violationMapper.selectById(id);
            if (v != null) {
                violationMapper.deleteById(id);
                count++;
            }
        }
        
        return ResponseEntity.ok(Map.of("message", "成功删除 " + count + " 条记录", "count", count));
    }

    @Operation(summary = "导出违规记录为 Excel")
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public void exportExcel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            HttpServletResponse response) throws IOException {
        LambdaQueryWrapper<Violation> qw = new LambdaQueryWrapper<>();
        if (from != null) qw.ge(Violation::getOccurredAt, from.atStartOfDay());
        if (to != null) qw.lt(Violation::getOccurredAt, to.plusDays(1).atStartOfDay());
        qw.orderByDesc(Violation::getOccurredAt);
        List<Violation> violations = violationMapper.selectList(qw);
        List<ViolationDto> list = convertToDtoList(violations);

        String filename = "violations";
        if (from != null && to != null) {
            filename += "-" + from + "_" + to;
        } else if (from != null) {
            filename += "-from-" + from;
        } else if (to != null) {
            filename += "-to-" + to;
        } else {
            filename += "-all";
        }
        filename += ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("违规记录");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "用户ID", "用户名", "预约ID", "违规类型", "描述", "发生时间", "已处理"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            int rowNum = 1;
            for (ViolationDto v : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(v.getId() != null ? v.getId() : 0);
                row.createCell(1).setCellValue(v.getUserId() != null ? v.getUserId() : 0);
                row.createCell(2).setCellValue(v.getUsername() != null ? v.getUsername() : "");
                row.createCell(3).setCellValue(v.getReservationId() != null ? v.getReservationId().toString() : "");
                row.createCell(4).setCellValue(v.getType() != null ? v.getType() : "");
                row.createCell(5).setCellValue(v.getDescription() != null ? v.getDescription() : "");
                row.createCell(6).setCellValue(v.getOccurredAt() != null ? v.getOccurredAt().format(formatter) : "");
                row.createCell(7).setCellValue(Boolean.TRUE.equals(v.getHandled()) ? "是" : "否");
            }
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        }
    }

    /**
     * 将Violation转换为ViolationDto
     */
    private ViolationDto convertToDto(Violation v) {
        ViolationDto dto = new ViolationDto();
        dto.setId(v.getId());
        dto.setUserId(v.getUserId());
        dto.setReservationId(v.getReservationId());
        dto.setType(v.getType());
        dto.setDescription(v.getDescription());
        dto.setOccurredAt(v.getOccurredAt());
        dto.setHandled(v.getHandled());
        
        // 查询用户名和黑名单状态
        if (v.getUserId() != null) {
            User user = userMapper.selectById(v.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setIsBlacklisted(Boolean.TRUE.equals(user.getIsBlacklisted()));
            } else {
                dto.setUsername("未知用户");
                dto.setIsBlacklisted(false);
            }
        } else {
            dto.setUsername("未知用户");
            dto.setIsBlacklisted(false);
        }
        
        return dto;
    }

    /**
     * 批量转换
     */
    private List<ViolationDto> convertToDtoList(List<Violation> violations) {
        return violations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 手动触发检查未签到的违规（用于检查历史数据）
     * @param minutesBack 检查过去多少分钟的预约（默认24小时=1440分钟）
     */
    @Operation(summary = "手动检查未签到违规（历史补偿）")
    @PostMapping("/check-missing")
    public ResponseEntity<?> checkMissingViolations(@RequestParam(defaultValue = "1440") int minutesBack) {
        try {
            if (reservationScheduleService == null) {
                return ResponseEntity.status(500).body(Map.of("message", "检查服务未初始化，请重启后端服务"));
            }
            
            int count = reservationScheduleService.checkUncheckedReservations(minutesBack);
            return ResponseEntity.ok(Map.of(
                    "message", "检查完成，发现 " + count + " 个未签到的违规",
                    "count", count
            ));
        } catch (Exception e) {
            e.printStackTrace(); // 打印完整堆栈信息到控制台
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
            }
            return ResponseEntity.status(500).body(Map.of("message", "检查失败: " + errorMsg));
        }
    }

    /**
     * 将用户加入黑名单
     */
    @Operation(summary = "将用户加入黑名单")
    @PostMapping("/blacklist/{userId}")
    public ResponseEntity<?> addToBlacklist(@PathVariable Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }
        
        if (Boolean.TRUE.equals(user.getIsBlacklisted())) {
            return ResponseEntity.badRequest().body(Map.of("message", "该用户已在黑名单中"));
        }
        
        user.setIsBlacklisted(true);
        userMapper.updateById(user);
        
        log.info("管理员将用户 {} ({}) 加入黑名单", userId, user.getUsername());
        return ResponseEntity.ok(Map.of("message", "用户已加入黑名单", "userId", userId, "username", user.getUsername()));
    }
    
    /**
     * 将用户从黑名单中移除
     */
    @Operation(summary = "将用户从黑名单中移除")
    @DeleteMapping("/blacklist/{userId}")
    public ResponseEntity<?> removeFromBlacklist(@PathVariable Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }
        
        if (!Boolean.TRUE.equals(user.getIsBlacklisted())) {
            return ResponseEntity.badRequest().body(Map.of("message", "该用户不在黑名单中"));
        }
        
        user.setIsBlacklisted(false);
        userMapper.updateById(user);
        
        log.info("管理员将用户 {} ({}) 从黑名单中移除", userId, user.getUsername());
        return ResponseEntity.ok(Map.of("message", "用户已从黑名单中移除", "userId", userId, "username", user.getUsername()));
    }
    
    /**
     * 获取用户的黑名单状态
     */
    @Operation(summary = "获取用户黑名单状态")
    @GetMapping("/blacklist/{userId}")
    public ResponseEntity<?> getBlacklistStatus(@PathVariable Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }
        
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "username", user.getUsername() != null ? user.getUsername() : "未知用户",
                "isBlacklisted", Boolean.TRUE.equals(user.getIsBlacklisted())
        ));
    }

}
