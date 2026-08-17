import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const portfolioSource = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')
const holdingSource = await readFile(new URL('./HoldingView.vue', import.meta.url), 'utf8')

test('mobile portfolio controls share one compact list toolbar', () => {
  assert.match(portfolioSource, /class="mobile-list-toolbar"/)
  assert.match(portfolioSource, /class="mobile-create-button"/)
  assert.doesNotMatch(portfolioSource, /class="mobile-header-actions"/)
  assert.match(portfolioSource, /\.portfolio-page \.portfolio-header\s*\{\s*display:\s*none;/)
})

test('mobile portfolio rows keep today performance beside the portfolio name', () => {
  assert.match(portfolioSource, /v-if="isMobileViewport" class="pf-mobile-pnl"/)
  assert.match(portfolioSource, /v-if="!isMobileViewport" class="pf-summary"[\s\S]*?class="pf-pnl"/)
})

test('mobile portfolio rows use distinct card boundaries for scanning', () => {
  assert.match(portfolioSource, /\.pf-card\s*\{\s*margin: 10px 0 0;[\s\S]*?border: 1px solid rgba\(0, 0, 0, 0\.1\);/)
  assert.match(portfolioSource, /\.mobile-list-toolbar \+ \.pf-card\s*\{\s*margin-top: 12px;/)
})

test('opening mobile detail redraws charts after their containers mount', () => {
  assert.match(
    portfolioSource,
    /watch\(mobileDetailOpen, async \(open\) => \{[\s\S]*?await nextTick\(\)[\s\S]*?renderPies\(\)[\s\S]*?renderDailyChart\(\)/,
  )
  assert.match(portfolioSource, /themeChart\.getDom\(\) !== themePieRef\.value/)
  assert.match(portfolioSource, /chart\.getDom\(\) !== chartRef\.value/)
})

test('mobile portfolio detail starts at the document top after selection', () => {
  assert.match(
    portfolioSource,
    /await nextTick\(\)\s*window\.scrollTo\(\{ top: 0, behavior: 'auto' \}\)\s*requestAnimationFrame\(\(\) => window\.scrollTo\(\{ top: 0, behavior: 'auto' \}\)\)/,
  )
})

test('portfolio summary keeps only action-oriented metrics', () => {
  const detailTemplate = portfolioSource.slice(
    portfolioSource.indexOf('class="stat-cards stat-cards--portfolio"'),
    portfolioSource.indexOf('<section v-if="rows.length && brief"'),
  )
  assert.match(detailTemplate, /持仓只数/)
  assert.match(detailTemplate, /今日盈亏/)
  assert.match(detailTemplate, /持仓盈亏/)
  assert.doesNotMatch(detailTemplate, /持仓市值|现金|总权益/)
})

test('holding tables keep stock links blue and avoid fixed columns on mobile', () => {
  assert.match(portfolioSource, /:fixed="sharingCapture \|\| isMobileViewport \? false : 'left'"/)
  assert.match(holdingSource, /:fixed="mobileRowActions \? false : 'left'"/)
  const mobileActionsStart = holdingSource.indexOf('v-if="mobileRowActions"')
  const mobileActionsEnd = holdingSource.indexOf('</el-table-column>', mobileActionsStart)
  const mobileActionsColumn = holdingSource.slice(mobileActionsStart, mobileActionsEnd)
  assert.doesNotMatch(mobileActionsColumn, /fixed="right"/)
  assert.match(portfolioSource, /\.security-link\s*\{[\s\S]*?color: var\(--el-color-primary\);/)
  assert.match(holdingSource, /\.security-link\s*\{[\s\S]*?color: var\(--el-color-primary\);/)
})

test('holding quantity headers reserve room for their sort indicator', () => {
  assert.match(portfolioSource, /prop="quantity"[\s\S]*?label="持仓数量"[\s\S]*?:width="shareCol\.qty"/)
  assert.match(portfolioSource, /qty: 104/)
  assert.match(holdingSource, /prop="quantity" label="持仓数量" width="104"/)
})

test('portfolio list holding summaries omit market badges', () => {
  const portfolioListTemplate = portfolioSource.slice(portfolioSource.indexOf('class="pf-card"'), portfolioSource.indexOf('class="side-rail"'))
  assert.doesNotMatch(portfolioListTemplate, /<SecurityMarketBadge :security="h"/)
})

test('portfolio cards support long-press drag ordering with persistent sort numbers', () => {
  assert.match(portfolioSource, /function onSortHandlePointerDown\(row, event\)/)
  assert.match(portfolioSource, /window\.setTimeout\(\(\) => startPortfolioDrag\(row\.id\), 350\)/)
  assert.match(portfolioSource, /async function persistPortfolioOrder\(fromId, toId, placeAfter\)/)
  assert.match(portfolioSource, /await sortPortfolios\(list\.value\.map\(\(row\) => row\.id\)\)/)
  assert.match(portfolioSource, /class="pf-sort-handle"/)
  assert.match(portfolioSource, /:data-portfolio-id="row\.id"/)
})

test('desktop portfolio actions use one grouped toolbar', () => {
  assert.match(portfolioSource, /class="portfolio-desktop-toolbar"/)
  assert.match(portfolioSource, /function handleDesktopToolbarAction\(command\)/)
  assert.doesNotMatch(portfolioSource, /class="actions desktop-header-actions"/)
  assert.doesNotMatch(portfolioSource, /class="actions detail-actions"/)
})

test('desktop portfolio cards keep drag, selection, name, and menu in stable columns', () => {
  assert.match(portfolioSource, /<article[\s\S]*?class="pf-card"[\s\S]*?role="button"/)
  assert.match(
    portfolioSource,
    /\.pf-top\s*\{[\s\S]*?grid-template-columns:\s*32px 18px minmax\(0, 1fr\) 32px;/,
  )
  assert.match(portfolioSource, /class="pf-card-menu-trigger"/)
  assert.match(portfolioSource, /@keydown\.enter\.self\.prevent="onPortfolioCardClick\(row\)"/)
  assert.match(portfolioSource, /@keydown\.space\.self\.prevent="onPortfolioCardClick\(row\)"/)
  assert.match(portfolioSource, /\.pf-name strong\s*\{[\s\S]*?text-overflow:\s*ellipsis;[\s\S]*?white-space:\s*nowrap;/)
  assert.doesNotMatch(portfolioSource, /\.pf-top\s*\{[\s\S]*?padding-right:\s*72px;/)
})
