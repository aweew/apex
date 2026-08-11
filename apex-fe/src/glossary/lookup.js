import { GLOSSARY_CATEGORIES, GLOSSARY_TERMS } from './terms.js'

const byId = new Map(GLOSSARY_TERMS.map((term) => [term.id, term]))

function normalize(text) {
  return String(text ?? '')
    .trim()
    .toLowerCase()
    .replace(/\s+/g, '')
}

function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 按 id 或别名精确查找 */
export function findTerm(key) {
  if (!key) return null
  const raw = String(key).trim()
  if (byId.has(raw)) return byId.get(raw)
  const needle = normalize(raw)
  if (!needle) return null
  for (const term of GLOSSARY_TERMS) {
    if (normalize(term.id) === needle || normalize(term.title) === needle) return term
    for (const alias of term.aliases || []) {
      if (normalize(alias) === needle) return term
    }
  }
  return null
}

/** 搜索：命中标题/别名/释义 */
export function searchTerms(keyword, limit = 30) {
  const needle = normalize(keyword)
  if (!needle) {
    return GLOSSARY_TERMS.slice(0, limit)
  }
  const scored = []
  for (const term of GLOSSARY_TERMS) {
    const title = normalize(term.title)
    const id = normalize(term.id)
    const aliases = (term.aliases || []).map(normalize)
    let score = 0
    if (id === needle || title === needle || aliases.includes(needle)) score = 100
    else if (title.startsWith(needle) || id.startsWith(needle)) score = 80
    else if (aliases.some((a) => a.startsWith(needle))) score = 70
    else if (title.includes(needle) || id.includes(needle) || aliases.some((a) => a.includes(needle))) score = 50
    else if (normalize(term.plain).includes(needle)) score = 30
    else if (normalize(term.short).includes(needle) || normalize(term.detail).includes(needle)) score = 20
    if (score > 0) scored.push({ term, score })
  }
  scored.sort((a, b) => b.score - a.score || a.term.title.localeCompare(b.term.title, 'zh'))
  return scored.slice(0, limit).map((row) => row.term)
}

export function listByCategory(category) {
  if (!category) return [...GLOSSARY_TERMS]
  return GLOSSARY_TERMS.filter((term) => term.category === category)
}

export function allCategories() {
  return [...GLOSSARY_CATEGORIES]
}

export function allTerms() {
  return [...GLOSSARY_TERMS]
}

/** 将词条正文拆成普通文本与重点文本，供页面和分享图统一渲染 */
export function splitHighlightedText(text, highlights = []) {
  const source = String(text ?? '')
  const phrases = [...new Set((highlights || []).map((item) => String(item).trim()).filter(Boolean))]
    .sort((a, b) => b.length - a.length)
  if (!source || !phrases.length) {
    return source ? [{ text: source, highlighted: false }] : []
  }

  const highlightedPhrases = new Set(phrases.map((item) => item.toLowerCase()))
  const pattern = new RegExp(`(${phrases.map(escapeRegExp).join('|')})`, 'gi')
  return source
    .split(pattern)
    .filter(Boolean)
    .map((part) => ({
      text: part,
      highlighted: highlightedPhrases.has(part.toLowerCase()),
    }))
}

/** 按配置顺序返回有效的相关词条 */
export function getRelatedTerms(termOrKey) {
  const term = typeof termOrKey === 'string' ? findTerm(termOrKey) : termOrKey
  if (!term) return []
  return (term.related || []).map((id) => byId.get(id)).filter(Boolean)
}

export const GLOSSARY_EVENT = 'apex:glossary-open'
