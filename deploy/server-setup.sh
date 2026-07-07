#!/bin/bash
# 在 ECS 服务器上执行此脚本（Ubuntu 22.04）
# 用法：bash server-setup.sh
set -euo pipefail

REPO_URL="https://github.com/zzc-s/Library-seat-reservation-system.git"
BRANCH="feature-my-updates"
INSTALL_DIR="/opt/Library-seat-reservation-system"

echo "==> 安装 git..."
apt update
apt install -y git docker-compose-plugin

echo "==> 确认 Docker..."
docker --version
docker compose version

if ss -tlnp | grep -q ':80 '; then
  echo ""
  echo "警告：80 端口已被占用。若当前运行的是其他项目，请先停止："
  echo "  docker ps"
  echo "  docker stop <容器名或ID>"
  echo "或修改 docker-compose.yml 中 nginx 的 ports 为 \"8080:80\"，然后访问 http://8.136.47.73:8080"
  echo ""
fi

echo "==> 克隆/更新项目..."
if [ -d "$INSTALL_DIR/.git" ]; then
  cd "$INSTALL_DIR"
  git fetch origin
  git checkout "$BRANCH"
  git pull origin "$BRANCH"
else
  git clone -b "$BRANCH" "$REPO_URL" "$INSTALL_DIR"
  cd "$INSTALL_DIR"
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo ""
  echo "!!! 请先编辑 .env 填入真实密码后再继续："
  echo "    nano $INSTALL_DIR/.env"
  echo ""
  echo "必填项：MYSQL_ROOT_PASSWORD, DB_PASSWORD, JWT_SECRET, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM"
  echo "FRONTEND_BASE_URL 保持 http://8.136.47.73"
  exit 1
fi

echo "==> 构建并启动容器（首次约 5~15 分钟）..."
chmod +x deploy/deploy.sh
./deploy/deploy.sh

echo ""
echo "==> 部署完成！请访问：http://8.136.47.73"
