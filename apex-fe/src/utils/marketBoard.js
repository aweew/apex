/**
 * 个股所属市场板块标签：科 / 创 / 京 / 港 / 美
 * 主板（沪/深）不展示标签。
 */

const BOARD_TITLE = {
  科: '科创板',
  创: '创业板',
  京: '北交所',
  港: '港股',
  美: '美股',
}

/**
 * 规范化为纯数字代码（兼容 600519.SH / SH600519）
 *
 * @param {string} code
 * @returns {string}
 */
export function normalizeStockDigits(code) {
  if (code == null || code === '') return ''
  const pure = String(code).trim().toUpperCase()
  if (pure.includes('.')) {
    const parts = pure.split('.')
    for (const part of parts) {
      if (/^\d{4,6}$/.test(part)) return part
    }
  }
  const digits = pure.replace(/\D/g, '')
  if (digits.length >= 6) return digits.slice(-6)
  return digits
}

/**
 * 是否美股代码（字母 ticker）
 *
 * @param {string} raw
 * @returns {boolean}
 */
function isUsTicker(raw) {
  if (!raw) return false
  const pure = String(raw)
    .trim()
    .toUpperCase()
    .replace(/\.(US|NYSE|NASDAQ)$/i, '')
  return /^[A-Z]{1,5}$/.test(pure)
}

/**
 * 是否北交所数字代码
 *
 * @param {string} digits
 * @returns {boolean}
 */
function isBjDigits(digits) {
  if (!digits || digits.length !== 6) return false
  return (
    digits.startsWith('92') ||
    digits.startsWith('83') ||
    digits.startsWith('87') ||
    digits.startsWith('43') ||
    digits.startsWith('4')
  )
}

/**
 * 推断板块标签：科 / 创 / 京 / 港 / 美；主板返回空串
 *
 * @param {string} code 证券代码
 * @param {string} [market] 交易所 SH/SZ/BJ/HK/US（可选，辅助判断）
 * @returns {''|'科'|'创'|'京'|'港'|'美'}
 */
export function resolveBoardTag(code, market) {
  const raw = String(code || '').trim().toUpperCase()
  const m = String(market || '').trim().toUpperCase()

  if (m === 'US' || m === 'NYSE' || m === 'NASDAQ' || isUsTicker(raw)) {
    return '美'
  }
  if (m === 'HK' || raw.endsWith('.HK') || raw.startsWith('HK')) {
    return '港'
  }
  if (m === 'BJ' || raw.endsWith('.BJ')) {
    return '京'
  }

  const digits = normalizeStockDigits(raw)
  if (!digits) return ''

  // 科创板
  if (digits.startsWith('688') || digits.startsWith('689')) return '科'
  // 创业板
  if (digits.startsWith('300') || digits.startsWith('301')) return '创'
  // 北交所（6 位）
  if (isBjDigits(digits)) return '京'
  // 港股常见 4~5 位
  if (digits.length >= 4 && digits.length <= 5) return '港'

  return ''
}

/**
 * 板块全称
 *
 * @param {string} tag
 * @returns {string}
 */
export function boardTagTitle(tag) {
  return BOARD_TITLE[tag] || ''
}
