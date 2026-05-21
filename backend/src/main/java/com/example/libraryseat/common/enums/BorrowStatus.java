package com.example.libraryseat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BorrowStatus {
    BORROWED("BORROWED"),
    RETURNED("RETURNED"),
    OVERDUE("OVERDUE");

    @EnumValue
    @JsonValue
    private final String value;

    BorrowStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}