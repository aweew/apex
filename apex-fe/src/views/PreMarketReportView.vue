<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Refresh } from '@element-plus/icons-vue'
import { fetchDailyPreMarketReport, refreshDailyPreMarketReport } from '../api/preMarketReport'

const loading = ref(false)
const refreshing = ref(false)
const report = ref(null)

const generatedTime = computed(() => {
  if (!report.value?.generatedAt) return '生成时间暂缺'
  return String(report.value.generatedAt).replace('T', ' ').slice(0, 19)
})

const sourceLabel = computed(() => report.value?.reportSource === 'AI' ? '智能研判' : '规则研判')

function dataLevelLabel(level) {
  if (level === 'GREEN') return '数据完整'
  if (level === 'YELLOW') return '部分数据缺失'
  return '关键数据不足'
}

function dataLevelType(level) {
  if (level === 'GREEN') return 'success'
  if (level === 'YELLOW') return 'warning'
  return 'danger'
}

async function loadReport() {
  loading.value = true
  try {
    const response = await fetchDailyPreMarketReport()
    report.value = response.data
  } catch (error) {
    ElMessage.error(error.message || '盘前研报加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshReport() {
  refreshing.value = true
  try {
    const response = await refreshDailyPreMarketReport()
    report.value = response.data
    ElMessage.success('盘前研报已重新生成')
  } catch (error) {
    ElMessage.error(error.message || '盘前研报生成失败')
  } finally {
    refreshing.value = false
  }
}

async function copyReport() {
  const content = report.value?.content
  if (!content) {
    ElMessage.warning('暂无可复制的研报')
    return
  }
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('研报已复制')
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}

onMounted(loadReport)
</script>

<template>
  <div class="page report-page" v-loading="loading">
    <header class="header report-header">
      <div>
        <p class="eyebrow">Research</p>
        <h1>Apex 每日盘前研报</h1>
        <p>市场、主线、观察池与组合风险的盘前统一判断</p>
      </div>
      <div class="actions">
        <el-tooltip content="复制完整研报" placement="bottom">
          <el-button :icon="DocumentCopy" :disabled="!report?.content" @click="copyReport">复制</el-button>
        </el-tooltip>
        <el-tooltip content="使用当前最新数据重新生成" placement="bottom">
          <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">重新生成</el-button>
        </el-tooltip>
      </div>
    </header>

    <div v-if="report" class="report-status" aria-label="研报状态">
      <div>
        <span>交易日</span>
        <strong>{{ report.tradeDate || '暂缺' }}</strong>
      </div>
      <div>
        <span>市场状态</span>
        <strong>{{ report.marketStatus || '数据暂缺' }}</strong>
      </div>
      <div>
        <span>情绪指数</span>
        <strong>{{ report.sentimentScore == null ? '数据暂缺' : `${report.sentimentScore} / 100` }}</strong>
      </div>
      <div>
        <span>覆盖范围</span>
        <strong>{{ report.portfolioCount || 0 }} 组合 · {{ report.holdingCount || 0 }} 持仓</strong>
      </div>
      <div class="status-meta">
        <el-tag size="small" effect="plain" :type="dataLevelType(report.dataLevel)">
          {{ dataLevelLabel(report.dataLevel) }}
        </el-tag>
        <span>{{ sourceLabel }} · {{ generatedTime }}</span>
      </div>
    </div>

    <section v-if="report?.missingData?.length" class="missing-band" aria-labelledby="missing-title">
      <div>
        <p id="missing-title">本次数据缺口</p>
        <span>以下项目不会被当作中性数据参与判断</span>
      </div>
      <ul>
        <li v-for="item in report.missingData" :key="item">{{ item }}</li>
      </ul>
    </section>

    <main v-if="report?.content" class="report-reader" aria-label="盘前研报正文">
      <article>{{ report.content }}</article>
    </main>

    <div v-else-if="!loading" class="page-empty">
      <h3>研报尚未生成</h3>
      <p>重新生成后，系统会基于当前可用行情、消息、观察池与组合数据形成研判。</p>
      <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">生成研报</el-button>
    </div>
  </div>
</template>

<style scoped>
.report-page {
  padding-bottom: 64px;
}

.report-header {
  align-items: flex-end;
}

.report-status {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1px;
  margin-top: 20px;
  border-block: 1px solid #dfe3e8;
  background: #dfe3e8;
}

.report-status > div {
  min-width: 0;
  padding: 14px 16px;
  background: #fff;
}

.report-status span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  letter-spacing: 0;
}

.report-status strong {
  display: block;
  margin-top: 4px;
  color: #17202a;
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.report-status .status-meta {
  grid-column: 1 / -1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-block: 10px;
}

.status-meta span {
  text-align: right;
}

.missing-band {
  display: grid;
  grid-template-columns: minmax(180px, 0.7fr) minmax(0, 2fr);
  gap: 24px;
  margin-top: 24px;
  padding: 18px 0;
  border-block: 1px solid #ead7a4;
  background: #fffdf5;
}

.missing-band > div,
.missing-band ul {
  padding-inline: 18px;
}

.missing-band p {
  margin: 0 0 4px;
  color: #7a4d00;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0;
}

.missing-band span,
.missing-band li {
  color: #735f3a;
  font-size: 13px;
  line-height: 1.65;
  letter-spacing: 0;
}

.missing-band ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 24px;
  margin: 0;
}

.report-reader {
  width: min(100%, 880px);
  margin: 38px auto 0;
}

.report-reader article {
  color: #20262e;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 16px;
  line-height: 1.9;
  letter-spacing: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

@media (max-width: 760px) {
  .report-header {
    align-items: stretch;
  }

  .report-header .actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .report-header .actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    margin: 0;
  }

  .report-status {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .missing-band {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .missing-band ul {
    grid-template-columns: minmax(0, 1fr);
    padding-left: 36px;
  }

  .report-reader {
    margin-top: 28px;
  }

  .report-reader article {
    font-size: 15px;
    line-height: 1.85;
  }
}

@media (max-width: 420px) {
  .report-status {
    grid-template-columns: minmax(0, 1fr);
  }

  .report-status .status-meta {
    grid-column: auto;
    align-items: flex-start;
    flex-direction: column;
  }

  .status-meta span {
    text-align: left;
  }
}
</style>
