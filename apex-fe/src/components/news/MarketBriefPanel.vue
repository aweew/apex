<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchMarketBriefing } from '../../api/market'
import { normalizeHotThemes } from '../../utils/hotTheme.js'

const router = useRouter()
const loading = ref(false)
const briefing = ref(null)

const indexes = computed(() => (briefing.value?.indexes || []).slice(0, 6))
const effect = computed(() => briefing.value?.effect || null)
const hotThemes = computed(() => normalizeHotThemes(briefing.value))

function fmtPct(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  const s = n.toFixed(2)
  return (n > 0 ? '+' : '') + s + '%'
}

function pctClass(v) {
  const n = Number(v)
  if (!Number.isFinite(n) || n === 0) return ''
  return n > 0 ? 'up' : 'down'
}

async function load(force = false) {
  loading.value = true
  try {
    const res = await fetchMarketBriefing(force)
    briefing.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '行情摘要加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => load(false))
defineExpose({ load })
</script>

<template>
  <div class="mkt-panel" v-loading="loading">
    <div class="panel-bar">
      <div>
        <strong>行情摘要</strong>
        <span class="muted">{{ briefing?.message || '指数 · 立场 · 赚钱效应' }}</span>
      </div>
      <div class="ops">
        <el-button size="small" :loading="loading" @click="load(true)">刷新</el-button>
        <el-button size="small" type="primary" plain @click="router.push('/market')">完整行情</el-button>
      </div>
    </div>

    <div class="stance-row" v-if="briefing">
      <span v-if="briefing.stance" class="chip">立场 {{ briefing.stance }}</span>
      <span v-if="briefing.volumeLabel" class="chip">量能 {{ briefing.volumeLabel }}</span>
      <span v-if="effect?.hint" class="chip">{{ effect.hint }}</span>
      <span v-if="briefing.positionAdvice" class="advice">{{ briefing.positionAdvice }}</span>
    </div>

    <div class="idx-grid" v-if="indexes.length">
      <div v-for="item in indexes" :key="item.code || item.name" class="idx" :class="pctClass(item.pctChg)">
        <label>{{ item.name }}</label>
        <b>{{ item.closePrice ?? item.close ?? '-' }}</b>
        <em>{{ fmtPct(item.pctChg) }}</em>
      </div>
    </div>

    <div v-if="hotThemes.length" class="themes">
      <label>主线</label>
      <span
        v-for="t in hotThemes.slice(0, 6)"
        :key="t.key"
      >
        <span class="theme-name">{{ t.name }}</span>
        <span v-if="t.abs" class="theme-pct" :class="t.pctDir">
          <span v-if="t.sign" class="theme-sign">{{ t.sign }}</span>{{ t.abs }}%
        </span>
      </span>
    </div>

    <p v-if="briefing?.stanceReason" class="reason">{{ briefing.stanceReason }}</p>
  </div>
</template>

<style scoped>
.panel-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}
.panel-bar strong {
  font-size: 15px;
  margin-right: 8px;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.ops {
  display: flex;
  gap: 6px;
}
.stance-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.chip {
  font-size: 12px;
  font-weight: 650;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.08);
  color: #0a66c2;
}
.advice {
  font-size: 12px;
  color: var(--ink-soft);
}
.idx-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.idx {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 10px;
  padding: 10px 12px;
}
.idx label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}
.idx b {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
  margin-right: 8px;
}
.idx em {
  font-style: normal;
  font-size: 13px;
  font-weight: 650;
}
.idx.up em,
.idx.up b {
  color: #c43d4a;
}
.idx.down em,
.idx.down b {
  color: #1f8a4c;
}
.themes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}
.themes label {
  font-size: 11px;
  color: var(--muted);
}
.themes span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: #3a3a3c;
  background: rgba(0, 0, 0, 0.04);
  padding: 2px 8px;
  border-radius: 6px;
}

.themes .theme-name {
  color: #3a3a3c;
}

.themes .theme-pct {
  display: inline-flex;
  align-items: center;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum' 1;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
}

.themes .theme-sign {
  display: inline-block;
  transform: translateY(-0.14em);
  line-height: 1;
}

.themes .theme-pct.up {
  color: #c45656;
}

.themes .theme-pct.down {
  color: #1f8a4c;
}
.reason {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: var(--muted);
}
@media (max-width: 800px) {
  .idx-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
