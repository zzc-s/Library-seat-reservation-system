package com.example.libraryseat.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Component
public class SecurityUtil {

    private final UserMapper userMapper;

    public SecurityUtil(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Long currentUserId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            Object uid = attrs.getRequest().getAttribute("uid");
            if (uid instanceof Number) {
                return ((Number) uid).longValue();
            }
        } catch (Exception e) {
            log.debug("无法从 request attribute 获取 uid: {}", e.getMessage());
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String username = auth.getName();
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null) {
                return user.getId();
            }
        }
        throw new IllegalStateException("无法获取用户ID");
    }

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}