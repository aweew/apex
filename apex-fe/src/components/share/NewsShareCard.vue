<script setup>
defineProps({
  item: { type: Object, required: true },
  sourceLabel: { type: Function, default: (s) => s },
  fmtTime: { type: Function, default: (t) => t || '-' },
})
</script>

<template>
  <div class="share-card">
    <div class="share-bg" aria-hidden="true" />
    <header class="share-brand">
      <div class="brand-mark">APEX</div>
      <div class="brand-sub">资讯速览</div>
    </header>

    <div class="share-meta">
      <span class="pill">{{ sourceLabel(item.source) }}</span>
      <span v-if="item.sentiment" class="pill tone" :data-tone="item.sentiment">{{ item.sentiment }}</span>
      <span class="time">{{ fmtTime(item.publishedAt) }}</span>
    </div>

    <h2 class="share-title">{{ item.title }}</h2>
    <p class="share-body">{{ item.summary || item.content || '（暂无摘要）' }}</p>

    <div v-if="item.relatedCodes?.length" class="share-codes">
      <span v-for="code in item.relatedCodes.slice(0, 8)" :key="code" class="code">{{ code }}</span>
    </div>

    <footer class="share-foot">
      <span>来自 Apex 本地量化台</span>
      <span class="dot">·</span>
      <span>仅供研究参考</span>
    </footer>
  </div>
</template>

<style scoped>
.share-card {
  position: relative;
  width: 680px;
  padding: 36px 40px 28px;
  color: #1d1d1f;
  background: #f7f4ee;
  overflow: hidden;
  box-sizing: border-box;
  font-family: "Source Han Sans SC", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.share-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 55% at 0% 0%, rgba(180, 120, 60, 0.14), transparent 55%),
    radial-gradient(ellipse 70% 50% at 100% 100%, rgba(40, 80, 120, 0.1), transparent 50%),
    linear-gradient(165deg, #faf7f1 0%, #f0ebe3 48%, #e8eef4 100%);
  pointer-events: none;
}

.share-brand,
.share-meta,
.share-title,
.share-body,
.share-codes,
.share-foot {
  position: relative;
  z-index: 1;
}

.share-brand {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 22px;
}

.brand-mark {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.14em;
  line-height: 1;
}

.brand-sub {
  font-size: 13px;
  color: #6e6e73;
  letter-spacing: 0.08em;
}

.share-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(29, 29, 31, 0.08);
  color: #3a3a3c;
}

.pill.tone[data-tone='利好'] {
  background: rgba(196, 60, 50, 0.12);
  color: #a3281c;
}

.pill.tone[data-tone='利空'] {
  background: rgba(30, 120, 70, 0.12);
  color: #1a6b3c;
}

.time {
  margin-left: auto;
  font-size: 12px;
  color: #86868b;
  font-variant-numeric: tabular-nums;
}

.share-title {
  margin: 0 0 14px;
  font-size: 26px;
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1.35;
}

.share-body {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  color: #3a3a3c;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 280px;
  overflow: hidden;
}

.share-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.code {
  font-size: 13px;
  font-weight: 650;
  padding: 4px 10px;
  border-radius: 8px;
  background: rgba(0, 113, 227, 0.1);
  color: #0b4ea2;
  font-variant-numeric: tabular-nums;
}

.share-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 28px;
  padding-top: 14px;
  border-top: 1px solid rgba(29, 29, 31, 0.08);
  font-size: 12px;
  color: #86868b;
}

.dot {
  opacity: 0.5;
}
</style>
