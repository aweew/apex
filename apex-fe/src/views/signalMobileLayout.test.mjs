import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const [signalSource, termTipSource] = await Promise.all([
  readFile(new URL('./SignalView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../components/TermTip.vue', import.meta.url), 'utf8'),
])
const signalApiSource = await readFile(new URL('../api/signal.js', import.meta.url), 'utf8')
const [signalDetailSource, routerSource] = await Promise.all([
  readFile(new URL('./SignalDetailView.vue', import.meta.url), 'utf8'),
  readFile(new URL('../router/index.js', import.meta.url), 'utf8'),
])

test('strategy signal workspace tabs follow the module header', () => {
  assert.match(
    signalSource,
    /<header class="header signal-header">[\s\S]*?<\/header>\s*<DecisionWorkspaceTabs \/>/,
  )
})

test('strategy signal page keeps loading feedback local to the affected content', () => {
  assert.doesNotMatch(signalSource, /class="page signal-page"\s+v-loading=/)
  assert.match(signalSource, /class="signal-results"\s+v-loading="listLoading \|\| ordering"/)
  assert.match(signalSource, /class="signal-metrics"\s+v-loading="overviewLoading"/)
})

test('mobile filters use touch-friendly segmented controls and progressive disclosure', () => {
  assert.match(signalSource, /isMobileViewport = computed\(\(\) => viewportWidth\.value <= 900\)/)
  assert.match(signalSource, /class="signal-segmented signal-direction-filter"/)
  assert.match(signalSource, /class="signal-segmented signal-strategy-filter"/)
  assert.match(signalSource, /class="advanced-filter-toggle"/)
  assert.match(signalSource, /class="advanced-filters"/)
  assert.match(signalSource, /class="filter-result-summary"/)
})

test('filter changes reload only the signal list', () => {
  assert.match(signalSource, /async function loadSignalList\([\s\S]*?latestSignals\(/)
  assert.match(signalSource, /function updateSideFilter\([\s\S]*?loadSignalList\(\)/)
  assert.match(signalSource, /function resetFilters\([\s\S]*?loadSignalList\(\)/)
  assert.doesNotMatch(signalSource, /@change="load"/)
})

test('mobile evaluation sections avoid desktop tables', () => {
  assert.match(signalSource, /class="forward-mobile-list"/)
  assert.match(signalSource, /class="confluence-mobile-list"/)
  assert.match(signalSource, /v-else-if="forward\?\.scoreBuckets\?\.length"/)
  assert.match(signalSource, /v-else-if="confluence\?\.items\?\.length"/)
})

test('signal list reuses market badges and balances mobile metadata', () => {
  assert.match(signalSource, /class="signal-stock">[\s\S]*?<StockIdentity :security="row" interactive/)
  assert.match(signalSource, /<el-table-column prop="name" label="股票" width="144">[\s\S]*?<StockIdentity :security="row" interactive compact/)
  assert.match(signalSource, /\.signal-mobile-meta\s*\{\s*display: grid;\s*grid-template-columns: repeat\(4, minmax\(0, 1fr\)\);/)
  assert.match(signalSource, /\.signal-mobile-meta \.strategy-badge,[\s\S]*?justify-self: center;/)
  assert.match(signalSource, /\.signal-mobile-meta \.signal-score\s*\{\s*justify-self: end;/)
})

test('confluence stock identities open stock details on mobile and desktop', () => {
  const confluenceTemplate = signalSource.slice(
    signalSource.indexOf('<el-collapse-item name="cf">'),
    signalSource.indexOf('</el-collapse-item>', signalSource.indexOf('<el-collapse-item name="cf">')),
  )

  assert.match(
    confluenceTemplate,
    /<StockIdentity\s+:security="item"\s+:interactive="Boolean\(item\.code\)"\s+compact\s+@select="router\.push\(`\/stock\/\$\{item\.code\}`\)"/,
  )
  assert.match(
    confluenceTemplate,
    /<StockIdentity\s+:security="row"\s+:interactive="Boolean\(row\.code\)"\s+compact\s+@select="router\.push\(`\/stock\/\$\{row\.code\}`\)"/,
  )
})

test('touch term underlines stay attached to their labels', () => {
  assert.match(
    termTipSource,
    /@media \(max-width: 820px\), \(hover: none\)\s*\{[\s\S]*?\.term-tip\s*\{[^}]*display:\s*inline-block;[^}]*line-height:\s*1\.2;[^}]*vertical-align:\s*baseline;/,
  )
})

test('signal center provides contextual usage guidance and terminology', () => {
  assert.match(signalSource, /class="signal-help-button"[\s\S]*?aria-label="查看信号中心使用说明"/)
  assert.match(signalSource, /<el-drawer[\s\S]*?class="signal-help-drawer"[\s\S]*?信号中心使用说明[\s\S]*?append-to-body/)
  assert.match(signalSource, /先看数据日期[\s\S]*?再看市场阶段[\s\S]*?查看主要信号[\s\S]*?核对证据/)
  assert.match(signalSource, /生命周期[\s\S]*?强度[\s\S]*?置信度[\s\S]*?历史概率[\s\S]*?风险分/)
  assert.match(signalSource, /行为信号只描述市场发生了什么，不等同于买卖建议/)
})

test('signal center separates market behavior from legacy strategy signals', () => {
  assert.match(signalSource, /class="signal-segmented signal-mode-switch"[\s\S]*?市场行为[\s\S]*?策略信号/)
  assert.match(signalSource, /behaviorOverview[\s\S]*?bullishCount[\s\S]*?bearishCount[\s\S]*?riskCount/)
  assert.match(signalSource, /v-if="workspaceMode === 'behavior' && canCalculateBehavior"[\s\S]*?v-else-if="workspaceMode === 'strategy'"/)
  assert.match(signalSource, /row\.lifecycleState[\s\S]*?row\.strength[\s\S]*?row\.confidence[\s\S]*?row\.riskScore/)
  assert.match(signalApiSource, /export function signalCenterOverview/)
  assert.match(signalApiSource, /export function signalCenterRankings/)
  assert.match(signalApiSource, /export function runSignalCenterCalculation/)
})

test('market behavior detail explains evidence and lifecycle at the stock route', () => {
  assert.match(routerSource, /path: '\/signals\/:code'[\s\S]*?SignalDetailView/)
  assert.match(signalDetailSource, /市场阶段[\s\S]*?主要行为[\s\S]*?风险行为[\s\S]*?生命周期时间轴/)
  assert.match(signalDetailSource, /强度[\s\S]*?置信度[\s\S]*?历史概率[\s\S]*?风险分/)
  assert.match(signalDetailSource, /价格与量能证据[\s\S]*?resistancePrice[\s\S]*?supportPrice[\s\S]*?atr14[\s\S]*?volumeRatio/)
})
