package com.example.libraryseat.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.libraryseat.attendance.entity.AttendanceLog;
import com.example.libraryseat.attendance.mapper.AttendanceLogMapper;
import com.example.libraryseat.attendance.service.AttendanceService;
import com.example.libraryseat.book.entity.SeatBookLink;
import com.example.libraryseat.book.mapper.SeatBookLinkMapper;
import com.example.libraryseat.reservation.entity.Reservation;
import com.example.libraryseat.reservation.mapper.ReservationMapper;
import com.example.libraryseat.seat.service.SeatStatusService;
import com.example.libraryseat.user.entity.User;
import com.example.libraryseat.user.mapper.UserMapper;
import com.example.libraryseat.violation.entity.Violation;
import com.example.libraryseat.violation.mapper.ViolationMapper;
import com.example.libraryseat.attendance.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.example.libraryseat.common.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "签到出勤接口", description = "扫码签到、签退、暂离与出勤记录")
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceLogMapper attendanceLogMapper;
    private final ReservationMapper reservationMapper;
    private final AttendanceService attendanceService;
    private final UserMapper userMapper;
    private final SeatBookLinkMapper seatBookLinkMapper;
    private final StringRedisTemplate redis;
    private final ViolationMapper violationMapper;
    private final SeatStatusService seatStatusService;
    private final QrCodeService qrCodeService;

    @Value("${app.frontend-base-url:}")
    private String configuredFrontendBaseUrl;

    public AttendanceController(AttendanceLogMapper attendanceLogMapper, ReservationMapper reservationMapper, AttendanceService attendanceService, UserMapper userMapper, SeatBookLinkMapper seatBookLinkMapper, StringRedisTemplate redis, ViolationMapper violationMapper, SeatStatusService seatStatusService, QrCodeService qrCodeService) {
        this.attendanceLogMapper = attendanceLogMapper;
        this.reservationMapper = reservationMapper;
        this.attendanceService = attendanceService;
        this.userMapper = userMapper;
        this.seatBookLinkMapper = seatBookLinkMapper;
        this.redis = redis;
        this.violationMapper = violationMapper;
        this.seatStatusService = seatStatusService;
        this.qrCodeService = qrCodeService;
    }
    
    /**
     * 获取前端基础URL
     * 从请求头中获取，如果没有则使用默认值
     * 如果检测到localhost，会自动替换为局域网IP，以便手机访问
     */
    private String getFrontendBaseUrl(HttpServletRequest request) {
        if (configuredFrontendBaseUrl != null && !configuredFrontendBaseUrl.isBlank()) {
            return configuredFrontendBaseUrl.replaceAll("/$", "");
        }

        String baseUrl = null;
        int port = -1;

        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");

        if (origin != null && !origin.isEmpty()) {
            baseUrl = origin;
        } else if (referer != null && !referer.isEmpty()) {
            try {
                java.net.URL url = new java.net.URL(referer);
                baseUrl = url.getProtocol() + "://" + url.getHost();
                port = url.getPort();
            } catch (Exception e) {
                log.warn("解析Referer失败: {}", referer, e);
            }
        }

        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost";
            port = 5173;
        }

        if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            String localIp = getLocalNetworkIp();
            if (localIp != null && !localIp.isEmpty()) {
                baseUrl = baseUrl.replace("localhost", localIp).replace("127.0.0.1", localIp);
                log.info("检测到localhost，已替换为局域网IP: {}", baseUrl);
            } else {
                log.warn("无法获取局域网IP，二维码可能无法在手机上访问。请确保手机和电脑在同一网络，或手动配置前端URL");
            }
            if (port == -1) {
                port = 5173;
            }
        }

        if (port == -1) {
            return baseUrl;
        }
        if (port == 80 || port == 443) {
            return baseUrl;
        }
        return baseUrl + ":" + port;
    }
    
    /**
     * 获取本机的局域网IP地址
     * @return 局域网IP地址，如果获取失败则返回null
     */
    private String getLocalNetworkIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                // 跳过回环接口和未启用的接口
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    // 只返回IPv4地址，且不是回环地址
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                        String ip = address.getHostAddress();
                        // 排除169.254.x.x（Windows自动配置地址）和127.x.x.x（回环地址）
                        if (!ip.startsWith("169.254.") && !ip.startsWith("127.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取局域网IP失败", e);
        }
        return null;
    }

    public record ActionReq(Long reservationId, String note) {}

    @Operation(summary = "查看某次预约的签到日志")
    @GetMapping("/logs")
    public List<AttendanceLog> logs(@RequestParam Long reservationId) {
        return attendanceLogMapper.selectList(new LambdaQueryWrapper<AttendanceLog>()
                .eq(AttendanceLog::getReservationId, reservationId)
                .orderByAsc(AttendanceLog::getOccurredAt));
    }

    /**
     * 二维码扫码签到接口
     * 支持两种方式：
     * 1. 直接传入预约ID（传统方式）
     * 2. 传入座位二维码内容（扫码方式）- 系统自动匹配用户的预约
     */
    @Operation(summary = "扫码或按预约ID签到")
    @PostMapping("/checkin")
    public Map<String, Object> checkIn(@RequestBody Map<String, Object> req) {
        Long reservationId = null;
        
        // 支持固定座位二维码扫码
        if (req.containsKey("qrContent")) {
            try {
                Map<String, Object> qrData = qrCodeService.parseQrCode((String) req.get("qrContent"));
                
                if ("seat".equals(qrData.get("type"))) {
                    // 固定座位二维码：根据座位ID和当前用户自动匹配预约
                    Long seatId = (Long) qrData.get("seatId");
                    reservationId = findActiveReservationBySeatAndUser(seatId);
                    
                    if (reservationId == null) {
                        // 检查是否有已取消的预约，给出更明确的错误提示
                        String errorMessage = checkCancelledReservation(seatId);
                        if (errorMessage != null) {
                            throw new BusinessException(errorMessage);
                        }
                        throw new BusinessException(
                                "未找到该座位的有效预约，请确认您已预约此座位且预约时间已到"
                        );
                    }
                } else if ("checkin".equals(qrData.get("type"))) {
                    // 兼容旧格式的预约二维码
                    reservationId = (Long) qrData.get("reservationId");
                } else {
                    throw new BusinessException("二维码类型错误，请扫描座位上的二维码");
                }
            } catch (Exception e) {
                throw new BusinessException("二维码解析失败: " + e.getMessage());
            }
        } else if (req.containsKey("reservationId")) {
            reservationId = Long.valueOf(req.get("reservationId").toString());
        } else {
            throw new BusinessException("请提供预约ID或二维码内容");
        }
        
        String note = req.containsKey("note") ? (String) req.get("note") : "扫码签到";
        return createAction(new ActionReq(reservationId, note), "CHECK_IN", false);
    }

    /**
     * 二维码扫码签退接口(已取消扫码签到）
     * 支持两种方式：
     * 1. 直接传入预约ID（传统方式）
     * 2. 传入座位二维码内容（扫码方式）- 系统自动匹配用户的预约
     */
    @Operation(summary = "扫码或按预约ID签退")
    @PostMapping("/checkout")
    public Map<String, Object> checkOut(@RequestBody Map<String, Object> req) {
        Long reservationId = null;
        
        // 支持固定座位二维码扫码
        if (req.containsKey("qrContent")) {
            try {
                Map<String, Object> qrData = qrCodeService.parseQrCode((String) req.get("qrContent"));
                
                if ("seat".equals(qrData.get("type"))) {
                    // 固定座位二维码：根据座位ID和当前用户自动匹配预约
                    Long seatId = (Long) qrData.get("seatId");
                    reservationId = findActiveReservationBySeatAndUser(seatId);
                    
                    if (reservationId == null) {
                        throw new BusinessException(
                                "未找到该座位的有效预约，请确认您已预约此座位"
                        );
                    }
                } else if ("checkin".equals(qrData.get("type"))) {
                    // 兼容旧格式的预约二维码
                    reservationId = (Long) qrData.get("reservationId");
                } else {
                    throw new BusinessException("二维码类型错误，请扫描座位上的二维码");
                }
            } catch (Exception e) {
                throw new BusinessException("二维码解析失败: " + e.getMessage());
            }
        } else if (req.containsKey("reservationId")) {
            reservationId = Long.valueOf(req.get("reservationId").toString());
        } else {
            throw new BusinessException("请提供预约ID或二维码内容");
        }
        
        String note = req.containsKey("note") ? (String) req.get("note") : "扫码签退";
        return createAction(new ActionReq(reservationId, note), "CHECK_OUT", true);
    }
    
    /**
     * 检查是否有已取消的预约，返回相应的错误消息
     * @param seatId 座位ID
     * @return 错误消息，如果没有已取消的预约则返回null
     */
    private String checkCancelledReservation(Long seatId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 检查是否有已取消的预约（在时间窗口内：开始前5分钟至开始后5分钟）
        List<Reservation> cancelledReservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getSeatId, seatId)
                        .eq(Reservation::getUserId, user.getId())
                        .eq(Reservation::getStatus, "CANCELLED")
                        .le(Reservation::getStartTime, now.plusMinutes(5))
                        .ge(Reservation::getEndTime, now.minusMinutes(5))
                        .orderByDesc(Reservation::getStartTime)
                        .last("LIMIT 1")
        );
        
        if (!cancelledReservations.isEmpty()) {
            return "该预约已取消，无法进行签到。如需签到，请重新预约座位。";
        }
        
        return null;
    }
    
    /**
     * 根据座位ID和当前登录用户，查找有效的预约
     * @param seatId 座位ID
     * @return 预约ID，如果未找到则返回null
     */
    private Long findActiveReservationBySeatAndUser(Long seatId) {
        // 获取当前登录用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            log.warn("无法获取当前登录用户");
            return null;
        }
        
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
        if (user == null) {
            log.warn("用户不存在: {}", auth.getName());
            return null;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 查找该用户在该座位的有效预约（状态为ACTIVE或CONFIRMED，且在时间窗口内）
        // 时间窗口：预约开始前5分钟到开始后15分钟
        List<Reservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getSeatId, seatId)
                        .eq(Reservation::getUserId, user.getId())
                        .in(Reservation::getStatus, List.of("ACTIVE", "CONFIRMED"))
                        .le(Reservation::getStartTime, now.plusMinutes(5))  // 开始时间 <= 当前时间+5分钟（可提前5分钟签到）
                        .ge(Reservation::getStartTime, now.minusMinutes(15)) // 开始时间 >= 当前时间-15分钟（开始后15分钟内可签到）
                        .orderByDesc(Reservation::getStartTime)
                        .last("LIMIT 1")
        );
        
        if (reservations.isEmpty()) {
            log.info("用户 {} 在座位 {} 未找到有效预约", user.getId(), seatId);
            return null;
        }
        
        Reservation r = reservations.get(0);
        log.info("找到用户 {} 在座位 {} 的有效预约: {}", user.getId(), seatId, r.getId());
        return r.getId();
    }
    
    /**
     * 生成预约签到二维码
     * 返回包含URL的二维码，供用户扫码签到
     * 用户只能获取自己预约的二维码
     */
    @Operation(summary = "生成预约签到二维码")
    @GetMapping("/reservation/{reservationId}/qrcode")
    public Map<String, Object> getReservationQrCode(@PathVariable Long reservationId, HttpServletRequest request) {
        try {
            // 获取当前登录用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new BusinessException("未登录");
            }
            
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, auth.getName()));
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            
            Reservation r = reservationMapper.selectById(reservationId);
            if (r == null) {
                throw new BusinessException("预约不存在");
            }
            
            // 检查权限：用户只能获取自己预约的二维码
            if (!r.getUserId().equals(user.getId())) {
                throw BusinessException.forbidden("无权访问此预约的二维码");
            }
            
            // 检查预约状态：只有有效状态的预约才能生成二维码
            if (!List.of("ACTIVE", "CONFIRMED", "PENDING").contains(r.getStatus())) {
                throw new BusinessException("该预约状态不允许签到");
            }
            
            // 生成包含URL的二维码（供微信扫码）---->没有实现
            // URL格式：http://域名/checkin?qr=seat:{seatId}
            // 这样微信扫码后会跳转到签到页面，页面自动处理签到
            // 从请求头获取前端URL，如果没有则使用默认值
            String baseUrl = getFrontendBaseUrl(request);
            String qrContent = String.format("%s/checkin?qr=seat:%d", baseUrl, r.getSeatId());
            String qrCodeImage = qrCodeService.generateQrCodeFromContent(qrContent);
            
            // 记录生成的二维码URL，方便调试
            log.info("生成签到二维码 - 预约ID: {}, 座位ID: {}, 二维码URL: {}", reservationId, r.getSeatId(), qrContent);
            
            return Map.of(
                    "qrCode", qrCodeImage, 
                    "reservationId", reservationId, 
                    "seatId", r.getSeatId(),
                    "qrContent", qrContent,
                    "qrUrl", qrContent,
                    "note", "请使用手机扫描此二维码进行签到",
                    "tip", "如果手机无法访问，请确保手机和电脑连接同一WiFi，并在手机浏览器中访问: " + baseUrl
            );
        } catch (Exception e) {
            log.error("生成预约二维码失败，预约ID: {}", reservationId, e);
            throw new RuntimeException("生成二维码失败: " + e.getMessage());
        }
    }

    @Operation(summary = "开始暂离")
    @PostMapping("/leave/start")
    public Map<String, Object> leaveStart(@RequestBody ActionReq req) {
        return createAction(req, "LEAVE_START", false);
    }

    @Operation(summary = "结束暂离")
    @PostMapping("/leave/end")
    public Map<String, Object> leaveEnd(@RequestBody ActionReq req) {
        return createAction(req, "LEAVE_END", false);
    }


    private Map<String, Object> createAction(ActionReq req, String action, boolean isCheckOut) {
        if (req.reservationId() == null) {
            throw new BusinessException("reservationId 必填");
        }
        Reservation r = reservationMapper.selectById(req.reservationId());
        if (r == null || !List.of("ACTIVE", "CONFIRMED").contains(r.getStatus())) {
            throw new BusinessException("预约不存在或非有效状态");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(r.getStartTime().minusMinutes(5)) || now.isAfter(r.getStartTime().plusMinutes(15))) {
            throw new BusinessException("不在允许签到时间范围内（可提前5分钟签到，开始后15分钟内可签到）");
        }
        AttendanceLog attendanceLog = new AttendanceLog();
        attendanceLog.setUserId(r.getUserId());
        attendanceLog.setReservationId(r.getId());
        attendanceLog.setSeatId(r.getSeatId());
        attendanceLog.setAction(action);
        attendanceLog.setOccurredAt(now);
        attendanceLog.setNote(req.note());
        attendanceLogMapper.insert(attendanceLog);
        
        // 更新预约的签到/签退时间
        if ("CHECK_IN".equals(action)) {
            // 签到：更新check_in_time
            r.setCheckInTime(now);
            reservationMapper.updateById(r);
            log.info("更新预约签到时间，预约ID: {}, 签到时间: {}", r.getId(), now);
        } else if (isCheckOut) {
            // 签退：更新check_out_time
            r.setCheckOutTime(now);
            log.info("更新预约签退时间，预约ID: {}, 签退时间: {}", r.getId(), now);
        }
        
        // 更新座位状态
        try {
            if (isCheckOut) {
                // 签退后更新座位状态
                seatStatusService.onCheckOut(r.getSeatId());
            } else if ("CHECK_IN".equals(action)) {
                // 签到后更新座位状态为使用中
                seatStatusService.onCheckIn(r.getSeatId());
            }
        } catch (Exception e) {
            log.error("更新座位状态失败，座位ID: {}", r.getSeatId(), e);
            // 座位状态更新失败不影响签到签退操作
        }
        
        // 签退后更新预约状态为FINISHED，释放座位
        if (isCheckOut) {
            r.setStatus("FINISHED");
            reservationMapper.updateById(r);
            
            // 自动将关联的图书状态更新为已归还（RETURNED）
            try {
                List<SeatBookLink> bookLinks = seatBookLinkMapper.selectList(
                        new LambdaQueryWrapper<SeatBookLink>()
                                .eq(SeatBookLink::getReservationId, r.getId())
                                .in(SeatBookLink::getPlaceStatus, List.of("TO_PLACE", "PLACED", "CONFIRMED"))
                );
                for (SeatBookLink link : bookLinks) {
                    link.setPlaceStatus("RETURNED");
                    link.setUpdatedAt(now);
                    seatBookLinkMapper.updateById(link);
                    log.info("签退时自动更新图书状态为已归还，预约ID: {}, 图书关联ID: {}", r.getId(), link.getId());
                }
            } catch (Exception e) {
                log.error("签退时更新图书状态失败，预约ID: {}, 错误: {}", r.getId(), e.getMessage(), e);
                // 图书状态更新失败不 影响签退操作
            }
            
            log.info("签退成功，预约ID: {}, 用户ID: {}, 签退时间: {}", r.getId(), r.getUserId(), now);
        }
        
        // 返回包含预约ID和座位ID的完整信息
        Map<String, Object> result = new HashMap<>();
        result.put("reservationId", r.getId());
        result.put("seatId", r.getSeatId());
        result.put("userId", r.getUserId());
        result.put("action", action);
        result.put("occurredAt", attendanceLog.getOccurredAt());
        result.put("note", attendanceLog.getNote());
        result.put("message", "CHECK_IN".equals(action) ? "签到成功" : "操作成功");
        
        return result;
    }
}


