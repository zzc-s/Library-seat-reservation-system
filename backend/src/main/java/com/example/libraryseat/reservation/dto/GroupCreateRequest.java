package com.example.libraryseat.reservation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GroupCreateRequest(
        List<Long> seatIds,
        LocalDateTime startTime,
        LocalDateTime endTime
) {}