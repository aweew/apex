<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { fetchLimitUpLadder, refreshLimitUpLadder } from '../api/limitUp'
import { saveObserve } from '../api/observe'
import { getSyncJob, startSyncJob } from '../api/sync'
import { useTradeDateStore } from '../stores/tradeDate'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture'
import { buildLimitUpShareSheet, mountShareSheet, LIMIT_UP_SHARE_WIDTH } from '../utils/limitUpShareSheet'
import { snapshotFallbackText, snapshotStamp } from '../utils/snapshotDate'
import FloatingShareButton from '../components/FloatingShareButton.vue'

const router = useRouter()
const tradeDateStore = useTradeDateStore()
const { tradeDate } = storeToRefs(tradeDateStore)
const loading = ref(false)
const refreshing = ref(false)
const sharing = ref(false)
const copying = ref(false)
const downloading = ref(false)
const shareOpen = ref(false)
const sharePreviewUrl = ref('')
/** @type {import('vue').Ref<'desktop'|'mobile'>} */
const shareMode = ref('desktop')
const sharePreviewWidth = ref(LIMIT_UP_SHARE_WIDTH.desktop)
const data = ref(null)
const snapshotNotice = ref('')
const availableDateSet = ref(new Set())
const activeTheme = ref('')
const boardRef = ref(null)

let suppressDateWatch = false
let syncPollCancelled = false
let sharePreviewObjectUrl = ''

const themes = computed(() => data.value?.themes || [])
const tiers = computed(() => data.value?.tiers || [])
const effect = computed(() => data.value?.effect || null)
const titleDate = computed(() => {
  const d = snapshotStamp(data.value)
  if (!d) return ''
  const s = String(d).slice(0, 10)
  const [y, m, day] = s.split('-')
  return `${Number(m)}月${Number(day)}日`
})

const filteredTiers = computed(() => {
  const theme = activeTheme.value
  if (!theme) return tiers.value
  const out = []
  for (const tier of tiers.value) {
    const stocks = (tier.stocks || []).filter((s) => (s.theme || '') === theme)
    if (!stocks.length) continue
    out.push({ ...tier, stocks, count: stocks.length })
  }
  return out
})

const totalShown = computed(() => {
  if (!activeTheme.value && data.value?.totalCount != null) {
    return Number(data.value.totalCount)
  }
  let n = 0
  for (const tier of filteredTiers.value) {
    for (const s of tier.stocks || []) {
      if (!s.failed) n += 1
    }
  }
  return n
})

function syncAvailableDates(dates) {
  const list = (dates || []).map((d) => String(d).slice(0, 10))
  availableDateSet.value = new Set(list)
}

function disableUnavailableDate(date) {
  if (!availableDateSet.value.size) return false
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return !availableDateSet.value.has(`${y}-${m}-${d}`)
}

function fmtRate(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return ''
  // 100.0% → 100%，避免左侧窄栏叠字
  if (Math.abs(n - Math.round(n)) < 0.05) return `${Math.round(n)}%`
  return `${n.toFixed(1)}%`
}

function fmtPctChg(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return ''
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function onThemeClick(theme) {
  if (!theme) return
  activeTheme.value = activeTheme.value === theme ? '' : theme
}

function clearTheme() {
  activeTheme.value = ''
}

function goSector(theme, e) {
  e?.stopPropagation?.()
  if (!theme) {
    router.push('/sector')
    return
  }
  router.push({ path: '/sector', query: { q: theme } })
}

async function addObserve(stock, e) {
  e?.stopPropagation?.()
  if (!stock?.code) return
  try {
    await saveObserve({
      code: stock.code,
      name: stock.name || '',
      status: 'WATCHING',
      reason: `连板天梯 ${stock.lianban || 1}板`,
      tags: 'limitup',
      priority: Math.min(5, Number(stock.lianban) || 3),
    })
    ElMessage.success(`${stock.code} 已进观察池`)
  } catch (err) {
    ElMessage.error(err.message || '加入失败')
  }
}

function fmtSealAmount(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)}亿`
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(0)}万`
  return n.toFixed(0)
}

/** 题材配色：与上方 chip、卡片题材共用同一套哈希色 */
const THEME_PALETTE = [
  { color: '#c43d4a', bg: 'rgba(196, 61, 74, 0.12)', border: 'rgba(196, 61, 74, 0.32)' },
  { color: '#1f8a4c', bg: 'rgba(31, 138, 76, 0.12)', border: 'rgba(31, 138, 76, 0.30)' },
  { color: '#0a66c2', bg: 'rgba(10, 102, 194, 0.12)', border: 'rgba(10, 102, 194, 0.30)' },
  { color: '#b36b00', bg: 'rgba(179, 107, 0, 0.14)', border: 'rgba(179, 107, 0, 0.32)' },
  { color: '#6b4fbb', bg: 'rgba(107, 79, 187, 0.12)', border: 'rgba(107, 79, 187, 0.30)' },
  { color: '#c45c26', bg: 'rgba(196, 92, 38, 0.12)', border: 'rgba(196, 92, 38, 0.30)' },
  { color: '#0d8a8a', bg: 'rgba(13, 138, 138, 0.12)', border: 'rgba(13, 138, 138, 0.30)' },
  { color: '#a83d7a', bg: 'rgba(168, 61, 122, 0.12)', border: 'rgba(168, 61, 122, 0.30)' },
  { color: '#3d6b9a', bg: 'rgba(61, 107, 154, 0.12)', border: 'rgba(61, 107, 154, 0.30)' },
  { color: '#7a8a1f', bg: 'rgba(122, 138, 31, 0.12)', border: 'rgba(122, 138, 31, 0.30)' },
]

function hashTheme(theme) {
  const s = String(theme || '')
  let h = 0
  for (let i = 0; i < s.length; i += 1) {
    h = (h * 31 + s.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

function themeTone(theme) {
  if (!theme) {
    return { color: '#86868b', bg: 'rgba(0,0,0,0.04)', border: 'rgba(0,0,0,0.08)' }
  }
  return THEME_PALETTE[hashTheme(theme) % THEME_PALETTE.length]
}

function themeChipStyle(theme, on) {
  const t = themeTone(theme)
  if (on) {
    return {
      color: t.color,
      background: t.bg,
      borderColor: t.border,
    }
  }
  return {
    color: t.color,
    background: '#fff',
    borderColor: t.border,
  }
}

function themeTagStyle(theme) {
  return { color: themeTone(theme).color }
}

function pctClass(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return ''
  return n > 0 ? 'up' : 'down'
}

async function load() {
  loading.value = true
  const requestedDate = tradeDate.value
  try {
    const res = await fetchLimitUpLadder(requestedDate || undefined)
    data.value = res.data
    const actualDate = snapshotStamp(res.data)
    snapshotNotice.value = snapshotFallbackText(requestedDate, actualDate)
    syncAvailableDates(res.data?.availableDates)
    if (res.data?.tradeDate) {
      const next = String(res.data.tradeDate).slice(0, 10)
      if (tradeDate.value !== next) {
        suppressDateWatch = true
        tradeDateStore.setTradeDate(next)
        Promise.resolve().then(() => {
          suppressDateWatch = false
        })
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function waitSyncJob(jobId, timeoutMs = 120000) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    if (syncPollCancelled) throw new Error('已取消')
    const res = await getSyncJob(jobId)
    const status = res.data?.status
    if (status === 'SUCCESS') return res.data
    if (status === 'FAILED' || status === 'CANCELLED') {
      throw new Error(res.data?.message || '同步任务失败')
    }
    await new Promise((r) => setTimeout(r, 2000))
  }
  throw new Error('同步超时，请到数据同步中心查看')
}

async function onRefresh() {
  refreshing.value = true
  syncPollCancelled = false
  try {
    try {
      const body = { taskType: 'LIMIT_UP' }
      if (tradeDate.value) {
        body.start = String(tradeDate.value).replace(/-/g, '')
      }
      const job = await startSyncJob(body)
      await waitSyncJob(job.data?.id)
      if (syncPollCancelled) return
      await load()
      ElMessage.success('涨停池已通过同步中心刷新')
    } catch (e) {
      if (syncPollCancelled || e?.message === '已取消') return
      const requestedDate = tradeDate.value
      const res = await refreshLimitUpLadder(requestedDate || undefined)
      data.value = res.data?.ladder || data.value
      snapshotNotice.value = snapshotFallbackText(requestedDate, snapshotStamp(data.value))
      syncAvailableDates(data.value?.availableDates)
      if (data.value?.tradeDate) {
        tradeDateStore.setTradeDate(data.value.tradeDate)
      }
      ElMessage.success(res.data?.message || '已刷新')
    }
  } catch (e) {
    if (!syncPollCancelled) {
      ElMessage.error(e.message || '刷新失败')
      await load()
    }
  } finally {
    refreshing.value = false
  }
}

function revokeSharePreview() {
  if (sharePreviewObjectUrl) {
    URL.revokeObjectURL(sharePreviewObjectUrl)
    sharePreviewObjectUrl = ''
  }
  sharePreviewUrl.value = ''
}

async function captureBoard(mode = shareMode.value) {
  await nextTick()
  if (!filteredTiers.value.length) throw new Error('暂无复盘内容')

  const layout = mode === 'mobile' ? 'mobile' : 'desktop'
  const width = LIMIT_UP_SHARE_WIDTH[layout]
  const sheet = buildLimitUpShareSheet({
    titleDate: titleDate.value,
    tradeDate: snapshotStamp(data.value),
    activeTheme: activeTheme.value,
    totalCount: data.value?.totalCount,
    themes: themes.value,
    tiers: filteredTiers.value,
    layout,
  })
  const mounted = mountShareSheet(sheet, width)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const height = Math.max(sheet.scrollHeight, sheet.offsetHeight, 1)
    sheet.style.width = `${width}px`
    sheet.style.height = `${height}px`
    // 高分屏至少 2x 才清晰；超长图才按 canvas 上限略降，且不低于 1.75
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

async function openShare(mode) {
  if (!filteredTiers.value.length) {
    ElMessage.warning('暂无复盘内容可分享')
    return
  }
  if (mode === 'desktop' || mode === 'mobile') {
    shareMode.value = mode
  }
  sharing.value = true
  try {
    const blob = await captureBoard(shareMode.value)
    revokeSharePreview()
    sharePreviewObjectUrl = URL.createObjectURL(blob)
    sharePreviewUrl.value = sharePreviewObjectUrl
    sharePreviewWidth.value = LIMIT_UP_SHARE_WIDTH[shareMode.value]
    shareOpen.value = true
  } catch (e) {
    console.error('生成连板天梯分享图失败', e)
    ElMessage.error(e.message || '截图失败')
  } finally {
    sharing.value = false
  }
}

async function onShareModeChange(mode) {
  if (!shareOpen.value) return
  await openShare(mode)
}

async function onCopyShare() {
  copying.value = true
  try {
    await copyImageBlob(captureBoard(shareMode.value))
    ElMessage.success('已复制到剪贴板，可直接粘贴到微信/文档')
  } catch (e) {
    console.error('复制连板天梯分享图失败', e)
    ElMessage.error(e.message || '复制失败，请改用下载')
  } finally {
    copying.value = false
  }
}

async function onDownloadShare() {
  downloading.value = true
  try {
    const blob = await captureBoard(shareMode.value)
    const suffix = shareMode.value === 'mobile' ? 'mobile' : 'ladder'
    downloadBlob(blob, shareFilename(`apex_limitup_${suffix}`, titleDate.value || 'ladder'))
    ElMessage.success('已下载分享图')
  } catch (e) {
    console.error('下载连板天梯分享图失败', e)
    ElMessage.error(e.message || '下载失败')
  } finally {
    downloading.value = false
  }
}

function closeShare() {
  shareOpen.value = false
  revokeSharePreview()
}

watch(tradeDate, (val, oldVal) => {
  if (suppressDateWatch || val === oldVal) return
  activeTheme.value = ''
  load()
})

onMounted(load)
onBeforeUnmount(() => {
  syncPollCancelled = true
  revokeSharePreview()
})
</script>

<template>
  <div class="page lu-page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <p class="eyebrow">Limit-Up</p>
        <h1>连板天梯</h1>
        <p>{{ snapshotNotice || data?.message || '连板天梯 · 东财涨停池 · 情绪与接力参考' }}</p>
      </div>
      <div class="actions">
        <el-date-picker
          v-model="tradeDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="交易日"
          style="width: 150px"
          :clearable="false"
          :disabled-date="disableUnavailableDate"
        />
        <el-button type="primary" :loading="refreshing" @click="onRefresh">刷新</el-button>
        <el-button plain @click="router.push('/sector')">板块</el-button>
        <el-button plain @click="router.push('/hot')">热点</el-button>
      </div>
    </header>

    <FloatingShareButton
      v-if="!shareOpen"
      :loading="sharing"
      :disabled="!filteredTiers.length"
      @click="openShare"
    />

    <section v-if="effect" class="effect-bar">
      <span>昨涨停 <b>{{ effect.prevCount ?? '-' }}</b> 家</span>
      <span>
        晋级 <b>{{ effect.promoteOk ?? '-' }}</b>
        / 同板 <b>{{ effect.promoteHold != null ? effect.promoteHold : '-' }}</b>
        / 断板 <b>{{ effect.promoteFail ?? '-' }}</b>
      </span>
      <span v-if="effect.promoteRate != null"><TermTip term="promote_rate">晋级率</TermTip> <b>{{ fmtRate(effect.promoteRate) }}</b></span>
      <span v-if="effect.avgNextPct != null">均涨跌 <b>{{ fmtRate(effect.avgNextPct) }}</b></span>
      <span v-if="effect.failNames?.length" class="fail-names">断板 {{ effect.failNames.join('、') }}</span>
    </section>

    <div ref="boardRef" class="ladder-board">
      <section class="hero">
        <div class="hero-top">
          <h2>{{ titleDate ? `${titleDate} A股 连板天梯` : 'A股 连板天梯' }}</h2>
          <div class="hero-actions">
            <span class="hero-count">{{ totalShown }} 家{{ activeTheme ? ` · ${activeTheme}` : '' }}</span>
          </div>
        </div>
        <div v-if="themes.length" class="theme-row">
          <button
            v-for="t in themes"
            :key="t.theme"
            type="button"
            class="theme-chip"
            :class="{ on: activeTheme === t.theme }"
            :style="themeChipStyle(t.theme, activeTheme === t.theme)"
            :title="`筛选「${t.theme}」；右键或「板块」进板块页`"
            @click="onThemeClick(t.theme)"
            @dblclick="goSector(t.theme, $event)"
          >
            <em>{{ t.theme }}</em>
            <b :style="{ color: themeTone(t.theme).color }">{{ t.count }}</b>
          </button>
          <button
            v-if="activeTheme"
            type="button"
            class="theme-clear"
            @click="clearTheme"
          >
            清除筛选
          </button>
          <button
            v-if="activeTheme"
            type="button"
            class="theme-clear link"
            @click="goSector(activeTheme, $event)"
          >
            看板块 →
          </button>
        </div>
        <div class="legend no-capture">
          <span class="legend-item"><i class="badge lb">N</i>连板</span>
          <span class="legend-item"><i class="badge break">炸</i>炸板</span>
          <span class="legend-item"><i class="badge yizi">一</i>一字板</span>
          <span class="legend-item"><i class="legend-fail-mark" aria-hidden="true">×</i>断板</span>
          <span class="legend-item"><i class="badge obs show">观</i>观察池</span>
          <span class="muted">末封 / 封单 / 换手 · 单击题材筛选 · 双击进板块</span>
        </div>
      </section>

      <div v-if="!filteredTiers.length" class="page-empty no-capture">
        <h3>{{ activeTheme ? `「${activeTheme}」暂无涨停` : '暂无连板天梯数据' }}</h3>
        <p v-if="activeTheme">换个题材，或清除筛选后再看全市场天梯</p>
        <p v-else>刷新东财涨停池后看连板天梯与题材分布</p>
        <el-button v-if="activeTheme" @click="clearTheme">清除筛选</el-button>
        <el-button v-else type="primary" :loading="refreshing" @click="onRefresh">立即刷新</el-button>
      </div>

      <section
        v-for="tier in filteredTiers"
        :key="tier.lianban"
        class="tier"
      >
        <aside class="tier-side">
          <div class="tier-title">{{ tier.title }}</div>
          <div v-if="tier.promoteLabel || tier.promoteRate != null" class="tier-promote">
            <span v-if="tier.promoteLabel" class="tier-promote-label">{{ tier.promoteLabel }}</span>
            <span v-if="tier.promoteRate != null" class="tier-promote-rate">{{ fmtRate(tier.promoteRate) }}</span>
          </div>
          <div class="tier-count">{{ tier.count }} 家</div>
        </aside>
        <div class="tier-grid">
          <button
            v-for="s in tier.stocks"
            :key="s.failed ? `fail-${s.code}` : s.code"
            type="button"
            class="card"
            :class="{
              hot: activeTheme && s.theme === activeTheme && !s.failed,
              failed: !!s.failed,
            }"
            :title="s.failed ? '昨日连板今日断板' : undefined"
            @click="router.push(`/stock/${s.code}`)"
          >
            <div class="card-top">
              <span class="seal">{{ s.failed ? '' : (s.lastSealTime || s.firstSealTime || '--:--') }}</span>
              <button
                type="button"
                class="badge obs no-capture"
                title="加入观察池"
                @click="addObserve(s, $event)"
              >观</button>
            </div>
            <div class="card-name-row">
              <StockIdentity :security="s" :show-code="false" compact />
              <span class="badges">
                <i v-if="!s.failed && s.yizi" class="badge yizi" title="一字板">一</i>
                <i v-if="s.lianban > 1" class="badge lb">{{ s.lianban }}</i>
                <i v-if="!s.failed && s.breakCount > 0" class="badge break">炸</i>
              </span>
            </div>
            <div class="card-sub">
              <button
                v-if="s.theme"
                type="button"
                class="card-theme"
                :style="themeTagStyle(s.theme)"
                :title="`筛选题材 ${s.theme}`"
                @click.stop="onThemeClick(s.theme)"
              >{{ s.theme }}</button>
              <div v-else class="card-theme mute">-</div>
              <div v-if="s.pctChg != null" class="card-pct" :class="pctClass(s.pctChg)">{{ fmtPctChg(s.pctChg) }}</div>
            </div>
            <div v-if="!s.failed" class="card-meta">
              <span v-if="s.sealAmount != null">封&nbsp;{{ fmtSealAmount(s.sealAmount) }}</span>
              <span v-if="s.turnoverRate != null">换&nbsp;{{ fmtRate(s.turnoverRate) }}</span>
            </div>
          </button>
        </div>
      </section>

      <footer class="board-foot">
        <span>灵极 · 连板天梯</span>
        <span>{{ snapshotStamp(data) || '日期待同步' }} · 仅供研究</span>
      </footer>
    </div>

    <el-dialog
      v-model="shareOpen"
      :title="shareMode === 'mobile' ? '分享连板天梯 · 手机版' : '分享连板天梯 · 桌面版'"
      width="96vw"
      top="3vh"
      append-to-body
      destroy-on-close
      class="lu-share-dialog"
      @closed="revokeSharePreview"
    >
      <div class="share-mode-row">
        <el-radio-group
          v-model="shareMode"
          size="small"
          :disabled="sharing"
          @change="onShareModeChange"
        >
          <el-radio-button value="desktop">桌面版 1180</el-radio-button>
          <el-radio-button value="mobile">手机版 750</el-radio-button>
        </el-radio-group>
        <span class="share-tip-inline">预览可滚动；推荐下载 PNG 发微信/社群</span>
      </div>
      <div class="share-stage" :class="{ 'is-mobile': shareMode === 'mobile' }">
        <img
          v-if="sharePreviewUrl"
          :src="sharePreviewUrl"
          alt="连板天梯分享预览"
          :style="{ width: `${sharePreviewWidth}px` }"
        />
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
.lu-page {
  --lu-red: #c45656;
  --lu-red-soft: rgba(196, 86, 86, 0.1);
  --lu-green: #3d9a4a;
}

.effect-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-bottom: 10px;
  padding: 8px 12px;
  border: 1px solid rgba(61, 154, 74, 0.22);
  border-radius: 10px;
  background: rgba(61, 154, 74, 0.07);
  font-size: 12px;
  color: #3a3a3c;
}

.effect-bar b {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.effect-bar .fail-names {
  color: var(--lu-red);
}

.ladder-board {
  padding: 14px 18px 16px;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 14px;
  box-sizing: border-box;
}

.hero {
  margin-bottom: 12px;
  padding: 12px 14px 10px;
  border: 1px solid #eee;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff8f6 0%, #fff 70%);
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.hero h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 750;
  color: var(--lu-red);
  letter-spacing: 0.01em;
}

.hero-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.hero-count {
  font-size: 12px;
  color: #86868b;
  white-space: nowrap;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  align-items: center;
}

.theme-chip {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 5px;
  padding: 3px 9px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 980px;
  background: #fff;
  font: inherit;
  font-size: 12px;
  color: #3a3a3c;
  cursor: pointer;
  white-space: nowrap;
  word-break: keep-all;
  transition: background 0.15s, border-color 0.15s, color 0.15s, box-shadow 0.15s;
}

.theme-chip em {
  font-style: normal;
}

.theme-chip b {
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.theme-chip:hover {
  filter: brightness(0.98);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.theme-chip.on {
  font-weight: 650;
  box-shadow: inset 0 0 0 1px currentColor;
}

.theme-clear {
  border: 0;
  background: transparent;
  color: #0071e3;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}

.theme-clear.link {
  color: #86868b;
}

.theme-clear:hover {
  text-decoration: underline;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  color: #86868b;
  font-size: 11px;
  align-items: center;
  line-height: 1;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  line-height: 1;
}

.legend .badge {
  margin: 0;
  flex-shrink: 0;
  position: relative;
  top: 0;
}

.legend .badge.obs.show {
  opacity: 1;
  pointer-events: none;
}

.legend-fail-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 13px;
  height: 13px;
  border-radius: 3px;
  background: #fcfcfd;
  color: rgba(60, 60, 67, 0.35);
  font-size: 13px;
  font-style: normal;
  font-weight: 300;
  line-height: 1;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.legend .muted {
  color: #aeaeb2;
  line-height: 1.2;
  align-self: center;
}

.tier {
  display: grid;
  grid-template-columns: max-content 1fr;
  column-gap: 12px;
  row-gap: 0;
  align-items: start;
  margin-bottom: 10px;
  padding: 4px 2px 10px 2px;
  border-bottom: 1px solid #f0f0f2;
}

.tier:last-of-type {
  border-bottom: 0;
  padding-bottom: 4px;
}

.tier-side {
  /* 与首行单张卡片同高，内部垂直居中，和第一行卡片对齐 */
  --lu-card-row-h: 72px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  box-sizing: border-box;
  width: max-content;
  min-height: var(--lu-card-row-h);
  height: var(--lu-card-row-h);
  padding: 0;
  text-align: left;
}

.tier-title {
  font-size: 16px;
  font-weight: 800;
  color: var(--lu-red);
  line-height: 1.15;
  letter-spacing: 0;
  white-space: nowrap;
}

.tier-promote {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
  margin-top: 1px;
  color: var(--lu-green);
  line-height: 1.15;
}

.tier-promote-label,
.tier-promote-rate {
  display: block;
  font-size: 10px;
  font-weight: 650;
  white-space: nowrap;
  letter-spacing: 0;
}

.tier-promote-rate {
  font-variant-numeric: tabular-nums;
  margin-top: 0;
}

.tier-count {
  margin-top: 1px;
  font-size: 10px;
  color: #aeaeb2;
  white-space: nowrap;
  line-height: 1.2;
}

.tier-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
}

.card {
  position: relative;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  width: 108px;
  height: 72px;
  padding: 5px 6px 4px;
  border: 1px solid #ebebef;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}

.card:hover {
  border-color: rgba(196, 86, 86, 0.45);
  box-shadow: 0 2px 8px rgba(196, 86, 86, 0.1);
  transform: translateY(-1px);
}

.card.hot {
  border-color: rgba(196, 86, 86, 0.35);
  background: #fffaf8;
}

.card.failed {
  border-color: #f0f0f2;
  background: #fcfcfd;
  box-shadow: none;
}

.card.failed:hover {
  border-color: #e8e8ec;
  box-shadow: none;
  transform: none;
}

.card.failed::after {
  content: '×';
  position: absolute;
  inset: -2px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
  font-weight: 200;
  font-style: normal;
  line-height: 1;
  color: rgba(60, 60, 67, 0.16);
  pointer-events: none;
  z-index: 2;
  font-family: "Helvetica Neue", Arial, sans-serif;
}

.card.failed .card-top,
.card.failed .card-name-row,
.card.failed .card-sub,
.card.failed .card-meta {
  position: relative;
  z-index: 1;
  opacity: 0.52;
}

.card.failed .badge.lb {
  background: #d1d1d6;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 2px;
  min-height: 13px;
}

.seal {
  font-size: 9px;
  color: #aeaeb2;
  font-variant-numeric: tabular-nums;
}

.card-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 3px;
  min-width: 0;
  height: 16px;
}

.badges {
  display: inline-flex;
  flex: 0 0 auto;
  flex-wrap: nowrap;
  gap: 2px;
  align-items: center;
  height: 16px;
  line-height: 1;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  flex-shrink: 0;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  border-radius: 3px;
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
  color: #fff;
  line-height: 1;
  vertical-align: middle;
}

.badge.lb {
  background: #409eff;
}

.badge.break {
  background: #e6a23c;
}

.badge.yizi {
  background: #c45656;
}

.badge.obs {
  border: 0;
  cursor: pointer;
  background: #0071e3;
  font-family: inherit;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease, filter 0.12s ease;
}

.card:hover .badge.obs,
.card:focus-within .badge.obs {
  opacity: 1;
  pointer-events: auto;
}

.badge.obs:hover {
  filter: brightness(1.08);
}

.card-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  min-width: 0;
}

.card-pct {
  flex: 0 0 auto;
  font-size: 10px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  color: #86868b;
}

.card-pct.up {
  color: var(--lu-red);
}

.card-pct.down {
  color: var(--lu-green);
}

.card-theme {
  flex: 1;
  min-width: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  font: inherit;
  font-size: 9px;
  font-weight: 650;
  color: #86868b;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  line-height: 1.35;
  box-sizing: border-box;
}

.card-theme:hover {
  text-decoration: underline;
}

.card-theme.mute {
  cursor: default;
  text-decoration: none;
}

.card-meta {
  display: flex;
  gap: 4px;
  font-size: 9px;
  color: #aeaeb2;
  margin-top: 0;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
}

.card-meta span {
  flex: 0 0 auto;
  white-space: nowrap;
}

.board-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  padding: 10px 4px 2px;
  border-top: 1px dashed #ececef;
  font-size: 11px;
  color: #aeaeb2;
  white-space: nowrap;
}

.board-foot span {
  flex: 0 0 auto;
  white-space: nowrap;
}

.share-action-btn {
  --el-button-bg-color: #fff !important;
  --el-button-border-color: rgba(196, 86, 86, 0.45) !important;
  --el-button-text-color: #c45656 !important;
  --el-button-hover-bg-color: rgba(196, 86, 86, 0.1) !important;
  --el-button-hover-border-color: rgba(196, 86, 86, 0.55) !important;
  --el-button-hover-text-color: #c45656 !important;
  font-weight: 650;
}

.share-mode-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 10px;
}

.share-tip-inline {
  font-size: 12px;
  color: #86868b;
}

.share-stage {
  display: block;
  max-height: min(72vh, 820px);
  overflow: auto;
  padding: 16px;
  background: #ececec;
  border-radius: 12px;
  text-align: center;
}

.share-stage.is-mobile {
  text-align: center;
}

.share-stage img {
  max-width: none;
  height: auto;
  display: inline-block;
  vertical-align: top;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  background: #fff;
}

@media (max-width: 720px) {
  .ladder-board {
    padding: 12px 12px 14px;
  }

  .tier {
    grid-template-columns: max-content minmax(0, 1fr);
    column-gap: 8px;
    padding-left: 0;
  }

  .tier-title {
    font-size: 15px;
  }

  .tier-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
    min-width: 0;
  }

  .card {
    width: 100%;
    min-width: 0;
  }

  .card-sub {
    gap: 2px;
  }

  .card-pct {
    font-size: 9px;
  }

  .actions {
    flex-wrap: wrap;
  }
}
</style>

<!-- dialog append-to-body：用全局样式保证预览区不被裁切 -->
<style>
.lu-share-dialog.el-dialog {
  max-width: 1400px;
  margin-bottom: 3vh;
}

.lu-share-dialog .el-dialog__body {
  padding-top: 8px;
  max-height: none;
  overflow: visible;
}

.lu-share-dialog .share-mode-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 10px;
}

.lu-share-dialog .share-tip-inline {
  font-size: 12px;
  color: #86868b;
}

.lu-share-dialog .share-stage {
  display: block;
  width: 100%;
  max-height: min(70vh, 780px);
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  padding: 16px;
  background: #ececec;
  border-radius: 12px;
  text-align: center;
  box-sizing: border-box;
}

.lu-share-dialog .share-stage img {
  max-width: none !important;
  height: auto !important;
  image-rendering: auto;
  display: inline-block;
  vertical-align: top;
  box-shadow: 0 10px 32px rgba(0, 0, 0, 0.12);
  border-radius: 4px;
  background: #fff;
}
</style>
