package com.example.libraryseat.seat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

@Data
@TableName("seat")
public class Seat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String building; // 楼栋名称，如：A楼、B楼
    private Integer floor; // 楼层号
    private String label; // 座位标签，如：A101
    @TableField("row_num")
    private Integer rowNum; // 行号，用于CSS Grid布局
    @TableField("col_num")
    private Integer colNum; // 列号，用于CSS Grid布局
    private String area; // 区域名称，如：一楼自习区、一楼安静区等
    private Boolean hasPower; // 是否有电源
    private Boolean isWindow; // 是否靠窗
    private String zone; // 区域类型：安静区、自习区等
    private String status; // FREE/IDLE/RESERVED/OCCUPIED/BROKEN/FAULT
    private String note; // 备注信息
    
    // 明确排除不存在的字段，防止 MyBatis-Plus 查询时出错
    @TableField(exist = false)
    private Integer x; // 已废弃，不再使用
    
    @TableField(exist = false)
    private Integer y; // 已废弃，不再使用
}


