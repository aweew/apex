<script setup>
import { computed } from 'vue'

const props = defineProps({
  pulse: { type: Object, default: null },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['refresh-llm'])

const cards = computed(() => props.pulse?.cards || [])

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function sourceLabel(s) {
  const map = { eastmoney: '东财', cls: '财联社', ths: '同花顺', sina: '新浪', cctv: '央视' }
  return map[s] || s || '-'
}

function sentimentClass(s) {
  if (s === '利好') return 'bull'
  if (s === '利空') return 'bear'
  return 'flat'
}

function openUrl(url) {
  if (url) window.open(url, '_blank', 'noopener')
}
</script>

<template>
  <section class="pulse" v-loading="loading">
    <header class="pulse-head">
      <div class="pulse-title">
        <h2>今日消息面</h2>
        <span v-if="pulse?.summarySource" class="src-tag">
          {{ pulse.summarySource === 'llm' ? 'Kimi 摘要' : '规则摘要' }}
        </span>
      </div>
      <div class="pills" v-if="pulse">
        <span class="pill bull">利好 {{ pulse.bullCount ?? 0 }}</span>
        <span class="pill bear">利空 {{ pulse.bearCount ?? 0 }}</span>
        <span v-if="pulse.biasLabel" class="pill bias">{{ pulse.biasLabel }}</span>
        <button type="button" class="llm-btn" @click="emit('refresh-llm')">重算摘要</button>
      </div>
    </header>

    <p class="exec" v-if="pulse?.executiveSummary">{{ pulse.executiveSummary }}</p>
    <p class="exec muted" v-else-if="!loading">暂无足够资讯生成消息面，请先刷新资讯。</p>

    <div v-if="pulse?.hotThemes?.length" class="hot-line">
      <label>热点</label>
      <span v-for="t in pulse.hotThemes.slice(0, 6)" :key="t">{{ t }}</span>
    </div>

    <div class="card-grid" v-if="cards.length">
      <article
        v-for="card in cards"
        :key="card.id"
        class="pulse-card"
        :class="sentimentClass(card.sentiment)"
      >
        <div class="card-top">
          <span class="sent" :class="sentimentClass(card.sentiment)">{{ card.sentiment || '中性' }}</span>
          <span class="stars" :title="`${card.stars || 0} 星`">
            <i v-for="n in 5" :key="n" :class="{ on: n <= (card.stars || 0) }">★</i>
          </span>
        </div>
        <h3
          class="card-title"
          :class="{ link: !!card.url }"
          @click="openUrl(card.url)"
        >{{ card.title }}</h3>
        <p class="card-sum">{{ card.summary || '—' }}</p>
        <div v-if="card.relatedCodes?.length" class="related-codes" aria-label="关联个股">
          <router-link
            v-for="code in card.relatedCodes"
            :key="code"
            :to="`/stock/${code}`"
            class="stock-code-link"
            :aria-label="`打开${code}股票详情`"
          >{{ code }}</router-link>
        </div>
        <div class="card-foot">
          <div class="themes">
            <span v-for="th in (card.themes || []).slice(0, 3)" :key="th">{{ th }}</span>
          </div>
          <time>{{ fmtTime(card.publishedAt) }} · {{ sourceLabel(card.source) }}</time>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.pulse {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 16px 18px 18px;
  margin-bottom: 16px;
}
.pulse-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.pulse-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pulse-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 750;
  letter-spacing: -0.02em;
}
.src-tag {
  font-size: 11px;
  color: var(--muted);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 999px;
  padding: 2px 8px;
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.pill {
  font-size: 12px;
  font-weight: 650;
  padding: 3px 9px;
  border-radius: 999px;
  border: 1px solid transparent;
}
.pill.bull {
  color: #c43d4a;
  background: rgba(255, 59, 48, 0.1);
  border-color: rgba(255, 59, 48, 0.18);
}
.pill.bear {
  color: #1f8a4c;
  background: rgba(52, 199, 89, 0.12);
  border-color: rgba(52, 199, 89, 0.2);
}
.pill.bias {
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.08);
  border-color: rgba(0, 113, 227, 0.16);
}
.llm-btn {
  appearance: none;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: #fff;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--ink-soft);
}
.llm-btn:hover {
  border-color: rgba(0, 113, 227, 0.35);
  color: var(--accent);
}
.exec {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink-soft);
}
.exec.muted {
  color: var(--muted);
}
.hot-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 12px;
}
.hot-line label {
  font-size: 11px;
  color: var(--muted);
  margin-right: 2px;
}
.hot-line span {
  font-size: 12px;
  font-weight: 600;
  color: #0a66c2;
  background: rgba(0, 113, 227, 0.08);
  border-radius: 6px;
  padding: 2px 8px;
}
.card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.pulse-card {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  padding: 12px 12px 10px;
  border-left: 3px solid rgba(0, 0, 0, 0.12);
}
.pulse-card.bull {
  border-left-color: rgba(255, 59, 48, 0.55);
}
.pulse-card.bear {
  border-left-color: rgba(52, 199, 89, 0.55);
}
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sent {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 5px;
}
.sent.bull {
  color: #c43d4a;
  background: rgba(255, 59, 48, 0.1);
}
.sent.bear {
  color: #1f8a4c;
  background: rgba(52, 199, 89, 0.12);
}
.sent.flat {
  color: var(--muted);
  background: rgba(0, 0, 0, 0.04);
}
.stars {
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(0, 0, 0, 0.15);
}
.stars i.on {
  color: #d4a017;
  font-style: normal;
}
.stars i {
  font-style: normal;
}
.card-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-title.link {
  cursor: pointer;
}
.card-title.link:hover {
  color: var(--accent);
}
.card-sum {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}
.related-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: -2px 0 9px;
}
.stock-code-link {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 1px 6px;
  border: 1px solid rgba(22, 105, 201, 0.2);
  border-radius: 4px;
  color: var(--accent);
  font-size: 11px;
  line-height: 1.35;
  text-decoration: none;
}
.stock-code-link:hover,
.stock-code-link:focus-visible {
  border-color: rgba(22, 105, 201, 0.45);
  background: rgba(22, 105, 201, 0.06);
  outline: none;
}
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.themes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-width: 0;
}
.themes span {
  font-size: 11px;
  color: #0a66c2;
  font-weight: 600;
}
.card-foot time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 1100px) {
  .card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
