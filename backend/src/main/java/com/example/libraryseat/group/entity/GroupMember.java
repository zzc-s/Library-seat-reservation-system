package com.example.libraryseat.group.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("group_member")
public class GroupMember {
    @TableId(type = IdType.INPUT)
    private Long groupId;
    private Long userId;
    private String role; // LEADER, MEMBER
}
