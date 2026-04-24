package com.example.libraryseat.book.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.book.entity.Book;
import com.example.libraryseat.book.entity.Subscription;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.book.mapper.SubscriptionMapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "图书订阅接口", description = "未上架图书订阅与订阅列表")
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionMapper subscriptionMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;

    public SubscriptionController(SubscriptionMapper subscriptionMapper, BookMapper bookMapper, UserMapper userMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.bookMapper = bookMapper;
        this.userMapper = userMapper;
    }

    /**
     * 获取当前用户的订阅列表
     */
    @Operation(summary = "获取当前用户的订阅列表")
    @GetMapping
    public List<Map<String, Object>> getMySubscriptions() {
        Long userId = currentUserId();
        List<Subscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<Subscription>()
                        .eq(Subscription::getUserId, userId)
                        .orderByDesc(Subscription::getCreatedAt));

        return subscriptions.stream().map(subscription -> {
            Book book = bookMapper.selectById(subscription.getBookId());
            Map<String, Object> result = new HashMap<>();
            result.put("id", subscription.getId());
            result.put("bookId", subscription.getBookId());
            result.put("createdAt", subscription.getCreatedAt());
            if (book != null) {
                result.put("book", book);
            }
            return result;
        }).collect(Collectors.toList());
    }

    /**
     * 获取当前用户订阅的图书ID列表（用于前端判断是否已订阅）
     */
    @Operation(summary = "获取当前用户订阅的图书ID列表")
    @GetMapping("/ids")
    public List<Long> getSubscriptionBookIds() {
        Long userId = currentUserId();
        List<Subscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<Subscription>()
                        .eq(Subscription::getUserId, userId)
                        .select(Subscription::getBookId));
        return subscriptions.stream().map(Subscription::getBookId).collect(Collectors.toList());
    }

    /**
     * 添加订阅（只能订阅未上架的图书）
     */
    @Operation(summary = "订阅未上架图书")
    @PostMapping
    public ResponseEntity<?> addSubscription(@RequestBody Map<String, Object> req) {
        Long userId = currentUserId();
        Long bookId = Long.valueOf(req.get("bookId").toString());

        // 检查图书是否存在
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "图书不存在"));
        }

        // 检查图书是否已上架，如果已上架则不能订阅
        if (book.getIsBorrowable() != null && book.getIsBorrowable()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书已上架，请直接借阅或收藏"));
        }

        // 检查是否已经订阅
        Subscription existing = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<Subscription>()
                        .eq(Subscription::getUserId, userId)
                        .eq(Subscription::getBookId, bookId));
        if (existing != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "已经订阅过该图书"));
        }

        // 创建订阅记录
        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setBookId(bookId);
        subscription.setCreatedAt(LocalDateTime.now());
        subscriptionMapper.insert(subscription);

        log.info("用户 {} 订阅了图书 {}", userId, bookId);
        return ResponseEntity.ok(Map.of("message", "订阅成功，图书上架后会通知您", "subscription", subscription));
    }

    /**
     * 取消订阅
     */
    @Operation(summary = "取消订阅")
    @DeleteMapping("/{bookId}")
    public ResponseEntity<?> removeSubscription(@PathVariable Long bookId) {
        Long userId = currentUserId();

        // 查找订阅记录
        Subscription subscription = subscriptionMapper.selectOne(
                new LambdaQueryWrapper<Subscription>()
                        .eq(Subscription::getUserId, userId)
                        .eq(Subscription::getBookId, bookId));

        if (subscription == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "未订阅该图书"));
        }

        // 删除订阅记录
        subscriptionMapper.deleteById(subscription.getId());

        log.info("用户 {} 取消订阅了图书 {}", userId, bookId);
        return ResponseEntity.ok(Map.of("message", "取消订阅成功"));
    }

    /**
     * 获取当前用户ID
     */
    private Long currentUserId() {
        try {
            Object uid = ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                    .getRequest().getAttribute("uid");
            if (uid instanceof Number) {
                return ((Number) uid).longValue();
            }
        } catch (Exception e) {
            log.debug("无法从 request attribute 获取 uid: {}", e.getMessage());
        }
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String username = auth.getName();
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null) {
                log.debug("从数据库查询到用户ID: {} (用户名: {})", user.getId(), username);
                return user.getId();
            }
        }
        
        throw new IllegalStateException("无法获取用户ID，用户: " + (auth != null ? auth.getName() : "unknown"));
    }
}
