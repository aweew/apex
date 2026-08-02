<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { fetchLimitUpLadder, refreshLimitUpLadder } from '../api/limitUp'
import { getSyncJob, startSyncJob } from '../api/sync'
import { useTradeDateStore } from '../stores/tradeDate'

const router = useRouter()
const tradeDateStore = useTradeDateStore()
const { tradeDate } = storeToRefs(tradeDateStore)
const loading = ref(false)
const refreshing = ref(false)
const data = ref(null)
const availableDateSet = ref(new Set())

let suppressDateWatch = false
let syncPollCancelled = false

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

watch(tradeDate, (val, oldVal) => {
  if (suppressDateWatch || val === oldVal) return
  load()
})

onMounted(load)
onBeforeUnmount(() => {
  syncPollCancelled = true
})
</script>

<template>
  <div class="page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <h1>涨停复盘</h1>
        <p class="sub">{{ data?.message || '连板天梯 · 东财涨停池' }}</p>
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
      </div>
    </header>

    <section v-if="effect" class="effect-bar">
      <span>昨涨停 {{ effect.prevCount ?? '-' }} 家</span>
      <span>
        晋级 {{ effect.promoteOk ?? '-' }}
        / 同板 {{ effect.promoteHold != null ? effect.promoteHold : '-' }}
        / 断板 {{ effect.promoteFail ?? '-' }}
      </span>
      <span v-if="effect.promoteRate != null">晋级率 {{ fmtRate(effect.promoteRate) }}</span>
      <span v-if="effect.avgNextPct != null">均涨跌 {{ fmtRate(effect.avgNextPct) }}</span>
      <span v-if="effect.failNames?.length" class="fail-names">断板 {{ effect.failNames.join('、') }}</span>
      <span v-if="effect.message">{{ effect.message }}</span>
    </section>

    <section class="hero">
      <h2>{{ titleDate }} A股 涨停复盘</h2>
      <div v-if="themes.length" class="theme-row">
        <span
          v-for="t in themes"
          :key="t.theme"
          class="theme-chip"
        >
          {{ t.theme }}（{{ t.count }}）
        </span>
      </div>
      <div class="legend">
        <span><i class="badge lb">N</i>连板数</span>
        <span><i class="badge break">炸</i>曾炸板</span>
        <span>卡片：末封时间 / 封单 / 换手</span>
      </div>
    </section>

    <div v-if="!tiers.length" class="empty">暂无数据，请点击刷新拉取涨停池</div>

    <section
      v-for="tier in tiers"
      :key="tier.lianban"
      class="tier"
    >
      <aside class="tier-side">
        <div class="tier-title">{{ tier.title }}</div>
        <div v-if="tier.promoteLabel" class="tier-promote">{{ tier.promoteLabel }}</div>
        <div v-if="tier.promoteRate != null" class="tier-rate">{{ fmtRate(tier.promoteRate) }}</div>
        <div v-else-if="tier.lianban <= 1" class="tier-count">（{{ tier.count }}）</div>
      </aside>
      <div class="tier-grid">
        <button
          v-for="s in tier.stocks"
          :key="s.code"
          type="button"
          class="card"
          @click="router.push(`/stock/${s.code}`)"
        >
          <div class="card-top">
            <span class="seal">{{ s.lastSealTime || s.firstSealTime || '--:--' }}</span>
            <span class="badges">
              <i v-if="s.lianban > 1" class="badge lb">{{ s.lianban }}</i>
              <i v-if="s.breakCount > 0" class="badge break">炸</i>
            </span>
          </div>
          <div class="card-name">{{ s.name || s.code }}</div>
          <div class="card-theme">{{ s.theme || '-' }}</div>
          <div class="card-meta">
            <span v-if="s.sealAmount != null">封 {{ fmtSealAmount(s.sealAmount) }}</span>
            <span v-if="s.turnoverRate != null">换 {{ fmtRate(s.turnoverRate) }}</span>
          </div>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.sub {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.effect-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: rgba(103, 194, 58, 0.08);
  font-size: 13px;
}

.effect-bar .fail-names {
  color: #c45656;
}

.hero {
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  background: var(--glass);
}

.hero h2 {
  margin: 0 0 10px;
  font-size: 20px;
  font-weight: 700;
  color: #c45656;
}

.theme-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-bottom: 10px;
  font-size: 13px;
}

.theme-chip {
  color: #333;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
  align-items: center;
}

.legend .badge {
  margin-right: 4px;
  vertical-align: middle;
}

.empty {
  padding: 40px;
  text-align: center;
  color: var(--muted);
}

.tier {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--glass-border);
}

.tier-side {
  padding-top: 6px;
}

.tier-title {
  font-size: 22px;
  font-weight: 800;
  color: #c45656;
  line-height: 1.2;
}

.tier-promote {
  margin-top: 4px;
  font-size: 12px;
  color: #67c23a;
}

.tier-rate {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 700;
  color: #67c23a;
}

.tier-count {
  margin-top: 4px;
  font-size: 13px;
  color: var(--muted);
}

.tier-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  gap: 8px;
}

.card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  padding: 8px 10px;
  border: 1px solid var(--glass-border);
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  min-height: 72px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.card:hover {
  border-color: #c45656;
  box-shadow: 0 2px 8px rgba(196, 86, 86, 0.12);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 4px;
}

.seal {
  font-size: 11px;
  color: var(--muted);
}

.badges {
  display: inline-flex;
  gap: 3px;
}

.badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  border-radius: 3px;
  font-size: 11px;
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

.card-name {
  font-size: 14px;
  font-weight: 700;
  color: #222;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-theme {
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  display: flex;
  gap: 6px;
  font-size: 10px;
  color: var(--muted);
  margin-top: 2px;
}

@media (max-width: 720px) {
  .tier {
    grid-template-columns: 64px 1fr;
  }

  .tier-title {
    font-size: 18px;
  }

  .tier-grid {
    grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  }
}
</style>
