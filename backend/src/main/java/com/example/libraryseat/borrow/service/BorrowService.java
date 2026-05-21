package com.example.libraryseat.borrow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.book.entity.Book;
import com.example.libraryseat.book.mapper.BookMapper;
import com.example.libraryseat.borrow.dto.BorrowRequest;
import com.example.libraryseat.borrow.dto.BorrowVO;
import com.example.libraryseat.borrow.dto.WarnOverdueResult;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import com.example.libraryseat.common.BusinessException;
import com.example.libraryseat.security.EmailService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BorrowService {

    private final BorrowMapper borrowMapper;
    private final BookMapper bookMapper;
    private final UserMapper userMapper;
    private final EmailService emailService;

    public BorrowService(BorrowMapper borrowMapper, BookMapper bookMapper,
                         UserMapper userMapper, EmailService emailService) {
        this.borrowMapper = borrowMapper;
        this.bookMapper = bookMapper;
        this.userMapper = userMapper;
        this.emailService = emailService;
    }

    public List<BorrowVO> getMyBorrows(Long userId) {
        List<Borrow> borrows = borrowMapper.selectList(
                new LambdaQueryWrapper<Borrow>()
                        .eq(Borrow::getUserId, userId)
                        .orderByDesc(Borrow::getBorrowDate));

        autoUpdateOverdue(borrows);
        return toBorrowVOs(borrows, false);
    }

    public List<BorrowVO> getAllBorrows() {
        List<Borrow> borrows = borrowMapper.selectList(
                new LambdaQueryWrapper<Borrow>().orderByDesc(Borrow::getBorrowDate));

        autoUpdateOverdue(borrows);

        borrows = borrowMapper.selectList(
                new LambdaQueryWrapper<Borrow>().orderByDesc(Borrow::getBorrowDate));

        return toBorrowVOs(borrows, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void borrowBook(BorrowRequest req, Long userId) {
        Book book = bookMapper.selectById(req.bookId());
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        if (!book.getIsBorrowable()) {
            throw new BusinessException("该图书不可借阅");
        }
        if (book.getStock() == null || book.getStock() <= 0) {
            throw new BusinessException("该图书库存不足，无法借阅");
        }

        boolean alreadyBorrowed = borrowMapper.selectCount(
                new LambdaQueryWrapper<Borrow>()
                        .eq(Borrow::getBookId, req.bookId())
                        .eq(Borrow::getUserId, userId)
                        .eq(Borrow::getStatus, "BORROWED")) > 0;
        if (alreadyBorrowed) {
            throw new BusinessException("您已借阅该图书，尚未归还");
        }

        LocalDateTime dueDate = req.dueDate() != null ? req.dueDate() : LocalDateTime.now().plusDays(30);
        if (!dueDate.isAfter(LocalDateTime.now())) {
            throw new BusinessException("归还日期必须是未来时间");
        }

        Borrow borrow = new Borrow();
        borrow.setUserId(userId);
        borrow.setBookId(req.bookId());
        borrow.setBorrowDate(LocalDateTime.now());
        borrow.setDueDate(dueDate);
        borrow.setStatus("BORROWED");
        borrow.setWarningCount(0);
        borrow.setCreatedAt(LocalDateTime.now());
        borrow.setUpdatedAt(LocalDateTime.now());
        borrowMapper.insert(borrow);

        book.setStock(book.getStock() - 1);
        bookMapper.updateById(book);
    }

    @Transactional(rollbackFor = Exception.class)
    public void returnBook(Long id) {
        Borrow borrow = borrowMapper.selectById(id);
        if (borrow == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if ("RETURNED".equals(borrow.getStatus())) {
            throw new BusinessException("该图书已归还");
        }

        borrow.setReturnDate(LocalDateTime.now());
        borrow.setStatus("RETURNED");
        borrow.setUpdatedAt(LocalDateTime.now());
        borrowMapper.updateById(borrow);

        Book book = bookMapper.selectById(borrow.getBookId());
        if (book != null) {
            book.setStock((book.getStock() != null ? book.getStock() : 0) + 1);
            bookMapper.updateById(book);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public WarnOverdueResult warnOverdueUser(Long id) {
        Borrow borrow = borrowMapper.selectById(id);
        if (borrow == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if ("RETURNED".equals(borrow.getStatus())) {
            throw new BusinessException("该图书已归还，无需警告");
        }

        LocalDateTime now = LocalDateTime.now();
        if (borrow.getDueDate() == null || !borrow.getDueDate().isBefore(now)) {
            throw new BusinessException("该借阅尚未逾期，无需警告");
        }

        User user = userMapper.selectById(borrow.getUserId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        int warningCount = (borrow.getWarningCount() != null ? borrow.getWarningCount() : 0) + 1;
        borrow.setWarningCount(warningCount);
        borrow.setLastWarningAt(now);
        borrow.setStatus("OVERDUE");
        borrow.setUpdatedAt(now);
        borrowMapper.updateById(borrow);

        sendWarningEmail(user, borrow, warningCount);

        if (warningCount >= 3) {
            user.setIsFrozen(true);
            userMapper.updateById(user);
            return new WarnOverdueResult(
                    String.format("已发送第%d次警告，账号已自动冻结", warningCount),
                    warningCount, true);
        }

        return new WarnOverdueResult(
                String.format("已发送第%d次警告", warningCount),
                warningCount, false);
    }

    private void autoUpdateOverdue(List<Borrow> borrows) {
        LocalDateTime now = LocalDateTime.now();
        for (Borrow borrow : borrows) {
            if ("BORROWED".equals(borrow.getStatus()) && borrow.getDueDate() != null && borrow.getDueDate().isBefore(now)) {
                borrow.setStatus("OVERDUE");
                borrow.setUpdatedAt(now);
                borrowMapper.updateById(borrow);
            }
        }
    }

    private List<BorrowVO> toBorrowVOs(List<Borrow> borrows, boolean includeUserInfo) {
        Set<Long> bookIds = borrows.stream().map(Borrow::getBookId).collect(Collectors.toSet());
        Map<Long, Book> bookMap = bookIds.isEmpty() ? Map.of() :
                bookMapper.selectBatchIds(bookIds).stream()
                        .collect(Collectors.toMap(Book::getId, Function.identity()));

        Map<Long, User> userMap = Map.of();
        if (includeUserInfo) {
            Set<Long> userIds = borrows.stream().map(Borrow::getUserId).collect(Collectors.toSet());
            userMap = userIds.isEmpty() ? Map.of() :
                    userMapper.selectBatchIds(userIds).stream()
                            .collect(Collectors.toMap(User::getId, Function.identity()));
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, User> finalUserMap = userMap;
        return borrows.stream().map(borrow -> {
            Book book = bookMap.get(borrow.getBookId());
            User user = finalUserMap.get(borrow.getUserId());
            String status = borrow.getStatus();
            if ("BORROWED".equals(status) && borrow.getDueDate() != null && borrow.getDueDate().isBefore(now)) {
                status = "OVERDUE";
            }
            return new BorrowVO(
                    borrow.getId(),
                    borrow.getUserId(),
                    borrow.getBookId(),
                    book != null ? book.getTitle() : null,
                    book != null ? book.getAuthor() : null,
                    user != null ? user.getUsername() : null,
                    user != null ? user.getEmail() : null,
                    borrow.getBorrowDate(),
                    borrow.getReturnDate(),
                    borrow.getDueDate(),
                    status,
                    borrow.getWarningCount() != null ? borrow.getWarningCount() : 0,
                    borrow.getLastWarningAt()
            );
        }).collect(Collectors.toList());
    }

    private void sendWarningEmail(User user, Borrow borrow, int warningCount) {
        if (user.getEmail() == null || user.getEmail().isBlank()) return;

        Book book = bookMapper.selectById(borrow.getBookId());
        String bookTitle = book != null ? book.getTitle() : "未知图书";
        long overdueDays = ChronoUnit.DAYS.between(borrow.getDueDate(), LocalDateTime.now());

        String subject = String.format("【警告】您有图书逾期未归还（第%d次警告）", warningCount);
        StringBuilder content = new StringBuilder();
        content.append(String.format(
                "尊敬的 %s 用户，\n\n您借阅的图书《%s》已逾期未归还。\n\n" +
                "借阅信息：\n- 图书名称：%s\n- 借阅日期：%s\n- 应还日期：%s\n- 当前逾期天数：%d 天\n\n" +
                "这是您的第 %d 次警告。\n",
                user.getUsername(), bookTitle, bookTitle,
                borrow.getBorrowDate(), borrow.getDueDate(), overdueDays, warningCount));

        if (warningCount >= 3) {
            content.append("\n⚠️ 警告：您已收到3次警告，账号将被冻结！请尽快归还图书并联系管理员。\n");
        } else {
            content.append("\n请尽快归还图书。累计收到3次警告后，您的账号将被冻结。\n");
        }
        content.append("\n感谢您的配合！\n\n此邮件由系统自动发送，请勿回复。");

        emailService.sendReminder(user.getEmail(), subject, content.toString());
    }
}
