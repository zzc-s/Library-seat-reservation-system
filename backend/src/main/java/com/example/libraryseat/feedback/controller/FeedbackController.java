package com.example.libraryseat.feedback.controller;

import com.example.libraryseat.common.util.SecurityUtil;
import com.example.libraryseat.feedback.dto.FeedbackRequest;
import com.example.libraryseat.feedback.dto.FeedbackVO;
import com.example.libraryseat.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "反馈与建议接口", description = "用户提交反馈、查看反馈及管理员回复处理")
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final SecurityUtil securityUtil;

    public FeedbackController(FeedbackService feedbackService, SecurityUtil securityUtil) {
        this.feedbackService = feedbackService;
        this.securityUtil = securityUtil;
    }

    @Operation(summary = "获取当前用户的反馈列表")
    @GetMapping("/my")
    public ResponseEntity<List<FeedbackVO>> getMyFeedbacks() {
        return ResponseEntity.ok(feedbackService.getMyFeedbacks(securityUtil.currentUserId()));
    }

    @Operation(summary = "提交反馈")
    @PostMapping
    public ResponseEntity<?> createFeedback(@RequestBody FeedbackRequest req) {
        return feedbackService.createFeedback(req, securityUtil.currentUserId());
    }

    @Operation(summary = "获取公开反馈列表")
    @GetMapping("/public")
    public ResponseEntity<List<FeedbackVO>> getPublicFeedbacks() {
        Long currentUserId = null;
        try {
            currentUserId = securityUtil.currentUserId();
        } catch (IllegalStateException ignored) {
        }
        return ResponseEntity.ok(feedbackService.getPublicFeedbacks(currentUserId));
    }

    @Operation(summary = "获取所有反馈（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FeedbackVO>> getAllFeedbacks() {
        return ResponseEntity.ok(feedbackService.getAllFeedbacks());
    }

    @Operation(summary = "管理员回复反馈")
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> replyFeedback(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        return feedbackService.replyFeedback(id, (String) req.get("adminReply"));
    }

    @Operation(summary = "关闭反馈（管理员）")
    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> closeFeedback(@PathVariable Long id) {
        return feedbackService.closeFeedback(id);
    }

    @Operation(summary = "用户补充回复")
    @PutMapping("/{id}/user-reply")
    public ResponseEntity<?> userReply(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        return feedbackService.userReply(id, (String) req.get("userReply"), securityUtil.currentUserId());
    }

    @Operation(summary = "删除自己的反馈")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFeedback(@PathVariable Long id) {
        return feedbackService.deleteFeedback(id, securityUtil.currentUserId());
    }
}
