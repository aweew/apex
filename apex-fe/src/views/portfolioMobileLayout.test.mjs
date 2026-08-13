import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const portfolioSource = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')

test('mobile portfolio controls share one compact list toolbar', () => {
  assert.match(portfolioSource, /class="mobile-list-toolbar"/)
  assert.match(portfolioSource, /class="mobile-create-button"/)
  assert.doesNotMatch(portfolioSource, /class="mobile-header-actions"/)
  assert.match(portfolioSource, /\.portfolio-page \.portfolio-header\s*\{\s*display:\s*none;/)
})

test('mobile portfolio rows keep today performance beside the portfolio name', () => {
  assert.match(portfolioSource, /v-if="isMobileViewport" class="pf-mobile-pnl"/)
  assert.match(portfolioSource, /v-if="!isMobileViewport" class="pf-pnl"/)
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

test('portfolio list holding summaries omit market badges', () => {
  const portfolioListTemplate = portfolioSource.slice(portfolioSource.indexOf('class="pf-card"'), portfolioSource.indexOf('class="side-rail"'))
  assert.doesNotMatch(portfolioListTemplate, /<SecurityMarketBadge :security="h"/)
})
