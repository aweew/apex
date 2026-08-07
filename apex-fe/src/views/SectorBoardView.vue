<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
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

const router = useRouter()
const route = useRoute()
const tradeDateStore = useTradeDateStore()
const { tradeDate } = storeToRefs(tradeDateStore)
const loading = ref(false)
const refreshing = ref(false)
const activeTab = ref('INDUSTRY')
const sortBy = ref('pctChg')
const order = ref('desc')
const board = ref(null)
const mainline = ref([])
const rotation = ref(null)
const rotationLoading = ref(false)
const availableDateSet = ref(new Set())
const nameFilter = ref('')

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerRefreshing = ref(false)
const drawerSortBy = ref('pctChg')
const drawerOrder = ref('desc')
const currentSector = ref(null)
const constituents = ref(null)

const TAB_META = {
  INDUSTRY: { label: '行业', defaultSort: 'pctChg' },
  CONCEPT: { label: '概念', defaultSort: 'pctChg' },
  THEME: { label: '题材', defaultSort: 'netInflow' },
}

const TYPE_LABEL = { INDUSTRY: '行业', CONCEPT: '概念', THEME: '题材' }

const items = computed(() => {
  const list = board.value?.items || []
  const kw = nameFilter.value.trim()
  if (!kw) return list
  return list.filter((row) => String(row.name || '').includes(kw))
})

function applyRouteQuery() {
  const q = String(route.query.q || '').trim()
  nameFilter.value = q
  if (q) {
    activeTab.value = 'THEME'
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

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
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
  try {
    const res = await fetchSectorBoard({
      type: activeTab.value,
      sortBy: sortBy.value,
      order: order.value,
      limit: 200,
      tradeDate: tradeDate.value || undefined,
    })
    board.value = res.data
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
    await loadMainline(tradeDate.value || res.data?.tradeDate)
    await loadRotation()
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
  drawerLoading.value = true
  try {
    const res = await fetchSectorConstituents(currentSector.value.code, {
      type: activeTab.value,
      sortBy: drawerSortBy.value,
      order: drawerOrder.value,
      tradeDate: tradeDate.value || undefined,
    })
    constituents.value = res.data
    return res.data
  } catch (e) {
    ElMessage.error(e.message || '成分股加载失败')
    return null
  } finally {
    drawerLoading.value = false
  }
}

async function openConstituents(row) {
  if (!row?.code) return
  currentSector.value = row
  constituents.value = null
  suppressDrawerSortWatch = true
  drawerSortBy.value = 'pctChg'
  drawerOrder.value = 'desc'
  suppressDrawerSortWatch = false
  drawerOpen.value = true
  try {
    const data = await loadConstituents()
    // 仅当天无成分时自动拉取；历史日不自动刷新以免覆盖当日口径
    const isLatest = !tradeDate.value
      || String(board.value?.availableDates?.[0] || '').slice(0, 10) === tradeDate.value
    if (!(data?.items || []).length && isLatest) {
      await onRefreshConstituents(false)
    }
  } catch {
    // loadConstituents 已提示
  }
}

async function onRefreshConstituents(showToast = true) {
  if (!currentSector.value?.code) return
  drawerRefreshing.value = true
  try {
    await refreshSectorConstituents(currentSector.value.code, activeTab.value)
    await loadConstituents()
    if (showToast) ElMessage.success('成分已刷新')
  } catch (e) {
    ElMessage.error(e.message || '成分刷新失败')
  } finally {
    drawerRefreshing.value = false
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

onMounted(() => {
  applyRouteQuery()
  load()
})
onBeforeUnmount(() => {
  syncPollCancelled = true
})
</script>

<template>
  <div class="page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Sector</p>
        <h1>板块行情</h1>
        <p>主线强弱 · 涨停家数 · 联动决策候选</p>
      </div>
      <div class="actions">
        <el-date-picker
          v-model="tradeDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="交易日"
          style="width: 150px"
          :clearable="false"
          :disabled-date="disableUnavailableDate"
        />
        <el-select v-model="sortBy" style="width: 120px" size="default">
          <el-option label="涨跌幅" value="pctChg" />
          <el-option label="3日涨幅" value="pctChg3d" />
          <el-option label="5日涨幅" value="pctChg5d" />
          <el-option label="涨停家数" value="limitUpCount" />
          <el-option label="连板高度" value="maxLianban" />
          <el-option label="净流入" value="netInflow" />
        </el-select>
        <el-select v-model="order" style="width: 100px" size="default">
          <el-option label="降序" value="desc" />
          <el-option label="升序" value="asc" />
        </el-select>
        <el-input
          v-model="nameFilter"
          clearable
          placeholder="筛板块名"
          style="width: 140px"
        />
        <el-button type="primary" :loading="refreshing" @click="onRefresh">刷新</el-button>
        <el-button plain @click="router.push('/limit-up')">涨停</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
      </div>
    </header>

    <section v-loading="rotationLoading" class="rotation">
      <div class="rotation-head">
        <h3>轮动时间轴</h3>
        <span class="muted">{{ rotation?.message || '近10日行业涨幅 Top' }}</span>
      </div>
      <div v-if="rotation?.days?.length" class="rotation-track">
        <div v-for="day in rotation.days" :key="day.tradeDate" class="rotation-day">
          <div class="rotation-date">{{ String(day.tradeDate).slice(5) }}</div>
          <div class="rotation-tops">
            <span v-for="(top, idx) in day.tops || []" :key="idx" class="rotation-chip">{{ top }}</span>
          </div>
        </div>
      </div>
      <div v-else-if="!rotationLoading" class="rotation-empty">暂无轮动数据</div>
    </section>

    <div v-if="mainline.length" class="mainline">
      <div
        v-for="(row, idx) in mainline"
        :key="row.boardType + row.code"
        class="mainline-item"
        @click="openMainline(row)"
      >
        <span v-if="idx < 3" class="rank" :class="'rank-' + (idx + 1)">{{ idx + 1 }}</span>
        <span class="ml-type">{{ TYPE_LABEL[row.boardType] || row.boardType }}</span>
        <b>{{ row.name }}</b>
        <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
        <span v-if="row.limitUpCount" class="up">涨停{{ row.limitUpCount }}</span>
        <span v-if="row.maxLianban" class="up">{{ row.maxLianban }}板</span>
        <span class="muted">{{ row.moveReason || '-' }}</span>
      </div>
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
      :data="items"
      size="small"
      stripe
      empty-text="暂无数据，请先刷新榜单"
      highlight-current-row
      @row-click="openConstituents"
      style="cursor: pointer"
    >
      <el-table-column label="#" width="52" align="center">
        <template #default="{ $index }">
          <span v-if="$index < 3" class="rank" :class="'rank-' + ($index + 1)">{{ $index + 1 }}</span>
          <span v-else class="rank-muted">{{ $index + 1 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="110" sortable />
      <el-table-column label="涨跌幅" width="90" sortable prop="pctChg">
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
      <el-table-column label="净流入" width="100" sortable prop="netInflow">
        <template #default="{ row }">
          <span :class="Number(row.netInflow) >= 0 ? 'up' : 'down'">{{ fmtInflowYi(row.netInflow) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="涨停" width="70" sortable prop="limitUpCount" align="center">
        <template #default="{ row }">
          <span :class="Number(row.limitUpCount) > 0 ? 'up' : ''">{{ row.limitUpCount ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="连板" width="70" sortable prop="maxLianban" align="center">
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

    <el-drawer
      v-model="drawerOpen"
      :title="`${currentSector?.name || ''}（${currentSector?.code || ''}）成分股`"
      size="520px"
      destroy-on-close
    >
      <div class="drawer-actions">
        <span class="muted">{{ fmtTime(constituents?.syncedAt) }}</span>
        <div class="drawer-controls">
          <el-select v-model="drawerSortBy" style="width: 100px" size="small">
            <el-option label="涨跌幅" value="pctChg" />
            <el-option label="最新价" value="latestPrice" />
          </el-select>
          <el-select v-model="drawerOrder" style="width: 88px" size="small">
            <el-option label="降序" value="desc" />
            <el-option label="升序" value="asc" />
          </el-select>
          <el-button
            type="primary"
            size="small"
            :loading="drawerRefreshing"
            @click="onRefreshConstituents()"
          >
            刷新成分
          </el-button>
        </div>
      </div>
      <el-table
        v-loading="drawerLoading || drawerRefreshing"
        :data="constituents?.items || []"
        size="small"
        stripe
        empty-text="暂无成分股，请刷新成分"
        max-height="70vh"
      >
        <el-table-column prop="code" label="代码" width="90" sortable>
          <template #default="{ row }">
            <el-button
              v-if="row.code"
              link
              type="primary"
              @click="router.push(`/stock/${row.code}`)"
            >
              {{ row.code }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" sortable>
          <template #default="{ row }">
            <StockBoardTag :code="row.code" :market="row.market">{{ row.name || '-' }}</StockBoardTag>
          </template>
        </el-table-column>
        <el-table-column prop="latestPrice" label="最新价" width="90" sortable />
        <el-table-column label="涨跌幅" width="90" sortable prop="pctChg">
          <template #default="{ row }">
            <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" :disabled="!row.code" @click="addObserve(row)">观察</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.rotation {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
}

.rotation-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.rotation-head h3 {
  margin: 0;
  font-size: 14px;
}

.rotation-track {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.rotation-day {
  flex: 0 0 auto;
  min-width: 140px;
  max-width: 200px;
  padding: 8px 10px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
}

.rotation-date {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 6px;
}

.rotation-tops {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.rotation-chip {
  font-size: 11px;
  color: var(--slate);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rotation-empty {
  font-size: 12px;
  color: var(--muted);
  padding: 8px 0;
}

.mainline {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.mainline-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
  cursor: pointer;
  font-size: 13px;
}

.mainline-item:hover {
  border-color: var(--el-color-primary);
}

.ml-type {
  color: var(--muted);
  font-size: 12px;
  min-width: 28px;
}

.rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
}

.rank-1 {
  background: #e6a23c;
}

.rank-2 {
  background: #909399;
}

.rank-3 {
  background: #b87333;
}

.rank-muted {
  color: var(--muted);
  font-size: 12px;
}

.lead-pct {
  margin-left: 6px;
  font-size: 12px;
}

.drawer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.drawer-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}
</style>
