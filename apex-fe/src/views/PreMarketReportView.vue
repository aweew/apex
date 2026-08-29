<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, DocumentCopy, Download, Refresh, Share } from '@element-plus/icons-vue'
import { fetchDailyPreMarketReport, refreshDailyPreMarketReport } from '../api/preMarketReport'
import PreMarketReportSections from '../components/PreMarketReportSections.vue'
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
const reportTitle = computed(() => reportDocument.value.title.replace(/^今日投资机会[｜|]\s*/, '') || '今日投资机会')
const sentimentWidth = computed(() => Math.min(Math.max(report.value?.sentimentScore || 0, 0), 100))

const generatedTime = computed(() => {
  if (!report.value?.generatedAt) return ''
  return String(report.value.generatedAt).replace('T', ' ').slice(0, 19)
})

const sourceLabel = computed(() => report.value?.reportSource === 'AI' ? '智能研判' : '规则研判')

function dataLevelLabel(level) {
  if (level === 'GREEN') return '数据覆盖较完整'
  if (level === 'YELLOW') return '数据覆盖一般'
  return '数据覆盖有限'
}

function sentimentLabel(score) {
  if (score >= 65) return '偏强'
  if (score <= 35) return '偏弱'
  return '均衡'
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
    backgroundColor: '#f8fafb',
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
    <header class="report-toolbar">
      <div class="report-toolbar-copy">
        <span>盘前策略</span>
        <strong v-if="report?.tradeDate">{{ report.tradeDate }}</strong>
      </div>
      <div class="actions report-actions">
        <el-tooltip content="生成盘前观点长图" placement="bottom">
          <el-button type="primary" :icon="Share" :disabled="!report?.content" @click="openShare">分享</el-button>
        </el-tooltip>
        <el-tooltip content="复制完整研报" placement="bottom">
          <el-button :icon="DocumentCopy" :disabled="!report?.content" @click="copyReport">复制</el-button>
        </el-tooltip>
        <el-tooltip content="使用最新数据重新生成" placement="bottom">
          <el-button :icon="Refresh" :loading="refreshing" @click="refreshReport">更新</el-button>
        </el-tooltip>
      </div>
    </header>

    <article v-if="report?.content" class="report-article" aria-label="盘前研报正文">
      <header class="report-lead">
        <div class="report-lead-main">
          <p class="report-kicker">APEX PRE-MARKET RESEARCH</p>
          <h1>{{ reportTitle }}</h1>

          <section class="report-thesis" aria-label="核心观点">
            <span>核心观点</span>
            <p>{{ reportDocument.judgement || report.marketJudgement }}</p>
          </section>
        </div>

        <aside class="market-temperature" aria-label="盘前市场温度">
          <span>市场温度</span>
          <strong v-if="report.sentimentScore != null">{{ report.sentimentScore }}</strong>
          <strong v-else>--</strong>
          <em>{{ report.marketStatus || sentimentLabel(report.sentimentScore) }}</em>
          <i aria-hidden="true"><b :style="{ width: `${sentimentWidth}%` }" /></i>
          <small v-if="report.sentimentScore != null">0 防守 · 100 进攻</small>
        </aside>

        <dl v-if="reportDocument.priority || reportDocument.risk" class="decision-lines">
          <div v-if="reportDocument.priority" class="priority-line">
            <dt>优先方向</dt>
            <dd>{{ reportDocument.priority }}</dd>
          </div>
          <div v-if="reportDocument.risk" class="risk-line">
            <dt>最大风险</dt>
            <dd>{{ reportDocument.risk }}</dd>
          </div>
        </dl>
      </header>

      <PreMarketReportSections :sections="reportDocument.sections" />

      <footer class="report-footnote">
        <span>{{ sourceLabel }} · {{ dataLevelLabel(report.dataLevel) }}</span>
        <span v-if="generatedTime">生成于 {{ generatedTime }}</span>
        <span>{{ report.portfolioCount || 0 }} 个组合 · {{ report.holdingCount || 0 }} 只持仓纳入筛选</span>
      </footer>
    </article>

    <div v-else-if="!loading" class="page-empty">
      <h3>今日研报尚未生成</h3>
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
        <el-button type="primary" plain :icon="CopyDocument" :loading="copyingImage" @click="copyShareImage">
          复制图片
        </el-button>
        <el-button type="primary" :icon="Download" :loading="downloadingImage" @click="downloadShareImage">
          下载 PNG
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.report-page {
  padding-bottom: 72px;
}

.report-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 48px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e6e9;
}

.report-toolbar-copy {
  display: flex;
  align-items: baseline;
  gap: 10px;
  color: #7b8790;
  font-size: 12px;
}

.report-toolbar-copy span {
  color: #314558;
  font-size: 14px;
  font-weight: 700;
}

.report-toolbar-copy strong {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.report-article {
  width: min(100%, 1120px);
  margin: 0 auto;
  color: #26323c;
}

.report-lead {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 190px;
  gap: 36px 48px;
  padding: 56px 0 38px;
}

.report-kicker {
  margin: 0 0 14px;
  color: #3977a6;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0;
}

.report-lead h1 {
  max-width: 820px;
  margin: 0;
  color: #142733;
  font-size: 42px;
  font-weight: 780;
  line-height: 1.22;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.report-thesis {
  max-width: 850px;
  margin-top: 26px;
  padding: 15px 18px;
  border-left: 4px solid #b47b27;
  background: #f6f3ed;
}

.report-thesis > span {
  display: block;
  margin-bottom: 7px;
  color: #88601f;
  font-size: 12px;
  font-weight: 700;
}

.report-thesis p {
  margin: 0;
  color: #273944;
  font-size: 17px;
  font-weight: 650;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.market-temperature {
  align-self: start;
  padding: 4px 0 0 24px;
  border-left: 1px solid #d9e1e5;
}

.market-temperature > span,
.market-temperature strong,
.market-temperature em,
.market-temperature small {
  display: block;
}

.market-temperature > span {
  color: #7d8992;
  font-size: 11px;
  font-weight: 700;
}

.market-temperature strong {
  margin-top: 9px;
  color: #1d3341;
  font-size: 52px;
  font-weight: 760;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.market-temperature em {
  margin-top: 7px;
  color: #3977a6;
  font-size: 13px;
  font-style: normal;
  font-weight: 720;
}

.market-temperature i {
  display: block;
  width: 100%;
  height: 5px;
  margin-top: 18px;
  overflow: hidden;
  border-radius: 3px;
  background: #dfe6ea;
}

.market-temperature b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #3977a6;
}

.market-temperature small {
  margin-top: 7px;
  color: #929ca3;
  font-size: 9px;
}

.decision-lines {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-block: 1px solid #dbe2e6;
}

.decision-lines > div {
  min-width: 0;
  padding: 16px 22px 17px 0;
}

.decision-lines > div + div {
  padding-left: 22px;
  border-left: 1px solid #e3e7ea;
}

.decision-lines dt {
  color: #77838d;
  font-size: 10px;
  font-weight: 720;
}

.decision-lines dd {
  min-width: 0;
  margin: 0;
  color: #273640;
  margin-top: 6px;
  font-size: 15px;
  font-weight: 650;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.risk-line dt {
  color: #a14d47;
}

.report-footnote {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  padding: 18px 0 0;
  color: #8a949c;
  font-size: 11px;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
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
  .report-toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .report-actions {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
  }

  .report-actions :deep(.el-button) {
    width: 100%;
    min-width: 0;
    margin: 0;
  }

  .report-lead {
    grid-template-columns: minmax(0, 1fr) 150px;
    gap: 28px;
    padding: 40px 0 30px;
  }

  .report-lead h1 {
    font-size: 34px;
  }

  .report-thesis p {
    font-size: 16px;
  }

  .decision-lines {
    grid-template-columns: minmax(0, 1fr);
  }

  .decision-lines > div + div {
    padding-left: 0;
    border-top: 1px solid #e3e7ea;
    border-left: 0;
  }

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

@media (max-width: 600px) {
  .report-lead {
    grid-template-columns: minmax(0, 1fr);
  }

  .report-lead h1 {
    font-size: 30px;
  }

  .market-temperature {
    display: grid;
    grid-template-columns: auto auto 1fr;
    align-items: baseline;
    gap: 8px 12px;
    padding: 14px 0 0;
    border-top: 1px solid #d9e1e5;
    border-left: 0;
  }

  .market-temperature strong {
    margin: 0;
    font-size: 34px;
  }

  .market-temperature em {
    margin: 0;
  }

  .market-temperature i,
  .market-temperature small {
    grid-column: 1 / -1;
    margin-top: 0;
  }

  .decision-lines > div {
    padding-block: 13px;
  }
}
</style>
