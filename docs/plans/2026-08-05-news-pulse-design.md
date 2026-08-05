# 今日消息面 + 市场资讯整合页：设计文档

## 确认记录（2026-08-05）
- 枢纽：`/news` 升级
- 摘要：Kimi `kimi-k2.6`（`https://api.moonshot.cn/v1`，Key 仅 `application-local.yml`）
- Tab：资讯 | 热点 | 行情摘要；`/market` `/hot` 保留深链

## 1. 问题陈述

行情、热点、资讯分在 `/market` `/hot` `/news`，缺少「今日消息面」总览；需要同屏读消息、热点与行情脉搏，并由大模型产出利好/利空方向摘要。

## 2. 落地（已实现）

- `GET /api/news/pulse`：聚合今日资讯 + 热点 + briefing，Kimi 摘要可降级为规则
- `/news`：顶部今日消息面 + Tab（资讯/热点/行情摘要）
- 配置：`apex.ai.*`（示例见 `application-local.yml.example`）

## 3. 成功标准

- `/news` 顶部可见今日消息面：统计 + 总述 + 卡片
- 同页可切资讯 / 热点 / 行情摘要
- `/market` `/hot` 仍可独立访问
- 无 API Key 时页面仍可用（规则摘要）
