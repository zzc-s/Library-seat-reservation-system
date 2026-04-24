package com.example.libraryseat.borrow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.libraryseat.borrow.entity.Borrow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BorrowMapper extends BaseMapper<Borrow> {
}
