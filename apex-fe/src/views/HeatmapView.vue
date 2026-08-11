<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchHeatmapIndustryStocks, fetchMarketHeatmap } from '../api/heatmap'
import { fetchSectorConstituents } from '../api/sector'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  freezeCanvasesForCapture,
  shareFilename,
} from '../utils/shareCapture'
import BrandShareLockup from '../components/share/BrandShareLockup.vue'
import BrandShareFoot from '../components/share/BrandShareFoot.vue'
import { snapshotStamp } from '../utils/snapshotDate'
import FloatingShareButton from '../components/FloatingShareButton.vue'

const props = defineProps({
  /** 嵌入行情页时为 true，不展示独立页头 */
  embedded: { type: Boolean, default: false },
})

const router = useRouter()
const loading = ref(false)
const chartRef = ref(null)
const shareCardRef = ref(null)
let chart = null

const boardType = ref('INDUSTRY')
const colorBy = ref('pctChg')
const sizeBy = ref('circMv')
const data = ref(null)

const drawerOpen = ref(false)
const drawerLoading = ref(false)
const drawerTitle = ref('')
const drawerRows = ref([])

const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''

const TYPE_OPTS = [
  { value: 'INDUSTRY', label: '行业' },
  { value: 'CONCEPT', label: '概念' },
  { value: 'THEME', label: '题材' },
]

const COLOR_OPTS = [
  { value: 'pctChg', label: '涨跌幅' },
  { value: 'pe', label: '市盈率' },
  { value: 'netInflow', label: '资金流向' },
]

const SIZE_OPTS = [
  { value: 'circMv', label: '流通市值' },
  { value: 'amount', label: '成交额' },
  { value: 'stockCount', label: '成分家数' },
]

const colorLabel = computed(() => COLOR_OPTS.find((x) => x.value === colorBy.value)?.label || '涨跌幅')
const typeLabel = computed(() => TYPE_OPTS.find((x) => x.value === boardType.value)?.label || '行业')
const nodeCount = computed(() => data.value?.nodes?.length || 0)

const subtitle = computed(() => {
  const d = data.value
  if (!d) return '参考金融界大盘云图 · 块大小×着色看结构'
  const parts = []
  if (d.tradeDate) parts.push(`交易日 ${d.tradeDate}`)
  parts.push(`${typeLabel.value} ${nodeCount.value} 块`)
  parts.push(`色=${colorLabel.value}`)
  if (d.note) parts.push(d.note)
  return parts.join(' · ')
})

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function fmtYi(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return '-'
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(1)}亿`
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(0)}万`
  return n.toFixed(0)
}

function pctColor(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return '#8b949e'
  return n > 0 ? '#f04848' : '#1aad5b'
}

function clamp01(x) {
  return Math.max(0, Math.min(1, x))
}

function lerp(a, b, t) {
  return a + (b - a) * t
}

function mixHex(a, b, t) {
  const pa = parseInt(a.slice(1), 16)
  const pb = parseInt(b.slice(1), 16)
  const ar = (pa >> 16) & 255
  const ag = (pa >> 8) & 255
  const ab = pa & 255
  const br = (pb >> 16) & 255
  const bg = (pb >> 8) & 255
  const bb = pb & 255
  const r = Math.round(lerp(ar, br, t))
  const g = Math.round(lerp(ag, bg, t))
  const bl = Math.round(lerp(ab, bb, t))
  return `#${((1 << 24) + (r << 16) + (g << 8) + bl).toString(16).slice(1)}`
}

/** A股云图：红涨绿跌，中间深灰底板，饱和度随幅度增强 */
function heatColor(colorKey, raw) {
  const n = Number(raw)
  if (!Number.isFinite(n)) return '#2a3139'

  if (colorKey === 'pe') {
    const t = clamp01(n / 80)
    if (t < 0.5) return mixHex('#1aad5b', '#3a4450', t * 2)
    return mixHex('#3a4450', '#e64545', (t - 0.5) * 2)
  }

  if (colorKey === 'netInflow') {
    const t = clamp01((n + 5e9) / 1e10)
    if (t < 0.48) return mixHex('#0f7a45', '#2a3139', t / 0.48)
    if (t > 0.52) return mixHex('#2a3139', '#d63b3b', (t - 0.52) / 0.48)
    return '#2a3139'
  }

  // 涨跌幅：±5% 封顶，弱幅略暗，强幅更艳
  const capped = Math.max(-5, Math.min(5, n))
  const intensity = Math.pow(Math.abs(capped) / 5, 0.72)
  if (Math.abs(capped) < 0.08) return '#2a3139'
  if (capped > 0) return mixHex('#3a2a2e', '#f04848', 0.35 + intensity * 0.65)
  return mixHex('#24352c', '#1aad5b', 0.35 + intensity * 0.65)
}

function labelInk(bg) {
  const hex = String(bg || '').replace('#', '')
  if (hex.length !== 6) return '#f5f7fa'
  const r = parseInt(hex.slice(0, 2), 16)
  const g = parseInt(hex.slice(2, 4), 16)
  const b = parseInt(hex.slice(4, 6), 16)
  const luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
  return luma > 0.55 ? '#0f1419' : '#f5f7fa'
}

/** 底部色阶条（仅作图例，着色由 heatColor 直算） */
function buildVisualMap(colorKey) {
  const base = {
    show: true,
    calculable: false,
    orient: 'horizontal',
    left: 'center',
    bottom: 4,
    itemWidth: 14,
    itemHeight: 8,
    textStyle: { color: 'rgba(230,237,243,.55)', fontSize: 10 },
    hoverLink: false,
    seriesIndex: [],
  }
  if (colorKey === 'pe') {
    return {
      ...base,
      min: 0,
      max: 80,
      text: ['高估值', '低估值'],
      inRange: { color: ['#1aad5b', '#3a4450', '#e64545'] },
    }
  }
  if (colorKey === 'netInflow') {
    return {
      ...base,
      min: -5e9,
      max: 5e9,
      text: ['流入', '流出'],
      inRange: { color: ['#0f7a45', '#2a3139', '#d63b3b'] },
      formatter: (v) => fmtYi(v),
    }
  }
  return {
    ...base,
    min: -5,
    max: 5,
    text: ['涨', '跌'],
    inRange: { color: ['#1aad5b', '#2a3139', '#f04848'] },
    formatter: (v) => `${Number(v).toFixed(0)}%`,
  }
}

function toTreeData(nodes, colorKey) {
  return (nodes || []).map((n) => {
    const colorVal = n.colorValue != null ? Number(n.colorValue) : Number(n.pctChg)
    const safeColor = Number.isFinite(colorVal) ? colorVal : 0
    const fill = heatColor(colorKey, safeColor)
    const metric = formatMetric(n, colorKey)
    return {
      name: n.name,
      value: Math.max(Number(n.value) || 1, 1),
      colorValue: safeColor,
      code: n.code,
      pctChg: n.pctChg,
      circMv: n.circMv,
      amount: n.amount,
      stockCount: n.stockCount,
      upCount: n.upCount,
      downCount: n.downCount,
      avgPe: n.avgPe,
      netInflow: n.netInflow,
      leadStockName: n.leadStockName,
      leadStockPct: n.leadStockPct,
      itemStyle: {
        color: fill,
        borderColor: '#0b0f14',
        borderWidth: 1.5,
        gapWidth: 1.5,
      },
      label: {
        color: labelInk(fill),
      },
      labelName: n.name || '',
      labelMetric: metric,
    }
  })
}

function formatMetric(n, colorKey) {
  if (colorKey === 'pe') return n.avgPe != null ? `PE ${Number(n.avgPe).toFixed(1)}` : ''
  if (colorKey === 'netInflow') return n.netInflow != null ? fmtYi(n.netInflow) : ''
  return fmtPct(n.pctChg)
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value, null, { renderer: 'canvas' })
  const nodes = data.value?.nodes || []
  const colorKey = colorBy.value
  const tree = toTreeData(nodes, colorKey)
  chart.setOption(
    {
      backgroundColor: 'transparent',
      animationDuration: 280,
      animationEasing: 'cubicOut',
      tooltip: {
        backgroundColor: 'rgba(12,16,21,0.94)',
        borderColor: 'rgba(255,255,255,0.1)',
        borderWidth: 1,
        padding: [10, 12],
        textStyle: { color: '#e6edf3', fontSize: 12 },
        extraCssText: 'border-radius:10px;box-shadow:0 8px 28px rgba(0,0,0,.35);',
        formatter(info) {
          const d = info?.data || {}
          const lines = [
            `<div style="font-weight:700;font-size:13px;margin-bottom:6px;">${d.name || ''}</div>`,
            `<div>涨跌 <span style="color:${pctColor(d.pctChg)};font-weight:700;">${fmtPct(d.pctChg)}</span></div>`,
            d.circMv != null ? `<div>流通 ${fmtYi(d.circMv)}</div>` : null,
            d.amount != null ? `<div>成交额 ${fmtYi(d.amount)}</div>` : null,
            d.stockCount != null
              ? `<div>家数 ${d.stockCount}（↑${d.upCount ?? '-'} ↓${d.downCount ?? '-'}）</div>`
              : null,
            d.avgPe != null ? `<div>均PE ${Number(d.avgPe).toFixed(1)}</div>` : null,
            d.netInflow != null ? `<div>净流入 ${fmtYi(d.netInflow)}</div>` : null,
            d.leadStockName
              ? `<div>领涨 ${d.leadStockName} <span style="color:${pctColor(d.leadStockPct)};">${fmtPct(d.leadStockPct)}</span></div>`
              : null,
          ]
          return lines.filter(Boolean).join('')
        },
      },
      visualMap: buildVisualMap(colorKey),
      series: [
        {
          type: 'treemap',
          width: '100%',
          height: '100%',
          top: 4,
          left: 4,
          right: 4,
          bottom: 28,
          roam: false,
          nodeClick: false,
          breadcrumb: { show: false },
          squareRatio: 0.72 * (1 + Math.sqrt(5)),
          label: {
            show: true,
            position: 'inside',
            padding: [4, 6],
            formatter(p) {
              const name = p.data?.labelName || p.name || ''
              const metric = p.data?.labelMetric || ''
              return metric ? `${name}\n${metric}` : name
            },
            fontSize: 13,
            fontWeight: 700,
            lineHeight: 17,
            fontFamily: '"Plus Jakarta Sans","PingFang SC","Microsoft YaHei",sans-serif',
            textShadowColor: 'rgba(0,0,0,0.35)',
            textShadowBlur: 2,
          },
          upperLabel: { show: false },
          itemStyle: {
            borderColor: '#0b0f14',
            borderWidth: 1.5,
            gapWidth: 1.5,
            borderRadius: 2,
          },
          emphasis: {
            itemStyle: {
              borderColor: 'rgba(255,255,255,0.55)',
              borderWidth: 2,
              shadowBlur: 12,
              shadowColor: 'rgba(0,0,0,0.35)',
            },
            label: {
              fontWeight: 750,
            },
          },
          levels: [
            {
              itemStyle: {
                borderColor: '#0b0f14',
                borderWidth: 0,
                gapWidth: 2,
              },
              upperLabel: { show: false },
            },
            {
              itemStyle: {
                borderWidth: 1.5,
                gapWidth: 1.5,
                borderColor: '#0b0f14',
                borderRadius: 2,
              },
            },
          ],
          data: tree,
        },
      ],
    },
    true,
  )
}

function bindChartEvents() {
  if (!chart) return
  chart.off('click')
  chart.on('click', onChartClick)
}

async function load() {
  loading.value = true
  try {
    const size =
      sizeBy.value ||
      (boardType.value === 'INDUSTRY' ? 'circMv' : 'amount')
    if (boardType.value === 'INDUSTRY' && colorBy.value === 'netInflow') {
      colorBy.value = 'pctChg'
    }
    if (boardType.value !== 'INDUSTRY' && colorBy.value === 'pe') {
      colorBy.value = 'pctChg'
    }
    const res = await fetchMarketHeatmap({
      type: boardType.value,
      colorBy: colorBy.value,
      sizeBy: size,
      limit: 100,
    })
    data.value = res.data
    await nextTick()
    renderChart()
    bindChartEvents()
  } catch (e) {
    data.value = null
    ElMessage.error(e.message || '加载云图失败')
  } finally {
    loading.value = false
  }
}

async function openNode(node) {
  if (!node?.name) return
  drawerTitle.value = node.name
  drawerOpen.value = true
  drawerLoading.value = true
  drawerRows.value = []
  try {
    if (boardType.value === 'INDUSTRY') {
      const res = await fetchHeatmapIndustryStocks(node.name, 50)
      drawerRows.value = res.data || []
    } else {
      const res = await fetchSectorConstituents(node.code || node.name, {
        type: boardType.value,
        sortBy: 'pctChg',
        order: 'desc',
      })
      drawerRows.value = (res.data?.items || []).map((x) => ({
        code: x.code,
        name: x.name,
        latestPrice: x.latestPrice,
        pctChg: x.pctChg,
        industry: node.name,
      }))
    }
  } catch (e) {
    ElMessage.error(e.message || '加载成分失败')
  } finally {
    drawerLoading.value = false
  }
}

function onChartClick(params) {
  const d = params?.data
  if (!d?.name) return
  openNode(d)
}

function onResize() {
  chart?.resize()
}

async function captureHeatmapShare() {
  const el = shareCardRef.value
  if (!el) throw new Error('分享卡片未就绪')
  // 先保证图已绘制
  renderChart()
  await nextTick()
  await new Promise((r) => setTimeout(r, 80))
  const unfreeze = freezeCanvasesForCapture(el)
  try {
    return await captureElementBlob(el, {
      scale: 2,
      backgroundColor: '#0f1419',
    })
  } finally {
    unfreeze()
  }
}

async function openShare() {
  if (!nodeCount.value) {
    ElMessage.warning('暂无云图可分享')
    return
  }
  sharing.value = true
  try {
    const blob = await captureHeatmapShare()
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    shareOpen.value = true
  } catch (e) {
    console.error(e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onCopyShare() {
  copying.value = true
  try {
    await copyImageBlob(captureHeatmapShare())
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error(e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  downloading.value = true
  try {
    const blob = await captureHeatmapShare()
    const stamp = snapshotStamp(data.value) || 'date-unknown'
    downloadBlob(blob, shareFilename('apex_heatmap', `${boardType.value}_${stamp}`))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error(e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
}

watch([boardType, colorBy, sizeBy], () => {
  // 切换维度时校正默认 size
  if (boardType.value === 'INDUSTRY' && sizeBy.value === 'amount') {
    sizeBy.value = 'circMv'
  }
  if (boardType.value !== 'INDUSTRY' && sizeBy.value === 'circMv') {
    sizeBy.value = 'amount'
  }
  load()
})

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  revokeSharePreview()
  if (chart) {
    chart.off('click')
    chart.dispose()
    chart = null
  }
})
</script>

<template>
  <div
    class="heatmap-page"
    :class="{ embedded, page: !embedded }"
    v-loading="loading"
  >
    <header v-if="!embedded" class="header">
      <div>
        <p class="eyebrow">灵枢 · Heatmap</p>
        <h1><TermTip term="sector">大盘云图</TermTip></h1>
        <p>{{ subtitle }}</p>
      </div>
      <div class="actions">
        <el-radio-group v-model="boardType" size="small">
          <el-radio-button v-for="t in TYPE_OPTS" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="colorBy" size="small" style="width: 110px">
          <el-option
            v-for="c in COLOR_OPTS"
            :key="c.value"
            :label="c.label"
            :value="c.value"
            :disabled="(boardType === 'INDUSTRY' && c.value === 'netInflow') || (boardType !== 'INDUSTRY' && c.value === 'pe')"
          />
        </el-select>
        <el-select v-model="sizeBy" size="small" style="width: 110px">
          <el-option
            v-for="s in SIZE_OPTS"
            :key="s.value"
            :label="s.label"
            :value="s.value"
            :disabled="(boardType === 'INDUSTRY' && s.value === 'amount') || (boardType !== 'INDUSTRY' && s.value === 'circMv')"
          />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button plain @click="router.push('/sector')">板块榜</el-button>
      </div>
    </header>

    <section v-else id="heatmap" class="embed-head">
      <div class="embed-title">
        <h2><TermTip term="sector">大盘云图</TermTip></h2>
        <p>{{ subtitle }}</p>
      </div>
      <div class="actions">
        <el-radio-group v-model="boardType" size="small">
          <el-radio-button v-for="t in TYPE_OPTS" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
        <el-select v-model="colorBy" size="small" style="width: 110px">
          <el-option
            v-for="c in COLOR_OPTS"
            :key="c.value"
            :label="c.label"
            :value="c.value"
            :disabled="(boardType === 'INDUSTRY' && c.value === 'netInflow') || (boardType !== 'INDUSTRY' && c.value === 'pe')"
          />
        </el-select>
        <el-select v-model="sizeBy" size="small" style="width: 110px">
          <el-option
            v-for="s in SIZE_OPTS"
            :key="s.value"
            :label="s.label"
            :value="s.value"
            :disabled="(boardType === 'INDUSTRY' && s.value === 'amount') || (boardType !== 'INDUSTRY' && s.value === 'circMv')"
          />
        </el-select>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <FloatingShareButton
      v-if="!embedded && !shareOpen"
      :loading="sharing"
      :disabled="!nodeCount"
      label="分享大盘云图"
      @click="openShare"
    />

    <div ref="shareCardRef" class="share-card">
      <div class="share-head">
        <div>
          <BrandShareLockup subtitle="大盘云图" theme="dark" :size="44" />
          <span class="share-meta">{{ typeLabel }} · {{ colorLabel }} · {{ nodeCount }} 块</span>
        </div>
        <em v-if="data?.tradeDate">{{ data.tradeDate }}</em>
      </div>
      <div ref="chartRef" class="chart" />
      <BrandShareFoot
        theme="dark"
        :note="`${data?.tradeDate || ''} · 仅供研究参考 · 不构成投资建议`"
      />
    </div>

    <el-drawer v-model="drawerOpen" :title="drawerTitle" size="420px">
      <div v-loading="drawerLoading">
        <el-table :data="drawerRows" size="small" stripe height="100%" empty-text="暂无成分">
          <el-table-column prop="code" label="代码" width="88">
            <template #default="{ row }">
              <el-button link type="primary" @click="router.push(`/stock/${row.code}`)">{{ row.code }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="96" />
          <el-table-column prop="latestPrice" label="现价" width="72" />
          <el-table-column prop="pctChg" width="80">
            <template #header><TermTip term="pct_chg">涨跌%</TermTip></template>
            <template #default="{ row }">
              <span :style="{ color: pctColor(row.pctChg) }">{{ fmtPct(row.pctChg) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="circMv" label="流通" width="72">
            <template #default="{ row }">{{ fmtYi(row.circMv) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <el-dialog
      v-model="shareOpen"
      title="分享大盘云图"
      width="720px"
      append-to-body
      destroy-on-close
      align-center
      class="heatmap-share-dialog"
      @closed="revokeSharePreview"
    >
      <p class="share-tip">含灵枢 Apex 品牌；可复制或下载 PNG 后发微信/社群。</p>
      <div v-if="sharePreviewUrl" class="share-preview">
        <img :src="sharePreviewUrl" alt="大盘云图分享预览" />
      </div>
      <template #footer>
        <el-button @click="closeShare">关闭</el-button>
        <el-button type="primary" plain :loading="copying" @click="onCopyShare">复制图片</el-button>
        <el-button type="primary" :loading="downloading" @click="onDownloadShare">下载 PNG</el-button>
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

.heatmap-page {
  min-height: calc(100vh - 48px);
}

.heatmap-page.embedded {
  min-height: 0;
  margin-top: 16px;
}

.embed-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid var(--glass-border, rgba(255, 255, 255, 0.08));
  border-radius: var(--radius, 12px);
  background: var(--glass-strong, rgba(255, 255, 255, 0.04));
}

.embed-title h2 {
  margin: 0;
  font-size: 16px;
}

.embed-title p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--muted, #8b949e);
}

.embedded .chart {
  height: min(56vh, 620px);
  min-height: 360px;
}

.header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.header h1 {
  margin: 0;
  font-size: 22px;
}

.header p {
  margin: 4px 0 0;
  color: var(--muted, #8b949e);
  font-size: 13px;
  max-width: 720px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.share-card {
  position: relative;
  background:
    radial-gradient(ellipse 70% 45% at 0% 0%, rgba(0, 113, 227, 0.14), transparent 55%),
    radial-gradient(ellipse 50% 40% at 100% 0%, rgba(196, 86, 86, 0.1), transparent 50%),
    linear-gradient(165deg, #141a22 0%, #0f1419 52%, #0c1015 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px 14px 10px;
  overflow: hidden;
}

.share-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
  color: #e6edf3;
}

.share-meta {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  color: rgba(230, 237, 243, 0.48);
}

.share-head em {
  font-style: normal;
  font-size: 12px;
  color: rgba(230, 237, 243, 0.48);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.chart {
  width: 100%;
  height: min(72vh, 720px);
  min-height: 420px;
  border-radius: 8px;
  overflow: hidden;
  background: #0b0f14;
}

.share-tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--muted, #8b949e);
}

.share-preview {
  max-height: 70vh;
  overflow: auto;
  background: #0f1419;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.share-preview img {
  display: block;
  width: 100%;
}
</style>
