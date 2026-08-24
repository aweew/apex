<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Download, Refresh } from '@element-plus/icons-vue'
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
import { useSessionViewState } from '../utils/viewState.js'

const router = useRouter()

const loading = ref(false)
const syncing = ref(false)
const rows = ref([])
const selected = ref([])
const keyword = ref('')
const statusFilter = ref('')
const peMax = ref('')
const onlyHasBars = ref(false)
const sortByPct = ref(true)
const industryFilter = ref('')
const filePath = ref('mx_zixuan_我的自选股列表.csv')
const groupName = ref('我的自选')
const movers = ref(null)
const corr = ref(null)

useSessionViewState('watchlist', {
  keyword,
  statusFilter,
  peMax,
  onlyHasBars,
  sortByPct,
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
  if (sortByPct.value) {
    list.sort((a, b) => Number(b.pctChg || -999) - Number(a.pctChg || -999))
  }
  return list
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
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    loading.value = false
  }
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

function statusTag(status) {
  if (status === 'OK') return 'success'
  if (status === 'STALE') return 'warning'
  return 'info'
}

function formatPct(value) {
  return value != null ? `${Number(value).toFixed(2)}%` : '--'
}

onMounted(loadList)
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
      <div class="watchlist-import">
        <label class="watchlist-field">
          <span>导入文件</span>
          <el-input v-model="filePath" placeholder="妙想导出文件名" />
        </label>
        <label class="watchlist-field watchlist-group-field">
          <span>分组</span>
          <el-input v-model="groupName" placeholder="我的自选" />
        </label>
        <el-button type="primary" :loading="loading" @click="onImport">导入自选</el-button>
      </div>
      <div class="watchlist-daily-actions">
        <div>
          <h2>日常操作</h2>
          <p>勾选后同步 K 线；行情刷新不影响已有自选。</p>
        </div>
        <div class="watchlist-action-buttons">
          <el-button type="primary" :loading="syncing" :disabled="!selected.length" @click="onSyncSelected">
            同步已选（{{ selected.length }}）
          </el-button>
          <el-button :loading="syncing" @click="onRefreshQuotes">刷新行情</el-button>
          <el-dropdown :disabled="syncing" @command="onSyncCommand">
            <el-button plain>
              数据维护<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="sync-group">同步全组 K 线</el-dropdown-item>
                <el-dropdown-item command="sync-stale">只同步过期 K 线</el-dropdown-item>
                <el-dropdown-item command="fill-bars" divided>补齐缺失 K 线</el-dropdown-item>
                <el-dropdown-item command="fill-quotes">补齐缺失行情</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-tooltip content="重新加载列表" placement="top">
            <el-button circle plain :icon="Refresh" :loading="loading" aria-label="重新加载列表" @click="loadList" />
          </el-tooltip>
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

    <section v-if="movers || corr?.codes?.length" class="watchlist-insights" aria-label="自选行情提示">
      <div v-if="movers" class="mover-summary">
        <div class="insight-title">
          <h2>行情快照</h2>
          <span>{{ movers.message }}</span>
        </div>
        <div class="mover-groups">
          <div class="mover-group gainers">
            <span>涨幅较大</span>
            <template v-if="movers.gainers?.length">
              <button v-for="row in movers.gainers" :key="row.code" type="button" @click="router.push(`/stock/${row.code}`)">
                {{ row.name || row.code }} <small>{{ formatPct(row.pctChg) }}</small>
              </button>
            </template>
            <em v-else>暂无</em>
          </div>
          <div class="mover-group losers">
            <span>跌幅较大</span>
            <template v-if="movers.losers?.length">
              <button v-for="row in movers.losers" :key="row.code" type="button" @click="router.push(`/stock/${row.code}`)">
                {{ row.name || row.code }} <small>{{ formatPct(row.pctChg) }}</small>
              </button>
            </template>
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
      </div>
      <div class="watchlist-filter-controls">
        <el-input v-model="keyword" clearable placeholder="搜索代码、名称或行业" />
        <el-select v-model="statusFilter" clearable placeholder="同步状态">
          <el-option label="正常" value="OK" />
          <el-option label="过期" value="STALE" />
          <el-option label="无 K 线" value="EMPTY" />
        </el-select>
        <el-select v-model="industryFilter" clearable filterable placeholder="行业">
          <el-option v-for="industry in industries" :key="industry" :label="industry" :value="industry" />
        </el-select>
        <el-input v-model="peMax" clearable placeholder="PE 上限" />
        <el-checkbox v-model="onlyHasBars">K 线不少于 60 天</el-checkbox>
        <el-checkbox v-model="sortByPct">按今日涨跌幅排序</el-checkbox>
      </div>
    </section>

    <el-empty
      v-if="!loading && !rows.length"
      description="还没有自选。先导入妙想 CSV/JSON，再点「同步全组」。"
    >
      <el-button type="primary" @click="onImport">导入自选</el-button>
    </el-empty>

    <el-table
      v-else
      v-loading="loading"
      :data="filtered"
      class="watchlist-table"
      height="calc(100vh - 300px)"
      @selection-change="(val) => (selected = val)"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="name" label="股票" min-width="140" sortable>
        <template #default="{ row }">
          <StockIdentity :security="row" interactive compact @select="router.push(`/stock/${row.code}`)" />
        </template>
      </el-table-column>
      <el-table-column prop="latestPrice" label="最新价" width="100" sortable />
      <el-table-column prop="pctChg" width="90" sortable>
        <template #header><TermTip term="pct_chg">今日%</TermTip></template>
        <template #default="{ row }">
          <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">
            {{ row.pctChg != null ? Number(row.pctChg).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg5" label="5日%" width="90" sortable>
        <template #default="{ row }">
          <span :class="Number(row.pctChg5) >= 0 ? 'up' : 'down'">
            {{ row.pctChg5 != null ? Number(row.pctChg5).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg20" label="20日%" width="90" sortable>
        <template #default="{ row }">
          <span :class="Number(row.pctChg20) >= 0 ? 'up' : 'down'">
            {{ row.pctChg20 != null ? Number(row.pctChg20).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="rs20VsHs300" width="90" sortable>
        <template #header><TermTip term="rs20">RS20</TermTip></template>
        <template #default="{ row }">
          <span :class="Number(row.rs20VsHs300) >= 0 ? 'up' : 'down'">
            {{ row.rs20VsHs300 != null ? Number(row.rs20VsHs300).toFixed(2) : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pctChg60" label="60日%" width="90" sortable>
        <template #default="{ row }">
          <span :class="Number(row.pctChg60) >= 0 ? 'up' : 'down'">
            {{ row.pctChg60 != null ? Number(row.pctChg60).toFixed(2) + '%' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="peTtm" width="80" sortable>
        <template #header><TermTip term="pe_ttm">PE</TermTip></template>
      </el-table-column>
      <el-table-column prop="pb" width="80" sortable>
        <template #header><TermTip term="pb">PB</TermTip></template>
      </el-table-column>
      <el-table-column prop="circMv" width="110" sortable>
        <template #header><TermTip term="circ_mv">流通市值(亿)</TermTip></template>
        <template #default="{ row }">
          {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="industry" width="120" show-overflow-tooltip>
        <template #header><TermTip term="sector">行业</TermTip></template>
      </el-table-column>
      <el-table-column prop="lastBarDate" width="120" sortable>
        <template #header><TermTip term="ohlc">最后K线</TermTip></template>
      </el-table-column>
      <el-table-column prop="barCount" width="100" sortable>
        <template #header><TermTip term="ohlc">K线条数</TermTip></template>
      </el-table-column>
      <el-table-column prop="syncStatus" label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.syncStatus)">{{ row.syncStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">K线</el-button>
          <el-button link type="warning" @click="addObserve(row)">观察</el-button>
          <el-button link @click="router.push({ path: '/backtest', query: { code: row.code } })">回测</el-button>
        </template>
      </el-table-column>
    </el-table>
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

.watchlist-import,
.watchlist-daily-actions,
.watchlist-destinations {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}

.watchlist-import {
  border-bottom: 1px solid var(--glass-border);
  background: #f8fbff;
}

.watchlist-field {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr);
  align-items: center;
  gap: 8px;
  flex: 1 1 340px;
  color: var(--slate);
  font-size: 13px;
  font-weight: 600;
}

.watchlist-group-field {
  flex-basis: 220px;
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
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

.mover-group > span {
  color: var(--muted);
  font-size: 12px;
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
  align-items: flex-start;
  margin-bottom: 12px;
}

.watchlist-filter-controls {
  display: grid;
  grid-template-columns: minmax(220px, 1.6fr) repeat(3, minmax(120px, 1fr));
  gap: 8px;
  align-items: center;
}

.watchlist-filter-controls :deep(.el-checkbox) {
  min-height: 32px;
  margin-right: 0;
}

.watchlist-table {
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-soft);
}

@media (max-width: 960px) {
  .watchlist-filter-controls {
    grid-template-columns: minmax(220px, 1.4fr) repeat(2, minmax(120px, 1fr));
  }
}

@media (max-width: 720px) {
  .watchlist-action-panel,
  .watchlist-insights,
  .watchlist-list-section {
    border-radius: 8px;
  }

  .watchlist-import,
  .watchlist-daily-actions,
  .watchlist-destinations,
  .watchlist-insights,
  .watchlist-list-section {
    padding-right: 12px;
    padding-left: 12px;
  }

  .watchlist-import {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .watchlist-field,
  .watchlist-group-field {
    grid-template-columns: 1fr;
    gap: 5px;
  }

  .watchlist-import :deep(.el-button) {
    width: 100%;
  }

  .watchlist-daily-actions {
    align-items: stretch;
  }

  .watchlist-action-buttons {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    width: 100%;
  }

  .watchlist-action-buttons :deep(.el-button),
  .watchlist-action-buttons :deep(.el-dropdown) {
    width: 100%;
    margin: 0;
  }

  .watchlist-action-buttons :deep(.el-button.is-circle) {
    grid-column: 2;
    justify-self: end;
    width: 40px;
  }

  .mover-summary,
  .mover-groups {
    grid-template-columns: 1fr;
  }

  .mover-groups {
    gap: 10px;
  }

  .watchlist-filter-controls {
    grid-template-columns: 1fr 1fr;
  }

  .watchlist-filter-controls :deep(.el-input),
  .watchlist-filter-controls :deep(.el-select) {
    min-width: 0;
  }

  .watchlist-filter-controls :deep(.el-input:first-child) {
    grid-column: 1 / -1;
  }

  .watchlist-filter-controls :deep(.el-checkbox) {
    grid-column: 1 / -1;
  }

  .watchlist-table {
    height: calc(100vh - 360px) !important;
  }
}
</style>
