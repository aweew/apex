<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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

function statusTag(status) {
  if (status === 'OK') return 'success'
  if (status === 'STALE') return 'warning'
  return 'info'
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Watchlist</p>
        <h1>自选股</h1>
        <p>导入 → 同步日线 → 看行情 → 进决策 / 观察池 / K线</p>
      </div>
      <div class="actions">
        <el-input v-model="filePath" style="width: 280px" placeholder="妙想导出文件名" />
        <el-input v-model="groupName" style="width: 120px" placeholder="分组" />
        <el-button type="primary" :loading="loading" @click="onImport">导入自选</el-button>
        <el-button type="success" :loading="syncing" @click="onSyncSelected">同步勾选</el-button>
        <el-button type="warning" :loading="syncing" @click="onSyncGroup">同步全组K线</el-button>
        <el-button type="warning" plain :loading="syncing" @click="onSyncStale">只同步过期</el-button>
        <el-button :loading="syncing" @click="onFillBars">多轮补齐K线</el-button>
        <el-button :loading="syncing" @click="onRefreshQuotes">刷新行情</el-button>
        <el-button :loading="syncing" @click="onFillQuotes">多轮补齐行情</el-button>
        <el-button plain @click="router.push('/decision')">决策</el-button>
        <el-button plain @click="router.push('/observe')">观察池</el-button>
        <el-button plain @click="router.push('/pipeline')">流水线</el-button>
        <el-link
          type="primary"
          :href="`http://127.0.0.1:8080/apex/api/export/watchlist?groupName=${encodeURIComponent(groupName)}`"
          target="_blank"
        >导出</el-link>
        <el-button text :loading="loading" @click="loadList">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="movers"
      :title="`${movers.message}${movers.gainers?.length ? ' · 涨 ' + movers.gainers.map((g) => g.code).join('/') : ''}${movers.losers?.length ? ' · 跌 ' + movers.losers.map((g) => g.code).join('/') : ''}`"
      :type="(movers.losers || []).length ? 'warning' : 'success'"
      :closable="false"
      style="margin-bottom: 10px"
    />
    <el-alert
      v-if="corr?.codes?.length"
      :title="`${corr.message} · ${corr.codes.join(' / ')}`"
      type="info"
      :closable="false"
      style="margin-bottom: 10px"
    />
    <el-table
      v-if="corr?.matrix?.length"
      :data="corr.codes.map((code, i) => ({ code, name: corr.names[i], row: corr.matrix[i] }))"
      size="small"
      style="margin-bottom: 12px"
    >
      <el-table-column prop="code" label="相关" width="90" />
      <el-table-column
        v-for="(c, j) in corr.codes"
        :key="c"
        :label="c"
        width="80"
      >
        <template #default="{ row }">
          <span :style="{ color: Number(row.row[j]) > 0.7 ? '#c45c26' : Number(row.row[j]) < 0 ? '#2e7d32' : '#333' }">
            {{ row.row[j] }}
          </span>
        </template>
      </el-table-column>
    </el-table>

    <div class="toolbar-bar">
      <el-input v-model="keyword" clearable placeholder="搜索代码/名称/行业" style="width: 220px" />
      <el-select v-model="statusFilter" clearable placeholder="同步状态" style="width: 140px">
        <el-option label="正常" value="OK" />
        <el-option label="过期" value="STALE" />
        <el-option label="无K线" value="EMPTY" />
      </el-select>
      <el-select v-model="industryFilter" clearable filterable placeholder="行业" style="width: 150px">
        <el-option v-for="ind in industries" :key="ind" :label="ind" :value="ind" />
      </el-select>
      <el-input v-model="peMax" clearable placeholder="PE上限" style="width: 100px" />
      <el-checkbox v-model="onlyHasBars">K线≥60</el-checkbox>
      <el-checkbox v-model="sortByPct">按涨跌幅排序</el-checkbox>
      <span class="hint">共 {{ filtered.length }} / {{ rows.length }} 只</span>
    </div>

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
      height="calc(100vh - 210px)"
      @selection-change="(val) => (selected = val)"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="code" label="代码" width="100" sortable>
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="120">
        <template #default="{ row }">
          <el-button link @click="router.push(`/stock/${row.code}`)">{{ row.name || '-' }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="latestPrice" label="最新价" width="100" sortable />
      <el-table-column prop="pctChg" label="今日%" width="90" sortable>
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
      <el-table-column prop="rs20VsHs300" label="RS20" width="90" sortable>
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
      <el-table-column prop="peTtm" label="PE" width="80" sortable />
      <el-table-column prop="pb" label="PB" width="80" sortable />
      <el-table-column prop="circMv" label="流通市值(亿)" width="110" sortable>
        <template #default="{ row }">
          {{ row.circMv != null ? (Number(row.circMv) / 1e8).toFixed(1) : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="industry" label="行业" width="120" show-overflow-tooltip />
      <el-table-column prop="lastBarDate" label="最后K线" width="120" sortable />
      <el-table-column prop="barCount" label="K线条数" width="100" sortable />
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
.hint {
  color: var(--slate);
  font-size: 13px;
}
</style>
