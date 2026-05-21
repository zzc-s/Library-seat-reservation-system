package com.example.libraryseat.borrow.dto;

import java.time.LocalDateTime;

public record BorrowVO(
        Long id,
        Long userId,
        Long bookId,
        String bookTitle,
        String bookAuthor,
        String username,
        String email,
        LocalDateTime borrowDate,
        LocalDateTime returnDate,
        LocalDateTime dueDate,
        String status,
        Integer warningCount,
        LocalDateTime lastWarningAt
) {}