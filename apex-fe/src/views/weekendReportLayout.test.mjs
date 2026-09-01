import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const viewSource = await readFile(new URL('./WeekendReportView.vue', import.meta.url), 'utf8')
const apiSource = await readFile(new URL('../api/weekendReport.js', import.meta.url), 'utf8')
const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('weekend report exposes the stable API contract', () => {
  assert.match(apiSource, /http\.get\('\/api\/weekend-report'/)
  assert.match(apiSource, /http\.post\('\/api\/weekend-report\/refresh'/)
  assert.match(routerSource, /path: '\/weekend-report'/)
})

test('weekend report presents six evidence-bearing research sections', () => {
  for (const section of ['上周走势', '周五收盘', '周末消息', '机构与大 V 观点', '下周交易主线', '市场剧本与风险']) {
    assert.match(viewSource, new RegExp(section))
  }
  assert.match(viewSource, /report\.coreView \|\| report\.coreOpinion/)
  assert.match(viewSource, /report\.maxRisk \|\| report\.risk/)
  assert.match(viewSource, /item\.weeklyReturn/)
  assert.match(viewSource, /fridaySnapshot\.stance/)
  assert.match(viewSource, /item\.subjectName/)
  assert.match(viewSource, /item\.relatedName \|\| item\.topic \|\| item\.relatedCode/)
  assert.match(viewSource, /item\.title/)
  assert.match(viewSource, /来源索引 \{\{ item\.source \}\}#\{\{ item\.externalId \}\}/)
  assert.match(viewSource, /item\.scenario/)
  assert.match(viewSource, /item\.source/)
  assert.match(viewSource, /target="_blank" rel="noreferrer"/)
  assert.match(viewSource, /item\.catalyst/)
  assert.match(viewSource, /item\.confirm/)
  assert.match(viewSource, /item\.invalid/)
})

test('weekend report keeps long evidence and controls safe on mobile', () => {
  assert.match(viewSource, /overflow-wrap:\s*anywhere/)
  assert.match(viewSource, /@media \(max-width: 760px\)/)
  assert.match(viewSource, /@media \(max-width: 520px\)/)
  assert.match(viewSource, /grid-template-columns:\s*minmax\(0, 1fr\)/)
})

test('weekend report is hidden outside the Sunday night to Monday open window', () => {
  assert.match(viewSource, /isWeekendReportVisible/)
  assert.match(viewSource, /v-if="reportWindowOpen && report"/)
  assert.match(viewSource, /router\.replace\('\/dashboard'\)/)
  assert.match(dashboardSource, /v-if="weekendReportVisible"/)
  assert.match(appSource, /item\.to !== '\/weekend-report' \|\| isWeekendReportVisible/)
})

test('dashboard and command search expose weekend report without adding main navigation', () => {
  assert.match(appSource, /to: '\/weekend-report', label: '周末研报'/)
  assert.match(appSource, /\['\/weekend-report', '周末研报'\]/)
  assert.match(dashboardSource, /fetchWeekendMarketReport/)
  assert.match(dashboardSource, /router\.push\('\/weekend-report'\)/)
  assert.match(dashboardSource, /查看周末研报/)
})
