# 后端本地配置说明

## 快速开始

1. 复制示例配置（仓库内为占位符，不含真实密钥）：

   ```bash
   cp src/main/resources/application-example.yml src/main/resources/application-local.yml
   ```

2. 编辑 `application-local.yml`，填入本机 MySQL、Redis、QQ 邮箱授权码、JWT 密钥等。

3. 启动时加载本地覆盖（**无需修改** `application.yml`）：

   - IDEA：Run Configuration → Program arguments 或 VM options 增加：

     ```
     --spring.config.additional-location=classpath:application-local.yml
     ```

   - 命令行：

     ```bash
     mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=classpath:application-local.yml"
     ```

`application-local.yml` 与 `application-prod.yml` 已在项目根 `.gitignore` 中忽略，不会提交到 Git。

## 环境变量（可选）

与 `application-example.yml` 中的占位符对应：

| 变量 | 说明 |
|------|------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MySQL 连接 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` | Redis |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | QQ 邮箱 SMTP |
| `JWT_SECRET` | JWT 签名密钥（HS256 建议 ≥ 32 字节） |

## 上传 GitHub 前的安全提醒

- 仓库中的 **`application-example.yml` 仅含占位符**，可安全公开。
- 若你曾在 Git 历史中提交过带真实密码的 **`application.yml`**，仅新增示例文件**无法**从历史中删除已泄露的密钥。
- 上传公开仓库前请：
  1. **轮换** QQ 邮箱授权码、数据库密码、JWT 密钥；
  2. 后续勿将含真实密码的 `application.yml` 推送到远程；
  3. 若需彻底清理历史，可使用 `git filter-repo` 等工具重写历史，或新建干净仓库只推送脱敏后的配置。

本地开发可继续使用你现有的 `application.yml`（不强制修改该文件）。
