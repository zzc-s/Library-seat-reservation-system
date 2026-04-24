package com.example.libraryseat.auth.dto;

public class AuthDtos {
    public record RegisterRequest(String username, String password, String email) {}
    public record LoginRequest(String username, String password) {}
    public record TokenResponse(String token, Long serverStartTime) {}
    public record ForgotRequest(String email) {}
    public record ResetRequest(String email, String code, String newPassword) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    /** 注册：发送验证码 */
    public record RegisterCodeRequest(String email) {}

    /** 自助注销：校验当前登录密码 */
    public record DeleteAccountRequest(String password) {}
}


