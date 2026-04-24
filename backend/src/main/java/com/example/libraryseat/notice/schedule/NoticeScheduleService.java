package com.example.libraryseat.notice.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.notice.entity.Notice;
import com.example.libraryseat.notice.mapper.NoticeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告到期后自动取消发布（is_published = 0）
 */
@Slf4j
@Service
public class NoticeScheduleService {

    private final NoticeMapper noticeMapper;

    public NoticeScheduleService(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void unpublishExpiredNotices() {
        LocalDateTime now = LocalDateTime.now();
        List<Notice> expired = noticeMapper.selectList(
                new LambdaQueryWrapper<Notice>()
                        .eq(Notice::getIsPublished, true)
                        .isNotNull(Notice::getExpiresAt)
                        .le(Notice::getExpiresAt, now)
        );
        if (expired.isEmpty()) {
            return;
        }
        for (Notice n : expired) {
            n.setIsPublished(false);
            n.setUpdatedAt(now);
            noticeMapper.updateById(n);
            log.info("公告 [{}] id={} 已到期，自动取消发布", n.getTitle(), n.getId());
        }
        log.info("公告到期处理完成，共 {} 条", expired.size());
    }
}
