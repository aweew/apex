<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { searchStock } from './api/stock'
import http from './api/http'
import GlossaryPanel from './components/GlossaryPanel.vue'
import { BRAND } from './brand/identity.js'

const router = useRouter()
const healthOk = ref(null)
const searchOpen = ref(false)
const query = ref('')
const loading = ref(false)
const results = ref([])
const inputRef = ref(null)
const glossaryRef = ref(null)
/** 终端紧凑密度：缩小表格与页边距 */
const denseMode = ref(localStorage.getItem('apex.ui.dense') === '1')
let healthTimer
let searchSeq = 0
let debounceTimer

function toggleDense() {
  denseMode.value = !denseMode.value
  localStorage.setItem('apex.ui.dense', denseMode.value ? '1' : '0')
}

/** 对齐终端工作流：看板→研判→交易→市场→工具 */
const navGroups = [
  {
    label: '主线',
    items: [
      { to: '/dashboard', label: '看板' },
      { to: '/decision', label: '决策' },
      { to: '/observe', label: '观察池' },
      { to: '/holding', label: '持仓' },
      { to: '/portfolio', label: '组合' },
      { to: '/paper', label: '模拟盘' },
    ],
  },
  {
    label: '研究',
    items: [
      { to: '/signals', label: '信号' },
      { to: '/valuation', label: '估值' },
      { to: '/screener', label: '选股' },
      { to: '/pipeline', label: '流水线' },
      { to: '/backtest', label: '回测' },
    ],
  },
  {
    label: '市场',
    items: [
      { to: '/market', label: '行情' },
      { to: '/sector', label: '板块' },
      { to: '/limit-up', label: '涨停' },
      { to: '/hot', label: '热点' },
      { to: '/news', label: '消息/资讯' },
    ],
  },
  {
    label: '数据',
    items: [
      { to: '/watchlist', label: '自选' },
      { to: '/sync', label: '同步' },
      { to: '/daily', label: '日终' },
      { to: '/config', label: '参数' },
    ],
  },
]

function openGlossary(termId) {
  glossaryRef.value?.openGlossary?.(termId)
}

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
  loadRecentStocks()
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

const RECENT_KEY = 'apex.search.recent'
const recentStocks = ref([])

function loadRecentStocks() {
  try {
    const list = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]')
    recentStocks.value = Array.isArray(list) ? list.slice(0, 8) : []
  } catch {
    recentStocks.value = []
  }
}

function rememberStock(code, name = '') {
  const c = String(code || '').trim()
  if (!c) return
  const next = [{ code: c, name: name || '' }, ...recentStocks.value.filter((r) => r.code !== c)].slice(0, 8)
  recentStocks.value = next
  try {
    localStorage.setItem(RECENT_KEY, JSON.stringify(next))
  } catch {
    /* ignore */
  }
}

function goStock(code, name = '') {
  if (!code) return
  rememberStock(code, name)
  router.push(`/stock/${code}`)
  closeSearch()
}

function onEnter() {
  const keyword = String(query.value || '').trim()
  const code = keyword.replace(/\D/g, '').slice(0, 6)
  if (results.value.length) {
    goStock(results.value[0].code, results.value[0].name)
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
  // Ctrl+/ 打开名词百科
  if ((e.ctrlKey || e.metaKey) && (e.key === '/' || e.code === 'Slash')) {
    e.preventDefault()
    openGlossary()
  }
  // Ctrl+Shift+D 切换紧凑密度
  if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key.toLowerCase() === 'd') {
    e.preventDefault()
    toggleDense()
  }
  // Ctrl+1..4 主线快捷跳转（终端习惯）
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey) {
    const map = { '1': '/dashboard', '2': '/decision', '3': '/observe', '4': '/holding' }
    const path = map[e.key]
    if (path) {
      e.preventDefault()
      router.push(path)
    }
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
  <div class="shell" :class="{ dense: denseMode }">
    <nav class="nav">
      <div class="brand-block" :title="BRAND.slogan">
        <img class="brand-logo" :src="BRAND.assets.mark" :alt="`${BRAND.nameZh} ${BRAND.nameEn}`" />
        <div class="brand-text">
          <strong class="brand">{{ BRAND.nameZh }}</strong>
          <span class="brand-en">{{ BRAND.nameEn }}</span>
        </div>
        <span class="tagline">{{ BRAND.taglineShort }}</span>
      </div>
      <span class="health" :class="healthOk === false ? 'down' : healthOk ? 'up' : ''">
        <i class="dot" />
        {{ healthOk === false ? '离线' : healthOk ? '在线' : '…' }}
      </span>
      <button
        type="button"
        class="search-btn density-btn"
        :class="{ on: denseMode }"
        title="紧凑密度 Ctrl+Shift+D"
        @click="toggleDense"
      >
        <span>{{ denseMode ? '紧凑' : '舒适' }}</span>
      </button>
      <div class="links">
        <div v-for="group in navGroups" :key="group.label" class="nav-group">
          <span class="group-label">{{ group.label }}</span>
          <RouterLink v-for="item in group.items" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
        </div>
      </div>
      <button type="button" class="search-btn" title="名词百科 Ctrl+/" @click="openGlossary()">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="8" />
          <path d="M12 11v5" stroke-linecap="round" />
          <circle cx="12" cy="8" r="0.8" fill="currentColor" stroke="none" />
        </svg>
        <span>名词</span>
      </button>
      <button type="button" class="search-btn" title="搜索股票 Ctrl+K" @click="openSearch">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="7" />
          <path d="M20 20l-3.2-3.2" stroke-linecap="round" />
        </svg>
        <span>搜索</span>
      </button>
    </nav>

    <GlossaryPanel ref="glossaryRef" />

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
            placeholder="代码或名称"
            autocomplete="off"
            @input="onQueryInput"
            @keydown.enter.prevent="onEnter"
            @keydown.esc.prevent="closeSearch"
          />
          <button type="button" class="search-close" @click="closeSearch">esc</button>
        </div>
        <div class="search-body">
          <div v-if="loading" class="search-tip">搜索中…</div>
          <template v-else-if="!query.trim()">
            <div v-if="recentStocks.length" class="search-recent">
              <div class="search-tip">最近浏览</div>
              <ul class="search-list">
                <li v-for="item in recentStocks" :key="item.code">
                  <button type="button" class="search-item" @click="goStock(item.code, item.name)">
                    <span class="code">{{ item.code }}</span>
                    <span class="name">{{ item.name || '-' }}</span>
                  </button>
                </li>
              </ul>
            </div>
            <div v-else class="search-tip">
              输入后回车打开第一条结果
              <div class="search-keys">
                Ctrl+K 搜索 · Ctrl+/ 名词 · Ctrl+Shift+D 紧凑 · Ctrl+1~4 看板/决策/观察/持仓
              </div>
            </div>
          </template>
          <div v-else-if="!results.length" class="search-tip">无匹配</div>
          <ul v-else class="search-list">
            <li v-for="item in results" :key="item.code">
              <button type="button" class="search-item" @click="goStock(item.code, item.name)">
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

.shell.dense .nav {
  height: 48px;
}

.shell.dense .nav-group .group-label {
  font-size: 9px;
}

.nav {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  height: 56px;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
  user-select: none;
  -webkit-user-select: none;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.brand-logo {
  width: 28px;
  height: 28px;
  object-fit: contain;
  flex: 0 0 auto;
  display: block;
  padding: 2px;
  border-radius: 50%;
  background: #0c1015;
  box-sizing: border-box;
}

.brand-text {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.brand {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1;
}

.brand-en {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--slate);
  text-transform: uppercase;
  font-family: var(--font-display), Arial, sans-serif;
}

.tagline {
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 0.01em;
}

.health {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--slate);
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.health .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c7c7cc;
}

.health.up {
  color: #248a3d;
}

.health.up .dot {
  background: #34c759;
}

.health.down {
  color: #d70015;
}

.health.down .dot {
  background: #ff3b30;
}

.links {
  display: flex;
  flex: 1 1 auto;
  min-width: 0;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: none;
}

.nav-group {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 2px;
  padding-right: 6px;
  margin-right: 4px;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
}

.nav-group:last-child {
  border-right: 0;
  margin-right: 0;
  padding-right: 0;
}

.group-label {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 600;
  color: var(--muted);
  padding: 0 6px 0 4px;
  letter-spacing: 0.04em;
  user-select: none;
}

.links::-webkit-scrollbar {
  display: none;
}

.links a {
  flex: 0 0 auto;
  color: var(--slate);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 10px;
  border-radius: 980px;
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  -webkit-user-select: none;
  transition: background 0.15s ease, color 0.15s ease;
}

.links a:hover {
  color: var(--ink);
  background: rgba(0, 0, 0, 0.04);
}

.links a.router-link-active {
  color: var(--accent);
  background: var(--accent-soft);
}

.search-btn {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 980px;
  background: rgba(255, 255, 255, 0.55);
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.search-btn:hover {
  background: rgba(255, 255, 255, 0.85);
  color: var(--ink);
}

.density-btn.on {
  color: var(--accent);
  border-color: rgba(0, 113, 227, 0.28);
  background: rgba(0, 113, 227, 0.1);
}

.search-layer {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  justify-content: center;
  padding: 14vh 16px 0;
  background: rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(10px) saturate(140%);
  -webkit-backdrop-filter: blur(10px) saturate(140%);
}

.search-panel {
  width: min(440px, 100%);
  max-height: min(520px, 70vh);
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(28px) saturate(180%);
  -webkit-backdrop-filter: blur(28px) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 18px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.18);
  overflow: hidden;
}

.search-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  color: var(--muted);
}

.search-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  font-size: 16px;
  letter-spacing: -0.01em;
  color: var(--ink);
  background: transparent;
  font-family: inherit;
}

.search-input::placeholder {
  color: #aeaeb2;
}

.search-close {
  border: 0;
  background: rgba(0, 0, 0, 0.05);
  color: var(--slate);
  border-radius: 8px;
  font-size: 11px;
  padding: 4px 8px;
  cursor: pointer;
  text-transform: lowercase;
}

.search-body {
  overflow: auto;
  min-height: 120px;
}

.search-tip {
  padding: 32px 16px;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
}

.search-keys {
  margin-top: 10px;
  font-size: 11px;
  color: var(--slate);
  letter-spacing: 0.01em;
}

.search-list {
  list-style: none;
  margin: 0;
  padding: 8px;
}

.search-item {
  width: 100%;
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border: 0;
  background: transparent;
  border-radius: 12px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
}

.search-item:hover {
  background: rgba(0, 113, 227, 0.08);
}

.search-item .code {
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--ink);
  font-size: 13px;
}

.search-item .name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--slate);
  font-size: 13px;
}

.search-item .market {
  font-size: 11px;
  color: var(--muted);
}

.search-item :deep(em) {
  font-style: normal;
  color: #ff3b30;
  font-weight: 700;
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
