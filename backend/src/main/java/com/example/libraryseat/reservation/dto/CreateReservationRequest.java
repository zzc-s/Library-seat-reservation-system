package com.example.libraryseat.reservation.dto;

import java.time.LocalDateTime;

public record CreateReservationRequest(
        Long seatId,
        LocalDateTime startTime,
        LocalDateTime endTime
) {}