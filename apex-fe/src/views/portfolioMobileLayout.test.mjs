import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const portfolioSource = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')
const holdingSource = await readFile(new URL('./HoldingView.vue', import.meta.url), 'utf8')

test('mobile portfolio controls share one compact list toolbar', () => {
  assert.match(portfolioSource, /class="mobile-list-toolbar"/)
  assert.match(portfolioSource, /class="mobile-create-button"/)
  assert.match(portfolioSource, /class="mobile-sort-button"/)
  assert.match(portfolioSource, /grid-template-columns:\s*minmax\(72px, 1fr\) auto auto 44px;/)
  assert.match(portfolioSource, /\.mobile-list-toolbar\.can-sort\s*\{[\s\S]*?grid-template-columns:\s*minmax\(72px, 1fr\) auto auto auto 44px;/)
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
  assert.match(portfolioSource, /intradayChart\.getDom\(\) !== intradayChartRef\.value/)
})

test('portfolio detail exposes a responsive intraday return curve with five-minute polling', () => {
  assert.match(portfolioSource, /class="intraday-panel"/)
  assert.match(portfolioSource, /盘中收益/)
  assert.match(portfolioSource, /盘中最高/)
  assert.match(portfolioSource, /盘中最低/)
  assert.match(portfolioSource, /日内振幅/)
  assert.match(portfolioSource, /setInterval\([\s\S]*?5 \* 60 \* 1000/)
  assert.match(portfolioSource, /@media \(max-width: 820px\) \{[\s\S]*?\.intraday-summary\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
})

test('portfolio theme pie keeps theme names beside the chart instead of a separate row', () => {
  assert.match(portfolioSource, /const pieOpt = \(dist, colors, options = \{\}\) =>/)
  assert.match(
    portfolioSource,
    /legend:\s*\{[\s\S]*?show:\s*showLegend[\s\S]*?orient:\s*'vertical'[\s\S]*?left:\s*'55%'/,
  )
  assert.match(portfolioSource, /center:\s*showLegend \? \['27%', '50%'\] : \['50%', '50%'\]/)
  assert.match(portfolioSource, /data:\s*dist\.map\(\(x, i\) => \(\{[\s\S]*?name:\s*x\.name/)
  assert.match(portfolioSource, /pieOpt\(pieData, colors, \{ showLegend: true \}\)/)

  const themePanelTemplate = portfolioSource.slice(
    portfolioSource.indexOf('<section v-if="rows.length" class="theme-panel">'),
    portfolioSource.indexOf('<section v-if="rows.length" class="holding-layout">'),
  )
  assert.match(themePanelTemplate, /class="pie-wrap pie-wrap--theme"/)
  assert.doesNotMatch(themePanelTemplate, /class="theme-bars"/)
})

test('mobile portfolio detail starts at the document top after selection', () => {
  assert.match(
    portfolioSource,
    /await nextTick\(\)\s*window\.scrollTo\(\{ top: 0, behavior: 'auto' \}\)\s*requestAnimationFrame\(\(\) => window\.scrollTo\(\{ top: 0, behavior: 'auto' \}\)\)/,
  )
})

test('mobile portfolio detail exposes buy and per-holding actions in the first viewport', () => {
  assert.match(portfolioSource, /class="mobile-trade-button"[^>]*@click="openCreate"/)
  assert.match(portfolioSource, /class="portfolio-mobile-row-actions"/)
  assert.match(portfolioSource, /v-if="isMobileViewport && !sharingCapture"[\s\S]*?@command="handleHoldingAction\(\$event, row\)"/)
  assert.match(portfolioSource, /v-if="!sharingCapture && !isMobileViewport"[\s\S]*?label="操作"/)
  assert.match(portfolioSource, /@media \(max-width: 820px\) \{[\s\S]*?\.portfolio-stock-cell\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\) 44px;/)
  assert.match(portfolioSource, /@media \(max-width: 820px\) \{[\s\S]*?\.portfolio-row-actions-trigger\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/)
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
  assert.match(portfolioSource, /v-if="!sharingCapture && !isMobileViewport"[\s\S]*?fixed="right"/)
  assert.match(portfolioSource, /<StockIdentity[\s\S]*?:security="row"[\s\S]*?interactive[\s\S]*?compact/)
  assert.match(holdingSource, /<StockIdentity :security="row" interactive compact/)
})

test('mobile holding tables use one outer horizontal scroll layer', () => {
  for (const source of [portfolioSource, holdingSource]) {
    assert.match(source, /class="holding-table-scroll"[\s\S]*?<el-table/)
    assert.match(source, /\.holding-table-scroll\s*\{[\s\S]*?overflow-x:\s*auto;[\s\S]*?touch-action:\s*pan-x pan-y;/)
    assert.match(source, /\.holding-table :deep\(\.el-scrollbar__wrap\)\s*\{[\s\S]*?overflow-x:\s*hidden;/)
  }
})

test('desktop portfolio keeps native document scrolling', () => {
  assert.doesNotMatch(
    portfolioSource,
    /@media \(min-width: 961px\) \{[\s\S]*?\.portfolio-page\s*\{[\s\S]*?overflow:\s*hidden;/,
  )
  assert.doesNotMatch(
    portfolioSource,
    /@media \(min-width: 961px\) \{[\s\S]*?\.main\s*\{[\s\S]*?overflow-y:\s*auto;/,
  )
})

test('mobile portfolio stock cells use a compact table-specific identity layout', () => {
  assert.match(portfolioSource, /security: isMobileViewport\.value \? 152 : 128/)
  assert.match(portfolioSource, /class="portfolio-stock-identity"/)
  assert.match(portfolioSource, /\.holding-table :deep\(\.el-table__body td\.el-table__cell\)[\s\S]*?padding-top:\s*4px;[\s\S]*?padding-bottom:\s*4px;/)
  assert.match(portfolioSource, /\.holding-table :deep\(\.portfolio-stock-identity\.stock-identity\)[\s\S]*?min-height:\s*32px;[\s\S]*?gap:\s*1px;/)
  assert.match(portfolioSource, /\.portfolio-stock-identity \.stock-identity__meta-line[\s\S]*?height:\s*16px;/)
  assert.match(portfolioSource, /\.portfolio-stock-identity \.security-market-badge[\s\S]*?height:\s*16px;/)
})

test('holding P&L percentages use an explicit sign on both holding surfaces', () => {
  assert.match(portfolioSource, /<small v-if="row\.pnlPct != null">\{\{ fmtSignedPct\(row\.pnlPct\) \}\}<\/small>/)
  assert.match(holdingSource, /<small v-if="row\.pnlPct != null">\{\{ fmtSignedPct\(row\.pnlPct\) \}\}<\/small>/)
})

test('holding tables keep all actions inside one compact overflow menu', () => {
  assert.match(holdingSource, /width="52"[\s\S]*?:fixed="mobileRowActions \? false : 'right'"/)
  assert.match(portfolioSource, /v-if="!sharingCapture && !isMobileViewport"[\s\S]*?width="44"[\s\S]*?fixed="right"/)
  assert.match(holdingSource, /class="row-actions-trigger"/)
  assert.match(portfolioSource, /class="portfolio-row-actions-trigger"/)
  assert.match(holdingSource, /class="row-actions-trigger"[^>]*@click\.stop/)
  assert.match(portfolioSource, /class="portfolio-row-actions-trigger"[^>]*@click\.stop/)
  assert.doesNotMatch(holdingSource, /class="row-inline-actions"/)
  assert.doesNotMatch(portfolioSource, /class="portfolio-row-inline-actions"/)
  assert.match(holdingSource, /width: 28px;/)
  assert.match(portfolioSource, /width: 30px;/)
  assert.doesNotMatch(holdingSource, /box-shadow:\s*10px 0 18px -18px/)
  assert.doesNotMatch(portfolioSource, /box-shadow:\s*10px 0 18px -18px/)
  assert.match(holdingSource, /border-right:\s*1px solid var\(--line\)/)
  assert.match(portfolioSource, /border-right:\s*1px solid var\(--line\)/)
})

test('portfolio holding actions use a compact arrowless menu with transaction priority', () => {
  assert.match(portfolioSource, /:show-arrow="false"/)
  assert.match(portfolioSource, /<el-icon><Operation \/><\/el-icon>/)
  assert.match(portfolioSource, /\.holding-table :deep\(\.ops-column \.cell\)[\s\S]*?padding:\s*0 6px;/)
  assert.match(portfolioSource, /command="edit" :icon="EditPen" divided/)
  assert.match(portfolioSource, /min-width:\s*120px;/)
  assert.match(portfolioSource, /border-radius:\s*6px;/)
  assert.match(portfolioSource, /background:\s*rgba\(0, 113, 227, 0\.06\);/)
  assert.match(portfolioSource, /el-dropdown-menu__item:not\(\.is-disabled\):focus[\s\S]*?background:\s*#f3f6fa !important;/)
  assert.match(portfolioSource, /row-action-buy[\s\S]*?color:\s*#b54747 !important;/)
  assert.match(portfolioSource, /row-action-sell[\s\S]*?color:\s*#218052 !important;/)
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

test('current users default portfolio remains first in the list', () => {
  assert.match(
    portfolioSource,
    /list\.value = \[\.\.\.\(res\?\.data \|\| \[\]\)\]\.sort\(\(left, right\) => Number\(right\.isDefault\) - Number\(left\.isDefault\)\)/,
  )
})

test('mobile portfolio ordering uses direct up and down controls while desktop keeps drag sorting', () => {
  assert.match(portfolioSource, /import \{ getCurrentUser \} from '\.\.\/api\/auth'/)
  assert.match(portfolioSource, /const canSortPortfolios = computed\(\(\) => currentUser\?\.role === 'ADMIN'\)/)
  assert.match(portfolioSource, /v-if="canSortPortfolios"[\s\S]*?class="mobile-sort-button"/)
  assert.match(portfolioSource, /class="mobile-sort-button"/)
  assert.match(portfolioSource, /mobileSortMode \? '完成' : '排序'/)
  assert.match(portfolioSource, /function movePortfolio\(row, direction\)/)
  assert.match(portfolioSource, /class="pf-mobile-sort-controls"/)
  assert.match(portfolioSource, /aria-label="上移组合"/)
  assert.match(portfolioSource, /aria-label="下移组合"/)
  assert.match(portfolioSource, /grid-template-columns:\s*76px minmax\(48px, 1fr\) auto 18px;/)
  assert.match(portfolioSource, /width: 36px;/)
  assert.match(portfolioSource, /height: 44px;/)
  assert.match(portfolioSource, /v-if="!isMobileViewport && canSortPortfolios"[\s\S]*?class="pf-sort-handle"/)
  assert.match(portfolioSource, /async function persistPortfolioOrder\(fromId, toId, placeAfter\)/)
  assert.match(portfolioSource, /await sortPortfolios\(list\.value\.map\(\(row\) => row\.id\)\)/)
  assert.match(portfolioSource, /class="pf-sort-handle"/)
  assert.match(portfolioSource, /:data-portfolio-id="row\.id"/)
})

test('shared portfolios expose write controls when the backend marks them editable', () => {
  assert.match(portfolioSource, /const activeEditable = computed\(\(\) => activeSummary\.value\?\.editable === true\)/)
  assert.match(portfolioSource, /<strong>共享组合<\/strong>/)
  assert.match(portfolioSource, /v-else-if="row\.editable"[\s\S]*?class="pf-card-menu"/)
  assert.match(portfolioSource, /<el-dropdown-item v-if="detail\.editable" command="edit">编辑组合<\/el-dropdown-item>/)
  assert.match(portfolioSource, /<el-dropdown-item v-if="detail\.editable" command="import">导入持仓<\/el-dropdown-item>/)
  assert.match(portfolioSource, /v-if="activeEditable"[\s\S]*?command="buy"/)
  assert.match(portfolioSource, /v-if="activeEditable"[\s\S]*?command="sell"/)
  assert.match(portfolioSource, /v-if="activeEditable"[\s\S]*?command="edit"/)
  assert.match(portfolioSource, /command="observe" :icon="View">加入观察池<\/el-dropdown-item>/)
  assert.match(portfolioSource, /command="remove" :disabled="row\.systemDefault"/)
  assert.match(portfolioSource, /v-if="detail\.editable && !detail\.systemDefault" command="remove"/)
  assert.match(portfolioSource, /editingSystemDefault\.value = row\.systemDefault === true/)
  assert.match(portfolioSource, /v-if="pfForm\.id && !editingSystemDefault" label="状态"/)
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
