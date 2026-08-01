# Apex 本地量化平台

A 股日频波段决策助手：自选 → 信号 → 回测 → 模拟盘 → 日终清单 → 人工成交复盘。

## 目录

- `apex-be`：Java / Spring Boot / MySQL 后端（包名 `com.awe.apex`）
- `apex-fe`：Vue 3 + Vite + Element Plus 前端

## 环境

- JDK 17+
- Maven 3.8+
- MySQL `localhost:3306`，库名 `apex`
- Node.js 18+

## 数据库

```bash
# 在 MySQL 中依次执行
apex-be/docs/sql/01_create_database.sql
apex-be/docs/sql/02_schema.sql
apex-be/docs/sql/03_p1_schema.sql
apex-be/docs/sql/04_p2_schema.sql
```

全 A 列表 + 历史日线导入（AKShare → MySQL，可断点续传）：见 `scripts/market_data/README.md`。

复制本地配置：

```bash
cp apex-be/src/main/resources/application-local.yml.example \
   apex-be/src/main/resources/application-local.yml
# 填写用户名密码
```

## 启动

```bash
# 后端
cd apex-be
mvn spring-boot:run
# http://127.0.0.1:8080/apex
# Swagger: http://127.0.0.1:8080/apex/swagger-ui.html

# 前端
cd apex-fe
npm install
npm run dev
# http://localhost:5173/
```

本地登录（可选）：用户 `admin` / 密码 `admin123`（可在 `application.yml` 的 `apex.local-*` 修改）。

## 推荐使用流

1. 自选页导入妙想 CSV（`.mx_output/`）
2. 同步日线
3. 信号页刷新股票池并运行 S1/S2/S3
4. 回测页验证策略（含成本；过去表现不代表未来收益）
5. 模拟盘练习下单（有仓位风控）
6. 日终清单一键生成，并录入真实成交到 journal
7. 看板查看资产/预警/近五日信号

## 免责声明

本系统仅供个人研究与模拟，不构成投资建议。过去表现不代表未来收益。

## 冒烟验证

```bash
bash apex/scripts/smoke.sh
```
