import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const readSource = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('stock identity keeps the market badge beside the name and code secondary', () => {
  const source = readSource('./StockIdentity.vue')

  assert.match(source, /class="stock-identity__name-line"[\s\S]*?<SecurityMarketBadge[\s\S]*?class="stock-identity__code"/)
  assert.match(source, /include-main/)
  assert.match(source, /:aria-label="accessibleLabel"/)
  assert.match(source, /min-height:\s*44px/)
})

test('primary stock surfaces reuse the shared stock identity', () => {
  const surfaces = [
    '../App.vue',
    '../views/BacktestView.vue',
    '../views/DashboardView.vue',
    '../views/DecisionView.vue',
    '../views/DailyView.vue',
    '../views/HotView.vue',
    '../views/HeatmapView.vue',
    '../views/HoldingView.vue',
    '../views/LimitUpLadderView.vue',
    '../views/ObserveView.vue',
    '../views/PaperView.vue',
    '../views/PipelineView.vue',
    '../views/PortfolioView.vue',
    '../views/ScreenerView.vue',
    '../views/SectorBoardView.vue',
    '../views/SignalView.vue',
    '../views/StockView.vue',
    '../views/TradeRecordView.vue',
    '../views/ValuationView.vue',
    '../views/WatchlistView.vue',
    './StockAnalysisPanel.vue',
    './news/HotBriefPanel.vue',
  ]

  for (const surface of surfaces) {
    const surfaceSource = readSource(surface)
    assert.match(surfaceSource, /<StockIdentity\b/, surface)
    assert.doesNotMatch(surfaceSource, /<SecurityMarketBadge\b/, surface)
  }
})

test('stock identity is registered as an application primitive', () => {
  const source = readSource('../main.js')

  assert.match(source, /import StockIdentity from '.\/components\/StockIdentity\.vue'/)
  assert.match(source, /app\.component\('StockIdentity', StockIdentity\)/)
})
