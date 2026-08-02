<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchNewsOverview, refreshNews } from '../api/news'
import { saveObserve } from '../api/observe'
import NewsShareDialog from '../components/share/NewsShareDialog.vue'

const router = useRouter()
const loading = ref(false)
const refreshing = ref(false)
const data = ref(null)
const lastLog = ref('')
const activeSource = ref('all')
const keyword = ref('')
const limit = ref(80)
const shareOpen = ref(false)
const shareItem = ref(null)

const items = computed(() => data.value?.items || [])
const counts = computed(() => data.value?.sourceCounts || {})

const sourceTabs = [
  { key: 'all', label: '全部' },
  { key: 'eastmoney', label: '东财' },
  { key: 'cls', label: '财联社' },
  { key: 'ths', label: '同花顺' },
  { key: 'sina', label: '新浪' },
  { key: 'cctv', label: '央视' },
]

function sourceLabel(s) {
  const hit = sourceTabs.find((t) => t.key === s)
  return hit?.label || s
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function sentimentType(s) {
  if (s === '利好') return 'danger'
  if (s === '利空') return 'success'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await fetchNewsOverview(activeSource.value, limit.value, keyword.value.trim())
    data.value = res.data
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onRefresh(sources = 'eastmoney,cls,ths,sina') {
  refreshing.value = true
  try {
    const res = await refreshNews(sources, limit.value)
    data.value = res.data?.overview || data.value
    lastLog.value = res.data?.log || ''
    ElMessage.success(res.data?.message || '新闻已刷新')
  } catch (e) {
    ElMessage.error(e.message || '刷新失败，可命令行运行 sync_news.py')
    await load()
  } finally {
    refreshing.value = false
  }
}

function openUrl(row, event) {
  event?.stopPropagation?.()
  if (row?.url) window.open(row.url, '_blank', 'noopener')
}

/** 资讯关联代码批量进观察池（最多 3 只） */
async function addRelatedObserve(row) {
  const codes = (row?.relatedCodes || []).filter(Boolean).slice(0, 3)
  if (!codes.length) {
    ElMessage.warning('无关联代码')
    return
  }
  let ok = 0
  for (const code of codes) {
    try {
      await saveObserve({
        code,
        status: 'WATCHING',
        reason: '资讯关联',
        tags: 'news',
        note: String(row.title || '').slice(0, 80),
      })
      ok++
    } catch {
      /* 单票失败继续 */
    }
  }
  if (ok) ElMessage.success(`已将 ${ok} 只关联股写入观察池`)
  else ElMessage.error('加入观察池失败')
}

function openShare(row, event) {
  event?.preventDefault?.()
  event?.stopPropagation?.()
  if (!row) return
  shareItem.value = { ...row }
  shareOpen.value = true
}

function isYaowen(row) {
  const text = `${row?.summary || ''}${row?.content || ''}`
  return text.includes('【要闻】')
}

function displaySummary(row) {
  const text = row?.summary || row?.content || ''
  return String(text).replace(/^【要闻】/, '')
}

let debounceTimer
watch([activeSource, limit], () => load())
watch(keyword, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(load, 280)
})

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading || refreshing">
    <header class="header">
      <div>
        <p class="eyebrow">Apex · News</p>
        <h1>新闻资讯</h1>
        <p>{{ data?.message || '聚合东财 / 财联社 / 同花顺 / 新浪财经热点快讯' }}</p>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="refreshing" @click="onRefresh()">刷新全部</el-button>
        <el-button :loading="refreshing" @click="onRefresh('eastmoney,cls')">快刷(东财+财联社)</el-button>
        <el-button plain @click="router.push('/hot')">市场热点</el-button>
        <el-button plain @click="router.push('/decision')">智能决策</el-button>
        <el-button text @click="load">刷新</el-button>
      </div>
    </header>

    <div class="summary" v-if="data">
      <div v-for="tab in sourceTabs.filter((t) => t.key !== 'all')" :key="tab.key">
        <label>{{ tab.label }}</label>
        <span>{{ fmtTime(data.snapshotTimes?.[tab.key]) }}</span>
        <small>· 近3日 {{ counts[tab.key] ?? 0 }}</small>
      </div>
    </div>

    <div class="toolbar">
      <el-radio-group v-model="activeSource" size="small">
        <el-radio-button v-for="tab in sourceTabs" :key="tab.key" :value="tab.key">
          {{ tab.label }}
          <template v-if="tab.key !== 'all'">({{ counts[tab.key] ?? 0 }})</template>
        </el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索标题/正文/代码"
        style="width: 220px"
      />
      <el-select v-model="limit" style="width: 110px">
        <el-option :value="40" label="40 条" />
        <el-option :value="80" label="80 条" />
        <el-option :value="150" label="150 条" />
      </el-select>
    </div>

    <div v-if="!loading && !items.length" class="page-empty">
      <h3>暂无资讯</h3>
      <p>拉取东财 / 财联社等快讯后，可交叉对照热点与决策清单</p>
      <el-button type="primary" :loading="refreshing" @click="onRefresh()">立即刷新</el-button>
      <el-button plain @click="router.push('/hot')">看市场热点</el-button>
      <el-button plain @click="router.push('/decision')">智能决策</el-button>
    </div>

    <div v-else class="news-list">
      <article v-for="row in items" :key="row.id" class="news-card">
        <div class="news-head">
          <el-tag size="small" effect="plain">{{ sourceLabel(row.source) }}</el-tag>
          <el-tag
            v-if="isYaowen(row)"
            size="small"
            type="warning"
            effect="dark"
          >要闻</el-tag>
          <el-tag v-if="row.sentiment" size="small" :type="sentimentType(row.sentiment)" effect="light">
            {{ row.sentiment }}
          </el-tag>
          <time>{{ fmtTime(row.publishedAt) }}</time>
        </div>
        <h3
          class="news-title"
          :class="{ link: !!row.url }"
          @click="openUrl(row, $event)"
        >{{ row.title }}</h3>
        <p class="news-summary">{{ displaySummary(row) }}</p>
        <div v-if="row.relatedCodes?.length" class="news-codes">
          <el-button
            v-for="code in row.relatedCodes"
            :key="code"
            link
            type="primary"
            @click="router.push(`/stock/${code}`)"
          >{{ code }}</el-button>
          <el-button
            link
            type="warning"
            @click="addRelatedObserve(row)"
          >相关进观察</el-button>
        </div>
        <div class="news-ops">
          <button
            type="button"
            class="op-btn"
            :disabled="!row.url"
            @click="openUrl(row, $event)"
          >看原文</button>
          <button
            type="button"
            class="op-btn primary"
            @click="openShare(row, $event)"
          >截图分享</button>
        </div>
      </article>
    </div>

    <NewsShareDialog v-model="shareOpen" :item="shareItem" />

    <el-collapse v-if="lastLog" style="margin-top: 16px">
      <el-collapse-item title="最近刷新日志" name="log">
        <pre class="log">{{ lastLog }}</pre>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.summary > div {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.summary label {
  display: block;
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
}

.summary span {
  font-size: 13px;
  font-weight: 600;
}

.summary small {
  margin-left: 6px;
  color: var(--muted);
  font-size: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.news-list {
  display: grid;
  gap: 10px;
}

.news-card {
  background: var(--glass);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  padding: 14px 16px;
  cursor: default;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.news-card:hover {
  border-color: rgba(0, 113, 227, 0.35);
  box-shadow: var(--shadow-soft);
}

.news-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.news-head time {
  margin-left: auto;
  font-size: 12px;
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.news-ops {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.op-btn {
  appearance: none;
  border: 1px solid rgba(0, 0, 0, 0.12);
  background: rgba(255, 255, 255, 0.85);
  color: var(--ink, #1d1d1f);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 8px 14px;
  border-radius: 10px;
  cursor: pointer;
}

.op-btn:hover:not(:disabled) {
  border-color: rgba(0, 113, 227, 0.35);
  color: var(--accent, #0071e3);
}

.op-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.op-btn.primary {
  background: var(--accent, #0071e3);
  border-color: var(--accent, #0071e3);
  color: #fff;
}

.op-btn.primary:hover {
  background: var(--accent-hover, #0077ed);
  border-color: var(--accent-hover, #0077ed);
  color: #fff;
}

.news-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.45;
}

.news-title.link {
  color: var(--ink);
  cursor: pointer;
}

.news-title.link:hover {
  color: var(--accent);
}

.news-summary {
  margin: 0;
  font-size: 13px;
  line-height: 1.65;
  color: var(--ink-soft);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-codes {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.log {
  margin: 0;
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--slate);
}

@media (max-width: 900px) {
  .summary {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
