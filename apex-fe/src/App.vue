<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { searchStock } from './api/stock'
import http from './api/http'
import GlossaryPanel from './components/GlossaryPanel.vue'
import { BRAND } from './brand/identity.js'

const router = useRouter()
const route = useRoute()
const healthOk = ref(null)
const searchOpen = ref(false)
const mobileMenuOpen = ref(false)
const query = ref('')
const loading = ref(false)
const results = ref([])
const inputRef = ref(null)
const glossaryRef = ref(null)
const mobileMenuRef = ref(null)
const mobileMenuButtonRef = ref(null)
const mobileMenuCloseRef = ref(null)
/** 终端紧凑密度：缩小表格与页边距 */
const denseMode = ref(localStorage.getItem('apex.ui.dense') === '1')
let healthTimer
let searchSeq = 0
let debounceTimer

function toggleDense() {
  denseMode.value = !denseMode.value
  localStorage.setItem('apex.ui.dense', denseMode.value ? '1' : '0')
}

function setMobileMenu(open) {
  mobileMenuOpen.value = open
}

function closeMobileMenuOnDesktop() {
  if (window.innerWidth > 900 && mobileMenuOpen.value) setMobileMenu(false)
}

function isNavGroupActive(group) {
  return group.items.some((item) => route.path === item.to || route.path.startsWith(`${item.to}/`))
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
  setMobileMenu(false)
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
  if (e.key === 'Escape' && mobileMenuOpen.value) {
    e.preventDefault()
    setMobileMenu(false)
    return
  }
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

watch(
  () => route.fullPath,
  () => setMobileMenu(false),
)

watch(mobileMenuOpen, async (open) => {
  document.documentElement.classList.toggle('mobile-menu-open', open)
  await nextTick()
  if (open) {
    const activeLink = mobileMenuRef.value?.querySelector?.('.router-link-active')
    activeLink?.scrollIntoView?.({ block: 'center' })
    if (activeLink) activeLink.focus()
    else mobileMenuCloseRef.value?.focus?.()
    return
  }
  mobileMenuButtonRef.value?.focus?.()
})

onMounted(() => {
  pingHealth()
  healthTimer = setInterval(pingHealth, 30000)
  window.addEventListener('keydown', onGlobalKeydown)
  window.addEventListener('resize', closeMobileMenuOnDesktop)
})
onBeforeUnmount(() => {
  if (healthTimer) clearInterval(healthTimer)
  clearTimeout(debounceTimer)
  window.removeEventListener('keydown', onGlobalKeydown)
  window.removeEventListener('resize', closeMobileMenuOnDesktop)
  document.documentElement.classList.remove('mobile-menu-open')
})
</script>

<template>
  <div class="shell" :class="{ dense: denseMode }">
    <nav class="nav" aria-label="主导航">
      <div class="brand-block" :title="BRAND.slogan">
        <img class="brand-logo" :src="BRAND.assets.mark" :alt="`${BRAND.nameZh} ${BRAND.nameEn}`" />
        <div class="brand-text">
          <strong class="brand">{{ BRAND.nameZh }}</strong>
          <span class="brand-en">{{ BRAND.nameEn }}</span>
        </div>
        <span class="tagline">{{ BRAND.taglineShort }}</span>
      </div>
      <div class="mobile-top-actions">
        <button type="button" class="nav-icon-btn" aria-label="搜索股票" title="搜索股票" @click="openSearch">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.2-3.2" stroke-linecap="round" />
          </svg>
        </button>
        <button
          ref="mobileMenuButtonRef"
          type="button"
          class="nav-icon-btn menu-toggle"
          aria-label="打开菜单"
          aria-controls="mobile-navigation"
          :aria-expanded="mobileMenuOpen"
          @click="setMobileMenu(true)"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 7h16M4 12h16M4 17h16" stroke-linecap="round" />
          </svg>
        </button>
      </div>
      <button
        v-if="mobileMenuOpen"
        type="button"
        class="nav-scrim"
        aria-label="关闭菜单"
        @click="setMobileMenu(false)"
      />
      <div
        id="mobile-navigation"
        ref="mobileMenuRef"
        class="links"
        :class="{ open: mobileMenuOpen }"
        aria-label="移动端功能菜单"
      >
        <div class="mobile-menu-head">
          <div>
            <strong>功能菜单</strong>
            <span>快速进入投研工作区</span>
          </div>
          <button
            ref="mobileMenuCloseRef"
            type="button"
            class="nav-icon-btn"
            aria-label="关闭菜单"
            @click="setMobileMenu(false)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" stroke-linecap="round" />
            </svg>
          </button>
        </div>
        <div
          v-for="(group, groupIndex) in navGroups"
          :key="group.label"
          class="nav-group"
          :class="{ 'has-active': isNavGroupActive(group) }"
        >
          <span class="group-label">
            <span class="group-number">{{ String(groupIndex + 1).padStart(2, '0') }}</span>
            <span class="group-name">{{ group.label }}</span>
            <span v-if="isNavGroupActive(group)" class="group-current">当前</span>
          </span>
          <div class="nav-group-links">
            <RouterLink v-for="item in group.items" :key="item.to" :to="item.to" @click="setMobileMenu(false)">
              {{ item.label }}
              <svg class="mobile-link-arrow" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </RouterLink>
          </div>
        </div>
        <div class="mobile-menu-actions">
          <span class="health" :class="healthOk === false ? 'down' : healthOk ? 'up' : ''">
            <i class="dot" />
            {{ healthOk === false ? '服务离线' : healthOk ? '服务在线' : '检测中…' }}
          </span>
          <button type="button" class="mobile-action-btn" :class="{ on: denseMode }" @click="toggleDense">
            <span>{{ denseMode ? '紧凑密度' : '舒适密度' }}</span>
          </button>
          <button type="button" class="mobile-action-btn" @click="openGlossary(); setMobileMenu(false)">名词百科</button>
        </div>
      </div>
      <div class="nav-actions desktop-nav-actions">
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
      </div>
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

    <main class="main" :inert="mobileMenuOpen" :aria-hidden="mobileMenuOpen ? 'true' : undefined">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.shell.dense .nav {
  min-height: 48px;
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
  gap: 10px;
  padding: 0 18px;
  min-height: 56px;
  height: auto;
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.04);
  user-select: none;
  -webkit-user-select: none;
}

.mobile-top-actions,
.mobile-menu-head,
.mobile-menu-actions,
.mobile-link-arrow,
.nav-scrim {
  display: none;
}

.nav-icon-btn {
  width: 44px;
  height: 44px;
  display: inline-grid;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: 10px;
  color: var(--ink-soft);
  background: transparent;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.nav-icon-btn svg,
.mobile-link-arrow {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  max-width: 42%;
  min-width: 0;
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
  min-width: 0;
}

.brand {
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
  line-height: 1;
  white-space: nowrap;
}

.brand-en {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--slate);
  text-transform: uppercase;
  font-family: var(--font-display), Arial, sans-serif;
  white-space: nowrap;
}

.tagline {
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  margin-left: auto;
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
  white-space: nowrap;
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
  overscroll-behavior-x: contain;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.22) transparent;
}

.links:hover,
.links:focus-within {
  scrollbar-color: rgba(0, 0, 0, 0.35) transparent;
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

.nav-group-links {
  display: contents;
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
  white-space: nowrap;
}

.group-number,
.group-current {
  display: none;
}

.links::-webkit-scrollbar {
  height: 4px;
}

.links::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.22);
  border-radius: 999px;
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
  -webkit-tap-highlight-color: transparent;
  transition: background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.links a:hover {
  color: var(--ink);
  background: rgba(0, 0, 0, 0.04);
}

.links a:focus {
  outline: none;
}

.links a:focus-visible {
  outline: none;
  box-shadow: inset 0 0 0 2px rgba(0, 113, 227, 0.34);
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
  white-space: nowrap;
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

@media (max-width: 1280px) {
  .tagline,
  .brand-en {
    display: none;
  }

  .brand-block {
    max-width: none;
  }
}

@media (max-width: 1100px) {
  .search-btn span {
    display: none;
  }

  .search-btn {
    width: 30px;
    padding: 0;
    justify-content: center;
  }
}

@media (max-width: 900px) {
  .nav,
  .shell.dense .nav {
    min-height: calc(56px + env(safe-area-inset-top));
    height: calc(56px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 10px 0 14px;
    gap: 8px;
    background: rgba(255, 255, 255, 0.96);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .brand-block {
    max-width: calc(100% - 104px);
  }

  .brand-logo {
    width: 30px;
    height: 30px;
  }

  .brand {
    font-size: 16px;
  }

  .mobile-top-actions {
    display: flex;
    align-items: center;
    margin-left: auto;
  }

  .desktop-nav-actions {
    display: none;
  }

  .nav-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 101;
    padding: 0;
    border: 0;
    background: rgba(15, 23, 42, 0.46);
    backdrop-filter: blur(3px);
    -webkit-backdrop-filter: blur(3px);
  }

  .links {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 102;
    width: min(86vw, 360px);
    max-width: 100%;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    padding: env(safe-area-inset-top) 12px calc(16px + env(safe-area-inset-bottom));
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    background: #f2f4f7;
    border-right: 1px solid rgba(0, 0, 0, 0.08);
    box-shadow: 18px 0 54px rgba(15, 23, 42, 0.16);
    transform: translateX(-104%);
    visibility: hidden;
    transition: transform 0.22s ease, visibility 0.22s step-end;
  }

  .links.open {
    transform: translateX(0);
    visibility: visible;
    transition: transform 0.22s ease, visibility 0s step-start;
  }

  .mobile-menu-head {
    display: flex;
    position: sticky;
    top: 0;
    z-index: 2;
    align-items: center;
    justify-content: space-between;
    min-height: 68px;
    margin: 0 -12px 2px;
    padding: 8px 14px 10px 20px;
    border-bottom: 1px solid rgba(15, 23, 42, 0.1);
    background: #fff;
  }

  .mobile-menu-head > div {
    display: grid;
    gap: 3px;
  }

  .mobile-menu-head strong {
    font-size: 18px;
    font-weight: 700;
  }

  .mobile-menu-head span {
    font-size: 12px;
    color: var(--muted);
  }

  .nav-group {
    display: block;
    padding: 0;
    margin: 0;
    overflow: hidden;
    border: 1px solid rgba(15, 23, 42, 0.1);
    border-radius: 10px;
    background: #fff;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  }

  .nav-group.has-active {
    border-color: rgba(0, 113, 227, 0.28);
    box-shadow: 0 0 0 1px rgba(0, 113, 227, 0.06);
  }

  .group-label {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 42px;
    padding: 0 12px;
    border-bottom: 1px solid rgba(15, 23, 42, 0.08);
    background: #f8fafc;
    color: #273142;
    font-size: 14px;
    font-weight: 700;
  }

  .nav-group.has-active .group-label {
    background: rgba(0, 113, 227, 0.08);
    color: #0058b0;
  }

  .group-number {
    display: inline-grid;
    place-items: center;
    width: 26px;
    height: 22px;
    border-radius: 6px;
    background: rgba(15, 23, 42, 0.06);
    color: var(--slate);
    font-size: 10px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  .nav-group.has-active .group-number {
    background: var(--accent);
    color: #fff;
  }

  .group-current {
    display: inline-flex;
    align-items: center;
    min-height: 22px;
    margin-left: auto;
    padding: 0 7px;
    border-radius: 999px;
    background: rgba(0, 113, 227, 0.12);
    color: #0058b0;
    font-size: 10px;
    font-weight: 650;
  }

  .nav-group-links {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 2px;
    padding: 6px;
  }

  .links a {
    min-height: 44px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 6px;
    padding: 0 9px;
    border-left: 3px solid transparent;
    border-radius: 6px;
    color: #424b5a;
    font-size: 13px;
    font-weight: 550;
  }

  .links a.router-link-active {
    border-left-color: var(--accent);
    background: rgba(0, 113, 227, 0.11);
    color: #0058b0;
    font-weight: 700;
  }

  .mobile-link-arrow {
    display: block;
    width: 16px;
    height: 16px;
    color: #8a93a1;
  }

  .links a.router-link-active .mobile-link-arrow {
    color: var(--accent);
  }

  .mobile-menu-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    padding: 14px 4px 0;
  }

  .mobile-menu-actions .health {
    grid-column: 1 / -1;
    justify-content: center;
    min-height: 36px;
  }

  .mobile-action-btn {
    min-height: 44px;
    border: 1px solid var(--line-strong);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.72);
    color: var(--ink-soft);
    font: inherit;
    font-size: 13px;
  }

  .mobile-action-btn.on {
    color: var(--accent);
    border-color: rgba(0, 113, 227, 0.28);
    background: var(--accent-soft);
  }

  .main {
    min-width: 0;
  }

  .search-close {
    min-width: 44px;
    min-height: 44px;
    padding: 0 8px;
  }

  .search-head {
    padding: 8px 10px;
  }

  .search-item {
    min-height: 44px;
  }
}

@media (max-width: 380px) {
  .brand-en,
  .tagline {
    display: none;
  }
}
</style>
