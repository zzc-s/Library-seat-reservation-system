package com.example.libraryseat.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("book")
public class Book {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String isbn;
    private String title;
    private String author;
    private Boolean isBorrowable;
    private Integer hotScore;
    @com.baomidou.mybatisplus.annotation.TableField("stock")
    private Integer stock; // 库存数量
    @com.baomidou.mybatisplus.annotation.TableField("publisher")
    private String publisher; // 出版社
    @com.baomidou.mybatisplus.annotation.TableField("category")
    private String category; // 分类
    @com.baomidou.mybatisplus.annotation.TableField("cover_url")
    private String coverUrl; // 封面图片URL
    @com.baomidou.mybatisplus.annotation.TableField("description")
    private String description; // 图书内容简介
}
