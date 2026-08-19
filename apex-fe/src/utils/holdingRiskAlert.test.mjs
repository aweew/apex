import assert from 'node:assert/strict'
import test from 'node:test'

import {
  HOLDING_ALERT_TYPE,
  detectHoldingRiskAlert,
  summarizeHoldingRiskAlerts,
} from './holdingRiskAlert.js'

test('detects user stop-loss and take-profit prices with stop-loss priority', () => {
  assert.deepEqual(
    detectHoldingRiskAlert({ code: '000001', marketPrice: 9.5, stopLoss: 10, takeProfit: 12 }),
    {
      code: '000001',
      type: HOLDING_ALERT_TYPE.STOP_LOSS,
      marketPrice: 9.5,
      triggerPrice: 10,
    },
  )

  assert.deepEqual(
    detectHoldingRiskAlert({ code: '600000', marketPrice: 12.2, stopLoss: 9, takeProfit: 12 }),
    {
      code: '600000',
      type: HOLDING_ALERT_TYPE.TAKE_PROFIT,
      marketPrice: 12.2,
      triggerPrice: 12,
    },
  )

  assert.equal(
    detectHoldingRiskAlert({ code: '300001', marketPrice: 10, stopLoss: 10, takeProfit: 10 })?.type,
    HOLDING_ALERT_TYPE.STOP_LOSS,
  )
})

test('ignores missing market prices and unset or invalid trigger prices', () => {
  assert.equal(detectHoldingRiskAlert({ code: '000001', marketPrice: null, stopLoss: 10 }), null)
  assert.equal(detectHoldingRiskAlert({ code: '000001', marketPrice: 10, stopLoss: 0 }), null)
  assert.equal(detectHoldingRiskAlert({ code: '000001', marketPrice: 10, takeProfit: 'invalid' }), null)
})

test('summarizes triggered holdings by urgency', () => {
  const summary = summarizeHoldingRiskAlerts([
    { code: '000001', name: '平安银行', marketPrice: 9.5, stopLoss: 10 },
    { code: '600000', name: '浦发银行', marketPrice: 12.2, takeProfit: 12 },
    { code: '300001', name: '特锐德', marketPrice: 10, stopLoss: 9, takeProfit: 11 },
  ])

  assert.equal(summary.total, 2)
  assert.equal(summary.stopLossCount, 1)
  assert.equal(summary.takeProfitCount, 1)
  assert.equal(summary.items[0].type, HOLDING_ALERT_TYPE.STOP_LOSS)
  assert.equal(summary.items[1].type, HOLDING_ALERT_TYPE.TAKE_PROFIT)
})
