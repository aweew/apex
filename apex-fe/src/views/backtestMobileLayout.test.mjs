import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const backtestSource = await readFile(new URL('./BacktestView.vue', import.meta.url), 'utf8')
const phoneStyles = backtestSource.slice(backtestSource.indexOf('@media (max-width: 420px)'))

test('strategy laboratory keeps its loading state independent from legacy backtest actions', () => {
  assert.match(backtestSource, /const labLoading = ref\(false\)/)
  assert.match(backtestSource, /:loading="labLoading" :disabled="labControlsDisabled" @click="onRollingEvaluate"/)
})

test('backtest defaults use a current trailing three-year range', () => {
  assert.match(backtestSource, /buildTrailingDateRange\(3\)/)
  assert.match(backtestSource, /beginDate:\s*defaultBacktestRange\.beginDate/)
  assert.match(backtestSource, /endDate:\s*defaultBacktestRange\.endDate/)
  assert.doesNotMatch(backtestSource, /endDate:\s*'2026-08-01'/)
})

test('strategy laboratory uses one-column controls and a stable mobile action target', () => {
    assert.match(backtestSource, /<span>初始资金<\/span>[\s\S]*?v-model="form\.initCash"/)
    assert.match(phoneStyles, /\.lab-controls,[\s\S]*?\.lab-costs\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);/)
  assert.match(phoneStyles, /\.lab-head > \.el-button\s*\{[\s\S]*?min-height:\s*44px;[\s\S]*?touch-action:\s*manipulation;/)
  assert.match(phoneStyles, /\.lab-control :deep\(\.el-select__wrapper\)[\s\S]*?min-height:\s*44px;/)
  assert.match(phoneStyles, /\.lab-control :deep\(\.el-input-number\)[\s\S]*?min-height:\s*44px;/)
  assert.match(phoneStyles, /\.lab-control :deep\(\.el-segmented\)[\s\S]*?min-height:\s*44px;/)
})

test('strategy results isolate wide tables and allow audit metadata to wrap', () => {
  assert.match(backtestSource, /\.lab-table-wrap\s*\{[\s\S]*?max-width:\s*100%;[\s\S]*?overflow-x:\s*auto;/)
  assert.match(backtestSource, /\.lab-run-meta span\s*\{[\s\S]*?min-width:\s*0;[\s\S]*?overflow-wrap:\s*anywhere;/)
  assert.match(backtestSource, /row\.endingPositionQuantity[\s\S]*?未平仓/)
  assert.match(backtestSource, /rollingResult\.executionModelVersion\s*\|\|\s*'未知成交模型'/)
  assert.match(backtestSource, /rollingResult\.priceAdjustment\s*\|\|\s*'未知复权口径'/)
  assert.match(backtestSource, /rollingResult\.dataBeginDate[^\n]*rollingResult\.dataEndDate/)
  assert.match(backtestSource, /rollingResult\.cost\?\.commissionRate/)
  assert.match(backtestSource, /rollingResult\.cost\?\.stampTaxRate/)
  assert.match(backtestSource, /rollingResult\.cost\?\.buySlippage/)
  assert.match(backtestSource, /rollingResult\.cost\?\.sellSlippage/)
})

test('strategy leaderboard labels paired comparison samples explicitly', () => {
  assert.match(backtestSource, /label="配对策略榜"/)
  assert.match(backtestSource, /prop="jobCount" label="配对批次"/)
  assert.match(backtestSource, /row\.strategyParameters/)
  assert.match(backtestSource, /row\.executionModelVersion[^\n]*row\.priceAdjustment/)
  assert.match(backtestSource, /row\.initCash/)
  assert.match(backtestSource, /row\.commissionRate/)
  assert.match(backtestSource, /row\.stampTaxRate/)
  assert.match(backtestSource, /row\.buySlippage/)
  assert.match(backtestSource, /row\.sellSlippage/)
  assert.match(backtestSource, /row\.comparisonConfigFingerprint\.slice\(0, 12\)/)
  assert.match(backtestSource, /class="lab-table-wrap leaderboard-table-wrap"[\s\S]*?v-if="leaderboard\.length"/)
})

test('job history and leaderboard refresh independently without stale fallback data', () => {
  assert.match(backtestSource, /let jobsLoadSequence = 0/)
  assert.match(backtestSource, /const loadSequence = \+\+jobsLoadSequence[\s\S]*?if \(loadSequence !== jobsLoadSequence\) return/)
  assert.match(backtestSource, /async function loadJobs\(\)[\s\S]*?Promise\.allSettled/)
  assert.match(backtestSource, /jobsResult\.status === 'fulfilled'[\s\S]*?: \[\]/)
  assert.match(backtestSource, /leaderboardResult\.status === 'fulfilled'[\s\S]*?: \[\]/)
})

test('experiment history has an independent loading state and local table scrolling', () => {
  assert.match(backtestSource, /const experimentLoading = ref\(false\)/)
  assert.match(backtestSource, /\.experiment-table-wrap\s*\{[\s\S]*?max-width:\s*100%;[\s\S]*?overflow-x:\s*auto;/)
})

test('experiment history switches from its desktop table to mobile records', () => {
  assert.match(backtestSource, /class="[^"]*experiment-desktop[^"]*"/)
  assert.match(backtestSource, /class="experiment-mobile-list"/)
  assert.match(phoneStyles, /\.experiment-desktop\s*\{[\s\S]*?display:\s*none;/)
  assert.match(phoneStyles, /\.experiment-mobile-list\s*\{[\s\S]*?display:\s*grid;/)
})

test('experiment history exposes a retry state when loading fails', () => {
  assert.match(backtestSource, /const experimentError = ref\(''\)/)
  assert.match(backtestSource, /v-if="experimentError && !experimentLoading"/)
  assert.match(backtestSource, /@click="loadExperiments"[^>]*>重试</)
})

test('rolling evaluation clears stale results and blocks duplicate requests', () => {
  assert.match(backtestSource, /const labControlsDisabled = computed\(\(\) => labLoading\.value \|\| experimentLoading\.value\)/)
  assert.match(backtestSource, /async function runRollingEvaluation\(strategyConfig, replayAudit\)\s*\{\s*if \(labControlsDisabled\.value\) return\s*labLoading\.value = true\s*rollingResult\.value = null/)
  assert.match(backtestSource, /:disabled="labControlsDisabled"/)
})

test('research actions clear stale results before starting a replacement request', () => {
  assert.match(backtestSource, /async function onCompare\(\)\s*\{\s*if \(loading\.value\) return\s*resetBacktestResults\(\)\s*loading\.value = true/)
  assert.match(backtestSource, /async function onSweep\(\)\s*\{\s*if \(loading\.value\) return\s*resetBacktestResults\(\)\s*loading\.value = true/)
  assert.match(backtestSource, /async function onBenchmark\(\)\s*\{\s*if \(loading\.value\) return\s*resetBacktestResults\(\)\s*loading\.value = true/)
})

test('legacy backtest actions clear mutually exclusive stale result state', () => {
  assert.match(backtestSource, /function resetBacktestResults\(\)[\s\S]*?job\.value = null[\s\S]*?compareRows\.value = \[\][\s\S]*?portfolioLegs\.value = \[\][\s\S]*?portfolioCodes\.value = \[\][\s\S]*?benchmarkRow\.value = null[\s\S]*?sweepRows\.value = \[\][\s\S]*?monthlyRows\.value = \[\][\s\S]*?stressRow\.value = null[\s\S]*?expectancy\.value = null/)
  assert.match(backtestSource, /function resetBacktestResults\(\)[\s\S]*?chart\.dispose\(\)[\s\S]*?chart = null/)
  for (const actionName of ['showDetail', 'onRun', 'onCompare', 'onPortfolio', 'onSweep', 'onBenchmark']) {
    assert.match(backtestSource, new RegExp(`async function ${actionName}\\([^)]*\\)\\s*\\{\\s*if \\(loading\\.value\\) return\\s*resetBacktestResults\\(\\)`))
  }
})

test('job details keep core results when optional analytics fail', () => {
  const detailSource = backtestSource.slice(
    backtestSource.indexOf('async function showDetail'),
    backtestSource.indexOf('async function onRun'),
  )
  const runSource = backtestSource.slice(
    backtestSource.indexOf('async function onRun'),
    backtestSource.indexOf('async function onCompare'),
  )
  const benchmarkSource = backtestSource.slice(
    backtestSource.indexOf('async function onBenchmark'),
    backtestSource.indexOf('function renderChart'),
  )
  assert.match(detailSource, /Promise\.allSettled\(\[/)
  assert.match(detailSource, /if \(detailResult\.status !== 'fulfilled'\) throw detailResult\.reason/)
  assert.match(detailSource, /monthlyResult\.status === 'fulfilled'[\s\S]*?: \[\]/)
  assert.match(detailSource, /stressResult\.status === 'fulfilled'[\s\S]*?: null/)
  assert.match(runSource, /Promise\.allSettled\(\[/)
  assert.match(runSource, /backtestStress\(res\.data\.id, 400, 20\)/)
  assert.match(runSource, /if \(detailResult\.status !== 'fulfilled'\) throw detailResult\.reason/)
  assert.match(runSource, /expectancy\.value = detail\.expectancy/)
  assert.match(runSource, /monthlyResult\.status === 'fulfilled'[\s\S]*?: \[\]/)
  assert.match(runSource, /stressResult\.status === 'fulfilled'[\s\S]*?: null/)
  assert.match(benchmarkSource, /Promise\.allSettled\(\[getBacktestDetail\(data\.job\.id\)\]\)/)
  assert.match(benchmarkSource, /const detail = detailResult\.status === 'fulfilled'[\s\S]*?: \{\}/)
  assert.match(benchmarkSource, /trades\.value = detail\.trades \|\| \[\]/)
  assert.match(benchmarkSource, /expectancy\.value = detail\.expectancy/)
  assert.doesNotMatch(benchmarkSource, /await getBacktestDetail\(data\.job\.id\)/)
})

test('cost controls expose all eight backend rate decimals through percent inputs', () => {
  const preciseCostControls = [...backtestSource.matchAll(
    /v-model="labForm\.(?:commissionPercent|stampTaxPercent|buySlippagePercent|sellSlippagePercent)"[^>]*:precision="6"/g,
  )]

  assert.equal(preciseCostControls.length, 4)
})

test('initial cash control exposes the backend two-decimal contract', () => {
  assert.match(
    backtestSource,
    /<span>初始资金<\/span>[\s\S]*?v-model="form\.initCash"[^>]*:min="0\.01"[^>]*:precision="2"/,
  )
})

test('experiment exact replay passes the complete immutable audit snapshot', () => {
  assert.match(backtestSource, /function hasCompleteReplaySnapshot\(detail\)/)
  assert.match(backtestSource, /旧实验缺少完整审计快照，无法精确复跑/)
  assert.match(backtestSource, /await runRollingEvaluation\(detail\.request\.strategyConfig, detail\.result\)/)
  assert.match(backtestSource, /result\?\.executionModelVersion/)
  assert.match(backtestSource, /result\.priceAdjustment/)
  assert.match(backtestSource, /result\.dataFingerprint/)
  assert.match(backtestSource, /async function onRollingEvaluate\(\)\s*\{\s*await runRollingEvaluation\(\)\s*\}/)
})

test('experiment comparison identifies benchmark window mode dates and cost assumptions', () => {
  assert.match(backtestSource, /experiment\.benchmarkCode/)
  assert.match(backtestSource, /experiment\.strategyParameters/)
  assert.match(backtestSource, /experiment\.windowMode === 'EXPANDING'/)
  assert.match(backtestSource, /experiment\.trainDays/)
  assert.match(backtestSource, /experiment\.testDays/)
  assert.match(backtestSource, /experiment\.stepDays/)
    assert.match(backtestSource, /experiment\.foldCount/)
    assert.match(backtestSource, /experiment\.initCash[^\n]*未知初始资金/)
  assert.match(backtestSource, /experiment\.dataBeginDate[^\n]*experiment\.dataEndDate/)
  assert.match(backtestSource, /experiment\.outSampleBeginDate[^\n]*experiment\.outSampleEndDate/)
  assert.match(backtestSource, /experiment\.commissionRate/)
  assert.match(backtestSource, /experiment\.stampTaxRate/)
  assert.match(backtestSource, /experiment\.buySlippage/)
  assert.match(backtestSource, /experiment\.sellSlippage/)
  assert.match(backtestSource, /experiment\.executionModelVersion\s*\|\|\s*'未知成交模型'/)
    assert.match(backtestSource, /experiment\.priceAdjustment\s*\|\|\s*'未知复权口径'/)
    assert.match(backtestSource, /rollingResult\.initCash/)
})
