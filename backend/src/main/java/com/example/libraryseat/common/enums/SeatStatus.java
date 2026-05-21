package com.example.libraryseat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SeatStatus {
    FREE("FREE"),
    IDLE("IDLE"),
    RESERVED("RESERVED"),
    OCCUPIED("OCCUPIED"),
    BROKEN("BROKEN"),
    FAULT("FAULT");

    @EnumValue
    @JsonValue
    private final String value;

    SeatStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isFault() {
        return this == BROKEN || this == FAULT;
    }

    public boolean isUnavailable() {
        return this == RESERVED || this == OCCUPIED || this == BROKEN || this == FAULT;
    }
}