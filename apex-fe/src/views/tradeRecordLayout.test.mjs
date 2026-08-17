import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./TradeRecordView.vue', import.meta.url), 'utf8')
const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const portfolioSource = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')

test('trade record workspace exposes provenance, estimates and post-sell performance', () => {
  assert.match(source, /交易记录/)
  assert.match(source, /SecurityMarketBadge/)
  assert.match(source, /portfolioId/)
  assert.match(source, /estimated/)
  assert.match(source, /latestReturnPct/)
  assert.match(source, /maxRisePct/)
  assert.match(source, /maxFallPct/)
})

test('trade record workspace is routed and reachable from portfolios', () => {
  assert.match(routerSource, /path:\s*['"]\/trades['"]/)
  assert.match(portfolioSource, /router\.push\(['"]\/trades['"]\)/)
})

test('mobile trade records use cards instead of a squeezed desktop table', () => {
  assert.match(source, /trade-mobile-list/)
  assert.match(source, /@media \(max-width: 820px\)/)
  assert.match(source, /\.trade-table[\s\S]*display:\s*none/)
})
