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
  return n > 0 ? '#e5484d' : '#2da44e'
}

/** JRJ 风格：红涨绿跌连续色阶 */
function buildVisualMap(colorKey) {
  if (colorKey === 'pe') {
    return {
      min: 0,
      max: 80,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 8,
      text: ['高估值', '低估值'],
      inRange: {
        color: ['#1a7f4b', '#7dcea0', '#f5f5f5', '#f5b7b1', '#c0392b'],
      },
      textStyle: { color: '#8b949e', fontSize: 11 },
    }
  }
  if (colorKey === 'netInflow') {
    return {
      min: -5e9,
      max: 5e9,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 8,
      text: ['流入', '流出'],
      inRange: {
        color: ['#1a7f4b', '#7dcea0', '#2a3038', '#e57373', '#c62828'],
      },
      textStyle: { color: '#8b949e', fontSize: 11 },
      formatter: (v) => fmtYi(v),
    }
  }
  return {
    min: -5,
    max: 5,
    calculable: false,
    orient: 'horizontal',
    left: 'center',
    bottom: 8,
    text: ['涨', '跌'],
    inRange: {
      color: ['#0d5c2e', '#1a7f4b', '#2da44e', '#3d444d', '#e57373', '#e5484d', '#b71c1c'],
    },
    textStyle: { color: '#8b949e', fontSize: 11 },
    formatter: (v) => `${Number(v).toFixed(1)}%`,
  }
}

function toTreeData(nodes, colorKey) {
  return (nodes || []).map((n) => {
    const colorVal = n.colorValue != null ? Number(n.colorValue) : Number(n.pctChg)
    return {
      name: n.name,
      value: Math.max(Number(n.value) || 1, 1),
      colorValue: Number.isFinite(colorVal) ? colorVal : 0,
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
      labelText: formatBlockLabel(n, colorKey),
    }
  })
}

function formatBlockLabel(n, colorKey) {
  const name = n.name || ''
  let metric = ''
  if (colorKey === 'pe') metric = n.avgPe != null ? `PE ${Number(n.avgPe).toFixed(1)}` : ''
  else if (colorKey === 'netInflow') metric = n.netInflow != null ? fmtYi(n.netInflow) : ''
  else metric = fmtPct(n.pctChg)
  return metric ? `${name}\n${metric}` : name
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const nodes = data.value?.nodes || []
  const colorKey = colorBy.value
  const tree = toTreeData(nodes, colorKey)
  chart.setOption(
    {
      backgroundColor: 'transparent',
      tooltip: {
        backgroundColor: 'rgba(15,20,25,0.92)',
        borderColor: '#30363d',
        textStyle: { color: '#e6edf3', fontSize: 12 },
        formatter(info) {
          const d = info?.data || {}
          const lines = [
            `<b>${d.name || ''}</b>`,
            `涨跌 ${fmtPct(d.pctChg)}`,
            d.circMv != null ? `流通 ${fmtYi(d.circMv)}` : null,
            d.amount != null ? `成交额 ${fmtYi(d.amount)}` : null,
            d.stockCount != null ? `家数 ${d.stockCount}（↑${d.upCount ?? '-'} ↓${d.downCount ?? '-'}）` : null,
            d.avgPe != null ? `均PE ${Number(d.avgPe).toFixed(1)}` : null,
            d.netInflow != null ? `净流入 ${fmtYi(d.netInflow)}` : null,
            d.leadStockName
              ? `领涨 ${d.leadStockName} ${fmtPct(d.leadStockPct)}`
              : null,
          ]
          return lines.filter(Boolean).join('<br/>')
        },
      },
      visualMap: buildVisualMap(colorKey),
      series: [
        {
          type: 'treemap',
          width: '100%',
          height: '92%',
          top: 0,
          bottom: 36,
          roam: false,
          nodeClick: false,
          breadcrumb: { show: false },
          visualDimension: 1,
          label: {
            show: true,
            formatter: (p) => p.data?.labelText || p.name,
            color: '#fff',
            fontSize: 12,
            fontWeight: 600,
            lineHeight: 16,
            textShadowColor: 'rgba(0,0,0,0.45)',
            textShadowBlur: 3,
          },
          upperLabel: { show: false },
          itemStyle: {
            borderColor: '#0f1419',
            borderWidth: 2,
            gapWidth: 2,
          },
          emphasis: {
            itemStyle: { borderColor: '#e6edf3', borderWidth: 2 },
          },
          levels: [
            {
              itemStyle: {
                borderColor: '#0f1419',
                borderWidth: 0,
                gapWidth: 2,
              },
            },
          ],
          data: tree.map((t) => ({
            ...t,
            visualDimension: 1,
            value: [t.value, t.colorValue],
          })),
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
    const blob = await captureHeatmapShare()
    await copyImageBlob(blob)
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
    const stamp = data.value?.tradeDate || new Date().toISOString().slice(0, 10)
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
  <div class="page heatmap-page" v-loading="loading">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · Heatmap</p>
        <h1>大盘云图</h1>
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
        <el-button class="share-action-btn" type="primary" :loading="sharing" :disabled="!nodeCount" @click="openShare">
          分享图片
        </el-button>
        <el-button plain @click="router.push('/sector')">板块榜</el-button>
      </div>
    </header>

    <div ref="shareCardRef" class="share-card">
      <div class="share-head">
        <div>
          <strong>Apex 大盘云图</strong>
          <span>{{ typeLabel }} · {{ colorLabel }} · {{ nodeCount }} 块</span>
        </div>
        <em v-if="data?.tradeDate">{{ data.tradeDate }}</em>
      </div>
      <div ref="chartRef" class="chart" />
      <p class="share-foot">块越大权重越高 · 红涨绿跌 · 仅供研究参考</p>
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
          <el-table-column prop="pctChg" label="涨跌%" width="80">
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

    <el-dialog v-model="shareOpen" title="分享大盘云图" width="720px" destroy-on-close @closed="closeShare">
      <div v-if="sharePreviewUrl" class="share-preview">
        <img :src="sharePreviewUrl" alt="大盘云图分享预览" />
      </div>
      <template #footer>
        <el-button :loading="copying" @click="onCopyShare">复制图片</el-button>
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
  background: #0f1419;
  border: 1px solid #30363d;
  border-radius: 10px;
  padding: 12px 12px 8px;
  overflow: hidden;
}

.share-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
  color: #e6edf3;
}

.share-head strong {
  margin-right: 10px;
  font-size: 15px;
}

.share-head span,
.share-head em {
  font-style: normal;
  font-size: 12px;
  color: #8b949e;
}

.chart {
  width: 100%;
  height: min(72vh, 720px);
  min-height: 420px;
}

.share-foot {
  margin: 6px 0 0;
  text-align: right;
  font-size: 11px;
  color: #6e7681;
}

.share-preview {
  max-height: 70vh;
  overflow: auto;
  background: #0f1419;
  border-radius: 8px;
}

.share-preview img {
  display: block;
  width: 100%;
}
</style>
