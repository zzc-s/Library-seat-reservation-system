package com.example.libraryseat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
@EnableAsync//开启异步支持对应（ sendCodeAsync）
public class LibrarySeatApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibrarySeatApplication.class, args);
    }

    /**
     * 启动时确保预置唯一管理员账号存在。
     */
    @Bean
    public CommandLineRunner initDefaultAdmin(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        return args -> {
            User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
            if (admin == null) {
                User u = new User();
                u.setUsername("admin");
                u.setPasswordHash(passwordEncoder.encode("admin123"));
                u.setRole("ADMIN");
                u.setIsFrozen(false);
                userMapper.insert(u);
            } else if (!"ADMIN".equals(admin.getRole())) {
                // 确保预置 admin 账号始终为管理员（避免曾被注册为 USER 的情况）
                admin.setRole("ADMIN");
                userMapper.updateById(admin);
            }
        };
    }
}


