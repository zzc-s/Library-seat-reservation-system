package com.example.libraryseat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FeedbackStatus {
    PENDING("PENDING"),
    PROCESSED("PROCESSED"),
    CLOSED("CLOSED");

    @EnumValue
    @JsonValue
    private final String value;

    FeedbackStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}