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

test('opening mobile detail redraws charts after their containers mount', () => {
  assert.match(
    portfolioSource,
    /watch\(mobileDetailOpen, async \(open\) => \{[\s\S]*?await nextTick\(\)[\s\S]*?renderPies\(\)[\s\S]*?renderDailyChart\(\)/,
  )
  assert.match(portfolioSource, /themeChart\.getDom\(\) !== themePieRef\.value/)
  assert.match(portfolioSource, /chart\.getDom\(\) !== chartRef\.value/)
})
