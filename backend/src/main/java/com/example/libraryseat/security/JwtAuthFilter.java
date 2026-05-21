package com.example.libraryseat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Key;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProperties properties;
    private final RedisTokenService redisTokenService;
    private final UserDetailsService userDetailsService;
    private final Key signingKey;

    public JwtAuthFilter(JwtProperties properties, RedisTokenService redisTokenService, UserDetailsService userDetailsService) {
        this.properties = properties;
        this.redisTokenService = redisTokenService;
        this.userDetailsService = userDetailsService;
        this.signingKey = JwtSigningKeyFactory.fromSecret(properties.getSecret());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 跳过不需要认证的路径，并清除可能存在的认证信息
        String path = request.getRequestURI();
        
        // 允许匿名访问的路径（静态资源和公开API）
        boolean isPublicPath = path.equals("/api/auth/login") || 
            path.equals("/api/auth/register") || 
            path.equals("/api/auth/forgot") || 
            path.equals("/api/auth/reset") ||
            path.startsWith("/api/auth/login") || 
            path.startsWith("/api/auth/register") || 
            path.startsWith("/api/auth/forgot") || 
            path.startsWith("/api/auth/reset") ||
            path.startsWith("/uploads/") ||  // 静态资源（包括图片）允许匿名访问
            path.equals("/api/notices/public") ||  // 公开公告允许匿名访问
            path.equals("/api/feedbacks/public") ||  // 公开反馈允许匿名访问
            path.equals("/api/seat/query") ||  // 可视化选座查询允许匿名访问
            (path.equals("/api/seats") && request.getMethod().equals("GET")) ||  // 座位列表查询允许匿名访问（用于加载选项）
            (path.startsWith("/api/books") && request.getMethod().equals("GET"));  // 图书列表查询允许匿名访问
        
        if (isPublicPath) {
            // 清除可能存在的认证信息，确保这些路径完全匿名访问
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }
        //从HTTP请求头中提取 Authorization 字段
        String authHeader = request.getHeader("Authorization");
        log.info("请求路径: {}, Authorization 头: {}", path, authHeader != null ? "存在" : "不存在");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);//：截取token字符串（去掉"Bearer "前缀）
            if (redisTokenService.isBlacklisted(token)) {
                log.warn("Token 已被加入黑名单");//检查token是否在Redis黑名单中（已登出的token会被加入黑名单）
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Token已失效，请重新登录\"}");
                return;
            } else {
                try {//使用密钥解析JWT token，验证签名是否有效
                    Claims claims = Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
                    String username = claims.getSubject();//：从token中提取用户名（subject字段）
                    Object uidClaim = claims.get("uid");
                    log.info("JWT 解析成功: username={}, uid={}", username, uidClaim);
                    
                    if (uidClaim != null) {
                        request.setAttribute("uid", uidClaim);
                    }
                    if (username != null) {
                        // 移除 SecurityContext 检查，确保每次都设置认证信息
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        log.info("用户详情加载成功: username={}, authorities={}", userDetails.getUsername(), userDetails.getAuthorities());
                        
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        //将认证信息存入 SecurityContext，后续Controller可以获取当前用户
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        log.info("认证成功设置到 SecurityContext: path={}, username={}", path, username);
                    } else {
                        log.warn("JWT 解析成功但 username 为 null");
                    }
                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    log.warn("JWT Token 已过期: {}", e.getMessage());
                    //如果token无效或过期，直接返回401错误，不会进入Controller
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Token已过期，请重新登录\"}");
                    return;
                } catch (Exception e) {
                    log.warn("JWT 解析失败: path={}, error={}", path, e.getMessage(), e);
                    // Token 无效时返回 401，而不是继续处理
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Token无效，请重新登录\"}");
                    return;
                }
            }
        } else {
            // 对于需要认证的路径，如果没有token，记录警告但继续处理
            // Spring Security会根据配置返回403或401
            log.warn("请求没有 Authorization 头，路径: {}", path);
        }
        
        // 在继续处理前，检查 SecurityContext 中的认证信息
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        log.info("继续处理请求前，SecurityContext 认证信息: path={}, authenticated={}, username={}", 
                path, currentAuth != null, currentAuth != null ? currentAuth.getName() : "null");
        
        filterChain.doFilter(request, response);
    }
}


