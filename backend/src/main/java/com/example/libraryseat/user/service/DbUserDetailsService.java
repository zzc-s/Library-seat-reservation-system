package com.example.libraryseat.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public DbUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        // 检查用户是否被冻结，如果被冻结则禁用账户
        boolean enabled = !Boolean.TRUE.equals(user.getIsFrozen());
        // 角色规范化：去空格并转大写，确保与 hasRole("ADMIN") 一致
        String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole().trim().toUpperCase();
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            role = "USER";
        }
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                enabled, // account enabled
                true,   // account not expired
                true,   // credentials not expired
                true,   // account not locked
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}


