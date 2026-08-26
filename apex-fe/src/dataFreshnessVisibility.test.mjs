import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const sources = await Promise.all([
  './App.vue',
  './components/ChipDistributionPanel.vue',
  './views/ApexAiView.vue',
  './views/CapitalFlowView.vue',
  './views/ConfigView.vue',
  './views/DashboardView.vue',
  './views/DecisionView.vue',
  './views/FactorCenterView.vue',
  './views/HeatmapView.vue',
  './views/IndexBoardView.vue',
  './views/SectorBoardView.vue',
  './views/StockView.vue',
].map(async (path) => [path, await readFile(new URL(path, import.meta.url), 'utf8')]))

const sourceByPath = Object.fromEntries(sources)

test('the app loads the authoritative A-share trading calendar for freshness checks', () => {
  assert.match(sourceByPath['./App.vue'], /getTradingCalendar/)
  assert.match(sourceByPath['./App.vue'], /setTradingCalendar/)
})

test('all market data time surfaces use the shared stale-only formatter', () => {
  for (const path of Object.keys(sourceByPath).filter((path) => path !== './App.vue')) {
    assert.match(sourceByPath[path], /staleDataTime/, `${path} must use staleDataTime`)
  }
})

test('latest data times are conditionally rendered instead of always occupying the interface', () => {
  assert.match(sourceByPath['./views/DashboardView.vue'], /v-if="dashboardMarketTime"/)
  assert.match(sourceByPath['./views/DecisionView.vue'], /v-if="decisionMarketTime"/)
  assert.match(sourceByPath['./views/IndexBoardView.vue'], /v-if="globalMarketTime"/)
  assert.match(sourceByPath['./views/SectorBoardView.vue'], /v-if="constituentDataTime"/)
  assert.match(sourceByPath['./views/StockView.vue'], /v-if="intradayDataTime"/)
  assert.match(sourceByPath['./views/ApexAiView.vue'], /v-if="analysisDataTime\(message\.analysis\)"/)
})
