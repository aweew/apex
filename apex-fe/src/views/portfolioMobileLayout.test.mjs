import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const portfolioSource = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')
const holdingSource = await readFile(new URL('./HoldingView.vue', import.meta.url), 'utf8')

test('mobile portfolio controls share one compact list toolbar', () => {
  assert.match(portfolioSource, /class="mobile-list-toolbar"/)
  assert.match(portfolioSource, /class="mobile-create-button"/)
  assert.match(portfolioSource, /class="mobile-sort-button"/)
  assert.match(portfolioSource, /grid-template-columns:\s*minmax\(72px, 1fr\) auto auto auto 44px;/)
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
  assert.match(holdingSource, /:fixed="mobileRowActions \? false : 'right'"/)
  assert.match(portfolioSource, /:fixed="isMobileViewport \? false : 'right'"/)
  assert.match(portfolioSource, /<StockIdentity :security="row" interactive compact/)
  assert.match(holdingSource, /<StockIdentity :security="row" interactive compact/)
})

test('holding tables keep all actions inside one compact overflow menu', () => {
  assert.match(holdingSource, /width="52"[\s\S]*?:fixed="mobileRowActions \? false : 'right'"/)
  assert.match(portfolioSource, /width="52"[\s\S]*?:fixed="isMobileViewport \? false : 'right'"/)
  assert.match(holdingSource, /class="row-actions-trigger"/)
  assert.match(portfolioSource, /class="portfolio-row-actions-trigger"/)
  assert.match(holdingSource, /class="row-actions-trigger"[^>]*@click\.stop/)
  assert.match(portfolioSource, /class="portfolio-row-actions-trigger"[^>]*@click\.stop/)
  assert.doesNotMatch(holdingSource, /class="row-inline-actions"/)
  assert.doesNotMatch(portfolioSource, /class="portfolio-row-inline-actions"/)
  assert.match(holdingSource, /width: 28px;/)
  assert.match(portfolioSource, /width: 28px;/)
  assert.doesNotMatch(holdingSource, /box-shadow:\s*10px 0 18px -18px/)
  assert.doesNotMatch(portfolioSource, /box-shadow:\s*10px 0 18px -18px/)
  assert.match(holdingSource, /border-right:\s*1px solid var\(--line\)/)
  assert.match(portfolioSource, /border-right:\s*1px solid var\(--line\)/)
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

test('mobile portfolio ordering uses direct up and down controls while desktop keeps drag sorting', () => {
  assert.match(portfolioSource, /class="mobile-sort-button"/)
  assert.match(portfolioSource, /mobileSortMode \? '完成' : '排序'/)
  assert.match(portfolioSource, /function movePortfolio\(row, direction\)/)
  assert.match(portfolioSource, /class="pf-mobile-sort-controls"/)
  assert.match(portfolioSource, /aria-label="上移组合"/)
  assert.match(portfolioSource, /aria-label="下移组合"/)
  assert.match(portfolioSource, /grid-template-columns:\s*76px minmax\(48px, 1fr\) auto 18px;/)
  assert.match(portfolioSource, /width: 36px;/)
  assert.match(portfolioSource, /height: 44px;/)
  assert.match(portfolioSource, /v-if="!isMobileViewport"[\s\S]*?class="pf-sort-handle"/)
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

test('desktop portfolio cards keep holdings in one compact summary band', () => {
  const portfolioListTemplate = portfolioSource.slice(
    portfolioSource.indexOf('class="pf-card"'),
    portfolioSource.indexOf('class="side-rail"'),
  )
  assert.match(portfolioListTemplate, /row\.topHoldings\.slice\(0, 2\)/)
  assert.match(
    portfolioSource,
    /\.pf-tops\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/,
  )
  assert.match(
    portfolioSource,
    /\.pf-top-chip\s*\{[\s\S]*?justify-content:\s*space-between;[\s\S]*?border-right:\s*1px solid var\(--line\);/,
  )
})
