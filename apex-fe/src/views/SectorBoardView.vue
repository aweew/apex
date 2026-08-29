<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { ArrowDown, Refresh, Search, SortDown, SortUp } from '@element-plus/icons-vue'
import {
  fetchSectorBoard,
  fetchSectorConstituents,
  fetchSectorMainline,
  fetchSectorRotation,
  refreshSectorBoard,
  refreshSectorConstituents,
} from '../api/sector'
import { saveObserve } from '../api/observe'
import { getSyncJob, startSyncJob } from '../api/sync'
import { useTradeDateStore } from '../stores/tradeDate'
import { staleDataTime } from '../utils/dataFreshness.js'
import { resolveActionColumnFixed } from '../utils/responsiveTable.js'
import { snapshotFallbackText, snapshotStamp } from '../utils/snapshotDate'
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()
const route = useRoute()
defineProps({
  embedded: {
    type: Boolean,
    default: false,
  },
})
const tradeDateStore = useTradeDateStore()
const { tradeDate } = storeToRefs(tradeDateStore)
const loading = ref(false)
const refreshing = ref(false)
const activeTab = ref('INDUSTRY')
const sortBy = ref('pctChg')
const order = ref('desc')
const board = ref(null)
const snapshotNotice = ref('')
const mainline = ref([])
const rotation = ref(null)
const rotationLoading = ref(false)
const availableDateSet = ref(new Set())
const nameFilter = ref('')
const mobileSortExpanded = ref(false)

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerRefreshing = ref(false)
const drawerSortBy = ref('pctChg')
const drawerOrder = ref('desc')
const currentSector = ref(null)
const constituents = ref(null)
const pendingSectorCode = ref('')
const viewportWidth = ref(window.innerWidth)
const drawerActionColumnFixed = computed(() => resolveActionColumnFixed(viewportWidth.value))
const constituentDataTime = computed(() => staleDataTime({
  tradeDate: constituents.value?.tradeDate,
  updatedAt: constituents.value?.syncedAt,
  intraday: true,
}))
let constituentLoadSequence = 0
let constituentRefreshSequence = 0

useSessionViewState('sector', {
  activeTab,
  sortBy,
  order,
  nameFilter,
})

const TAB_META = {
  INDUSTRY: { label: '行业', defaultSort: 'pctChg' },
  CONCEPT: { label: '概念', defaultSort: 'pctChg' },
  THEME: { label: '题材', defaultSort: 'netInflow' },
}

const TYPE_LABEL = { INDUSTRY: '行业', CONCEPT: '概念', THEME: '题材' }
const RANKING_PAGE_SIZE = 50
const MOBILE_PRIMARY_SORTS = [
  { label: '涨跌', fullLabel: '涨跌幅', value: 'pctChg' },
  { label: '3日', fullLabel: '3日涨幅', value: 'pctChg3d' },
  { label: '5日', fullLabel: '5日涨幅', value: 'pctChg5d' },
]
const MOBILE_MORE_SORTS = [
  { label: '涨停数', fullLabel: '涨停家数', value: 'limitUpCount' },
  { label: '连板', fullLabel: '连板高度', value: 'maxLianban' },
  { label: '净流入', fullLabel: '净流入', value: 'netInflow' },
]
const mobileSortLabel = computed(() => {
  const currentSort = [...MOBILE_PRIMARY_SORTS, ...MOBILE_MORE_SORTS]
    .find((option) => option.value === sortBy.value)
  return currentSort?.fullLabel || '涨跌幅'
})
const mobileMoreSortActive = computed(() => {
  return MOBILE_MORE_SORTS.some((option) => option.value === sortBy.value)
})

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
}

function selectMobileSort(value) {
  sortBy.value = value
  mobileSortExpanded.value = false
}

function toggleSortOrder() {
  order.value = order.value === 'desc' ? 'asc' : 'desc'
}

const items = computed(() => {
  const list = board.value?.items || []
  const kw = nameFilter.value.trim()
  if (!kw) return list
  return list.filter((row) => String(row.name || '').includes(kw))
})
const rankingPage = ref(1)
const rankingPageOffset = computed(() => (rankingPage.value - 1) * RANKING_PAGE_SIZE)
const rankingTotalPages = computed(() => Math.max(1, Math.ceil(items.value.length / RANKING_PAGE_SIZE)))
const pagedItems = computed(() => {
  const start = (rankingPage.value - 1) * RANKING_PAGE_SIZE
  return items.value.slice(start, start + RANKING_PAGE_SIZE)
})

function onRankingPageChange(page) {
  rankingPage.value = Math.min(Math.max(1, page), rankingTotalPages.value)
}

function applyRouteQuery() {
  const q = String(route.query.q || '').trim()
  const type = String(route.query.type || '').toUpperCase()
  pendingSectorCode.value = String(route.query.code || '').trim()
  if (TAB_META[type]) {
    activeTab.value = type
  }
  if (q) {
    nameFilter.value = q
    if (!TAB_META[type]) {
      activeTab.value = 'THEME'
    }
  }
}

function syncAvailableDates(dates) {
  const list = (dates || []).map((d) => String(d).slice(0, 10))
  availableDateSet.value = new Set(list)
}

function disableUnavailableDate(date) {
  if (!availableDateSet.value.size) return false
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return !availableDateSet.value.has(`${y}-${m}-${d}`)
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return `${n >= 0 ? '+' : ''}${n.toFixed(2)}%`
}

function fmtInflowYi(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const yi = n / 1e8
  const sign = yi > 0 ? '+' : ''
  return `${sign}${yi.toFixed(2)}亿`
}

function fmtAmountYi(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return `${(n / 1e8).toFixed(2)}亿`
}

let suppressDateWatch = false

async function loadMainline(date) {
  try {
    const res = await fetchSectorMainline({ tradeDate: date || undefined, limit: 8 })
    mainline.value = res.data || []
  } catch {
    mainline.value = []
  }
}

async function loadRotation() {
  rotationLoading.value = true
  try {
    const res = await fetchSectorRotation({ days: 10, type: activeTab.value })
    rotation.value = res.data
  } catch {
    rotation.value = null
  } finally {
    rotationLoading.value = false
  }
}

async function load() {
  loading.value = true
  rankingPage.value = 1
  const requestedDate = tradeDate.value
  try {
    const res = await fetchSectorBoard({
      type: activeTab.value,
      sortBy: sortBy.value,
      order: order.value,
      limit: 200,
      tradeDate: requestedDate || undefined,
    })
    board.value = res.data
    const actualDate = snapshotStamp(res.data)
    snapshotNotice.value = snapshotFallbackText(requestedDate, actualDate)
    syncAvailableDates(res.data?.availableDates)
    if (res.data?.tradeDate) {
      const next = String(res.data.tradeDate).slice(0, 10)
      if (tradeDate.value !== next) {
        suppressDateWatch = true
        tradeDateStore.setTradeDate(next)
        Promise.resolve().then(() => {
          suppressDateWatch = false
        })
      }
    }
    await loadMainline(actualDate || undefined)
    await loadRotation()
    await openRouteSector()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

let syncPollCancelled = false

async function waitSyncJob(jobId, timeoutMs = 180000) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    if (syncPollCancelled) throw new Error('已取消')
    const res = await getSyncJob(jobId)
    const status = res.data?.status
    if (status === 'SUCCESS') return res.data
    if (status === 'FAILED' || status === 'CANCELLED') {
      throw new Error(res.data?.message || '同步任务失败')
    }
    await new Promise((r) => setTimeout(r, 2000))
  }
  throw new Error('同步超时，请到数据同步中心查看')
}

async function onRefresh() {
  refreshing.value = true
  syncPollCancelled = false
  try {
    try {
      const job = await startSyncJob({
        taskType: 'SECTOR_QUOTE',
        types: 'INDUSTRY,CONCEPT,THEME',
      })
      await waitSyncJob(job.data?.id)
      if (syncPollCancelled) return
    } catch (syncErr) {
      if (syncPollCancelled || syncErr?.message === '已取消') return
      await refreshSectorBoard('INDUSTRY,CONCEPT,THEME')
    }
    ElMessage.success('已刷新')
    suppressDateWatch = true
    tradeDateStore.setTradeDate('')
    await load()
  } catch (e) {
    if (!syncPollCancelled) {
      ElMessage.error(e.message || '刷新失败')
      await load()
    }
  } finally {
    suppressDateWatch = false
    refreshing.value = false
  }
}

function openMainline(row) {
  if (!row?.boardType) return
  activeTab.value = row.boardType
  openConstituents(row)
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: `板块 ${currentSector.value?.name || ''}`.trim() || '板块成分',
      tags: 'sector',
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function loadConstituents() {
  if (!currentSector.value?.code) return
  const sectorCode = currentSector.value.code
  const sectorType = activeTab.value
  const requestSequence = ++constituentLoadSequence
  drawerLoading.value = true
  try {
    const res = await fetchSectorConstituents(sectorCode, {
      type: sectorType,
      sortBy: drawerSortBy.value,
      order: drawerOrder.value,
      tradeDate: snapshotStamp(board.value) || undefined,
    })
    if (requestSequence !== constituentLoadSequence
        || currentSector.value?.code !== sectorCode
        || activeTab.value !== sectorType) {
      return null
    }
    constituents.value = res.data
    return res.data
  } catch (e) {
    if (requestSequence === constituentLoadSequence) {
      ElMessage.error(e.message || '成分股加载失败')
    }
    return null
  } finally {
    if (requestSequence === constituentLoadSequence) {
      drawerLoading.value = false
    }
  }
}

async function openConstituents(row) {
  if (!row?.code) return
  constituentLoadSequence += 1
  constituentRefreshSequence += 1
  drawerLoading.value = false
  drawerRefreshing.value = false
  currentSector.value = row
  constituents.value = null
  suppressDrawerSortWatch = true
  drawerSortBy.value = 'pctChg'
  drawerOrder.value = 'desc'
  suppressDrawerSortWatch = false
  drawerOpen.value = true
  try {
    const data = await loadConstituents()
    // 当天无缓存或缓存落后时后台刷新，旧快照继续可读
    const isLatest = !tradeDate.value
      || String(board.value?.availableDates?.[0] || '').slice(0, 10) === tradeDate.value
    const boardDate = String(snapshotStamp(board.value) || '').slice(0, 10)
    const constituentDate = String(data?.tradeDate || '').slice(0, 10)
    if (isLatest && (!(data?.items || []).length || (boardDate && boardDate !== constituentDate))) {
      onRefreshConstituents(false)
    }
  } catch {
    // loadConstituents 已提示
  }
}

async function openRouteSector() {
  const sectorCode = pendingSectorCode.value
  const sectorName = String(route.query.q || '').trim()
  if (!sectorCode && !sectorName) return
  const sector = board.value?.items?.find((row) => (
    sectorCode ? row.code === sectorCode : row.name === sectorName
  ))
  pendingSectorCode.value = ''
  if (sector) {
    await openConstituents(sector)
  }
}

function clearRouteSector() {
  if (!route.query.code) return
  const query = { ...route.query }
  delete query.code
  router.replace({ path: route.path, query })
}

async function onRefreshConstituents(showToast = true) {
  if (!currentSector.value?.code) return
  const sectorCode = currentSector.value.code
  const sectorType = activeTab.value
  const requestSequence = ++constituentRefreshSequence
  constituentLoadSequence += 1
  drawerLoading.value = false
  drawerRefreshing.value = true
  try {
    const res = await refreshSectorConstituents(sectorCode, sectorType)
    const refreshed = res.data?.constituents
    if (requestSequence === constituentRefreshSequence
        && currentSector.value?.code === sectorCode
        && activeTab.value === sectorType
        && refreshed) {
      const sortField = drawerSortBy.value === 'latestPrice' ? 'latestPrice' : 'pctChg'
      const sortDirection = drawerOrder.value === 'asc' ? 1 : -1
      const sortedItems = [...(refreshed.items || [])].sort((left, right) => {
        const leftValue = Number(left?.[sortField])
        const rightValue = Number(right?.[sortField])
        const leftMissing = left?.[sortField] == null || Number.isNaN(leftValue)
        const rightMissing = right?.[sortField] == null || Number.isNaN(rightValue)
        if (leftMissing && rightMissing) return 0
        if (leftMissing) return 1
        if (rightMissing) return -1
        return (leftValue - rightValue) * sortDirection
      })
      constituents.value = { ...refreshed, items: sortedItems }
      if (showToast) ElMessage.success('成分已刷新')
    }
  } catch (e) {
    if (requestSequence === constituentRefreshSequence) {
      ElMessage.error(e.message || '成分刷新失败')
    }
  } finally {
    if (requestSequence === constituentRefreshSequence) {
      drawerRefreshing.value = false
    }
  }
}

let suppressSortWatch = false
let suppressDrawerSortWatch = false

watch(activeTab, (name) => {
  const meta = TAB_META[name]
  suppressSortWatch = true
  if (meta) {
    sortBy.value = meta.defaultSort
    order.value = 'desc'
  }
  // load() 内已拉 rotation，避免重复请求
  load().finally(() => {
    suppressSortWatch = false
  })
})

watch([sortBy, order], () => {
  if (suppressSortWatch) return
  load()
})

watch(nameFilter, () => {
  rankingPage.value = 1
})

watch(tradeDate, (val, oldVal) => {
  if (suppressDateWatch || val === oldVal) return
  load()
})

watch([drawerSortBy, drawerOrder], () => {
  if (suppressDrawerSortWatch) return
  if (drawerOpen.value && currentSector.value?.code) {
    loadConstituents()
  }
})

watch(
  () => route.query.q,
  () => {
    applyRouteQuery()
    load()
  },
)

watch(
  () => [route.query.type, route.query.code],
  ([routeType, code]) => {
    const type = String(routeType || '').toUpperCase()
    pendingSectorCode.value = String(code || '').trim()
    if (TAB_META[type] && activeTab.value !== type) {
      activeTab.value = type
      return
    }
    openRouteSector()
  },
)

onMounted(() => {
  window.addEventListener('resize', syncViewportWidth)
  applyRouteQuery()
  load()
})
onBeforeUnmount(() => {
  syncPollCancelled = true
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<template>
  <div :class="['sector-page', { page: !embedded, embedded }]" v-loading="loading || refreshing">
    <header class="header sector-header">
      <div>
        <p class="eyebrow">Sector</p>
        <h1>板块行情</h1>
        <p>{{ snapshotNotice || board?.message || '主线强弱 · 涨停家数 · 联动决策候选' }}</p>
      </div>
      <div class="actions sector-actions">
        <div class="sector-filters sector-desktop-filters">
          <el-date-picker
            v-model="tradeDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="交易日"
            aria-label="选择交易日"
            style="width: 150px"
            :clearable="false"
            :disabled-date="disableUnavailableDate"
          />
          <el-select v-model="sortBy" aria-label="选择排序指标" style="width: 120px" size="default">
            <el-option label="涨跌幅" value="pctChg" />
            <el-option label="3日涨幅" value="pctChg3d" />
            <el-option label="5日涨幅" value="pctChg5d" />
            <el-option label="涨停家数" value="limitUpCount" />
            <el-option label="连板高度" value="maxLianban" />
            <el-option label="净流入" value="netInflow" />
          </el-select>
          <el-select v-model="order" aria-label="选择排序方向" style="width: 100px" size="default">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
          <el-input
            v-model="nameFilter"
            clearable
            aria-label="筛选板块名称"
            placeholder="筛板块名"
            style="width: 140px"
          />
        </div>
        <div class="sector-shortcuts sector-desktop-shortcuts">
          <el-button type="primary" :loading="refreshing" @click="onRefresh">刷新</el-button>
          <el-button plain @click="router.push('/limit-up')">涨停</el-button>
          <el-button plain @click="router.push('/decision')">决策</el-button>
        </div>

        <section class="sector-mobile-filters" aria-label="板块筛选">
          <div class="mobile-filter-primary">
            <el-date-picker
              v-model="tradeDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="交易日"
              aria-label="选择交易日"
              :clearable="false"
              :disabled-date="disableUnavailableDate"
            />
            <el-input
              v-model="nameFilter"
              clearable
              :prefix-icon="Search"
              aria-label="筛选板块名称"
              placeholder="搜索板块"
            />
          </div>

          <div class="mobile-sort-control">
            <span class="mobile-filter-label">排序</span>
            <div class="mobile-sort-row">
              <div class="mobile-sort-strip" role="group" aria-label="排序指标">
                <button
                  v-for="option in MOBILE_PRIMARY_SORTS"
                  :key="option.value"
                  type="button"
                  class="mobile-sort-chip"
                  :class="{ 'is-active': sortBy === option.value }"
                  :aria-pressed="sortBy === option.value"
                  @click="selectMobileSort(option.value)"
                >
                  {{ option.label }}
                </button>
                <button
                  type="button"
                  class="mobile-sort-chip mobile-sort-more"
                  :class="{ 'is-active': mobileMoreSortActive }"
                  :aria-expanded="mobileSortExpanded"
                  aria-controls="mobile-sector-more-sorts"
                  @click="mobileSortExpanded = !mobileSortExpanded"
                >
                  更多
                  <el-icon :class="{ 'is-open': mobileSortExpanded }"><ArrowDown /></el-icon>
                </button>
              </div>
              <button
                type="button"
                class="mobile-order-toggle"
                :aria-label="order === 'desc' ? '切换为升序' : '切换为降序'"
                :title="order === 'desc' ? '当前降序' : '当前升序'"
                @click="toggleSortOrder"
              >
                <el-icon><SortDown v-if="order === 'desc'" /><SortUp v-else /></el-icon>
              </button>
            </div>
            <div
              v-show="mobileSortExpanded"
              id="mobile-sector-more-sorts"
              class="mobile-sort-overflow"
              role="group"
              aria-label="更多排序指标"
            >
              <button
                v-for="option in MOBILE_MORE_SORTS"
                :key="option.value"
                type="button"
                class="mobile-sort-chip"
                :class="{ 'is-active': sortBy === option.value }"
                :aria-pressed="sortBy === option.value"
                @click="selectMobileSort(option.value)"
              >
                {{ option.label }}
              </button>
            </div>
          </div>

          <div class="mobile-filter-summary" aria-live="polite">
            <span><b>{{ items.length }}</b> 个板块</span>
            <span>{{ mobileSortLabel }} · {{ order === 'desc' ? '降序' : '升序' }}</span>
          </div>
        </section>

        <div class="mobile-sector-shortcuts">
          <el-button
            class="mobile-refresh-button"
            :icon="Refresh"
            :loading="refreshing"
            aria-label="刷新板块行情"
            title="刷新板块行情"
            @click="onRefresh"
          />
          <el-button plain @click="router.push('/limit-up')">涨停</el-button>
          <el-button plain @click="router.push('/decision')">决策</el-button>
        </div>
      </div>
    </header>

    <section v-loading="rotationLoading" class="rotation">
      <div class="rotation-head">
        <h3><TermTip term="theme_rotation">轮动时间轴</TermTip></h3>
        <span class="muted">{{ rotation?.message || '近10日行业涨幅 Top' }}</span>
      </div>
      <div v-if="rotation?.days?.length" class="rotation-viewport">
        <div class="rotation-track">
          <div
            v-for="(day, dayIndex) in rotation.days"
            :key="day.tradeDate"
            class="rotation-day"
            :class="{ 'is-latest': dayIndex === 0 }"
          >
            <div class="rotation-date">
              <span>{{ String(day.tradeDate).slice(5) }}</span>
              <span v-if="dayIndex === 0" class="latest-dot" aria-label="最新交易日" />
            </div>
            <div class="rotation-tops">
              <span v-for="(top, idx) in day.tops || []" :key="idx" class="rotation-chip">
                <span class="rotation-position">{{ idx + 1 }}</span>
                <span class="rotation-name">{{ top }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="!rotationLoading" class="rotation-empty">暂无轮动数据</div>
    </section>

    <section v-if="mainline.length" class="mainline-section">
      <div class="mainline-head">
        <h3>主线候选</h3>
        <span>{{ mainline.length }} 个方向</span>
      </div>
      <div class="mainline">
        <button
          v-for="(row, idx) in mainline"
          :key="row.boardType + row.code"
          type="button"
          class="mainline-item"
          :aria-label="`查看${row.name || ''}成分股`"
          @click="openMainline(row)"
        >
          <span class="rank" :class="idx < 3 ? 'rank-' + (idx + 1) : 'rank-other'">{{ idx + 1 }}</span>
          <span class="mainline-heading">
            <span class="ml-type">{{ TYPE_LABEL[row.boardType] || row.boardType }}</span>
            <b class="mainline-name">{{ row.name }}</b>
          </span>
          <span class="mainline-change" :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">
            {{ fmtPct(row.pctChg) }}
          </span>
          <span class="mainline-signals">
            <span v-if="row.limitUpCount" class="signal-pill limit-up-signal">涨停 {{ row.limitUpCount }}</span>
            <span v-if="row.maxLianban" class="signal-pill board-height-signal">{{ row.maxLianban }} 板</span>
          </span>
          <span class="mainline-reason">{{ row.moveReason || '-' }}</span>
        </button>
      </div>
    </section>

    <section class="board-ranking">
      <div class="board-ranking-head">
        <h3>板块榜单</h3>
        <span>{{ items.length }} 个板块</span>
      </div>
      <el-tabs v-model="activeTab" class="tabs">
        <el-tab-pane
          v-for="(meta, key) in TAB_META"
          :key="key"
          :label="meta.label"
          :name="key"
        />
      </el-tabs>

      <el-table
        :data="pagedItems"
        class="board-table"
        size="small"
        stripe
        empty-text="暂无数据，请先刷新榜单"
        highlight-current-row
        @row-click="openConstituents"
        style="cursor: pointer"
      >
        <el-table-column label="#" width="52" align="center">
          <template #default="{ $index }">
            <span
              v-if="rankingPageOffset + $index < 3"
              class="rank"
              :class="'rank-' + (rankingPageOffset + $index + 1)"
            >
              {{ rankingPageOffset + $index + 1 }}
            </span>
            <span v-else class="rank-muted">{{ rankingPageOffset + $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="110" sortable />
        <el-table-column width="90" sortable prop="pctChg">
          <template #header><TermTip term="pct_chg">涨跌幅</TermTip></template>
          <template #default="{ row }">
            <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="3日" width="85" sortable prop="pctChg3d">
          <template #default="{ row }">
            <span :class="Number(row.pctChg3d) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg3d) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="5日" width="85" sortable prop="pctChg5d">
          <template #default="{ row }">
            <span :class="Number(row.pctChg5d) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg5d) }}</span>
          </template>
        </el-table-column>
        <el-table-column width="100" sortable prop="netInflow">
          <template #header><TermTip term="main_fund_flow">净流入</TermTip></template>
          <template #default="{ row }">
            <span :class="Number(row.netInflow) >= 0 ? 'up' : 'down'">{{ fmtInflowYi(row.netInflow) }}</span>
          </template>
        </el-table-column>
        <el-table-column width="70" sortable prop="limitUpCount" align="center">
          <template #header><TermTip term="limit_up">涨停</TermTip></template>
          <template #default="{ row }">
            <span :class="Number(row.limitUpCount) > 0 ? 'up' : ''">{{ row.limitUpCount ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column width="70" sortable prop="maxLianban" align="center">
          <template #header><TermTip term="lianban">连板</TermTip></template>
          <template #default="{ row }">
            <span :class="Number(row.maxLianban) > 1 ? 'up' : ''">{{ row.maxLianban ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="领涨股" min-width="120">
          <template #default="{ row }">
            <template v-if="row.leadStockName || row.leadStockCode">
              <el-button
                v-if="row.leadStockCode"
                link
                type="primary"
                @click.stop="router.push(`/stock/${row.leadStockCode}`)"
              >
                {{ row.leadStockName || row.leadStockCode }}
              </el-button>
              <span v-else>{{ row.leadStockName }}</span>
              <span class="lead-pct" :class="Number(row.leadStockPct) >= 0 ? 'up' : 'down'">
                {{ fmtPct(row.leadStockPct) }}
              </span>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="moveReason" label="涨跌原因" min-width="220" show-overflow-tooltip />
      </el-table>
      <div v-if="items.length > RANKING_PAGE_SIZE" class="sector-pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="items.length"
          :current-page="rankingPage"
          :page-size="RANKING_PAGE_SIZE"
          @current-change="onRankingPageChange"
        />
      </div>
      <div v-if="items.length > RANKING_PAGE_SIZE" class="sector-mobile-pagination" aria-label="板块榜单分页">
        <el-button
          :disabled="rankingPage <= 1"
          @click="onRankingPageChange(rankingPage - 1)"
        >
          上一页
        </el-button>
        <span>{{ rankingPage }} / {{ rankingTotalPages }}</span>
        <el-button
          :disabled="rankingPage >= rankingTotalPages"
          @click="onRankingPageChange(rankingPage + 1)"
        >
          下一页
        </el-button>
      </div>
    </section>

    <el-drawer
      v-model="drawerOpen"
      class="sector-drawer"
      :title="`${currentSector?.name || ''}（${currentSector?.code || ''}）成分股`"
      size="440px"
      append-to-body
      destroy-on-close
      @closed="clearRouteSector"
    >
      <div class="drawer-actions">
        <div v-if="constituentDataTime" class="drawer-snapshot">
          <span>{{ constituentDataTime }}</span>
        </div>
        <div class="drawer-controls">
          <el-select
            v-model="drawerSortBy"
            size="small"
            :disabled="drawerRefreshing"
            aria-label="成分股排序指标"
          >
            <el-option label="涨跌幅" value="pctChg" />
            <el-option label="最新价" value="latestPrice" />
          </el-select>
          <el-select
            v-model="drawerOrder"
            size="small"
            :disabled="drawerRefreshing"
            aria-label="成分股排序方向"
          >
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
          <el-tooltip content="刷新成分股" placement="top">
            <el-button
              type="primary"
              plain
              size="small"
              :icon="Refresh"
              aria-label="刷新成分股"
              :loading="drawerRefreshing"
              @click="onRefreshConstituents()"
            />
          </el-tooltip>
        </div>
      </div>
      <el-table
        v-loading="drawerLoading"
        class="constituent-table"
        :data="constituents?.items || []"
        size="small"
        stripe
        :empty-text="drawerRefreshing ? '正在获取最新成分股，可关闭抽屉继续浏览' : '暂无成分股，请刷新成分'"
        max-height="70vh"
      >
        <el-table-column prop="name" label="股票" width="132" sortable>
          <template #default="{ row }">
            <StockIdentity
              :security="row"
              :interactive="Boolean(row.code)"
              compact
              @select="router.push(`/stock/${row.code}`)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="latestPrice" label="最新价" width="96" sortable />
        <el-table-column width="96" sortable prop="pctChg">
          <template #header><TermTip term="pct_chg">涨跌幅</TermTip></template>
          <template #default="{ row }">
            <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="64" :fixed="drawerActionColumnFixed">
          <template #default="{ row }">
            <el-button link type="warning" :disabled="!row.code" @click="addObserve(row)">观察</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.sector-actions,
.sector-filters,
.sector-shortcuts {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sector-actions {
  justify-content: flex-end;
}

.sector-mobile-filters,
.mobile-sector-shortcuts {
  display: none;
}

.sector-shortcuts :deep(.el-button + .el-button) {
  margin-left: 0;
}

.rotation {
  margin-bottom: 16px;
  padding: 14px 16px 12px;
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: #fff;
  box-shadow: var(--shadow-soft);
}

.rotation-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.rotation-head h3 {
  position: relative;
  margin: 0;
  padding-left: 10px;
  font-size: 16px;
  letter-spacing: 0;
}

.rotation-head h3::before {
  position: absolute;
  top: 2px;
  bottom: 2px;
  left: 0;
  width: 3px;
  border-radius: 3px;
  background: var(--accent);
  content: '';
}

.rotation-viewport {
  position: relative;
  margin-right: -16px;
}

.rotation-viewport::after {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 8px;
  width: 30px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.94));
  pointer-events: none;
  content: '';
}

.rotation-track {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 0 28px 9px 0;
  scroll-padding-left: 0;
  scroll-snap-type: x proximity;
  overscroll-behavior-x: contain;
  scrollbar-color: rgba(0, 113, 227, 0.34) rgba(20, 32, 51, 0.06);
  scrollbar-width: thin;
  -webkit-overflow-scrolling: touch;
}

.rotation-track::-webkit-scrollbar {
  height: 4px;
}

.rotation-track::-webkit-scrollbar-track {
  border-radius: 4px;
  background: rgba(20, 32, 51, 0.06);
}

.rotation-track::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: rgba(0, 113, 227, 0.34);
}

.rotation-day {
  flex: 0 0 178px;
  min-height: 148px;
  padding: 10px 11px;
  scroll-snap-align: start;
  border: 1px solid rgba(20, 32, 51, 0.09);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(20, 32, 51, 0.035);
}

.rotation-day.is-latest {
  border-color: rgba(0, 113, 227, 0.28);
  background: #f7fbff;
  box-shadow: inset 0 2px 0 rgba(0, 113, 227, 0.72);
}

.rotation-date {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.latest-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(0, 113, 227, 0.11);
}

.rotation-tops {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.rotation-chip {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  align-items: baseline;
  gap: 5px;
  min-width: 0;
  font-size: 11px;
  color: var(--slate);
}

.rotation-position {
  color: #9ca3af;
  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.rotation-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.rotation-empty {
  font-size: 12px;
  color: var(--muted);
  padding: 8px 0;
}

.mainline-section {
  margin-bottom: 18px;
}

.mainline-head,
.board-ranking-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.mainline-head h3,
.board-ranking-head h3 {
  margin: 0;
  font-size: 15px;
  letter-spacing: 0;
}

.mainline-head > span,
.board-ranking-head > span {
  color: var(--muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.mainline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mainline-item {
  position: relative;
  display: grid;
  grid-template-columns: 24px minmax(150px, max-content) max-content max-content minmax(0, 1fr);
  align-items: center;
  width: 100%;
  gap: 12px;
  padding: 11px 36px 11px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: #fff;
  box-shadow: var(--shadow-soft);
  cursor: pointer;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  text-align: left;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
  -webkit-tap-highlight-color: transparent;
}

.mainline-item::after {
  position: absolute;
  top: 50%;
  right: 16px;
  width: 7px;
  height: 7px;
  border-top: 1.5px solid #a0a7b2;
  border-right: 1.5px solid #a0a7b2;
  content: '';
  transform: translateY(-50%) rotate(45deg);
}

.mainline-item:hover {
  border-color: rgba(22, 105, 201, 0.42);
  box-shadow: 0 3px 9px rgba(15, 23, 42, 0.08);
}

.mainline-item:active {
  box-shadow: 0 2px 8px rgba(20, 32, 51, 0.05);
  transform: translateY(0) scale(0.997);
}

.mainline-item:focus-visible {
  outline: 3px solid rgba(0, 113, 227, 0.2);
  outline-offset: 2px;
}

.mainline-heading {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.ml-type {
  flex: 0 0 auto;
  padding: 3px 6px;
  border: 1px solid rgba(0, 113, 227, 0.12);
  border-radius: 5px;
  background: rgba(0, 113, 227, 0.06);
  color: #44617c;
  font-size: 11px;
  white-space: nowrap;
}

.mainline-name {
  min-width: 0;
  overflow: hidden;
  color: #202733;
  font-size: 14px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mainline-change,
.mainline-signals {
  white-space: nowrap;
}

.mainline-change {
  padding: 4px 7px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 650;
}

.mainline-change.up {
  background: rgba(255, 59, 48, 0.075);
}

.mainline-change.down {
  background: rgba(52, 199, 89, 0.09);
}

.mainline-signals {
  display: flex;
  gap: 6px;
}

.signal-pill {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 7px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 600;
}

.limit-up-signal {
  background: rgba(255, 59, 48, 0.08);
  color: #d92f28;
}

.board-height-signal {
  background: rgba(255, 159, 10, 0.11);
  color: #a75f00;
}

.mainline-reason {
  min-width: 0;
  overflow: hidden;
  color: #737b87;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  font-variant-numeric: tabular-nums;
}

.rank-1 {
  background: #d9901d;
  box-shadow: 0 3px 8px rgba(217, 144, 29, 0.22);
}

.rank-2 {
  background: #7d8795;
}

.rank-3 {
  background: #a96c43;
}

.rank-other {
  background: #eef1f4;
  color: var(--muted);
  font-size: 12px;
}

.rank-muted {
  color: var(--muted);
  font-size: 12px;
}

.board-ranking {
  min-width: 0;
}

.board-ranking-head {
  margin-bottom: 2px;
}

.tabs {
  --el-tabs-header-height: 42px;
}

.tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.tabs :deep(.el-tabs__item) {
  min-width: 72px;
  padding: 0 18px;
  font-weight: 600;
}

.board-table {
  overflow: hidden;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: #fff;
}

.sector-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

.sector-mobile-pagination {
  display: none;
}

.lead-pct {
  margin-left: 6px;
  font-size: 12px;
}

:global(.sector-drawer) {
  width: min(440px, 100vw) !important;
}

:global(.sector-drawer .el-drawer__header) {
  margin-bottom: 12px;
  padding: 16px 14px 0;
}

:global(.sector-drawer .el-drawer__body) {
  padding: 0 12px 16px;
}

:global(.sector-drawer .drawer-actions) {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

:global(.sector-drawer .drawer-snapshot) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.4;
}

:global(.sector-drawer .drawer-controls) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.82fr) 36px;
  gap: 6px;
}

:global(.sector-drawer .drawer-controls .el-select),
:global(.sector-drawer .drawer-controls .el-button) {
  width: 100% !important;
  min-width: 0;
  margin: 0;
}

:global(.sector-drawer .drawer-controls .el-select__wrapper),
:global(.sector-drawer .drawer-controls .el-button) {
  min-height: 36px;
  border-radius: 7px;
}

:global(.sector-drawer .constituent-table th.is-sortable > .cell) {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

:global(.sector-drawer .constituent-table .caret-wrapper) {
  flex: 0 0 24px;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .sector-header {
    margin-bottom: 16px;
  }

  .sector-actions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .sector-filters {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .sector-filters :deep(.el-date-editor),
  .sector-filters :deep(.el-select),
  .sector-filters :deep(.el-input) {
    width: 100% !important;
    min-width: 0;
  }

  .sector-shortcuts {
    justify-content: flex-end;
  }

  .mainline {
    gap: 8px;
  }

  .mainline-item {
    grid-template-columns: 24px minmax(0, 1fr) max-content;
    grid-template-areas:
      "rank heading change"
      ". signals signals"
      ". reason reason";
    align-items: start;
    column-gap: 8px;
    row-gap: 7px;
    padding: 12px 36px 12px 12px;
  }

  .mainline-item::after {
    top: 26px;
  }

  .mainline-item .rank {
    grid-area: rank;
  }

  .mainline-heading {
    grid-area: heading;
  }

  .mainline-change {
    grid-area: change;
  }

  .mainline-signals {
    grid-area: signals;
  }

  .mainline-reason {
    grid-area: reason;
    padding-top: 7px;
    border-top: 1px solid rgba(20, 32, 51, 0.065);
    overflow: hidden;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    white-space: normal;
  }
}

@media (max-width: 560px) {
  .sector-header {
    gap: 12px;
    margin-bottom: 14px;
  }

  .sector-header > div:first-child {
    width: 100%;
  }

  .sector-header > div:first-child > p:last-child {
    margin-top: 4px;
    font-size: 12px;
  }

  .sector-actions {
    width: 100%;
  }

  .sector-desktop-filters,
  .sector-desktop-shortcuts {
    display: none;
  }

  .sector-mobile-filters {
    display: grid;
    gap: 10px;
    width: 100%;
    padding: 10px;
    border: 1px solid var(--glass-border);
    border-radius: 8px;
    background: var(--glass-strong);
    box-shadow: var(--shadow-soft);
  }

  .mobile-filter-primary {
    display: grid;
    grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
    gap: 8px;
  }

  .mobile-filter-primary :deep(.el-date-editor),
  .mobile-filter-primary :deep(.el-input) {
    width: 100% !important;
    min-width: 0;
  }

  .mobile-filter-primary :deep(.el-input__wrapper) {
    min-height: 44px;
    border-radius: 7px;
    box-shadow: 0 0 0 1px var(--line) inset;
  }

  .mobile-filter-primary :deep(.el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px var(--accent) inset;
  }

  .mobile-sort-control {
    display: grid;
    gap: 6px;
  }

  .mobile-filter-label {
    color: var(--muted);
    font-size: 11px;
    font-weight: 600;
  }

  .mobile-sort-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 44px;
    gap: 8px;
  }

  .mobile-sort-strip {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 3px;
    min-width: 0;
    padding: 3px;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--paper-deep);
  }

  .mobile-sort-chip {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 2px;
    min-width: 0;
    min-height: 44px;
    padding: 0 5px;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--slate);
    font: inherit;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    touch-action: manipulation;
  }

  .mobile-sort-chip.is-active {
    background: var(--glass-strong);
    color: var(--accent);
    box-shadow: 0 1px 4px rgba(20, 32, 51, 0.1);
  }

  .mobile-sort-more .el-icon {
    transition: transform 0.2s ease;
  }

  .mobile-sort-more .el-icon.is-open {
    transform: rotate(180deg);
  }

  .mobile-order-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    align-self: center;
    padding: 0;
    border: 1px solid var(--line);
    border-radius: 8px;
    background: var(--glass-strong);
    color: var(--accent);
    cursor: pointer;
    touch-action: manipulation;
  }

  .mobile-order-toggle .el-icon {
    font-size: 19px;
  }

  .mobile-sort-chip:active,
  .mobile-order-toggle:active {
    background: var(--fill);
  }

  .mobile-sort-chip:focus-visible,
  .mobile-order-toggle:focus-visible {
    outline: 3px solid rgba(0, 113, 227, 0.2);
    outline-offset: 1px;
  }

  .mobile-sort-overflow {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 6px;
    padding-top: 2px;
  }

  .mobile-sort-overflow .mobile-sort-chip {
    border: 1px solid var(--line);
  }

  .mobile-filter-summary {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    min-height: 30px;
    padding-top: 7px;
    border-top: 1px solid var(--line);
    color: var(--muted);
    font-size: 11px;
  }

  .mobile-filter-summary b {
    color: var(--ink-soft);
    font-variant-numeric: tabular-nums;
  }

  .mobile-sector-shortcuts {
    display: grid;
    grid-template-columns: 44px repeat(2, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
  }

  .mobile-sector-shortcuts :deep(.el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
    border-radius: 8px;
  }

  .mobile-sector-shortcuts .mobile-refresh-button {
    padding: 0;
  }

  .rotation {
    margin-right: -10px;
    margin-left: -10px;
    padding: 14px 10px 12px;
    border-right: 0;
    border-left: 0;
    border-radius: 0;
    box-shadow: 0 5px 16px rgba(20, 32, 51, 0.035);
  }

  .rotation-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .rotation-head .muted {
    padding-left: 10px;
    line-height: 1.4;
  }

  .rotation-viewport {
    margin-right: -10px;
  }

  .rotation-day {
    flex-basis: min(68vw, 196px);
  }

  .mainline-section {
    margin-bottom: 20px;
  }

  .mainline-item:hover {
    box-shadow: 0 2px 10px rgba(20, 32, 51, 0.035);
    transform: none;
  }

  .mainline-item:active {
    border-color: rgba(0, 113, 227, 0.3);
    background: #f8fbff;
    transform: scale(0.995);
  }

  .mainline-heading {
    align-items: center;
    flex-direction: row;
    gap: 6px;
  }

  .mainline-name {
    flex: 1;
  }

  .mainline-change {
    margin-top: 1px;
  }

  .tabs {
    --el-tabs-header-height: 44px;
  }

  .tabs :deep(.el-tabs__nav-wrap) {
    padding: 3px;
    border: 1px solid rgba(20, 32, 51, 0.08);
    border-radius: 8px;
    background: rgba(20, 32, 51, 0.045);
  }

  .tabs :deep(.el-tabs__nav-wrap::after),
  .tabs :deep(.el-tabs__active-bar) {
    display: none;
  }

  .tabs :deep(.el-tabs__nav) {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
    float: none;
  }

  .tabs :deep(.el-tabs__item) {
    min-width: 0;
    width: 100%;
    height: 38px;
    padding: 0;
    color: var(--slate);
    transition: color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
  }

  .tabs :deep(.el-tabs__item.is-active) {
    border-radius: 5px;
    background: var(--accent);
    color: #fff;
    box-shadow: none;
  }

  .board-table {
    border-radius: 8px;
  }

  .sector-pagination {
    display: none;
  }

  .sector-mobile-pagination {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: center;
    gap: 10px;
    padding-top: 12px;
    color: var(--muted);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    text-align: center;
  }

  .sector-mobile-pagination :deep(.el-button) {
    min-height: 40px;
    margin: 0;
    border-radius: 7px;
  }

}
</style>
