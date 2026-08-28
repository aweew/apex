<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Reading, Search } from '@element-plus/icons-vue'
import { getTradingCalendar } from './api/market'
import { searchStock } from './api/stock'
import http from './api/http'
import BackToTopButton from './components/BackToTopButton.vue'
import GlossaryPanel from './components/GlossaryPanel.vue'
import { BRAND } from './brand/identity.js'
import { isNavigating, requestCount } from './utils/appActivity'
import { MAIN_NAV_GROUPS, PRIMARY_SHORTCUTS } from './navigation/menu.js'
import { getCurrentUser, logout } from './api/auth'
import {
  chinaMarketDate,
  clearDataFreshness,
  dataFreshness,
  setTradingCalendar,
  tradingCalendar,
} from './utils/dataFreshness.js'
import {
  menuContentOffset,
  menuSwipeProgress,
  resolveMenuSwipeAxis,
  shouldOpenMenuAfterSwipe,
} from './utils/mobileMenuSwipe.js'

const router = useRouter()
const route = useRoute()
const healthOk = ref(null)
const searchOpen = ref(false)
const mobileMenuOpen = ref(false)
const mobileMenuProgress = ref(0)
const mobileMenuDragging = ref(false)
const isMobileViewport = ref(window.innerWidth <= 900)
const mobileViewportWidth = ref(window.innerWidth)
const query = ref('')
const loading = ref(false)
const results = ref([])
const commandSelection = ref(0)
const inputRef = ref(null)
const glossaryRef = ref(null)
const shellRef = ref(null)
const mobileMenuRef = ref(null)
const mobileMenuButtonRef = ref(null)
const mobileMenuCloseRef = ref(null)
const appActivityVisible = ref(false)
const appActivityFinishing = ref(false)
const mobileModuleTitle = ref('')
const mobileBackPath = ref('')
const mobileBackLabel = ref('')
const currentUser = ref(getCurrentUser())
const isPublicRoute = computed(() => Boolean(route.meta.public))
const mobileMenuActive = computed(() => (
  mobileMenuOpen.value || mobileMenuDragging.value || mobileMenuProgress.value > 0
))
const mobileMenuDrawerStyle = computed(() => (isMobileViewport.value
  ? { transform: `translate3d(${(mobileMenuProgress.value - 1) * 100}%, 0, 0)` }
  : undefined))
const mobileMenuShellStyle = computed(() => (isMobileViewport.value
  ? { '--mobile-menu-page-offset': `${menuContentOffset(mobileMenuProgress.value, mobileViewportWidth.value)}px` }
  : undefined))
const COMMAND_ROUTE_ITEMS = [
  { to: '/dashboard', label: '看板', detail: '市场立场与今日指挥', keywords: '首页 工作台' },
  { to: '/pre-market-report', label: '盘前研报', detail: '盘前市场、主线与组合风险研判', keywords: '晨报 研报 市场判断' },
  { to: '/decision', label: '智能决策', detail: '买卖清单与执行条件', keywords: '决策 买入 卖出' },
  { to: '/watchlist', label: '自选', detail: '维护关注标的', keywords: '股票 分组' },
  { to: '/observe', label: '观察池', detail: '跟踪触发条件', keywords: '提醒 观察' },
  { to: '/portfolio', label: '组合', detail: '组合与模拟交易', keywords: '持仓 账户' },
  { to: '/market', label: '行情', detail: '指数、资金流与板块轮动', keywords: '指数 大盘 资金 北向 主力 龙虎榜 板块 行业 概念 题材' },
  { to: '/screener', label: '股票筛选', detail: '筛选全市场标的', keywords: '选股 条件' },
  { to: '/news', label: '财经资讯', detail: '市场新闻与脉搏', keywords: '新闻 消息' },
  { to: '/sync', label: '同步中心', detail: '更新行情与决策数据', keywords: '刷新 数据 任务' },
  { to: '/backtest', label: '回测', detail: '验证策略表现', keywords: '策略 历史' },
  { to: '/config', label: '参数设置', detail: '管理系统参数', keywords: '设置 配置' },
]
const filteredRouteCommands = computed(() => {
  const keyword = String(query.value || '').trim().toLowerCase()
  if (!keyword) return COMMAND_ROUTE_ITEMS.slice(0, 6).map((item) => ({ ...item, kind: 'route' }))
  return COMMAND_ROUTE_ITEMS
    .filter((item) => `${item.label} ${item.detail} ${item.keywords}`.toLowerCase().includes(keyword))
    .map((item) => ({ ...item, kind: 'route' }))
})
const stockCommandResults = computed(() => {
  const keyword = String(query.value || '').trim()
  const source = keyword ? results.value : recentStocks.value
  return source.map((item) => ({ ...item, kind: 'stock' }))
})
const commandResults = computed(() => [...stockCommandResults.value, ...filteredRouteCommands.value])
const commandResultStatus = computed(() => {
  if (loading.value) return '正在搜索股票'
  if (!commandResults.value.length) return '没有找到匹配页面或股票'
  return `找到 ${commandResults.value.length} 项结果，使用上下方向键选择，按 Enter 打开`
})
let healthTimer
let searchSeq = 0
let debounceTimer
let searchReturnFocus
let activityShowTimer
let activityHideTimer
let moduleTitleFrame
let mobileMenuSwipe

function syncMobileModuleTitle() {
  if (window.innerWidth > 900) {
    mobileModuleTitle.value = ''
    return
  }
  const detailHeading = document.querySelector('.page.mobile-detail-open .detail-title h2')
  const heading = document.querySelector('.page .header h1')
  const module = document.querySelector('.page .header .eyebrow')
  const navigation = document.querySelector('.nav')
  if (!navigation) {
    mobileModuleTitle.value = ''
    return
  }
  if (detailHeading) {
    mobileModuleTitle.value = detailHeading.getBoundingClientRect().bottom <= navigation.getBoundingClientRect().bottom
      ? detailHeading.textContent.trim()
      : ''
    return
  }
  if (!heading || !module) {
    mobileModuleTitle.value = ''
    return
  }
  mobileModuleTitle.value = heading.getBoundingClientRect().bottom <= navigation.getBoundingClientRect().bottom
    ? heading.textContent.trim()
    : ''
}

function scheduleMobileModuleTitle() {
  if (moduleTitleFrame) return
  moduleTitleFrame = window.requestAnimationFrame(() => {
    moduleTitleFrame = undefined
    syncMobileModuleTitle()
  })
}

function resolveRouteLabel(pathname) {
  const routeLabels = [
    ['/dashboard', '看板'],
    ['/pre-market-report', '盘前研报'],
    ['/decision', '智能决策'],
    ['/ai-center', '小灵'],
    ['/watchlist', '自选'],
    ['/observe', '观察池'],
    ['/portfolio', '组合'],
    ['/paper', '模拟盘'],
    ['/market', '行情中心'],
    ['/screener', '股票筛选'],
    ['/factors', '因子中心'],
    ['/limit-up', '连板天梯'],
    ['/news', '财经资讯'],
    ['/backtest', '回测'],
    ['/sync', '同步中心'],
    ['/config', '参数设置'],
    ['/stock', '股票详情'],
  ]
  const hit = routeLabels.find(([path]) => pathname === path || pathname.startsWith(`${path}/`))
  return hit?.[1] || '上一页'
}

function syncMobileBackTarget() {
  if (window.innerWidth > 900 || route.path === '/dashboard') {
    mobileBackPath.value = ''
    mobileBackLabel.value = ''
    return
  }
  const historyBack = window.history.state?.back
  if (typeof historyBack !== 'string' || !historyBack) {
    mobileBackPath.value = ''
    mobileBackLabel.value = ''
    return
  }
  try {
    const target = new URL(historyBack, window.location.origin)
    if (target.origin !== window.location.origin || target.pathname === route.path) {
      mobileBackPath.value = ''
      mobileBackLabel.value = ''
      return
    }
    mobileBackPath.value = `${target.pathname}${target.search}${target.hash}`
    mobileBackLabel.value = `返回${resolveRouteLabel(target.pathname)}`
  } catch {
    mobileBackPath.value = ''
    mobileBackLabel.value = ''
  }
}

function goMobileBack() {
  if (!mobileBackPath.value) return
  router.back()
}

async function logoutCurrentUser() {
  setMobileMenu(false)
  await logout()
  currentUser.value = null
  await router.replace('/login')
}

function setMobileMenu(open) {
  mobileMenuDragging.value = false
  mobileMenuOpen.value = open
  mobileMenuProgress.value = open ? 1 : 0
  mobileMenuSwipe = undefined
}

function isMobileMenuSwipeTarget(target) {
  if (!(target instanceof Element)) return true
  if (target.closest([
    'input',
    'textarea',
    'select',
    '[contenteditable="true"]',
    '[data-mobile-swipe-ignore]',
    'canvas',
    '.el-slider',
    '.el-carousel',
    '.el-date-editor',
    '.el-select',
  ].join(','))) return false

  let currentElement = target
  while (currentElement && currentElement !== shellRef.value) {
    const style = window.getComputedStyle(currentElement)
    const scrollsHorizontally = /(auto|scroll)/.test(style.overflowX)
      && currentElement.scrollWidth > currentElement.clientWidth + 2
    if (scrollsHorizontally) return false
    currentElement = currentElement.parentElement
  }
  return true
}

function onMobileMenuTouchStart(event) {
  if (
    window.innerWidth > 900
    || isPublicRoute.value
    || searchOpen.value
    || event.touches.length !== 1
    || !isMobileMenuSwipeTarget(event.target)
  ) {
    mobileMenuSwipe = undefined
    return
  }

  const touch = event.touches[0]
  mobileMenuSwipe = {
    startX: touch.clientX,
    startY: touch.clientY,
    lastX: touch.clientX,
    lastTime: event.timeStamp,
    velocity: 0,
    startProgress: mobileMenuOpen.value ? 1 : 0,
    axis: 'pending',
  }
}

function onMobileMenuTouchMove(event) {
  if (!mobileMenuSwipe || event.touches.length !== 1) return
  const touch = event.touches[0]
  const deltaX = touch.clientX - mobileMenuSwipe.startX
  const deltaY = touch.clientY - mobileMenuSwipe.startY

  if (mobileMenuSwipe.axis === 'pending') {
    mobileMenuSwipe.axis = resolveMenuSwipeAxis(deltaX, deltaY)
    if (mobileMenuSwipe.axis === 'pending') return
    const openingWrongWay = mobileMenuSwipe.startProgress === 0 && deltaX < 0
    const closingWrongWay = mobileMenuSwipe.startProgress === 1 && deltaX > 0
    if (mobileMenuSwipe.axis === 'vertical' || openingWrongWay || closingWrongWay) {
      mobileMenuSwipe.axis = 'ignored'
      return
    }
    mobileMenuDragging.value = true
  }

  if (mobileMenuSwipe.axis !== 'horizontal') return
  event.preventDefault()

  const drawerWidth = mobileMenuRef.value?.getBoundingClientRect?.().width || window.innerWidth
  mobileMenuProgress.value = menuSwipeProgress(mobileMenuSwipe.startProgress, deltaX, drawerWidth)
  const elapsed = event.timeStamp - mobileMenuSwipe.lastTime
  if (elapsed > 0) mobileMenuSwipe.velocity = (touch.clientX - mobileMenuSwipe.lastX) / elapsed
  mobileMenuSwipe.lastX = touch.clientX
  mobileMenuSwipe.lastTime = event.timeStamp
}

function finishMobileMenuSwipe(event) {
  if (!mobileMenuSwipe || mobileMenuSwipe.axis !== 'horizontal') {
    mobileMenuSwipe = undefined
    return
  }
  const releaseVelocity = event.timeStamp - mobileMenuSwipe.lastTime > 100 ? 0 : mobileMenuSwipe.velocity
  const shouldOpen = shouldOpenMenuAfterSwipe(mobileMenuProgress.value, releaseVelocity)
  setMobileMenu(shouldOpen)
}

function closeMobileMenuOnDesktop() {
  mobileViewportWidth.value = window.innerWidth
  isMobileViewport.value = window.innerWidth <= 900
  if (!isMobileViewport.value && mobileMenuActive.value) setMobileMenu(false)
  syncMobileBackTarget()
}

function isNavGroupActive(group) {
  return group.items.some((item) => {
    const activePaths = item.activePaths || [item.to]
    return activePaths.some((path) => route.path === path || route.path.startsWith(`${path}/`))
  })
}

const navGroups = MAIN_NAV_GROUPS

function openGlossary(termId) {
  glossaryRef.value?.openGlossary?.(termId)
}

function dataFreshnessClass() {
  return `is-${String(dataFreshness.value?.level || '').toLowerCase()}`
}

async function pingHealth() {
  try {
    const res = await http.get('/api/health', { timeout: 4000, activity: false })
    healthOk.value = res?.data?.status === 'UP'
  } catch {
    healthOk.value = false
  }
}

async function syncTradingCalendar() {
  if (isPublicRoute.value || tradingCalendar.value?.date === chinaMarketDate()) return
  try {
    const response = await getTradingCalendar(undefined, 2)
    setTradingCalendar(response.data)
  } catch {
    setTradingCalendar(null)
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
  searchReturnFocus = document.activeElement
  searchOpen.value = true
  commandSelection.value = 0
  loadRecentStocks()
  await nextTick()
  inputRef.value?.focus?.()
}

function closeSearch(restoreFocus = true) {
  clearTimeout(debounceTimer)
  searchSeq += 1
  searchOpen.value = false
  query.value = ''
  results.value = []
  commandSelection.value = 0
  loading.value = false
  if (restoreFocus) nextTick(() => searchReturnFocus?.focus?.())
}

function onQueryInput() {
  clearTimeout(debounceTimer)
  commandSelection.value = 0
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
  closeSearch(false)
}

function goCommand(item) {
  if (!item) return
  if (item.kind === 'stock') {
    goStock(item.code, item.name)
    return
  }
  router.push(item.to)
  closeSearch(false)
}

function moveCommandSelection(step) {
  const count = commandResults.value.length
  if (!count) return
  commandSelection.value = (commandSelection.value + step + count) % count
}

function runSelectedCommand() {
  const item = commandResults.value[commandSelection.value]
  if (item) {
    goCommand(item)
    return
  }
  const keyword = String(query.value || '').trim()
  const code = keyword.replace(/\D/g, '').slice(0, 6)
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
  // Ctrl+1..4 主线快捷跳转（终端习惯）
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey) {
    const path = PRIMARY_SHORTCUTS[e.key]
    if (path) {
      e.preventDefault()
      router.push(path)
    }
  }
}

watch(
  () => route.fullPath,
  () => {
    setMobileMenu(false)
    clearDataFreshness()
    void syncTradingCalendar()
    nextTick(() => {
      syncMobileModuleTitle()
      syncMobileBackTarget()
    })
  },
)

watch(commandResults, (items) => {
  if (!items.length) {
    commandSelection.value = 0
    return
  }
  if (commandSelection.value >= items.length) commandSelection.value = items.length - 1
})

watch(
  [isNavigating, requestCount],
  ([navigating, requests]) => {
    const busy = navigating || requests > 0
    clearTimeout(activityShowTimer)

    if (busy) {
      clearTimeout(activityHideTimer)
      appActivityFinishing.value = false
      if (appActivityVisible.value) return

      const show = () => {
        appActivityVisible.value = true
      }
      if (navigating) show()
      else activityShowTimer = setTimeout(show, 240)
      return
    }

    if (!appActivityVisible.value) return
    appActivityFinishing.value = true
    activityHideTimer = setTimeout(() => {
      appActivityVisible.value = false
      appActivityFinishing.value = false
    }, 180)
  },
  { immediate: true, flush: 'sync' },
)

watch(
  mobileMenuOpen,
  async (open) => {
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
  },
  { immediate: true },
)

watch(
  searchOpen,
  (open) => {
    document.documentElement.classList.toggle('search-open', open)
  },
  { immediate: true },
)

onMounted(() => {
  pingHealth()
  void syncTradingCalendar()
  healthTimer = setInterval(pingHealth, 30000)
  window.addEventListener('keydown', onGlobalKeydown)
  window.addEventListener('resize', closeMobileMenuOnDesktop)
  window.addEventListener('resize', scheduleMobileModuleTitle)
  window.addEventListener('scroll', scheduleMobileModuleTitle, { passive: true })
  nextTick(() => {
    syncMobileModuleTitle()
    syncMobileBackTarget()
  })
})
onBeforeUnmount(() => {
  if (healthTimer) clearInterval(healthTimer)
  clearTimeout(debounceTimer)
  clearTimeout(activityShowTimer)
  clearTimeout(activityHideTimer)
  if (moduleTitleFrame) window.cancelAnimationFrame(moduleTitleFrame)
  window.removeEventListener('keydown', onGlobalKeydown)
  window.removeEventListener('resize', closeMobileMenuOnDesktop)
  window.removeEventListener('resize', scheduleMobileModuleTitle)
  window.removeEventListener('scroll', scheduleMobileModuleTitle)
  document.documentElement.classList.remove('mobile-menu-open')
  document.documentElement.classList.remove('search-open')
})
</script>

<template>
  <div
    ref="shellRef"
    class="shell"
    :class="{ 'mobile-menu-dragging': mobileMenuDragging }"
    :style="mobileMenuShellStyle"
    @touchstart="onMobileMenuTouchStart"
    @touchmove="onMobileMenuTouchMove"
    @touchend="finishMobileMenuSwipe"
    @touchcancel="finishMobileMenuSwipe"
  >
    <nav
      v-if="!isPublicRoute"
      class="nav"
      :class="{ 'nav--opaque': route.path.startsWith('/portfolio'), 'nav--has-back': mobileBackLabel }"
      aria-label="主导航"
      :inert="searchOpen"
      :aria-hidden="searchOpen ? 'true' : undefined"
    >
      <RouterLink
        class="brand-block"
        to="/dashboard"
        replace
        aria-label="返回首页"
        :title="`${BRAND.slogan} · 返回首页`"
        @click="setMobileMenu(false)"
      >
        <img class="brand-logo" :src="BRAND.assets.mark" :alt="`${BRAND.nameZh} ${BRAND.nameEn}`" />
        <div class="brand-text">
          <strong class="brand">{{ BRAND.nameZh }}</strong>
          <span class="brand-en">{{ BRAND.nameEn }}</span>
        </div>
        <span class="tagline">{{ BRAND.taglineShort }}</span>
      </RouterLink>
      <Transition name="mobile-module-title">
        <span v-if="mobileModuleTitle" class="mobile-module-title">{{ mobileModuleTitle }}</span>
      </Transition>
      <div class="mobile-top-actions">
        <button
          v-if="mobileBackLabel"
          type="button"
          class="nav-return-btn"
          :aria-label="mobileBackLabel"
          :title="mobileBackLabel"
          @click="goMobileBack"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m14.5 5-7 7 7 7" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>{{ mobileBackLabel }}</span>
        </button>
        <button type="button" class="nav-icon-btn" aria-label="个股搜索" title="个股搜索" @click="openSearch">
          <Search aria-hidden="true" />
        </button>
        <button type="button" class="nav-icon-btn" aria-label="名词百科" title="名词百科" @click="openGlossary()">
          <Reading aria-hidden="true" />
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
        type="button"
        class="nav-scrim"
        :class="{ visible: mobileMenuActive }"
        :disabled="!mobileMenuActive"
        :aria-hidden="mobileMenuActive ? undefined : 'true'"
        aria-label="关闭菜单"
        @click="setMobileMenu(false)"
      />
      <div
        id="mobile-navigation"
        ref="mobileMenuRef"
        class="links"
        :class="{ open: mobileMenuOpen, visible: mobileMenuActive, dragging: mobileMenuDragging }"
        :style="mobileMenuDrawerStyle"
        :inert="isMobileViewport && !mobileMenuActive"
        :aria-hidden="isMobileViewport && !mobileMenuActive ? 'true' : undefined"
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
        <div class="mobile-menu-scroll">
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
              <RouterLink
                v-for="item in group.items"
                :key="item.to"
                :to="item.to"
                :class="{ 'router-link-active': item.activePaths?.includes(route.path) }"
                @click="setMobileMenu(false)"
              >
                {{ item.label }}
                <svg class="mobile-link-arrow" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </RouterLink>
            </div>
          </div>
        </div>
        <div class="mobile-menu-actions">
          <RouterLink
            v-if="dataFreshness"
            class="data-status"
            :class="dataFreshnessClass()"
            :to="dataFreshness.route"
            :title="dataFreshness.detail || dataFreshness.label"
          >
            <i class="dot" />
            {{ dataFreshness.label }}
          </RouterLink>
          <span v-else class="health" :class="healthOk === false ? 'down' : healthOk ? 'up' : ''">
            <i class="dot" />
            {{ healthOk === false ? '服务离线' : healthOk ? '服务在线' : '检测中…' }}
          </span>
          <button v-if="currentUser" type="button" class="mobile-action-btn mobile-logout-btn" @click="logoutCurrentUser">退出登录</button>
        </div>
      </div>
      <div class="nav-actions desktop-nav-actions">
        <RouterLink
          v-if="dataFreshness"
          class="data-status"
          :class="dataFreshnessClass()"
          :to="dataFreshness.route"
          :title="dataFreshness.detail || dataFreshness.label"
        >
          <i class="dot" />
          <span>{{ dataFreshness.label }}</span>
        </RouterLink>
        <span v-else class="health" :class="healthOk === false ? 'down' : healthOk ? 'up' : ''">
          <i class="dot" />
          {{ healthOk === false ? '离线' : healthOk ? '在线' : '…' }}
        </span>
        <button
          type="button"
          class="search-btn stock-search-trigger"
          aria-label="个股搜索"
          title="个股搜索 Ctrl+K"
          @click="openSearch"
        >
          <Search aria-hidden="true" />
          <span>个股搜索</span>
        </button>
        <button type="button" class="search-btn" title="名词百科 Ctrl+/" @click="openGlossary()">
          <Reading aria-hidden="true" />
          <span>百科</span>
        </button>
        <el-dropdown v-if="currentUser" trigger="click">
          <button type="button" class="search-btn user-menu" :title="currentUser.phone">
            <span>{{ currentUser.nickName || currentUser.phone }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>{{ currentUser.phone }}</el-dropdown-item>
              <el-dropdown-item divided @click="logoutCurrentUser">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div
        class="app-activity"
        :class="{ visible: appActivityVisible, finishing: appActivityFinishing }"
        aria-hidden="true"
      >
        <span />
      </div>
    </nav>

    <span v-if="!isPublicRoute" class="sr-only" role="status" aria-live="polite">
      {{ appActivityVisible ? '正在更新数据' : '' }}
    </span>

    <GlossaryPanel v-if="!isPublicRoute" ref="glossaryRef" />

    <div
      v-if="!isPublicRoute && searchOpen"
      class="search-layer"
      @click.self="closeSearch"
    >
      <div class="search-panel" role="dialog" aria-label="快捷入口" aria-modal="true">
        <div class="search-head">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.2-3.2" stroke-linecap="round" />
          </svg>
          <input
            ref="inputRef"
            v-model="query"
            role="combobox"
            class="search-input"
            placeholder="输入代码、名称或拼音缩写"
            autocomplete="off"
            autocapitalize="none"
            enterkeyhint="search"
            inputmode="search"
            :spellcheck="false"
            aria-controls="command-results"
            aria-describedby="command-result-status"
            :aria-expanded="searchOpen"
            aria-autocomplete="list"
            :aria-activedescendant="commandResults[commandSelection] ? `command-result-${commandSelection}` : undefined"
            @input="onQueryInput"
            @keydown.down.prevent="moveCommandSelection(1)"
            @keydown.up.prevent="moveCommandSelection(-1)"
            @keydown.enter.prevent="runSelectedCommand"
            @keydown.esc.prevent="closeSearch"
          />
          <button type="button" class="search-close" aria-label="关闭搜索" @click="closeSearch">
            <span class="desktop-label">esc</span>
            <span class="mobile-label">取消</span>
          </button>
        </div>
        <p id="command-result-status" class="sr-only" role="status" aria-live="polite" aria-atomic="true">
          {{ commandResultStatus }}
        </p>
        <div id="command-results" class="search-body">
          <div v-if="loading" class="search-tip" role="status">正在搜索股票…</div>
          <div v-else-if="!commandResults.length" class="search-tip">没有找到匹配结果，请尝试代码、名称、拼音缩写或功能名称</div>
          <template v-else>
            <section v-if="stockCommandResults.length" class="command-section" aria-labelledby="command-stock-title">
              <h2 id="command-stock-title" class="command-section-title">{{ query.trim() ? '股票' : '最近股票' }}</h2>
              <ul class="search-list command-list" role="listbox" aria-label="股票">
                <li v-for="(item, index) in stockCommandResults" :key="item.code">
                  <button
                    :id="`command-result-${index}`"
                    type="button"
                    class="search-item"
                    :class="{ selected: index === commandSelection }"
                    role="option"
                    :aria-selected="index === commandSelection"
                    @mouseenter="commandSelection = index"
                    @click="goCommand(item)"
                  >
                    <StockIdentity :security="item" include-main>
                      <template v-if="query.trim()" #name><span v-html="item.nameHtml" /></template>
                      <template v-if="query.trim()" #code><span v-html="item.codeHtml" /></template>
                    </StockIdentity>
                  </button>
                </li>
              </ul>
            </section>
            <section v-if="filteredRouteCommands.length" class="command-section" aria-labelledby="command-route-title">
              <h2 id="command-route-title" class="command-section-title">页面与操作</h2>
              <ul class="search-list command-list" role="listbox" aria-label="页面与操作">
                <li v-for="(item, index) in filteredRouteCommands" :key="item.to">
                  <button
                    :id="`command-result-${stockCommandResults.length + index}`"
                    type="button"
                    class="search-item command-item"
                    :class="{ selected: stockCommandResults.length + index === commandSelection }"
                    role="option"
                    :aria-selected="stockCommandResults.length + index === commandSelection"
                    @mouseenter="commandSelection = stockCommandResults.length + index"
                    @click="goCommand(item)"
                  >
                    <span class="command-item-copy">
                      <b>{{ item.label }}</b>
                      <small>{{ item.detail }}</small>
                    </span>
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                  </button>
                </li>
              </ul>
            </section>
          </template>
        </div>
        <footer class="search-keys" aria-label="快捷键提示">上下键选择 · Enter 打开 · Ctrl+/ 名词百科</footer>
      </div>
    </div>

    <main
      class="main"
      :inert="mobileMenuOpen || searchOpen"
      :aria-hidden="mobileMenuOpen || searchOpen ? 'true' : undefined"
      :aria-busy="appActivityVisible ? 'true' : 'false'"
    >
      <RouterView />
      <BackToTopButton />
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
  padding: 0 18px;
  min-height: 56px;
  height: auto;
  background: #ffffff;
  border-bottom: 1px solid var(--glass-border);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  user-select: none;
  -webkit-user-select: none;
}

.nav.nav--opaque {
  background: #fff;
}

.mobile-module-title {
  display: none;
}

.mobile-module-title-enter-active,
.mobile-module-title-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.mobile-module-title-enter-from,
.mobile-module-title-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

.app-activity {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}

.app-activity span {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 0 2px 2px 0;
  background: var(--accent);
  box-shadow: 0 0 8px rgba(0, 113, 227, 0.28);
  transform: scaleX(0.08);
  transform-origin: left center;
}

.app-activity.visible {
  opacity: 1;
}

.app-activity.visible span {
  animation: appActivityProgress 8s cubic-bezier(0.1, 0.65, 0.25, 1) forwards;
}

.app-activity.finishing span {
  animation: none;
  transform: scaleX(1);
  transition: transform 0.16s ease-out;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@keyframes appActivityProgress {
  0% {
    transform: scaleX(0.08);
  }
  18% {
    transform: scaleX(0.34);
  }
  55% {
    transform: scaleX(0.68);
  }
  100% {
    transform: scaleX(0.88);
  }
}

.mobile-top-actions,
.mobile-menu-head,
.mobile-menu-actions,
.mobile-link-arrow,
.nav-scrim {
  display: none;
}

.mobile-menu-scroll {
  display: contents;
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

.nav-return-btn {
  display: inline-flex;
  align-items: center;
  min-width: 44px;
  height: 44px;
  gap: 1px;
  padding: 0 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--accent);
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.nav-return-btn svg {
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
}

.nav-return-btn span {
  max-width: 76px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  max-width: 42%;
  min-width: 0;
  color: inherit;
  text-decoration: none;
  border-radius: 8px;
  cursor: pointer;
}

.brand-block:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.22);
  outline-offset: 4px;
}

.brand-block:active {
  opacity: 0.72;
}

.brand-logo {
  width: 28px;
  height: 28px;
  object-fit: contain;
  flex: 0 0 auto;
  display: block;
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

.health,
.data-status {
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

.data-status {
  max-width: 188px;
  color: var(--ink-soft);
  text-decoration: none;
}

.data-status:hover {
  border-color: var(--line-strong);
  background: #f8fafc;
}

.data-status.is-green .dot {
  background: #34c759;
}

.data-status.is-yellow .dot {
  background: #ff9f0a;
}

.data-status.is-red .dot {
  background: #ff3b30;
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

.nav-group:first-child {
  padding-left: 6px;
  margin-left: 4px;
  border-left: 1px solid rgba(0, 0, 0, 0.06);
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
  border-radius: 5px;
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
  border: 1px solid var(--line);
  border-radius: 5px;
  background: #fff;
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}

.search-btn:hover {
  background: #f7f9fc;
  color: var(--ink);
}

.search-layer {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  justify-content: center;
  padding: 14vh 16px 0;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(6px) saturate(110%);
  -webkit-backdrop-filter: blur(6px) saturate(110%);
}

.search-panel {
  width: min(440px, 100%);
  max-height: min(520px, 70vh);
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 12px;
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

.mobile-label {
  display: none;
}

.search-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.command-section {
  padding: 12px 16px 0;
}

.command-section + .command-section {
  margin-top: 8px;
  border-top: 1px solid rgba(15, 23, 42, 0.07);
}

.command-section-title {
  margin: 0 0 6px;
  color: var(--slate);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.search-tip {
  padding: 32px 16px;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
}

.search-keys {
  flex: 0 0 auto;
  padding: 10px 16px 12px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  font-size: 11px;
  color: var(--slate);
  line-height: 1.4;
  letter-spacing: 0.01em;
}

.search-list {
  list-style: none;
  margin: 0;
  padding: 12px 16px 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.command-list {
  padding: 0;
}

.search-list > li {
  min-width: 0;
}

.search-recent-list > li:last-child:nth-child(odd) {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
}

.search-recent-list > li:last-child:nth-child(odd) .search-item {
  width: min(100%, 196px);
}

.search-item {
  width: 100%;
  display: flex;
  align-items: center;
  border: 0;
  background: transparent;
  border-radius: 8px;
  padding: 11px 12px;
  text-align: left;
  cursor: pointer;
  font-family: inherit;
}

.search-item:hover {
  background: rgba(0, 113, 227, 0.08);
}

.search-item.selected,
.search-item:focus-visible {
  outline: none;
  background: rgba(0, 113, 227, 0.1);
  box-shadow: inset 0 0 0 2px rgba(0, 113, 227, 0.26);
}

.command-item {
  justify-content: space-between;
  gap: 12px;
}

.command-item-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.command-item-copy b {
  color: var(--ink);
  font-size: 13px;
}

.command-item-copy small {
  overflow: hidden;
  color: var(--muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.command-item svg {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  fill: none;
  stroke: var(--muted);
  stroke-width: 2;
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
  .shell {
    overflow-x: clip;
  }

  .mobile-module-title {
    position: absolute;
    right: 188px;
    left: 120px;
    display: block;
    overflow: hidden;
    color: var(--ink);
    font-size: 14px;
    font-weight: 650;
    line-height: 1;
    text-align: center;
    text-overflow: ellipsis;
    white-space: nowrap;
    pointer-events: none;
  }

  .nav {
    min-height: calc(56px + env(safe-area-inset-top));
    height: calc(56px + env(safe-area-inset-top));
    padding: env(safe-area-inset-top) 10px 0 14px;
    gap: 8px;
    background: rgba(255, 255, 255, 0.96);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .brand-block {
    max-width: calc(100% - 148px);
  }

  .nav--has-back .brand-block {
    max-width: calc(100% - 192px);
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

  .mobile-module-title + .mobile-top-actions .nav-return-btn span {
    display: none;
  }

  .desktop-nav-actions {
    display: none;
  }

  @media (max-width: 380px) {
    .nav-return-btn span {
      display: none;
    }
  }

  .nav-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 101;
    padding: 0;
    border: 0;
    background: transparent;
    visibility: hidden;
    pointer-events: none;
    transition: visibility 0.3s step-end;
  }

  .nav-scrim.visible {
    visibility: visible;
    pointer-events: auto;
    transition: visibility 0s step-start;
  }

  .links {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 102;
    width: 100vw;
    max-width: none;
    height: 100vh;
    height: 100dvh;
    min-height: 0;
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    padding: 0;
    overflow: hidden;
    background: #f2f4f7;
    border-right: 1px solid rgba(0, 0, 0, 0.08);
    box-shadow: 18px 0 54px rgba(15, 23, 42, 0.16);
    transform: translate3d(-100%, 0, 0);
    visibility: hidden;
    transition: transform 0.3s cubic-bezier(0.22, 0.72, 0, 1), visibility 0.3s step-end;
  }

  .links.open,
  .links.visible {
    visibility: visible;
    transition: transform 0.3s cubic-bezier(0.22, 0.72, 0, 1), visibility 0s step-start;
  }

  .links.dragging {
    transition: none;
    will-change: transform;
  }

  .mobile-menu-head {
    display: flex;
    position: relative;
    z-index: 2;
    flex: 0 0 auto;
    align-items: center;
    justify-content: space-between;
    min-height: calc(68px + env(safe-area-inset-top));
    margin: 0;
    padding: calc(8px + env(safe-area-inset-top)) 14px 10px 20px;
    border-bottom: 1px solid rgba(15, 23, 42, 0.1);
    background: #fff;
  }

  .mobile-menu-scroll {
    display: flex;
    flex: 1 1 auto;
    flex-direction: column;
    gap: 10px;
    min-height: 0;
    padding: 10px 12px 16px;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior-y: contain;
    touch-action: pan-y;
    -webkit-overflow-scrolling: touch;
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
    flex: 0 0 auto;
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
    flex: 0 0 auto;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
    border-top: 1px solid rgba(15, 23, 42, 0.1);
    background: #fff;
  }

  .mobile-menu-actions .health {
    justify-content: center;
    min-height: 44px;
    border-radius: 8px;
    background: #f8fafc;
  }

  .mobile-menu-actions .data-status {
    justify-content: center;
    min-height: 44px;
    max-width: none;
    overflow: hidden;
    border-radius: 8px;
    background: #f8fafc;
    text-overflow: ellipsis;
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

  .mobile-logout-btn {
    border-color: rgba(192, 57, 43, 0.28);
    background: rgba(192, 57, 43, 0.06);
    color: #a3342a;
  }

  .main {
    min-width: 0;
    transform: translate3d(var(--mobile-menu-page-offset, 0px), 0, 0);
    transition: transform 0.3s cubic-bezier(0.22, 0.72, 0, 1);
  }

  .nav > .brand-block,
  .nav > .mobile-module-title,
  .nav > .mobile-top-actions,
  .nav > .app-activity {
    transform: translate3d(var(--mobile-menu-page-offset, 0px), 0, 0);
    transition: transform 0.3s cubic-bezier(0.22, 0.72, 0, 1);
  }

  .mobile-menu-dragging .main {
    transition: none;
    will-change: transform;
  }

  .mobile-menu-dragging .nav > .brand-block,
  .mobile-menu-dragging .nav > .mobile-module-title,
  .mobile-menu-dragging .nav > .mobile-top-actions,
  .mobile-menu-dragging .nav > .app-activity {
    transition: none;
    will-change: transform;
  }

  .search-layer {
    inset: 0;
    box-sizing: border-box;
    height: 100dvh;
    align-items: flex-start;
    padding: max(8px, env(safe-area-inset-top)) 0 0;
    background: #fff;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
    overscroll-behavior: none;
  }

  .search-panel {
    flex: 1;
    width: 100%;
    height: 100%;
    max-height: none;
    border: 0;
    border-radius: 0;
    background: #fff;
    box-shadow: none;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .search-head {
    flex: 0 0 auto;
    min-height: 60px;
    padding: 8px 10px 8px 14px;
    background: #fff;
    color: #6e7785;
  }

  .search-head > svg {
    flex: 0 0 auto;
    width: 20px;
    height: 20px;
  }

  .search-input {
    min-height: 44px;
    padding: 0;
    font-size: 16px;
    letter-spacing: 0;
  }

  .search-close {
    min-width: 52px;
    min-height: 44px;
    padding: 0 10px;
    background: transparent;
    color: var(--accent);
    font-size: 14px;
    font-weight: 600;
    text-transform: none;
  }

  .search-close:active {
    background: rgba(0, 113, 227, 0.08);
  }

  .desktop-label {
    display: none;
  }

  .mobile-label {
    display: inline;
  }

  .search-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    overscroll-behavior: contain;
    touch-action: pan-y;
    -webkit-overflow-scrolling: touch;
  }

  .search-tip {
    padding: 28px 18px;
    line-height: 1.6;
  }

  .search-recent > .search-tip {
    padding: 16px 16px 6px;
    color: var(--ink-soft);
    font-size: 12px;
    font-weight: 650;
    text-align: left;
  }

  .command-section {
    padding: 12px 12px 0;
  }

  .command-section-title {
    padding: 0 6px;
  }

  .search-keys {
    display: none;
  }

  .search-list {
    grid-template-columns: 1fr;
    gap: 2px;
    padding: 4px 8px calc(10px + env(safe-area-inset-bottom));
  }

  .command-list {
    padding: 0;
  }

  .search-recent-list > li:last-child:nth-child(odd) {
    display: block;
  }

  .search-recent-list > li:last-child:nth-child(odd) .search-item {
    width: 100%;
  }

  .search-item {
    min-height: 52px;
    padding: 8px 10px;
    border-radius: 8px;
  }

  .search-item:active {
    background: rgba(0, 113, 227, 0.1);
  }

}

@media (max-width: 380px) {
  .brand-en,
  .tagline {
    display: none;
  }
}
</style>
