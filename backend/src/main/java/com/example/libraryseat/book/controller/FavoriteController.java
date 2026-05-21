package com.example.libraryseat.book.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.book.entity.Book;
import com.example.libraryseat.book.entity.Favorite;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.book.mapper.FavoriteMapper;
import com.example.libraryseat.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "图书收藏接口", description = "收藏列表、添加收藏与取消收藏")
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final FavoriteMapper favoriteMapper;
    private final BookMapper bookMapper;
    private final SecurityUtil securityUtil;

    public FavoriteController(FavoriteMapper favoriteMapper, BookMapper bookMapper, SecurityUtil securityUtil) {
        this.favoriteMapper = favoriteMapper;
        this.bookMapper = bookMapper;
        this.securityUtil = securityUtil;
    }

    /**
     * 获取当前用户的收藏列表
     */
    @Operation(summary = "获取当前用户的收藏列表")
    @GetMapping
    public List<Map<String, Object>> getMyFavorites() {
        Long userId = securityUtil.currentUserId();
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt));

        return favorites.stream().map(favorite -> {
            Book book = bookMapper.selectById(favorite.getBookId());
            Map<String, Object> result = new HashMap<>();
            result.put("id", favorite.getId());
            result.put("bookId", favorite.getBookId());
            result.put("createdAt", favorite.getCreatedAt());
            if (book != null) {
                result.put("book", book);
            }
            return result;
        }).collect(Collectors.toList());
    }

    /**
     * 获取当前用户收藏的图书ID列表（用于前端判断是否已收藏）
     */
    @Operation(summary = "获取当前用户收藏的图书ID列表")
    @GetMapping("/ids")
    public List<Long> getFavoriteBookIds() {
        Long userId = securityUtil.currentUserId();
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .select(Favorite::getBookId));
        return favorites.stream().map(Favorite::getBookId).collect(Collectors.toList());
    }

    /**
     * 添加收藏
     */
    @Operation(summary = "添加收藏图书")
    @PostMapping
    public ResponseEntity<?> addFavorite(@RequestBody Map<String, Object> req) {
        Long userId = securityUtil.currentUserId();
        Long bookId = Long.valueOf(req.get("bookId").toString());

        // 检查图书是否存在
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "图书不存在"));
        }

        // 检查是否已经收藏
        Favorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getBookId, bookId));
        if (existing != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "已经收藏过该图书"));
        }

        // 创建收藏记录
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setBookId(bookId);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);

        log.info("用户 {} 收藏了图书 {}", userId, bookId);
        return ResponseEntity.ok(Map.of("message", "收藏成功", "favorite", favorite));
    }

    /**
     * 取消收藏
     */
    @Operation(summary = "取消收藏图书")
    @DeleteMapping("/{bookId}")
    public ResponseEntity<?> removeFavorite(@PathVariable Long bookId) {
        Long userId = securityUtil.currentUserId();

        // 查找收藏记录
        Favorite favorite = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getBookId, bookId));

        if (favorite == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "未收藏该图书"));
        }

        // 删除收藏记录
        favoriteMapper.deleteById(favorite.getId());

        log.info("用户 {} 取消收藏了图书 {}", userId, bookId);
        return ResponseEntity.ok(Map.of("message", "取消收藏成功"));
    }

    /**
     * 检查是否已收藏
     */
    @Operation(summary = "检查指定图书是否已收藏")
    @GetMapping("/check/{bookId}")
    public ResponseEntity<?> checkFavorite(@PathVariable Long bookId) {
        Long userId = securityUtil.currentUserId();
        Favorite favorite = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getBookId, bookId));
        return ResponseEntity.ok(Map.of("isFavorite", favorite != null));
    }

}
