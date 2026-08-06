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
import { buildLimitUpShareSheet, mountShareSheet } from '../utils/limitUpShareSheet'

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
const data = ref(null)
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
  const d = data.value?.tradeDate || tradeDate.value
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
  let n = 0
  for (const tier of filteredTiers.value) {
    n += tier.stocks?.length || 0
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
  return `${Number(v).toFixed(1)}%`
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
      reason: `涨停复盘 ${stock.lianban || 1}板`,
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

async function load() {
  loading.value = true
  try {
    const res = await fetchLimitUpLadder(tradeDate.value || undefined)
    data.value = res.data
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
      const res = await refreshLimitUpLadder(tradeDate.value || undefined)
      data.value = res.data?.ladder || data.value
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

async function captureBoard() {
  await nextTick()
  if (!filteredTiers.value.length) throw new Error('暂无复盘内容')

  const sheet = buildLimitUpShareSheet({
    titleDate: titleDate.value,
    tradeDate: String(data.value?.tradeDate || tradeDate.value || '').slice(0, 10),
    activeTheme: activeTheme.value,
    themes: themes.value,
    tiers: filteredTiers.value,
  })
  const mounted = mountShareSheet(sheet)
  try {
    await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
    const width = 1180
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

async function openShare() {
  if (!filteredTiers.value.length) {
    ElMessage.warning('暂无复盘内容可分享')
    return
  }
  sharing.value = true
  try {
    const blob = await captureBoard()
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
    const blob = await captureBoard()
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
    const blob = await captureBoard()
    downloadBlob(blob, shareFilename('apex_limitup', titleDate.value || 'ladder'))
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
        <p class="eyebrow">灵枢 · Limit-Up</p>
        <h1>涨停复盘</h1>
        <p>{{ data?.message || '连板天梯 · 东财涨停池 · 情绪与接力参考' }}</p>
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
        <el-button
          class="share-action-btn"
          :loading="sharing"
          :disabled="!filteredTiers.length"
          @click="openShare"
        >
          分享截图
        </el-button>
        <el-button plain @click="router.push('/sector')">板块</el-button>
        <el-button plain @click="router.push('/hot')">热点</el-button>
      </div>
    </header>

    <section v-if="effect" class="effect-bar">
      <span>昨涨停 <b>{{ effect.prevCount ?? '-' }}</b> 家</span>
      <span>
        晋级 <b>{{ effect.promoteOk ?? '-' }}</b>
        / 同板 <b>{{ effect.promoteHold != null ? effect.promoteHold : '-' }}</b>
        / 断板 <b>{{ effect.promoteFail ?? '-' }}</b>
      </span>
      <span v-if="effect.promoteRate != null">晋级率 <b>{{ fmtRate(effect.promoteRate) }}</b></span>
      <span v-if="effect.avgNextPct != null">均涨跌 <b>{{ fmtRate(effect.avgNextPct) }}</b></span>
      <span v-if="effect.failNames?.length" class="fail-names">断板 {{ effect.failNames.join('、') }}</span>
    </section>

    <div ref="boardRef" class="ladder-board">
      <section class="hero">
        <div class="hero-top">
          <h2>{{ titleDate }} A股 涨停复盘</h2>
          <div class="hero-actions">
            <span class="hero-count">{{ totalShown }} 家{{ activeTheme ? ` · ${activeTheme}` : '' }}</span>
            <button
              type="button"
              class="share-btn no-capture"
              :disabled="sharing || !filteredTiers.length"
              @click="openShare"
            >
              {{ sharing ? '生成中…' : '分享截图' }}
            </button>
          </div>
        </div>
        <div v-if="themes.length" class="theme-row">
          <button
            v-for="t in themes"
            :key="t.theme"
            type="button"
            class="theme-chip"
            :class="{ on: activeTheme === t.theme }"
            :title="`筛选「${t.theme}」；右键或「板块」进板块页`"
            @click="onThemeClick(t.theme)"
            @dblclick="goSector(t.theme, $event)"
          >
            <em>{{ t.theme }}</em>
            <b>{{ t.count }}</b>
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
          <span><i class="badge lb">N</i>连板</span>
          <span><i class="badge break">炸</i>炸板</span>
          <span><i class="badge obs">观</i>观察池</span>
          <span class="muted">末封 / 封单 / 换手 · 单击题材筛选 · 双击进板块</span>
        </div>
      </section>

      <div v-if="!filteredTiers.length" class="page-empty no-capture">
        <h3>{{ activeTheme ? `「${activeTheme}」暂无涨停` : '暂无涨停复盘' }}</h3>
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
            <span v-if="tier.promoteLabel">{{ tier.promoteLabel }}</span>
            <span v-if="tier.promoteRate != null">{{ fmtRate(tier.promoteRate) }}</span>
          </div>
          <div class="tier-count">{{ tier.count }} 家</div>
        </aside>
        <div class="tier-grid">
          <button
            v-for="s in tier.stocks"
            :key="s.code"
            type="button"
            class="card"
            :class="{ hot: activeTheme && s.theme === activeTheme }"
            @click="router.push(`/stock/${s.code}`)"
          >
            <div class="card-top">
              <span class="seal">{{ s.lastSealTime || s.firstSealTime || '--:--' }}</span>
              <span class="badges">
                <i v-if="s.lianban > 1" class="badge lb">{{ s.lianban }}</i>
                <i v-if="s.breakCount > 0" class="badge break">炸</i>
                <button
                  type="button"
                  class="badge obs no-capture"
                  title="加入观察池"
                  @click="addObserve(s, $event)"
                >观</button>
              </span>
            </div>
            <div class="card-name">{{ s.name || s.code }}</div>
            <div class="card-sub">
              <button
                v-if="s.theme"
                type="button"
                class="card-theme"
                :title="`筛选题材 ${s.theme}`"
                @click.stop="onThemeClick(s.theme)"
              >{{ s.theme }}</button>
              <div v-else class="card-theme mute">-</div>
              <div v-if="s.pctChg != null" class="card-pct up">{{ fmtPctChg(s.pctChg) }}</div>
            </div>
            <div class="card-meta">
              <span v-if="s.sealAmount != null">封 {{ fmtSealAmount(s.sealAmount) }}</span>
              <span v-if="s.turnoverRate != null">换 {{ fmtRate(s.turnoverRate) }}</span>
            </div>
          </button>
        </div>
      </section>

      <footer class="board-foot">
        <span>灵枢 · 涨停复盘</span>
        <span>{{ data?.tradeDate || tradeDate || '' }} · 仅供研究</span>
      </footer>
    </div>

    <el-dialog
      v-model="shareOpen"
      title="分享涨停复盘截图"
      width="96vw"
      top="3vh"
      append-to-body
      destroy-on-close
      class="lu-share-dialog"
      @closed="revokeSharePreview"
    >
      <p class="share-tip">预览可滚动查看全图；推荐下载 PNG 后发微信/社群。</p>
      <div class="share-stage">
        <img v-if="sharePreviewUrl" :src="sharePreviewUrl" alt="涨停复盘分享预览" />
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

.share-btn {
  border: 1px solid rgba(196, 86, 86, 0.35);
  background: #fff;
  color: var(--lu-red);
  border-radius: 980px;
  padding: 5px 12px;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
  white-space: nowrap;
  line-height: 1.2;
}

.share-btn:hover:not(:disabled) {
  background: var(--lu-red-soft);
}

.share-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
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
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.theme-chip em {
  font-style: normal;
}

.theme-chip b {
  font-size: 11px;
  font-weight: 700;
  color: var(--lu-red);
  font-variant-numeric: tabular-nums;
}

.theme-chip:hover {
  border-color: rgba(196, 86, 86, 0.35);
  background: var(--lu-red-soft);
}

.theme-chip.on {
  border-color: rgba(196, 86, 86, 0.45);
  background: var(--lu-red-soft);
  color: var(--lu-red);
  font-weight: 650;
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
}

.legend .badge {
  margin-right: 3px;
  vertical-align: middle;
}

.legend .muted {
  color: #aeaeb2;
}

.tier {
  display: grid;
  grid-template-columns: 58px 1fr;
  gap: 8px;
  margin-bottom: 10px;
  padding: 4px 2px 10px 4px;
  border-bottom: 1px solid #f0f0f2;
}

.tier:last-of-type {
  border-bottom: 0;
  padding-bottom: 4px;
}

.tier-side {
  padding: 2px 0 0;
  text-align: left;
}

.tier-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--lu-red);
  line-height: 1.15;
  letter-spacing: 0.02em;
}

.tier-promote {
  display: flex;
  flex-direction: column;
  gap: 1px;
  margin-top: 2px;
  font-size: 10px;
  font-weight: 650;
  color: var(--lu-green);
  line-height: 1.25;
}

.tier-count {
  margin-top: 2px;
  font-size: 11px;
  color: #aeaeb2;
}

.tier-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-content: flex-start;
}

.card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  width: 108px;
  padding: 5px 6px 4px;
  border: 1px solid #ebebef;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  min-height: 0;
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

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 2px;
}

.seal {
  font-size: 9px;
  color: #aeaeb2;
  font-variant-numeric: tabular-nums;
}

.badges {
  display: inline-flex;
  gap: 2px;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 13px;
  height: 13px;
  padding: 0 2px;
  border-radius: 3px;
  font-size: 9px;
  font-style: normal;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}

.badge.lb {
  background: #409eff;
}

.badge.break {
  background: #e6a23c;
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

.card-name {
  font-size: 12px;
  font-weight: 700;
  color: #1d1d1f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.25;
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
}

.card-theme {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  padding: 0;
  margin: 0;
  font: inherit;
  font-size: 9px;
  color: #86868b;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.card-theme:hover {
  color: var(--lu-red);
  text-decoration: underline;
}

.card-theme.mute {
  cursor: default;
  text-decoration: none;
}

.card-meta {
  display: flex;
  gap: 5px;
  font-size: 9px;
  color: #aeaeb2;
  margin-top: 0;
  font-variant-numeric: tabular-nums;
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

.share-tip {
  margin: 0 0 10px;
  font-size: 13px;
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

.share-stage img {
  width: 1180px;
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
    grid-template-columns: 52px 1fr;
    gap: 6px;
    padding-left: 2px;
  }

  .tier-title {
    font-size: 16px;
  }

  .card {
    width: 96px;
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

.lu-share-dialog .share-tip {
  margin: 0 0 10px;
  font-size: 13px;
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
  /* 按逻辑宽度展示，保留 2x 像素密度，避免被浏览器二次拉伸发虚 */
  width: 1180px !important;
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
