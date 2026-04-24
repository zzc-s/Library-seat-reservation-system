package com.example.libraryseat.violation.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 违规信息DTO，包含用户信息
 */
@Data
public class ViolationDto {
    private Long id;
    private Long userId;
    private String username; // 用户名
    private Long reservationId;
    private String type;
    private String description;
    private LocalDateTime occurredAt;
    private Boolean handled;
    private Boolean isBlacklisted; // 是否在黑名单中
}
