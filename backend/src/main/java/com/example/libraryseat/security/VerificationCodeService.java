package com.example.libraryseat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Slf4j
@Service
public class VerificationCodeService {

    private static final String KEY_PREFIX = "email:code:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private final StringRedisTemplate redis;
    private final Random random = new Random();

    public VerificationCodeService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 生成并存储验证码
     * 如果 Redis 不可用，记录警告但不抛出异常
     */
    public String generateAndStore(String email) {
        String code = String.format("%06d", random.nextInt(999999));
        try {
            redis.opsForValue().set(KEY_PREFIX + email, code, DEFAULT_TTL);
        } catch (Exception e) {
            log.warn("Redis 不可用，无法存储验证码: {}", e.getMessage());
            // 不抛出异常，允许系统继续运行（只是验证码功能不可用）
        }
        return code;
    }

    /**
     * 验证验证码
     * 如果 Redis 不可用，返回 false（验证失败）
     */
    public boolean verify(String email, String code) {
        try {
            String cached = redis.opsForValue().get(KEY_PREFIX + email);
            return cached != null && cached.equals(code);
        } catch (Exception e) {
            log.warn("Redis 不可用，无法验证验证码: {}", e.getMessage());
            // Redis 不可用时，返回 false（验证失败）
            return false;
        }
    }

    /**
     * 清除验证码
     * 如果 Redis 不可用，记录警告但不抛出异常
     */
    public void clear(String email) {
        try {
            redis.delete(KEY_PREFIX + email);
        } catch (Exception e) {
            log.warn("Redis 不可用，无法清除验证码: {}", e.getMessage());
            // 不抛出异常，允许系统继续运行
        }
    }
}

