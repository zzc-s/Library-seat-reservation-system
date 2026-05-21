package com.example.libraryseat.seat.dto;

public record SeatVO(
        Long id,
        String seatCode,
        String building,
        Integer floor,
        String label,
        Integer row,
        Integer col,
        String area,
        Boolean hasPower,
        Boolean isWindow,
        String zone,
        String status
) {}