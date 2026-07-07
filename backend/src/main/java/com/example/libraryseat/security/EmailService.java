package com.example.libraryseat.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.springframework.scheduling.annotation.Async;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:no-reply@example.com}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    //发验证码
    public void sendCode(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        try {
            mailSender.send(message);
            log.info("邮件发送成功 to={}, subject={}", to, subject);
        } catch (MailException e) {
            // 检查是否是收件人地址问题
            String errorMsg = e.getMessage();
            Throwable cause = e.getCause();
            
            // 打印完整的错误堆栈以便调试
            log.error("发送邮件失败 to={}, from={}, error={}", to, from, errorMsg, e);
            if (cause != null) {
                log.error("错误原因: {}", cause.getClass().getName());
                log.error("错误详情: {}", cause.getMessage());
            }
            
            if (cause instanceof SMTPSendFailedException) {
                SMTPSendFailedException smtpEx = (SMTPSendFailedException) cause;
                String smtpMsg = smtpEx.getMessage();
                log.error("SMTP 错误详情: {}", smtpMsg);
                
                if (smtpMsg != null) {
                    if (smtpMsg.contains("550") || smtpMsg.contains("non-existent")) {
                        // 550 错误通常表示收件人地址不存在或无效
                        log.warn("收件人邮箱可能不存在或无效 to={}", to);
                        throw new RuntimeException("邮箱地址无效或不存在，请检查邮箱地址是否正确");
                    } else if (smtpMsg.contains("535") || smtpMsg.contains("authentication")) {
                        // 535 错误表示认证失败
                        log.error("SMTP 认证失败，请检查邮箱配置");
                        throw new RuntimeException("邮件服务配置错误，请联系管理员");
                    } else if (smtpMsg.contains("553") || smtpMsg.contains("sender")) {
                        // 553 错误表示发件人地址问题
                        log.error("发件人地址配置错误 from={}", from);
                        throw new RuntimeException("邮件服务配置错误，请联系管理员");
                    }
                }
            }
            
            throw new RuntimeException("验证码发送失败，请稍后重试");
        } catch (Exception e) {
            log.error("发送邮件时发生未知错误 to={}, from={}, error={}", to, from, e.getMessage(), e);
            throw new RuntimeException("验证码发送失败，请稍后重试");
        }
    }

    /**
     * 异步发送验证码邮件：避免接口等待 SMTP 完整发送流程导致前端超时。(防止接口堵塞)
     * 该方法只负责提交发送并记录日志；发送失败不会再反向影响 HTTP 响应。
     */
    @Async
    public void sendCodeAsync(String to, String subject, String content) {
        try {
            sendCode(to, subject, content);
        } catch (RuntimeException e) {
            log.warn("异步发送验证码邮件失败 to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }
    
    /**
     * 异步发送提醒邮件（静默失败，不影响主流程）
     */
    @Async
    public void sendReminderAsync(String to, String subject, String content) {
        try {
            sendReminder(to, subject, content);
        } catch (RuntimeException e) {
            log.warn("异步发送提醒邮件失败 to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }

    /**
     * 发送预约提醒邮件（静默失败，不影响主流程）
     * @param to 收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 是否发送成功
     */
    public boolean sendReminder(String to, String subject, String content) {
        if (to == null || to.isBlank()) {
            log.warn("邮箱地址为空，跳过发送提醒邮件");
            return false;
        }
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        try {
            mailSender.send(message);
            log.info("预约提醒邮件发送成功 to={}, subject={}", to, subject);
            return true;
        } catch (Exception e) {
            // 提醒邮件发送失败不影响主流程，只记录日志
            log.warn("预约提醒邮件发送失败 to={}, subject={}, error={}", to, subject, e.getMessage());
            return false;
        }
    }
}

