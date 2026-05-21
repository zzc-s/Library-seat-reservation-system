package com.example.libraryseat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public enum ReservationStatus {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    ACTIVE("ACTIVE"),
    CANCELLED("CANCELLED"),
    FINISHED("FINISHED");

    @EnumValue
    @JsonValue
    private final String value;

    ReservationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final List<ReservationStatus> EFFECTIVE = List.of(ACTIVE, CONFIRMED, PENDING);

    public boolean isEffective() {
        return EFFECTIVE.contains(this);
    }
}