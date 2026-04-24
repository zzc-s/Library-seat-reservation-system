package com.example.libraryseat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 座位状态 WebSocket 处理器
 * 用于向所有连接的客户端推送座位状态变更
 */
@Slf4j
@Component
public class SeatStatusWebSocketHandler extends TextWebSocketHandler {

    // 存储所有连接的 WebSocket 会话
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 连接建立时调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("WebSocket 连接建立: sessionId={}, 当前连接数={}", sessionId, sessions.size());
        
        // 发送欢迎消息
        sendMessage(session, Map.of(
            "type", "connected",
            "message", "WebSocket 连接成功"
        ));
    }

    /**
     * 连接关闭时调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("WebSocket 连接关闭: sessionId={}, status={}, 当前连接数={}", sessionId, status, sessions.size());
    }

    /**
     * 处理接收到的消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("收到 WebSocket 消息: sessionId={}, message={}", session.getId(), message.getPayload());
        // 可以在这里处理客户端发送的消息，例如心跳检测
    }

    /**
     * 处理错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket 传输错误: sessionId={}", session.getId(), exception);
        sessions.remove(session.getId());
    }

    /**
     * 向所有连接的客户端广播座位状态变更
     */
    public void broadcastSeatStatusUpdate(Long seatId, Integer status, String statusText) {
        Map<String, Object> message = Map.of(
            "type", "seatStatusUpdate",
            "seatId", seatId,
            "status", status,
            "statusText", statusText != null ? statusText : "",
            "timestamp", System.currentTimeMillis()
        );
        broadcastMessage(message);
    }

    /**
     * 广播“需要刷新座位数据”的信号（用于区域/布局等元数据变更）。
     * 前端收到后可重新拉取座位列表/重新分组，以确保跨浏览器即时同步。
     */
    public void broadcastSeatRefresh(String reason) {
        Map<String, Object> message = Map.of(
            "type", "seatStatusRefresh",
            "reason", reason != null ? reason : "unknown",
            "timestamp", System.currentTimeMillis()
        );
        broadcastMessage(message);
    }

    /**
     * 广播小组协同相关变更（小组、成员、协同预约状态等）
     */
    public void broadcastGroupChanged(String action, Long groupId) {
        Map<String, Object> message = Map.of(
                "type", "groupChanged",
                "action", action != null ? action : "updated",
                "groupId", groupId != null ? groupId : -1L,
                "timestamp", System.currentTimeMillis()
        );
        broadcastMessage(message);
    }

    /**
     * 向所有连接的客户端广播消息
     */
    public void broadcastMessage(Map<String, Object> message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("序列化 WebSocket 消息失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(jsonMessage);
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.error("发送 WebSocket 消息失败: sessionId={}", session.getId(), e);
            }
        });
        log.debug("广播 WebSocket 消息: message={}, 接收客户端数={}", message, sessions.size());
    }

    /**
     * 向指定会话发送消息
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            log.error("发送 WebSocket 消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
