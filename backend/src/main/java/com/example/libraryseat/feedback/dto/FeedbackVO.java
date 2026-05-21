package com.example.libraryseat.feedback.dto;

import java.time.LocalDateTime;

public record FeedbackVO(
        Long id,
        Long userId,
        String username,
        String avatarUrl,
        String content,
        String type,
        String status,
        String adminReply,
        String userReply,
        Boolean isPrivate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}