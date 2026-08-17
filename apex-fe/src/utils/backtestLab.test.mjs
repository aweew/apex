import assert from 'node:assert/strict'
import test from 'node:test'

import {
  benchmarkOptions,
  buildRollingPayload,
  buildTrailingDateRange,
  formatAmount,
  formatPercent,
  restoreRollingForms,
} from './backtestLab.js'

test('benchmark options use unambiguous backend index codes', () => {
  assert.deepEqual(
    benchmarkOptions.map((option) => option.value),
    ['000300', '000905', '000852'],
  )
})

test('trailing backtest range follows the local calendar and clamps leap day', () => {
  assert.deepEqual(buildTrailingDateRange(2, new Date(2026, 7, 16, 23, 30)), {
    beginDate: '2024-08-16',
    endDate: '2026-08-16',
  })
  assert.deepEqual(buildTrailingDateRange(1, new Date(2024, 1, 29, 12)), {
    beginDate: '2023-02-28',
    endDate: '2024-02-29',
  })
})

test('rolling payload converts visible percent costs to decimal rates', () => {
  const payload = buildRollingPayload(
    {
      code: '600519',
      strategyId: 'S1',
      beginDate: '2024-01-01',
      endDate: '2026-08-01',
      initCash: 1000000,
    },
    {
      windowMode: 'ROLLING',
      trainDays: 252,
      testDays: 63,
      stepDays: 63,
      benchmarkCode: '000300',
      commissionPercent: 0.05,
      stampTaxPercent: 0.05,
      buySlippagePercent: 0.1,
      sellSlippagePercent: 0.1,
    },
  )

  assert.equal(payload.commissionRate, 0.0005)
  assert.equal(payload.stampTaxRate, 0.0005)
  assert.equal(payload.buySlippage, 0.001)
  assert.equal(payload.sellSlippage, 0.001)
  assert.equal(payload.trainDays, 252)
  assert.equal(payload.benchmarkCode, '000300')
})

test('rolling payload uses strategy snapshot only when the caller explicitly supplies it', () => {
  const strategyConfig = {
    strategyId: 'S1',
    logicVersion: 'S1_V1',
    s1FastMa: 5,
    s1SlowMa: 20,
    s1VolumeMa: 5,
  }
  const baseForm = {
    code: '600519',
    strategyId: 'S1',
    beginDate: '2024-01-01',
    endDate: '2026-08-01',
    initCash: 1000000.01,
  }
  const labForm = {
    windowMode: 'ROLLING',
    trainDays: 252,
    testDays: 63,
    stepDays: 63,
    benchmarkCode: '000300',
  }

  assert.equal(buildRollingPayload(baseForm, labForm).strategyConfig, undefined)
  assert.equal(buildRollingPayload(baseForm, labForm).exactReplay, undefined)
  assert.deepEqual(buildRollingPayload(baseForm, labForm, strategyConfig).strategyConfig, strategyConfig)
  assert.equal(buildRollingPayload({ ...baseForm, strategyId: 'S2' }, labForm, strategyConfig).strategyConfig, undefined)
})

test('rolling payload includes immutable audit expectations only for exact replay', () => {
  const strategyConfig = { strategyId: 'S1', logicVersion: 'S1_V1' }
  const replayAudit = {
    executionModelVersion: 'NEXT_OPEN_V4',
    priceAdjustment: 'QFQ',
    dataFingerprint: 'a'.repeat(64),
  }
  const payload = buildRollingPayload(
    {
      code: '600519',
      strategyId: 'S1',
      beginDate: '2024-01-01',
      endDate: '2026-08-01',
      initCash: 1000000,
    },
    {
      windowMode: 'ROLLING',
      trainDays: 252,
      testDays: 63,
      stepDays: 63,
      benchmarkCode: '000300',
      commissionPercent: 0.05,
      stampTaxPercent: 0.05,
      buySlippagePercent: 0.1,
      sellSlippagePercent: 0.1,
    },
    strategyConfig,
    replayAudit,
  )

  assert.equal(payload.exactReplay, true)
  assert.equal(payload.expectedExecutionModelVersion, 'NEXT_OPEN_V4')
  assert.equal(payload.expectedPriceAdjustment, 'QFQ')
  assert.equal(payload.expectedDataFingerprint, 'a'.repeat(64))
})

test('percent formatter keeps missing values distinct from zero', () => {
    assert.equal(formatPercent(null), '-')
    assert.equal(formatPercent(0), '0.00%')
    assert.equal(formatPercent(0.12345), '12.35%')
    assert.equal(formatAmount(null), '-')
    assert.equal(formatAmount(1000000), '1,000,000')
})

test('experiment history restores effective rates into visible percentages', () => {
  const currentBacktestForm = {
    code: '000001',
    strategyId: 'S2',
    beginDate: '2024-01-01',
    endDate: '2024-12-31',
    initCash: 500000,
  }
  const currentLabForm = {
    windowMode: 'EXPANDING',
    trainDays: 120,
    testDays: 20,
    stepDays: 20,
    benchmarkCode: '000905',
    commissionPercent: 0.03,
    stampTaxPercent: 0.1,
    buySlippagePercent: 0.08,
    sellSlippagePercent: 0.08,
  }

  const restored = restoreRollingForms({
    code: '600519',
    strategyId: 'S1',
    beginDate: '2023-01-03',
    endDate: '2026-08-14',
    initCash: 1000000,
    benchmarkCode: '000300',
    windowMode: 'ROLLING',
    trainDays: 252,
    testDays: 63,
    stepDays: 63,
    commissionRate: 0.0005,
    stampTaxRate: 0.0005,
    buySlippage: 0.001,
    sellSlippage: 0.001,
    strategyConfig: {
      strategyId: 'S1',
      s1FastMa: 5,
      s1SlowMa: 20,
      s1VolumeMa: 5,
    },
  }, currentBacktestForm, currentLabForm)

    assert.equal(restored.backtestForm.code, '600519')
    assert.equal(restored.backtestForm.beginDate, '2023-01-03')
    assert.equal(restored.backtestForm.initCash, 1000000)
  assert.equal(restored.labForm.trainDays, 252)
  assert.equal(restored.labForm.commissionPercent, 0.05)
  assert.equal(restored.labForm.buySlippagePercent, 0.1)
  assert.equal(Object.hasOwn(restored.backtestForm, 'strategyConfig'), false)
  assert.equal(currentBacktestForm.code, '000001')
  assert.equal(currentLabForm.commissionPercent, 0.03)
})

test('experiment history keeps current cost defaults when an older snapshot omits rates', () => {
  const restored = restoreRollingForms(
    { code: '600519', commissionRate: null, buySlippage: undefined },
    { code: '000001' },
    { commissionPercent: 0.05, buySlippagePercent: 0.1 },
  )

  assert.equal(restored.labForm.commissionPercent, 0.05)
  assert.equal(restored.labForm.buySlippagePercent, 0.1)
})

test('experiment history preserves eight-decimal rates when rebuilt for rerun', () => {
  const currentBacktestForm = {
    code: '600519',
    strategyId: 'S1',
    beginDate: '2023-01-03',
    endDate: '2026-08-14',
    initCash: 1000000.01,
  }
  const currentLabForm = {
    windowMode: 'ROLLING',
    trainDays: 252,
    testDays: 63,
    stepDays: 63,
    benchmarkCode: '000300',
    commissionPercent: 0.05,
    stampTaxPercent: 0.05,
    buySlippagePercent: 0.1,
    sellSlippagePercent: 0.1,
  }
  const effectiveRequest = {
    ...currentBacktestForm,
    ...currentLabForm,
    commissionRate: 0.00050049,
    stampTaxRate: 0.00060049,
    buySlippage: 0.00100049,
    sellSlippage: 0.00110049,
  }

  const restored = restoreRollingForms(effectiveRequest, currentBacktestForm, currentLabForm)
  const payload = buildRollingPayload(restored.backtestForm, restored.labForm)

  assert.equal(restored.labForm.commissionPercent, 0.050049)
  assert.equal(restored.backtestForm.initCash, 1000000.01)
  assert.equal(payload.initCash, 1000000.01)
  assert.equal(payload.commissionRate, 0.00050049)
  assert.equal(payload.stampTaxRate, 0.00060049)
  assert.equal(payload.buySlippage, 0.00100049)
  assert.equal(payload.sellSlippage, 0.00110049)
})
