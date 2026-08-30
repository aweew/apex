import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const componentSource = await readFile(new URL('./IntradaySparkline.vue', import.meta.url), 'utf8')
const dashboardSource = await readFile(new URL('../views/DashboardView.vue', import.meta.url), 'utf8')

test('intraday sparkline accepts generic price points and exposes an accessible SVG', () => {
  assert.match(componentSource, /defineProps\(\{[\s\S]*points:\s*\{[\s\S]*type:\s*Array/)
  assert.match(componentSource, /role="img"/)
  assert.match(componentSource, /:aria-label="ariaLabel"/)
  assert.match(componentSource, /intraday-sparkline-baseline/)
  assert.match(componentSource, /暂无日内走势/)
})

test('dashboard applies the reusable sparkline to China assets without inventing missing data', () => {
  assert.match(
    dashboardSource,
    /import IntradaySparkline from ['"]\.\.\/components\/IntradaySparkline\.vue['"]/
  )
  assert.match(dashboardSource, /const chinaAssetIntradayPoints = computed\(/)
  assert.match(dashboardSource, /:points="chinaAssetIntradayPoints"/)
  assert.match(dashboardSource, /label="富时 A50 期指连续日内走势"/)
})
