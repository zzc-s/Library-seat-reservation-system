package com.example.libraryseat.seat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.libraryseat.seat.entity.Seat;
import com.example.libraryseat.seat.mapper.SeatMapper;
import com.example.libraryseat.websocket.SeatStatusWebSocketHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Tag(name = "座位管理接口（管理员）", description = "座位CRUD、状态管理与批量导入")
@RestController
@RequestMapping("/api/admin/seats")
@PreAuthorize("hasRole('ADMIN')")
public class SeatAdminController {
    private final SeatMapper seatMapper;
    private final SeatStatusWebSocketHandler webSocketHandler;

    private static final List<String> IMPORT_HEADERS = List.of(
            "标签", "楼栋", "楼层", "区域类型", "行号", "列号", "有电源", "靠窗", "状态", "备注"
    );
    private static final Set<String> ALLOWED_STATUS = new HashSet<>(Arrays.asList(
            "FREE", "IDLE", "RESERVED", "OCCUPIED", "BROKEN", "FAULT"
    ));
    private static final Set<String> ALLOWED_ZONE = new HashSet<>(Arrays.asList(
            "安静区", "自习区"
    ));
    private static final Set<Integer> ALLOWED_FLOOR = new HashSet<>(Arrays.asList(1, 2));
    // 标签格式：A1-001（楼栋前缀 + 楼层 + "-" + 三位序号）
    private static final Pattern LABEL_PATTERN = Pattern.compile("^([A-Za-z0-9]+)(\\d+)-(\\d{3})$");

    public SeatAdminController(SeatMapper seatMapper, SeatStatusWebSocketHandler webSocketHandler) {
        this.seatMapper = seatMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @Operation(summary = "分页查询座位列表")
    @GetMapping
    //：GET /api/admin/seats?current=1&size=10&floor=1&zone=安静区
    public Map<String, Object> list(@RequestParam(defaultValue = "1") Integer current,
                                    @RequestParam(defaultValue = "10") Integer size,
                                    @RequestParam(required = false) Integer floor,
                                    @RequestParam(required = false) String zone,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) Boolean hasPower,
                                    @RequestParam(required = false) Boolean isWindow,
                                    @RequestParam(required = false) String labelQuery) {
        log.info("收到座位管理列表请求: current={}, size={}, floor={}, zone={}, status={}, hasPower={}, isWindow={}, labelQuery={}",
                current, size, floor, zone, status, hasPower, isWindow, labelQuery);
        //创建分页对象和查询构造器。
        Page<Seat> page = new Page<>(current, size);
        LambdaQueryWrapper<Seat> qw = new LambdaQueryWrapper<>();
        // 精确筛选：楼层 / 区域类型 / 状态 / 有电源 / 靠窗
        if (floor != null) {
            qw.eq(Seat::getFloor, floor);
        }
        String z = zone != null ? zone.trim() : "";
        if (!z.isEmpty()) {
            qw.eq(Seat::getZone, z);
        }
        String st = status != null ? status.trim() : "";
        if (!st.isEmpty()) {
            qw.eq(Seat::getStatus, st);
        }
        if (hasPower != null) {
            qw.eq(Seat::getHasPower, hasPower);
        }
        if (isWindow != null) {
            qw.eq(Seat::getIsWindow, isWindow);
        }

        // 模糊筛选：标签（可扩展到备注，但不与楼层混在一起）
        String lq = labelQuery != null ? labelQuery.trim() : "";
        if (!lq.isEmpty()) {
            qw.and(w -> w.like(Seat::getLabel, lq).or().like(Seat::getNote, lq));
        }

        qw.orderByAsc(Seat::getId);
        Page<Seat> result = seatMapper.selectPage(page, qw);
        
        log.info("查询结果: total={}, records={}", result.getTotal(), result.getRecords().size());
        long totalAll = seatMapper.selectCount(null);
        
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal()); // 筛选后的总数（用于分页）
        response.put("totalAll", totalAll);       // 不筛选的总数（用于前端展示）
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        response.put("pages", result.getPages());
        log.info("返回响应: {}", response);
        return response;
    }

    @Operation(summary = "新增座位")
    @PostMapping//：POST /api/admin/seats
    public ResponseEntity<?> create(@RequestBody Seat seat) {
        // 与批量导入一致的字段校验（避免“单个新增”绕过规则）
        String err = validateSeatForAdminUpsert(seat, null);
        if (err != null) {
            return ResponseEntity.badRequest().body(Map.of("message", err));
        }
        // 标签唯一性校验（与导入一致：标签不可重复）
        Seat labelHit = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getLabel, seat.getLabel())
                .last("LIMIT 1"));
        if (labelHit != null) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    String.format("座位标签重复：%s（已被 ID=%d 占用）", seat.getLabel(), labelHit.getId())));
        }
        // 坐标唯一性校验：同楼栋/楼层/区域/行/列只能有一个座位（避免可视化布局“重叠/覆盖”）
        Seat coordHit = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getBuilding, seat.getBuilding())
                .eq(Seat::getFloor, seat.getFloor())
                .eq(Seat::getZone, seat.getZone())
                .eq(Seat::getRowNum, seat.getRowNum())
                .eq(Seat::getColNum, seat.getColNum())
                .last("LIMIT 1"));
        if (coordHit != null) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    String.format("坐标冲突：%s，已被座位 %s（ID=%d）占用",
                            buildCoordKey(seat), coordHit.getLabel(), coordHit.getId())));
        }
        fillAreaIfBlank(seat);
        seatMapper.insert(seat);
        // 新增座位属于“元数据变更”，通知客户端刷新座位列表/分组
        webSocketHandler.broadcastSeatRefresh("admin-create");
        return ResponseEntity.ok(seat);
    }

    @Operation(summary = "更新座位信息")
    @PutMapping("/{id}")//：PUT /api/admin/seats/123
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Seat seat) {
        // 获取更新前的座位状态
        Seat oldSeat = seatMapper.selectById(id);
        if (oldSeat == null) {
            return ResponseEntity.status(404).body(Map.of("message", "座位不存在或已被删除"));
        }
        String oldStatus = oldSeat != null ? oldSeat.getStatus() : null;

        // 与批量导入一致的字段校验（避免绕过规则）
        String err = validateSeatForAdminUpsert(seat, oldSeat);
        if (err != null) {
            return ResponseEntity.badRequest().body(Map.of("message", err));
        }
        // 标签唯一性：任何情况下都不能与其他座位冲突（即使保持不变，也要防止历史重复数据继续扩散）
        String newLabel = seat.getLabel() != null ? seat.getLabel().trim() : "";
        Seat labelHit = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getLabel, newLabel)
                .ne(Seat::getId, id)
                .last("LIMIT 1"));
        if (labelHit != null) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    String.format("座位标签重复：%s（已被 ID=%d 占用）", newLabel, labelHit.getId())));
        }
        // 坐标唯一性：同楼栋/楼层/区域/行/列只能有一个座位（避免可视化布局重叠）
        Seat coordHit = seatMapper.selectOne(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getBuilding, seat.getBuilding())
                .eq(Seat::getFloor, seat.getFloor())
                .eq(Seat::getZone, seat.getZone())
                .eq(Seat::getRowNum, seat.getRowNum())
                .eq(Seat::getColNum, seat.getColNum())
                .ne(Seat::getId, id)
                .last("LIMIT 1"));
        if (coordHit != null) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    String.format("坐标冲突：%s，已被座位 %s（ID=%d）占用",
                            buildCoordKey(seat), coordHit.getLabel(), coordHit.getId())));
        }
        
        seat.setId(id);
        fillAreaIfBlank(seat);
        seatMapper.updateById(seat);
        
        // 若区域/布局等元数据变更，通知客户端刷新（跨浏览器实时生效）
        boolean metaChanged =
                !java.util.Objects.equals(oldSeat.getLabel(), seat.getLabel()) ||
                !java.util.Objects.equals(oldSeat.getBuilding(), seat.getBuilding()) ||
                !java.util.Objects.equals(oldSeat.getFloor(), seat.getFloor()) ||
                !java.util.Objects.equals(oldSeat.getZone(), seat.getZone()) ||
                !java.util.Objects.equals(oldSeat.getArea(), seat.getArea()) ||
                !java.util.Objects.equals(oldSeat.getRowNum(), seat.getRowNum()) ||
                !java.util.Objects.equals(oldSeat.getColNum(), seat.getColNum()) ||
                !java.util.Objects.equals(oldSeat.getHasPower(), seat.getHasPower()) ||
                !java.util.Objects.equals(oldSeat.getIsWindow(), seat.getIsWindow());

        // 如果状态发生变化，通过 WebSocket 广播状态更新
        String newStatus = seat.getStatus();
        if (oldStatus == null || !oldStatus.equals(newStatus)) {
            // 将状态映射为数字：0-空闲、1-已预约、2-使用中、3-故障、4-维修
            Integer statusNum = 0;
            if ("FREE".equals(newStatus) || "IDLE".equals(newStatus)) {
                statusNum = 0;
            } else if ("RESERVED".equals(newStatus)) {
                statusNum = 1;
            } else if ("OCCUPIED".equals(newStatus)) {
                statusNum = 2;
            } else if ("BROKEN".equals(newStatus)) {
                statusNum = 3;
            } else if ("FAULT".equals(newStatus)) {
                statusNum = 4;
            }
            
            // 广播状态更新事件
            webSocketHandler.broadcastSeatStatusUpdate(id, statusNum, newStatus);
            log.info("座位 {} 状态已更新: {} -> {}, 已广播 WebSocket 事件", id, oldStatus, newStatus);
        }
       // 如果座位位置/属性变化，通知客户端重新查询完整列表。
        if (metaChanged) {
            webSocketHandler.broadcastSeatRefresh("admin-meta-update");
        }
        
        return ResponseEntity.ok(seat);
    }

    /**
     * 单个新增/编辑的校验逻辑：与 Excel 批量导入规则保持一致。
     * @param seat 请求体
     * @param oldSeat 更新时传入原记录（用于允许部分字段为空时继承旧值）；新增时传 null
     */
    private String validateSeatForAdminUpsert(Seat seat, Seat oldSeat) {
        if (seat == null) return "请求体不能为空";

        // 统一 trim / 规范化
        String label = seat.getLabel() != null ? seat.getLabel().trim() : "";
        String building = seat.getBuilding() != null ? seat.getBuilding().trim() : "";
        String zone = seat.getZone() != null ? seat.getZone().trim() : "";
        String status = seat.getStatus() != null ? seat.getStatus().trim().toUpperCase() : "";
        seat.setLabel(label);
        seat.setBuilding(building);
        seat.setZone(zone);
        seat.setStatus(status);
        if (seat.getNote() != null) seat.setNote(seat.getNote().trim());

        // 必填字段
        if (label.isEmpty()) return "座位标签不能为空";
        if (building.isEmpty()) return "楼栋不能为空";
        if (!"A楼".equals(building)) return "楼栋仅允许填写 A楼";
        Integer floor = seat.getFloor();
        if (floor == null) return "楼层不能为空（仅允许 1 或 2）";
        if (!ALLOWED_FLOOR.contains(floor)) return "楼层仅允许 1 或 2";
        if (zone.isEmpty()) return "区域类型不能为空（仅允许：安静区/自习区）";
        if (!ALLOWED_ZONE.contains(zone)) return "区域类型不合法，仅允许：安静区/自习区";
        Integer rowNum = seat.getRowNum();
        Integer colNum = seat.getColNum();
        if (rowNum == null || rowNum <= 0) return "行号不能为空，且必须为正整数";
        if (colNum == null || colNum <= 0) return "列号不能为空，且必须为正整数";
        Boolean hasPower = seat.getHasPower();
        Boolean isWindow = seat.getIsWindow();
        if (hasPower == null) return "有电源不能为空（仅允许：是/否）";
        if (isWindow == null) return "靠窗不能为空（仅允许：是/否）";
        if (status.isEmpty()) return "状态不能为空";
        if (!ALLOWED_STATUS.contains(status)) return "状态不合法（允许：" + ALLOWED_STATUS + "）";

        // 标签格式 + 与楼栋/楼层一致
        String buildingPrefix = extractBuildingPrefix(building);
        if (buildingPrefix.isEmpty()) return "楼栋格式不支持，无法校验标签";
        String expectedLabelPrefix = buildingPrefix + floor;
        Matcher m = LABEL_PATTERN.matcher(label);
        if (!m.matches()) {
            return "标签格式不合法（" + label + "），应为 " + expectedLabelPrefix + "-001 这种格式（例：" + expectedLabelPrefix + "-019）";
        }
        String labelPrefix = m.group(1) + m.group(2);
        if (!expectedLabelPrefix.equalsIgnoreCase(labelPrefix)) {
            return "标签与楼栋/楼层不一致：标签前缀为 " + labelPrefix + "，按楼栋/楼层应为 " + expectedLabelPrefix + "（示例：" + expectedLabelPrefix + "-001）";
        }
        return null;
    }

    @Operation(summary = "删除座位")
    @DeleteMapping("/{id}")//DELETE /api/admin/seats/123
    public ResponseEntity<?> delete(@PathVariable Long id) {
        //删除后广播刷新通知
        seatMapper.deleteById(id);
        webSocketHandler.broadcastSeatRefresh("admin-delete");
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    @Operation(summary = "批量删除座位")
    @PostMapping("/batch-delete")//：POST /api/admin/seats/batch-delete
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, Object> req) {
        //解析请求体
        Object idsObj = req.get("ids");
        if (!(idsObj instanceof List<?> idsRaw) || idsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择要删除的座位"));
        }
        List<Long> ids = new ArrayList<>();
        for (Object o : idsRaw) {
            if (o == null) continue;
            try {
                ids.add(Long.parseLong(String.valueOf(o)));
            } catch (Exception ignored) {
                // ignore invalid
            }
        }
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "请选择要删除的座位"));
        }
        //逐个删除
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                int affected = seatMapper.deleteById(id);
                if (affected > 0) {
                    success++;
                } else {
                    errors.add("座位不存在或已删除：ID=" + id);
                }
            } catch (Exception e) {
                errors.add("删除失败：ID=" + id + "，原因：" + e.getMessage());
            }
        }
        Map<String, Object> resp = new HashMap<>();
        //返回结果
        resp.put("message", "批量删除完成");
        resp.put("total", ids.size());
        resp.put("success", success);
        if (!errors.isEmpty()) {
            resp.put("errors", errors);
        }
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "批量创建座位（JSON）")//已经没用到该需求
    @PostMapping("/batch")
    public ResponseEntity<?> batchCreate(@RequestBody List<Seat> seats) {
        if (seats == null || seats.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "座位列表不能为空"));
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            String err = validateSeatForAdminUpsert(seat, null);
            if (err != null) {
                errors.add(String.format("第 %d 条：%s", i + 1, err));
                continue;
            }
            Long labelExists = seatMapper.selectCount(new LambdaQueryWrapper<Seat>().eq(Seat::getLabel, seat.getLabel()));
            if (labelExists != null && labelExists > 0) {
                errors.add(String.format("第 %d 条：座位标签已存在（%s）", i + 1, seat.getLabel()));
                continue;
            }
            Long coordExists = seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                    .eq(Seat::getBuilding, seat.getBuilding())
                    .eq(Seat::getFloor, seat.getFloor())
                    .eq(Seat::getZone, seat.getZone())
                    .eq(Seat::getRowNum, seat.getRowNum())
                    .eq(Seat::getColNum, seat.getColNum()));
            if (coordExists != null && coordExists > 0) {
                errors.add(String.format("第 %d 条：座位坐标已存在（%s）", i + 1, buildCoordKey(seat)));
                continue;
            }
            fillAreaIfBlank(seat);
            seatMapper.insert(seat);
            success++;
        }
        Map<String, Object> out = new HashMap<>();
        out.put("message", "批量创建完成");
        out.put("total", seats.size());
        out.put("success", success);
        if (!errors.isEmpty()) out.put("errors", errors);
        return ResponseEntity.ok(out);
    }

    /**
     * Excel 批量导入座位
     * 支持 .xlsx / .xls 格式
     * Excel 列格式：标签、楼栋、楼层、区域类型、行号、列号、有电源、靠窗、状态、备注
     */
    @Operation(summary = "批量导入座位（Excel）")
    @PostMapping("/batch-excel")//POST /api/admin/seats/batch-excel
    public ResponseEntity<?> batchImportFromExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件不能为空"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件名不能为空"));
        }
        String lower = filename.toLowerCase();
        if (!(lower.endsWith(".xlsx") || lower.endsWith(".xls"))) {
            return ResponseEntity.badRequest().body(Map.of("message", "仅支持 Excel 文件（.xlsx / .xls）"));
        }

        List<Seat> seats = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = WorkbookFactory.create(inputStream);

            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();

            if (rowCount < 2) {
                return ResponseEntity.badRequest().body(Map.of("message", "Excel 文件至少需要包含表头和数据行"));
            }

            // 1) 校验表头（第一行必须与模板一致）
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Excel 表头缺失，请使用标准模板"));
            }
            List<String> actualHeaders = new ArrayList<>();
            for (int c = 0; c < IMPORT_HEADERS.size(); c++) {
                Cell cell = headerRow.getCell(c);
                actualHeaders.add(getCellValue(cell).trim());
            }
            if (!IMPORT_HEADERS.equals(actualHeaders)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Excel 表头不符合标准模板（列名/顺序必须完全一致）",
                        "expected", IMPORT_HEADERS,
                        "actual", actualHeaders
                ));
            }

            // 2) 跳过表头，从第2行开始读取（索引从0开始，所以是1）
            Set<String> labelsInFile = new HashSet<>();
            Set<String> coordsInFile = new HashSet<>();
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    Seat seat = new Seat();

                    // 标签（必填，第0列）
                    Cell labelCell = row.getCell(0);
                    if (labelCell == null || getCellValue(labelCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：座位标签不能为空", i + 1));
                        continue;
                    }
                    String label = getCellValue(labelCell).trim();
                    if (labelsInFile.contains(label)) {
                        errors.add(String.format("第 %d 行：座位标签重复（%s）", i + 1, label));
                        continue;
                    }
                    labelsInFile.add(label);
                    seat.setLabel(label);

                    // 楼栋（必填，第1列）
                    Cell buildingCell = row.getCell(1);
                    if (buildingCell == null || getCellValue(buildingCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：楼栋不能为空", i + 1));
                        continue;
                    }
                    String building = getCellValue(buildingCell).trim();
                    if (!"A楼".equals(building)) {
                        errors.add(String.format("第 %d 行：楼栋仅允许填写 A楼（当前：%s）", i + 1, building));
                        continue;
                    }
                    seat.setBuilding(building);

                    // 楼层（必填，第2列）
                    Cell floorCell = row.getCell(2);
                    if (floorCell == null || getCellValue(floorCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：楼层不能为空", i + 1));
                        continue;
                    }
                    try {
                        seat.setFloor((int) requirePositiveInt(floorCell, "楼层"));
                    } catch (Exception e) {
                        errors.add(String.format("第 %d 行：楼层必须是正整数", i + 1));
                        continue;
                    }
                    if (!ALLOWED_FLOOR.contains(seat.getFloor())) {
                        errors.add(String.format("第 %d 行：楼层仅允许 1 或 2（当前：%d）", i + 1, seat.getFloor()));
                        continue;
                    }

                    // 标签格式校验：统一为 A1-001，并与「楼栋/楼层」保持一致
                    String buildingPrefix = extractBuildingPrefix(building);
                    if (buildingPrefix.isEmpty()) {
                        errors.add(String.format("第 %d 行：楼栋格式不支持（%s），无法校验标签。建议楼栋填写如：A楼/B楼", i + 1, building));
                        continue;
                    }
                    String expectedLabelPrefix = buildingPrefix + seat.getFloor();
                    Matcher m = LABEL_PATTERN.matcher(label);
                    if (!m.matches()) {
                        errors.add(String.format("第 %d 行：标签格式不合法（%s），应为 %s-001 这种格式（例：%s-001）",
                                i + 1, label, expectedLabelPrefix, expectedLabelPrefix));
                        continue;
                    }
                    String labelPrefix = m.group(1) + m.group(2);
                    if (!expectedLabelPrefix.equalsIgnoreCase(labelPrefix)) {
                        errors.add(String.format("第 %d 行：标签与楼栋/楼层不一致：标签前缀为 %s，按楼栋/楼层应为 %s（示例：%s-001）",
                                i + 1, labelPrefix, expectedLabelPrefix, expectedLabelPrefix));
                        continue;
                    }

                    // 区域类型（必填，第3列）
                    Cell zoneCell = row.getCell(3);
                    if (zoneCell == null || getCellValue(zoneCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：区域类型不能为空（仅允许：安静区/自习区）", i + 1));
                        continue;
                    }
                    String zone = getCellValue(zoneCell).trim();
                    if (!ALLOWED_ZONE.contains(zone)) {
                        errors.add(String.format("第 %d 行：区域类型不合法（%s），仅允许：安静区/自习区", i + 1, zone));
                        continue;
                    }
                    seat.setZone(zone);
                    // area 为空时自动补齐为“一楼安静区/一楼自习区...”以匹配可视化分组逻辑
                    fillAreaIfBlank(seat);

                    // 行号（必填，第4列）
                    Cell rowNumCell = row.getCell(4);
                    if (rowNumCell == null || getCellValue(rowNumCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：行号不能为空", i + 1));
                        continue;
                    }
                    try {
                        seat.setRowNum((int) requirePositiveInt(rowNumCell, "行号"));
                    } catch (Exception e) {
                        errors.add(String.format("第 %d 行：行号必须是正整数（不能写字母/中文）", i + 1));
                        continue;
                    }

                    // 列号（必填，第5列）
                    Cell colNumCell = row.getCell(5);
                    if (colNumCell == null || getCellValue(colNumCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：列号不能为空", i + 1));
                        continue;
                    }
                    try {
                        seat.setColNum((int) requirePositiveInt(colNumCell, "列号"));
                    } catch (Exception e) {
                        errors.add(String.format("第 %d 行：列号必须是正整数（不能写字母/中文）", i + 1));
                        continue;
                    }

                    // 有电源（必填，第6列，只允许 是/否）
                    Cell hasPowerCell = row.getCell(6);
                    if (hasPowerCell == null || getCellValue(hasPowerCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：有电源不能为空（仅允许：是/否）", i + 1));
                        continue;
                    }
                    Boolean hasPower = parseYesNoStrict(getCellValue(hasPowerCell).trim());
                    if (hasPower == null) {
                        errors.add(String.format("第 %d 行：有电源仅允许填写“是”或“否”（当前：%s）", i + 1, getCellValue(hasPowerCell).trim()));
                        continue;
                    }
                    seat.setHasPower(hasPower);

                    // 靠窗（必填，第7列，只允许 是/否）
                    Cell isWindowCell = row.getCell(7);
                    if (isWindowCell == null || getCellValue(isWindowCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：靠窗不能为空（仅允许：是/否）", i + 1));
                        continue;
                    }
                    Boolean isWindow = parseYesNoStrict(getCellValue(isWindowCell).trim());
                    if (isWindow == null) {
                        errors.add(String.format("第 %d 行：靠窗仅允许填写“是”或“否”（当前：%s）", i + 1, getCellValue(isWindowCell).trim()));
                        continue;
                    }
                    seat.setIsWindow(isWindow);

                    // 状态（必填，第8列，枚举校验）
                    Cell statusCell = row.getCell(8);
                    if (statusCell == null || getCellValue(statusCell).trim().isEmpty()) {
                        errors.add(String.format("第 %d 行：状态不能为空（允许：%s）", i + 1, ALLOWED_STATUS));
                        continue;
                    }
                    String status = getCellValue(statusCell).trim().toUpperCase();
                    if (!ALLOWED_STATUS.contains(status)) {
                        errors.add(String.format("第 %d 行：状态不合法（%s），允许：%s", i + 1, status, ALLOWED_STATUS));
                        continue;
                    }
                    seat.setStatus(status);

                    // 备注（可选，第9列）
                    Cell noteCell = row.getCell(9);
                    if (noteCell != null) {
                        seat.setNote(getCellValue(noteCell).trim());
                    }

                    // 同文件内坐标重复校验（同楼栋/楼层/区域/行/列不允许重复）
                    String coordKey = buildCoordKey(seat);
                    if (coordsInFile.contains(coordKey)) {
                        errors.add(String.format("第 %d 行：同楼栋/楼层/区域下行号/列号重复（%s）", i + 1, coordKey));
                        continue;
                    }
                    coordsInFile.add(coordKey);

                    seats.add(seat);
                } catch (Exception e) {
                    errors.add(String.format("第 %d 行：解析失败 - %s", i + 1, e.getMessage()));
                }
            }

            workbook.close();

            if (seats.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Excel 文件中没有有效的座位数据"));
            }

            // 3) 与数据库重复校验（同标签不允许重复导入）
            List<String> labels = seats.stream().map(Seat::getLabel).toList();
            if (!labels.isEmpty()) {
                List<Seat> existing = seatMapper.selectList(
                        new LambdaQueryWrapper<Seat>().in(Seat::getLabel, labels));
                if (existing != null && !existing.isEmpty()) {
                    Set<String> existsLabels = existing.stream().map(Seat::getLabel).collect(java.util.stream.Collectors.toSet());
                    // 过滤掉已存在的，并记录错误
                    List<Seat> filtered = new ArrayList<>();
                    for (Seat s : seats) {
                        if (existsLabels.contains(s.getLabel())) {
                            errors.add(String.format("座位标签已存在，无法导入：%s", s.getLabel()));
                        } else {
                            filtered.add(s);
                        }
                    }
                    seats = filtered;
                }
            }

            // 4) 与数据库坐标重复校验（同楼栋/楼层/区域/行/列不允许重复导入）
            if (!seats.isEmpty()) {
                List<Seat> filtered = new ArrayList<>();
                for (Seat s : seats) {
                    Long coordExists = seatMapper.selectCount(new LambdaQueryWrapper<Seat>()
                            .eq(Seat::getBuilding, s.getBuilding())
                            .eq(Seat::getFloor, s.getFloor())
                            .eq(Seat::getZone, s.getZone())
                            .eq(Seat::getRowNum, s.getRowNum())
                            .eq(Seat::getColNum, s.getColNum()));
                    if (coordExists != null && coordExists > 0) {
                        errors.add(String.format("座位坐标已存在，无法导入：%s（标签：%s）", buildCoordKey(s), s.getLabel()));
                    } else {
                        filtered.add(s);
                    }
                }
                seats = filtered;
            }

            if (seats.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "没有可导入的数据（可能全部与现有座位标签重复）",
                        "errors", errors
                ));
            }

            // 批量插入
            int successCount = 0;
            for (Seat seat : seats) {
                try {
                    seatMapper.insert(seat);
                    successCount++;
                } catch (Exception e) {
                    log.error("插入座位失败: {}", seat.getLabel(), e);
                    errors.add(String.format("座位 %s 插入失败: %s", seat.getLabel(), e.getMessage()));
                }
            }

            // 批量导入属于“元数据变更”，导入完成后广播一次刷新即可
            if (successCount > 0) {
                webSocketHandler.broadcastSeatRefresh("admin-import");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("message", "导入完成");
            result.put("total", seats.size());
            result.put("success", successCount);
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Excel 导入失败", e);
            return ResponseEntity.status(500).body(Map.of("message", "Excel 导入失败: " + e.getMessage()));
        }
    }

    /**
     * 批量导出座位为 Excel（.xlsx）
     * 列：ID、标签、楼栋、楼层、区域类型、行号、列号、有电源、靠窗、状态、备注
     */
    @Operation(summary = "批量导出座位（Excel）")
    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public void exportExcel(HttpServletResponse response) throws IOException {
        List<Seat> list = seatMapper.selectList(new LambdaQueryWrapper<Seat>().orderByAsc(Seat::getId));

        String filename = "seats-export.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");
        //创建工作簿
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("座位列表");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "标签", "楼栋", "楼层", "区域类型", "行号", "列号", "有电源", "靠窗", "状态", "备注"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }
            int rowNum = 1;
            for (Seat s : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(s.getId() != null ? s.getId() : 0);
                row.createCell(1).setCellValue(s.getLabel() != null ? s.getLabel() : "");
                row.createCell(2).setCellValue(s.getBuilding() != null ? s.getBuilding() : "");
                row.createCell(3).setCellValue(s.getFloor() != null ? s.getFloor() : 0);
                row.createCell(4).setCellValue(s.getZone() != null ? s.getZone() : "");
                row.createCell(5).setCellValue(s.getRowNum() != null ? s.getRowNum() : 0);
                row.createCell(6).setCellValue(s.getColNum() != null ? s.getColNum() : 0);
                row.createCell(7).setCellValue(Boolean.TRUE.equals(s.getHasPower()) ? "是" : "否");
                row.createCell(8).setCellValue(Boolean.TRUE.equals(s.getIsWindow()) ? "是" : "否");
                row.createCell(9).setCellValue(s.getStatus() != null ? s.getStatus() : "");
                row.createCell(10).setCellValue(s.getNote() != null ? s.getNote() : "");
            }
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        }
    }

    /**
     * 下载座位导入模板（.xlsx）
     * 表头与导入格式强绑定：标签、楼栋、楼层、区域类型、行号、列号、有电源、靠窗、状态、备注
     */
    @Operation(summary = "下载座位导入模板（Excel）")
    @GetMapping(value = "/import-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        String filename = "座位导入模板.xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setCharacterEncoding("UTF-8");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("座位导入");

            // 表头
            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < IMPORT_HEADERS.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(IMPORT_HEADERS.get(i));
            }

            // 示例行（标签格式与系统一致：A1-001）
            Row sample1 = sheet.createRow(1);
            sample1.createCell(0).setCellValue("A1-001");
            sample1.createCell(1).setCellValue("A楼");
            sample1.createCell(2).setCellValue(1);
            sample1.createCell(3).setCellValue("安静区");
            sample1.createCell(4).setCellValue(1);
            sample1.createCell(5).setCellValue(1);
            sample1.createCell(6).setCellValue("是");
            sample1.createCell(7).setCellValue("是");
            sample1.createCell(8).setCellValue("FREE");
            sample1.createCell(9).setCellValue("靠窗有电源");

            Row sample2 = sheet.createRow(2);
            sample2.createCell(0).setCellValue("A1-002");
            sample2.createCell(1).setCellValue("A楼");
            sample2.createCell(2).setCellValue(1);
            sample2.createCell(3).setCellValue("自习区");
            sample2.createCell(4).setCellValue(1);
            sample2.createCell(5).setCellValue(2);
            sample2.createCell(6).setCellValue("否");
            sample2.createCell(7).setCellValue("是");
            sample2.createCell(8).setCellValue("FREE");
            sample2.createCell(9).setCellValue("靠窗无电源");

            // 调整列宽，使模板观感接近导出结果、便于录入
            int[] colChars = {10, 8, 6, 10, 6, 6, 8, 8, 10, 20};
            for (int c = 0; c < IMPORT_HEADERS.size(); c++) {
                int chars = c < colChars.length ? colChars[c] : 12;
                sheet.setColumnWidth(c, chars * 256);
            }

            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        }
    }

    /**
     * 获取单元格的字符串值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // 避免科学计数法，转为整数或保留小数
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (int) numValue) {
                        return String.valueOf((int) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * 获取单元格的数值
     */
    private double getNumericCellValue(Cell cell) {
        if (cell == null) return 0;
        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            default:
                return 0;
        }
    }

    private long requirePositiveInt(Cell cell, String fieldName) {
        double v = getNumericCellValue(cell);
        long lv = (long) v;
        if (v != lv || lv <= 0) {
            throw new IllegalArgumentException(fieldName + "必须是正整数");
        }
        return lv;
    }

    private Boolean parseYesNo(String raw) {
        String v = raw.trim().toLowerCase();
        if ("是".equals(v) || "有".equals(v) || "true".equals(v) || "1".equals(v)) return true;
        if ("否".equals(v) || "无".equals(v) || "false".equals(v) || "0".equals(v)) return false;
        return null;
    }

    private Boolean parseYesNoStrict(String raw) {
        String v = raw == null ? "" : raw.trim();
        if ("是".equals(v)) return true;
        if ("否".equals(v)) return false;
        return null;
    }

    private String extractBuildingPrefix(String building) {
        if (building == null) return "";
        String b = building.trim();
        if (b.isEmpty()) return "";
        // 常见写法：A楼/B楼/C楼，取首个字母数字作为前缀
        for (int i = 0; i < b.length(); i++) {
            char ch = b.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                return String.valueOf(ch).toUpperCase();
            }
        }
        return "";
    }

    private String buildCoordKey(Seat seat) {
        if (seat == null) return "";
        return String.format("%s-%s-%s-(%s,%s)",
                seat.getBuilding(),
                seat.getFloor(),
                seat.getZone(),
                seat.getRowNum(),
                seat.getColNum());
    }

    private void fillAreaIfBlank(Seat seat) {
        if (seat == null) return;
        String area = seat.getArea();
        if (area != null && !area.trim().isEmpty()) return;
        Integer floor = seat.getFloor();
        String zone = seat.getZone() != null ? seat.getZone().trim() : "";
        if (floor == null || floor <= 0) return;
        String floorCn = floorToCn(floor) + "楼";
        if (!zone.isEmpty()) {
            seat.setArea(floorCn + zone);
        } else {
            seat.setArea(floorCn + "未分类");
        }
    }

    private String floorToCn(int floor) {
        // 只覆盖常见楼层；超出则直接用数字兜底
        return switch (floor) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> String.valueOf(floor);
        };
    }

    @Operation(summary = "更新座位状态")
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> req) {
        Seat seat = seatMapper.selectById(id);
        if (seat == null) {
            return ResponseEntity.status(404).body(Map.of("message", "座位不存在"));
        }
        
        // 获取更新前的状态
        String oldStatus = seat.getStatus();
        String newStatus = req.get("status");
        
        seat.setStatus(newStatus);
        seatMapper.updateById(seat);
        
        // 如果状态发生变化，通过 WebSocket 广播状态更新
        if (oldStatus == null || !oldStatus.equals(newStatus)) {
            // 将状态映射为数字：0-空闲、1-已预约、2-使用中、3-故障、4-维修
            Integer statusNum = 0;
            if ("FREE".equals(newStatus) || "IDLE".equals(newStatus)) {
                statusNum = 0;
            } else if ("RESERVED".equals(newStatus)) {
                statusNum = 1;
            } else if ("OCCUPIED".equals(newStatus)) {
                statusNum = 2;
            } else if ("BROKEN".equals(newStatus)) {
                statusNum = 3;
            } else if ("FAULT".equals(newStatus)) {
                statusNum = 4;
            }
            
            // 广播状态更新事件
            webSocketHandler.broadcastSeatStatusUpdate(id, statusNum, newStatus);
            log.info("座位 {} 状态已更新: {} -> {}, 已广播 WebSocket 事件", id, oldStatus, newStatus);
        }
        
        return ResponseEntity.ok(seat);
    }

}
