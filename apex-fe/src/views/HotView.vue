<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchHotOverview, refreshHot } from '../api/hot'
import { addWatchlistCodes } from '../api/watchlist'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const data = ref(null)
const lastLog = ref('')
const activeTab = ref('confluence')

const eastmoney = computed(() => data.value?.eastmoney || [])
const xueqiu = computed(() => data.value?.xueqiu || [])
const baidu = computed(() => data.value?.baidu || [])
const confluence = computed(() => data.value?.confluence || [])

function sourceLabel(s) {
  if (s === 'eastmoney') return '东财'
  if (s === 'xueqiu') return '雪球'
  if (s === 'baidu') return '百度'
  return s
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(2) + '%'
}

async function load() {
  loading.value = true
  try {
    const res = await fetchHotOverview(40)
    data.value = res.data
    if (!confluence.value.length && eastmoney.value.length) activeTab.value = 'eastmoney'
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function addToWatch(rows) {
  const items = (rows || [])
    .filter((r) => r?.code)
    .map((r) => ({ code: r.code, name: r.name || '' }))
  if (!items.length) {
    ElMessage.warning('没有可加入的代码')
    return
  }
  try {
    const res = await addWatchlistCodes({
      groupName: '我的自选',
      source: 'hot',
      items,
    })
    ElMessage.success(res.data?.message || `已加入 ${items.length} 只`)
  } catch (e) {
    ElMessage.error(e.message || '加入自选失败')
  }
}

async function onRefresh(sources = 'eastmoney,xueqiu,baidu') {
  refreshing.value = true
  try {
    const res = await refreshHot(sources, 50)
    data.value = res.data?.overview || data.value
    lastLog.value = res.data?.log || ''
    ElMessage.success(res.data?.message || '热点已刷新')
    if (!confluence.value.length && eastmoney.value.length) activeTab.value = 'eastmoney'
  } catch (e) {
    ElMessage.error(e.message || '刷新失败，可命令行运行 sync_hot.py')
    await load()
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <h1>市场热点</h1>
        <p>
          {{ data?.message || '聚合东财人气 / 雪球关注 / 百度热搜，多源共振优先看' }}
        </p>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="refreshing" @click="onRefresh()">刷新全部</el-button>
        <el-button :loading="refreshing" @click="onRefresh('eastmoney,baidu')">快刷(跳过雪球)</el-button>
        <el-button :disabled="!confluence.length" @click="addToWatch(confluence)">共振进自选</el-button>
        <el-button @click="load">重新加载</el-button>
        <el-button @click="router.push('/decision')">智能决策</el-button>
      </div>
    </header>

    <div class="summary" v-if="data">
      <div>
        <label>东财快照</label>
        <span>{{ fmtTime(data.snapshotTimes?.eastmoney) }}</span>
        <small v-if="!eastmoney.length" class="warn">暂无，建议快刷补齐</small>
      </div>
      <div>
        <label>雪球快照</label>
        <span>{{ fmtTime(data.snapshotTimes?.xueqiu) }}</span>
      </div>
      <div>
        <label>百度快照</label>
        <span>{{ fmtTime(data.snapshotTimes?.baidu) }}</span>
      </div>
      <div>
        <label>共振标的</label>
        <b>{{ confluence.length }}</b>
      </div>
    </div>

    <el-alert
      v-if="lastLog"
      class="hint"
      type="info"
      :closable="true"
      show-icon
      title="最近刷新日志"
      :description="lastLog"
      @close="lastLog = ''"
    />

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane :label="`多源共振 (${confluence.length})`" name="confluence">
        <el-alert
          class="hint"
          type="success"
          :closable="false"
          show-icon
          title="同时出现在 ≥2 个平台热榜的标的，优先级更高（仍只作参考，不构成投资建议）"
        />
        <el-table :data="confluence" size="small" stripe empty-text="暂无共振，请先刷新热点">
          <el-table-column prop="code" label="代码" width="100" sortable>
            <template #default="{ row }">
              <el-button v-if="row.code" link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="120" sortable />
          <el-table-column prop="sourceCount" label="源数" width="70" sortable />
          <el-table-column label="来源" min-width="140">
            <template #default="{ row }">
              <el-tag v-for="s in row.sources || []" :key="s" size="small" class="tag">{{ sourceLabel(s) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="bestRank" label="最佳排名" width="100" sortable />
          <el-table-column label="涨跌幅" width="90" sortable prop="pctChg">
            <template #default="{ row }">
              <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="price" label="现价" width="100" sortable />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!row.code" @click="addToWatch([row])">自选</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`东财/成交额 (${eastmoney.length})`" name="eastmoney">
        <el-alert
          class="hint"
          type="info"
          :closable="false"
          show-icon
          title="优先东财人气；不可用时自动降级为东财/新浪成交额榜（source 仍为 eastmoney）"
        />
        <el-table :data="eastmoney" size="small" stripe empty-text="暂无东财数据，点刷新">
          <el-table-column prop="rankNo" label="排名" width="70" sortable />
          <el-table-column prop="code" label="代码" width="100" sortable>
            <template #default="{ row }">
              <el-button v-if="row.code" link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="120" sortable />
          <el-table-column prop="price" label="现价" width="100" sortable />
          <el-table-column label="涨跌幅" width="90" sortable prop="pctChg">
            <template #default="{ row }">
              <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="heatText" label="热度" min-width="140" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`雪球关注 (${xueqiu.length})`" name="xueqiu">
        <el-alert class="hint" type="info" :closable="false" show-icon title="雪球接口较慢，完整刷新可能需 1～2 分钟" />
        <el-table :data="xueqiu" size="small" stripe empty-text="暂无雪球数据，点「刷新全部」">
          <el-table-column prop="rankNo" label="排名" width="70" sortable />
          <el-table-column prop="code" label="代码" width="100" sortable>
            <template #default="{ row }">
              <el-button v-if="row.code" link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="120" sortable />
          <el-table-column prop="price" label="现价" width="100" sortable />
          <el-table-column prop="heatScore" label="关注数" width="110" sortable />
          <el-table-column prop="heatText" label="说明" min-width="140" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`百度热搜 (${baidu.length})`" name="baidu">
        <el-table :data="baidu" size="small" stripe empty-text="暂无百度数据，点刷新">
          <el-table-column prop="rankNo" label="排名" width="70" sortable />
          <el-table-column prop="code" label="代码" width="100" sortable>
            <template #default="{ row }">
              <el-button v-if="row.code" link type="primary" @click="router.push(`/stock/${row.code}`)">
                {{ row.code }}
              </el-button>
              <span v-else class="muted">未匹配</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" width="140" sortable />
          <el-table-column label="涨跌幅" width="90" sortable prop="pctChg">
            <template #default="{ row }">
              <span :class="Number(row.pctChg) >= 0 ? 'up' : 'down'">{{ fmtPct(row.pctChg) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="heatScore" label="综合热度" width="120" sortable />
          <el-table-column prop="heatText" label="说明" min-width="140" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.summary > div {
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 12px 14px;
  box-shadow: var(--shadow-soft);
}

.summary label {
  display: block;
  color: var(--muted);
  font-size: 11px;
  margin-bottom: 6px;
}

.hint {
  margin-bottom: 10px;
}

.tag {
  margin-right: 4px;
}

.muted {
  color: var(--muted);
}

.warn {
  display: block;
  margin-top: 4px;
  color: var(--el-color-warning);
  font-size: 11px;
}

@media (max-width: 900px) {
  .summary {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
