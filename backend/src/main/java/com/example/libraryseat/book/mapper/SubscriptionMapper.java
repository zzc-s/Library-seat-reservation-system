package com.example.libraryseat.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.libraryseat.book.entity.Subscription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubscriptionMapper extends BaseMapper<Subscription> {
}
