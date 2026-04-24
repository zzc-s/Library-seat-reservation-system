package com.example.libraryseat.borrow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import com.example.libraryseat.book.entity.Book;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.security.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "图书借阅与逾期管理接口", description = "用户借阅、管理员归还与逾期警告")
@RestController
@RequestMapping("/api/borrows")
public class BorrowController {
    
    private final BorrowMapper borrowMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;
    
    public BorrowController(BorrowMapper borrowMapper, BookMapper bookMapper, UserMapper userMapper, EmailService emailService) {
        this.borrowMapper = borrowMapper;
        this.bookMapper = bookMapper;
        this.userMapper = userMapper;
        this.emailService = emailService;
    }
    
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String username = auth.getName();
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null) {
                return user.getId();
            }
        }
        throw new IllegalStateException("无法获取当前用户ID");
    }
    
    // 获取当前用户的借阅记录
    @Operation(summary = "获取当前用户的借阅记录")
    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> getMyBorrows() {
        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        
        List<Borrow> borrows = borrowMapper.selectList(
            new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getUserId, userId)
                .orderByDesc(Borrow::getBorrowDate)
        );
        
        // 自动检查并更新逾期状态
        for (Borrow borrow : borrows) {
            if ("BORROWED".equals(borrow.getStatus()) && borrow.getDueDate().isBefore(now)) {
                borrow.setStatus("OVERDUE");
                borrow.setUpdatedAt(now);
                borrowMapper.updateById(borrow);
            }
        }
        
        List<Map<String, Object>> result = borrows.stream().map(borrow -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", borrow.getId());
            item.put("bookId", borrow.getBookId());
            Book book = bookMapper.selectById(borrow.getBookId());
            if (book != null) {
                item.put("bookTitle", book.getTitle());
                item.put("bookAuthor", book.getAuthor());
            }
            item.put("borrowDate", borrow.getBorrowDate());
            item.put("returnDate", borrow.getReturnDate());
            item.put("dueDate", borrow.getDueDate());
            // 如果状态是BORROWED但已逾期，返回OVERDUE
            String status = borrow.getStatus();
            if ("BORROWED".equals(status) && borrow.getDueDate().isBefore(now)) {
                status = "OVERDUE";
            }
            item.put("status", status);
            return item;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // 借阅图书
    @Operation(summary = "借阅图书")
    @PostMapping
    public ResponseEntity<?> borrowBook(@RequestBody Map<String, Object> req) {
        Long bookId = Long.valueOf(req.get("bookId").toString());
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "图书不存在"));
        }
        if (!book.getIsBorrowable()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书不可借阅"));
        }
        
        // 检查库存
        Integer stock = book.getStock();
        if (stock == null || stock <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书库存不足，无法借阅"));
        }
        //调用 currentUserId() 方法从JWT token中获取当前登录用户ID
        Long userId = currentUserId();
        
        // 检查用户是否已有未归还的该图书借阅记录
        List<Borrow> existing = borrowMapper.selectList(
            new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getBookId, bookId)
                .eq(Borrow::getUserId, userId)
                .eq(Borrow::getStatus, "BORROWED")
        );
        if (!existing.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "您已借阅该图书，尚未归还"));
        }
        
        // 解析归还日期（如果提供）
        LocalDateTime dueDate;
        if (req.containsKey("dueDate") && req.get("dueDate") != null) {
            String dueDateStr = req.get("dueDate").toString();
            try {
                // 处理ISO格式的日期字符串（可能包含时区信息）
                // 例如：2026-01-15T23:59:59.000Z 或 2026-01-15T23:59:59
                if (dueDateStr.contains("T")) {
                    // 如果包含时区信息（Z或+/-），先移除
                    if (dueDateStr.endsWith("Z")) {
                        dueDateStr = dueDateStr.replace("Z", "");
                    } else if (dueDateStr.contains("+") || dueDateStr.matches(".*-\\d{2}:\\d{2}$")) {
                        // 移除时区偏移量（如 +08:00 或 -05:00）
                        dueDateStr = dueDateStr.split("[+-]")[0];
                    }
                    // 移除毫秒部分（如果有）
                    if (dueDateStr.contains(".")) {
                        dueDateStr = dueDateStr.split("\\.")[0];
                    }
                    // 确保格式为 yyyy-MM-ddTHH:mm:ss
                    dueDate = LocalDateTime.parse(dueDateStr);
                } else {
                    // 如果只是日期（yyyy-MM-dd），设置为当天的23:59:59
                    dueDate = LocalDate.parse(dueDateStr).atTime(23, 59, 59);
                }
                // 验证归还日期必须是未来时间
                if (!dueDate.isAfter(LocalDateTime.now())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "归还日期必须是未来时间"));
                }
            } catch (Exception e) {
                log.error("解析归还日期失败: {}", dueDateStr, e);
                return ResponseEntity.badRequest().body(Map.of("message", "归还日期格式错误: " + e.getMessage()));
            }
        } else {
            // 默认30天
            dueDate = LocalDateTime.now().plusDays(30);
        }
        
        // 创建借阅记录
        Borrow borrow = new Borrow();
        borrow.setUserId(userId);
        borrow.setBookId(bookId);
        borrow.setBorrowDate(LocalDateTime.now());
        borrow.setDueDate(dueDate);
        borrow.setStatus("BORROWED");
        borrow.setWarningCount(0);
        borrow.setCreatedAt(LocalDateTime.now());
        borrow.setUpdatedAt(LocalDateTime.now());
        borrowMapper.insert(borrow);
        
        // 扣减库存
        book.setStock(stock - 1);
        bookMapper.updateById(book);
        
        return ResponseEntity.ok(Map.of("message", "借阅成功"));
    }
    
    // 归还图书（管理员）
    @Operation(summary = "归还图书（管理员）")
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> returnBook(@PathVariable Long id) {
        Borrow borrow = borrowMapper.selectById(id);
        if (borrow == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "借阅记录不存在"));
        }
        if ("RETURNED".equals(borrow.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书已归还"));
        }
        
        borrow.setReturnDate(LocalDateTime.now());
        borrow.setStatus("RETURNED");
        borrow.setUpdatedAt(LocalDateTime.now());
        borrowMapper.updateById(borrow);
        
        // 恢复库存
        Book book = bookMapper.selectById(borrow.getBookId());
        if (book != null) {
            Integer stock = book.getStock();
            if (stock == null) {
                stock = 0;
            }
            book.setStock(stock + 1);
            bookMapper.updateById(book);
        }
        
        // 如果用户被冻结且已归还，可以考虑解冻（可选功能）
        // 这里暂时不实现自动解冻，需要管理员手动处理
        
        return ResponseEntity.ok(Map.of("message", "归还成功"));
    }
    
    // 获取所有借阅记录（管理员）
    @Operation(summary = "获取所有借阅记录（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllBorrows() {
        LocalDateTime now = LocalDateTime.now();
        List<Borrow> borrows = borrowMapper.selectList(
            new LambdaQueryWrapper<Borrow>()
                .orderByDesc(Borrow::getBorrowDate)
        );
        
        // 自动检查并更新逾期状态
        for (Borrow borrow : borrows) {
            if ("BORROWED".equals(borrow.getStatus()) && borrow.getDueDate() != null && borrow.getDueDate().isBefore(now)) {
                borrow.setStatus("OVERDUE");
                borrow.setUpdatedAt(now);
                borrowMapper.updateById(borrow);
            }
        }
        
        // 重新查询以获取最新状态
        borrows = borrowMapper.selectList(
            new LambdaQueryWrapper<Borrow>()
                .orderByDesc(Borrow::getBorrowDate)
        );
        
        List<Map<String, Object>> result = borrows.stream().map(borrow -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", borrow.getId());
            item.put("userId", borrow.getUserId());
            item.put("bookId", borrow.getBookId());
            Book book = bookMapper.selectById(borrow.getBookId());
            if (book != null) {
                item.put("bookTitle", book.getTitle());
                item.put("bookAuthor", book.getAuthor());
            }
            User user = userMapper.selectById(borrow.getUserId());
            if (user != null) {
                item.put("username", user.getUsername());
                item.put("email", user.getEmail());
            }
            item.put("borrowDate", borrow.getBorrowDate());
            item.put("returnDate", borrow.getReturnDate());
            item.put("dueDate", borrow.getDueDate());
            // 如果状态是BORROWED但已逾期，返回OVERDUE
            String status = borrow.getStatus();
            if ("BORROWED".equals(status) && borrow.getDueDate() != null && borrow.getDueDate().isBefore(now)) {
                status = "OVERDUE";
            }
            item.put("status", status);
            item.put("warningCount", borrow.getWarningCount() != null ? borrow.getWarningCount() : 0);
            item.put("lastWarningAt", borrow.getLastWarningAt());
            return item;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }
    
    // 警告逾期用户（管理员）
    @Operation(summary = "对逾期借阅发送警告（管理员）")
    @PostMapping("/{id}/warn")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> warnOverdueUser(@PathVariable Long id) {
        Borrow borrow = borrowMapper.selectById(id);
        if (borrow == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "借阅记录不存在"));
        }
        
        // 检查是否已归还
        if ("RETURNED".equals(borrow.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书已归还，无需警告"));
        }
        
        // 检查是否逾期
        LocalDateTime now = LocalDateTime.now();
        if (borrow.getDueDate() == null || !borrow.getDueDate().isBefore(now)) {
            return ResponseEntity.badRequest().body(Map.of("message", "该借阅尚未逾期，无需警告"));
        }
        
        User user = userMapper.selectById(borrow.getUserId());
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户不存在"));
        }
        
        // 更新警告次数
        int warningCount = (borrow.getWarningCount() != null ? borrow.getWarningCount() : 0) + 1;
        borrow.setWarningCount(warningCount);
        borrow.setLastWarningAt(now);
        borrow.setStatus("OVERDUE");
        borrow.setUpdatedAt(now);
        borrowMapper.updateById(borrow);
        
        Book book = bookMapper.selectById(borrow.getBookId());
        String bookTitle = book != null ? book.getTitle() : "未知图书";
        
        // 发送警告邮件
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String subject = String.format("【警告】您有图书逾期未归还（第%d次警告）", warningCount);
            String content = String.format(
                "尊敬的 %s 用户，\n\n" +
                "您借阅的图书《%s》已逾期未归还。\n\n" +
                "借阅信息：\n" +
                "- 图书名称：%s\n" +
                "- 借阅日期：%s\n" +
                "- 应还日期：%s\n" +
                "- 当前逾期天数：%d 天\n\n" +
                "这是您的第 %d 次警告。\n",
                user.getUsername(),
                bookTitle,
                bookTitle,
                borrow.getBorrowDate(),
                borrow.getDueDate(),
                java.time.temporal.ChronoUnit.DAYS.between(borrow.getDueDate(), now),
                warningCount
            );
            
            if (warningCount >= 3) {
                content += "\n⚠️ 警告：您已收到3次警告，账号将被冻结！请尽快归还图书并联系管理员。\n";
            } else {
                content += String.format("\n请尽快归还图书。累计收到3次警告后，您的账号将被冻结。\n");
            }
            
            content += "\n感谢您的配合！\n\n此邮件由系统自动发送，请勿回复。";
            
            emailService.sendReminder(user.getEmail(), subject, content);
        }
        
        // 如果警告次数达到3次，冻结账号
        if (warningCount >= 3) {
            user.setIsFrozen(true);
            userMapper.updateById(user);
            return ResponseEntity.ok(Map.of(
                "message", String.format("已发送第%d次警告，账号已自动冻结", warningCount),
                "warningCount", warningCount,
                "accountFrozen", true
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "message", String.format("已发送第%d次警告", warningCount),
            "warningCount", warningCount,
            "accountFrozen", false
        ));
    }
}
