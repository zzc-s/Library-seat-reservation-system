package com.example.libraryseat.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * 从配置的 secret 构建 HS256 签名密钥（支持 Base64 或明文，长度须 >= 256 bit）。
 */
public final class JwtSigningKeyFactory {

    private JwtSigningKeyFactory() {
    }

    public static Key fromSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret 未配置：请在 application.yml 设置 security.jwt.secret，"
                            + "或通过环境变量 JWT_SECRET 注入（至少 32 个字符）");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret.trim());
            if (keyBytes.length < 32) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (RuntimeException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret 长度不足：HS256 需要至少 32 字节（256 bit），当前为 "
                            + keyBytes.length + " 字节");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
