package com.example.libraryseat.user.controller;

import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户基础接口（调试用）", description = "简单的用户列表与创建，仅 mysql 环境启用")
@RestController
@RequestMapping("/api/users")
@Profile("mysql")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Operation(summary = "（调试）查询所有用户列表")
    @GetMapping
    public List<User> list() {
        return userMapper.selectList(null);
    }

    @Operation(summary = "（调试）直接创建用户记录")
    @PostMapping
    public User create(@RequestBody @Validated User user) {
        userMapper.insert(user);
        return user;
    }
}


