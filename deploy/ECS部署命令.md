# ECS 一键部署命令（复制到阿里云「远程连接」终端执行）

## 第一步：安装 git 并拉代码

```bash
apt update && apt install -y git docker-compose-plugin
cd /opt
git clone -b feature-my-updates https://github.com/zzc-s/Library-seat-reservation-system.git
cd Library-seat-reservation-system
```

若目录已存在，改为：

```bash
cd /opt/Library-seat-reservation-system
git fetch origin && git checkout feature-my-updates && git pull origin feature-my-updates
```

## 第二步：配置环境变量

```bash
cp .env.example .env
nano .env
```

按 `Ctrl+O` 保存，`Ctrl+X` 退出。必填项：

- `MYSQL_ROOT_PASSWORD` — 自设强密码
- `DB_PASSWORD` — 自设强密码
- `JWT_SECRET` — 32 位以上随机字符串
- `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` — QQ 邮箱与 SMTP 授权码
- `FRONTEND_BASE_URL=http://8.136.47.73`

## 第三步：处理 80 端口占用（当前服务器运行的是「秒杀系统」）

查看占用：

```bash
docker ps
ss -tlnp | grep :80
```

停止旧容器（示例）：

```bash
docker stop $(docker ps -q)
```

或只停占用 80 端口的容器。

## 第四步：部署

```bash
chmod +x deploy/deploy.sh
./deploy/deploy.sh
```

或一键脚本：

```bash
bash deploy/server-setup.sh
```

## 第五步：验证

```bash
docker compose ps
curl -s http://127.0.0.1/api/notices/public
```

浏览器打开：http://8.136.47.73
