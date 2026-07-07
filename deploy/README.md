# Docker 云服务器部署说明

## 1. 阿里云安全组

在 ECS 控制台 → 网络与安全 → 安全组 → 入方向，放行：

| 端口 | 协议 | 授权对象 |
|------|------|----------|
| 22 | TCP | 你的本机 IP |
| 80 | TCP | 0.0.0.0/0 |

## 2. 服务器部署

```bash
# 安装 git（如未安装）
yum install -y git    # Alibaba Cloud Linux / CentOS
# apt install -y git  # Ubuntu

cd /opt
git clone https://github.com/zzc-s/Library-seat-reservation-system.git
cd Library-seat-reservation-system

cp .env.example .env
vi .env   # 填入数据库密码、JWT、邮箱授权码、FRONTEND_BASE_URL

chmod +x deploy/deploy.sh
./deploy/deploy.sh
```

或手动执行：

```bash
docker compose up -d --build
docker compose logs -f backend
```

## 3. 验证

| 检查项 | 地址 |
|--------|------|
| 首页 | http://8.136.47.73 |
| 接口文档 | http://8.136.47.73/doc.html |
| 健康检查 | `docker compose exec backend curl http://localhost:8081/actuator/health` |

## 4. 日常运维

```bash
docker compose ps
docker compose logs -f backend
docker compose restart backend
git pull && docker compose up -d --build
```

## 5. 注意事项

- `deploy/application-prod.yml` 已提交模板，敏感信息通过 `.env` 注入，`.env` 不要提交 Git
- MySQL 初始化 SQL 仅在首次创建数据卷时执行；重建数据库需 `docker compose down -v`
- 上传文件保存在 Docker 卷 `uploads_data` 中
