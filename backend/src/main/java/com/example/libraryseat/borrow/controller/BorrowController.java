package com.example.libraryseat.borrow.controller;

import com.example.libraryseat.borrow.dto.BorrowRequest;
import com.example.libraryseat.borrow.dto.BorrowVO;
import com.example.libraryseat.borrow.service.BorrowService;
import com.example.libraryseat.common.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "图书借阅与逾期管理接口", description = "用户借阅、管理员归还与逾期警告")
@RestController
@RequestMapping("/api/borrows")
public class BorrowController {

    private final BorrowService borrowService;
    private final SecurityUtil securityUtil;

    public BorrowController(BorrowService borrowService, SecurityUtil securityUtil) {
        this.borrowService = borrowService;
        this.securityUtil = securityUtil;
    }

    @Operation(summary = "获取当前用户的借阅记录")
    @GetMapping("/my")
    public ResponseEntity<List<BorrowVO>> getMyBorrows() {
        return ResponseEntity.ok(borrowService.getMyBorrows(securityUtil.currentUserId()));
    }

    @Operation(summary = "借阅图书")
    @PostMapping
    public ResponseEntity<?> borrowBook(@RequestBody BorrowRequest req) {
        return borrowService.borrowBook(req, securityUtil.currentUserId());
    }

    @Operation(summary = "归还图书（管理员）")
    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> returnBook(@PathVariable Long id) {
        return borrowService.returnBook(id);
    }

    @Operation(summary = "获取所有借阅记录（管理员）")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BorrowVO>> getAllBorrows() {
        return ResponseEntity.ok(borrowService.getAllBorrows());
    }

    @Operation(summary = "对逾期借阅发送警告（管理员）")
    @PostMapping("/{id}/warn")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> warnOverdueUser(@PathVariable Long id) {
        return borrowService.warnOverdueUser(id);
    }
}
