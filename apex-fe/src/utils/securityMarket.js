const BADGES = {
  STAR: { label: '科', tone: 'star', title: '科创板' },
  CHINEXT: { label: '创', tone: 'chinext', title: '创业板' },
  BJ: { label: '京', tone: 'bj', title: '北交所' },
  HK: { label: '港', tone: 'hk', title: '港股' },
  US: { label: '美', tone: 'us', title: '美股' },
  SH: { label: '沪', tone: 'sh', title: '沪市' },
  SZ: { label: '深', tone: 'sz', title: '深市' },
}

function numericCode(code) {
  const parts = String(code || '').match(/\d+/g) || []
  return parts.find((part) => part.length >= 4 && part.length <= 6) || ''
}

export function securityMarketBadge(row, options = {}) {
  const code = String(row?.code || '').trim().toUpperCase()
  const market = String(row?.market || '').trim().toUpperCase()

  if (market === 'HK' || code.startsWith('HK') || code.endsWith('.HK')) return BADGES.HK
  if (['US', 'NASDAQ', 'NYSE', 'AMEX'].includes(market) || code.startsWith('US.') || code.endsWith('.US')) {
    return BADGES.US
  }

  const digits = numericCode(code)
  if (digits.length >= 4 && digits.length <= 5) return BADGES.HK
  if (/^(688|689)\d{3}$/.test(digits)) return BADGES.STAR
  if (/^(300|301)\d{3}$/.test(digits)) return BADGES.CHINEXT
  if (/^(92|83|87)\d{4}$/.test(digits) || /^4\d{5}$/.test(digits) || market === 'BJ') return BADGES.BJ
  if (/[A-Z]/.test(code) && !/(^|\.)(SH|SZ|BJ)(\.|$)/.test(code)) return BADGES.US

  if (!options.includeMain) return null
  if (['SH', 'SSE', 'XSHG'].includes(market) || /^6\d{5}$/.test(digits)) return BADGES.SH
  if (['SZ', 'SZSE', 'XSHE'].includes(market) || /^(0|2|3)\d{5}$/.test(digits)) return BADGES.SZ
  return null
}
