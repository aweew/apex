import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const componentSource = await readFile(new URL('./IntradaySparkline.vue', import.meta.url), 'utf8')
const klineComponentSource = await readFile(new URL('./IntradayKlineThumbnail.vue', import.meta.url), 'utf8')
const dashboardSource = await readFile(new URL('../views/DashboardView.vue', import.meta.url), 'utf8')

test('intraday sparkline accepts generic price points and exposes an accessible SVG', () => {
  assert.match(componentSource, /defineProps\(\{[\s\S]*points:\s*\{[\s\S]*type:\s*Array/)
  assert.match(componentSource, /role="img"/)
  assert.match(componentSource, /:aria-label="ariaLabel"/)
  assert.match(componentSource, /intraday-sparkline-baseline/)
  assert.match(componentSource, /暂无日内走势/)
})

test('intraday K-line thumbnail renders one accessible close-price trend line', () => {
  assert.match(klineComponentSource, /role="img"/)
  assert.match(klineComponentSource, /bar\?\.closePrice \?\? bar\?\.close/)
  assert.match(klineComponentSource, /class="intraday-kline-line"/)
  assert.match(klineComponentSource, /#d6495f/)
  assert.match(klineComponentSource, /#16866a/)
  assert.doesNotMatch(klineComponentSource, /intraday-kline-wick/)
  assert.doesNotMatch(klineComponentSource, /intraday-kline-body/)
  assert.doesNotMatch(klineComponentSource, /intraday-kline-baseline/)
})

test('dashboard applies real intraday K-line thumbnails to US indexes and China assets', () => {
  assert.match(
    dashboardSource,
    /import IntradayKlineThumbnail from ['"]\.\.\/components\/IntradayKlineThumbnail\.vue['"]/
  )
  assert.match(dashboardSource, /quote\.intradayBars\?\.length/)
  assert.match(dashboardSource, /:bars="quote\.intradayBars"/)
  assert.match(dashboardSource, /:previous-close="quote\.previousClose"/)
  assert.match(dashboardSource, /ftseA50Future\.intradayBars\?\.length/)
  assert.match(dashboardSource, /:bars="ftseA50Future\.intradayBars"/)
  assert.match(dashboardSource, /label="富时 A50 期指连续日内 K 线"/)
})
