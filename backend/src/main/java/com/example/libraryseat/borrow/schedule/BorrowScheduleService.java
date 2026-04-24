package com.example.libraryseat.borrow.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图书借阅定时任务服务
 * 用于检测和处理逾期借阅
 */
@Slf4j
@Service
public class BorrowScheduleService {
    
    private final BorrowMapper borrowMapper;
    
    public BorrowScheduleService(BorrowMapper borrowMapper) {
        this.borrowMapper = borrowMapper;
    }
    
    /**
     * 检测并更新逾期借阅状态
     * 每30分钟执行一次
     */
    @Scheduled(fixedRate = 1800000) // 30分钟 = 1800000毫秒
    public void checkOverdueBorrows() {
        log.info("开始检测逾期借阅...");
        
        LocalDateTime now = LocalDateTime.now();
        
        // 查找所有已借阅但未归还，且应还日期已过的记录
        List<Borrow> overdueBorrows = borrowMapper.selectList(
            new LambdaQueryWrapper<Borrow>()
                .eq(Borrow::getStatus, "BORROWED")
                .lt(Borrow::getDueDate, now)
        );
        
        if (overdueBorrows.isEmpty()) {
            log.info("未发现逾期借阅");
            return;
        }
        
        log.info("发现 {} 条逾期借阅记录", overdueBorrows.size());
        
        // 更新状态为逾期
        for (Borrow borrow : overdueBorrows) {
            borrow.setStatus("OVERDUE");
            borrow.setUpdatedAt(LocalDateTime.now());
            borrowMapper.updateById(borrow);
            log.info("借阅记录 {} (图书ID: {}) 已标记为逾期", borrow.getId(), borrow.getBookId());
        }
        
        log.info("逾期借阅检测完成，共处理 {} 条记录", overdueBorrows.size());
    }
}
