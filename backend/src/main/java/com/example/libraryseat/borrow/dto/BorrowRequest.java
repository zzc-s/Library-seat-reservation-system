package com.example.libraryseat.borrow.dto;

import java.time.LocalDateTime;

public record BorrowRequest(
        Long bookId,
        LocalDateTime dueDate
) {}