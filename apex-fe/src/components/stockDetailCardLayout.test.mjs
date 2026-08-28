import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const componentSource = await readFile(new URL('./StockDetailCard.vue', import.meta.url), 'utf8')
const mainSource = await readFile(new URL('../main.js', import.meta.url), 'utf8')
const stockViewSource = await readFile(new URL('../views/StockView.vue', import.meta.url), 'utf8')

test('stock detail card stays globally available while the stock page uses one primary chart', () => {
  assert.match(mainSource, /defineAsyncComponent\(\(\) => import\('.\/components\/StockDetailCard\.vue'\)\)/)
  assert.match(mainSource, /app\.component\('StockDetailCard', StockDetailCard\)/)
  assert.doesNotMatch(stockViewSource, /<StockDetailCard\b/)
  assert.match(stockViewSource, /class="market-overview"[\s\S]*?class="chart-stage"/)
})

test('stock detail card exposes all chart periods without a reference disclaimer', () => {
  assert.match(componentSource, /const periods = \[[\s\S]*?value: 'intraday'[\s\S]*?value: 'day'[\s\S]*?value: 'week'[\s\S]*?value: 'month'/)
  assert.match(componentSource, /:data-period="item\.value"/)
  assert.match(componentSource, /aggregateBars\(props\.bars, activePeriod\.value\)/)
  assert.doesNotMatch(componentSource, /仅供参考/)
})

test('stock detail card keeps a stable responsive chart surface', () => {
  assert.match(componentSource, /\.stock-detail-card__chart\s*\{[\s\S]*?height:\s*clamp\(/)
  assert.match(componentSource, /@media \(max-width:\s*640px\)[\s\S]*?\.stock-detail-card__chart\s*\{[\s\S]*?height:\s*360px/)
  assert.match(componentSource, /aria-label="个股行情周期"/)
  assert.match(componentSource, /aria-pressed="activePeriod === item\.value"/)
})

test('stock detail card keeps unavailable quote and intraday volume distinct from zero', () => {
  assert.match(componentSource, /if \(isIntraday\.value && !intradayPoints\.value\.length\) return '-'/)
  assert.match(componentSource, /if \(props\.basic\?\.latestPrice == null \|\| props\.basic\?\.pctChg == null\) return '-'/)
})

test('stock detail card can request another attempt for unavailable intraday data', () => {
  assert.match(componentSource, /if \(activePeriod\.value === period\) \{[\s\S]*?period === 'intraday'[\s\S]*?emit\('period-change', period\)/)
})
