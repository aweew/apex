import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')
const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')

test('desktop decision table uses the shared name-first stock identity', () => {
  const tableStart = dashboardSource.indexOf(':data="topBuys"')
  const tableEnd = dashboardSource.indexOf('</el-table>', tableStart)
  const decisionTable = dashboardSource.slice(tableStart, tableEnd)

  assert.match(decisionTable, /<el-table-column prop="name" label="股票"[\s\S]*?<StockIdentity :security="row" interactive compact/)
  assert.doesNotMatch(decisionTable, /label="代码"|label="名称"/)
})

test('decision rows expose current change and daily K-line thumbnail', () => {
  const tableStart = dashboardSource.indexOf(':data="topBuys"')
  const tableEnd = dashboardSource.indexOf('</el-table>', tableStart)
  const decisionTable = dashboardSource.slice(tableStart, tableEnd)

  assert.match(decisionTable, /label="今日涨跌" width="60"/)
  assert.match(decisionTable, /label="股票" :width="showDashboardKline \? 196 : 154"[\s\S]*?class="dashboard-stock-cell"[\s\S]*?class="decision-kline-inline"/)
  assert.match(decisionTable, /class="dash-table desktop-action-table"[\s\S]*?:class="\{ 'dashboard-table-no-kline': !showDashboardKline \}"/)
  assert.match(dashboardSource, /\.dashboard-stock-cell\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) 76px;[\s\S]*?align-items:\s*center;/)
  assert.match(dashboardSource, /\.dashboard-stock-cell :deep\(\.stock-identity\)\s*\{[\s\S]*?width:\s*100%;/)
  assert.match(dashboardSource, /\.dashboard-table-no-kline :deep\(\.dashboard-stock-cell\)\s*\{[\s\S]*?display:\s*block;/)
  assert.doesNotMatch(decisionTable, /label="日 K"/)
  assert.match(decisionTable, /row\.pctChg[\s\S]*fmtIndexPct\(row\.pctChg\)/)
  assert.match(decisionTable, /showDashboardKline && row\.sparkCloses\?\.length[\s\S]*label="近 20 日日 K"/)
  assert.match(dashboardSource, /class="mobile-decision-kline"/)
})

test('dashboard daily K-line thumbnails share a persisted display preference', () => {
  assert.match(dashboardSource, /from '\.\.\/utils\/displayPreferences\.js'/)
  assert.match(appSource, /显示日 K 缩略图/)
  assert.match(appSource, /@change="setShowDashboardKline"/)
  assert.match(dashboardSource, /v-if="showDashboardKline && row\.sparkCloses\?\.length"/)
})

test('observe reminder chips use a dedicated compact alert treatment', () => {
  const observeChipStyle = dashboardSource.match(/\.observe-chip\s*\{([^}]*)\}/)?.[1] || ''

  assert.match(dashboardSource, /<StockIdentity class="observe-chip__identity" :security="item" compact \/>/)
  assert.match(dashboardSource, /class="observe-chip__status-dot" aria-hidden="true"/)
  assert.match(dashboardSource, /\.observe-chip :deep\(\.observe-chip__identity\)\s*\{[\s\S]*?width:\s*auto;/)
  assert.match(dashboardSource, /\.observe-chip\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) 72px;/)
  assert.match(dashboardSource, /\.observe-chip\s*\{[\s\S]*?min-width:\s*250px;[\s\S]*?max-width:\s*360px;/)
  assert.match(dashboardSource, /class="observe-chip__quote" aria-label="今日行情"/)
  assert.match(dashboardSource, /class="observe-chip__kline"/)
  assert.match(dashboardSource, /class="observe-chip__body" :class="\{ 'without-kline': !item\.sparkCloses\?\.length \}"/)
  assert.match(dashboardSource, /\.observe-chip__body\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) minmax\(120px, 150px\);/)
  assert.match(dashboardSource, /\.observe-chip__body\.without-kline\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);/)
  assert.match(dashboardSource, /\.observe-chip__kline\s*\{[\s\S]*?max-width:\s*none;[\s\S]*?margin-top:\s*0;/)
  assert.match(dashboardSource, /@media \(max-width:\s*560px\)[\s\S]*?\.observe-chip__body\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) minmax\(100px, 132px\);/)
  assert.match(dashboardSource, /\.observe-chip\s*\{[\s\S]*?border-radius:\s*8px;/)
  assert.match(dashboardSource, /\.observe-chip em\s*\{[\s\S]*?border-left:\s*1px solid/)
  assert.match(dashboardSource, /\.observe-chip em\s*\{[\s\S]*?width:\s*72px;[\s\S]*?justify-content:\s*flex-start;/)
  assert.match(dashboardSource, /\.observe-chip em\s*\{[\s\S]*?border-radius:\s*4px;/)
  assert.match(dashboardSource, /@media \(max-width:\s*560px\)[\s\S]*?\.observe-chips\s*\{[\s\S]*?grid-template-columns:\s*1fr;/)
  assert.doesNotMatch(observeChipStyle, /border-radius:\s*999px;/)
})

test('desktop decision table reserves one complete flexible cue column for linkage and mainline tags', () => {
  const tableStart = dashboardSource.indexOf(':data="topBuys"')
  const tableEnd = dashboardSource.indexOf('</el-table>', tableStart)
  const decisionTable = dashboardSource.slice(tableStart, tableEnd)

  assert.match(decisionTable, /label="联动" min-width="90" class-name="action-cues-col"/)
  assert.match(decisionTable, /class="action-cues"[\s\S]*?row\.linkHint[\s\S]*?row\.mainlineMatch/)
  assert.doesNotMatch(decisionTable, /<el-table-column label="主线"/)
  assert.match(dashboardSource, /\.action-cues :deep\(\.link-hint-tag\)\s*\{[\s\S]*?white-space:\s*nowrap;/)
})

test('desktop action panels stay inside the dashboard container', () => {
  assert.match(
    dashboardSource,
    /\.two-col\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);[^}]*align-items:\s*start;/,
  )
  assert.match(dashboardSource, /@media \(max-width:\s*1100px\)[\s\S]*?\.two-col\s*\{\s*grid-template-columns:\s*1fr;/)
  assert.match(
    dashboardSource,
    /\.panel\.action-panel\s*\{[^}]*align-self:\s*start;[^}]*min-width:\s*0;/,
  )
})

test('action panels use compact hierarchy and align their table starts', () => {
  assert.match(dashboardSource, /class="panel action-panel decision-action-panel enter delay-1"/)
  assert.match(dashboardSource, /class="panel action-panel holding-action-panel enter delay-2"/)
  assert.match(dashboardSource, /全部决策 <span aria-hidden="true">→<\/span>/)
  assert.match(dashboardSource, /查看组合 <span aria-hidden="true">→<\/span>/)
  assert.match(
    dashboardSource,
    /\.panel-meta\s*\{[^}]*min-height:\s*94px;[^}]*padding:\s*10px 0 12px;/,
  )
  assert.match(
    dashboardSource,
    /\.panel-meta \.val-dist-placeholder\s*\{[^}]*display:\s*none;/,
  )
  assert.match(
    dashboardSource,
    /\.panel-meta\s*\{[^}]*min-height:\s*94px;[^}]*box-sizing:\s*border-box;/,
  )
  assert.match(dashboardSource, /\.panel-body\s*\{[^}]*flex:\s*0 0 auto;/)
})

test('action summaries expose decision counts as aligned visual metrics', () => {
  assert.match(dashboardSource, /class="meta-stat is-buy"><em>买入<\/em><b>/)
  assert.match(dashboardSource, /class="meta-stat is-sell"><em>卖出<\/em><b>/)
  assert.match(dashboardSource, /class="meta-stat is-executable"><em>可执行<\/em><b>/)
  assert.match(dashboardSource, /class="meta-stat is-sell"><em>卖点<\/em><b>/)
  assert.match(
    dashboardSource,
    /\.meta-date\s*\{[^}]*border-right:\s*1px solid/,
  )
  assert.match(
    dashboardSource,
    /\.meta-stat\s*\{[^}]*display:\s*inline-flex;[^}]*font-variant-numeric:\s*tabular-nums;/,
  )
})

test('desktop action tables use a flat compact table surface inside each panel', () => {
  assert.match(
    dashboardSource,
    /\.action-panel \.dash-table\s*\{[^}]*border-top:\s*1px solid[^}]*border-bottom:\s*1px solid[^}]*border-radius:\s*0;/,
  )
  assert.match(
    dashboardSource,
    /\.action-panel \.dash-table :deep\(th\.el-table__cell\)\s*\{[^}]*height:\s*36px;/,
  )
  assert.match(
    dashboardSource,
    /\.action-panel \.dash-table :deep\(td\.el-table__cell\)\s*\{[^}]*height:\s*50px;/,
  )
})

test('desktop action tables keep adjacent fields together and fill their panels', () => {
  const buyTableStart = dashboardSource.indexOf(':data="topBuys"')
  const buyTableEnd = dashboardSource.indexOf('</el-table>', buyTableStart)
  const buyTable = dashboardSource.slice(buyTableStart, buyTableEnd)
  const sellTableStart = dashboardSource.indexOf(':data="topSells"')
  const sellTableEnd = dashboardSource.indexOf('</el-table>', sellTableStart)
  const sellTable = dashboardSource.slice(sellTableStart, sellTableEnd)

  assert.match(buyTable, /prop="name" label="股票" :width="showDashboardKline \? 196 : 154"/)
  assert.match(buyTable, /label="联动" min-width="90" class-name="action-cues-col"/)
  assert.match(sellTable, /prop="name" label="股票" :width="showDashboardKline \? 196 : 154"/)
  assert.match(sellTable, /prop="exitRule" label="触发" min-width="160"/)
})

test('mobile sell cards keep strategy beside the stock identity instead of a separate row', () => {
  const sellListStart = dashboardSource.indexOf('aria-label="持仓卖出行动"')
  const sellListEnd = dashboardSource.indexOf('</div>', sellListStart)
  const sellList = dashboardSource.slice(sellListStart, sellListEnd)

  assert.match(sellList, /class="mobile-stock-with-strategy"/)
  assert.match(sellList, /class="mobile-strategy-badge"/)
  assert.doesNotMatch(sellList, /class="mobile-action-details sell-details"/)
})

test('mobile sell trigger label uses a stable column and aligns with the first content line', () => {
  assert.match(
    dashboardSource,
    /\.mobile-exit-rule\s*\{[^}]*grid-template-columns:\s*24px minmax\(0, 1fr\);[^}]*align-items:\s*baseline;/,
  )
})
