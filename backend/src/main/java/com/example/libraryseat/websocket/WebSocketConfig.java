package com.example.libraryseat.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置类
 * 用于实时推送座位状态变更
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SeatStatusWebSocketHandler seatStatusWebSocketHandler;
    private final FeedbackWebSocketHandler feedbackWebSocketHandler;

    public WebSocketConfig(SeatStatusWebSocketHandler seatStatusWebSocketHandler,
                          FeedbackWebSocketHandler feedbackWebSocketHandler) {
        this.seatStatusWebSocketHandler = seatStatusWebSocketHandler;
        this.feedbackWebSocketHandler = feedbackWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册座位状态 WebSocket 处理器
        registry.addHandler(seatStatusWebSocketHandler, "/ws/seat-status")
                .setAllowedOrigins("*");
        
        // 注册反馈通知 WebSocket 处理器
        registry.addHandler(feedbackWebSocketHandler, "/ws/feedback")
                .setAllowedOrigins("*"); // 允许所有来源，生产环境应该限制为前端域名
    }
}
