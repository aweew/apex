import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { ref, computed } from 'vue'
import { decisionJobChanged, decisionRunFeedback } from '../utils/decisionRunFeedback.js'
import { isActiveSyncJob } from './syncPolling.mjs'

const source = await readFile(new URL('./DecisionView.vue', import.meta.url), 'utf8')
const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1]
  .replace(/^import[\s\S]*?from ['"][^'"]+['"]\n/gm, '')

function page(role = 'ADMIN', overrides = {}) {
  let latestJob = null
  let todayError = false
  let reads = 0
  let starts = 0
  let unmount
  const messages = []
  const result = {
    generated: true, actionDate: '2026-09-23', runMode: 'LIVE',
    marketBriefing: { dataLevel: 'GREEN' },
    buys: [{ action: 'BUY', executableHint: false, riskFlags: ['市场广度不足，禁止新开仓'] }],
    sells: [], holds: [],
  }
  const context = vm.createContext({
    ref, computed, decisionJobChanged, decisionRunFeedback, isActiveSyncJob,
    getCurrentUser: () => ({ role }), useRouter: () => ({ push() {} }),
    localStorage: { getItem: () => null }, useSessionViewState() {},
    onMounted() {}, onBeforeUnmount(callback) { unmount = callback },
    createSerialPoller: () => ({ start() {}, stop() {} }),
    ElMessage: { success: (message) => messages.push(message), error: (message) => messages.push(message) },
    fetchSyncOverview: async () => ({ data: { tasks: [{ taskType: 'DECISION', latestJob }] } }),
    startSyncJob: async () => { starts++; return { data: { id: 2, status: 'PENDING' } } },
    fetchDecisionToday: async () => { reads++; if (todayError) throw new Error('结果连接中断'); return { data: result } },
    fetchDecisionPlaybook: async () => ({ data: {} }),
    fetchDecisionHistory: async () => ({ data: [] }),
    fetchDecisionAttribution: async () => ({ data: null }),
    fetchDecisionAdvice: async () => ({ data: null }),
    fetchDecisionBuyAiSummary: async () => ({ data: null }),
    publishDataFreshness() {}, staleDataTime: () => '',
    isCurrentLiveDecision: () => true, chinaMarketDate: () => '2026-09-23',
    isPaperBuyAllowed: (row) => row.executableHint === true,
    normalizeHotThemes: () => [],
    ...overrides,
  })
  vm.runInContext(script, context)
  return {
    call: (expression) => vm.runInContext(expression, context),
    setJob(job) { latestJob = job },
    setError(value) { todayError = value },
    reads: () => reads, starts: () => starts, unmount: () => unmount(), messages,
  }
}

test('decision page submits once and leaves active task visible', async () => {
  const view = page()
  await view.call('onGenerateDecision()')
  await view.call('onGenerateDecision()')
  assert.equal(view.starts(), 1)
  assert.equal(view.call('decisionRunning.value'), true)
  assert.match(source, /v-if="isAdmin"[^>]+@click="onGenerateDecision"/)
})

test('ordinary user cannot start shared generation through the handler', async () => {
  const view = page('USER')
  await view.call('onGenerateDecision()')
  assert.equal(view.starts(), 0)
})

test('completion refreshes results and reveals blocked candidates', async () => {
  const view = page()
  view.setJob({ id: 1, status: 'RUNNING' })
  await view.call('refreshDecisionTask()')
  view.setJob({ id: 1, status: 'SUCCESS' })
  await view.call('refreshDecisionTask()')
  assert.equal(view.reads(), 1)
  assert.equal(view.call('data.value.buys.length'), 1)
  assert.equal(view.call('trackingOpen.value'), true)
  await view.call('refreshDecisionTask()')
  assert.equal(view.reads(), 1)
})

test('failed result refresh retries on next poll and shows a persistent error', async () => {
  const view = page()
  view.setJob({ id: 1, status: 'RUNNING' })
  await view.call('refreshDecisionTask()')
  view.setJob({ id: 1, status: 'SUCCESS' })
  view.setError(true)
  await view.call('refreshDecisionTask()')
  assert.match(view.call('resultLoadError.value'), /结果连接中断/)
  view.setError(false)
  await view.call('refreshDecisionTask()')
  assert.equal(view.reads(), 2)
  assert.equal(view.call('resultLoadError.value'), '')
})

test('unmount prevents an outstanding task status response from updating the page', async () => {
  let resolveOverview
  const view = page('ADMIN', { fetchSyncOverview: () => new Promise((resolve) => { resolveOverview = resolve }) })
  const pending = view.call('refreshDecisionTask()')
  view.unmount()
  resolveOverview({ data: { tasks: [{ taskType: 'DECISION', latestJob: { id: 1, status: 'RUNNING' } }] } })
  assert.equal(await pending, false)
  assert.equal(view.call('decisionJob.value'), null)
})

test('an older overview request cannot overwrite a newly submitted task', async () => {
  let resolveOverview
  const view = page('ADMIN', { fetchSyncOverview: () => new Promise((resolve) => { resolveOverview = resolve }) })
  const pending = view.call('refreshDecisionTask()')
  await view.call('onGenerateDecision()')
  resolveOverview({ data: { tasks: [{ taskType: 'DECISION', latestJob: { id: 1, status: 'SUCCESS' } }] } })
  await pending
  assert.equal(view.call('decisionJob.value.id'), 2)
  assert.equal(view.call('decisionRunning.value'), true)
})
