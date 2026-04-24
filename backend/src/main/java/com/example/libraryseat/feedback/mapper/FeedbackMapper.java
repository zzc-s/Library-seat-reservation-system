package com.example.libraryseat.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.libraryseat.feedback.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
