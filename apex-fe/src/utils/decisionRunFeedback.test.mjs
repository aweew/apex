import test from 'node:test'
import assert from 'node:assert/strict'
import { decisionRunFeedback, decisionJobChanged } from './decisionRunFeedback.js'

test('distinguishes not generated from published with no opportunities', () => {
  assert.equal(decisionRunFeedback({ generated: false }).title, '今日决策尚未生成')
  assert.equal(decisionRunFeedback({ generated: true, buys: [], sells: [], holds: [] }).title, '决策已完成，暂无符合条件的操作')
})

test('unpublished attempt exposes reason even when an older result exists', () => {
  const result = decisionRunFeedback({ generated: true, latestRun: {
    status: 'SUCCESS', published: false, message: '指数行情未就绪',
  } })
  assert.equal(result.type, 'warning')
  assert.match(result.detail, /指数行情未就绪/)
  assert.match(result.detail, /上次已发布/)
})

test('running task shows actual progress, and failures are not empty opportunities', () => {
  assert.equal(decisionRunFeedback(null, { status: 'RUNNING', message: '正在扫描 120/500' }).detail, '正在扫描 120/500')
  assert.equal(decisionRunFeedback(null, { status: 'FAILED', message: '共享股票池尚未发布' }).type, 'error')
  assert.equal(decisionRunFeedback({ latestRun: { status: 'FAILED', message: '数据源超时' } }).detail, '数据源超时')
})

test('published result counts all rows independent of view filters', () => {
  const result = decisionRunFeedback({ generated: true, buys: [{}, {}], sells: [{}], holds: [{}] })
  assert.match(result.detail, /买入候选 2 · 卖出 1 · 持有 1/)
})

test('refreshes on completion and a new completed job, not unchanged polling', () => {
  assert.equal(decisionJobChanged({ id: 1, status: 'RUNNING' }, { id: 1, status: 'SUCCESS' }), true)
  assert.equal(decisionJobChanged({ id: 1, status: 'SUCCESS' }, { id: 2, status: 'SUCCESS' }), true)
  assert.equal(decisionJobChanged({ id: 1, status: 'SUCCESS' }, { id: 1, status: 'SUCCESS' }), false)
  assert.equal(decisionJobChanged(null, { id: 1, status: 'SUCCESS' }), false)
})

test('zero executable candidates explain the gates without turning them into buy advice', () => {
  const result = decisionRunFeedback({ generated: true, buys: [
    { executableHint: false, riskFlags: ['市场广度不足，禁止新开仓', '行业逆主线，禁止新开仓'] },
    { executableHint: false, riskFlags: ['市场广度不足，禁止新开仓', '市场广度不足，禁止新开仓'] },
  ] })
  assert.equal(result.type, 'warning')
  assert.equal(result.title, '已评估 2 只候选，当前均需继续观察')
  assert.match(result.detail, /市场广度不足，禁止新开仓（2只）/)
  assert.match(result.detail, /通过开仓风控 0/)
})
