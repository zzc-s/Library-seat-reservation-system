# 图书馆座位预约系统

基于 `Spring Boot 3 + Vue 3 + MySQL 8.0 + Redis` 的前后端分离毕设项目。  
系统围绕“图书馆座位预约”主线，扩展了学习小组协同预约、图书借阅、违规管理、公告通知、反馈处理等完整业务闭环，支持普通用户与管理员双角色使用。

**代码仓库**：https://github.com/zzc-s/Library-seat-reservation-system  
**线上演示**（Docker 部署）：http://8.136.47.73

## 1. 项目定位

- 项目类型：本科毕业设计（工程实现类）
- 核心目标：提升图书馆座位资源利用率，规范预约与出勤流程，提供可运营的管理后台
- 技术路线：前后端分离 + JWT 鉴权 + RBAC 权限控制 + MyBatis-Plus 数据访问
- 特色能力：可视化选座、冲突校验、协同预约、预约与借阅联动、定时任务自动治理

## 2. 技术栈

### 后端

- `Java 17`
- `Spring Boot 3.3.3`
- `Spring Security`（JWT 认证、权限控制）
- `MyBatis-Plus 3.5.7`
- `MySQL 8.0`
- `Redis`（Token 黑名单、验证码/提醒缓存）
- `Spring Mail`（验证码、预约提醒）
- `WebSocket`（实时状态推送支持）

### 前端

- `Vue 3`（Composition API）
- `TypeScript`
- `Vite 5`
- `Vue Router 4`
- `Pinia`
- `Axios`

## 3. 系统功能

### 3.1 普通用户端

- 用户注册、登录、登出、找回密码、修改密码、个人信息维护
- 座位筛选（楼栋/楼层/区域/电源/靠窗等）与可视化选座
- 单人预约、批量预约、取消预约、我的预约记录查询
- 预约冲突检测（同座位同时间不可重叠）与状态流转管理
- 签到、签退、暂离管理与出勤日志查询
- 学习小组创建、发布、入组申请审批、协同预约与确认
- 图书浏览、收藏、借阅、订阅、归还记录查看
- 公告查看、系统通知查看与已读管理
- 反馈提交（公开/私密）与处理进度查看

### 3.2 管理员端

- 用户管理：列表、冻结/解冻、角色调整、信息维护
- 座位管理：增删改查、状态维护、批量导入
- 预约管理：查询、审核、拒绝说明、手动释放
- 违规管理：记录维护、筛选统计、批量处理、CSV 导出
- 图书管理：图书与分类维护、库存管理、封面上传
- 借阅管理：借阅记录检索、归还处理、逾期告警
- 公告管理：创建、编辑、发布状态控制
- 反馈管理：查看、回复、关闭、删除
- 数据看板：预约与借阅趋势、座位使用率统计

## 4. 业务规则（答辩重点）

- 预约时长上限：单次预约不超过 4 小时
- 时间约束：预约开始时间必须晚于当前时间
- 冲突规则：同一座位在时间窗口内不可重复预约
- 出勤窗口：开始前 5 分钟到结束后 5 分钟可签到/签退
- 签退行为：签退后预约自动结束并释放座位
- 借阅规则：借出扣库存、归还回库存；逾期自动标记并可触发告警治理
- 权限模型：`USER` 与 `ADMIN` 两级角色隔离

## 5. 目录结构

```text
.
├─ backend/                    # Spring Boot 后端服务
│  ├─ Dockerfile
│  └─ src/main/java/com/example/libraryseat/
├─ frontend/                   # Vue3 前端工程
│  ├─ Dockerfile
│  └─ src/
├─ database/
│  └─ library_seat.sql         # 初始化 SQL
├─ deploy/                     # 云服务器部署（Nginx、生产配置、脚本）
│  ├─ README.md
│  ├─ nginx.conf
│  └─ ECS部署命令.md
├─ docker-compose.yml          # 一键启动 MySQL + Redis + 后端 + Nginx
├─ uploads/                    # 本地上传目录（未纳入 Git，生产用 Docker 卷）
└─ README.md
```

## 6. 开发环境要求

- `JDK 17`（项目启用了 Java 版本约束）
- `Maven 3.6+`
- `Node.js 18+`
- `MySQL 8.0+`
- `Redis 5.0+`

## 7. 快速启动

### 7.1 数据库初始化

1. 创建数据库（如：`library_seat`）
2. 执行脚本：`database/library_seat.sql`

### 7.2 后端启动

```bash
mvn -f backend/pom.xml spring-boot:run
```

默认端口：`8081`（以 `application.yml` 实际配置为准）

### 7.3 前端启动

```bash
cd frontend
npm install
npm run dev
```

默认访问：`http://localhost:5173`  
前端通过 Vite 代理访问后端 `/api` 接口。

## 8. 关键配置说明

- **示例配置（可提交 GitHub）**：[`backend/src/main/resources/application-example.yml`](backend/src/main/resources/application-example.yml)
- **详细说明**：[`backend/CONFIG.md`](backend/CONFIG.md)

克隆项目后，复制示例为 `application-local.yml` 并填入本机环境，启动时通过 `--spring.config.additional-location=classpath:application-local.yml` 加载（无需修改仓库内的 `application.yml`）。

请至少配置：

- MySQL 连接（URL、用户名、密码）
- Redis 连接（Host、Port）
- JWT 密钥（建议使用高强度随机串，≥ 32 字节）
- 邮件 SMTP（用于验证码与提醒邮件）

> **安全提示**：若历史提交中曾包含真实邮箱授权码或数据库密码，上传公开仓库前请轮换密钥，详见 `backend/CONFIG.md`。

## 9. 主要接口分组

- 认证模块：`/api/auth/**`
- 座位模块：`/api/seats/**`
- 预约模块：`/api/reservations/**`
- 学习小组：`/api/groups/**`
- 图书/借阅：`/api/books/**`、`/api/borrows/**`
- 通知/反馈：`/api/notifications/**`、`/api/feedbacks/**`
- 管理员接口：`/api/admin/**`

> 项目已接入 Knife4j/OpenAPI，可通过后端文档入口查看接口明细（启动后按实际路径访问）。

## 10. 测试账号

执行 `database/library_seat.sql` 后会初始化演示账号，例如：

- 管理员：`admin`（角色 `ADMIN`，用于后台演示）
- 普通用户：可按需注册，或使用 SQL 中预置账号

> 演示环境密码请勿使用弱口令；公开仓库勿提交真实邮箱授权码与数据库密码。

## 11. 部署说明

### 11.1 Docker 部署（推荐，已用于云服务器）

项目已支持 `Docker Compose` 一键部署（MySQL、Redis、Spring Boot、Nginx 前端反代 + WebSocket）。

```bash
git clone https://github.com/zzc-s/Library-seat-reservation-system.git
cd Library-seat-reservation-system
cp .env.example .env    # 填写数据库密码、JWT、邮箱、FRONTEND_BASE_URL 等
docker compose up -d --build
```

- 详细步骤与安全组配置：[`deploy/README.md`](deploy/README.md)
- 服务器操作命令备忘：[`deploy/ECS部署命令.md`](deploy/ECS部署命令.md)
- 更新代码后重建前端：`git pull && docker compose up -d --build nginx`

**上传文件说明**：`uploads/`（头像、图书封面等）在 `.gitignore` 中，不会随 Git 部署。云上需在管理后台重新上传，或将文件拷贝到 Docker 卷 `uploads_data`。

### 11.2 传统部署（可选）

- 前端：`npm run build` 后使用 Nginx 托管静态资源
- 后端：打包为 Jar 后以 `java -jar` 方式运行
- 数据库与 Redis 使用独立服务
- 配置跨域与反向代理，统一通过网关域名访问；WebSocket 需 Nginx 配置 `Upgrade` 头（可参考 `deploy/nginx.conf`）

## 12. 项目亮点总结

- 业务完整：预约、出勤、借阅、通知、反馈、治理形成闭环
- 规则明确：冲突校验、时窗控制、状态流转具备工程可落地性
- 权限清晰：用户端与管理端职责边界明确
- 可扩展性好：模块化设计，便于后续加入信用分、推荐算法、移动端适配

## 13. 版权与说明

本项目仅用于学习交流与毕业设计成果展示。  
如需二次开发，请在保留原始项目说明的前提下进行。

