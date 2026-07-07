package com.example.libraryseat.feedback.controller;

import com.example.libraryseat.common.util.SecurityUtil;
import com.example.libraryseat.feedback.dto.FeedbackRequest;
import com.example.libraryseat.feedback.dto.FeedbackVO;
import com.example.libraryseat.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    public List<FeedbackVO> getMyFeedbacks() {
        return feedbackService.getMyFeedbacks(securityUtil.currentUserId());
    }

    @Operation(summary = "提交反馈")
    @PostMapping
    public Map<String, String> createFeedback(@RequestBody FeedbackRequest req) {
        feedbackService.createFeedback(req, securityUtil.currentUserId());
        return Map.of("message", "反馈提交成功");
    }

    @Operation(summary = "获取公开反馈列表")
    @GetMapping("/public")
    public List<FeedbackVO> getPublicFeedbacks() {
        Long currentUserId = null;
        try {
            currentUserId = securityUtil.currentUserId();
        } catch (IllegalStateException ignored) {
        }
        return feedbackService.getPublicFeedbacks(currentUserId);
    }

    @Operation(summary = "获取所有反馈（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<FeedbackVO> getAllFeedbacks() {
        return feedbackService.getAllFeedbacks();
    }

    @Operation(summary = "管理员回复反馈")
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> replyFeedback(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        feedbackService.replyFeedback(id, (String) req.get("adminReply"));
        return Map.of("message", "回复成功", "success", true);
    }

    @Operation(summary = "关闭反馈（管理员）")
    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> closeFeedback(@PathVariable Long id) {
        feedbackService.closeFeedback(id);
        return Map.of("message", "反馈已关闭");
    }

    @Operation(summary = "用户补充回复")
    @PutMapping("/{id}/user-reply")
    public Map<String, String> userReply(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        feedbackService.userReply(id, (String) req.get("userReply"), securityUtil.currentUserId());
        return Map.of("message", "回复成功");
    }

    @Operation(summary = "删除自己的反馈")
    @DeleteMapping("/{id}")
    public Map<String, String> deleteFeedback(@PathVariable Long id) {
        feedbackService.deleteFeedback(id, securityUtil.currentUserId());
        return Map.of("message", "反馈已删除");
    }
}