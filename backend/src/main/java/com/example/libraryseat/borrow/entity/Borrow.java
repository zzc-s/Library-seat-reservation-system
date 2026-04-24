package com.example.libraryseat.borrow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("borrow")
public class Borrow {
    @TableId(type = IdType.AUTO)
    private Long id;
    @com.baomidou.mybatisplus.annotation.TableField("user_id")
    private Long userId;
    @com.baomidou.mybatisplus.annotation.TableField("book_id")
    private Long bookId;
    @com.baomidou.mybatisplus.annotation.TableField("borrow_date")
    private LocalDateTime borrowDate;
    @com.baomidou.mybatisplus.annotation.TableField("return_date")
    private LocalDateTime returnDate;
    @com.baomidou.mybatisplus.annotation.TableField("due_date")
    private LocalDateTime dueDate;
    private String status; // BORROWED / RETURNED / OVERDUE (借阅中/已归还/逾期)
    @com.baomidou.mybatisplus.annotation.TableField("warning_count")
    private Integer warningCount; // 警告次数
    @com.baomidou.mybatisplus.annotation.TableField("last_warning_at")
    private LocalDateTime lastWarningAt; // 最后警告时间
    @com.baomidou.mybatisplus.annotation.TableField("created_at")
    private LocalDateTime createdAt;
    @com.baomidou.mybatisplus.annotation.TableField("updated_at")
    private LocalDateTime updatedAt;
}
