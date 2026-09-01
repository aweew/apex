<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Download, Filter, MoreFilled, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import {
  fetchWatchlist,
  fillQuotes,
  importWatchlist,
  refreshQuotes,
  watchlistCorrelation,
  watchlistMovers,
} from '../api/watchlist'
import { fillWatchlistBars, syncBars, syncBarsGroup, syncStaleBars } from '../api/bars'
import { saveObserve } from '../api/observe'
import { buildApiUrl } from '../api/baseUrl'
import { resolveActionColumnVisible } from '../utils/responsiveTable.js'
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()

const loading = ref(false)
const syncing = ref(false)
const rows = ref([])
const selected = ref([])
const importDialogVisible = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const peMax = ref('')
const onlyHasBars = ref(false)
const sortMode = ref('pctChgDesc')
const industryFilter = ref('')
const filePath = ref('mx_zixuan_我的自选股列表.csv')
const groupName = ref('我的自选')
const movers = ref(null)
const corr = ref(null)
const viewportWidth = ref(typeof window === 'undefined' ? 1024 : window.innerWidth)
const filtersExpanded = ref(false)
const currentPage = ref(1)
const tableSort = ref({ prop: '', order: '' })
const watchlistTableRef = ref(null)

const isMobileViewport = computed(() => viewportWidth.value <= 820)
const showActionColumn = computed(() => resolveActionColumnVisible(viewportWidth.value))
const pageSize = computed(() => (isMobileViewport.value ? 30 : 50))
const activeFilterCount = computed(() => {
  let count = 0
  if (statusFilter.value) count += 1
  if (industryFilter.value) count += 1
  if (peMax.value !== '') count += 1
  if (onlyHasBars.value) count += 1
  return count
})

useSessionViewState('watchlist', {
  keyword,
  statusFilter,
  peMax,
  onlyHasBars,
  sortMode,
  industryFilter,
})

const industries = computed(() => {
  const set = new Set()
  for (const row of rows.value) {
    if (row.industry) set.add(row.industry)
  }
  return [...set].sort()
})

const filtered = computed(() => {
  let list = [...rows.value]
  const q = keyword.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (r) =>
        String(r.code || '').includes(q) ||
        String(r.name || '').toLowerCase().includes(q) ||
        String(r.industry || '').includes(q),
    )
  }
  if (statusFilter.value) {
    list = list.filter((r) => r.syncStatus === statusFilter.value)
  }
  if (industryFilter.value) {
    list = list.filter((r) => r.industry === industryFilter.value)
  }
  if (peMax.value !== '' && !Number.isNaN(Number(peMax.value))) {
    const max = Number(peMax.value)
    list = list.filter((r) => r.peTtm != null && Number(r.peTtm) > 0 && Number(r.peTtm) <= max)
  }
  if (onlyHasBars.value) {
    list = list.filter((r) => (r.barCount || 0) >= 60)
  }
  if (sortMode.value === 'pctChgDesc') {
    list.sort((a, b) => Number(b.pctChg || -999) - Number(a.pctChg || -999))
  }
  return list
})

const sortedRows = computed(() => {
  if (!tableSort.value.prop || !tableSort.value.order) return filtered.value
  const sorted = [...filtered.value]
  const direction = tableSort.value.order === 'ascending' ? 1 : -1
  sorted.sort((leftRow, rightRow) => {
    const leftValue = leftRow[tableSort.value.prop]
    const rightValue = rightRow[tableSort.value.prop]
    const leftEmpty = leftValue == null || leftValue === ''
    const rightEmpty = rightValue == null || rightValue === ''
    if (leftEmpty && rightEmpty) return 0
    if (leftEmpty) return 1
    if (rightEmpty) return -1
    const leftNumber = Number(leftValue)
    const rightNumber = Number(rightValue)
    if (!Number.isNaN(leftNumber) && !Number.isNaN(rightNumber)) {
      return (leftNumber - rightNumber) * direction
    }
    return String(leftValue).localeCompare(String(rightValue), 'zh-CN') * direction
  })
  return sorted
})

const totalPages = computed(() => Math.max(1, Math.ceil(sortedRows.value.length / pageSize.value)))
const pagedRows = computed(() => {
  const safePage = Math.min(currentPage.value, totalPages.value)
  const start = (safePage - 1) * pageSize.value
  return sortedRows.value.slice(start, start + pageSize.value)
})

watch([keyword, statusFilter, industryFilter, peMax, onlyHasBars, sortMode, pageSize], async () => {
  currentPage.value = 1
  selected.value = []
  await nextTick()
  watchlistTableRef.value?.clearSelection()
})

async function loadList() {
  loading.value = true
  try {
    const [res, mv, cr] = await Promise.all([
      fetchWatchlist(groupName.value),
      watchlistMovers(groupName.value, 5, 8),
      watchlistCorrelation(groupName.value, 6, 60),
    ])
    rows.value = res.data || []
    currentPage.value = 1
    movers.value = mv.data || null
    corr.value = cr.data || null
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: '自选关注',
      tags: 'watchlist',
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function onImport() {
  loading.value = true
  try {
    const res = await importWatchlist({
      filePath: filePath.value,
      groupName: groupName.value,
    })
    ElMessage.success(res.data.message || `导入 ${res.data.importCount} 条`)
    await loadList()
    importDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    loading.value = false
  }
}

function openImportDialog() {
  importDialogVisible.value = true
}

async function onSyncSelected() {
  const codes = selected.value.map((r) => r.code)
  if (!codes.length) {
    ElMessage.warning('请先勾选股票')
    return
  }
  syncing.value = true
  try {
    const res = await syncBars({ codes: codes.slice(0, 40) })
    const data = res.data
    ElMessage.success(
      `同步完成：成功 ${data.successCount}，失败 ${data.failCount}，K线 ${data.barCount}`,
    )
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function onSyncGroup() {
  syncing.value = true
  try {
    const res = await syncBarsGroup(groupName.value)
    const data = res.data
    ElMessage.success(
      `分组同步：成功 ${data.successCount}，失败 ${data.failCount}，K线 ${data.barCount}（最多80只）`,
    )
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '分组同步失败')
  } finally {
    syncing.value = false
  }
}

async function onRefreshQuotes() {
  syncing.value = true
  try {
    const res = await refreshQuotes(groupName.value, 40, true)
    ElMessage.success(`行情刷新：成功 ${res.data.successCount}，失败 ${res.data.failCount}`)
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '刷新行情失败')
  } finally {
    syncing.value = false
  }
}

async function onFillQuotes() {
  syncing.value = true
  try {
    const res = await fillQuotes(groupName.value, 3, 40)
    ElMessage.success(`${res.data.message}（${res.data.rounds} 轮）`)
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '补齐行情失败')
  } finally {
    syncing.value = false
  }
}

async function onFillBars() {
  syncing.value = true
  try {
    const res = await fillWatchlistBars(groupName.value, 2, 40)
    ElMessage.success(
      `K线补齐 ${res.data.rounds} 轮 · 成功 ${res.data.totalSuccess} · ${res.data.message}`,
    )
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '补齐K线失败')
  } finally {
    syncing.value = false
  }
}

async function onSyncStale() {
  syncing.value = true
  try {
    const res = await syncStaleBars(groupName.value, 40)
    const data = res.data
    ElMessage.success(`过期同步：成功 ${data.successCount}，失败 ${data.failCount}`)
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function onSyncCommand(command) {
  if (command === 'import-watchlist') {
    openImportDialog()
    return
  }
  if (command === 'sync-group') {
    await onSyncGroup()
    return
  }
  if (command === 'sync-stale') {
    await onSyncStale()
    return
  }
  if (command === 'fill-bars') {
    await onFillBars()
    return
  }
  if (command === 'fill-quotes') {
    await onFillQuotes()
  }
}

async function handleWatchlistRowAction(command, row) {
  if (command === 'detail') {
    await router.push(`/stock/${row.code}`)
    return
  }
  if (command === 'observe') {
    await addObserve(row)
    return
  }
  if (command === 'backtest') {
    await router.push({ path: '/backtest', query: { code: row.code } })
  }
}

function statusTag(status) {
  if (status === 'OK') return 'success'
  if (status === 'STALE') return 'warning'
  return 'info'
}

function formatPct(value) {
  return value != null ? `${Number(value).toFixed(2)}%` : '--'
}

function syncViewportWidth() {
  viewportWidth.value = window.innerWidth
}

function clearAdvancedFilters() {
  statusFilter.value = ''
  industryFilter.value = ''
  peMax.value = ''
  onlyHasBars.value = false
}

function handleTableSort({ prop, order }) {
  tableSort.value = { prop: prop || '', order: order || '' }
  currentPage.value = 1
}

function clearTableSort() {
  tableSort.value = { prop: '', order: '' }
}

async function changePage(page) {
  currentPage.value = page
  await nextTick()
  document.getElementById('watchlist-table-start')?.scrollIntoView({ block: 'start', behavior: 'auto' })
}

onMounted(() => {
  syncViewportWidth()
  window.addEventListener('resize', syncViewportWidth)
  loadList()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Watchlist</p>
        <h1>自选股</h1>
        <p>管理关注标的，先看行情，再进入决策或观察。</p>
      </div>
    </header>

    <section class="watchlist-action-panel" aria-label="自选管理">
      <div class="watchlist-daily-actions">
        <div>
          <h2>日常操作</h2>
          <p>勾选后同步 K 线；行情刷新不影响已有自选。</p>
        </div>
        <div class="watchlist-action-buttons">
          <el-button :loading="syncing" @click="onRefreshQuotes">刷新行情</el-button>
          <el-button type="primary" :loading="syncing" :disabled="!selected.length" @click="onSyncSelected">
            {{ isMobileViewport ? `同步（${selected.length}）` : `同步已选（${selected.length}）` }}
          </el-button>
          <el-dropdown :disabled="syncing" @command="onSyncCommand">
            <el-button plain>
              数据维护<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="import-watchlist">导入自选</el-dropdown-item>
                <el-dropdown-item command="sync-group" divided>同步全组 K 线</el-dropdown-item>
                <el-dropdown-item command="sync-stale">只同步过期 K 线</el-dropdown-item>
                <el-dropdown-item command="fill-bars" divided>补齐缺失 K 线</el-dropdown-item>
                <el-dropdown-item command="fill-quotes">补齐缺失行情</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
      <div class="watchlist-destinations" aria-label="关联页面">
        <span>查看</span>
        <el-link type="primary" @click="router.push('/decision')">决策</el-link>
        <el-link type="primary" @click="router.push('/observe')">观察池</el-link>
        <el-link type="primary" @click="router.push('/pipeline')">流水线</el-link>
        <el-link
          :href="buildApiUrl(`/api/export/watchlist?groupName=${encodeURIComponent(groupName)}`)"
          target="_blank"
        ><el-icon><Download /></el-icon>导出</el-link>
      </div>
    </section>

    <el-dialog v-model="importDialogVisible" title="导入自选" width="min(520px, calc(100vw - 32px))">
      <el-form label-position="top">
        <el-form-item label="导入文件">
          <el-input v-model="filePath" placeholder="妙想导出文件名" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="groupName" placeholder="我的自选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="onImport">导入自选</el-button>
      </template>
    </el-dialog>

    <section v-if="movers || corr?.codes?.length" class="watchlist-insights" aria-label="自选行情提示">
      <div v-if="movers" class="mover-summary">
        <div class="insight-title">
          <h2>行情快照</h2>
          <span>{{ movers.message }}</span>
        </div>
        <div class="mover-groups">
          <div class="mover-group gainers">
            <span>涨幅较大</span>
            <div v-if="movers.gainers?.length" class="mover-items">
              <button v-for="row in movers.gainers" :key="row.code" type="button" @click="router.push(`/stock/${row.code}`)">
                {{ row.name || row.code }} <small>{{ formatPct(row.pctChg) }}</small>
              </button>
            </div>
            <em v-else>暂无</em>
          </div>
          <div class="mover-group losers">
            <span>跌幅较大</span>
            <div v-if="movers.losers?.length" class="mover-items">
              <button v-for="row in movers.losers" :key="row.code" type="button" @click="router.push(`/stock/${row.code}`)">
                {{ row.name || row.code }} <small>{{ formatPct(row.pctChg) }}</small>
              </button>
            </div>
            <em v-else>暂无</em>
          </div>
        </div>
      </div>

      <el-collapse v-if="corr?.codes?.length" class="correlation-collapse">
        <el-collapse-item name="correlation" title="同涨同跌风险">
          <p class="correlation-note">{{ corr.message }}。数值越接近 1，表示近 {{ corr.sampleDays || 60 }} 个交易日越容易同时涨跌。</p>
          <div v-if="corr.matrix?.length" class="correlation-scroll">
            <el-table
              :data="corr.codes.map((code, index) => ({ code, name: corr.names[index], row: corr.matrix[index] }))"
              size="small"
              class="correlation-table"
            >
              <el-table-column prop="code" label="相关系数" width="100" />
              <el-table-column v-for="(code, index) in corr.codes" :key="code" :label="code" width="86">
                <template #default="{ row }">
                  <span :class="{ 'correlation-high': Number(row.row[index]) > 0.7 }">{{ row.row[index] }}</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-collapse-item>
      </el-collapse>
    </section>

    <section class="watchlist-list-section">
      <div class="watchlist-list-heading">
        <div>
          <h2>自选列表</h2>
          <p>共 {{ filtered.length }} / {{ rows.length }} 只</p>
        </div>
        <el-tooltip content="重新加载列表" placement="top">
          <el-button
            class="watchlist-reload-action"
            circle
            plain
            :icon="Refresh"
            :loading="loading"
            aria-label="重新加载列表"
            @click="loadList"
          />
        </el-tooltip>
      </div>
      <div class="watchlist-filter-controls">
        <div class="watchlist-primary-filter-row">
          <el-input
            v-model="keyword"
            class="watchlist-keyword-filter"
            clearable
            :prefix-icon="Search"
            inputmode="search"
            enterkeyhint="search"
            aria-label="搜索自选股票"
            placeholder="搜索代码、名称或行业"
          />
          <el-button
            class="watchlist-filter-toggle"
            :class="{ 'is-active': filtersExpanded || activeFilterCount > 0 }"
            :icon="Filter"
            :aria-expanded="filtersExpanded"
            aria-controls="watchlist-advanced-filters"
            @click="filtersExpanded = !filtersExpanded"
          >
            筛选<span v-if="activeFilterCount" class="watchlist-filter-count">{{ activeFilterCount }}</span>
            <el-icon class="watchlist-filter-arrow" :class="{ 'is-expanded': filtersExpanded }"><ArrowDown /></el-icon>
          </el-button>
        </div>
        <el-collapse-transition>
          <div
            v-show="!isMobileViewport || filtersExpanded"
            id="watchlist-advanced-filters"
            class="watchlist-advanced-filters"
          >
            <el-select v-model="statusFilter" class="watchlist-status-filter" clearable aria-label="数据状态" placeholder="数据状态">
              <el-option label="正常" value="OK" />
              <el-option label="过期" value="STALE" />
              <el-option label="无 K 线" value="EMPTY" />
            </el-select>
            <el-select v-model="industryFilter" class="watchlist-industry-filter" clearable filterable aria-label="行业" placeholder="行业">
              <el-option v-for="industry in industries" :key="industry" :label="industry" :value="industry" />
            </el-select>
            <el-input
              v-model="peMax"
              class="watchlist-pe-filter"
              clearable
              inputmode="decimal"
              aria-label="市盈率上限"
              placeholder="PE 上限"
            />
            <el-checkbox v-model="onlyHasBars" class="watchlist-data-filter">K 线不少于 60 天</el-checkbox>
            <el-select v-model="sortMode" class="watchlist-sort-control" aria-label="列表排序" @change="clearTableSort">
              <el-option label="今日涨跌幅降序" value="pctChgDesc" />
              <el-option label="默认顺序" value="default" />
            </el-select>
            <el-button
              v-if="activeFilterCount"
              class="watchlist-filter-reset"
              text
              :icon="RefreshLeft"
              @click="clearAdvancedFilters"
            >重置</el-button>
          </div>
        </el-collapse-transition>
      </div>
    </section>

    <el-empty
      v-if="!loading && !rows.length"
      description="还没有自选。先导入妙想 CSV/JSON，再点「同步全组」。"
    >
      <el-button type="primary" @click="openImportDialog">导入自选</el-button>
    </el-empty>

    <el-table
      v-else
      ref="watchlistTableRef"
      v-loading="loading"
      id="watchlist-table-start"
      :data="pagedRows"
      row-key="code"
      class="watchlist-table"
      @selection-change="(val) => (selected = val)"
      @sort-change="handleTableSort"
    >
      <el-table-column type="selection" width="48" reserve-selection />
      <el-table-column prop="name" label="股票" min-width="140" sortable="custom">
        <template #default="{ row }">
          <div class="watchlist-stock-cell">
            <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
            <el-dropdown
              v-if="isMobileViewport"
              class="watchlist-row-actions-trigger"
              trigger="click"
              @command="handleWatchlistRowAction($event, row)"
            >
              <el-button text circle :icon="MoreFilled" aria-label="更多股票操作" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="detail">K 线</el-dropdown-item>
                  <el-dropdown-item command="observe">加入观察</el-dropdown-item>
                  <el-dropdown-item command="backtest">回测</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="latestPrice" label="最新价" width="100" sortable="custom" />
      <el-table-column prop="pctChg" width="90" sortable="custom">
        <template #header><TermTip term="pct_chg">今日%</TermTip></template>
        <template #default="{ row }">
          <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">
            {{ row.pctChg != null ? Number(row.pctChg).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg5" label="5日%" width="90" sortable="custom">
        <template #default="{ row }">
          <span :class="Number(row.pctChg5) >= 0 ? 'up' : 'down'">
            {{ row.pctChg5 != null ? Number(row.pctChg5).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg20" label="20日%" width="90" sortable="custom">
        <template #default="{ row }">
          <span :class="Number(row.pctChg20) >= 0 ? 'up' : 'down'">
            {{ row.pctChg20 != null ? Number(row.pctChg20).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="rs20VsHs300" width="90" sortable="custom">
        <template #header><TermTip term="rs20">RS20</TermTip></template>
        <template #default="{ row }">
          <span :class="Number(row.rs20VsHs300) >= 0 ? 'up' : 'down'">
            {{ row.rs20VsHs300 != null ? Number(row.rs20VsHs300).toFixed(2) : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg60" label="60日%" width="90" sortable="custom">
        <template #default="{ row }">
          <span :class="Number(row.pctChg60) >= 0 ? 'up' : 'down'">
            {{ row.pctChg60 != null ? Number(row.pctChg60).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="peTtm" width="80" sortable="custom">
        <template #header><TermTip term="pe_ttm">PE</TermTip></template>
      </el-table-column>
      <el-table-column prop="pb" width="80" sortable="custom">
        <template #header><TermTip term="pb">PB</TermTip></template>
      </el-table-column>
      <el-table-column prop="circMv" width="128" sortable="custom" label-class-name="watchlist-circ-mv-header">
        <template #header><TermTip term="circ_mv">流通市值(亿)</TermTip></template>
        <template #default="{ row }">
          {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="industry" width="120" show-overflow-tooltip>
        <template #header><TermTip term="sector">行业</TermTip></template>
      </el-table-column>
      <el-table-column prop="lastBarDate" width="120" sortable="custom">
        <template #header><TermTip term="ohlc">最后K线</TermTip></template>
      </el-table-column>
      <el-table-column prop="barCount" width="100" sortable="custom">
        <template #header><TermTip term="ohlc">K线条数</TermTip></template>
      </el-table-column>
      <el-table-column prop="syncStatus" label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.syncStatus)">{{ row.syncStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="showActionColumn" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="watchlist-desktop-row-actions">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">K线</el-button>
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
            <el-button link @click="router.push({ path: '/backtest', query: { code: row.code } })">回测</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="filtered.length > pageSize" class="watchlist-pagination" aria-label="自选列表分页">
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <el-pagination
        :current-page="currentPage"
        :page-size="pageSize"
        :total="filtered.length"
        :layout="isMobileViewport ? 'prev, next' : 'prev, pager, next'"
        :pager-count="5"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.watchlist-action-panel,
.watchlist-insights,
.watchlist-list-section {
  margin-bottom: 16px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: #fff;
  box-shadow: var(--shadow-soft);
}

.watchlist-action-panel {
  overflow: hidden;
}

.watchlist-daily-actions,
.watchlist-destinations {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}

.watchlist-daily-actions {
  justify-content: space-between;
  flex-wrap: wrap;
}

.watchlist-daily-actions h2,
.insight-title h2,
.watchlist-list-heading h2 {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
  line-height: 1.35;
}

.watchlist-daily-actions p,
.watchlist-list-heading p,
.correlation-note {
  margin: 3px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.watchlist-action-buttons,
.watchlist-destinations {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.watchlist-destinations {
  min-height: 40px;
  padding-top: 8px;
  padding-bottom: 8px;
  border-top: 1px solid var(--glass-border);
  color: var(--muted);
  font-size: 12px;
}

.watchlist-destinations :deep(.el-link) {
  gap: 4px;
  font-size: 12px;
}

.watchlist-insights {
  padding: 14px 16px;
}

.mover-summary {
  display: grid;
  grid-template-columns: minmax(150px, 0.45fr) minmax(0, 1fr);
  gap: 16px;
  align-items: center;
}

.insight-title span {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}

.mover-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.mover-group {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  align-items: start;
  gap: 6px;
  min-width: 0;
}

.mover-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.mover-group > span {
  color: var(--muted);
  font-size: 12px;
  line-height: 26px;
  white-space: nowrap;
}

.mover-group button {
  max-width: 132px;
  overflow: hidden;
  border: 0;
  border-radius: 4px;
  background: #f3f6fa;
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  line-height: 26px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mover-group button:hover,
.mover-group button:focus-visible {
  background: #e7f1ff;
  outline: none;
}

.mover-group small {
  margin-left: 2px;
  color: #d24a23;
  font-size: 11px;
}

.losers small {
  color: #17804a;
}

.mover-group em {
  color: var(--muted);
  font-size: 12px;
  font-style: normal;
}

.correlation-collapse {
  margin-top: 14px;
  border-top: 1px solid var(--glass-border);
}

.correlation-collapse :deep(.el-collapse-item__header) {
  height: 38px;
  border: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 600;
}

.correlation-collapse :deep(.el-collapse-item__wrap) {
  border: 0;
}

.correlation-note {
  margin: 0 0 10px;
}

.correlation-scroll {
  overflow-x: auto;
}

.correlation-table {
  min-width: 560px;
}

.correlation-high {
  color: #c54a20;
  font-weight: 650;
}

.watchlist-list-section {
  padding: 14px 16px;
}

.watchlist-list-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.watchlist-list-heading :deep(.watchlist-reload-action) {
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  flex: 0 0 auto;
}

.watchlist-filter-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.watchlist-primary-filter-row,
.watchlist-advanced-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.watchlist-keyword-filter {
  width: min(300px, 100%);
}

.watchlist-filter-toggle {
  display: none;
}

.watchlist-filter-count {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  margin-left: 2px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  line-height: 1;
}

.watchlist-filter-arrow {
  margin-left: 2px;
  transition: transform 160ms ease;
}

.watchlist-filter-arrow.is-expanded {
  transform: rotate(180deg);
}

.watchlist-status-filter,
.watchlist-sort-control {
  width: 154px;
}

.watchlist-industry-filter {
  width: 152px;
}

.watchlist-pe-filter {
  width: 104px;
}

.watchlist-data-filter {
  min-height: 32px;
  margin-right: 0;
}

.watchlist-table {
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-soft);
  scroll-margin-top: 72px;
}

.watchlist-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 8px 4px 0;
  color: var(--muted);
  font-size: 12px;
}

.watchlist-pagination > span {
  flex: 0 0 auto;
  white-space: nowrap;
}

.watchlist-table :deep(th.watchlist-circ-mv-header > .cell) {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.watchlist-table :deep(th.watchlist-circ-mv-header .caret-wrapper) {
  flex: 0 0 24px;
}

.watchlist-stock-cell,
.watchlist-desktop-row-actions {
  display: flex;
  align-items: center;
}

.watchlist-stock-cell {
  justify-content: space-between;
  gap: 4px;
  min-width: 0;
}

.watchlist-stock-cell > :first-child {
  min-width: 0;
}

.watchlist-row-actions-trigger {
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
}

.watchlist-row-actions-trigger :deep(.el-button) {
  width: 44px;
  height: 44px;
  margin: 0;
  border: 0;
}

.watchlist-desktop-row-actions {
  flex-wrap: nowrap;
  white-space: nowrap;
}

.watchlist-desktop-row-actions :deep(.el-button) {
  min-width: auto;
  margin-left: 12px;
  padding: 0;
  white-space: nowrap;
}

.watchlist-desktop-row-actions :deep(.el-button:first-child) {
  margin-left: 0;
}

@media (max-width: 720px) {
  .watchlist-action-panel,
  .watchlist-insights,
  .watchlist-list-section {
    border-radius: 8px;
  }

  .watchlist-daily-actions,
  .watchlist-destinations,
  .watchlist-insights,
  .watchlist-list-section {
    padding-right: 12px;
    padding-left: 12px;
  }

  .watchlist-daily-actions {
    align-items: stretch;
  }

  .watchlist-action-buttons {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
    max-width: 360px;
    gap: 6px;
  }

  .watchlist-action-buttons :deep(.el-button),
  .watchlist-action-buttons :deep(.el-dropdown) {
    width: 100%;
    margin: 0;
  }

  .watchlist-action-buttons :deep(.el-button) {
    min-height: 44px;
    padding-right: 6px;
    padding-left: 6px;
    font-size: 13px;
    white-space: nowrap;
  }

  .watchlist-list-heading :deep(.watchlist-reload-action) {
    width: 44px;
    height: 44px;
    padding: 0;
  }

  .mover-summary,
  .mover-groups {
    grid-template-columns: 1fr;
  }

  .mover-groups {
    gap: 10px;
  }

  .watchlist-filter-controls {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .watchlist-primary-filter-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
  }

  .watchlist-filter-toggle {
    display: inline-flex;
    min-width: 88px;
    min-height: 44px;
    margin: 0;
    padding: 0 10px;
  }

  .watchlist-filter-toggle.is-active {
    border-color: rgba(0, 113, 227, 0.35);
    background: rgba(0, 113, 227, 0.08);
    color: var(--accent);
  }

  .watchlist-advanced-filters {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 8px;
    padding: 10px;
    border: 1px solid var(--glass-border);
    border-radius: 6px;
    background: #f7f9fc;
  }

  .watchlist-keyword-filter,
  .watchlist-status-filter,
  .watchlist-industry-filter,
  .watchlist-pe-filter,
  .watchlist-sort-control {
    width: 100%;
  }

  .watchlist-keyword-filter :deep(.el-input__wrapper),
  .watchlist-advanced-filters :deep(.el-input__wrapper),
  .watchlist-advanced-filters :deep(.el-select__wrapper) {
    min-height: 44px;
  }

  .watchlist-keyword-filter :deep(.el-input__inner),
  .watchlist-advanced-filters :deep(.el-input__inner),
  .watchlist-advanced-filters :deep(.el-select__input) {
    font-size: 16px;
  }

  .watchlist-sort-control {
    grid-column: 1 / -1;
  }

  .watchlist-data-filter {
    min-height: 44px;
    margin-left: 0;
  }

  .watchlist-filter-reset {
    justify-self: end;
    min-height: 36px;
    grid-column: 1 / -1;
    padding: 0 6px;
  }

  .watchlist-pagination {
    justify-content: space-between;
    min-height: 56px;
    padding-right: 0;
    padding-left: 0;
  }

  .watchlist-pagination :deep(.el-pagination) {
    gap: 8px;
  }

  .watchlist-pagination :deep(.btn-prev),
  .watchlist-pagination :deep(.btn-next) {
    width: 44px;
    min-width: 44px;
    height: 44px;
    margin: 0;
  }
}
</style>
