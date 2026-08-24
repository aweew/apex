# Apex Frontend

Vue 3 / Vite / Element Plus 前端。页面按业务模块拆分，数据请求集中于 `src/api`，路由位于 `src/router`，导航分组位于 `src/navigation/menu.js`。

## 目录

| 目录 | 职责 |
| --- | --- |
| `src/views` | 看板、决策、行情、同步、回测、组合等业务页面 |
| `src/components` | 可复用业务组件，含认证、资讯和分享组件 |
| `src/api` | 以业务域划分的接口调用与统一 API 地址 |
| `src/router`、`src/navigation` | 路由、登录拦截、滚动行为、侧边导航与命令中心 |
| `src/stores`、`src/utils` | 状态与纯业务工具函数 |
| `src/brand`、`src/glossary` | 品牌样式和术语定义 |

## 开发

```bash
npm install
npm run dev
```

开发服务监听 `0.0.0.0:5173`，默认将 `/apex` 代理到 `http://127.0.0.1:8080`。`VITE_API_BASE` 可覆盖接口根地址；本地默认值在 `.env.development` 中，生产镜像构建时使用同源 `/apex`。

```bash
npm test
npm run build
npm run preview
```

`npm test` 包含 API 地址、导航、交互状态和移动布局测试。构建通过仅说明静态资源可生成；登录后真实数据、权限及同步结果仍需在运行环境验证。

## 路由模块

- 工作台：`/dashboard`、`/decision`、`/ai-center`
- 个人资产：`/watchlist`、`/observe`、`/portfolio`、`/paper`、`/trades`
- 市场研究：`/market`、`/screener`、`/sector`、`/capital-flow`、`/limit-up`、`/news`、`/stock/:code`
- 策略与数据：`/signals`、`/backtest`、`/sync`、`/config`

产品模块和操作流程见仓库根目录的 [README](../README.md) 与 [操作指引](../docs/OPERATIONS.md)。
