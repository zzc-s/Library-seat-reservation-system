package com.example.libraryseat.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.auth.dto.AuthDtos.*;
import com.example.libraryseat.security.JwtProperties;
import com.example.libraryseat.security.JwtService;
import com.example.libraryseat.security.RedisTokenService;
import com.example.libraryseat.security.EmailService;
import com.example.libraryseat.security.VerificationCodeService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.user.service.UserDeletionService;
import com.example.libraryseat.config.AppInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static com.example.libraryseat.auth.dto.AuthDtos.DeleteAccountRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Tag(name = "认证与账号接口", description = "登录、注册、找回密码、个人信息等")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final UserDeletionService userDeletionService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,50}$");
    /** 可选手机号：11 位中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 20;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager, JwtService jwtService,
                          JwtProperties jwtProperties, RedisTokenService redisTokenService,
                          EmailService emailService, VerificationCodeService verificationCodeService,
                          UserDeletionService userDeletionService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.redisTokenService = redisTokenService;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
        this.userDeletionService = userDeletionService;
    }

    @Operation(summary = "用户注册")
    @PostMapping(value = "/register", consumes = {"multipart/form-data"})
    public ResponseEntity<?> register(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("email") String email,
            @RequestParam("code") String code,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        // 验证用户名
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
        }
        if (username.length() < 1 || username.length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名长度必须在1-50个字符之间"));
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名只能包含字母、数字和下划线"));
        }
        
        // 验证密码
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不能为空"));
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码长度必须在6-20个字符之间"));
        }
        
        // 验证邮箱
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确，请输入有效的邮箱地址"));
        }

        // 验证邮箱验证码
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码不能为空"));
        }
        if (!verificationCodeService.verify(email, code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码错误或已过期"));
        }
        
        // 检查用户名是否已存在
        User exists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在，请选择其他用户名"));
        }
        
        // 检查邮箱是否已被使用
        User emailExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailExists != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "该邮箱已被使用，请使用其他邮箱"));
        }

        String phoneNormalized = null;
        if (phone != null && !phone.isBlank()) {
            phoneNormalized = phone.trim();
            if (phoneNormalized.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of("message", "手机号长度无效"));
            }
            if (!PHONE_PATTERN.matcher(phoneNormalized).matches()) {
                return ResponseEntity.badRequest().body(Map.of("message", "手机号格式不正确，请输入11位中国大陆手机号"));
            }
            User phoneExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phoneNormalized));
            if (phoneExists != null) {
                return ResponseEntity.badRequest().body(Map.of("message", "该手机号已被注册"));
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhone(phoneNormalized);
        user.setRole("USER");
        
        // 处理头像上传
        if (avatar != null && !avatar.isEmpty()) {
            try {
                String originalFilename = avatar.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".") 
                        ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
                String filename = UUID.randomUUID().toString() + extension;
                
                Path uploadDir = Paths.get("uploads/avatars");
                if (!Files.exists(uploadDir)) {
                    Files.createDirectories(uploadDir);
                }
                
                Path filePath = uploadDir.resolve(filename);
                Files.write(filePath, avatar.getBytes());
                user.setAvatarUrl("/uploads/avatars/" + filename);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("message", "头像上传失败：" + e.getMessage()));
            }
        }
        
        userMapper.insert(user);
        verificationCodeService.clear(email);
        return ResponseEntity.ok(Map.of("message", "注册成功"));
    }

    @Operation(summary = "发送注册验证码到邮箱")
    @PostMapping("/register-code")
    public ResponseEntity<?> sendRegisterCode(@RequestBody RegisterCodeRequest req) {
        if (req == null || req.email() == null || req.email().isBlank() || !EMAIL_PATTERN.matcher(req.email()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确"));
        }

        // 注册验证码需要确保邮箱未被占用
        User emailExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.email()));
        if (emailExists != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "该邮箱已被使用，请使用其他邮箱"));
        }

        String code = verificationCodeService.generateAndStore(req.email());
        // 异步发送：避免接口等待 SMTP 发送导致前端超时；发送失败会在后端日志体现
        emailService.sendCodeAsync(req.email(), "图书馆座位预约 - 注册验证码", "您的验证码是：" + code + "，10分钟内有效。");
        return ResponseEntity.ok(Map.of("message", "验证码发送请求已提交，请稍后查收邮件"));
    }

    @Operation(summary = "用户登录获取 JWT")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        // 先检查用户是否存在以及是否被冻结
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.username()));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "该用户未注册"));
        }
        // 检查用户是否被冻结
        if (Boolean.TRUE.equals(user.getIsFrozen())) {
            return ResponseEntity.status(403).body(Map.of("message", "您的账号已被冻结，无法登录。请联系管理员。"));
        }
        
        try {
            Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
            SecurityContextHolder.getContext().setAuthentication(auth);
            String token = jwtService.generateToken(req.username(), Map.of(
                    "role", user.getRole() != null ? user.getRole() : "USER",
                    "uid", user.getId()
            ));
            // 返回 token 和服务器启动时间，用于前端检测后端是否重启
            return ResponseEntity.ok(new TokenResponse(token, AppInfo.getStartupTime()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("message", "用户名或密码错误"));
        }
    }

    /**
     * 发送找回密码验证码
     */
    @Operation(summary = "发送找回密码验证码到邮箱")
    @PostMapping("/forgot")
    public ResponseEntity<?> forgot(@RequestBody ForgotRequest req) {
        if (req.email() == null || req.email().isBlank() || !EMAIL_PATTERN.matcher(req.email()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确"));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.email()));
        // 不暴露邮箱是否存在
        String code = verificationCodeService.generateAndStore(req.email());
        // 异步发送：避免接口等待 SMTP 发送导致前端超时；无论邮箱是否存在都返回成功，防止撞库
        emailService.sendCodeAsync(req.email(), "图书馆座位预约 - 找回密码验证码", "您的验证码是：" + code + "，10分钟内有效。");
        return ResponseEntity.ok(Map.of("message", "验证码发送请求已提交，请稍后查收邮件"));
    }

    /**
     * 使用邮箱验证码重置密码
     */
    @Operation(summary = "使用邮箱验证码重置密码")
    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody ResetRequest req) {
        if (req.email() == null || req.email().isBlank() || !EMAIL_PATTERN.matcher(req.email()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确"));
        }
        if (req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码不能为空"));
        }
        //据邮箱查询用户  如果找到返回 User 对象，否则返回 null
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.email()));
        if (user == null) {
            // 不暴露邮箱是否存在
            return ResponseEntity.ok(Map.of("message", "重置成功"));
        }
        boolean ok = verificationCodeService.verify(req.email(), req.code());
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("message", "验证码错误或已过期"));
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(user);
        verificationCodeService.clear(req.email());
        // 可选：将现有 token 加入黑名单（这里仅依赖前端清除登录态）
        return ResponseEntity.ok(Map.of("message", "重置成功"));
    }

    /**
     * 已登录用户修改密码
     */
    @Operation(summary = "已登录用户修改密码")
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req) {
        Authentication authContext = SecurityContextHolder.getContext().getAuthentication();
        if (authContext == null || authContext.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, authContext.getName()));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "原密码不正确"));
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(user);
        // 前端可在收到 200 后清理 token 并跳转登录
        return ResponseEntity.ok(Map.of("message", "密码已更新，请重新登录"));
    }

    @Operation(summary = "获取当前登录用户的个人信息")
    @GetMapping("/profile")
    public ResponseEntity<?> profile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "用户不存在"));
        }
        return ResponseEntity.ok(profilePayload(user));
    }

    private Map<String, Object> profilePayload(User user) {
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("role", user.getRole());
        userInfo.put("avatarUrl", user.getAvatarUrl());
        userInfo.put("createdAt", user.getCreatedAt());
        userInfo.put("serverStartTime", AppInfo.getStartupTime());
        return userInfo;
    }

    private static String normStr(Object o) {
        if (o == null) {
            return "";
        }
        return String.valueOf(o).trim();
    }

    @Operation(summary = "更新当前用户账号信息（邮箱、手机；预置 admin 不可改用户名；改用户名后返回新 token）")
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体不能为空"));
        }
        Authentication authContext = SecurityContextHolder.getContext().getAuthentication();
        if (authContext == null || authContext.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, authContext.getName()));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "用户不存在"));
        }

        boolean updated = false;
        boolean usernameChanged = false;

        if (body.containsKey("username")) {
            String newName = normStr(body.get("username"));
            if (newName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
            }
            if ("admin".equals(user.getUsername())) {
                if (!"admin".equals(newName)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "预置管理员账号不可修改用户名，可修改邮箱与手机号"));
                }
            } else if (!newName.equals(user.getUsername())) {
                if (!USERNAME_PATTERN.matcher(newName).matches()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "用户名只能包含字母、数字和下划线，长度不超过50个字符"));
                }
                User exists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, newName));
                if (exists != null && !exists.getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在，请选择其他用户名"));
                }
                user.setUsername(newName);
                usernameChanged = true;
                updated = true;
            }
        }

        if (body.containsKey("email")) {
            String email = normStr(body.get("email"));
            if (email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "邮箱不能为空"));
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确，请输入有效的邮箱地址"));
            }
            if (!Objects.equals(email, user.getEmail())) {
                User emailExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
                if (emailExists != null && !emailExists.getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "该邮箱已被使用，请使用其他邮箱"));
                }
                user.setEmail(email);
                updated = true;
            }
        }

        if (body.containsKey("phone")) {
            String raw = body.get("phone") == null ? "" : String.valueOf(body.get("phone")).trim();
            if (raw.isEmpty()) {
                if (user.getPhone() != null && !user.getPhone().isEmpty()) {
                    user.setPhone(null);
                    updated = true;
                }
            } else {
                if (!PHONE_PATTERN.matcher(raw).matches()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "手机号格式不正确，请输入11位中国大陆手机号"));
                }
                User phoneExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, raw));
                if (phoneExists != null && !phoneExists.getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "该手机号已被注册"));
                }
                if (!Objects.equals(raw, user.getPhone())) {
                    user.setPhone(raw);
                    updated = true;
                }
            }
        }

        if (!updated) {
            return ResponseEntity.badRequest().body(Map.of("message", "没有需要更新的信息"));
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        Map<String, Object> out = new HashMap<>(profilePayload(user));
        if (usernameChanged) {
            String token = jwtService.generateToken(user.getUsername(), Map.of(
                    "role", user.getRole() != null ? user.getRole() : "USER",
                    "uid", user.getId()
            ));
            out.put("token", token);
            out.put("message", "账号信息已更新；用户名已变更，请使用返回的新 token（前端将自动刷新登录态）");
        } else {
            out.put("message", "账号信息已更新");
        }
        return ResponseEntity.ok(out);
    }

    /**
     * 普通用户自助注销：校验密码；条件与管理员删除普通用户一致（未完成预约/未还书/任小组组长则不可注销）。
     */
    @Operation(summary = "自助注销账户（仅普通用户）")
    @DeleteMapping("/account")
    public ResponseEntity<?> deleteOwnAccount(
            @RequestBody DeleteAccountRequest req,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "用户不存在"));
        }
        if ("admin".equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "预置管理员不可自助注销"));
        }
        if ("ADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("message", "管理员账号无法在用户端自助注销，请联系系统管理员"));
        }
        if (req == null || req.password() == null || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请输入当前密码以确认注销"));
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "密码不正确"));
        }

        List<String> blockers = userDeletionService.collectDeletionBlockers(user.getId());
        if (!blockers.isEmpty()) {
            String message = "无法注销账户，您仍存在未完成事项：" + String.join("、", blockers)
                    + "。请先处理完毕后再注销。";
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }

        ResponseEntity<?> result = userDeletionService.purgeRelatedDataAndDeleteUser(user.getId());
        if (result.getStatusCode().is2xxSuccessful() && authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            redisTokenService.blacklistToken(token, Duration.ofSeconds(jwtProperties.getAccessTokenTtlSeconds()));
        }
        if (result.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.ok(Map.of("message", "账户已注销"));
        }
        return result;
    }

    @Operation(summary = "退出登录（将当前 Token 加入黑名单）")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(name = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            redisTokenService.blacklistToken(token, Duration.ofSeconds(jwtProperties.getAccessTokenTtlSeconds()));
        }
        return ResponseEntity.ok(Map.of("message", "已登出"));
    }

    @Operation(summary = "上传头像")
    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file, 
                                         @RequestHeader(name = "Authorization", required = false) String authorization) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "文件不能为空"));
            }
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
            String filename = UUID.randomUUID().toString() + extension;
            
            Path uploadDir = Paths.get("uploads/avatars");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, file.getBytes());
         //获取认证信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
            if (user != null) {
                // 如果用户已有头像，删除旧的头像文件
                if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                    try {
                        String oldAvatarPath = user.getAvatarUrl();
                        // 处理相对路径和绝对路径
                        if (oldAvatarPath.startsWith("/uploads/")) {
                            Path oldFilePath = Paths.get(oldAvatarPath.substring(1)); // 去掉开头的"/"
                            if (Files.exists(oldFilePath)) {
                                Files.delete(oldFilePath);
                            }
                        }
                    } catch (IOException e) {
                        // 删除旧文件失败不影响新头像上传，只记录日志
                        System.err.println("删除旧头像文件失败: " + e.getMessage());
                    }
                }
                user.setAvatarUrl("/uploads/avatars/" + filename);
                userMapper.updateById(user);
            }
            
            return ResponseEntity.ok(Map.of("avatarUrl", "/uploads/avatars/" + filename));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("message", "上传失败：" + e.getMessage()));
        }
    }

    /**
     * 移除头像（将avatarUrl设置为null）
     */
    @Operation(summary = "移除头像")
    @PostMapping("/remove-avatar")
    public ResponseEntity<?> removeAvatar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("removeAvatar 被调用，Authentication: " + (auth != null ? auth.getName() : "null"));
        if (auth == null || auth.getName() == null) {
            System.out.println("removeAvatar: 认证失败，返回 401");
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "用户不存在"));
        }
        
        // 如果用户有头像，尝试删除头像文件
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            try {
                String avatarPath = user.getAvatarUrl();
                // 处理相对路径和绝对路径
                if (avatarPath.startsWith("/uploads/")) {
                    Path filePath = Paths.get(avatarPath.substring(1)); // 去掉开头的"/"
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                    }
                }
            } catch (IOException e) {
                // 删除文件失败不影响移除操作，只记录日志
                System.err.println("删除头像文件失败: " + e.getMessage());
            }
        }
        
        // 使用 UpdateWrapper 将 avatarUrl 设置为 null（MyBatis-Plus 默认不会更新 null 值）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User> updateWrapper = 
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, user.getId())
                     .set(User::getAvatarUrl, null);
        userMapper.update(null, updateWrapper);
        
        System.out.println("removeAvatar: 已将用户 " + user.getUsername() + " 的头像URL设置为null");
        
        return ResponseEntity.ok(Map.of("message", "头像已移除"));
    }
}


