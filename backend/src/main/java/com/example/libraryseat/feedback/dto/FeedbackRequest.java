package com.example.libraryseat.feedback.dto;

public record FeedbackRequest(
        String content,
        String type,
        Boolean isPrivate
) {
    public FeedbackRequest {
        if (type == null) type = "OTHER";
        if (isPrivate == null) isPrivate = false;
    }
}