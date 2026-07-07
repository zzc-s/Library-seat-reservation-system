#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "请先复制并编辑 .env：cp .env.example .env"
  exit 1
fi

if [ ! -f deploy/application-prod.yml ]; then
  echo "缺少 deploy/application-prod.yml"
  exit 1
fi

echo "==> 构建并启动容器..."
docker compose up -d --build

echo "==> 等待后端就绪..."
for i in $(seq 1 30); do
  if docker compose exec -T backend curl -sf http://localhost:8081/actuator/health >/dev/null 2>&1; then
    echo "后端已就绪"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "后端启动超时，请查看日志：docker compose logs backend"
    exit 1
  fi
  sleep 5
done

docker compose ps
echo ""
echo "部署完成。请访问：${FRONTEND_BASE_URL:-http://8.136.47.73}"
