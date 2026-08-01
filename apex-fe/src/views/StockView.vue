<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { fetchStockDetail, fetchStockFundamental, syncStockBasic } from '../api/stock'
import { syncBars } from '../api/bars'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const syncingBars = ref(false)
const refreshing = ref(false)
const fundLoading = ref(false)
const code = ref(String(route.params.code || route.query.code || '600519'))
const basic = ref(null)
const note = ref('')
const bars = ref([])
const needSyncBars = ref(false)
const barCount = ref(0)
const rs20 = ref(null)
const rs60 = ref(null)
const volumeRatio = ref(null)
const chartRef = ref(null)
const activeTab = ref('chart')
const fund = ref(null)
const macdTip = ref('')
let chart

function ma(closes, period) {
  return closes.map((_, i) => {
    if (i + 1 < period) return null
    let sum = 0
    for (let j = i - period + 1; j <= i; j++) sum += closes[j]
    return +(sum / period).toFixed(2)
  })
}

function ema(closes, period) {
  const k = 2 / (period + 1)
  const out = []
  let prev = null
  for (let i = 0; i < closes.length; i++) {
    if (i + 1 < period) {
      out.push(null)
      continue
    }
    if (prev == null) {
      let sum = 0
      for (let j = i - period + 1; j <= i; j++) sum += closes[j]
      prev = sum / period
    } else {
      prev = closes[i] * k + prev * (1 - k)
    }
    out.push(+prev.toFixed(4))
  }
  return out
}

function macd(closes) {
  const ema12 = ema(closes, 12)
  const ema26 = ema(closes, 26)
  const dif = closes.map((_, i) =>
    ema12[i] == null || ema26[i] == null ? null : +(ema12[i] - ema26[i]).toFixed(4),
  )
  const dea = (() => {
    const vals = dif.map((v) => (v == null ? 0 : v))
    const e = ema(vals, 9)
    return e.map((v, i) => (dif[i] == null ? null : v))
  })()
  const hist = dif.map((v, i) => (v == null || dea[i] == null ? null : +((v - dea[i]) * 2).toFixed(4)))
  return { dif, dea, hist }
}

/** DIF 上穿 DEA=金叉，下穿=死叉 */
function macdCrosses(dif, dea) {
  const signals = new Array(dif.length).fill(null)
  for (let i = 1; i < dif.length; i++) {
    if (dif[i] == null || dea[i] == null || dif[i - 1] == null || dea[i - 1] == null) continue
    const prev = dif[i - 1] - dea[i - 1]
    const cur = dif[i] - dea[i]
    if (prev <= 0 && cur > 0) signals[i] = 'golden'
    else if (prev >= 0 && cur < 0) signals[i] = 'death'
  }
  return signals
}

/** KDJ(9,3,3) */
function kdj(highs, lows, closes, n = 9, m1 = 3, m2 = 3) {
  const rsv = closes.map((c, i) => {
    if (i + 1 < n) return null
    let hh = -Infinity
    let ll = Infinity
    for (let j = i - n + 1; j <= i; j++) {
      hh = Math.max(hh, highs[j])
      ll = Math.min(ll, lows[j])
    }
    if (!Number.isFinite(hh) || !Number.isFinite(ll) || hh === ll) return 50
    return ((c - ll) / (hh - ll)) * 100
  })
  const k = []
  const d = []
  let prevK = 50
  let prevD = 50
  for (let i = 0; i < rsv.length; i++) {
    if (rsv[i] == null) {
      k.push(null)
      d.push(null)
      continue
    }
    prevK = (rsv[i] + (m1 - 1) * prevK) / m1
    prevD = (prevK + (m2 - 1) * prevD) / m2
    k.push(+prevK.toFixed(2))
    d.push(+prevD.toFixed(2))
  }
  const j = k.map((kv, i) => (kv == null || d[i] == null ? null : +(3 * kv - 2 * d[i]).toFixed(2)))
  return { k, d, j }
}

function fmtVol(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  const n = Number(v)
  const abs = Math.abs(n)
  if (abs >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  if (abs >= 1e4) return (n / 1e4).toFixed(2) + '万'
  return n.toFixed(0)
}

function disposeChart() {
  if (chart) {
    chart.dispose()
    chart = null
  }
}

function dayPctOf(list, closes, idx) {
  const row = list[idx]
  if (row?.pctChg != null && row.pctChg !== '') {
    const n = Number(row.pctChg)
    if (!Number.isNaN(n)) return n
  }
  if (idx <= 0) return null
  const prev = closes[idx - 1]
  const cur = closes[idx]
  if (!prev || Number.isNaN(prev) || Number.isNaN(cur)) return null
  return ((cur - prev) / prev) * 100
}

function sincePctOf(closes, idx) {
  if (idx == null || idx < 0 || idx >= closes.length) return null
  const base = closes[idx]
  const latest = closes[closes.length - 1]
  if (!base || Number.isNaN(base) || latest == null || Number.isNaN(latest)) return null
  return ((latest - base) / base) * 100
}

function pctColor(v) {
  if (v == null || Number.isNaN(v)) return '#666'
  if (v > 0) return '#ef5350'
  if (v < 0) return '#26a69a'
  return '#666'
}

function fmtSignedPct(v) {
  if (v == null || Number.isNaN(v)) return '-'
  const sign = v > 0 ? '+' : ''
  return `${sign}${v.toFixed(2)}%`
}

async function renderChart(list) {
  if (!list.length) {
    macdTip.value = ''
    disposeChart()
    return
  }
  // 等容器从空态切出并完成布局，避免 width=0 把图压成一条线
  await nextTick()
  await new Promise((r) => requestAnimationFrame(() => r()))
  if (!chartRef.value) return
  const width = chartRef.value.clientWidth
  if (width < 80) {
    await new Promise((r) => setTimeout(r, 50))
  }
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  } else {
    chart.resize()
  }
  const dates = list.map((b) => b.tradeDate)
  const ohlc = list.map((b) => [+b.openPrice, +b.closePrice, +b.lowPrice, +b.highPrice])
  const volumes = list.map((b) => +b.volume)
  const amounts = list.map((b) => (b.amount != null ? +b.amount : null))
  const closes = list.map((b) => +b.closePrice)
  const highs = list.map((b) => +b.highPrice)
  const lows = list.map((b) => +b.lowPrice)
  const ma5 = ma(closes, 5)
  const ma10 = ma(closes, 10)
  const ma20 = ma(closes, 20)
  const ma60 = ma(closes, 60)
  const { dif, dea, hist } = macd(closes)
  const crosses = macdCrosses(dif, dea)
  const goldenPoints = []
  const deathPoints = []
  for (let i = 0; i < crosses.length; i++) {
    if (crosses[i] === 'golden') goldenPoints.push([dates[i], dif[i]])
    if (crosses[i] === 'death') deathPoints.push([dates[i], dif[i]])
  }
  let lastCrossIdx = -1
  for (let i = crosses.length - 1; i >= 0; i--) {
    if (crosses[i]) {
      lastCrossIdx = i
      break
    }
  }
  if (lastCrossIdx >= 0) {
    const kind = crosses[lastCrossIdx] === 'golden' ? '金叉' : '死叉'
    macdTip.value = `最近 MACD${kind}：${dates[lastCrossIdx]}（DIF ${fmtNum(dif[lastCrossIdx], 3)} / DEA ${fmtNum(dea[lastCrossIdx], 3)}）`
  } else {
    macdTip.value = ''
  }
  const { k: kLine, d: dLine, j: jLine } = kdj(highs, lows, closes)

  chart.setOption({
    backgroundColor: 'transparent',
    animation: false,
    legend: {
      data: ['K线', 'MA5', 'MA10', 'MA20', 'MA60', '成交量', 'DIF', 'DEA', 'MACD', '金叉', '死叉', 'K', 'D', 'J'],
      top: 2,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { fontSize: 11, color: '#6b7280' },
      inactiveColor: '#c5c5c7',
    },
    // 副图左侧名称：成交量 / MACD / KDJ
    title: [
      {
        text: '成交量',
        left: 8,
        top: '51%',
        textStyle: { fontSize: 11, color: 'rgba(107,114,128,0.85)', fontWeight: 600 },
      },
      {
        text: 'MACD',
        left: 8,
        top: '65%',
        textStyle: { fontSize: 11, color: 'rgba(107,114,128,0.85)', fontWeight: 600 },
      },
      {
        text: 'KDJ',
        left: 8,
        top: '81%',
        textStyle: { fontSize: 11, color: 'rgba(107,114,128,0.85)', fontWeight: 600 },
      },
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        snap: true,
        animation: false,
        lineStyle: { color: 'rgba(29,29,31,0.28)', width: 1, type: 'dashed' },
        crossStyle: { color: 'rgba(29,29,31,0.22)', width: 1 },
        label: {
          backgroundColor: 'rgba(255,255,255,0.72)',
          borderColor: 'rgba(0,0,0,0.06)',
          borderWidth: 1,
          color: '#3a3a3c',
          fontSize: 11,
          padding: [3, 6],
        },
      },
      confine: true,
      enterable: false,
      appendToBody: true,
      className: 'kline-tip',
      borderWidth: 0,
      backgroundColor: 'transparent',
      padding: 0,
      extraCssText:
        'box-shadow:none;background:transparent;border:none;padding:0;backdrop-filter:none;',
      formatter(params) {
        const items = Array.isArray(params) ? params : [params]
        const idx = items[0]?.dataIndex
        if (idx == null || idx < 0) return ''
        const date = dates[idx]
        const candle = ohlc[idx] || []
        const open = candle[0]
        const close = candle[1]
        const low = candle[2]
        const high = candle[3]
        const dayPct = dayPctOf(list, closes, idx)
        const sincePct = sincePctOf(closes, idx)
        const isLatest = idx === closes.length - 1
        const sinceText = isLatest ? '0.00%' : fmtSignedPct(sincePct)
        const sinceLabel =
          sincePct == null || Number.isNaN(sincePct) || sincePct >= 0 ? '至今涨幅' : '至今跌幅'
        const sincePeriods = Math.max(1, closes.length - idx)
        const crossBadge =
          crosses[idx] === 'golden'
            ? '<span class="kline-tip__badge kline-tip__badge--up">MACD 金叉</span>'
            : crosses[idx] === 'death'
              ? '<span class="kline-tip__badge kline-tip__badge--down">MACD 死叉</span>'
              : ''

        const byName = {}
        for (const p of items) {
          if (!p?.seriesName) continue
          let val = p.data
          if (Array.isArray(val)) val = val.length >= 2 && typeof val[1] === 'number' ? val[1] : val[0]
          byName[p.seriesName] = val
        }
        const maColors = { MA5: '#f59e0b', MA10: '#10b981', MA20: '#c79100', MA60: '#5c6bc0' }
        const maChips = ['MA5', 'MA10', 'MA20', 'MA60']
          .map((name) => {
            const v = byName[name]
            if (v == null) return ''
            return `<span class="kline-tip__chip"><i style="color:${maColors[name]}">${name}</i>${fmtNum(v)}</span>`
          })
          .join('')
        const chipRow = (items) =>
          items
            .filter(([, v]) => v != null && v !== '-')
            .map(([name, v, color, valueColor]) => {
              const num = fmtNum(v, 2)
              const valStyle = valueColor ? ` style="color:${valueColor}"` : ''
              return `<span class="kline-tip__chip"><i style="color:${color}">${name}</i><b${valStyle}>${num}</b></span>`
            })
            .join('')
        const macdVal = byName.MACD
        const macdChips = chipRow([
          ['DIF', byName.DIF, '#1f6f5b'],
          ['DEA', byName.DEA, '#c79100'],
          [
            'MACD',
            macdVal,
            '#86868b',
            macdVal == null || Number.isNaN(Number(macdVal))
              ? null
              : Number(macdVal) >= 0
                ? '#ef5350'
                : '#26a69a',
          ],
        ])
        const kdjChips = chipRow([
          ['K', byName.K, '#c79100'],
          ['D', byName.D, '#5c6bc0'],
          ['J', byName.J, '#6a4c93'],
        ])
        const amount = amounts[idx]
        const amountHtml =
          amount == null || Number.isNaN(Number(amount))
            ? ''
            : `<span><em>额</em>${fmtVol(amount)}</span>`

        return `
<div class="kline-tip__card">
  <div class="kline-tip__head">
    <span class="kline-tip__date">${date}</span>
    ${crossBadge}
  </div>
  <div class="kline-tip__price-row">
    <span class="kline-tip__price" style="color:${pctColor(dayPct)}">${fmtNum(close)}</span>
    <span class="kline-tip__day" style="color:${pctColor(dayPct)}">当日 ${fmtSignedPct(dayPct)}</span>
  </div>
  <div class="kline-tip__metrics">
    <span class="kline-tip__metric">
      <em>${sinceLabel}</em>
      <b style="color:${pctColor(sincePct)}">${sinceText}</b>
    </span>
    <span class="kline-tip__periods">${sincePeriods}周期</span>
  </div>
  <div class="kline-tip__ohlc">
    <span><em>开</em>${fmtNum(open)}</span>
    <span><em>高</em>${fmtNum(high)}</span>
    <span><em>低</em>${fmtNum(low)}</span>
    <span><em>收</em>${fmtNum(close)}</span>
  </div>
  <div class="kline-tip__vol">
    <span><em>量</em>${fmtVol(volumes[idx])}</span>
    ${amountHtml}
  </div>
  ${maChips ? `<div class="kline-tip__row">${maChips}</div>` : ''}
  ${macdChips ? `<div class="kline-tip__row">${macdChips}</div>` : ''}
  ${kdjChips ? `<div class="kline-tip__row">${kdjChips}</div>` : ''}
</div>`
      },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      { left: 56, right: 20, top: 40, height: '38%' },
      { left: 56, right: 20, top: '52%', height: '10%' },
      { left: 56, right: 20, top: '66%', height: '12%' },
      { left: 56, right: 20, top: '82%', height: '10%' },
    ],
    xAxis: [
      { type: 'category', data: dates, boundaryGap: true, axisLine: { onZero: false }, min: 'dataMin', max: 'dataMax' },
      { type: 'category', gridIndex: 1, data: dates, boundaryGap: true, axisLabel: { show: false }, min: 'dataMin', max: 'dataMax' },
      { type: 'category', gridIndex: 2, data: dates, boundaryGap: true, axisLabel: { show: false }, min: 'dataMin', max: 'dataMax' },
      { type: 'category', gridIndex: 3, data: dates, boundaryGap: true, axisLabel: { show: false }, min: 'dataMin', max: 'dataMax' },
    ],
    yAxis: [
      {
        scale: true,
        splitArea: { show: true, areaStyle: { color: ['rgba(255,255,255,0.08)', 'rgba(0,0,0,0.015)'] } },
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.05)' } },
        axisLabel: { color: '#86868b', fontSize: 11 },
      },
      { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false } },
      { scale: true, gridIndex: 2, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false } },
      { min: 0, max: 100, gridIndex: 3, splitNumber: 2, axisLabel: { show: false }, axisLine: { show: false }, axisTick: { show: false }, splitLine: { show: false } },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2, 3], start: 45, end: 100 },
      { show: true, xAxisIndex: [0, 1, 2, 3], type: 'slider', top: '95%', start: 45, end: 100 },
    ],
    series: [
      {
        name: 'K线',
        type: 'candlestick',
        data: ohlc,
        itemStyle: { color: '#ef5350', color0: '#26a69a', borderColor: '#ef5350', borderColor0: '#26a69a' },
      },
      { name: 'MA5', type: 'line', data: ma5, smooth: true, showSymbol: false, lineStyle: { width: 1.2, color: '#f59e0b' } },
      { name: 'MA10', type: 'line', data: ma10, smooth: true, showSymbol: false, lineStyle: { width: 1.2, color: '#10b981' } },
      { name: 'MA20', type: 'line', data: ma20, smooth: true, showSymbol: false, lineStyle: { width: 1.5, color: '#c79100' } },
      { name: 'MA60', type: 'line', data: ma60, smooth: true, showSymbol: false, lineStyle: { width: 1.5, color: '#5c6bc0' } },
      {
        name: '成交量',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes,
        itemStyle: {
          color: (p) => {
            const row = ohlc[p.dataIndex]
            return row && row[1] >= row[0] ? '#ef5350' : '#26a69a'
          },
        },
      },
      { name: 'DIF', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: dif, showSymbol: false, lineStyle: { width: 1, color: '#1f6f5b' } },
      { name: 'DEA', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: dea, showSymbol: false, lineStyle: { width: 1, color: '#c79100' } },
      {
        name: 'MACD',
        type: 'bar',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: hist,
        itemStyle: { color: (p) => (p.data >= 0 ? '#ef5350' : '#26a69a') },
      },
      {
        name: '金叉',
        type: 'scatter',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: goldenPoints,
        symbol: 'triangle',
        symbolSize: 11,
        itemStyle: { color: '#ef5350' },
        label: {
          show: true,
          formatter: '金',
          position: 'top',
          color: '#ef5350',
          fontSize: 10,
          fontWeight: 700,
        },
        z: 10,
      },
      {
        name: '死叉',
        type: 'scatter',
        xAxisIndex: 2,
        yAxisIndex: 2,
        data: deathPoints,
        symbol: 'triangle',
        symbolRotate: 180,
        symbolSize: 11,
        itemStyle: { color: '#26a69a' },
        label: {
          show: true,
          formatter: '死',
          position: 'bottom',
          color: '#26a69a',
          fontSize: 10,
          fontWeight: 700,
        },
        z: 10,
      },
      { name: 'K', type: 'line', xAxisIndex: 3, yAxisIndex: 3, data: kLine, showSymbol: false, lineStyle: { width: 1.2, color: '#c79100' } },
      { name: 'D', type: 'line', xAxisIndex: 3, yAxisIndex: 3, data: dLine, showSymbol: false, lineStyle: { width: 1.2, color: '#5c6bc0' } },
      { name: 'J', type: 'line', xAxisIndex: 3, yAxisIndex: 3, data: jLine, showSymbol: false, lineStyle: { width: 1.2, color: '#6a4c93' } },
    ],
  }, true)
  chart.resize()
}

async function load(refreshQuote = false) {
  if (!code.value) return
  loading.value = true
  try {
    const res = await fetchStockDetail(code.value.trim(), 220, false)
    applyDetail(res.data)
    await loadFundamental()
    if (refreshQuote) {
      await refreshQuoteOnly()
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadFundamental() {
  if (!code.value) return
  fundLoading.value = true
  try {
    const res = await fetchStockFundamental(code.value.trim(), 40, 12)
    fund.value = res.data || null
  } catch (e) {
    fund.value = null
    console.warn('加载基本面失败', e)
  } finally {
    fundLoading.value = false
  }
}

function applyDetail(data) {
  basic.value = data.basic
  note.value = data.note || ''
  bars.value = data.bars || []
  needSyncBars.value = !!data.needSyncBars
  barCount.value = data.barCount ?? bars.value.length
  rs20.value = data.rs20VsHs300
  rs60.value = data.rs60VsHs300
  volumeRatio.value = data.volumeRatio
  renderChart(bars.value)
}

async function refreshQuoteOnly() {
  refreshing.value = true
  try {
    await syncStockBasic(code.value.trim())
    const res = await fetchStockDetail(code.value.trim(), 220, false)
    applyDetail(res.data)
    ElMessage.success('行情已刷新并落库')
  } catch (e) {
    ElMessage.error(e.message || '刷新行情失败')
  } finally {
    refreshing.value = false
  }
}

async function syncDailyBars() {
  if (!code.value) return
  syncingBars.value = true
  try {
    const pure = code.value.trim()
    const res = await syncBars({ codes: [pure] })
    const data = res.data || {}
    // 日线同步只落 K 线，顺带刷一次行情快照，避免现价/估值仍是空
    try {
      await syncStockBasic(pure)
    } catch (quoteErr) {
      console.warn('同步日后刷新行情失败', quoteErr)
    }
    ElMessage.success(`日线同步完成：K线 ${data.barCount ?? 0} 根`)
    const detail = await fetchStockDetail(pure, 220, false)
    applyDetail(detail.data)
  } catch (e) {
    ElMessage.error(e.message || '同步日线失败')
  } finally {
    syncingBars.value = false
  }
}

function go() {
  router.replace(`/stock/${code.value.trim()}`)
  load(false)
}

function onResize() {
  chart?.resize()
}

watch(
  () => route.params.code,
  (v) => {
    if (v) {
      code.value = String(v)
      load(false)
    }
  },
)

onMounted(() => {
  load(false)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeChart()
})

function fmtMv(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (n >= 1e12) return (n / 1e12).toFixed(2) + '万亿'
  if (n >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  return n.toFixed(2)
}

function fmtMoney(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const abs = Math.abs(n)
  const sign = n < 0 ? '-' : ''
  if (abs >= 1e12) return sign + (abs / 1e12).toFixed(2) + '万亿'
  if (abs >= 1e8) return sign + (abs / 1e8).toFixed(2) + '亿'
  if (abs >= 1e4) return sign + (abs / 1e4).toFixed(2) + '万'
  return sign + abs.toFixed(2)
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(digits)
}

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toFixed(2) + '%'
}

function sheetCell(row, idx) {
  const text = row?.texts?.[idx]
  if (text) return text
  return fmtMoney(row?.values?.[idx])
}

function onTabChange(name) {
  if (name === 'chart' && bars.value.length) {
    nextTick(() => renderChart(bars.value))
  }
}
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <h1>{{ basic?.name || '股票详情' }} <span class="code">{{ basic?.code || code }}</span></h1>
        <p>{{ note }}</p>
      </div>
      <div class="actions">
        <el-input v-model="code" style="width: 120px" placeholder="代码" @keyup.enter="go" />
        <el-button type="primary" @click="go">查询</el-button>
        <el-button :loading="refreshing" @click="refreshQuoteOnly">刷新行情</el-button>
        <el-button type="success" :loading="syncingBars" @click="syncDailyBars">同步日线</el-button>
        <el-button @click="router.push({ path: '/backtest', query: { code: code.trim() } })">回测</el-button>
        <el-button @click="router.push({ path: '/paper', query: { code: code.trim(), side: 'BUY' } })">模拟买</el-button>
      </div>
    </header>

    <div class="meta" v-if="basic">
      <div><label>最新价</label><b :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.latestPrice ?? '-' }}</b></div>
      <div><label>涨跌幅</label><b :class="basic.pctChg >= 0 ? 'up' : 'down'">{{ basic.pctChg != null ? basic.pctChg + '%' : '-' }}</b></div>
      <div><label>市盈率</label><span>{{ basic.peTtm ?? '-' }}</span></div>
      <div><label>市净率</label><span>{{ basic.pb ?? '-' }}</span></div>
      <div><label>总市值</label><span>{{ fmtMv(basic.totalMv) }}</span></div>
      <div><label>流通市值</label><span>{{ fmtMv(basic.circMv) }}</span></div>
      <div><label>市场</label><span>{{ basic.market || '-' }}</span></div>
      <div><label>行业</label><span>{{ basic.industry || '-' }}</span></div>
      <div><label>上市</label><span>{{ basic.listDate || '-' }}</span></div>
      <div><label>来源</label><span>{{ basic.source || '-' }}</span></div>
      <div><label>本地日线</label><span>{{ barCount }}</span></div>
      <div><label>RS20 vs沪深300</label><b :class="Number(rs20) >= 0 ? 'up' : 'down'">{{ rs20 != null ? rs20 + 'pp' : '-' }}</b></div>
      <div><label>RS60 vs沪深300</label><b :class="Number(rs60) >= 0 ? 'up' : 'down'">{{ rs60 != null ? rs60 + 'pp' : '-' }}</b></div>
      <div><label>量比</label><b :class="Number(volumeRatio) >= 1.5 ? 'up' : ''">{{ volumeRatio ?? '-' }}</b></div>
    </div>

    <el-alert
      v-if="!loading && bars.length && needSyncBars"
      class="hint"
      type="warning"
      :closable="false"
      show-icon
      :title="`本地仅 ${barCount} 根日线，建议同步补齐后再做指标/回测`"
    />

    <el-tabs v-model="activeTab" class="tabs" @tab-change="onTabChange">
      <el-tab-pane label="行情图表" name="chart">
        <el-empty
          v-if="!loading && !bars.length"
          class="empty-bars"
          description="本地暂无日线，请先同步日线落库"
        >
          <el-button type="primary" :loading="syncingBars" @click="syncDailyBars">同步日线</el-button>
        </el-empty>
        <div v-if="bars.length" class="chart-toolbar">
          <p class="chart-hint">
            悬浮看价格与至今涨跌 · 副图：成交量 / MACD / KDJ · 金叉▲ 死叉▼
          </p>
          <p v-if="macdTip" class="macd-tip">{{ macdTip }}</p>
        </div>
        <div v-if="bars.length" ref="chartRef" class="chart" />
      </el-tab-pane>

      <el-tab-pane label="财务摘要" name="abstract" lazy>
        <div v-loading="fundLoading">
          <p class="fund-note">{{ fund?.note || '加载中…' }}</p>
          <div class="meta fund-kpi" v-if="fund?.latestAbstract">
            <div><label>报告期</label><span>{{ fund.latestAbstract.reportDate || '-' }}</span></div>
            <div><label>净利润</label><b>{{ fmtMoney(fund.latestAbstract.netProfit) }}</b></div>
            <div><label>净利润同比</label><b :class="Number(fund.latestAbstract.netProfitYoy) >= 0 ? 'up' : 'down'">{{ fmtPct(fund.latestAbstract.netProfitYoy) }}</b></div>
            <div><label>扣非净利润</label><b>{{ fmtMoney(fund.latestAbstract.netProfitExcl) }}</b></div>
            <div><label>营业总收入</label><b>{{ fmtMoney(fund.latestAbstract.revenue) }}</b></div>
            <div><label>营收同比</label><b :class="Number(fund.latestAbstract.revenueYoy) >= 0 ? 'up' : 'down'">{{ fmtPct(fund.latestAbstract.revenueYoy) }}</b></div>
            <div><label>EPS</label><span>{{ fmtNum(fund.latestAbstract.epsBasic, 4) }}</span></div>
            <div><label>BPS</label><span>{{ fmtNum(fund.latestAbstract.bps, 4) }}</span></div>
            <div><label>ROE</label><span>{{ fmtPct(fund.latestAbstract.roe) }}</span></div>
            <div><label>净利率</label><span>{{ fmtPct(fund.latestAbstract.netMargin) }}</span></div>
            <div><label>资产负债率</label><span>{{ fmtPct(fund.latestAbstract.debtRatio) }}</span></div>
            <div><label>流动/速动</label><span>{{ fmtNum(fund.latestAbstract.currentRatio) }} / {{ fmtNum(fund.latestAbstract.quickRatio) }}</span></div>
          </div>
          <el-table v-if="fund?.abstracts?.length" :data="fund.abstracts" size="small" stripe height="480" class="fund-table">
            <el-table-column prop="reportDate" label="报告期" width="110" fixed />
            <el-table-column label="净利润" min-width="110"><template #default="{ row }">{{ fmtMoney(row.netProfit) }}</template></el-table-column>
            <el-table-column label="净利同比" width="100"><template #default="{ row }">{{ fmtPct(row.netProfitYoy) }}</template></el-table-column>
            <el-table-column label="营收" min-width="110"><template #default="{ row }">{{ fmtMoney(row.revenue) }}</template></el-table-column>
            <el-table-column label="营收同比" width="100"><template #default="{ row }">{{ fmtPct(row.revenueYoy) }}</template></el-table-column>
            <el-table-column label="EPS" width="90"><template #default="{ row }">{{ fmtNum(row.epsBasic, 4) }}</template></el-table-column>
            <el-table-column label="ROE" width="90"><template #default="{ row }">{{ fmtPct(row.roe) }}</template></el-table-column>
            <el-table-column label="净利率" width="90"><template #default="{ row }">{{ fmtPct(row.netMargin) }}</template></el-table-column>
            <el-table-column label="负债率" width="90"><template #default="{ row }">{{ fmtPct(row.debtRatio) }}</template></el-table-column>
          </el-table>
          <el-empty v-else description="暂无财务摘要，请先同步基本面" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="分析指标" name="indicator" lazy>
        <div v-loading="fundLoading">
          <div class="meta fund-kpi" v-if="fund?.latestIndicator">
            <div><label>报告期</label><span>{{ fund.latestIndicator.reportDate || '-' }}</span></div>
            <div><label>EPS</label><span>{{ fmtNum(fund.latestIndicator.eps, 4) }}</span></div>
            <div><label>扣非EPS</label><span>{{ fmtNum(fund.latestIndicator.epsExcl, 4) }}</span></div>
            <div><label>BPS</label><span>{{ fmtNum(fund.latestIndicator.bps, 4) }}</span></div>
            <div><label>OCFPS</label><span>{{ fmtNum(fund.latestIndicator.ocfps, 4) }}</span></div>
            <div><label>ROE</label><span>{{ fmtPct(fund.latestIndicator.roe) }}</span></div>
            <div><label>ROA</label><span>{{ fmtPct(fund.latestIndicator.roa) }}</span></div>
            <div><label>毛利率</label><span>{{ fmtPct(fund.latestIndicator.grossMargin) }}</span></div>
            <div><label>净利率</label><span>{{ fmtPct(fund.latestIndicator.netMargin) }}</span></div>
            <div><label>营业利润率</label><span>{{ fmtPct(fund.latestIndicator.operateMargin) }}</span></div>
            <div><label>资产负债率</label><span>{{ fmtPct(fund.latestIndicator.debtRatio) }}</span></div>
            <div><label>流动/速动</label><span>{{ fmtNum(fund.latestIndicator.currentRatio) }} / {{ fmtNum(fund.latestIndicator.quickRatio) }}</span></div>
          </div>
          <el-table v-if="fund?.indicators?.length" :data="fund.indicators" size="small" stripe height="480" class="fund-table">
            <el-table-column prop="reportDate" label="报告期" width="110" fixed />
            <el-table-column label="EPS" width="90"><template #default="{ row }">{{ fmtNum(row.eps, 4) }}</template></el-table-column>
            <el-table-column label="加权EPS" width="90"><template #default="{ row }">{{ fmtNum(row.epsWeighted, 4) }}</template></el-table-column>
            <el-table-column label="扣非EPS" width="90"><template #default="{ row }">{{ fmtNum(row.epsExcl, 4) }}</template></el-table-column>
            <el-table-column label="BPS" width="90"><template #default="{ row }">{{ fmtNum(row.bps, 4) }}</template></el-table-column>
            <el-table-column label="OCFPS" width="90"><template #default="{ row }">{{ fmtNum(row.ocfps, 4) }}</template></el-table-column>
            <el-table-column label="ROE" width="90"><template #default="{ row }">{{ fmtPct(row.roe) }}</template></el-table-column>
            <el-table-column label="ROA" width="90"><template #default="{ row }">{{ fmtPct(row.roa) }}</template></el-table-column>
            <el-table-column label="毛利率" width="90"><template #default="{ row }">{{ fmtPct(row.grossMargin) }}</template></el-table-column>
            <el-table-column label="净利率" width="90"><template #default="{ row }">{{ fmtPct(row.netMargin) }}</template></el-table-column>
            <el-table-column label="负债率" width="90"><template #default="{ row }">{{ fmtPct(row.debtRatio) }}</template></el-table-column>
            <el-table-column label="流动比率" width="90"><template #default="{ row }">{{ fmtNum(row.currentRatio) }}</template></el-table-column>
            <el-table-column label="速动比率" width="90"><template #default="{ row }">{{ fmtNum(row.quickRatio) }}</template></el-table-column>
          </el-table>
          <el-empty v-else description="暂无分析指标，请先同步基本面" />
        </div>
      </el-tab-pane>

      <el-tab-pane
        v-for="sheetKey in ['profitSheet', 'balanceSheet', 'cashflowSheet']"
        :key="sheetKey"
        :label="fund?.[sheetKey]?.statementName || ({ profitSheet: '利润表', balanceSheet: '资产负债表', cashflowSheet: '现金流量表' }[sheetKey])"
        :name="sheetKey"
        lazy
      >
        <div v-loading="fundLoading" class="sheet-wrap">
          <el-table
            v-if="fund?.[sheetKey]?.rows?.length"
            :data="fund[sheetKey].rows"
            size="small"
            stripe
            height="560"
            class="fund-table"
          >
            <el-table-column prop="itemName" label="科目" min-width="180" fixed />
            <el-table-column
              v-for="(p, idx) in fund[sheetKey].periods"
              :key="p + '-' + idx"
              :label="String(p)"
              min-width="120"
              align="right"
            >
              <template #default="{ row }">{{ sheetCell(row, idx) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无该报表数据，请先同步基本面" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.header .code {
  color: var(--muted);
  font-size: 18px;
  font-weight: 500;
  margin-left: 8px;
  letter-spacing: -0.02em;
}

.meta {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.meta > div {
  background: var(--glass);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 12px 14px;
  box-shadow: var(--shadow-soft);
  font-variant-numeric: tabular-nums;
}

.meta label {
  display: block;
  color: var(--muted);
  font-size: 11px;
  font-weight: 500;
  margin-bottom: 6px;
}

.hint {
  margin-bottom: 12px;
}

.empty-bars {
  margin: 24px 0;
}

.chart-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px 14px;
  margin: 0 0 10px;
}

.chart-hint {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.01em;
}

.macd-tip {
  margin: 0;
  color: #1d1d1f;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.chart {
  height: 720px;
  width: 100%;
  background: var(--glass-strong);
  backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  -webkit-backdrop-filter: blur(var(--blur)) saturate(var(--saturate));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.tabs {
  margin-top: 4px;
}

.fund-note {
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 12px;
}

.fund-kpi {
  margin-bottom: 14px;
}

.fund-table {
  width: 100%;
  background: transparent;
}

.sheet-wrap {
  min-height: 200px;
}

@media (max-width: 900px) {
  .meta {
    grid-template-columns: 1fr 1fr;
  }

  .chart {
    height: 560px;
  }
}
</style>

<!-- tooltip 挂到 body，需非 scoped -->
<style>
.kline-tip {
  pointer-events: none !important;
  z-index: 40 !important;
}

.kline-tip__card {
  width: 236px;
  box-sizing: border-box;
  padding: 12px 14px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.32);
  border: 1px solid rgba(255, 255, 255, 0.48);
  box-shadow:
    0 12px 32px rgba(15, 23, 42, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(20px) saturate(1.4);
  -webkit-backdrop-filter: blur(20px) saturate(1.4);
  color: #1d1d1f;
  font-family: inherit;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
  overflow: hidden;
}

.kline-tip__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.kline-tip__date {
  font-size: 12px;
  color: #86868b;
  font-weight: 500;
}

.kline-tip__badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.kline-tip__badge--up {
  color: #c62828;
  background: rgba(239, 83, 80, 0.14);
}

.kline-tip__badge--down {
  color: #0f766e;
  background: rgba(38, 166, 154, 0.16);
}

.kline-tip__price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 8px;
  min-width: 0;
}

.kline-tip__price {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.03em;
}

.kline-tip__day {
  flex: 0 0 auto;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
}

.kline-tip__metrics {
  display: flex;
  flex-wrap: nowrap;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 10px;
  min-width: 0;
  white-space: nowrap;
}

.kline-tip__metric {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.25;
  color: #3a3a3c;
}

.kline-tip__metric em {
  font-style: normal;
  color: #86868b;
  font-weight: 500;
  margin-right: 4px;
}

.kline-tip__metric b {
  font-weight: 700;
}

.kline-tip__periods {
  font-size: 12px;
  font-weight: 500;
  color: #86868b;
}

.kline-tip__ohlc,
.kline-tip__vol {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 4px 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #3a3a3c;
}

.kline-tip__vol {
  grid-template-columns: 1fr 1fr;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  color: #6b7280;
  margin-bottom: 8px;
}

.kline-tip__ohlc em,
.kline-tip__vol em {
  font-style: normal;
  color: #a1a1a6;
  margin-right: 3px;
  font-size: 11px;
}

.kline-tip__row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.kline-tip__chip {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.35);
  font-size: 11px;
  color: #3a3a3c;
  font-weight: 600;
}

.kline-tip__chip i {
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
}

.kline-tip__chip b {
  font-weight: 600;
}
</style>
