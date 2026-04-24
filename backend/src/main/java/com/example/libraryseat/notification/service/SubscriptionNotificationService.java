package com.example.libraryseat.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.book.entity.Book;
import com.example.libraryseat.book.entity.Subscription;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.book.mapper.SubscriptionMapper;
import com.example.libraryseat.notification.entity.UserNotification;
import com.example.libraryseat.notification.mapper.UserNotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅通知服务：当图书上架时，通知订阅该图书的用户
 */
@Slf4j
@Service
public class SubscriptionNotificationService {
    private final SubscriptionMapper subscriptionMapper;
    private final BookMapper bookMapper;
    private final UserNotificationMapper notificationMapper;

    public SubscriptionNotificationService(
            SubscriptionMapper subscriptionMapper,
            BookMapper bookMapper,
            UserNotificationMapper notificationMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.bookMapper = bookMapper;
        this.notificationMapper = notificationMapper;
    }

    /**
     * 检查并处理图书上架通知
     * 当图书从未上架变为上架时，给所有订阅该图书的用户发送通知
     * 
     * @param bookId 图书ID
     * @param oldIsBorrowable 更新前的isBorrowable状态
     * @param newIsBorrowable 更新后的isBorrowable状态
     */
    @Transactional
    public void checkAndNotifyBookAvailable(Long bookId, Boolean oldIsBorrowable, Boolean newIsBorrowable) {
        // 只有当图书从未上架（false/null）变为上架（true）时才通知
        boolean wasNotAvailable = (oldIsBorrowable == null || !oldIsBorrowable);
        boolean isNowAvailable = (newIsBorrowable != null && newIsBorrowable);
        
        if (wasNotAvailable && isNowAvailable) {
            Book book = bookMapper.selectById(bookId);
            if (book == null) {
                log.warn("图书 {} 不存在，跳过订阅通知", bookId);
                return;
            }
            
            // 查找所有订阅该图书的用户
            List<Subscription> subscriptions = subscriptionMapper.selectList(
                    new LambdaQueryWrapper<Subscription>()
                            .eq(Subscription::getBookId, bookId));
            
            log.info("图书 {}（{}）已上架，找到 {} 个订阅用户，准备发送通知", 
                    bookId, book.getTitle(), subscriptions.size());
            
            // 为每个订阅用户创建通知
            for (Subscription subscription : subscriptions) {
                UserNotification notification = new UserNotification();
                notification.setUserId(subscription.getUserId());
                notification.setType("BOOK_AVAILABLE");
                notification.setTitle("您订阅的图书已上架");
                notification.setContent(String.format("您订阅的图书《%s》已经上架，现在可以借阅了！", book.getTitle()));
                notification.setRelatedBookId(bookId);
                notification.setIsRead(false);
                notification.setCreatedAt(LocalDateTime.now());
                
                notificationMapper.insert(notification);
                
                log.info("已为用户 {} 创建图书上架通知（图书：{}）", 
                        subscription.getUserId(), book.getTitle());
            }
            
            // 通知创建完成后，可以选择是否删除订阅记录
            // 这里我们保留订阅记录，用户可以在通知中看到历史订阅
            // 如果希望自动取消订阅，可以在这里删除：subscriptionMapper.deleteBatchIds(...)
        }
    }
}
