package com.example.libraryseat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 应用信息：记录应用启动时间，用于检测后端是否重启
 */
@Slf4j
@Component
public class AppInfo {
    private static long startupTime = 0L;

    @PostConstruct 
    public void init() {
        startupTime = System.currentTimeMillis();
        log.info("应用启动时间已记录: {}", startupTime);
    }

    public static long getStartupTime() {
        // 如果还没初始化，返回当前时间（防止返回0）
        if (startupTime == 0L) {
            startupTime = System.currentTimeMillis();
            log.warn("AppInfo 未正确初始化，使用当前时间: {}", startupTime);
        }
        return startupTime;
    }
}
