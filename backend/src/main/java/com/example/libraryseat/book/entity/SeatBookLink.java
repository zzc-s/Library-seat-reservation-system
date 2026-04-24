package com.example.libraryseat.book.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seat_book_link")
public class SeatBookLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("reservation_id")
    private Long reservationId;
    
    @TableField("book_id")
    private Long bookId;
    
    @TableField("place_status")
    private String placeStatus; // TO_PLACE, PLACED, CONFIRMED, RETURNED
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
