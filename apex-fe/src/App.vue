<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { searchStock } from './api/stock'
import http from './api/http'

const router = useRouter()
const healthOk = ref(null)
const searchOpen = ref(false)
const query = ref('')
const loading = ref(false)
const results = ref([])
const inputRef = ref(null)
let healthTimer
let searchSeq = 0
let debounceTimer

async function pingHealth() {
  try {
    const res = await http.get('/api/health', { timeout: 4000 })
    healthOk.value = res?.data?.status === 'UP'
  } catch {
    healthOk.value = false
  }
}

function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function markHtml(text, keyword) {
  const src = String(text ?? '')
  const kw = String(keyword ?? '').trim()
  if (!src) return ''
  if (!kw) return escapeHtml(src)
  const lowerSrc = src.toLowerCase()
  const lowerKw = kw.toLowerCase()
  let out = ''
  let cursor = 0
  while (cursor < src.length) {
    const idx = lowerSrc.indexOf(lowerKw, cursor)
    if (idx < 0) {
      out += escapeHtml(src.slice(cursor))
      break
    }
    if (idx > cursor) out += escapeHtml(src.slice(cursor, idx))
    out += `<em>${escapeHtml(src.slice(idx, idx + kw.length))}</em>`
    cursor = idx + kw.length
  }
  return out
}

async function openSearch() {
  searchOpen.value = true
  await nextTick()
  inputRef.value?.focus?.()
}

function closeSearch() {
  searchOpen.value = false
  query.value = ''
  results.value = []
  loading.value = false
}

function onQueryInput() {
  clearTimeout(debounceTimer)
  const keyword = String(query.value || '').trim()
  if (!keyword) {
    results.value = []
    loading.value = false
    return
  }
  debounceTimer = setTimeout(() => runSearch(keyword), 200)
}

async function runSearch(keyword) {
  const seq = ++searchSeq
  loading.value = true
  try {
    const res = await searchStock(keyword, 10)
    if (seq !== searchSeq) return
    results.value = (res.data || []).map((item) => ({
      code: item.code,
      name: item.name || '',
      market: item.market || '',
      codeHtml: markHtml(item.code, keyword),
      nameHtml: markHtml(item.name || '', keyword),
    }))
  } catch {
    if (seq !== searchSeq) return
    results.value = []
  } finally {
    if (seq === searchSeq) loading.value = false
  }
}

function goStock(code) {
  if (!code) return
  router.push(`/stock/${code}`)
  closeSearch()
}

function onEnter() {
  const keyword = String(query.value || '').trim()
  const code = keyword.replace(/\D/g, '').slice(0, 6)
  if (results.value.length) {
    goStock(results.value[0].code)
    return
  }
  if (code.length === 6) goStock(code)
}

function onGlobalKeydown(e) {
  if (e.key === 'Escape' && searchOpen.value) {
    e.preventDefault()
    closeSearch()
  }
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    openSearch()
  }
}

onMounted(() => {
  pingHealth()
  healthTimer = setInterval(pingHealth, 30000)
  window.addEventListener('keydown', onGlobalKeydown)
})
onBeforeUnmount(() => {
  if (healthTimer) clearInterval(healthTimer)
  clearTimeout(debounceTimer)
  window.removeEventListener('keydown', onGlobalKeydown)
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
        {{ healthOk === false ? '离线' : healthOk ? '在线' : '…' }}
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
      <button type="button" class="search-btn" title="搜索股票 Ctrl+K" @click="openSearch">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.2-3.2" stroke-linecap="round" />
        </svg>
        <span>搜索</span>
        <kbd>⌘K</kbd>
      </button>
    </nav>

    <div v-if="searchOpen" class="search-layer" @click.self="closeSearch">
      <div class="search-panel" role="dialog" aria-label="搜索股票">
        <div class="search-head">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.2-3.2" stroke-linecap="round" />
          </svg>
          <input
            ref="inputRef"
            v-model="query"
            class="search-input"
            placeholder="输入代码或名称"
            autocomplete="off"
            @input="onQueryInput"
            @keydown.enter.prevent="onEnter"
            @keydown.esc.prevent="closeSearch"
          />
          <button type="button" class="search-close" @click="closeSearch">Esc</button>
        </div>
        <div class="search-body">
          <div v-if="loading" class="search-tip">搜索中…</div>
          <div v-else-if="!query.trim()" class="search-tip">支持代码、名称，回车打开第一条</div>
          <div v-else-if="!results.length" class="search-tip">无匹配结果</div>
          <ul v-else class="search-list">
            <li v-for="item in results" :key="item.code">
              <button type="button" class="search-item" @click="goStock(item.code)">
                <span class="code" v-html="item.codeHtml" />
                <span class="name" v-html="item.nameHtml" />
                <span v-if="item.market" class="market">{{ item.market }}</span>
              </button>
            </li>
          </ul>
        </div>
      </div>
    </div>

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
  gap: 10px;
  padding: 0 16px;
  height: 52px;
  background: #16261e;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.brand-block {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex: 0 0 auto;
}

.brand {
  color: #e8d4a8;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
}

.tagline {
  font-size: 11px;
  color: rgba(232, 212, 168, 0.4);
}

.health {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  font-size: 11px;
  color: #8a968f;
}

.health .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6a756e;
}

.health.up {
  color: #8fbf9f;
}

.health.up .dot {
  background: #5cb87a;
}

.health.down {
  color: #d98888;
}

.health.down .dot {
  background: #d07070;
}

.links {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  align-items: center;
  overflow-x: auto;
  scrollbar-width: none;
}

.links::-webkit-scrollbar {
  display: none;
}

.links a {
  flex: 0 0 auto;
  color: rgba(220, 231, 225, 0.72);
  text-decoration: none;
  font-size: 13px;
  padding: 6px 9px;
  border-radius: 6px;
  white-space: nowrap;
}

.links a:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}

.links a.router-link-active {
  color: #e8d4a8;
  background: rgba(232, 212, 168, 0.1);
}

.search-btn {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  background: transparent;
  color: rgba(238, 242, 240, 0.78);
  font-size: 12px;
  cursor: pointer;
}

.search-btn:hover {
  color: #fff;
  border-color: rgba(232, 212, 168, 0.35);
  background: rgba(255, 255, 255, 0.04);
}

.search-btn kbd {
  display: none;
  font-family: inherit;
  font-size: 10px;
  color: rgba(238, 242, 240, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 3px;
  padding: 0 4px;
}

.search-layer {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  justify-content: center;
  padding: 12vh 16px 0;
  background: rgba(12, 18, 15, 0.45);
  backdrop-filter: blur(2px);
}

.search-panel {
  width: min(420px, 100%);
  max-height: min(520px, 70vh);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(10, 18, 14, 0.28);
  overflow: hidden;
}

.search-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(20, 36, 28, 0.08);
  color: #6a7b72;
}

.search-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  font-size: 15px;
  color: #14241c;
  background: transparent;
}

.search-input::placeholder {
  color: #9aa8a1;
}

.search-close {
  border: 1px solid rgba(20, 36, 28, 0.12);
  background: #f4f7f5;
  color: #6a7b72;
  border-radius: 5px;
  font-size: 11px;
  padding: 2px 7px;
  cursor: pointer;
}

.search-body {
  overflow: auto;
  min-height: 120px;
}

.search-tip {
  padding: 28px 16px;
  text-align: center;
  color: #8a968f;
  font-size: 13px;
}

.search-list {
  list-style: none;
  margin: 0;
  padding: 6px;
}

.search-item {
  width: 100%;
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 10px 10px;
  text-align: left;
  cursor: pointer;
}

.search-item:hover {
  background: #f1f5f2;
}

.search-item .code {
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: #14241c;
  font-size: 13px;
}

.search-item .name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #5a6b62;
  font-size: 13px;
}

.search-item .market {
  font-size: 11px;
  color: #8a968f;
}

.search-item :deep(em) {
  font-style: normal;
  color: #c62828;
  font-weight: 700;
}

@media (min-width: 900px) {
  .search-btn kbd {
    display: inline;
  }
}

@media (max-width: 900px) {
  .tagline,
  .health {
    display: none;
  }

  .search-btn span {
    display: none;
  }

  .search-btn {
    width: 30px;
    padding: 0;
    justify-content: center;
  }
}
</style>
