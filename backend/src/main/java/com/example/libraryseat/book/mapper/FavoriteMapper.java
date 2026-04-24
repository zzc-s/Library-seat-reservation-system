package com.example.libraryseat.book.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.libraryseat.book.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
}
