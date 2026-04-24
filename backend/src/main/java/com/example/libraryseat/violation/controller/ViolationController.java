package com.example.libraryseat.violation.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.libraryseat.violation.dto.ViolationDto;
import com.example.libraryseat.violation.entity.Violation;
import com.example.libraryseat.violation.mapper.ViolationMapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 普通用户违规记录控制器
 * 允许用户查看自己的违规记录
 */
@Slf4j
@Tag(name = "违规记录接口（用户）", description = "当前用户违规记录与统计")
@RestController
@RequestMapping("/api/violations")
public class ViolationController {
    private final ViolationMapper violationMapper;
    private final UserMapper userMapper;

    public ViolationController(ViolationMapper violationMapper, UserMapper userMapper) {
        this.violationMapper = violationMapper;
        this.userMapper = userMapper;
    }

    /**
     * 分页获取当前用户的违规记录（默认每页 10 条，最大 50）
     */
    @Operation(summary = "获取当前用户的违规记录（分页）")
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyViolations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        if (page < 1) {
            page = 1;
        }
        size = Math.min(Math.max(size, 1), 50);

        Page<Violation> mpPage = new Page<>(page, size);
        Page<Violation> result = violationMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<Violation>()
                        .eq(Violation::getUserId, user.getId())
                        .orderByDesc(Violation::getOccurredAt));

        List<ViolationDto> dtos = result.getRecords().stream().map(v -> {
            ViolationDto dto = new ViolationDto();
            dto.setId(v.getId());
            dto.setUserId(v.getUserId());
            dto.setReservationId(v.getReservationId());
            dto.setType(v.getType());
            dto.setDescription(v.getDescription());
            dto.setOccurredAt(v.getOccurredAt());
            dto.setHandled(v.getHandled() != null && v.getHandled());
            dto.setUsername(user.getUsername());
            return dto;
        }).collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("records", dtos);
        body.put("total", result.getTotal());
        body.put("page", result.getCurrent());
        body.put("size", result.getSize());
        body.put("pages", result.getPages());
        return ResponseEntity.ok(body);
    }

    /**
     * 获取当前用户的违规统计
     */
    @Operation(summary = "获取当前用户的违规统计")
    @GetMapping("/my/stats")
    public ResponseEntity<Map<String, Object>> getMyViolationStats() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<Violation> violations = violationMapper.selectList(
                new LambdaQueryWrapper<Violation>()
                        .eq(Violation::getUserId, user.getId())
        );
        
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", violations.size());
        
        long unhandledCount = violations.stream()
                .filter(v -> v.getHandled() == null || !v.getHandled())
                .count();
        stats.put("unhandled", unhandledCount);
        stats.put("handled", violations.size() - unhandledCount);
        
        return ResponseEntity.ok(stats);
    }

}

