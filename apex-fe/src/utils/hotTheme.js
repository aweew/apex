/**
 * 主线题材展示：优先 hotThemeItems（含涨幅），兼容旧 hotThemes 字符串列表
 */

/**
 * 结果型情绪板（与后端 MainlineBoardRules 对齐，前端兜底过滤）
 */
export function isOutcomeBoard(name) {
  if (!name || !String(name).trim()) return true
  const n = String(name).trim()
  if (n.startsWith('昨日')) return true
  if ((n.startsWith('最近') || n.startsWith('近期'))
      && (n.includes('板') || n.includes('涨停') || n.includes('振幅'))) {
    return true
  }
  if (n.startsWith('今日')
      && (n.includes('涨停') || n.includes('连板') || n.includes('首板')
          || n.includes('打板') || n.includes('跌停') || n.includes('振幅')
          || n.includes('多板') || n.includes('炸板') || n.includes('一字'))) {
    return true
  }
  const keywords = [
    '昨日连板', '昨日涨停', '昨日高振幅', '最近多板', '近期多板',
    '含一字', '炸板', '连板天梯', '涨停天梯', '多板', '高振幅', '振幅',
    '曾涨停', '曾连板', '空间板', '卡位板', '弱转强', '反包',
    '一字板', '连板', '涨停', '首板', '打板', '跌停',
  ]
  return keywords.some((k) => n.includes(k))
}

/**
 * @param {number|string|null|undefined} pctChg
 * @returns {{ sign: string, abs: string, text: string, dir: ''|'up'|'down' }|null}
 */
export function formatHotThemePct(pctChg) {
  if (pctChg == null || pctChg === '') return null
  const n = Number(pctChg)
  if (Number.isNaN(n)) return null
  const abs = Math.abs(n).toFixed(2)
  const sign = n > 0 ? '+' : n < 0 ? '\u2212' : ''
  const dir = n > 0 ? 'up' : n < 0 ? 'down' : ''
  return {
    sign,
    abs,
    text: `${sign}${abs}%`,
    dir,
  }
}

export function formatHotThemeLabel(item) {
  if (item == null) return ''
  if (typeof item === 'string') return item
  const name = item.name || ''
  const pct = formatHotThemePct(item.pctChg)
  if (!pct) return name
  return `${name} ${pct.text}`
}

/**
 * @param {{ hotThemeItems?: Array, hotThemes?: string[] }|null|undefined} source
 * @returns {{ name: string, pctChg: number|null, sign: string, abs: string, pctText: string, pctDir: string, label: string, key: string }[]}
 */
export function normalizeHotThemes(source) {
  const items = source?.hotThemeItems
  if (Array.isArray(items) && items.length) {
    return items
      .filter((it) => it && it.name && !isOutcomeBoard(it.name))
      .map((it) => {
        const pct = formatHotThemePct(it.pctChg)
        return {
          name: it.name,
          pctChg: it.pctChg == null || it.pctChg === '' ? null : Number(it.pctChg),
          sign: pct?.sign || '',
          abs: pct?.abs || '',
          pctText: pct?.text || '',
          pctDir: pct?.dir || '',
          label: formatHotThemeLabel(it),
          key: it.name,
        }
      })
  }
  const names = source?.hotThemes || []
  return names
    .filter((name) => name && !isOutcomeBoard(name))
    .map((name) => ({
      name,
      pctChg: null,
      sign: '',
      abs: '',
      pctText: '',
      pctDir: '',
      label: String(name),
      key: String(name),
    }))
}
