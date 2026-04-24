package com.example.libraryseat.group.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("study_group")
public class StudyGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long leaderId;
    @com.baomidou.mybatisplus.annotation.TableField("is_published")
    private Boolean isPublished;
    @com.baomidou.mybatisplus.annotation.TableField("reservation_start_time")
    private LocalDateTime reservationStartTime;
    @com.baomidou.mybatisplus.annotation.TableField("created_at")
    private LocalDateTime createdAt;
}
