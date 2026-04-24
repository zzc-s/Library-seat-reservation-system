package com.example.libraryseat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class RedisTokenService {
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private final StringRedisTemplate redis;

    public RedisTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 将 token 加入黑名单
     * 如果 Redis 不可用，记录警告但不抛出异常，允许系统继续运行
     */
    public void blacklistToken(String token, Duration ttl) {
        try {
            redis.opsForValue().set(BLACKLIST_PREFIX + token, "1", ttl);
        } catch (Exception e) {
            log.warn("Redis 不可用，无法将 token 加入黑名单: {}", e.getMessage());
            // 不抛出异常，允许系统继续运行（只是无法使用黑名单功能）
        }
    }

    /**
     * 检查 token 是否在黑名单中
     * 如果 Redis 不可用，返回 false（认为 token 不在黑名单中，允许继续）
     */
    public boolean isBlacklisted(String token) {
        try {
            Boolean hasKey = redis.hasKey(BLACKLIST_PREFIX + token);
            return hasKey != null && hasKey;
        } catch (Exception e) {
            log.warn("Redis 不可用，无法检查 token 黑名单，允许 token 继续使用: {}", e.getMessage());
            // Redis 不可用时，返回 false（认为 token 不在黑名单中）
            return false;
        }
    }
}


