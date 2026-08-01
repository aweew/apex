<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { searchStock } from './api/stock'
import http from './api/http'

const router = useRouter()
const query = ref('')
const healthOk = ref(null)
let healthTimer

async function pingHealth() {
  try {
    const res = await http.get('/api/health', { timeout: 4000 })
    healthOk.value = res?.data?.status === 'UP'
  } catch {
    healthOk.value = false
  }
}

async function querySearch(q, cb) {
  if (!q || !String(q).trim()) {
    cb([])
    return
  }
  try {
    const res = await searchStock(String(q).trim(), 12)
    cb(
      (res.data || []).map((item) => ({
        value: `${item.code} ${item.name || ''}`.trim(),
        code: item.code,
      })),
    )
  } catch {
    cb([])
  }
}

function onSelect(item) {
  if (item?.code) {
    router.push(`/stock/${item.code}`)
    query.value = ''
  }
}

function onEnter() {
  const code = String(query.value || '').replace(/\D/g, '').slice(0, 6)
  if (code.length === 6) {
    router.push(`/stock/${code}`)
    query.value = ''
  }
}

onMounted(() => {
  pingHealth()
  healthTimer = setInterval(pingHealth, 30000)
})
onBeforeUnmount(() => {
  if (healthTimer) clearInterval(healthTimer)
})
</script>

<template>
  <div class="shell">
    <nav class="nav">
      <div class="brand-block">
        <strong class="brand">Apex</strong>
        <span class="tagline">本地量化</span>
      </div>
      <span class="health" :class="healthOk === false ? 'down' : healthOk ? 'up' : ''">
        <i class="dot" />
        {{ healthOk === false ? '后端离线' : healthOk ? '后端在线' : '检测中' }}
      </span>
      <div class="links">
        <RouterLink to="/dashboard">看板</RouterLink>
        <RouterLink to="/pipeline">流水线</RouterLink>
        <RouterLink to="/screener">选股</RouterLink>
        <RouterLink to="/watchlist">自选</RouterLink>
        <RouterLink to="/stock">个股</RouterLink>
        <RouterLink to="/signals">信号</RouterLink>
        <RouterLink to="/backtest">回测</RouterLink>
        <RouterLink to="/paper">模拟盘</RouterLink>
        <RouterLink to="/daily">日终</RouterLink>
        <RouterLink to="/config">参数</RouterLink>
      </div>
      <el-autocomplete
        v-model="query"
        class="search"
        :fetch-suggestions="querySearch"
        placeholder="搜代码 / 名称"
        clearable
        @select="onSelect"
        @keyup.enter="onEnter"
      />
    </nav>
    <main class="main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.nav {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  min-height: 56px;
  flex-wrap: wrap;
  background:
    linear-gradient(105deg, rgba(20, 36, 28, 0.97) 0%, rgba(31, 51, 40, 0.94) 55%, rgba(36, 58, 48, 0.96) 100%);
  border-bottom: 1px solid rgba(212, 176, 106, 0.18);
  box-shadow: 0 8px 24px rgba(10, 18, 14, 0.22);
  backdrop-filter: blur(12px);
}

.brand-block {
  display: flex;
  flex-direction: column;
  line-height: 1.05;
  margin-right: 4px;
  padding: 8px 0;
}

.brand {
  color: var(--gold-soft);
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.tagline {
  font-size: 10px;
  color: rgba(232, 212, 168, 0.55);
  letter-spacing: 0.18em;
  text-transform: uppercase;
  margin-top: 2px;
}

.health {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #9aa8a1;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(0, 0, 0, 0.15);
}

.health .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7a887f;
}

.health.up {
  color: #9fd4b5;
  border-color: rgba(159, 212, 181, 0.35);
}

.health.up .dot {
  background: #6dcf9a;
  box-shadow: 0 0 0 3px rgba(109, 207, 154, 0.25);
  animation: pulse 2s ease infinite;
}

.health.down {
  color: #f0a0a0;
  border-color: rgba(240, 160, 160, 0.45);
}

.health.down .dot {
  background: #f08080;
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
}

.links a {
  position: relative;
  color: rgba(220, 231, 225, 0.78);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  padding: 8px 10px;
  border-radius: 8px;
  transition: color 0.18s ease, background 0.18s ease;
}

.links a:hover {
  color: #f5f8f6;
  background: rgba(255, 255, 255, 0.06);
}

.links a.router-link-active {
  color: var(--gold-soft);
  background: rgba(212, 176, 106, 0.12);
}

.links a.router-link-active::after {
  content: "";
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 4px;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, transparent, var(--gold), transparent);
}

.search {
  margin-left: auto;
  width: 220px;
}

.search :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12) inset;
  border-radius: 10px;
}

.search :deep(.el-input__inner) {
  color: #f5f7f6;
}

.search :deep(.el-input__inner::placeholder) {
  color: rgba(245, 247, 246, 0.45);
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgba(109, 207, 154, 0.2);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(109, 207, 154, 0.08);
  }
}

@media (max-width: 900px) {
  .search {
    width: 100%;
    margin-left: 0;
    order: 10;
    margin-bottom: 10px;
  }

  .tagline {
    display: none;
  }
}
</style>
