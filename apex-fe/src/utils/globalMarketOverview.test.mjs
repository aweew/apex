import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildGlobalMarketHubs,
  derivePointChange,
  summarizeGlobalMarkets,
} from './globalMarketOverview.js'

const marketIndexes = {
  cn: [
    { code: 'CN_SH', name: '上证指数', tradeDate: '2026-08-25', closePrice: 3889.44, pctChg: 0.19 },
    { code: 'CN_SZ', name: '深证成指', tradeDate: '2026-08-25', closePrice: 13745.87, pctChg: -0.35 },
  ],
  hk: [
    { code: 'HK_HSI', name: '恒生指数', tradeDate: '2026-08-25', closePrice: 25511.1, pctChg: -0.02 },
  ],
  jp: [
    { code: 'JP_N225', name: '日经225', tradeDate: '2026-08-25', closePrice: 44500, pctChg: 0.5 },
  ],
  kr: [
    { code: 'KR_KOSPI', name: '韩国综合', tradeDate: '2026-08-25', closePrice: 3200, pctChg: 0 },
  ],
  us: [
    { code: 'US_DJI', name: '道琼斯', tradeDate: '2026-08-24', closePrice: 45600, pctChg: -0.3 },
    { code: 'US_SPX', name: '标普500', tradeDate: '2026-08-24', closePrice: 6480, pctChg: 0.2 },
  ],
}

test('global market hubs preserve geographic order and select each market benchmark', () => {
  const hubs = buildGlobalMarketHubs(marketIndexes)

  assert.deepEqual(hubs.map((hub) => hub.key), ['us', 'cn', 'hk', 'kr', 'jp'])
  assert.deepEqual(hubs.map((hub) => hub.primary?.code), [
    'US_SPX',
    'CN_SH',
    'HK_HSI',
    'KR_KOSPI',
    'JP_N225',
  ])
  assert.equal(hubs[0].items.length, 2)
})

test('global market summary reports breadth, coverage and the newest available snapshot', () => {
  assert.deepEqual(summarizeGlobalMarkets(marketIndexes), {
    up: 3,
    down: 3,
    flat: 1,
    total: 7,
    latestTradeDate: '2026-08-25',
  })
})

test('point change is derived from close and percentage without inventing missing values', () => {
  assert.equal(derivePointChange({ closePrice: 3889.44, pctChg: 0.19 }).toFixed(2), '7.38')
  assert.equal(derivePointChange({ closePrice: 13745.87, pctChg: -0.35 }).toFixed(2), '-48.28')
  assert.equal(derivePointChange({ closePrice: null, pctChg: 0.5 }), null)
})
