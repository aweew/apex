<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ChatDotRound,
  CircleCheck,
  DataAnalysis,
  EditPen,
  MagicStick,
  Promotion,
  Refresh,
  TrendCharts,
  Warning,
} from '@element-plus/icons-vue'
import {
  analyzeWithXiaoling,
  enhanceXiaolingAnalysis,
  getApexAiContext,
  getLatestApexAiConversation,
} from '../api/apexAi'

const analysisModes = [
  { label: '自动', value: 'AUTO' },
  { label: '组合', value: 'PORTFOLIO' },
  { label: '策略', value: 'STRATEGY' },
]
const router = useRouter()
const context = ref({ aiConfigured: false, portfolios: [], strategies: [], recommendedQuestions: [] })
const contextLoading = ref(true)
const contextError = ref('')
const analysisMode = ref('AUTO')
const selectedPortfolioId = ref(null)
const selectedStrategyId = ref('')
const question = ref('')
const analyzing = ref(false)
const messages = ref([])
const threadRef = ref(null)
const conversationId = ref(null)

const hasContext = computed(() => context.value.portfolios.length || context.value.strategies.length)
const selectedPortfolio = computed(() => context.value.portfolios
  .find((portfolio) => portfolio.id === selectedPortfolioId.value))
const selectedStrategy = computed(() => context.value.strategies
  .find((strategy) => strategy.strategyId === selectedStrategyId.value))
const scopeLabel = computed(() => {
  if (analysisMode.value === 'PORTFOLIO') return selectedPortfolio.value?.name || '默认组合'
  if (analysisMode.value === 'STRATEGY') return selectedStrategy.value?.strategyName || '自动选择弱势策略'
  return '自动识别数据范围'
})

async function loadContext() {
  contextLoading.value = true
  contextError.value = ''
  try {
    const response = await getApexAiContext()
    context.value = response.data || context.value
    const defaultPortfolio = context.value.portfolios.find((portfolio) => portfolio.defaultPortfolio)
    selectedPortfolioId.value = defaultPortfolio?.id || context.value.portfolios[0]?.id || null
    selectedStrategyId.value = context.value.strategies[0]?.strategyId || ''
  } catch (error) {
    contextError.value = error.message || '分析上下文加载失败'
  } finally {
    contextLoading.value = false
  }
}

async function loadLatestConversation() {
  try {
    const response = await getLatestApexAiConversation()
    const conversation = response.data
    if (!conversation?.conversationId || !conversation.messages?.length) return
    conversationId.value = conversation.conversationId
    if (analysisModes.some((mode) => mode.value === conversation.lastAnalysisType)) {
      analysisMode.value = conversation.lastAnalysisType
    }
    messages.value = conversation.messages.map((message) => {
      if (message.role === 'USER') {
        return {
          id: message.id || `${message.requestId}-user`,
          role: 'user',
          text: message.content,
          scope: message.analysisType || '历史分析',
        }
      }
      return {
        id: message.id || message.requestId,
        role: 'assistant',
        analysis: message.analysis,
      }
    }).filter((message) => message.role === 'user' || message.analysis)
    await scrollToLatest()
  } catch {
    // 会话恢复失败不影响新的规则分析。
  }
}

function startNewConversation() {
  if (analyzing.value) return
  conversationId.value = null
  messages.value = []
  question.value = ''
}

function inferMode(prompt) {
  if (/策略|失效|胜率|共振|超额/.test(prompt)) return 'STRATEGY'
  if (/组合|收益|盈亏|亏|赚|板块|持仓/.test(prompt)) return 'PORTFOLIO'
  return 'AUTO'
}

function askSuggested(prompt, forcedMode = '') {
  question.value = prompt
  analysisMode.value = analysisModes.some((mode) => mode.value === forcedMode) ? forcedMode : 'AUTO'
  submitQuestion()
}

function runAnalysisAction(action) {
  if (!action?.route) return
  router.push(action.route)
}

async function scrollToLatest() {
  await nextTick()
  threadRef.value?.lastElementChild?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' })
}

async function enhanceMessage(messageId) {
  const message = messages.value.find((item) => item.id === messageId)
  if (!message?.analysis) return
  try {
    const response = await enhanceXiaolingAnalysis({
      conversationId: message.analysis.conversationId,
      requestId: message.analysis.requestId,
    })
    if (response.data?.aiEnhanced) message.analysis = response.data
    else message.enhanceFailed = true
  } catch {
    message.enhanceFailed = true
  } finally {
    message.enhancing = false
  }
}

async function submitQuestion() {
  const prompt = question.value.trim()
  if (!prompt || analyzing.value) return
  const pendingMessageId = `${Date.now()}-assistant`
  const requestedAnalysisType = analysisMode.value
  messages.value.push(
    { id: `${Date.now()}-user`, role: 'user', text: prompt, scope: scopeLabel.value },
    { id: pendingMessageId, role: 'assistant', pending: true, analysisType: requestedAnalysisType },
  )
  question.value = ''
  analyzing.value = true
  await scrollToLatest()
  try {
    const response = await analyzeWithXiaoling({
      conversationId: conversationId.value,
      question: prompt,
      analysisType: analysisMode.value,
      portfolioId: analysisMode.value === 'PORTFOLIO' ? selectedPortfolioId.value : null,
      strategyId: analysisMode.value === 'STRATEGY' ? selectedStrategyId.value || null : null,
      days: 60,
    })
    if (!response.data) throw new Error('分析结果为空，请稍后重试')
    conversationId.value = response.data.conversationId
    const assistantMessage = {
      id: response.data.requestId || `${Date.now()}-assistant`,
      role: 'assistant',
      analysis: response.data,
      enhancing: context.value.aiConfigured,
      enhanceFailed: false,
    }
    const pendingMessageIndex = messages.value.findIndex((message) => message.id === pendingMessageId)
    if (pendingMessageIndex >= 0) messages.value.splice(pendingMessageIndex, 1, assistantMessage)
    else messages.value.push(assistantMessage)
    analyzing.value = false
    await scrollToLatest()
    if (assistantMessage.enhancing) enhanceMessage(assistantMessage.id)
  } catch (error) {
    const pendingMessageIndex = messages.value.findIndex((message) => message.id === pendingMessageId)
    const errorMessage = {
      id: `${Date.now()}-error`,
      role: 'error',
      text: error.message || '分析失败，请稍后重试',
    }
    if (pendingMessageIndex >= 0) messages.value.splice(pendingMessageIndex, 1, errorMessage)
    else messages.value.push(errorMessage)
  } finally {
    analyzing.value = false
    await scrollToLatest()
  }
}

function formatNumber(value, digits = 2) {
  if (value == null || Number.isNaN(Number(value))) return '--'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits })
}

function formatSigned(value, suffix = '') {
  if (value == null || Number.isNaN(Number(value))) return '--'
  return `${Number(value) > 0 ? '+' : ''}${formatNumber(value)}${suffix}`
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '时间未知'
}

function dataLabel(level) {
  if (level === 'GREEN') return '数据完整'
  if (level === 'YELLOW') return '部分数据'
  return '样本不足'
}

function contributorValue(analysis, contributor) {
  if (contributor.displayValue) return contributor.displayValue
  return analysis.analysisType === 'PORTFOLIO'
    ? formatSigned(contributor.value, ' 元')
    : formatSigned(contributor.value, '%')
}

function onComposerKeydown(event) {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return
  event.preventDefault()
  submitQuestion()
}

onMounted(() => Promise.allSettled([loadContext(), loadLatestConversation()]))
</script>

<template>
  <div class="page apex-ai-page">
    <header class="header ai-header">
      <div>
        <p class="eyebrow">AI CENTER</p>
        <h1>Apex AI</h1>
        <p>小灵 · 基于组合、决策与归因数据的智能研究助理</p>
      </div>
      <div class="ai-status" :class="context.aiConfigured ? 'online' : 'rules'">
        <el-icon><CircleCheck v-if="context.aiConfigured" /><DataAnalysis v-else /></el-icon>
        <span>{{ context.aiConfigured ? 'AI 增强已连接' : '规则分析可用' }}</span>
      </div>
    </header>

    <el-alert v-if="contextError" class="context-error" :title="contextError" type="error" :closable="false" show-icon>
      <template #default><el-button text :icon="Refresh" @click="loadContext">重新加载</el-button></template>
    </el-alert>

    <section class="ai-workbench" aria-label="小灵分析工作台">
      <aside class="context-rail" aria-label="分析范围">
        <div class="rail-heading"><span>分析范围</span><small>{{ scopeLabel }}</small></div>
        <el-segmented v-model="analysisMode" :options="analysisModes" block :disabled="analyzing" />

        <div v-if="analysisMode === 'PORTFOLIO'" class="scope-field">
          <label for="ai-portfolio">组合</label>
          <el-select id="ai-portfolio" v-model="selectedPortfolioId" :loading="contextLoading"
            :disabled="analyzing || !context.portfolios.length" placeholder="暂无组合">
            <el-option v-for="portfolio in context.portfolios" :key="portfolio.id" :label="portfolio.name" :value="portfolio.id">
              <span>{{ portfolio.name }}</span><small>{{ portfolio.positionCount || 0 }} 只持仓</small>
            </el-option>
          </el-select>
        </div>

        <div v-if="analysisMode === 'STRATEGY'" class="scope-field">
          <label for="ai-strategy">策略</label>
          <el-select id="ai-strategy" v-model="selectedStrategyId" :loading="contextLoading"
            :disabled="analyzing || !context.strategies.length" placeholder="自动选择" clearable>
            <el-option v-for="strategy in context.strategies" :key="strategy.strategyId"
              :label="strategy.strategyName" :value="strategy.strategyId">
              <span>{{ strategy.strategyName }}</span><small>{{ strategy.measuredCount || 0 }} 个样本</small>
            </el-option>
          </el-select>
        </div>

        <div class="data-inventory" :class="{ loading: contextLoading }">
          <div><b>{{ context.portfolios.length }}</b><span>可分析组合</span></div>
          <div><b>{{ context.strategies.length }}</b><span>已有策略样本</span></div>
        </div>

        <div class="quick-questions">
          <span>快捷分析</span>
          <button v-for="prompt in context.recommendedQuestions" :key="prompt" type="button"
            :disabled="analyzing" @click="askSuggested(prompt)">
            <el-icon><TrendCharts v-if="inferMode(prompt) === 'STRATEGY'" /><DataAnalysis v-else /></el-icon>
            <span>{{ prompt }}</span>
          </button>
        </div>
      </aside>

      <main class="conversation-panel">
        <div class="conversation-head">
          <div class="assistant-identity"><el-icon><MagicStick /></el-icon><div><b>小灵</b><span>Apex AI Analyst</span></div></div>
          <div class="conversation-tools">
            <span class="privacy-note">聚合数据分析</span>
            <el-tooltip content="新对话" placement="top">
              <el-button :icon="EditPen" circle text :disabled="analyzing"
                aria-label="新对话" @click="startNewConversation" />
            </el-tooltip>
          </div>
        </div>

        <div ref="threadRef" class="conversation-thread" aria-live="polite">
          <div v-if="!messages.length && !analyzing" class="starter-view">
            <div class="starter-mark"><MagicStick /></div>
            <h2>现在先做什么？</h2>
            <div class="starter-actions">
              <button type="button" @click="askSuggested('今天应该买什么？')">
                <TrendCharts /><span><b>今日操作</b><small>读取市场立场与今日决策</small></span>
              </button>
              <button type="button" :disabled="!context.portfolios.length" @click="askSuggested('我的持仓风险怎么样？')">
                <DataAnalysis /><span><b>组合处理</b><small>优先复核持仓风险与止损</small></span>
              </button>
              <button type="button" :disabled="!context.strategies.length" @click="askSuggested('这个策略最近为什么失效？')">
                <TrendCharts /><span><b>策略有效性诊断</b><small>核对样本、共振与市场立场</small></span>
              </button>
            </div>
            <p v-if="!contextLoading && !hasContext">当前还没有可分析的组合或策略样本</p>
          </div>

          <template v-for="message in messages" :key="message.id">
            <div v-if="message.role === 'user'" class="message user-message">
              <div class="message-meta">{{ message.scope }}</div><p>{{ message.text }}</p>
            </div>

            <article v-else-if="message.role === 'assistant' && message.pending"
              class="message assistant-message pending-message" aria-busy="true">
              <header class="answer-head">
                <div>
                  <span class="answer-kicker">{{ message.analysisType }} ANALYST</span>
                  <h2>正在分析 Apex 数据</h2>
                </div>
                <span class="pending-status"><el-icon><Refresh /></el-icon>读取中</span>
              </header>
              <p>正在汇总组合、决策与归因数据</p>
              <div class="pending-lines" aria-hidden="true"><i /><i /><i /></div>
            </article>

            <article v-else-if="message.role === 'assistant' && message.analysis" class="message assistant-message">
              <header class="answer-head">
                <div>
                  <span class="answer-kicker">{{ message.analysis.analysisType }} ANALYST</span>
                  <h2>{{ message.analysis.title }}</h2>
                </div>
                <span class="data-badge" :class="message.analysis.dataLevel?.toLowerCase()">
                  <el-icon><CircleCheck v-if="message.analysis.dataLevel === 'GREEN'" /><Warning v-else /></el-icon>
                  {{ dataLabel(message.analysis.dataLevel) }}
                </span>
              </header>
              <p class="answer-summary">{{ message.analysis.summary }}</p>

              <div v-if="message.analysis.metrics?.length" class="metric-grid">
                <div v-for="metric in message.analysis.metrics" :key="metric.label" class="metric-item">
                  <span>{{ metric.label }}</span><b :class="metric.tone?.toLowerCase()">{{ metric.value }}</b><small>{{ metric.detail }}</small>
                </div>
              </div>

              <div v-if="message.analysis.contributors?.length || message.analysis.suggestions?.length" class="analysis-layout">
                <section v-if="message.analysis.contributors?.length" class="evidence-section">
                  <div class="section-title"><span>主要证据</span><small>{{ message.analysis.contributors.length }} 项</small></div>
                  <ol class="contributor-list">
                    <li v-for="item in message.analysis.contributors" :key="`${item.rank}-${item.name}`">
                      <span class="contributor-rank">{{ String(item.rank).padStart(2, '0') }}</span>
                      <div><b>{{ item.name }}</b><p>{{ item.detail }}</p></div>
                      <span class="contributor-value" :class="item.direction?.toLowerCase()">
                        {{ contributorValue(message.analysis, item) }}
                        <small v-if="item.contributionPct != null">{{ formatSigned(item.contributionPct, ' pct') }}</small>
                      </span>
                    </li>
                  </ol>
                </section>

                <section v-if="message.analysis.suggestions?.length" class="suggestion-section">
                  <div class="section-title"><span>研究建议</span></div>
                  <ul><li v-for="suggestion in message.analysis.suggestions" :key="suggestion">
                    <el-icon><Promotion /></el-icon><span>{{ suggestion }}</span>
                  </li></ul>
                </section>
              </div>

              <div v-if="message.analysis.actions?.length" class="analysis-actions">
                <span>下一步</span>
                <el-button v-for="action in message.analysis.actions" :key="`${action.label}-${action.route}`"
                  size="small" :type="action.tone === 'PRIMARY' ? 'primary' : 'default'" @click="runAnalysisAction(action)">
                  {{ action.label }}
                </el-button>
              </div>

              <footer class="answer-footer">
                <span>{{ message.analysis.dataNote }}</span>
                <span>{{ formatTime(message.analysis.dataAsOf || message.analysis.generatedAt) }}</span>
                <span v-if="message.enhancing" class="enhancement-state">
                  <el-icon><Refresh /></el-icon>AI 解读中
                </span>
                <span v-else-if="message.enhanceFailed" class="enhancement-state failed">AI 增强暂不可用</span>
                <span v-else>{{ message.analysis.aiEnhanced ? 'AI 增强' : '规则分析' }}</span>
              </footer>
              <div v-if="message.analysis.followUpQuestions?.length" class="follow-ups">
                <span>继续追问</span>
                <button v-for="prompt in message.analysis.followUpQuestions" :key="prompt" type="button"
                  :disabled="analyzing" @click="askSuggested(prompt, message.analysis.analysisType)">{{ prompt }}</button>
              </div>
            </article>

            <div v-else-if="message.role === 'error'" class="message error-message">
              <el-icon><Warning /></el-icon><span>{{ message.text }}</span>
            </div>
          </template>
        </div>

        <form class="composer" @submit.prevent="submitQuestion">
          <el-input v-model="question" type="textarea" :rows="2" resize="none" maxlength="500"
            :disabled="analyzing" placeholder="问小灵：为什么今天收益下跌？" aria-label="向小灵提问"
            @keydown="onComposerKeydown" />
          <el-tooltip content="发送问题" placement="top">
            <el-button class="send-button" type="primary" circle :icon="ChatDotRound" :loading="analyzing"
              :disabled="!question.trim()" aria-label="发送问题" native-type="submit" />
          </el-tooltip>
        </form>
      </main>
    </section>
  </div>
</template>

<style scoped>
.apex-ai-page { padding-bottom: 24px; }
.ai-header { align-items: flex-end; }
.ai-header h1 { margin-bottom: 4px; }
.ai-status { display: inline-flex; align-items: center; gap: 7px; min-height: 34px; padding: 6px 10px; border: 1px solid #d9dee7; border-radius: 6px; color: #556274; background: #fff; font-size: 12px; font-weight: 700; }
.ai-status.online { color: #176b46; border-color: #b8decf; background: #f3faf7; }
.context-error { margin-bottom: 12px; }
.ai-workbench { display: grid; grid-template-columns: minmax(220px, 280px) minmax(0, 1fr); min-height: min(720px, calc(100vh - 184px)); border: 1px solid #dce1e8; border-radius: 8px; background: #fff; overflow: hidden; }
.context-rail { min-width: 0; padding: 18px; border-right: 1px solid #e1e5eb; background: #f7f8fa; }
.rail-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 14px; }
.rail-heading > span, .quick-questions > span, .section-title > span { color: #171b22; font-size: 13px; font-weight: 800; }
.rail-heading small { max-width: 130px; color: #7a8493; font-size: 11px; text-align: right; overflow-wrap: anywhere; }
.scope-field { margin-top: 16px; }
.scope-field label { display: block; margin-bottom: 6px; color: #697485; font-size: 11px; font-weight: 700; }
.scope-field :deep(.el-select) { width: 100%; }
.scope-field :deep(.el-select-dropdown__item) { display: flex; justify-content: space-between; }
.scope-field :deep(.el-select-dropdown__item small) { color: #9098a4; }
.data-inventory { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 18px -18px 0; padding: 16px 18px; border-top: 1px solid #e1e5eb; border-bottom: 1px solid #e1e5eb; }
.data-inventory > div + div { padding-left: 14px; border-left: 1px solid #dce1e8; }
.data-inventory b, .data-inventory span { display: block; }
.data-inventory b { color: #171b22; font-size: 22px; }
.data-inventory span { margin-top: 2px; color: #7a8493; font-size: 10px; }
.data-inventory.loading { opacity: .55; }
.quick-questions { margin-top: 18px; }
.quick-questions > span { display: block; margin-bottom: 8px; }
.quick-questions button { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: center; gap: 8px; width: 100%; min-height: 44px; padding: 8px 0; border: 0; border-bottom: 1px solid #e2e6ec; color: #475467; background: transparent; font: inherit; font-size: 12px; line-height: 1.4; text-align: left; cursor: pointer; }
.quick-questions button:hover:not(:disabled) { color: #1559b7; }
.conversation-panel { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; min-width: 0; min-height: 0; }
.conversation-head { display: flex; align-items: center; justify-content: space-between; min-height: 62px; padding: 10px 20px; border-bottom: 1px solid #e4e7ec; }
.assistant-identity, .conversation-tools { display: flex; align-items: center; gap: 10px; }
.assistant-identity > .el-icon { width: 34px; height: 34px; border-radius: 6px; color: #fff; background: #1f5fae; }
.conversation-head b, .conversation-head span { display: block; }
.conversation-head b { color: #171b22; font-size: 14px; }
.conversation-head span { color: #8a929e; font-size: 10px; }
.privacy-note { padding: 4px 7px; border: 1px solid #dde2e9; border-radius: 4px; color: #697485 !important; background: #f8f9fb; }
.conversation-thread { min-height: 0; padding: 22px; overflow-y: auto; }
.starter-view { display: grid; place-items: center; min-height: 100%; padding: 36px 16px; text-align: center; }
.starter-mark { display: grid; place-items: center; width: 48px; height: 48px; border: 1px solid #bfd0e6; border-radius: 8px; color: #1f5fae; background: #f3f7fc; }
.starter-view h2 { margin: 16px 0 20px; color: #171b22; font-size: 20px; letter-spacing: 0; }
.starter-view > p { margin-top: 14px; color: #9b4f42; font-size: 12px; }
.starter-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 260px)); gap: 10px; width: 100%; justify-content: center; }
.starter-actions button { display: grid; grid-template-columns: 28px minmax(0, 1fr); align-items: center; gap: 10px; min-height: 68px; padding: 12px; border: 1px solid #dce1e8; border-radius: 7px; color: #445064; background: #fff; text-align: left; cursor: pointer; }
.starter-actions button:hover:not(:disabled) { border-color: #9eb9da; background: #f7faff; }
.starter-actions button > svg { width: 23px; color: #1f5fae; }
.starter-actions b, .starter-actions small { display: block; }
.starter-actions b { color: #20252d; font-size: 13px; }
.starter-actions small { margin-top: 3px; color: #7b8491; font-size: 10px; }
.starter-actions button:disabled { cursor: not-allowed; opacity: .45; }
.message + .message { margin-top: 20px; }
.user-message { max-width: min(72%, 680px); margin-left: auto; padding: 10px 13px; border-radius: 7px 7px 2px 7px; color: #fff; background: #215fa8; }
.user-message p { margin: 3px 0 0; font-size: 14px; line-height: 1.6; overflow-wrap: anywhere; }
.message-meta { color: #dbe9f8; font-size: 9px; }
.assistant-message { padding-left: 16px; border-left: 3px solid #1f5fae; }
.pending-message { min-height: 148px; }
.pending-message > p { margin: 14px 0 0; color: #657185; font-size: 12px; }
.pending-status { display: inline-flex; align-items: center; gap: 5px; color: #657185; font-size: 10px; font-weight: 700; }
.pending-status .el-icon { animation: pendingRotate 1.2s linear infinite; }
.pending-lines { display: grid; gap: 9px; margin-top: 18px; }
.pending-lines i { display: block; width: min(76%, 620px); height: 9px; border-radius: 3px; background: #e9edf2; animation: pendingPulse 1.4s ease-in-out infinite; }
.pending-lines i:nth-child(2) { width: min(58%, 470px); animation-delay: .12s; }
.pending-lines i:nth-child(3) { width: min(68%, 550px); animation-delay: .24s; }
.answer-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.answer-kicker { display: block; margin-bottom: 4px; color: #64748a; font-size: 9px; font-weight: 800; }
.answer-head h2 { margin: 0; color: #171b22; font-size: 18px; letter-spacing: 0; }
.data-badge { display: inline-flex; align-items: center; gap: 4px; flex: 0 0 auto; min-height: 26px; padding: 3px 7px; border: 1px solid #cfd6df; border-radius: 4px; color: #697485; font-size: 10px; font-weight: 700; }
.data-badge.green { color: #16643f; border-color: #b8ddcd; background: #f2faf6; }
.data-badge.yellow { color: #8a5a0a; border-color: #ead5aa; background: #fffaf0; }
.data-badge.red { color: #9f342e; border-color: #ebc5c2; background: #fff6f5; }
.answer-summary { max-width: 88ch; margin: 14px 0 0; color: #303846; font-size: 14px; line-height: 1.75; white-space: pre-line; overflow-wrap: anywhere; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin-top: 16px; }
.metric-item { min-width: 0; padding: 10px; border: 1px solid #e0e4ea; border-radius: 6px; background: #fafbfc; }
.metric-item > span, .metric-item > b, .metric-item > small { display: block; }
.metric-item > span { color: #778190; font-size: 10px; }
.metric-item > b { margin-top: 4px; color: #20252d; font-size: 17px; line-height: 1.3; overflow-wrap: anywhere; }
.metric-item > b.up { color: #bc3030; }
.metric-item > b.down { color: #16825d; }
.metric-item > b.warning { color: #99630b; }
.metric-item > small { margin-top: 5px; color: #9299a4; font-size: 9px; line-height: 1.35; }
.analysis-layout { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(240px, 0.65fr); gap: 20px; margin-top: 20px; padding-top: 18px; border-top: 1px solid #e3e7ec; }
.section-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.section-title small { color: #9299a4; font-size: 10px; }
.contributor-list, .suggestion-section ul { margin: 0; padding: 0; list-style: none; }
.contributor-list li { display: grid; grid-template-columns: 26px minmax(0, 1fr) auto; align-items: center; gap: 9px; min-height: 56px; padding: 8px 0; border-bottom: 1px solid #eceff3; }
.contributor-rank { color: #a1a8b2; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }
.contributor-list b { color: #29313d; font-size: 12px; }
.contributor-list p { margin: 3px 0 0; color: #7b8491; font-size: 10px; line-height: 1.4; overflow-wrap: anywhere; }
.contributor-value { color: #505b6c; font-size: 12px; font-weight: 800; text-align: right; }
.contributor-value.positive { color: #bc3030; }
.contributor-value.negative { color: #16825d; }
.contributor-value small { display: block; margin-top: 2px; color: #8a929e; font-size: 9px; font-weight: 500; }
.suggestion-section { padding-left: 18px; border-left: 1px solid #e2e6ec; }
.suggestion-section li { display: grid; grid-template-columns: 18px minmax(0, 1fr); gap: 7px; padding: 9px 0; color: #4b5667; font-size: 11px; line-height: 1.55; }
.suggestion-section .el-icon { margin-top: 2px; color: #1f5fae; }
.analysis-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 16px; }
.analysis-actions > span { margin-right: 2px; color: #7f8997; font-size: 10px; }
.answer-footer { display: flex; flex-wrap: wrap; gap: 6px 16px; margin-top: 14px; color: #9098a4; font-size: 9px; line-height: 1.45; }
.answer-footer span:first-child { flex: 1 1 360px; }
.enhancement-state { display: inline-flex !important; align-items: center; gap: 4px; color: #526b8e; }
.enhancement-state .el-icon { animation: pendingRotate 1.2s linear infinite; }
.enhancement-state.failed { color: #98652a; }
.follow-ups { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 12px; }
.follow-ups > span { margin-right: 2px; color: #7f8997; font-size: 10px; }
.follow-ups button { min-height: 30px; padding: 5px 8px; border: 1px solid #d9dfe7; border-radius: 5px; color: #526075; background: #fff; font: inherit; font-size: 10px; cursor: pointer; }
.follow-ups button:hover:not(:disabled) { color: #1559b7; border-color: #a8bdd8; }
.error-message { display: flex; align-items: center; gap: 8px; padding: 10px; border: 1px solid #ecc9c5; border-radius: 6px; color: #9f342e; background: #fff7f6; font-size: 12px; }
@keyframes pendingRotate { to { transform: rotate(360deg); } }
@keyframes pendingPulse { 0%, 100% { opacity: .45; } 50% { opacity: 1; } }
.composer { display: grid; grid-template-columns: minmax(0, 1fr) 44px; align-items: end; gap: 10px; padding: 12px 16px; border-top: 1px solid #e1e5eb; background: #fbfcfd; }
.composer :deep(.el-textarea__inner) { min-height: 52px !important; max-height: 112px; border-radius: 6px; box-shadow: 0 0 0 1px #d7dde6 inset; line-height: 1.5; }
.send-button { width: 44px; height: 44px; }

@media (max-width: 1080px) { .metric-grid, .starter-actions { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 900px) {
  .ai-header { align-items: flex-start; }
  .ai-workbench { grid-template-columns: 1fr; min-height: auto; overflow: visible; }
  .context-rail { display: grid; grid-template-columns: minmax(0, 1fr) minmax(180px, .8fr); gap: 12px 18px; border-right: 0; border-bottom: 1px solid #e1e5eb; }
  .rail-heading, .context-rail > .el-segmented, .quick-questions { grid-column: 1 / -1; }
  .scope-field { margin-top: 0; }
  .data-inventory { margin: 0; padding: 0; border: 0; }
  .quick-questions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 0; }
  .quick-questions > span { width: 100%; }
  .quick-questions button { width: auto; min-height: 44px; padding: 7px 9px; border: 1px solid #dce1e8; border-radius: 5px; }
  .conversation-panel { min-height: 620px; }
}
@media (max-width: 640px) {
  .ai-status { margin-top: 8px; }
  .context-rail { grid-template-columns: 1fr; padding: 14px; }
  .rail-heading, .context-rail > .el-segmented, .quick-questions { grid-column: auto; }
  .data-inventory { padding: 12px 0; border-top: 1px solid #e1e5eb; border-bottom: 1px solid #e1e5eb; }
  .quick-questions { display: grid; grid-template-columns: 1fr; }
  .quick-questions button { width: 100%; }
  .conversation-head { min-height: 56px; padding: 8px 12px; }
  .conversation-thread { padding: 16px 12px; }
  .starter-view { padding: 28px 0; }
  .starter-actions, .metric-grid, .analysis-layout { grid-template-columns: 1fr; }
  .user-message { max-width: 88%; }
  .assistant-message { padding-left: 11px; }
  .answer-head { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
  .answer-head h2 { font-size: 16px; }
  .analysis-layout { grid-template-columns: 1fr; gap: 16px; }
  .suggestion-section { padding: 14px 0 0; border-top: 1px solid #e2e6ec; border-left: 0; }
  .contributor-list li { grid-template-columns: 22px minmax(0, 1fr); }
  .contributor-value { grid-column: 2; text-align: left; }
  .composer { padding: 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .pending-status .el-icon, .pending-lines i { animation: none; }
}
</style>
