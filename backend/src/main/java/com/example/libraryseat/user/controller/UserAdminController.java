package com.example.libraryseat.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.entity.AttendanceLog;
import com.example.libraryseat.attendance.mapper.AttendanceLogMapper;
import com.example.libraryseat.borrow.entity.Borrow;
import com.example.libraryseat.borrow.mapper.BorrowMapper;
import com.example.libraryseat.feedback.entity.Feedback;
import com.example.libraryseat.feedback.mapper.FeedbackMapper;
import com.example.libraryseat.group.entity.GroupJoinRequest;
import com.example.libraryseat.group.entity.GroupMember;
import com.example.libraryseat.group.entity.GroupNotification;
import com.example.libraryseat.group.entity.StudyGroup;
import com.example.libraryseat.group.mapper.GroupJoinRequestMapper;
import com.example.libraryseat.group.mapper.GroupMemberMapper;
import com.example.libraryseat.group.mapper.GroupNotificationMapper;
import com.example.libraryseat.group.mapper.StudyGroupMapper;
import com.example.libraryseat.notification.entity.UserNotification;
import com.example.libraryseat.notification.mapper.UserNotificationMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.security.EmailService;
import com.example.libraryseat.security.VerificationCodeService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.user.service.UserDeletionService;
import com.example.libraryseat.violation.entity.Violation;
import com.example.libraryseat.violation.mapper.ViolationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Tag(name = "用户管理接口（管理员）", description = "用户列表、冻结解冻、角色管理与删除")
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ReservationMapper reservationMapper;
    private final AttendanceLogMapper attendanceLogMapper;
    private final ViolationMapper violationMapper;
    private final StudyGroupMapper studyGroupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final GroupJoinRequestMapper groupJoinRequestMapper;
    private final GroupNotificationMapper groupNotificationMapper;
    private final FeedbackMapper feedbackMapper;
    private final BorrowMapper borrowMapper;
    private final UserNotificationMapper userNotificationMapper;
    private final UserDeletionService userDeletionService;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,50}$");
    /** 与注册接口一致：11 位中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 20;

    public UserAdminController(
            UserMapper userMapper, 
            PasswordEncoder passwordEncoder,
            ReservationMapper reservationMapper,
            AttendanceLogMapper attendanceLogMapper,
            ViolationMapper violationMapper,
            StudyGroupMapper studyGroupMapper,
            GroupMemberMapper groupMemberMapper,
            GroupJoinRequestMapper groupJoinRequestMapper,
            GroupNotificationMapper groupNotificationMapper,
            FeedbackMapper feedbackMapper,
            BorrowMapper borrowMapper,
            UserNotificationMapper userNotificationMapper,
            UserDeletionService userDeletionService,
            EmailService emailService,
            VerificationCodeService verificationCodeService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.reservationMapper = reservationMapper;
        this.attendanceLogMapper = attendanceLogMapper;
        this.violationMapper = violationMapper;
        this.studyGroupMapper = studyGroupMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.groupJoinRequestMapper = groupJoinRequestMapper;
        this.groupNotificationMapper = groupNotificationMapper;
        this.feedbackMapper = feedbackMapper;
        this.borrowMapper = borrowMapper;
        this.userNotificationMapper = userNotificationMapper;
        this.userDeletionService = userDeletionService;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
    }

    /**
     * 管理员新建用户：向未占用邮箱发送验证码（需登录且为管理员，见 SecurityConfig）
     */
    @Operation(summary = "管理员新建用户 - 发送邮箱验证码")
    @PostMapping("/create-account-code")
    public ResponseEntity<?> sendCreateUserEmailCode(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确"));
        }
        User emailExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailExists != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "该邮箱已被使用，请使用其他邮箱"));
        }
        String code = verificationCodeService.generateAndStore(email);
        // 异步发送：避免接口等待 SMTP 发送导致前端超时；发送失败会在后端日志体现
        emailService.sendCodeAsync(email, "图书馆座位预约 - 管理员新建用户验证码",
                "您的验证码是：" + code + "，10分钟内有效。如非本人操作请忽略。");
        return ResponseEntity.ok(Map.of("message", "验证码发送请求已提交，请稍后查收邮件"));
    }

    @Operation(summary = "冻结用户账号")
    @PostMapping("/{id}/freeze")
    public ResponseEntity<?> freeze(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        if (u == null) return ResponseEntity.notFound().build();

        // 防止冻结预置的超级管理员账号
        if ("admin".equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能冻结预置管理员账号"));
        }

        // 禁止冻结自己
        String current = currentUsername();
        if (current != null && current.equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能冻结自己的账号"));
        }
        
        u.setIsFrozen(true);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("message", "frozen"));
    }

    @Operation(summary = "解冻用户账号")
    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<?> unfreeze(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        if (u == null) return ResponseEntity.notFound().build();
        
        // 禁止解冻自己（虽然逻辑上解冻自己是可以的，但为了保持一致性，也禁止）
        // 实际上解冻自己是可以的，所以这里不检查
        
        u.setIsFrozen(false);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("message", "unfrozen"));
    }

    /**
     * 将用户提升为管理员（仅管理员可操作）
     * 注意：不能将预置的 admin 账号降级
     */
    @Operation(summary = "将普通用户提升为管理员")
    @PostMapping("/{id}/promote")
    public ResponseEntity<?> promoteToAdmin(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        if ("ADMIN".equals(u.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("message", "已是管理员，无需重复提升"));
        }
        // 防止将预置的 admin 账号降级
        if ("admin".equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改预置管理员账号的角色"));
        }
        u.setRole("ADMIN");
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("message", "已提升为管理员"));
    }

    /**
     * 将管理员降级为普通用户（仅管理员可操作）
     * 注意：不能将预置的 admin 账号降级
     */
    @Operation(summary = "将管理员降级为普通用户")
    @PostMapping("/{id}/demote")
    public ResponseEntity<?> demoteToUser(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        // 防止将预置的 admin 账号降级
        if ("admin".equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改预置管理员账号的角色"));
        }
        String current = currentUsername();
        // 禁止对自己进行降级
        if (current != null && current.equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能对自己降级，请使用其他管理员账号操作"));
        }
        // 仅超级管理员 admin 可以对其他管理员进行降级
        if ("ADMIN".equals(u.getRole())) {
            if (!"admin".equals(current)) {
                return ResponseEntity.badRequest().body(Map.of("message", "只有预置管理员可降级其他管理员"));
            }
        }
        u.setRole("USER");
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("message", "已降级为普通用户"));
    }

    /**
     * 删除普通用户（仅管理员可操作）
     * 注意：
     * 1. 只能删除普通用户（USER角色），不能删除管理员
     * 2. 不能删除预置的 admin 账号
     * 3. 删除前会检查用户是否有相关数据，如果有则提示无法删除
     */
    @Operation(summary = "删除普通用户（包含相关数据检查与清理）")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 防止删除预置的 admin 账号
        if ("admin".equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能删除预置管理员账号"));
        }
        
        // 只能删除普通用户，不能删除管理员
        if ("ADMIN".equals(u.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能删除管理员账号，请先将其降级为普通用户"));
        }
        
        List<String> relatedData = userDeletionService.collectDeletionBlockers(id);
        if (!relatedData.isEmpty()) {
            String message = "无法删除该用户，该用户存在以下未完成的数据：" + String.join("、", relatedData) +
                    "。请先处理完相关数据后再删除用户。";
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }

        return userDeletionService.purgeRelatedDataAndDeleteUser(id);
    }

    /**
     * 管理员创建新用户
     */
    @Operation(summary = "管理员创建新用户（支持设置角色和手机）")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> reqMap) {
        // 从 Map 中提取字段，支持 phone 字段
        String username = (String) reqMap.get("username");
        String password = (String) reqMap.get("password");
        String email = (String) reqMap.get("email");
        String phone = (String) reqMap.get("phone");
        String role = (String) reqMap.get("role");
        String code = (String) reqMap.get("code");
        
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
        
        // 验证邮箱格式
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确，请输入有效的邮箱地址"));
        }

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
        
        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhone(phoneNormalized);
        user.setRole(role != null && "ADMIN".equals(role) ? "ADMIN" : "USER");
        user.setIsFrozen(false);
        user.setIsBlacklisted(false); // 新用户默认不在黑名单
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        userMapper.insert(user);
        verificationCodeService.clear(email);
        return ResponseEntity.ok(Map.of("message", "用户创建成功", "userId", user.getId()));
    }
    
    /**
     * 修改用户名（仅管理员可操作）
     */
    @Operation(summary = "修改指定用户的用户名")
    @PutMapping("/{id}/username")
    public ResponseEntity<?> updateUsername(@PathVariable("id") Long id, @RequestBody Map<String, String> req) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }
        
        String newUsername = req.get("username");
        if (newUsername == null || newUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名不能为空"));
        }
        
        // 验证用户名格式
        if (newUsername.length() < 1 || newUsername.length() > 50) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名长度必须在1-50个字符之间"));
        }
        if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名只能包含字母、数字和下划线"));
        }
        
        // 防止修改预置的 admin 账号
        if ("admin".equals(u.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改预置管理员账号的用户名"));
        }
        
        // 检查新用户名是否已被使用
        User exists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, newUsername));
        if (exists != null && !exists.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在，请选择其他用户名"));
        }
        
        u.setUsername(newUsername);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("message", "用户名修改成功"));
    }
    
    /**
     * 获取用户详情（包含统计信息）
     */
    @Operation(summary = "查看指定用户详情与统计信息")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserDetail(@PathVariable("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 统计信息
        long reservationCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>().eq(Reservation::getUserId, id));
        long violationCount = violationMapper.selectCount(
                new LambdaQueryWrapper<Violation>().eq(Violation::getUserId, id));
        long borrowCount = borrowMapper.selectCount(
                new LambdaQueryWrapper<Borrow>().eq(Borrow::getUserId, id));
        long feedbackCount = feedbackMapper.selectCount(
                new LambdaQueryWrapper<Feedback>().eq(Feedback::getUserId, id));
        
        Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("id", user.getId());
        detail.put("username", user.getUsername());
        detail.put("email", user.getEmail());
        detail.put("phone", user.getPhone());
        detail.put("role", user.getRole());
        detail.put("isFrozen", Boolean.TRUE.equals(user.getIsFrozen()));
        detail.put("isBlacklisted", Boolean.TRUE.equals(user.getIsBlacklisted()));
        detail.put("avatarUrl", user.getAvatarUrl());
        detail.put("createdAt", user.getCreatedAt());
        detail.put("updatedAt", user.getUpdatedAt());
        
        // 统计信息
        detail.put("statistics", Map.of(
                "reservationCount", reservationCount,
                "violationCount", violationCount,
                "borrowCount", borrowCount,
                "feedbackCount", feedbackCount
        ));
        
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 更新用户信息
     */
    @Operation(summary = "批量更新用户基础信息（邮箱、电话、角色、密码等）")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable("id") Long id, @RequestBody Map<String, Object> req) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        // 防止修改预置的 admin 账号
        if ("admin".equals(user.getUsername()) && req.containsKey("username")) {
            return ResponseEntity.badRequest().body(Map.of("message", "不能修改预置管理员账号的信息"));
        }
        
        boolean updated = false;
        
        // 更新用户名
        if (req.containsKey("username")) {
            String newUsername = (String) req.get("username");
            if (newUsername != null && !newUsername.isBlank()) {
                if (newUsername.length() < 1 || newUsername.length() > 50) {
                    return ResponseEntity.badRequest().body(Map.of("message", "用户名长度必须在1-50个字符之间"));
                }
                if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "用户名只能包含字母、数字和下划线"));
                }
                
                // 检查新用户名是否已被使用
                User exists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, newUsername));
                if (exists != null && !exists.getId().equals(id)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "用户名已存在，请选择其他用户名"));
                }
                
                user.setUsername(newUsername);
                updated = true;
            }
        }
        
        // 更新邮箱
        if (req.containsKey("email")) {
            String email = (String) req.get("email");
            if (email != null && !email.isBlank()) {
                if (!EMAIL_PATTERN.matcher(email).matches()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "邮箱格式不正确，请输入有效的邮箱地址"));
                }
                
                // 检查邮箱是否已被使用
                User emailExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
                if (emailExists != null && !emailExists.getId().equals(id)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "该邮箱已被使用，请使用其他邮箱"));
                }
                
                user.setEmail(email);
                updated = true;
            }
        }
        
        // 更新电话（与注册一致：可选；填写则 11 位大陆号且不可重复）
        if (req.containsKey("phone")) {
            String phone = (String) req.get("phone");
            if (phone != null && !phone.isBlank()) {
                String phoneNorm = phone.trim();
                if (phoneNorm.length() > 20) {
                    return ResponseEntity.badRequest().body(Map.of("message", "手机号长度无效"));
                }
                if (!PHONE_PATTERN.matcher(phoneNorm).matches()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "手机号格式不正确，请输入11位中国大陆手机号"));
                }
                User phoneExists = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phoneNorm));
                if (phoneExists != null && !phoneExists.getId().equals(id)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "该手机号已被注册"));
                }
                user.setPhone(phoneNorm);
                updated = true;
            } else if (phone == null || phone.isBlank()) {
                user.setPhone(null);
                updated = true;
            }
        }
        
        // 更新角色
        if (req.containsKey("role")) {
            String role = (String) req.get("role");
            if (role != null && ("ADMIN".equals(role) || "USER".equals(role))) {
                // 防止将预置 admin 降级
                if ("admin".equals(user.getUsername()) && "USER".equals(role)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "不能将预置管理员账号降级"));
                }
                user.setRole(role);
                updated = true;
            }
        }
        
        // 更新密码（如果提供）
        if (req.containsKey("password") && req.get("password") != null) {
            String password = (String) req.get("password");
            if (!password.isBlank()) {
                if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
                    return ResponseEntity.badRequest().body(Map.of("message", "密码长度必须在6-20个字符之间"));
                }
                user.setPasswordHash(passwordEncoder.encode(password));
                updated = true;
            }
        }
        
        if (updated) {
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            return ResponseEntity.ok(Map.of("message", "用户信息更新成功"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "没有需要更新的信息"));
        }
    }
    
    /**
     * 创建用户请求DTO
     */
    public record CreateUserRequest(String username, String password, String email, String role) {}

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}


