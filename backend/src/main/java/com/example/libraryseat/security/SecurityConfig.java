package com.example.libraryseat.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.accessDeniedHandler(new JsonAccessDeniedHandler()))
                .authorizeHttpRequests(reg -> reg
                        // 接口文档相关资源允许匿名访问（Knife4j + springdoc）
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/doc.html", "/webjars/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/register-code", "/api/auth/forgot", "/api/auth/reset").permitAll()
                        .requestMatchers("/api/auth/upload-avatar", "/api/auth/remove-avatar").authenticated()  // 上传/移除头像需要登录
                        .requestMatchers("/api/dashboard/**").hasRole("ADMIN")  // 看板仅管理员可访问
                        .requestMatchers("/uploads/**").permitAll()  // 静态资源允许访问
                        .requestMatchers("/ws/**").permitAll()  // WebSocket 连接允许匿名访问
                        .requestMatchers(HttpMethod.GET, "/api/seat/query").permitAll()  // 可视化选座查询允许匿名访问
                        .requestMatchers("/api/seat/reserve").authenticated()  // 座位预约需要登录
                        .requestMatchers(HttpMethod.GET, "/api/seats").permitAll()  // 座位列表查询允许匿名访问（用于加载选项）
                        .requestMatchers("/api/seats/**").authenticated()  // 其他座位查询和推荐需要登录
                        .requestMatchers("/api/reservations/**").authenticated()
                        .requestMatchers("/api/groups/**").authenticated()  // 自习小组协同预约需要登录
                        .requestMatchers(HttpMethod.GET, "/api/books").permitAll()  // 图书列表查询允许匿名访问
                        .requestMatchers(HttpMethod.GET, "/api/books/{id}").permitAll()  // 图书详情查询允许匿名访问
                        .requestMatchers(HttpMethod.GET, "/api/books/by-title").permitAll()  // 按书名查询允许匿名访问
                        .requestMatchers(HttpMethod.POST, "/api/books/upload-cover").hasRole("ADMIN")  // 图书封面上传需要管理员权限（必须在通用规则之前）
                        .requestMatchers(HttpMethod.PUT, "/api/books/**").hasRole("ADMIN")  // 更新图书需要管理员权限
                        .requestMatchers(HttpMethod.POST, "/api/books", "/api/books/").hasRole("ADMIN")  // 创建图书需要管理员权限（含尾部斜杠）
                        .requestMatchers(HttpMethod.DELETE, "/api/books/**").hasRole("ADMIN")  // 删除图书需要管理员权限
                        .requestMatchers("/api/books/**").authenticated()  // 其他图书操作需要登录
                        .requestMatchers("/api/favorites/**").authenticated()  // 图书收藏需要登录（放在 books 之后，避免被 books/** 匹配）
                        .requestMatchers("/api/subscriptions/**").authenticated()  // 图书订阅需要登录
                        .requestMatchers("/api/notifications/**").authenticated()  // 用户通知需要登录
                        .requestMatchers(HttpMethod.GET, "/api/notices/public").permitAll()  // 公告通知公开接口允许匿名访问
                        .requestMatchers("/api/notices/**").hasRole("ADMIN")  // 公告管理需要管理员权限
                        .requestMatchers("/api/borrows/**").authenticated()  // 图书借阅需要登录
                        .requestMatchers(HttpMethod.GET, "/api/feedbacks/public").permitAll()  // 公开反馈允许匿名访问
                        .requestMatchers(HttpMethod.DELETE, "/api/feedbacks/{id}").authenticated()  // 用户删除自己的反馈需要登录
                        .requestMatchers("/api/feedbacks/**").authenticated()  // 其他反馈功能需要登录
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/attendance/**").authenticated()
                        .requestMatchers("/api/violations/**").authenticated()  // 违规记录需要登录
                        .requestMatchers(HttpMethod.GET, "/api/users").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}


