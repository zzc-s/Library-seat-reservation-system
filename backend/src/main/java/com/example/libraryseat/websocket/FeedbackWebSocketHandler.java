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
 * 反馈通知 WebSocket 处理器
 * 用于向管理员推送新反馈和反馈更新通知
 */
@Slf4j
@Component
public class FeedbackWebSocketHandler extends TextWebSocketHandler {

    // 存储所有连接的 WebSocket 会话（管理员）
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 连接建立时调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        log.info("反馈 WebSocket 连接建立: sessionId={}, 当前连接数={}", sessionId, sessions.size());
        
        // 发送欢迎消息
        sendMessage(session, Map.of(
            "type", "connected",
            "message", "反馈通知 WebSocket 连接成功"
        ));
    }

    /**
     * 连接关闭时调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        log.info("反馈 WebSocket 连接关闭: sessionId={}, status={}, 当前连接数={}", sessionId, status, sessions.size());
    }

    /**
     * 处理接收到的消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("收到反馈 WebSocket 消息: sessionId={}, message={}", session.getId(), message.getPayload());
        // 可以在这里处理客户端发送的消息，例如心跳检测
    }

    /**
     * 处理错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("反馈 WebSocket 传输错误: sessionId={}", session.getId(), exception);
        sessions.remove(session.getId());
    }

    /**
     * 向所有连接的管理员广播新反馈通知
     */
    public void broadcastNewFeedback(Long feedbackId, String username, String content, String type) {
        Map<String, Object> message = Map.of(
            "type", "newFeedback",
            "feedbackId", feedbackId,
            "username", username != null ? username : "未知用户",
            "content", content != null && content.length() > 50 ? content.substring(0, 50) + "..." : (content != null ? content : ""),
            "feedbackType", type != null ? type : "OTHER",
            "timestamp", System.currentTimeMillis()
        );
        broadcastMessage(message);
    }

    /**
     * 向所有连接的管理员广播反馈回复通知
     */
    public void broadcastFeedbackReply(Long feedbackId, String adminReply) {
        Map<String, Object> message = Map.of(
            "type", "feedbackReply",
            "feedbackId", feedbackId,
            "adminReply", adminReply != null ? adminReply : "",
            "timestamp", System.currentTimeMillis()
        );
        broadcastMessage(message);
    }

    /**
     * 向所有连接的管理员广播反馈状态更新
     */
    public void broadcastFeedbackStatusUpdate(Long feedbackId, String status) {
        Map<String, Object> message = Map.of(
            "type", "feedbackStatusUpdate",
            "feedbackId", feedbackId,
            "status", status != null ? status : "PENDING",
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
            log.error("序列化反馈 WebSocket 消息失败", e);
            return;
        }

        TextMessage textMessage = new TextMessage(jsonMessage);
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            } catch (IOException e) {
                log.error("发送反馈 WebSocket 消息失败: sessionId={}", session.getId(), e);
            }
        });
        log.debug("广播反馈 WebSocket 消息: message={}, 接收客户端数={}", message, sessions.size());
    }

    /**
     * 向指定会话发送消息
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            log.error("发送反馈 WebSocket 消息失败: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
