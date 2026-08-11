export function formatVolumeChangeAmount(value) {
  if (value == null || value === '') return ''
  const amount = Math.abs(Number(value))
  if (!Number.isFinite(amount)) return ''
  if (amount >= 100000000) return `${Math.round(amount / 100000000)}亿`
  if (amount >= 10000) return `${(amount / 10000).toFixed(1)}万`
  return `${Math.round(amount)}`
}

export function resolveVolumeChangeAmount(market, pct) {
  if (market?.indexVolumeChange != null && market.indexVolumeChange !== '') {
    return market.indexVolumeChange
  }
  const current = Number(market?.indexVolume)
  const rate = Number(pct)
  if (!Number.isFinite(current) || !Number.isFinite(rate) || rate === -100) return null
  return current * rate / (100 + rate)
}

export function buildVolumeChangeParts(market) {
  if (!market) {
    return {
      trendText: '',
      amountText: '',
      detailText: '',
      percentageText: '',
    }
  }
  const pct = market.volumeVsMa5Pct
  if (pct == null || pct === '' || !Number.isFinite(Number(pct))) {
    const trendText = market.volumeLabel || market.volumeTrend || ''
    return {
      trendText,
      amountText: '',
      detailText: trendText,
      percentageText: '',
    }
  }
  const rate = Number(pct)
  const trend = market.volumeTrend || (rate >= 0 ? '放量' : '缩量')
  const amount = formatVolumeChangeAmount(resolveVolumeChangeAmount(market, rate))
  const sign = rate > 0 ? '+' : ''
  return {
    trendText: trend,
    amountText: amount,
    detailText: `${trend}${amount ? ` ${amount}` : ''}`,
    percentageText: `${sign}${rate.toFixed(2)}%`,
  }
}

export function formatVolumeChangeText(market) {
  const parts = buildVolumeChangeParts(market)
  return [parts.detailText, parts.percentageText].filter(Boolean).join(' ')
}
