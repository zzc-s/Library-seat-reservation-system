package com.example.libraryseat.notice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.notice.entity.Notice;
import com.example.libraryseat.notice.mapper.NoticeMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Tag(name = "公告通知接口", description = "公告列表、创建编辑与删除")
@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    
    private final NoticeMapper noticeMapper;
    
    public NoticeController(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }
    
    // 获取所有已发布的公告（用户端）
    @Operation(summary = "获取已发布的公告列表（用户端）")
    @GetMapping("/public")
    public ResponseEntity<List<Notice>> getPublicNotices() {
        LocalDateTime now = LocalDateTime.now();
        List<Notice> notices = noticeMapper.selectList(
            new LambdaQueryWrapper<Notice>()
                .eq(Notice::getIsPublished, true)
                .and(w -> w.isNull(Notice::getExpiresAt).or().gt(Notice::getExpiresAt, now))
                .orderByDesc(Notice::getCreatedAt)
        );
        return ResponseEntity.ok(notices);
    }
    
    // 获取所有公告（管理员）
    @Operation(summary = "获取所有公告（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Notice>> getAllNotices() {
        List<Notice> notices = noticeMapper.selectList(
            new LambdaQueryWrapper<Notice>()
                .orderByDesc(Notice::getCreatedAt)
        );
        return ResponseEntity.ok(notices);
    }
    
    // 创建公告（管理员）
    @Operation(summary = "创建公告（管理员）")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createNotice(@RequestBody Map<String, Object> req) {
        Notice notice = new Notice();
        notice.setTitle((String) req.get("title"));
        notice.setContent((String) req.get("content"));
        notice.setType((String) req.getOrDefault("type", "NORMAL"));
        notice.setIsPublished((Boolean) req.getOrDefault("isPublished", false));
        notice.setExpiresAt(parseExpiresAt(req.get("expiresAt")));
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
        return ResponseEntity.ok(Map.of("message", "公告创建成功"));
    }
    
    // 更新公告（管理员）
    @Operation(summary = "更新公告（管理员）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateNotice(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "公告不存在"));
        }
        if (req.containsKey("title")) {
            notice.setTitle((String) req.get("title"));
        }
        if (req.containsKey("content")) {
            notice.setContent((String) req.get("content"));
        }
        if (req.containsKey("type")) {
            notice.setType((String) req.get("type"));
        }
        if (req.containsKey("isPublished")) {
            notice.setIsPublished((Boolean) req.get("isPublished"));
        }
        if (req.containsKey("expiresAt")) {
            notice.setExpiresAt(parseExpiresAt(req.get("expiresAt")));
        }
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
        return ResponseEntity.ok(Map.of("message", "公告更新成功"));
    }
    
    // 删除公告（管理员）
    @Operation(summary = "删除公告（管理员）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteNotice(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "公告删除成功"));
    }

    private static LocalDateTime parseExpiresAt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime dt) {
            return dt;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(t);
            } catch (DateTimeParseException e) {
                try {
                    return LocalDateTime.parse(t.replace(" ", "T"));
                } catch (DateTimeParseException e2) {
                    return null;
                }
            }
        }
        return null;
    }
}
