<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  archiveObserve,
  autoDecideObserve,
  fetchGuideTemplate,
  fetchGuideTemplates,
  listObserve,
  refreshObserve,
  removeObserve,
  saveObserve,
} from '../api/observe'
import { searchStock } from '../api/stock'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture'
import { buildObserveShareSheet, mountObserveShareSheet } from '../utils/observeShareSheet'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const refreshing = ref(false)
const deciding = ref(false)
const saving = ref(false)
const rows = ref([])
const templates = ref([])
const sideTab = ref('BUY')
const statusFilter = ref('')
const keyword = ref('')
const dialogVisible = ref(false)
const guideOpenId = ref(null)
const searchLoading = ref(false)
const searchOptions = ref([])
const sharing = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
const copying = ref(false)
const downloading = ref(false)
let sharePreviewObjectUrl = ''

const form = reactive({
  id: null,
  code: '',
  name: '',
  market: '',
  side: 'BUY',
  reason: '',
  guideText: '',
  triggerType: 'PRICE_ABOVE',
  triggerExpr: '',
  triggerPrice: '',
  stopLoss: '',
  targetPrice: '',
  basePrice: '',
  priority: 3,
  note: '',
  tags: '',
})

const TRIGGER_OPTS = [
  { value: 'PRICE_ABOVE', label: '站上触发价' },
  { value: 'PRICE_BELOW', label: '跌破/触及触发价' },
  { value: 'PCT_FROM_BASE', label: '相对基准涨跌幅%' },
  { value: 'BREAK_HIGH', label: '突破前高' },
  { value: 'MANUAL', label: '手工确认' },
]

function sideOf(row) {
  const blob = `${row?.tags || ''} ${row?.reason || ''} ${row?.guideText || ''}`
  const moodHint =
    row?.side === 'MOOD' ||
    blob.includes('情绪观察') ||
    blob.includes('风向标') ||
    /[,，]情绪/.test(blob) ||
    blob.includes('温度计') ||
    blob.includes('非买入')
  if (moodHint) return 'MOOD'
  const sellHint =
    blob.includes('决策卖出') ||
    blob.includes('持仓卖出') ||
    blob.includes('卖出剧本') ||
    /[,，]卖出/.test(blob) ||
    /卖出[,，]/.test(blob)
  const buyHint =
    blob.includes('决策买入') ||
    blob.includes('买入剧本') ||
    blob.includes('决策观察') ||
    /[,，]买入/.test(blob) ||
    /买入[,，]/.test(blob)
  if (sellHint && !buyHint) return 'SELL'
  if (buyHint && !sellHint) return 'BUY'
  if (row?.side === 'SELL' || row?.side === 'BUY' || row?.side === 'MOOD') return row.side
  if (blob.includes('卖出') || /sell/i.test(blob)) return 'SELL'
  return 'BUY'
}

function actionLabel(row) {
  const side = sideOf(row)
  const st = row.status
  if (side === 'MOOD') {
    if (st === 'STOPPED') return '退潮'
    if (st === 'HIT_TARGET') return '修复'
    return '情绪'
  }
  if (side === 'SELL') {
    if (st === 'TRIGGERED') return '减仓'
    if (st === 'NEAR') return '待卖'
    if (st === 'HIT_TARGET') return '卖完'
    if (st === 'STOPPED') return '作废'
    return '待卖'
  }
  if (st === 'TRIGGERED') return '可执行'
  if (st === 'NEAR') return '接近'
  if (st === 'HIT_TARGET') return '兑现'
  if (st === 'STOPPED') return '止损'
  return '观察'
}

/** 状态色：只有「可执行/接近」才强调，避免满屏红 */
function actTone(row) {
  const side = sideOf(row)
  if (side === 'MOOD') return 'quiet'
  if (side === 'SELL') {
    if (row.status === 'TRIGGERED' || row.status === 'NEAR') return 'go'
    return 'quiet'
  }
  if (row.status === 'TRIGGERED') return 'hot'
  if (row.status === 'NEAR') return 'warm'
  if (row.status === 'HIT_TARGET') return 'ok'
  if (row.status === 'STOPPED') return 'quiet'
  return 'quiet'
}

const activeRows = computed(() => {
  if (statusFilter.value === 'ARCHIVED') return rows.value
  return rows.value.filter((r) => r.status !== 'ARCHIVED')
})

const buyCount = computed(() => activeRows.value.filter((r) => sideOf(r) === 'BUY').length)
const moodCount = computed(() => activeRows.value.filter((r) => sideOf(r) === 'MOOD').length)
const sellCount = computed(() => activeRows.value.filter((r) => sideOf(r) === 'SELL').length)

const visibleRows = computed(() => {
  let list = activeRows.value
  if (sideTab.value === 'BUY') list = list.filter((r) => sideOf(r) === 'BUY')
  if (sideTab.value === 'MOOD') list = list.filter((r) => sideOf(r) === 'MOOD')
  if (sideTab.value === 'SELL') list = list.filter((r) => sideOf(r) === 'SELL')
  if (statusFilter.value === 'READY') {
    list = list.filter((r) => r.status === 'TRIGGERED' || r.status === 'NEAR')
  } else if (statusFilter.value) {
    list = list.filter((r) => r.status === statusFilter.value)
  }
  return [...list].sort((a, b) => {
    const order = { TRIGGERED: 0, NEAR: 1, WATCHING: 2, HIT_TARGET: 3, STOPPED: 4, ARCHIVED: 5 }
    const da = order[a.status] ?? 9
    const db = order[b.status] ?? 9
    if (da !== db) return da - db
    const rank = { BUY: 0, MOOD: 1, SELL: 2 }
    const sa = rank[sideOf(a)] ?? 9
    const sb = rank[sideOf(b)] ?? 9
    if (sa !== sb) return sa - sb
    return (a.priority ?? 3) - (b.priority ?? 3)
  })
})

const stats = computed(() => ({
  buy: buyCount.value,
  mood: moodCount.value,
  sell: sellCount.value,
  buyReady: activeRows.value.filter(
    (r) => sideOf(r) === 'BUY' && (r.status === 'TRIGGERED' || r.status === 'NEAR'),
  ).length,
  sellReady: activeRows.value.filter(
    (r) => sideOf(r) === 'SELL' && (r.status === 'TRIGGERED' || r.status === 'NEAR'),
  ).length,
}))

async function load() {
  loading.value = true
  try {
    const res = await listObserve({
      keyword: keyword.value || undefined,
      // READY/ARCHIVED 走服务端；其余状态仍前端筛（现场评估与落库可能不一致）
      status:
        statusFilter.value === 'ARCHIVED' || statusFilter.value === 'READY'
          ? statusFilter.value
          : undefined,
    })
    rows.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 切换状态；READY/归档需重新请求后端 */
async function setStatusFilter(next) {
  const prev = statusFilter.value
  statusFilter.value = next
  const serverStatuses = new Set(['ARCHIVED', 'READY'])
  if (serverStatuses.has(prev) || serverStatuses.has(next)) {
    await load()
  }
}

async function loadTemplates() {
  try {
    const res = await fetchGuideTemplates()
    templates.value = res.data || []
  } catch {
    templates.value = []
  }
}

async function onRefresh() {
  refreshing.value = true
  try {
    await refreshObserve()
    await load()
    ElMessage.success('已按现价重估')
  } catch (e) {
    ElMessage.error(e.message || '刷新失败')
  } finally {
    refreshing.value = false
  }
}

async function onAutoDecide() {
  deciding.value = true
  try {
    const res = await autoDecideObserve({ groupName: '我的自选' })
    const d = res.data || {}
    ElMessage.success(
      `今日可买 ${d.buys?.length ?? 0} · 写入观察 ${d.observeUpserted ?? 0}（含买入观察/情绪风向）`,
    )
    await load()
  } catch (e) {
    ElMessage.error(e.message || '自动决策失败')
  } finally {
    deciding.value = false
  }
}

function resetForm() {
  form.id = null
  form.code = ''
  form.name = ''
  form.market = ''
  form.side = 'BUY'
  form.reason = ''
  form.guideText = ''
  form.triggerType = 'PRICE_ABOVE'
  form.triggerExpr = ''
  form.triggerPrice = ''
  form.stopLoss = ''
  form.targetPrice = ''
  form.basePrice = ''
  form.priority = 3
  form.note = ''
  form.tags = ''
}

function openCreate(preset = {}) {
  resetForm()
  if (preset.code) form.code = preset.code
  if (preset.name) form.name = preset.name
  if (preset.market) form.market = preset.market
  if (preset.side) form.side = preset.side
  dialogVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.code = row.code || ''
  form.name = row.name || ''
  form.market = row.market || ''
  form.side = sideOf(row)
  form.reason = row.reason || ''
  form.guideText = row.guideText || ''
  form.triggerType = row.triggerType || 'PRICE_ABOVE'
  form.triggerExpr = row.triggerExpr || ''
  form.triggerPrice = row.triggerPrice ?? ''
  form.stopLoss = row.stopLoss ?? ''
  form.targetPrice = row.targetPrice ?? ''
  form.basePrice = row.basePrice ?? ''
  form.priority = row.priority ?? 3
  form.note = row.note || ''
  form.tags = row.tags || ''
  dialogVisible.value = true
}

async function applyTemplate(reason) {
  const key = reason || form.reason || templates.value[0]?.reason
  if (!key) return
  try {
    const res = await fetchGuideTemplate(key)
    const t = res.data
    if (!t) return
    form.reason = t.reason || form.reason
    form.guideText = t.guideText || ''
    form.triggerType = t.triggerType || form.triggerType
    form.triggerExpr = t.triggerExpr || ''
    form.tags = t.tags || form.tags
    ElMessage.success(`已套用「${t.reason}」`)
  } catch (e) {
    ElMessage.error(e.message || '模板加载失败')
  }
}

async function onSearchStock(q) {
  const query = String(q || '').trim()
  if (query.length < 1) {
    searchOptions.value = []
    return
  }
  searchLoading.value = true
  try {
    const res = await searchStock(query, 12)
    searchOptions.value = (res.data || []).map((x) => ({
      value: x.code,
      label: `${x.code} ${x.name || ''}`,
      name: x.name,
      market: x.market,
    }))
  } catch {
    searchOptions.value = []
  } finally {
    searchLoading.value = false
  }
}

function onPickStock(val) {
  const hit = searchOptions.value.find((x) => x.value === val)
  if (hit) {
    form.code = hit.value
    form.name = hit.name || form.name
    form.market = hit.market || form.market
  }
}

async function onSave() {
  if (!String(form.code || '').trim()) {
    ElMessage.warning('请填写证券代码')
    return
  }
  saving.value = true
  try {
    await saveObserve({
      id: form.id,
      code: String(form.code).trim(),
      name: String(form.name || '').trim() || null,
      market: String(form.market || '').trim() || null,
      side: form.side || 'BUY',
      reason: String(form.reason || '').trim() || null,
      guideText: String(form.guideText || '').trim() || null,
      triggerType: form.triggerType,
      triggerExpr: String(form.triggerExpr || '').trim() || null,
      triggerPrice: form.triggerPrice === '' ? null : Number(form.triggerPrice),
      stopLoss: form.stopLoss === '' ? null : Number(form.stopLoss),
      targetPrice: form.targetPrice === '' ? null : Number(form.targetPrice),
      basePrice: form.basePrice === '' ? null : Number(form.basePrice),
      priority: Number(form.priority || 3),
      note: String(form.note || '').trim() || null,
      tags: String(form.tags || '').trim() || null,
    })
    ElMessage.success(form.id ? '已更新' : '已加入')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`删除 ${row.code} ${row.name || ''}？`, '删除', { type: 'warning' })
    await removeObserve(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

async function onArchive(row) {
  try {
    await archiveObserve(row.id)
    ElMessage.success('已归档')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '归档失败')
  }
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toFixed(digits)
}

function fmtPct(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function strategyLabel(row) {
  const id = row.strategyId || ''
  if (id === 'S1') return 'S1均线趋势'
  if (id === 'S2') return 'S2 RSI回调'
  if (id === 'S3') return 'S3突破放量'
  if (id === 'MOOD' || id === 'HOT') return '情绪风向'
  return id || '策略'
}

/** 去掉与顶部芯片重复的「策略/估值」行，避免中间叠字 */
function displayPickReasons(row) {
  const list = row?.pickReasons || []
  const out = []
  for (const raw of list) {
    const text = String(raw || '').trim()
    if (!text) continue
    if (text.startsWith('策略：') || text.startsWith('策略:')) continue
    if (text.startsWith('估值：') || text.startsWith('估值:')) continue
    out.push(text)
    if (out.length >= 3) break
  }
  return out
}

function shortReason(row) {
  const side = sideOf(row)
  let raw = String(row.reason || '')
    .replace(/^决策关注\s*·\s*可执行\s*·\s*/, '')
    .replace(/^决策观察\s*·\s*/, '')
    .replace(/^情绪观察\s*·\s*/, '')
    .replace(/^情绪观察\s*[：:]\s*/, '')
    .replace(/^决策买入\s*·\s*/, '')
    .replace(/^决策卖出\s*·\s*/, '')
    .replace(/^观察\s*[：:]\s*/, '')
    .replace(/^持仓卖出\s*[：:]\s*/, '')
    .replace(/^买入\s*[：:]\s*/, '')
    .replace(/^加仓\s*[：:]\s*/, '')
  if (side === 'SELL') {
    raw = raw.replace(/持仓卖出\s*[：:]\s*/g, '')
  }
  const prefix = side === 'SELL' ? '卖出 · ' : side === 'MOOD' ? '情绪 · ' : '观察 · '
  const body = raw.length > 72 ? raw.slice(0, 72) + '…' : raw || '—'
  return prefix + body
}

/** 从理由/备注提取估值×策略联动提示 */
function linkHintOf(row) {
  const blob = `${row?.reason || ''} ${row?.note || ''} ${row?.statusHint || ''}`
  if (blob.includes('低估回调优先')) return '低估回调优先'
  if (blob.includes('高估突破降权')) return '高估突破降权'
  return ''
}

function statusTone(status) {
  if (status === 'NEAR') return 'near'
  if (status === 'TRIGGERED') return 'trig'
  if (status === 'HIT_TARGET') return 'ok'
  if (status === 'STOPPED') return 'stop'
  return ''
}

function toggleGuide(id) {
  guideOpenId.value = guideOpenId.value === id ? null : id
}

function filterTextForShare() {
  const sideMap = { BUY: '买入观察', MOOD: '情绪风向', SELL: '历史卖出', ALL: '全部方向' }
  const statusMap = {
    '': '全部状态',
    READY: '优先处理',
    TRIGGERED: '可执行',
    NEAR: '接近',
    WATCHING: '观察中',
    ARCHIVED: '已归档',
  }
  const side = sideMap[sideTab.value] || '全部方向'
  const status = statusMap[statusFilter.value] ?? '全部状态'
  return `${side} · ${status}`
}

function shareRowsPayload() {
  return visibleRows.value.map((row) => ({
    side: sideOf(row),
    code: row.code,
    name: row.name,
    action: actionLabel(row),
    latestPrice: row.latestPrice,
    pctChg: row.pctChg,
    strategy: row.strategyName || strategyLabel(row),
    valuationLabel: row.valuationLabel,
    reason: displayPickReasons(row)[0] || shortReason(row),
    triggerPrice: row.triggerPrice,
    stopLoss: row.stopLoss,
    targetPrice: row.targetPrice,
    pctToTrigger: row.pctToTrigger,
    pctToStop: row.pctToStop,
    pctToTarget: row.pctToTarget,
    statusHint: row.statusHint,
  }))
}

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

async function captureObserveShare() {
  await nextTick()
  if (!visibleRows.value.length) throw new Error('暂无观察标的')
  const titleDate = new Date().toISOString().slice(0, 10)
  const sheet = buildObserveShareSheet({
    titleDate,
    filterText: filterTextForShare(),
    stats: stats.value,
    rows: shareRowsPayload(),
  })
  const mounted = mountObserveShareSheet(sheet)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = 920
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    const dpr = Math.max(window.devicePixelRatio || 1, 2)
    const maxEdge = 14000
    const scaleCap = maxEdge / Math.max(width, height)
    const scale = Math.min(dpr, Math.max(1.75, scaleCap))
    return await captureElementBlob(sheet, {
      scale,
      width,
      height,
      backgroundColor: '#ffffff',
      style: {
        width: `${width}px`,
        height: `${height}px`,
        overflow: 'visible',
        transform: 'none',
        margin: '0',
        opacity: '1',
      },
    })
  } finally {
    mounted.dispose()
  }
}

async function openShare() {
  if (!visibleRows.value.length) {
    ElMessage.warning('暂无观察标的可分享')
    return
  }
  sharing.value = true
  try {
    const blob = await captureObserveShare()
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
    const blob = await captureObserveShare()
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
    const blob = await captureObserveShare()
    downloadBlob(blob, shareFilename('apex_observe', filterTextForShare()))
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

let keywordTimer
watch(keyword, () => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(load, 280)
})

onMounted(async () => {
  await Promise.all([load(), loadTemplates()])
  const qCode = route.query.code
  const qName = route.query.name
  if (qCode) {
    openCreate({ code: String(qCode), name: qName ? String(qName) : '' })
  }
})

onBeforeUnmount(() => {
  revokeSharePreview()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <header class="header">
      <div>
        <p class="eyebrow">灵枢 · Observe</p>
        <h1>观察池</h1>
        <p>买入机会 + 情绪风向标；优先处理「接近 / 可执行」</p>
      </div>
      <div class="actions">
        <button type="button" class="btn btn-primary" :disabled="deciding" @click="onAutoDecide">
          {{ deciding ? '决策中…' : '自动决策' }}
        </button>
        <button type="button" class="btn btn-ghost" :disabled="refreshing" @click="onRefresh">
          {{ refreshing ? '评估中…' : '重估' }}
        </button>
        <button
          type="button"
          class="btn btn-ghost"
          :disabled="sharing || !visibleRows.length"
          @click="openShare"
        >
          {{ sharing ? '生成中…' : '分享截图' }}
        </button>
        <button type="button" class="btn btn-ghost" @click="openCreate()">手动加</button>
        <a
          class="btn btn-text"
          href="http://127.0.0.1:8080/apex/api/export/observe"
          target="_blank"
          rel="noopener"
        >导出CSV</a>
        <button type="button" class="btn btn-text" @click="router.push('/decision')">决策明细</button>
      </div>
    </header>

    <div class="summary">
      <button type="button" class="sum buy" :class="{ on: sideTab === 'BUY' }" @click="sideTab = sideTab === 'BUY' ? 'ALL' : 'BUY'">
        <span>买入观察</span>
        <b>{{ stats.buy }}</b>
        <small v-if="stats.buyReady">{{ stats.buyReady }} 接近/可执行</small>
      </button>
      <button type="button" class="sum mood" :class="{ on: sideTab === 'MOOD' }" @click="sideTab = sideTab === 'MOOD' ? 'ALL' : 'MOOD'">
        <span>情绪风向</span>
        <b>{{ stats.mood }}</b>
        <small>非买卖</small>
      </button>
      <button
        v-if="stats.sell > 0"
        type="button"
        class="sum sell"
        :class="{ on: sideTab === 'SELL' }"
        @click="sideTab = sideTab === 'SELL' ? 'ALL' : 'SELL'"
      >
        <span>历史卖出</span>
        <b>{{ stats.sell }}</b>
        <small>请改看决策页</small>
      </button>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜代码/名称"
        class="search"
      />
    </div>

    <div class="status-chips">
      <button type="button" class="chip" :class="{ on: !statusFilter }" @click="setStatusFilter('')">全部状态</button>
      <button type="button" class="chip ready" :class="{ on: statusFilter === 'READY' }" @click="setStatusFilter('READY')">
        优先处理 {{ stats.buyReady || 0 }}
      </button>
      <button type="button" class="chip" :class="{ on: statusFilter === 'TRIGGERED' }" @click="setStatusFilter('TRIGGERED')">可执行</button>
      <button type="button" class="chip" :class="{ on: statusFilter === 'NEAR' }" @click="setStatusFilter('NEAR')">接近</button>
      <button type="button" class="chip" :class="{ on: statusFilter === 'WATCHING' }" @click="setStatusFilter('WATCHING')">观察中</button>
      <button type="button" class="chip" :class="{ on: statusFilter === 'ARCHIVED' }" @click="setStatusFilter('ARCHIVED')">已归档</button>
    </div>

    <el-empty
      v-if="!loading && !activeRows.length"
      description="还没有观察标的，先跑自动决策抓买点信号与情绪风向标"
    >
      <button type="button" class="btn btn-primary" :disabled="deciding" @click="onAutoDecide">
        {{ deciding ? '决策中…' : '自动决策' }}
      </button>
    </el-empty>

    <div v-else-if="!visibleRows.length" class="list-empty">当前筛选下没有标的</div>

    <div v-else class="cards">
      <article
        v-for="row in visibleRows"
        :key="row.id"
        class="card"
        :class="[
          sideOf(row) === 'BUY' ? 'buy' : sideOf(row) === 'MOOD' ? 'mood' : 'sell',
          statusTone(row.status),
        ]"
      >
        <div class="card-top">
          <div class="title">
            <span
              class="side-tag"
              :class="sideOf(row) === 'BUY' ? 'buy' : sideOf(row) === 'MOOD' ? 'mood' : 'sell'"
            >
              {{ sideOf(row) === 'BUY' ? '买' : sideOf(row) === 'MOOD' ? '情' : '卖' }}
            </span>
            <button type="button" class="code" @click="router.push(`/stock/${row.code}`)">
              {{ row.code }}
            </button>
            <span class="name">{{ row.name || '' }}</span>
            <span
              class="act"
              :class="[actTone(row), sideOf(row) === 'MOOD' ? 'mood' : '']"
            >{{ actionLabel(row) }}</span>
          </div>
          <div class="price">
            <b :class="Number(row.pctChg) > 0 ? 'up' : Number(row.pctChg) < 0 ? 'down' : ''">
              {{ fmtNum(row.latestPrice) }}
            </b>
            <small :class="Number(row.pctChg) > 0 ? 'up' : Number(row.pctChg) < 0 ? 'down' : ''">
              {{ fmtPct(row.pctChg) }}
            </small>
          </div>
        </div>

        <div class="card-main">
          <div class="pick">
            <div class="pick-head">
              <span class="strategy">{{ row.strategyName || strategyLabel(row) }}</span>
              <span class="setup">{{ row.setupStyle || '信号观察' }}</span>
              <span
                v-if="row.valuationLabel"
                class="val-chip"
                :class="{
                  cheap: row.valuationLevel === 'UNDERVALUED' || row.valuationLevel === 'SLIGHTLY_CHEAP',
                  rich: row.valuationLevel === 'OVERVALUED' || row.valuationLevel === 'SLIGHTLY_EXPENSIVE',
                }"
                @click.stop="router.push({ path: '/valuation', query: { code: row.code } })"
              >估·{{ row.valuationLabel }}</span>
              <span
                v-if="linkHintOf(row)"
                class="link-chip"
                :class="{ down: linkHintOf(row).includes('降权') }"
              >{{ linkHintOf(row) }}</span>
              <span v-if="row.pct2d != null" class="span-muted">2日 {{ fmtPct(row.pct2d) }}</span>
              <span v-if="row.pct5d != null" class="span-muted">5日 {{ fmtPct(row.pct5d) }}</span>
            </div>
            <ul v-if="displayPickReasons(row).length" class="pick-reasons">
              <li v-for="(r, idx) in displayPickReasons(row)" :key="idx">{{ r }}</li>
            </ul>
            <div v-else class="reason">{{ shortReason(row) }}</div>
            <div class="risks">
              <span
                v-for="(rf, idx) in (row.riskFlags || []).slice(0, 3)"
                :key="'rf'+idx"
                class="risk"
              >{{ rf }}</span>
            </div>
          </div>

          <div class="hint">{{ row.statusHint || ' ' }}</div>

          <div class="triple">
            <div>
              <em>{{ sideOf(row) === 'MOOD' ? '现价' : '触发' }}</em>
              <b>{{ fmtNum(row.triggerPrice) }}</b>
              <small>{{ fmtPct(row.pctToTrigger) }}</small>
            </div>
            <div>
              <em>{{ sideOf(row) === 'MOOD' ? '退潮' : '止损' }}</em>
              <b>{{ fmtNum(row.stopLoss) }}</b>
              <small>{{ fmtPct(row.pctToStop) }}</small>
            </div>
            <div>
              <em>{{ sideOf(row) === 'MOOD' ? '修复' : '目标' }}</em>
              <b>{{ fmtNum(row.targetPrice) }}</b>
              <small>{{ fmtPct(row.pctToTarget) }}</small>
            </div>
          </div>

          <div class="tech-block">
            <div class="tech-sum">{{ row.techSummary || ' ' }}</div>
            <div class="tech-chips">
              <span
                v-for="sig in (row.techSignals || []).slice(0, 8)"
                :key="sig.key"
                class="tech"
                :class="sig.hit ? 'on' : 'off'"
                :title="sig.detail || sig.label"
              >{{ sig.label }}</span>
            </div>
          </div>

          <pre v-if="guideOpenId === row.id && row.guideText" class="guide">{{ row.guideText }}</pre>
        </div>

        <div class="card-actions">
          <button type="button" class="op" @click="toggleGuide(row.id)">
            {{ guideOpenId === row.id ? '收起指导' : '完整指导' }}
          </button>
          <button type="button" class="op" @click="openEdit(row)">编辑</button>
          <button type="button" class="op muted" @click="onArchive(row)">归档</button>
          <button type="button" class="op danger" @click="onRemove(row)">删除</button>
        </div>
      </article>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑观察项' : '加入观察池'"
      width="560px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item label="方向" required>
          <el-radio-group v-model="form.side">
            <el-radio-button value="BUY">买入观察</el-radio-button>
            <el-radio-button value="MOOD">情绪风向</el-radio-button>
            <el-radio-button value="SELL">卖出</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="代码" required>
          <el-select
            v-model="form.code"
            filterable
            remote
            clearable
            allow-create
            default-first-option
            :remote-method="onSearchStock"
            :loading="searchLoading"
            placeholder="代码或名称"
            style="width: 100%"
            :disabled="!!form.id"
            @change="onPickStock"
          >
            <el-option
              v-for="opt in searchOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="原因">
          <div class="reason-row">
            <el-input v-model="form.reason" />
            <el-dropdown trigger="click" @command="applyTemplate">
              <button type="button" class="btn btn-ghost sm">模板</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="t in templates" :key="t.reason" :command="t.reason">
                    {{ t.reason }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-form-item>
        <el-form-item label="指导">
          <el-input v-model="form.guideText" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="触发类型">
          <el-select v-model="form.triggerType" style="width: 100%">
            <el-option v-for="o in TRIGGER_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-input v-model="form.triggerExpr" />
        </el-form-item>
        <div class="price-grid">
          <el-form-item label="触发价"><el-input v-model="form.triggerPrice" /></el-form-item>
          <el-form-item label="止损价"><el-input v-model="form.stopLoss" /></el-form-item>
          <el-form-item label="目标价"><el-input v-model="form.targetPrice" /></el-form-item>
          <el-form-item label="基准价"><el-input v-model="form.basePrice" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <button type="button" class="btn btn-ghost" @click="dialogVisible = false">取消</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="onSave">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="shareOpen"
      title="分享观察池截图"
      width="96vw"
      top="3vh"
      append-to-body
      destroy-on-close
      class="observe-share-dialog"
      @closed="revokeSharePreview"
    >
      <p class="share-tip">按当前筛选生成分享图；可复制或下载 PNG 后发微信/社群。</p>
      <div class="share-stage">
        <img v-if="sharePreviewUrl" :src="sharePreviewUrl" alt="观察池分享预览" />
        <el-empty v-else description="预览生成中…" />
      </div>
      <template #footer>
        <el-button @click="closeShare">关闭</el-button>
        <el-button :loading="copying" @click="onCopyShare">复制图片</el-button>
        <el-button type="primary" :loading="downloading" @click="onDownloadShare">下载 PNG</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 20px 24px 56px;
  user-select: none;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.header h1 {
  margin: 0 0 4px;
  font-size: 22px;
  letter-spacing: -0.02em;
}

.header p {
  margin: 0;
  font-size: 13px;
  color: var(--slate, #64748b);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.btn {
  border: 0;
  border-radius: 10px;
  padding: 8px 14px;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, opacity 0.15s ease;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--ink, #1d1d1f);
  color: #fff;
}

.btn-primary:hover:not(:disabled) {
  background: #000;
}

.btn-ghost {
  background: rgba(0, 0, 0, 0.04);
  color: var(--ink, #1d1d1f);
}

.btn-ghost:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.07);
}

.btn-text {
  background: transparent;
  color: var(--accent, #0071e3);
  font-weight: 500;
  padding-left: 8px;
  padding-right: 8px;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.btn.sm {
  padding: 6px 10px;
  font-size: 12px;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--accent, #0071e3);
  text-transform: uppercase;
}

.summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: stretch;
  margin-bottom: 10px;
}

.status-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.status-chips .chip {
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.55);
  border-radius: 980px;
  padding: 5px 12px;
  font-size: 12px;
  color: var(--ink-soft, #3a3a3c);
  cursor: pointer;
}

.status-chips .chip.on {
  background: var(--accent-soft, rgba(0, 113, 227, 0.12));
  color: var(--accent, #0071e3);
  border-color: rgba(0, 113, 227, 0.25);
  font-weight: 650;
}

.status-chips .chip.ready.on {
  background: rgba(255, 159, 10, 0.14);
  color: #b36b00;
  border-color: rgba(255, 159, 10, 0.35);
}

.sum {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  column-gap: 12px;
  row-gap: 2px;
  align-items: center;
  min-width: 168px;
  padding: 12px 16px;
  border-radius: 12px;
  border: 1px solid var(--line, #e8e8ed);
  background: #fff;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.sum span {
  grid-column: 1;
  grid-row: 1;
  font-size: 13px;
  color: var(--slate, #64748b);
}

.sum b {
  grid-column: 2;
  grid-row: 1 / span 2;
  font-size: 22px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  align-self: center;
}

.sum small {
  grid-column: 1;
  grid-row: 2;
  margin: 0;
  font-size: 11px;
  color: var(--muted, #8e8e93);
  white-space: nowrap;
}

.sum.buy b { color: var(--ink, #1d1d1f); }
.sum.mood b { color: #8a5a00; }
.sum.sell b { color: #1f6b3a; }
.sum.on {
  box-shadow: 0 0 0 2px rgba(0, 113, 227, 0.25);
}

.search {
  width: 200px;
  margin-left: auto;
}

.list-empty {
  padding: 40px 12px;
  text-align: center;
  font-size: 13px;
  color: var(--muted, #8e8e93);
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
  align-items: stretch;
}

.card {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  border: 1px solid var(--line, #e8e8ed);
  border-radius: 14px;
  padding: 16px 16px 12px;
  /* 实底 + 关掉全局 glass blur，避免中间字出现重影挡住正文 */
  background: #fff;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  border-top: 3px solid rgba(0, 0, 0, 0.08);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  overflow: hidden;
  isolation: isolate;
}

.card-main {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  background: #fff;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.card.near:hover { background: #fffbf2; }
.card.trig:hover { background: #f5f9ff; }
.card.mood:hover { background: #fffaf0; }
.card.sell.trig:hover { background: #f3fbf5; }

.card.buy { border-top-color: rgba(0, 0, 0, 0.14); }
.card.mood { border-top-color: rgba(180, 120, 20, 0.55); background: #fffaf0; }
.card.sell { border-top-color: rgba(52, 199, 89, 0.55); }
.card.near { background: #fffbf2; border-top-color: rgba(200, 140, 20, 0.65); }
.card.trig { background: #f5f9ff; border-top-color: rgba(0, 113, 227, 0.55); }
.card.sell.trig { background: #f3fbf5; border-top-color: rgba(52, 199, 89, 0.65); }
.card.ok { border-top-color: #34c759; }
.card.stop { opacity: 0.82; }

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
  min-height: 44px;
}

.title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1 1 auto;
}

.side-tag {
  width: 24px;
  height: 24px;
  margin: 1px 2px 0 0;
  border-radius: 7px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 750;
  color: #fff;
  flex: 0 0 auto;
}

.side-tag.buy { background: #1d1d1f; }
.side-tag.mood { background: #8a5a00; }
.side-tag.sell { background: #1f6b3a; }

.code {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-weight: 750;
  color: var(--accent, #0071e3);
  cursor: pointer;
}

.name {
  font-size: 13px;
  color: var(--ink-soft, #3a3a3c);
  max-width: 6.5em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.act {
  font-size: 12px;
  font-weight: 700;
  padding: 1px 8px;
  border-radius: 999px;
}

.act.quiet {
  background: rgba(0, 0, 0, 0.05);
  color: var(--slate, #64748b);
}

.act.warm {
  background: rgba(255, 196, 80, 0.22);
  color: #8a5a00;
}

.act.hot {
  background: rgba(0, 113, 227, 0.12);
  color: var(--accent, #0071e3);
}

.act.go {
  background: rgba(52, 199, 89, 0.16);
  color: #1f6b3a;
}

.act.ok {
  background: rgba(52, 199, 89, 0.12);
  color: #1f6b3a;
}

.act.mood {
  background: rgba(255, 196, 80, 0.18);
  color: #8a5a00;
}

.price {
  text-align: right;
  font-variant-numeric: tabular-nums;
  flex: 0 0 auto;
  min-width: 72px;
}

.price b {
  display: block;
  font-size: 18px;
}

.price small {
  font-size: 12px;
}

.hint {
  position: relative;
  z-index: 1;
  font-size: 12px;
  color: var(--slate, #64748b);
  margin: 0 0 10px;
  line-height: 1.45;
  min-height: 1.45em;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  background: transparent;
}

.triple {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin: 0 0 10px;
  padding: 6px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.triple > div {
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.035);
  min-width: 0;
}

.triple em {
  display: block;
  font-style: normal;
  font-size: 11px;
  color: var(--muted, #8e8e93);
}

.triple b {
  display: block;
  font-variant-numeric: tabular-nums;
  font-size: 14px;
  margin: 2px 0;
  color: var(--ink, #1d1d1f);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.triple small {
  display: block;
  font-size: 11px;
  color: var(--slate, #64748b);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.pick {
  position: relative;
  z-index: 1;
  margin: 0 0 8px;
  flex: 0 1 auto;
  min-height: 0;
  overflow: hidden;
}

.pick-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  min-height: 26px;
}

.pick-head .strategy {
  font-size: 12px;
  font-weight: 700;
  color: var(--ink, #1d1d1f);
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 8px;
  border-radius: 6px;
}

.pick-head .setup {
  font-size: 12px;
  font-weight: 650;
  color: #0b4ea2;
  background: rgba(0, 113, 227, 0.08);
  padding: 2px 8px;
  border-radius: 6px;
}

.pick-head .val-chip {
  font-size: 12px;
  font-weight: 650;
  color: #3a3a3c;
  background: rgba(0, 0, 0, 0.05);
  padding: 2px 8px;
  border-radius: 6px;
  cursor: pointer;
}

.pick-head .val-chip.cheap {
  color: #1f6b3a;
  background: rgba(31, 107, 58, 0.1);
}

.pick-head .val-chip.rich {
  color: #8a3b28;
  background: rgba(138, 59, 40, 0.1);
}

.pick-head .link-chip {
  font-size: 11px;
  font-weight: 650;
  color: #1f6b3a;
  background: rgba(31, 107, 58, 0.1);
  padding: 2px 8px;
  border-radius: 6px;
}

.pick-head .link-chip.down {
  color: #8a3b28;
  background: rgba(138, 59, 40, 0.1);
}

.pick-head .span-muted {
  font-size: 11px;
  color: var(--muted, #8e8e93);
  font-variant-numeric: tabular-nums;
}

.pick-reasons {
  margin: 0;
  padding: 0 0 0 16px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-soft, #3a3a3c);
  max-height: 5.8em;
  overflow: hidden;
}

.pick-reasons li {
  margin: 2px 0;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.risks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
  min-height: 22px;
}

.risk {
  font-size: 11px;
  font-weight: 650;
  padding: 2px 7px;
  border-radius: 6px;
  background: rgba(255, 196, 80, 0.2);
  color: #8a5a00;
  border: 1px solid rgba(180, 120, 20, 0.2);
}

.reason {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink, #1d1d1f);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 3.9em;
}

.tech-block {
  margin-bottom: 8px;
  min-height: 56px;
}

.tech-sum {
  font-size: 12px;
  color: var(--slate, #64748b);
  margin-bottom: 6px;
  min-height: 1.2em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tech-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 48px;
  overflow: hidden;
}

.tech {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 999px;
  border: 1px solid transparent;
  line-height: 1.3;
}

/* 技术命中用墨色/蓝强调，涨跌红只留给现价涨跌 */
.tech.on {
  background: rgba(0, 0, 0, 0.06);
  color: var(--ink, #1d1d1f);
  border-color: rgba(0, 0, 0, 0.1);
  font-weight: 650;
}

.card.trig .tech.on,
.card.near .tech.on {
  background: rgba(0, 113, 227, 0.08);
  color: #0b4ea2;
  border-color: rgba(0, 113, 227, 0.18);
}

.tech.off {
  background: transparent;
  color: #b0b0b5;
  border-color: rgba(0, 0, 0, 0.06);
}

.guide {
  margin: 0 0 8px;
  padding: 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.03);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.card-actions {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 2px;
  margin-top: auto;
  border-top: 1px solid var(--line, #e8e8ed);
  padding-top: 8px;
  flex: 0 0 auto;
}

.op {
  border: 0;
  background: transparent;
  padding: 4px 8px;
  font: inherit;
  font-size: 12px;
  color: var(--slate, #64748b);
  cursor: pointer;
  border-radius: 6px;
}

.op:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--ink, #1d1d1f);
}

.op.danger:hover {
  color: var(--up, #ff3b30);
}

.op.muted {
  opacity: 0.75;
}

.up { color: var(--up, #ff3b30); }
.down { color: var(--down, #34c759); }

.reason-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.price-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 8px;
}

@media (max-width: 960px) {
  .search {
    margin-left: 0;
    width: 100%;
  }

  .header {
    flex-direction: column;
  }
}

.share-tip {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--muted, #8a8f98);
}

.share-stage {
  max-height: min(72vh, 860px);
  overflow: auto;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  background: #f5f5f7;
  text-align: center;
}

.share-stage img {
  display: block;
  width: min(100%, 920px);
  margin: 0 auto;
  height: auto;
}
</style>

<style>
.observe-share-dialog.el-dialog {
  max-width: 1100px;
  margin-bottom: 4vh;
}

.observe-share-dialog .el-dialog__body {
  padding-top: 8px;
}

.observe-share-dialog .share-tip {
  margin: 0 0 10px;
  font-size: 13px;
  color: #8a8f98;
}

.observe-share-dialog .share-stage {
  max-height: min(72vh, 860px);
  overflow: auto;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  background: #f5f5f7;
  text-align: center;
}

.observe-share-dialog .share-stage img {
  display: block;
  width: min(100%, 920px);
  margin: 0 auto;
  height: auto;
}
</style>
