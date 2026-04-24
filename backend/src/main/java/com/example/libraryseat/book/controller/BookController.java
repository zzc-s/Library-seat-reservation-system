package com.example.libraryseat.book.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.book.entity.Book;
import com.example.libraryseat.book.entity.SeatBookLink;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.book.mapper.SeatBookLinkMapper;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import com.example.libraryseat.notification.service.SubscriptionNotificationService;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "图书与座位联动接口", description = "图书查询、管理以及与预约的关联")
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookMapper bookMapper;
    private final SeatBookLinkMapper linkMapper;
    private final SubscriptionNotificationService subscriptionNotificationService;
    private final BorrowMapper borrowMapper;
    private final ReservationMapper reservationMapper;

    public BookController(BookMapper bookMapper, SeatBookLinkMapper linkMapper,
                         SubscriptionNotificationService subscriptionNotificationService,
                         BorrowMapper borrowMapper, ReservationMapper reservationMapper) {
        this.bookMapper = bookMapper;
        this.linkMapper = linkMapper;
        this.subscriptionNotificationService = subscriptionNotificationService;
        this.borrowMapper = borrowMapper;
        this.reservationMapper = reservationMapper;
    }

    @Operation(summary = "查询图书列表")
    @GetMapping
    public List<Book> list(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String category) {
        LambdaQueryWrapper<Book> wrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String q = keyword.trim();
            wrapper.and(w -> w.like(Book::getTitle, q)
                    .or()
                    .like(Book::getAuthor, q)
                    .or()
                    .like(Book::getIsbn, q));
        }
        
        if (category != null && !category.trim().isEmpty() && !"全部".equals(category)) {
            wrapper.eq(Book::getCategory, category);
        }

        List<Book> list = bookMapper.selectList(wrapper);
        // 有关键字时：书名命中优先于作者，作者优先于 ISBN（同为模糊子串匹配）
        if (keyword != null && !keyword.trim().isEmpty()) {
            String qNorm = keyword.trim().toLowerCase(Locale.ROOT);
            list.sort(Comparator
                    .comparing((Book b) -> !fieldContainsIgnoreCase(b.getTitle(), qNorm))
                    .thenComparing(b -> !fieldContainsIgnoreCase(b.getAuthor(), qNorm))
                    .thenComparing(b -> !fieldContainsIgnoreCase(b.getIsbn(), qNorm))
                    .thenComparing(Book::getId, Comparator.nullsLast(Long::compareTo)));
        }
        return list;
    }

    private static boolean fieldContainsIgnoreCase(String field, String queryLower) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    @Operation(summary = "根据ID获取图书详情")
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(book);
    }

    @Operation(summary = "根据书名精确查询图书")
    @GetMapping("/by-title")
    public ResponseEntity<?> getByTitle(@RequestParam String title) {
        Book book = bookMapper.selectOne(new LambdaQueryWrapper<Book>()
                .eq(Book::getTitle, title)
                .last("LIMIT 1"));
        if (book == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(book);
    }

    @Operation(summary = "创建图书（管理员）")
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@RequestBody Book book) {
        // 新建图书时，默认未上架
        if (book.getIsBorrowable() == null) {
            book.setIsBorrowable(false);
        }
        bookMapper.insert(book);
        log.info("新建图书：{}（{}）", book.getTitle(), book.getId());
        return ResponseEntity.ok(book);
    }

    /**
     * 更新图书信息（需要管理员权限）
     */
    @Operation(summary = "更新图书信息（管理员）")
    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Book updatedBook) {
        Book existingBook = bookMapper.selectById(id);
        if (existingBook == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 保存旧的上架状态
        Boolean oldIsBorrowable = existingBook.getIsBorrowable();
        
        // 更新图书信息
        if (updatedBook.getTitle() != null) existingBook.setTitle(updatedBook.getTitle());
        if (updatedBook.getAuthor() != null) existingBook.setAuthor(updatedBook.getAuthor());
        if (updatedBook.getIsbn() != null) existingBook.setIsbn(updatedBook.getIsbn());
        if (updatedBook.getIsBorrowable() != null) existingBook.setIsBorrowable(updatedBook.getIsBorrowable());
        if (updatedBook.getHotScore() != null) existingBook.setHotScore(updatedBook.getHotScore());
        if (updatedBook.getStock() != null) existingBook.setStock(updatedBook.getStock());
        if (updatedBook.getPublisher() != null) existingBook.setPublisher(updatedBook.getPublisher());
        if (updatedBook.getCategory() != null) existingBook.setCategory(updatedBook.getCategory());
        if (updatedBook.getCoverUrl() != null) existingBook.setCoverUrl(updatedBook.getCoverUrl());
        if (updatedBook.getDescription() != null) existingBook.setDescription(updatedBook.getDescription());
        
        bookMapper.updateById(existingBook);
        
        // 检查是否需要发送订阅通知（当图书从未上架变为上架时）
        try {
            subscriptionNotificationService.checkAndNotifyBookAvailable(
                    id, oldIsBorrowable, existingBook.getIsBorrowable());
        } catch (Exception e) {
            log.error("发送订阅通知失败，图书ID: {}", id, e);
            // 通知失败不影响图书更新操作
        }
        
        log.info("图书 {} 已更新，上架状态：{} -> {}", id, oldIsBorrowable, existingBook.getIsBorrowable());
        return ResponseEntity.ok(existingBook);
    }

    /**
     * 删除图书（需要管理员权限）
     */
    @Operation(summary = "删除图书（管理员）")
    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            return ResponseEntity.notFound().build();
        }
        
        bookMapper.deleteById(id);
        log.info("图书 {}（{}）已删除", id, book.getTitle());
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @Operation(summary = "将图书关联到预约并自动创建借阅记录")
    @PostMapping("/link")
    @Transactional
    public ResponseEntity<?> linkBookToReservation(@RequestBody Map<String, Object> req) {
        Long reservationId = Long.valueOf(req.get("reservationId").toString());
        Long bookId = Long.valueOf(req.get("bookId").toString());
        
        // 获取预约信息
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "预约不存在"));
        }
        
        // 获取当前用户ID
        Long userId = reservation.getUserId(); // 使用预约的用户ID，而不是当前登录用户（支持协同预约）
        
        // 检查图书是否存在
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "图书不存在"));
        }
        
        // 检查图书是否可借阅
        if (!Boolean.TRUE.equals(book.getIsBorrowable())) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书不可借阅"));
        }
        
        // 检查库存
        Integer stock = book.getStock();
        if (stock == null || stock <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "该图书库存不足，无法借阅"));
        }
        
        // 检查用户是否已有未归还的该图书借阅记录
        List<Borrow> existing = borrowMapper.selectList(
            new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getBookId, bookId)
                .eq(Borrow::getUserId, userId)
                .eq(Borrow::getStatus, "BORROWED")
        );
        if (!existing.isEmpty()) {
            // 如果已经借阅，只创建关联记录，不重复创建借阅记录
            log.info("用户 {} 已借阅图书 {}，只创建关联记录", userId, bookId);
        } else {
            // 创建借阅记录
            // 归还日期设置为预约结束时间，如果预约结束时间已过，则设置为30天后
            LocalDateTime dueDate = reservation.getEndTime();
            if (dueDate == null || dueDate.isBefore(LocalDateTime.now())) {
                dueDate = LocalDateTime.now().plusDays(30);
            }
            
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
            
            log.info("用户 {} 关联图书 {} 到预约 {}，已自动创建借阅记录，归还日期：{}", 
                    userId, bookId, reservationId, dueDate);
        }
        
        // 创建关联记录
        SeatBookLink link = new SeatBookLink();
        link.setReservationId(reservationId);
        link.setBookId(bookId);
        link.setPlaceStatus("TO_PLACE");
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        linkMapper.insert(link);
        
        return ResponseEntity.ok(link);
    }

    @Operation(summary = "根据预约ID查询已关联的图书列表")
    @GetMapping("/reservation/{reservationId}")
    public List<Map<String, Object>> getBooksByReservation(@PathVariable Long reservationId) {
        List<SeatBookLink> links = linkMapper.selectList(new LambdaQueryWrapper<SeatBookLink>()
                .eq(SeatBookLink::getReservationId, reservationId));
        
        return links.stream().map(link -> {
            Book book = bookMapper.selectById(link.getBookId());
            Map<String, Object> result = new HashMap<>();
            result.put("linkId", link.getId());
            result.put("bookId", link.getBookId());
            result.put("reservationId", link.getReservationId());
            result.put("placeStatus", link.getPlaceStatus());
            if (book != null) {
                result.put("bookTitle", book.getTitle());
                result.put("bookAuthor", book.getAuthor());
                result.put("bookIsbn", book.getIsbn());
            }
            result.put("createdAt", link.getCreatedAt());
            result.put("updatedAt", link.getUpdatedAt());
            return result;
        }).collect(Collectors.toList());
    }

    /**
     * 更新图书放置状态
     * @param linkId 图书关联记录ID
     * @param status 新状态：PLACED（已放置）、CONFIRMED（已确认）、RETURNED（已归还）
     */
    @Operation(summary = "更新座位-图书关联的放置状态")
    @PutMapping("/link/{linkId}/status")
    public ResponseEntity<?> updatePlaceStatus(@PathVariable Long linkId, @RequestBody Map<String, String> req) {
        String status = req.get("status");
        
        if (status == null || !List.of("PLACED", "CONFIRMED", "RETURNED").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("message", "无效的状态值，必须是 PLACED、CONFIRMED 或 RETURNED"));
        }
        
        SeatBookLink link = linkMapper.selectById(linkId);
        if (link == null) {
            return ResponseEntity.notFound().build();
        }
        
        link.setPlaceStatus(status);
        link.setUpdatedAt(LocalDateTime.now());
        linkMapper.updateById(link);
        
        log.info("图书关联记录 {} 的状态已更新为 {}", linkId, status);
        return ResponseEntity.ok(Map.of("message", "状态更新成功", "link", link));
    }

    /**
     * 删除图书关联
     * @param linkId 图书关联记录ID
     */
    @Operation(summary = "删除座位-图书关联记录")
    @DeleteMapping("/link/{linkId}")
    public ResponseEntity<?> deleteLink(@PathVariable Long linkId) {
        SeatBookLink link = linkMapper.selectById(linkId);
        if (link == null) {
            return ResponseEntity.notFound().build();
        }
        
        linkMapper.deleteById(linkId);
        log.info("图书关联记录 {} 已删除", linkId);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    /**
     * 上传图书封面
     * @param file 封面图片文件
     * @return 返回封面URL
     */
    @Operation(summary = "上传图书封面（管理员）")
    @PostMapping("/upload-cover")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadCover(@RequestParam("file") MultipartFile file) {
        try {
            log.info("收到封面上传请求，原始文件名：{}，文件大小：{} bytes，Content-Type：{}", 
                    file.getOriginalFilename(), file.getSize(), file.getContentType());
            
            if (file.isEmpty()) {
                log.warn("上传的文件为空");
                return ResponseEntity.badRequest().body(Map.of("message", "文件不能为空"));
            }
            
            // 验证文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                log.warn("文件名为null");
                return ResponseEntity.badRequest().body(Map.of("message", "文件名无效"));
            }
            
            log.info("原始文件名：{}", originalFilename);
            
            String extension = originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase() 
                    : ".jpg";
            
            log.info("提取的文件扩展名：{}", extension);
            
            // 只允许图片格式
            if (!List.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(extension)) {
                log.warn("不支持的文件格式：{}", extension);
                return ResponseEntity.badRequest().body(Map.of("message", "只支持图片格式：jpg, jpeg, png, gif, webp，当前文件扩展名：" + extension));
            }
            
            // 验证文件大小（最大5MB）
            long maxSize = 5 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                log.warn("文件大小超过限制：{} bytes，最大允许：{} bytes", file.getSize(), maxSize);
                return ResponseEntity.badRequest().body(Map.of("message", "文件大小不能超过5MB，当前文件大小：" + String.format("%.2f", file.getSize() / 1024.0 / 1024.0) + "MB"));
            }
            
            // 生成唯一文件名（使用UUID，避免中文文件名问题）
            String filename = UUID.randomUUID().toString() + extension;
            log.info("生成的文件名：{}", filename);
            
            // 创建上传目录（使用绝对路径，与WebConfig保持一致）
            String projectRoot = System.getProperty("user.dir");
            Path uploadDir;
            
            // 如果从backend目录启动，uploads应该在项目根目录（父目录）
            if (projectRoot.endsWith("backend")) {
                uploadDir = Paths.get(projectRoot).getParent().resolve("uploads").resolve("book-covers").toAbsolutePath();
            } else {
                uploadDir = Paths.get(projectRoot, "uploads", "book-covers").toAbsolutePath();
            }
            
            log.info("上传目录路径：{}", uploadDir);
            
            // 确保目录存在
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
                log.info("创建上传目录：{}", uploadDir);
            }
            
            // 保存文件
            Path filePath = uploadDir.resolve(filename);
            log.info("文件保存路径：{}", filePath);
            
            Files.write(filePath, file.getBytes());
            log.info("文件保存成功，文件大小：{} bytes", Files.size(filePath));
            
            String coverUrl = "/uploads/book-covers/" + filename;
            log.info("图书封面上传成功：{}", coverUrl);
            
            return ResponseEntity.ok(Map.of("coverUrl", coverUrl));
        } catch (IOException e) {
            log.error("图书封面上传失败，错误信息：{}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("message", "上传失败：" + e.getMessage()));
        } catch (Exception e) {
            log.error("图书封面上传出现未知错误：{}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("message", "上传失败：" + e.getMessage()));
        }
    }
}
