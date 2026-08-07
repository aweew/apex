<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchHotOverview, refreshHot } from '../../api/hot'
import { saveObserve } from '../../api/observe'
import { addWatchlistCodes } from '../../api/watchlist'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const data = ref(null)

const confluence = computed(() => data.value?.confluence || [])
const eastmoney = computed(() => data.value?.eastmoney || [])

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return `${n.toFixed(2)}%`
}

async function load() {
  loading.value = true
  try {
    const res = await fetchHotOverview(40)
    data.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '热点加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    const res = await refreshHot('eastmoney,baidu', 50)
    data.value = res.data?.overview || data.value
    ElMessage.success(res.data?.message || '热点已刷新')
  } catch (e) {
    ElMessage.error(e.message || '热点刷新失败')
    await load()
  } finally {
    refreshing.value = false
  }
}

async function addToObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      reason: '热点共振',
      tags: 'hot',
      priority: Math.min(5, Number(row.sourceCount) || 3),
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入观察池失败')
  }
}

async function addToWatch() {
  const items = confluence.value
    .filter((r) => r?.code)
    .slice(0, 20)
    .map((r) => ({ code: r.code, name: r.name || '' }))
  if (!items.length) {
    ElMessage.warning('没有可加入的代码')
    return
  }
  try {
    const res = await addWatchlistCodes({ groupName: '我的自选', source: 'hot', items })
    ElMessage.success(res.data?.message || `已加入 ${items.length} 只`)
  } catch (e) {
    ElMessage.error(e.message || '加入自选失败')
  }
}

onMounted(load)

defineExpose({ load, onRefresh })
</script>

<template>
  <div class="hot-panel" v-loading="loading || refreshing">
    <div class="panel-bar">
      <div>
        <strong>市场热点</strong>
        <span class="muted">{{ data?.message || '多源共振优先' }}</span>
      </div>
      <div class="ops">
        <el-button size="small" type="primary" :loading="refreshing" @click="onRefresh">快刷</el-button>
        <el-button size="small" :disabled="!confluence.length" @click="addToWatch">共振进自选</el-button>
        <el-button size="small" plain @click="router.push('/hot')">完整热点</el-button>
      </div>
    </div>

    <div v-if="data" class="snap">
      <span>东财 {{ fmtTime(data.snapshotTimes?.eastmoney) }}</span>
      <span>雪球 {{ fmtTime(data.snapshotTimes?.xueqiu) }}</span>
      <span>百度 {{ fmtTime(data.snapshotTimes?.baidu) }}</span>
      <span>共振 {{ confluence.length }}</span>
    </div>

    <el-table
      v-if="confluence.length"
      :data="confluence.slice(0, 15)"
      size="small"
      stripe
      class="hot-table"
    >
      <el-table-column prop="code" label="代码" width="88">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="120">
        <template #default="{ row }">
          <StockBoardTag :code="row.code" :market="row.market">{{ row.name || '-' }}</StockBoardTag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceCount" label="源数" width="64" />
      <el-table-column prop="pctChg" label="涨跌" width="88">
        <template #default="{ row }">
          <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="88">
        <template #default="{ row }">
          <el-button link type="warning" @click="addToObserve(row)">观察</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table
      v-else-if="eastmoney.length"
      :data="eastmoney.slice(0, 15)"
      size="small"
      stripe
    >
      <el-table-column prop="code" label="代码" width="88">
        <template #default="{ row }">
          <el-button
            v-if="row.code"
            link
            type="primary"
            @click="router.push(`/stock/${row.code}`)"
          >{{ row.code }}</el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          <StockBoardTag :code="row.code" :market="row.market">{{ row.name || '-' }}</StockBoardTag>
        </template>
      </el-table-column>
      <el-table-column prop="rankNo" label="排名" width="72" />
    </el-table>

    <div v-else class="empty-tip">
      暂无热点，可点「快刷」或去
      <el-button link type="primary" @click="router.push('/hot')">热点页</el-button>
    </div>
  </div>
</template>

<style scoped>
.panel-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}
.panel-bar strong {
  font-size: 15px;
  margin-right: 8px;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.ops {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.snap {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 10px;
}
.up {
  color: #c43d4a;
  font-weight: 650;
}
.down {
  color: #1f8a4c;
  font-weight: 650;
}
.empty-tip {
  padding: 24px;
  text-align: center;
  color: var(--muted);
  font-size: 13px;
}
</style>
