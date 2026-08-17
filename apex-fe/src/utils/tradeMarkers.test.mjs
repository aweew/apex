import assert from 'node:assert/strict'
import test from 'node:test'

import { buildTradeMarkerSeries } from './tradeMarkers.js'

const chartBars = [
  { tradeDate: '2026-08-08', lowPrice: 9, highPrice: 11, closePrice: 10 },
  { tradeDate: '2026-08-15', lowPrice: 10, highPrice: 13, closePrice: 12 },
]

test('maps trades into the containing aggregated K-line period', () => {
  const result = buildTradeMarkerSeries([
    {
      id: 1,
      tradeTime: '2026-08-12T10:30:00',
      side: 'BUY',
      price: 11.2,
      ownerLabel: '张三',
      portfolioName: '疯锅',
    },
  ], chartBars)

  assert.equal(result.buy[0].value[0], '2026-08-15')
  assert.equal(result.buy[0].labelText, 'B 张三')
})

test('groups same-period same-side records and keeps every owner in details', () => {
  const result = buildTradeMarkerSeries([
    {
      id: 1,
      tradeTime: '2026-08-12T10:30:00',
      side: 'SELL',
      price: 12,
      ownerLabel: '张三',
      portfolioName: '疯锅',
    },
    {
      id: 2,
      tradeTime: '2026-08-13T14:00:00',
      side: 'SELL',
      price: 12.5,
      ownerLabel: '李四',
      portfolioName: '短线',
    },
  ], chartBars)

  assert.equal(result.sell.length, 1)
  assert.equal(result.sell[0].labelText, 'S 张三 +1')
  assert.deepEqual(result.sell[0].records.map((row) => row.ownerLabel), ['张三', '李四'])
})

test('falls back to bar price when an estimated record has no price', () => {
  const result = buildTradeMarkerSeries([
    { id: 1, tradeDate: '2026-08-08', side: 'SELL', portfolioName: '疯锅', price: null },
  ], chartBars)

  assert.equal(result.sell[0].value[1], 11)
  assert.equal(result.sell[0].labelText, 'S 疯锅')
})
