export const benchmarkOptions = [
  { label: '沪深300', value: '000300' },
  { label: '中证500', value: '000905' },
  { label: '中证1000', value: '000852' },
]

function formatLocalDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function buildTrailingDateRange(years, today = new Date()) {
  const endDate = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const beginDate = new Date(endDate)
  beginDate.setFullYear(endDate.getFullYear() - years)
  if (beginDate.getMonth() !== endDate.getMonth()) {
    beginDate.setDate(0)
  }
  return {
    beginDate: formatLocalDate(beginDate),
    endDate: formatLocalDate(endDate),
  }
}

function percentToRate(value) {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return null
  return Number((numericValue / 100).toFixed(8))
}

function rateToPercent(value, fallbackValue) {
  if (value === null || value === undefined || value === '') return fallbackValue
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return fallbackValue
  return Number((numericValue * 100).toFixed(6))
}

export function buildRollingPayload(backtestForm, labForm, requestedStrategyConfig, replayAudit) {
  const strategyConfig = requestedStrategyConfig?.strategyId === backtestForm.strategyId
    ? { ...requestedStrategyConfig }
    : undefined
  return {
    code: backtestForm.code,
    strategyId: backtestForm.strategyId,
    beginDate: backtestForm.beginDate,
    endDate: backtestForm.endDate,
    initCash: backtestForm.initCash,
    benchmarkCode: labForm.benchmarkCode,
    windowMode: labForm.windowMode,
    trainDays: labForm.trainDays,
    testDays: labForm.testDays,
    stepDays: labForm.stepDays,
    commissionRate: percentToRate(labForm.commissionPercent),
    stampTaxRate: percentToRate(labForm.stampTaxPercent),
    buySlippage: percentToRate(labForm.buySlippagePercent),
    sellSlippage: percentToRate(labForm.sellSlippagePercent),
    strategyConfig,
    exactReplay: replayAudit ? true : undefined,
    expectedExecutionModelVersion: replayAudit?.executionModelVersion,
    expectedPriceAdjustment: replayAudit?.priceAdjustment,
    expectedDataFingerprint: replayAudit?.dataFingerprint,
  }
}

export function formatPercent(value, digits = 2) {
  if (value === null || value === undefined || value === '') return '-'
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return '-'
  return `${(numericValue * 100).toFixed(digits)}%`
}

export function formatAmount(value) {
  if (value === null || value === undefined || value === '') return '-'
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) return '-'
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(numericValue)
}

export function restoreRollingForms(request, currentBacktestForm, currentLabForm) {
  const effectiveRequest = request || {}
  const strategyId = effectiveRequest.strategyId ?? currentBacktestForm.strategyId
  return {
    backtestForm: {
      ...currentBacktestForm,
      code: effectiveRequest.code ?? currentBacktestForm.code,
      strategyId,
      beginDate: effectiveRequest.beginDate ?? currentBacktestForm.beginDate,
      endDate: effectiveRequest.endDate ?? currentBacktestForm.endDate,
      initCash: effectiveRequest.initCash ?? currentBacktestForm.initCash,
    },
    labForm: {
      ...currentLabForm,
      windowMode: effectiveRequest.windowMode ?? currentLabForm.windowMode,
      trainDays: effectiveRequest.trainDays ?? currentLabForm.trainDays,
      testDays: effectiveRequest.testDays ?? currentLabForm.testDays,
      stepDays: effectiveRequest.stepDays ?? currentLabForm.stepDays,
      benchmarkCode: effectiveRequest.benchmarkCode ?? currentLabForm.benchmarkCode,
      commissionPercent: rateToPercent(effectiveRequest.commissionRate, currentLabForm.commissionPercent),
      stampTaxPercent: rateToPercent(effectiveRequest.stampTaxRate, currentLabForm.stampTaxPercent),
      buySlippagePercent: rateToPercent(effectiveRequest.buySlippage, currentLabForm.buySlippagePercent),
      sellSlippagePercent: rateToPercent(effectiveRequest.sellSlippage, currentLabForm.sellSlippagePercent),
    },
  }
}
