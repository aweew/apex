<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { signalCenterStock, signalCenterTimeline } from '../api/signal'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const snapshot = ref(null)
const timeline = ref([])
const symbol = computed(() => String(route.params.code || '').replace(/\D/g, '').slice(0, 6))

function evidence(item) {
  try {
    return JSON.parse(item?.evidenceJson || '{}')
  } catch {
    return {}
  }
}

async function load() {
  loading.value = true
  try {
    const [snapshotResponse, timelineResponse] = await Promise.all([
      signalCenterStock(symbol.value, 'DAY'),
      signalCenterTimeline(symbol.value, 'DAY', 100),
    ])
    snapshot.value = snapshotResponse.data
    timeline.value = timelineResponse.data || []
  } catch (error) {
    ElMessage.error(error.message || '市场行为详情加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page signal-detail-page" v-loading="loading">
    <header class="header signal-detail-header">
      <div class="signal-detail-title">
        <el-button :icon="ArrowLeft" circle aria-label="返回信号中心" title="返回信号中心" @click="router.push('/signals')" />
        <div>
          <p class="eyebrow">Market behavior</p>
          <h1>{{ snapshot?.name || symbol }} <small>{{ symbol }}</small></h1>
          <p>完整日线 · 数据截至 {{ snapshot?.dataAsOf || '尚无结果' }}</p>
        </div>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" aria-label="刷新市场行为详情" @click="load">刷新</el-button>
        <el-button type="primary" @click="router.push(`/stock/${symbol}`)">查看股票研究</el-button>
      </div>
    </header>

    <section class="state-band">
      <div><span>市场阶段</span><strong>{{ snapshot?.marketState || 'TRANSITION' }}</strong></div>
      <div><span>新鲜度</span><strong>{{ snapshot?.freshness || 'EMPTY' }}</strong></div>
      <p>{{ snapshot?.usageHint || '先核对数据日期和证据；行为信号不等同于买卖建议' }}</p>
    </section>

    <section class="meaning-band" aria-label="评分含义">
      <div><strong>强度</strong><span>这一次行为有多明显</span></div>
      <div><strong>置信度</strong><span>数据完整度与规则稳定性</span></div>
      <div><strong>历史概率</strong><span>相似历史样本的条件统计</span></div>
      <div><strong>风险分</strong><span>结构、过热和流动性风险</span></div>
    </section>

    <section class="detail-section">
      <header><h2>主要行为</h2><span>描述市场发生了什么，不直接生成买卖指令</span></header>
      <div v-if="snapshot?.activeSignals?.length" class="event-list">
        <article v-for="item in snapshot.activeSignals" :key="item.eventId" class="event-item">
          <div class="event-summary">
            <strong>{{ item.signalCode }} · {{ item.signalName }}</strong>
            <span>{{ item.lifecycleState }}</span>
          </div>
          <div class="score-row">
            <span>强度 <b>{{ item.strength ?? '-' }}</b></span>
            <span>置信度 <b>{{ item.confidence ?? '-' }}</b></span>
            <span>历史概率 <b>{{ item.probability ?? '-' }}</b></span>
            <span>风险分 <b>{{ item.riskScore ?? '-' }}</b></span>
          </div>
          <div class="evidence-grid">
            <h3>价格与量能证据</h3>
            <span>阻力 <b>{{ evidence(item).resistancePrice ?? '-' }}</b></span>
            <span>支撑 <b>{{ evidence(item).supportPrice ?? '-' }}</b></span>
            <span>ATR14 <b>{{ evidence(item).atr14 ?? '-' }}</b></span>
            <span>20日量比 <b>{{ evidence(item).volumeRatio ?? '-' }}</b></span>
            <span>收盘位置 <b>{{ evidence(item).closePosition ?? '-' }}</b></span>
            <p>{{ evidence(item).reason }}</p>
          </div>
        </article>
      </div>
      <p v-else class="empty-copy">当前没有方向性行为。可能尚未计算，或最新完整日线没有触发规则。</p>
    </section>

    <section class="detail-section risk-section">
      <header><h2>风险行为</h2><span>风险独立阅读，不与强度合成总分</span></header>
      <div v-if="snapshot?.riskSignals?.length" class="risk-list">
        <div v-for="item in snapshot.riskSignals" :key="item.eventId">
          <strong>{{ item.signalCode }} · {{ item.signalName }}</strong>
          <span>风险分 {{ item.riskScore }} · 置信度 {{ item.confidence }}</span>
        </div>
      </div>
      <p v-else class="empty-copy">当前没有已发布的风险行为。</p>
    </section>

    <section class="detail-section timeline-section">
      <header><h2>生命周期时间轴</h2><span>每次迁移都保留当时可见数据和原因</span></header>
      <el-timeline v-if="timeline.length">
        <el-timeline-item v-for="item in timeline" :key="`${item.eventId}-${item.eventTime}-${item.toState}`" :timestamp="item.eventTime">
          <strong>{{ item.signalCode }} · {{ item.signalName }}</strong>
          <p>{{ item.fromState || '初始' }} → {{ item.toState }} · {{ item.reasonCode }}</p>
        </el-timeline-item>
      </el-timeline>
      <p v-else class="empty-copy">尚无生命周期记录。</p>
    </section>
  </div>
</template>

<style scoped>
.signal-detail-page {
  display: grid;
  gap: 12px;
}

.signal-detail-header,
.signal-detail-title,
.state-band,
.meaning-band,
.event-summary,
.score-row,
.detail-section > header,
.risk-list > div {
  display: flex;
  align-items: center;
}

.signal-detail-title {
  gap: 12px;
}

.signal-detail-title h1 {
  margin: 0;
}

.signal-detail-title h1 small,
.signal-detail-title p,
.detail-section > header span,
.empty-copy {
  color: var(--muted);
  font-size: 12px;
}

.signal-detail-title p {
  margin: 4px 0 0;
}

.state-band,
.meaning-band,
.detail-section {
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  background: var(--glass-strong);
  box-shadow: var(--shadow-soft);
}

.state-band {
  gap: 28px;
  padding: 14px 16px;
}

.state-band > div {
  display: grid;
  gap: 2px;
}

.state-band span,
.meaning-band span {
  color: var(--muted);
  font-size: 11px;
}

.state-band p {
  margin: 0 0 0 auto;
  color: var(--muted);
  font-size: 12px;
}

.meaning-band {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.meaning-band > div {
  display: grid;
  gap: 3px;
  padding: 12px 16px;
  border-right: 1px solid var(--line);
}

.meaning-band > div:last-child {
  border-right: 0;
}

.detail-section {
  padding: 14px 16px;
}

.detail-section > header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.detail-section h2,
.evidence-grid h3 {
  margin: 0;
  color: var(--ink);
  font-size: 15px;
}

.event-item {
  padding: 13px 0;
  border-top: 1px solid var(--line);
}

.event-summary,
.risk-list > div {
  justify-content: space-between;
  gap: 12px;
}

.event-summary span,
.risk-list span {
  color: var(--muted);
  font-size: 11px;
}

.score-row {
  gap: 20px;
  margin-top: 8px;
  color: var(--muted);
  font-size: 12px;
}

.score-row b {
  color: var(--ink-soft);
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px 14px;
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--paper-deep);
  color: var(--muted);
  font-size: 11px;
}

.evidence-grid h3,
.evidence-grid p {
  grid-column: 1 / -1;
}

.evidence-grid p {
  margin: 0;
}

.risk-list > div {
  padding: 10px 0;
  border-top: 1px solid var(--line);
}

.timeline-section :deep(.el-timeline) {
  padding: 8px 0 0 5px;
}

.timeline-section p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 820px) {
  .signal-detail-header,
  .state-band,
  .detail-section > header {
    align-items: flex-start;
    flex-direction: column;
  }

  .signal-detail-header .actions {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr);
    width: 100%;
  }

  .signal-detail-header .actions :deep(.el-button) {
    width: 100%;
    min-height: 44px;
    margin: 0;
  }

  .signal-detail-header .actions :deep(.el-button:first-child span) {
    display: none;
  }

  .state-band {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .state-band p {
    grid-column: 1 / -1;
    margin: 0;
  }

  .meaning-band {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .meaning-band > div:nth-child(2) {
    border-right: 0;
  }

  .meaning-band > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
  }

  .score-row {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  .evidence-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
