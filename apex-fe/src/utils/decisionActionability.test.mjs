import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buyActionState,
  canPaperBuy,
  chinaMarketDate,
  paperBuyBlockedReason,
} from './decisionActionability.js'

const executableBuy = { action: 'BUY', executableHint: true }
const liveDecision = {
  dataLevel: 'GREEN',
  generated: true,
  runMode: 'LIVE',
  actionDate: '2026-08-23',
  currentDate: '2026-08-23',
}

test('only executable buy suggestions with usable market data can be paper ordered', () => {
  assert.equal(canPaperBuy(executableBuy, liveDecision), true)
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, dataLevel: 'YELLOW' }), true)
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, dataLevel: 'RED' }), false)
  assert.equal(canPaperBuy({ action: 'BUY', executableHint: false }, liveDecision), false)
  assert.equal(canPaperBuy({ action: 'SELL', executableHint: true }, liveDecision), false)
})

test('historical, shadow, and incomplete decision runs cannot be paper ordered', () => {
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, actionDate: '2026-08-22' }), false)
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, runMode: 'REPLAY' }), false)
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, runMode: 'SHADOW' }), false)
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, runMode: null }), false)
  assert.equal(canPaperBuy(executableBuy, { ...liveDecision, generated: false }), false)
})

test('actionability labels explain data and decision-run blocks', () => {
  assert.equal(buyActionState(executableBuy, liveDecision), '可执行')
  assert.equal(buyActionState(executableBuy, { ...liveDecision, dataLevel: 'YELLOW' }), '可执行：先复核')
  assert.equal(buyActionState(executableBuy, { ...liveDecision, dataLevel: 'RED' }), '仅观察：数据异常')
  assert.equal(buyActionState(executableBuy, { ...liveDecision, actionDate: '2026-08-22' }), '仅回放：非当日决策')
  assert.equal(buyActionState(executableBuy, { ...liveDecision, runMode: null }), '仅观察：运行状态不完整')
  assert.equal(buyActionState({ action: 'BUY', executableHint: false }, liveDecision), '仅观察：风控未通过')
  assert.equal(paperBuyBlockedReason(executableBuy, { ...liveDecision, runMode: 'REPLAY' }), '当前为历史回放，仅供复盘，不允许模拟买入')
  assert.equal(paperBuyBlockedReason(executableBuy, { ...liveDecision, generated: false }), '决策运行状态不完整，暂不允许模拟买入')
})

test('market date is evaluated in China time instead of the browser local zone', () => {
  assert.equal(chinaMarketDate(new Date('2026-08-23T16:30:00Z')), '2026-08-24')
})
