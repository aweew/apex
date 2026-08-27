<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, DocumentCopy, Download, Refresh, Share } from '@element-plus/icons-vue'
import { fetchDailyPreMarketReport, refreshDailyPreMarketReport } from '../api/preMarketReport'
import PreMarketReportShareSheet from '../components/share/PreMarketReportShareSheet.vue'
import { parsePreMarketReport } from '../utils/preMarketReport'
import {
  captureElementBlob,
  copyImageBlob,
  downloadBlob,
  shareFilename,
} from '../utils/shareCapture'

const loading = ref(false)
const refreshing = ref(false)
const report = ref(null)
const shareOpen = ref(false)
const copyingImage = ref(false)
const downloadingImage = ref(false)
const shareSheetRef = ref(null)
const reportDocument = computed(() => parsePreMarketReport(report.value?.content))
const primarySections = computed(() => reportDocument.value.sections.filter(
  (section) => ['03', '04', '05', '07'].includes(section.number),
))
const secondarySections = computed(() => reportDocument.value.sections.filter(
  (section) => section.number !== '01' && !['03', '04', '05', '07'].includes(section.number),
))

const generatedTime = computed(() => {
  if (!report.value?.generatedAt) return ''
  return String(report.value.generatedAt).replace('T', ' ').slice(0, 19)
})

const sourceLabel = computed(() => report.value?.reportSource === 'AI' ? '智能研判' : '规则研判')

function dataLevelLabel(level) {
  if (level === 'GREEN') return '高覆盖'
  if (level === 'YELLOW') return '中等覆盖'
  return '低覆盖'
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

function openShare() {
  if (!report.value?.content) {
    ElMessage.warning('暂无可分享的盘前观点')
    return
  }
  shareOpen.value = true
}

async function captureReportShare() {
  await nextTick()
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  const shareElement = shareSheetRef.value?.getCaptureElement?.()
  if (!shareElement) throw new Error('盘前观点分享图未就绪')
  const width = 760
  const height = Math.ceil(Math.max(shareElement.scrollHeight, shareElement.offsetHeight, 1))
  return captureElementBlob(shareElement, {
    scale: 2,
    width,
    height,
    backgroundColor: '#f7f9fb',
    style: {
      width: `${width}px`,
      height: `${height}px`,
      overflow: 'visible',
      transform: 'none',
      margin: '0',
      opacity: '1',
      letterSpacing: '0',
    },
  })
}

async function copyShareImage() {
  copyingImage.value = true
  try {
    await copyImageBlob(captureReportShare())
    ElMessage.success('盘前观点长图已复制，可直接粘贴分享')
  } catch (error) {
    console.error('复制盘前观点长图失败', error)
    ElMessage.error(error.message || '复制失败，请改用下载')
  } finally {
    copyingImage.value = false
  }
}

async function downloadShareImage() {
  downloadingImage.value = true
  try {
    const imageBlob = await captureReportShare()
    downloadBlob(imageBlob, shareFilename('apex_pre_market', report.value?.tradeDate || reportDocument.value.date))
    ElMessage.success('盘前观点长图已下载')
  } catch (error) {
    console.error('下载盘前观点长图失败', error)
    ElMessage.error(error.message || '下载失败')
  } finally {
    downloadingImage.value = false
  }
}

function resetShareState() {
  copyingImage.value = false
  downloadingImage.value = false
}

onMounted(loadReport)
</script>

<template>
  <div class="page report-page" v-loading="loading">
    <header class="header report-header">
      <div>
        <h1>Apex 每日盘前研报</h1>
      </div>
      <div class="actions">
        <el-tooltip content="生成清晰的盘前观点长图" placement="bottom">
          <el-button type="primary" :icon="Share" :disabled="!report?.content" @click="openShare">分享长图</el-button>
        </el-tooltip>
        <el-tooltip content="复制完整研报" placement="bottom">
          <el-button :icon="DocumentCopy" :disabled="!report?.content" @click="copyReport">复制</el-button>
        </el-tooltip>
        <el-tooltip content="使用当前最新数据重新生成" placement="bottom">
          <el-button :icon="Refresh" :loading="refreshing" @click="refreshReport">重新生成</el-button>
        </el-tooltip>
      </div>
    </header>

    <div v-if="report" class="report-status" aria-label="研报状态">
      <div v-if="report.tradeDate">
        <span>交易日</span>
        <strong>{{ report.tradeDate }}</strong>
      </div>
      <div v-if="report.marketStatus">
        <span>市场状态</span>
        <strong>{{ report.marketStatus }}</strong>
      </div>
      <div v-if="report.sentimentScore != null">
        <span>情绪指数</span>
        <strong>{{ report.sentimentScore }} / 100</strong>
      </div>
      <div>
        <span>覆盖范围</span>
        <strong>{{ report.portfolioCount || 0 }} 组合 · {{ report.holdingCount || 0 }} 持仓</strong>
      </div>
      <div class="status-meta">
        <el-tag size="small" effect="plain" :type="dataLevelType(report.dataLevel)">
          {{ dataLevelLabel(report.dataLevel) }}
        </el-tag>
        <span>{{ sourceLabel }}<template v-if="generatedTime"> · {{ generatedTime }}</template></span>
      </div>
    </div>

    <main v-if="report?.content" class="report-reader" aria-label="盘前研报正文">
      <section class="decision-brief" aria-label="今日核心判断">
        <div v-if="reportDocument.judgement || report.marketJudgement" class="decision-item judgement-item">
          <span>今日判断</span>
          <strong>{{ reportDocument.judgement || report.marketJudgement }}</strong>
        </div>
        <div v-if="reportDocument.priority" class="decision-item priority-item">
          <span>优先方向</span>
          <strong>{{ reportDocument.priority }}</strong>
        </div>
        <div v-if="reportDocument.risk" class="decision-item risk-item">
          <span>最大风险</span>
          <strong>{{ reportDocument.risk }}</strong>
        </div>
      </section>

      <div v-if="primarySections.length" class="primary-sections">
        <section v-for="section in primarySections" :key="section.number" class="report-section">
          <header class="section-heading">
            <span>{{ section.number }}</span>
            <h2>{{ section.title }}</h2>
          </header>
          <div class="section-lines">
            <p v-for="line in section.lines" :key="line">{{ line }}</p>
          </div>
        </section>
      </div>

      <div v-if="secondarySections.length" class="secondary-sections">
        <details v-for="section in secondarySections" :key="section.number" class="secondary-section">
          <summary>
            <span>{{ section.number }}</span>
            <strong>{{ section.title }}</strong>
          </summary>
          <div class="section-lines">
            <p v-for="line in section.lines" :key="line">{{ line }}</p>
          </div>
        </details>
      </div>
    </main>

    <div v-else-if="!loading" class="page-empty">
      <h3>研报尚未生成</h3>
      <p>重新生成后，系统会基于当前可用行情、消息、观察池与组合数据形成研判。</p>
      <el-button type="primary" :icon="Refresh" :loading="refreshing" @click="refreshReport">生成研报</el-button>
    </div>

    <el-dialog
      v-model="shareOpen"
      title="分享盘前观点长图"
      width="min(860px, 94vw)"
      append-to-body
      destroy-on-close
      align-center
      class="pre-market-share-dialog"
      @closed="resetShareState"
    >
      <div class="share-preview-stage" aria-label="盘前观点长图预览">
        <PreMarketReportShareSheet
          v-if="report"
          ref="shareSheetRef"
          :report="report"
          :document="reportDocument"
          :generated-time="generatedTime"
          :source-label="sourceLabel"
          :data-level-label="dataLevelLabel(report.dataLevel)"
        />
      </div>
      <template #footer>
        <el-button @click="shareOpen = false">关闭</el-button>
        <el-button
          type="primary"
          plain
          :icon="CopyDocument"
          :loading="copyingImage"
          @click="copyShareImage"
        >复制图片</el-button>
        <el-button
          type="primary"
          :icon="Download"
          :loading="downloadingImage"
          @click="downloadShareImage"
        >下载 PNG</el-button>
      </template>
    </el-dialog>
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
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
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

.report-reader {
  width: min(100%, 1080px);
  margin: 28px auto 0;
}

.decision-brief {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  border-block: 1px solid #cfd6dc;
  background: #f7f8fa;
}

.decision-item {
  min-width: 0;
  padding: 18px 20px;
  border-right: 1px solid #dde2e7;
}

.decision-item:last-child {
  border-right: 0;
}

.decision-item span {
  display: block;
  margin-bottom: 7px;
  color: #68717c;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0;
}

.decision-item strong {
  display: block;
  color: #17202a;
  font-size: 16px;
  font-weight: 680;
  line-height: 1.6;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.priority-item {
  border-top: 3px solid #2f7d5b;
}

.risk-item {
  border-top: 3px solid #b55245;
}

.primary-sections {
  margin-top: 10px;
}

.report-section {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 28px;
  padding: 24px 4px;
  border-bottom: 1px solid #e2e6ea;
}

.section-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.section-heading span,
.secondary-section summary > span {
  color: #8a949f;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.section-heading h2 {
  margin: 0;
  color: #1e2933;
  font-size: 17px;
  line-height: 1.45;
  letter-spacing: 0;
}

.section-lines {
  min-width: 0;
}

.section-lines p {
  margin: 0 0 10px;
  color: #303943;
  font-size: 15px;
  line-height: 1.75;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.section-lines p:last-child {
  margin-bottom: 0;
}

.secondary-sections {
  margin-top: 18px;
  border-top: 1px solid #dfe4e8;
}

.secondary-section {
  border-bottom: 1px solid #dfe4e8;
}

.secondary-section summary {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 24px;
  align-items: center;
  min-height: 54px;
  cursor: pointer;
  list-style: none;
}

.secondary-section summary::-webkit-details-marker {
  display: none;
}

.secondary-section summary::after {
  content: "+";
  grid-column: 3;
  color: #68717c;
  font-size: 20px;
}

.secondary-section[open] summary::after {
  content: "−";
}

.secondary-section summary strong {
  color: #303943;
  font-size: 15px;
  letter-spacing: 0;
}

.secondary-section .section-lines {
  padding: 0 0 20px 34px;
}

@media (max-width: 760px) {
  .report-header {
    align-items: stretch;
  }

  .report-header .actions {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
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

  .decision-brief {
    grid-template-columns: minmax(0, 1fr);
  }

  .decision-item {
    border-right: 0;
    border-bottom: 1px solid #dde2e7;
  }

  .decision-item:last-child {
    border-bottom: 0;
  }

  .report-section {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
    padding-block: 20px;
  }
}

:global(.pre-market-share-dialog .el-dialog__body) {
  padding: 12px 18px 6px;
}

.share-preview-stage {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  max-height: min(68vh, 760px);
  padding: 14px;
  overflow: auto;
  border: 1px solid #d8dee5;
  background: #e9edf1;
}

.share-preview-stage :deep(.pre-market-share-sheet) {
  flex: 0 0 auto;
  box-shadow: 0 8px 24px rgba(29, 41, 57, 0.12);
}

@media (max-width: 760px) {
  :global(.pre-market-share-dialog .el-dialog__body) {
    padding-inline: 10px;
  }

  :global(.pre-market-share-dialog .el-dialog__footer) {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 8px;
  }

  :global(.pre-market-share-dialog .el-dialog__footer .el-button) {
    min-height: 40px;
    margin: 0;
  }

  .share-preview-stage {
    justify-content: flex-start;
    padding: 8px;
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
