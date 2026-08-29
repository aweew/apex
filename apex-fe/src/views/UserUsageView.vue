<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { fetchUserUsageOverview } from '../api/usage'

const loading = ref(false)
const periodDays = ref(30)
const overview = ref({
  totalUsers: 0,
  enabledUsers: 0,
  activeUsers: 0,
  newUsers: 0,
  totalVisits: 0,
  activeRate: 0,
  trend: [],
  modules: [],
  users: [],
})
const trendChartRef = ref(null)
const periodOptions = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]
let trendChart
let resizeObserver

const metrics = computed(() => [
  { label: '用户总数', value: overview.value.totalUsers || 0, note: `启用 ${overview.value.enabledUsers || 0}` },
  { label: '活跃用户', value: overview.value.activeUsers || 0, note: `活跃率 ${formatPercent(overview.value.activeRate)}` },
  { label: '新增用户', value: overview.value.newUsers || 0, note: `${periodDays.value} 天内注册` },
  { label: '访问次数', value: overview.value.totalVisits || 0, note: '登录与页面访问' },
])

function formatPercent(value) {
  const number = Number(value || 0)
  return `${number.toFixed(number % 1 === 0 ? 0 : 1)}%`
}

function formatTime(value) {
  if (!value) return '暂无记录'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatShortDate(value) {
  if (!value) return '--'
  return String(value).slice(5)
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value, null, { renderer: 'canvas' })
  const trend = overview.value.trend || []
  trendChart.setOption({
    animationDuration: 360,
    color: ['#0a6ed1', '#16875d'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#ffffff',
      borderColor: '#d8dee8',
      textStyle: { color: '#172033' },
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#5f6b7a', fontSize: 12 },
    },
    grid: { left: 42, right: 18, top: 38, bottom: 28 },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: trend.map((item) => formatShortDate(item.date)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#d8dee8' } },
      axisLabel: { color: '#7a8696', hideOverlap: true },
    },
    yAxis: [
      {
        type: 'value',
        minInterval: 1,
        name: '用户',
        nameTextStyle: { color: '#7a8696', align: 'right' },
        axisLabel: { color: '#7a8696' },
        splitLine: { lineStyle: { color: '#edf0f4' } },
      },
      {
        type: 'value',
        minInterval: 1,
        name: '访问',
        nameTextStyle: { color: '#7a8696', align: 'left' },
        axisLabel: { color: '#7a8696' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '活跃用户',
        type: 'line',
        smooth: true,
        showSymbol: trend.length <= 14,
        symbolSize: 6,
        data: trend.map((item) => Number(item.activeUsers || 0)),
        lineStyle: { width: 2.5 },
        areaStyle: { color: 'rgba(10, 110, 209, 0.08)' },
      },
      {
        name: '访问次数',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 16,
        data: trend.map((item) => Number(item.visits || 0)),
        itemStyle: { borderRadius: [2, 2, 0, 0], opacity: 0.72 },
      },
    ],
  }, true)
}

async function loadOverview() {
  loading.value = true
  try {
    const response = await fetchUserUsageOverview(periodDays.value)
    overview.value = response.data || overview.value
    await nextTick()
    renderTrendChart()
  } catch (error) {
    ElMessage.error(error.message || '加载用户使用情况失败')
  } finally {
    loading.value = false
  }
}

watch(periodDays, loadOverview)

onMounted(() => {
  loadOverview()
  resizeObserver = new ResizeObserver(() => trendChart?.resize())
  if (trendChartRef.value) resizeObserver.observe(trendChartRef.value)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  trendChart?.dispose()
  trendChart = undefined
})
</script>

<template>
  <main class="page usage-page" v-loading="loading">
    <header class="header usage-header">
      <div>
        <p class="eyebrow">Administration</p>
        <h1>用户使用情况</h1>
        <p>掌握成员活跃度与产品使用节奏</p>
      </div>
      <div class="header-actions">
        <el-segmented v-model="periodDays" :options="periodOptions" aria-label="统计周期" />
        <el-button :icon="Refresh" circle aria-label="刷新统计" title="刷新统计" @click="loadOverview" />
      </div>
    </header>

    <section class="metric-strip" aria-label="用户使用核心指标">
      <article v-for="metric in metrics" :key="metric.label" class="metric-item">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </article>
    </section>

    <div class="analysis-grid">
      <section class="panel trend-panel" aria-labelledby="usage-trend-title">
        <div class="panel-head">
          <div>
            <h2 id="usage-trend-title">活跃趋势</h2>
            <p>按自然日统计去重用户与访问事件</p>
          </div>
          <time>{{ formatTime(overview.generatedAt) }}</time>
        </div>
        <div
          ref="trendChartRef"
          class="trend-chart"
          role="img"
          aria-label="用户活跃趋势"
        />
      </section>

      <section class="panel module-panel" aria-labelledby="module-ranking-title">
        <div class="panel-head">
          <div>
            <h2 id="module-ranking-title">功能使用排行</h2>
            <p>按页面访问次数排序</p>
          </div>
          <span>{{ overview.modules.length }} 个模块</span>
        </div>
        <ol v-if="overview.modules.length" class="module-ranking">
          <li v-for="(module, index) in overview.modules" :key="module.moduleCode">
            <span class="module-rank">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="module-copy">
              <div>
                <strong>{{ module.moduleName }}</strong>
                <span>{{ module.activeUsers }} 人 · {{ module.visits }} 次</span>
              </div>
              <div class="module-track" aria-hidden="true">
                <i :style="{ width: `${Math.max(Number(module.visitRate || 0), 2)}%` }" />
              </div>
            </div>
            <b>{{ formatPercent(module.visitRate) }}</b>
          </li>
        </ol>
        <el-empty v-else description="统计期内暂无页面访问" :image-size="72" />
      </section>
    </div>

    <section class="panel user-panel" aria-labelledby="user-detail-title">
      <div class="panel-head">
        <div>
          <h2 id="user-detail-title">用户明细</h2>
          <p>活跃用户优先排列</p>
        </div>
        <span>共 {{ overview.users.length }} 人</span>
      </div>

      <el-table :data="overview.users" class="desktop-user-table" stripe empty-text="暂无用户">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-identity">
              <strong>{{ row.nickName || '未设置昵称' }}</strong>
              <span>{{ row.phone }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '成员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span class="status-dot" :class="{ disabled: !row.enabled }">
              {{ row.enabled ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="activeDays" label="活跃天数" width="100" sortable />
        <el-table-column prop="visits" label="访问次数" width="100" sortable />
        <el-table-column label="最近活跃" min-width="150" sortable prop="lastActiveTime">
          <template #default="{ row }">{{ formatTime(row.lastActiveTime || row.lastLoginTime) }}</template>
        </el-table-column>
        <el-table-column label="注册时间" min-width="150" prop="registerTime">
          <template #default="{ row }">{{ formatTime(row.registerTime) }}</template>
        </el-table-column>
      </el-table>

      <ul class="mobile-user-list">
        <li v-for="row in overview.users" :key="row.userId">
          <div class="mobile-user-head">
            <div class="user-identity">
              <strong>{{ row.nickName || '未设置昵称' }}</strong>
              <span>{{ row.phone }}</span>
            </div>
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" size="small">
              {{ row.role === 'ADMIN' ? '管理员' : '成员' }}
            </el-tag>
          </div>
          <dl>
            <div><dt>活跃天数</dt><dd>{{ row.activeDays }}</dd></div>
            <div><dt>访问次数</dt><dd>{{ row.visits }}</dd></div>
            <div><dt>最近活跃</dt><dd>{{ formatTime(row.lastActiveTime || row.lastLoginTime) }}</dd></div>
          </dl>
        </li>
      </ul>
    </section>
  </main>
</template>

<style scoped>
.usage-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px 28px 48px;
  color: #172033;
}

.usage-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.usage-header .eyebrow {
  margin: 0 0 5px;
  color: #0a6ed1;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0;
  text-transform: uppercase;
}

.usage-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  letter-spacing: 0;
}

.usage-header p:not(.eyebrow) {
  margin: 7px 0 0;
  color: #667284;
  font-size: 14px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 18px;
  border: 1px solid #dfe4ec;
  border-radius: 6px;
  background: #fff;
}

.metric-item {
  min-width: 0;
  padding: 17px 20px;
  border-right: 1px solid #e8ebf0;
}

.metric-item:last-child {
  border-right: 0;
}

.metric-item span,
.metric-item small {
  display: block;
  color: #6d7888;
  font-size: 12px;
}

.metric-item strong {
  display: block;
  margin: 5px 0 3px;
  color: #172033;
  font-size: 26px;
  line-height: 1;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(320px, 0.85fr);
  gap: 18px;
  margin-bottom: 18px;
}

.panel {
  min-width: 0;
  border: 1px solid #dfe4ec;
  border-radius: 6px;
  background: #fff;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  min-height: 66px;
  padding: 16px 18px 12px;
  border-bottom: 1px solid #edf0f4;
}

.panel-head h2 {
  margin: 0;
  font-size: 16px;
  line-height: 1.3;
  letter-spacing: 0;
}

.panel-head p,
.panel-head > span,
.panel-head time {
  margin: 4px 0 0;
  color: #7a8696;
  font-size: 12px;
}

.trend-chart {
  width: 100%;
  height: 320px;
}

.module-ranking {
  max-height: 320px;
  margin: 0;
  padding: 5px 18px 12px;
  overflow: auto;
  list-style: none;
}

.module-ranking li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #edf0f4;
}

.module-ranking li:last-child {
  border-bottom: 0;
}

.module-rank {
  color: #929cab;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.module-copy {
  min-width: 0;
}

.module-copy > div:first-child {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.module-copy strong,
.module-copy span {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-copy span {
  color: #7a8696;
  font-size: 11px;
}

.module-track {
  height: 4px;
  margin-top: 7px;
  overflow: hidden;
  border-radius: 2px;
  background: #edf0f4;
}

.module-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #16875d;
}

.module-ranking b {
  color: #344054;
  font-size: 12px;
  text-align: right;
}

.user-panel {
  overflow: hidden;
}

.user-identity {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.user-identity strong {
  overflow: hidden;
  color: #172033;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-identity span {
  color: #7a8696;
  font-size: 11px;
}

.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #16875d;
  font-size: 12px;
}

.status-dot::before {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  content: '';
}

.status-dot.disabled {
  color: #a04a3a;
}

.mobile-user-list {
  display: none;
}

@media (max-width: 960px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }

  .module-ranking {
    max-height: none;
  }
}

@media (max-width: 720px) {
  .usage-page {
    padding: 16px 14px 104px;
  }

  .usage-header {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
  }

  .usage-header h1 {
    font-size: 24px;
  }

  .header-actions :deep(.el-segmented) {
    flex: 1;
  }

  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item {
    padding: 14px;
    border-bottom: 1px solid #e8ebf0;
  }

  .metric-item:nth-child(2) {
    border-right: 0;
  }

  .metric-item:nth-child(n + 3) {
    border-bottom: 0;
  }

  .metric-item strong {
    font-size: 23px;
  }

  .panel-head {
    min-height: 60px;
    padding: 14px;
  }

  .panel-head time {
    display: none;
  }

  .trend-chart {
    height: 260px;
  }

  .module-ranking {
    padding-right: 14px;
    padding-left: 14px;
  }

  .module-copy > div:first-child {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .desktop-user-table {
    display: none;
  }

  .mobile-user-list {
    display: block;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .mobile-user-list li {
    padding: 14px;
    border-bottom: 1px solid #edf0f4;
  }

  .mobile-user-list li:last-child {
    border-bottom: 0;
  }

  .mobile-user-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
  }

  .mobile-user-list dl {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin: 13px 0 0;
  }

  .mobile-user-list dl > div:last-child {
    grid-column: 1 / -1;
  }

  .mobile-user-list dt {
    color: #7a8696;
    font-size: 11px;
  }

  .mobile-user-list dd {
    margin: 3px 0 0;
    color: #344054;
    font-size: 13px;
    font-weight: 650;
  }
}

@media (prefers-reduced-motion: reduce) {
  .module-track i {
    transition: none;
  }
}
</style>
