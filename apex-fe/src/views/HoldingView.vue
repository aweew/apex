<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listHoldings, refreshHoldingQuotes, removeHolding, saveHolding } from '../api/holding'
import { saveObserve } from '../api/observe'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const rows = ref([])
const industryPieRef = ref(null)
const themePieRef = ref(null)
let industryChart = null
let themeChart = null

/** 默认隐藏二级行业分布/列，可手动打开 */
const showIndustry = ref(false)

/** 核心题材：清爽浅色（可区分，不刺眼） */
const CORE_THEME_META = {
  '光模块(CPO)': {
    short: '光模块',
    color: '#c43d4a',
    bg: 'rgba(255, 59, 48, 0.10)',
    border: 'rgba(255, 59, 48, 0.22)',
    chart: '#ff6b6b',
  },
  存储芯片: {
    short: '存储',
    color: '#1f8a4c',
    bg: 'rgba(52, 199, 89, 0.12)',
    border: 'rgba(52, 199, 89, 0.24)',
    chart: '#34c759',
  },
  '数据中心(IDC)': {
    short: 'IDC',
    color: '#0a66c2',
    bg: 'rgba(0, 113, 227, 0.10)',
    border: 'rgba(0, 113, 227, 0.22)',
    chart: '#0071e3',
  },
  算力: {
    short: '算力',
    color: '#b36b00',
    bg: 'rgba(255, 159, 10, 0.14)',
    border: 'rgba(255, 159, 10, 0.28)',
    chart: '#ff9f0a',
  },
}

function themeMeta(name) {
  return (
    CORE_THEME_META[name] || {
      short: name,
      color: '#515154',
      bg: 'rgba(0, 0, 0, 0.04)',
      border: 'rgba(0, 0, 0, 0.08)',
      chart: '#8e8e93',
    }
  )
}

function isCoreTheme(name) {
  return Object.prototype.hasOwnProperty.call(CORE_THEME_META, name)
}

const totalPnl = computed(() =>
  rows.value.reduce((sum, r) => sum + (Number(r.pnl) || 0), 0),
)
const totalTodayPnl = computed(() =>
  rows.value.reduce((sum, r) => sum + (Number(r.todayPnl) || 0), 0),
)
const hasTodayPnl = computed(() =>
  rows.value.some((r) => r.todayPnl != null && Number.isFinite(Number(r.todayPnl))),
)
const totalMv = computed(() =>
  rows.value.reduce((sum, r) => sum + (Number(r.marketValue) || 0), 0),
)
/** 组合今日涨跌幅% = 今日盈亏 / 昨收市值 */
const totalTodayPct = computed(() => {
  if (!hasTodayPnl.value) return null
  const preMv = totalMv.value - totalTodayPnl.value
  if (!Number.isFinite(preMv) || Math.abs(preMv) < 1e-6) return null
  return (totalTodayPnl.value / preMv) * 100
})

function fmtSignedPct(pct) {
  if (pct == null || !Number.isFinite(Number(pct))) return ''
  const n = Number(pct)
  return `${n > 0 ? '+' : ''}${n.toFixed(2)}%`
}

function fmtMoney(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  return Math.round(Number(v)).toLocaleString('zh-CN')
}

function fmtSignedMoney(v) {
  if (v == null || !Number.isFinite(Number(v))) return '-'
  const n = Math.round(Number(v))
  const abs = Math.abs(n).toLocaleString('zh-CN')
  if (n > 0) return `+${abs}`
  if (n < 0) return `-${abs}`
  return '0'
}

function rowWeight(row) {
  const mv = Number(row.marketValue)
  const costMv = Number(row.costPrice) * Number(row.quantity || 0)
  if (Number.isFinite(mv) && mv > 0) return mv
  if (Number.isFinite(costMv) && costMv > 0) return costMv
  return 0
}

function toDist(map) {
  const list = [...map.entries()]
    .map(([name, value]) => ({ name, value: Math.round(value * 100) / 100 }))
    .sort((a, b) => b.value - a.value)
  const sum = list.reduce((s, x) => s + x.value, 0)
  return list.map((x) => ({
    ...x,
    pct: sum > 0 ? x.value / sum : 0,
  }))
}

/** 二级行业分布：按市值 */
const industryDist = computed(() => {
  const map = new Map()
  for (const row of rows.value) {
    const name = String(row.industry || '').trim() || '未分类'
    const value = rowWeight(row)
    if (value <= 0) continue
    map.set(name, (map.get(name) || 0) + value)
  }
  return toDist(map)
})

/**
 * 题材分布：只统计核心题材（每票唯一主营题材，整票市值计入）
 */
const themeDist = computed(() => {
  const map = new Map()
  for (const row of rows.value) {
    const value = rowWeight(row)
    if (value <= 0) continue
    const cores = (Array.isArray(row.themeTags) ? row.themeTags : []).filter(isCoreTheme)
    const primary = cores[0]
    if (!primary) continue
    map.set(primary, (map.get(primary) || 0) + value)
  }
  return toDist(map)
})

/** 核心题材标签条（与饼图同源） */
const themeTagBar = computed(() => [...themeDist.value])

const themeHitCount = computed(() =>
  rows.value.filter((r) => Array.isArray(r.themeTags) && r.themeTags.some(isCoreTheme)).length,
)

function primaryTheme(row) {
  const tags = Array.isArray(row?.themeTags) ? row.themeTags.filter(isCoreTheme) : []
  return tags[0] || ''
}

/** 只展示已命中的技术标签，避免灰片铺满 */
function hitTech(row) {
  return (Array.isArray(row?.techSignals) ? row.techSignals : []).filter((s) => s && s.hit)
}

function todayPnlTone(row) {
  const n = row.todayPnl != null ? Number(row.todayPnl) : Number(row.pctChg)
  if (!Number.isFinite(n)) return ''
  return n >= 0 ? 'up' : 'down'
}

function shortTechLabel(label) {
  const raw = String(label || '')
  return raw
    .replace(/^站上/, '')
    .replace(/^跌破/, '')
    .replace(/金叉\/多/, '金叉')
    .replace(/死叉\/空/, '死叉')
    .replace(/红柱放大/, '红柱')
    .replace(/绿柱扩大/, '绿柱')
    .replace(/放量确认/, '放量')
    .replace(/RSI健康/, 'RSI')
    .replace(/RSI转弱/, 'RSI弱')
    .replace(/近20日高/, '新高')
    .replace(/破20日低/, '破低')
}

function valClass(level) {
  if (level === 'UNDERVALUED' || level === 'SLIGHTLY_CHEAP') return 'cheap'
  if (level === 'OVERVALUED' || level === 'SLIGHTLY_EXPENSIVE') return 'rich'
  return 'fair'
}

function verdictClass(verdict) {
  const v = String(verdict || '')
  if (v.includes('卖出') || v.includes('减仓')) return 'warn'
  if (v.includes('偏多') || v.includes('继续')) return 'ok'
  if (v.includes('谨慎') || v.includes('不足')) return 'soft'
  return ''
}

const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  code: '',
  name: '',
  quantity: 100,
  costPrice: '',
  stopLoss: '',
  takeProfit: '',
  note: '',
})

/**
 * 重新加载持仓列表（只读本地库，不打外网）
 */
async function load(opts = {}) {
  const silent = !!opts.silent
  loading.value = true
  try {
    const res = await listHoldings()
    rows.value = res.data || []
    if (!silent) {
      ElMessage.success(`已加载 ${rows.value.length} 只持仓`)
    }
    await nextTick()
    renderPies()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 更新现价：外网拉最新价 → 写 stock_basic → 重算市值/浮盈亏
 * @param forceAll true=全部重拉；false=只补缺现价的
 */
async function onRefreshQuotes(forceAll = true) {
  if (!rows.value.length) {
    ElMessage.info('暂无持仓，请先添加')
    return
  }
  refreshing.value = true
  try {
    const res = await refreshHoldingQuotes(!forceAll)
    rows.value = res.data?.holdings || []
    const ok = Number(res.data?.success || 0)
    const fail = Number(res.data?.fail || 0)
    if (ok === 0 && fail === 0) {
      ElMessage.warning('没有需要更新的标的（可能已是最新）')
    } else if (fail > 0 && ok === 0) {
      ElMessage.error(res.data?.message || `更新失败 ${fail} 只`)
    } else {
      ElMessage.success(res.data?.message || `已更新现价 ${ok} 只`)
    }
    await nextTick()
    renderPies()
  } catch (e) {
    ElMessage.error(e.message || '更新现价失败')
  } finally {
    refreshing.value = false
  }
}

function pieOption(data, opts = {}) {
  const themeColors = !!opts.themeColors
  const compact = !!opts.compact
  return {
    backgroundColor: 'transparent',
    color: [
      '#0071e3', '#34c759', '#ff9500', '#af52de', '#ff3b30',
      '#5ac8fa', '#ffcc00', '#ff2d55', '#5856d6', '#8e8e93',
    ],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.94)',
      borderColor: 'rgba(0,0,0,0.06)',
      textStyle: { color: '#1d1d1f', fontSize: 12 },
      formatter: (p) => {
        const pct = p.percent != null ? p.percent.toFixed(1) : '-'
        const val = Number(p.value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
        return `${p.name}<br/>市值 ${val} · ${pct}%`
      },
    },
    legend: { show: false },
    series: [
      {
        type: 'pie',
        radius: compact ? ['42%', '68%'] : ['40%', '66%'],
        center: ['50%', '52%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: !themeColors,
          formatter: (p) => `${p.name}\n${p.percent.toFixed(0)}%`,
          fontSize: 11,
          color: '#515154',
          lineHeight: 14,
        },
        labelLine: {
          show: !themeColors,
          length: 8,
          length2: 6,
        },
        emphasis: {
          scaleSize: 4,
        },
        data: data.map((d) => {
          const item = { name: d.name, value: d.value }
          if (themeColors) {
            item.itemStyle = {
              color: themeMeta(d.name).chart || themeMeta(d.name).color,
              opacity: 0.92,
            }
          }
          return item
        }),
      },
    ],
  }
}

function renderOnePie(elRef, chartHolder, data, opts = {}) {
  if (!elRef.value) return chartHolder
  if (!data.length) {
    chartHolder?.clear()
    return chartHolder
  }
  const chart = chartHolder || echarts.init(elRef.value)
  chart.setOption(pieOption(data, { ...opts, compact: true }), true)
  return chart
}

function renderPies() {
  if (showIndustry.value) {
    industryChart = renderOnePie(industryPieRef, industryChart, industryDist.value)
  } else if (industryChart) {
    industryChart.dispose()
    industryChart = null
  }
  themeChart = renderOnePie(themePieRef, themeChart, themeDist.value, { themeColors: true })
}

function onResize() {
  industryChart?.resize()
  themeChart?.resize()
}

function openCreate() {
  form.id = null
  form.code = ''
  form.name = ''
  form.quantity = 100
  form.costPrice = ''
  form.stopLoss = ''
  form.takeProfit = ''
  form.note = ''
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.code = row.code || ''
  form.name = row.name || ''
  form.quantity = row.quantity ?? 0
  form.costPrice = row.costPrice ?? ''
  form.stopLoss = row.stopLoss ?? ''
  form.takeProfit = row.takeProfit ?? ''
  form.note = row.note || ''
  dialogVisible.value = true
}

async function onSave() {
  if (!String(form.code || '').trim()) {
    ElMessage.warning('请填写证券代码')
    return
  }
  saving.value = true
  try {
    await saveHolding({
      id: form.id,
      code: String(form.code).trim(),
      name: String(form.name || '').trim() || null,
      quantity: Number(form.quantity || 0),
      costPrice: form.costPrice === '' ? null : Number(form.costPrice),
      stopLoss: form.stopLoss === '' ? null : Number(form.stopLoss),
      takeProfit: form.takeProfit === '' ? null : Number(form.takeProfit),
      note: String(form.note || '').trim() || null,
    })
    ElMessage.success(form.id ? '已更新' : '已添加')
    dialogVisible.value = false
    await load({ silent: true })
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function addObserve(row) {
  if (!row?.code) return
  try {
    await saveObserve({
      code: row.code,
      name: row.name || '',
      status: 'WATCHING',
      side: 'BUY',
      reason: '持仓跟踪',
      tags: 'holding',
      stopLoss: row.stopLoss || undefined,
      targetPrice: row.takeProfit || undefined,
      basePrice: row.costPrice || undefined,
      priority: 4,
    })
    ElMessage.success(`${row.code} 已进观察池`)
  } catch (e) {
    ElMessage.error(e.message || '加入失败')
  }
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`确认删除持仓 ${row.code} ${row.name || ''}？`, '删除持仓', {
      type: 'warning',
    })
    await removeHolding(row.id)
    ElMessage.success('已删除')
    await load({ silent: true })
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

function fmtPct(v) {
  if (v == null) return '-'
  return (Number(v) * 100).toFixed(2) + '%'
}

watch([industryDist, themeDist, showIndustry], async () => {
  await nextTick()
  renderPies()
})

onMounted(async () => {
  await load({ silent: true })
  const missing = rows.value.some((r) => r.marketPrice == null)
  if (missing) {
    await onRefreshQuotes(false)
  }
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  industryChart?.dispose()
  themeChart?.dispose()
  industryChart = null
  themeChart = null
})
</script>

<template>
  <div class="page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Holding</p>
        <h1>真实持仓</h1>
        <p>手动维护；决策卖出/持有读这里。日常点「刷新行情」更新价格。</p>
      </div>
      <div class="actions">
        <el-button type="primary" @click="openCreate">添加持仓</el-button>
        <el-button
          plain
          :loading="refreshing"
          :disabled="!rows.length"
          @click="onRefreshQuotes(true)"
        >
          刷新行情
        </el-button>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
        <el-button plain @click="router.push('/observe')">观察池</el-button>
        <el-button text :loading="loading" @click="load()">重载列表</el-button>
      </div>
    </header>

    <div v-if="rows.length" class="stat-cards">
      <div class="stat-card">
        <label>持仓只数</label>
        <b>{{ rows.length }}</b>
      </div>
      <div class="stat-card">
        <label>总市值</label>
        <b>{{ fmtMoney(totalMv) }}</b>
      </div>
      <div class="stat-card">
        <label>今日盈亏</label>
        <b :class="totalTodayPnl >= 0 ? 'up' : 'down'">
          <template v-if="hasTodayPnl">
            {{ fmtSignedMoney(totalTodayPnl) }}
            <span v-if="totalTodayPct != null" class="pct-aside">{{ fmtSignedPct(totalTodayPct) }}</span>
          </template>
          <template v-else>-</template>
        </b>
      </div>
      <div class="stat-card">
        <label>持仓盈亏</label>
        <b :class="totalPnl >= 0 ? 'up' : 'down'">{{ fmtSignedMoney(totalPnl) }}</b>
      </div>
    </div>

    <div v-if="!loading && !refreshing && !rows.length" class="page-empty">
      <h3>还没有持仓</h3>
      <p>录入后，智能决策会据此给出卖出 / 继续持有建议</p>
      <el-button type="primary" @click="openCreate">添加持仓</el-button>
    </div>

    <section v-if="rows.length" class="theme-panel">
      <div class="theme-panel-head">
        <div class="theme-panel-title">
          <h3>核心题材</h3>
          <span class="muted">命中 {{ themeHitCount }} 只 · 每票唯一主营题材</span>
        </div>
        <label class="industry-toggle">
          <span>二级行业</span>
          <el-switch v-model="showIndustry" size="small" />
        </label>
      </div>
      <div class="theme-panel-body" :class="{ 'with-industry': showIndustry }">
        <div v-if="showIndustry" class="pie-wrap">
          <div class="pie-caption">二级行业</div>
          <div v-if="industryDist.length" ref="industryPieRef" class="pie-chart" />
          <div v-else class="pie-empty">暂无市值</div>
        </div>
        <div class="pie-wrap">
          <div class="pie-caption">题材分布</div>
          <div v-if="themeDist.length" ref="themePieRef" class="pie-chart" />
          <div v-else class="pie-empty">暂无核心题材命中</div>
        </div>
        <div v-if="themeTagBar.length" class="theme-bars">
          <div
            v-for="item in themeTagBar"
            :key="item.name"
            class="theme-bar-row"
          >
            <span
              class="theme-chip"
              :style="{
                color: themeMeta(item.name).color,
                background: themeMeta(item.name).bg,
                borderColor: themeMeta(item.name).border,
              }"
            >
              {{ themeMeta(item.name).short }}
            </span>
            <div class="theme-bar-track">
              <i
                class="theme-bar-fill"
                :style="{
                  width: `${Math.max(item.pct * 100, 2)}%`,
                  background: themeMeta(item.name).chart || themeMeta(item.name).color,
                }"
              />
            </div>
            <span class="theme-bar-pct">{{ (item.pct * 100).toFixed(1) }}%</span>
            <span class="theme-bar-mv muted">{{ fmtMoney(item.value) }}</span>
          </div>
        </div>
      </div>
    </section>

    <section v-if="rows.length" class="holding-layout">
      <el-table class="holding-table" :data="rows" size="small" stripe>
        <el-table-column prop="code" label="代码" width="96" fixed sortable>
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="100" sortable />
        <el-table-column prop="todayPnl" label="今日盈亏" width="118" sortable>
          <template #default="{ row }">
            <div
              v-if="row.todayPnl != null || row.pctChg != null"
              class="today-pnl"
              :class="todayPnlTone(row)"
            >
              <b>{{ row.todayPnl != null ? fmtSignedMoney(row.todayPnl) : '-' }}</b>
              <small v-if="row.pctChg != null">{{ fmtSignedPct(row.pctChg) }}</small>
            </div>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="marketPrice" label="现价" width="84" sortable />
        <el-table-column prop="marketValue" label="市值" width="96" sortable>
          <template #default="{ row }">
            {{ row.marketValue != null ? fmtMoney(row.marketValue) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="pnl" label="持仓盈亏" width="118" sortable>
          <template #default="{ row }">
            <div
              v-if="row.pnl != null"
              class="hold-pnl"
              :class="Number(row.pnl) >= 0 ? 'up' : 'down'"
            >
              <b>{{ fmtSignedMoney(row.pnl) }}</b>
              <small v-if="row.pnlPct != null">{{ fmtPct(row.pnlPct) }}</small>
            </div>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="showIndustry" prop="industry" label="二级行业" width="100" show-overflow-tooltip sortable />
        <el-table-column label="题材" width="96">
          <template #default="{ row }">
            <span
              v-if="primaryTheme(row)"
              class="theme-chip theme-chip--sm"
              :style="{
                color: themeMeta(primaryTheme(row)).color,
                background: themeMeta(primaryTheme(row)).bg,
                borderColor: themeMeta(primaryTheme(row)).border,
              }"
            >
              {{ themeMeta(primaryTheme(row)).short }}
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="技术" min-width="180">
          <template #default="{ row }">
            <div class="tech-cell">
              <div class="tech-sum">{{ row.techSummary || '-' }}</div>
              <div v-if="hitTech(row).length" class="tech-chips">
                <span
                  v-for="sig in hitTech(row)"
                  :key="sig.key"
                  class="tech-chip on"
                  :title="sig.detail || sig.label"
                >{{ shortTechLabel(sig.label) }}</span>
              </div>
              <span v-else-if="row.techSignals?.length" class="muted">暂无命中</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="估值" width="88">
          <template #default="{ row }">
            <span
              v-if="row.valuationLabel"
              class="val-chip"
              :class="valClass(row.valuationLevel)"
              :title="row.valuationSummary || ''"
            >{{ row.valuationLabel }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="verdict" label="评价" width="96">
          <template #default="{ row }">
            <span v-if="row.verdict" class="verdict" :class="verdictClass(row.verdict)">{{ row.verdict }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="advice" label="建议" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="advice">{{ row.advice || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="72" sortable />
        <el-table-column prop="costPrice" label="成本" width="84" sortable />
        <el-table-column prop="stopLoss" label="止损" width="84" sortable />
        <el-table-column prop="takeProfit" label="止盈" width="84" sortable />
        <el-table-column prop="note" label="备注" min-width="100" show-overflow-tooltip sortable />
        <el-table-column label="操作" width="168" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="addObserve(row)">观察</el-button>
            <el-button link type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑持仓' : '添加持仓'" width="480px">
      <el-form label-width="80px">
        <el-form-item label="代码" required>
          <el-input v-model="form.code" placeholder="如 600519" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="可空，自动补全" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="form.quantity" :min="0" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="成本价">
          <el-input v-model="form.costPrice" placeholder="可选" />
        </el-form-item>
        <el-form-item label="止损">
          <el-input v-model="form.stopLoss" placeholder="可选" />
        </el-form-item>
        <el-form-item label="止盈">
          <el-input v-model="form.takeProfit" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent);
  text-transform: uppercase;
}
.header p {
  max-width: 42em;
}
.up { color: var(--up); }
.down { color: var(--down); }
.pct-aside {
  margin-left: 6px;
  font-size: 0.82em;
  font-weight: 600;
  opacity: 0.85;
}

.holding-layout {
  margin-top: 14px;
}

.today-pnl,
.hold-pnl {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  min-width: 92px;
  padding: 5px 9px;
  border-radius: 9px;
  line-height: 1.15;
}

.today-pnl b,
.hold-pnl b {
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}

.today-pnl small,
.hold-pnl small {
  font-size: 11px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  opacity: 0.88;
}

.today-pnl.up,
.hold-pnl.up {
  color: var(--up);
  background: rgba(255, 59, 48, 0.08);
}

.today-pnl.down,
.hold-pnl.down {
  color: var(--down);
  background: rgba(52, 199, 89, 0.08);
}

.today-pnl {
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.03);
}

.theme-panel {
  margin: 2px 0 12px;
  padding: 12px 14px 14px;
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

.theme-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.theme-panel-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.theme-panel-title h3 {
  margin: 0;
  font-size: 15px;
  white-space: nowrap;
}

.industry-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
  cursor: pointer;
  user-select: none;
}

.theme-panel-body {
  display: grid;
  grid-template-columns: minmax(200px, 280px) minmax(0, 1fr);
  gap: 12px 20px;
  align-items: center;
}

.theme-panel-body.with-industry {
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) minmax(0, 1.1fr);
}

.pie-wrap {
  min-width: 0;
}

.pie-caption {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 2px;
}

.pie-chart {
  width: 100%;
  height: 200px;
}

.pie-empty {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--muted);
  font-size: 13px;
}

.theme-bars {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 4px 0;
}

.theme-bar-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr) 48px 72px;
  gap: 8px;
  align-items: center;
}

.theme-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 7px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
  line-height: 1;
  white-space: nowrap;
}

.theme-chip--sm {
  height: 22px;
  padding: 0 7px;
  font-size: 11px;
  border-radius: 6px;
}

.tech-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0;
}

.tech-sum {
  font-size: 11px;
  color: var(--muted);
  line-height: 1.2;
}

.tech-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.tech-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;
  border: 1px solid transparent;
}

.tech-chip.on {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.10);
  border-color: rgba(0, 113, 227, 0.18);
}

.val-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 650;
  border: 1px solid transparent;
}

.val-chip.cheap {
  color: #1f8a4c;
  background: rgba(52, 199, 89, 0.12);
  border-color: rgba(52, 199, 89, 0.22);
}

.val-chip.rich {
  color: #c43d4a;
  background: rgba(255, 59, 48, 0.10);
  border-color: rgba(255, 59, 48, 0.20);
}

.val-chip.fair {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.08);
  border-color: rgba(0, 113, 227, 0.16);
}

.verdict {
  font-size: 12px;
  font-weight: 650;
  color: var(--ink-soft);
}

.verdict.ok { color: #1f8a4c; }
.verdict.warn { color: #c43d4a; }
.verdict.soft { color: #b36b00; }

.advice {
  font-size: 12px;
  color: var(--ink-soft);
  line-height: 1.35;
}

.theme-bar-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.theme-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  opacity: 0.92;
}

.theme-bar-pct {
  font-size: 12px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  text-align: right;
  color: var(--ink-soft);
}

.theme-bar-mv {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.muted {
  font-size: 11px;
  color: var(--muted);
}

.holding-table {
  min-width: 0;
}

@media (max-width: 900px) {
  .theme-panel-body,
  .theme-panel-body.with-industry {
    grid-template-columns: 1fr;
  }
  .theme-bar-row {
    grid-template-columns: 64px minmax(0, 1fr) 48px;
  }
  .theme-bar-mv {
    display: none;
  }
}
</style>
