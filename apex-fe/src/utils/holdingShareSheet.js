/**
 * 持仓分享画布：浅色高级风；仓位占比+数量，不含金额；品牌 APEX。
 * 字体刻意用系统中文字体 + 标准字重，避免截图克隆时字形变形。
 * 表格列宽全部固定，保证跨行对齐。
 */

export const HOLDING_SHARE_WIDTH = 1480

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function fmtPct(v, digits = 2) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(digits)}%`
}

function fmtWeight(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return `${n.toFixed(1)}%`
}

function fmtQty(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return Math.round(n).toLocaleString('zh-CN')
}

function pctColor(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return '#6e6e73'
  return n > 0 ? '#c23a3a' : '#1f7a4d'
}

function verdictColor(verdict) {
  const v = String(verdict || '')
  if (v.includes('卖出') || v.includes('减仓')) return '#c43d4a'
  if (v.includes('偏多') || v.includes('继续')) return '#1f8a4c'
  if (v.includes('谨慎') || v.includes('不足')) return '#b36b00'
  return '#515154'
}

function valuationColor(level) {
  if (level === 'UNDERVALUED' || level === 'SLIGHTLY_CHEAP') return '#1f8a4c'
  if (level === 'OVERVALUED' || level === 'SLIGHTLY_EXPENSIVE') return '#c43d4a'
  return '#6e6e73'
}

const FONT = '"Microsoft YaHei","PingFang SC","Noto Sans SC",sans-serif'

/** # 代码 名称 今日 数量 仓位 题材 技术 估值 评价 建议 — 题材列加宽，避免「其他电…」 */
const COL =
  '28px 64px 120px 58px 68px 64px 108px 156px 56px 76px minmax(240px,1fr)'
const COL_GAP = '14px'
const ROW_PAD = '12px 16px'

function cellBase(extra = '') {
  return `min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;letter-spacing:0;${extra}`
}

/** 名称完整显示，最多两行，不用省略号截断 */
function nameCellStyle() {
  return [
    'min-width:0',
    'overflow:visible',
    'white-space:normal',
    'word-break:break-word',
    'line-height:1.35',
    'color:#3a3a3c',
    'letter-spacing:0',
  ].join(';')
}

function wrapCellStyle(empty, extra = '') {
  return [
    'min-width:0',
    'overflow:hidden',
    'display:-webkit-box',
    '-webkit-box-orient:vertical',
    '-webkit-line-clamp:2',
    'white-space:normal',
    'word-break:break-word',
    'line-height:1.35',
    'font-size:11px',
    'letter-spacing:0',
    empty ? 'color:#b0b0b5' : 'color:#515154',
    extra,
  ].filter(Boolean).join(';')
}

/**
 * @param {object} payload
 * @returns {HTMLElement}
 */
export function buildHoldingShareSheet(payload) {
  const {
    titleDate = '',
    count = 0,
    todayPct = null,
    themeHitCount = 0,
    themes = [],
    otherPct = 0,
    rows = [],
  } = payload || {}

  const list = (rows || []).slice(0, 24)
  const themeList = [...(themes || [])]
  const other = Number(otherPct) || 0
  if (other > 0.005) {
    themeList.push({
      name: '其他',
      short: '其他',
      pct: other,
      color: '#8e8e93',
      bg: 'rgba(0,0,0,.05)',
    })
  }

  const root = document.createElement('div')
  root.setAttribute('data-holding-share-sheet', '1')
  root.style.cssText = [
    'box-sizing:border-box',
    `width:${HOLDING_SHARE_WIDTH}px`,
    'padding:0',
    'background:#f7f4ee',
    'color:#1d1d1f',
    `font-family:${FONT}`,
    'font-size:12px',
    'font-weight:400',
    'line-height:1.4',
    'letter-spacing:0',
    'position:relative',
    'overflow:visible',
    '-webkit-font-smoothing:antialiased',
  ].join(';')

  const distSegs = themeList.map((t) => {
    const pct = Math.max(Number(t.pct || 0) * 100, 0)
    return `<i style="display:block;height:100%;width:${pct.toFixed(2)}%;background:${t.color || '#0071e3'};"></i>`
  }).join('')

  const distChips = themeList.map((t) => {
    const pct = (Number(t.pct || 0) * 100).toFixed(1)
    const label = t.short || t.name
    return `<span style="display:inline-flex;align-items:center;gap:6px;padding:5px 10px;border-radius:999px;background:${t.bg || 'rgba(0,0,0,.04)'};border:1px solid rgba(0,0,0,.06);white-space:nowrap;letter-spacing:0;">
      <i style="width:8px;height:8px;border-radius:50%;background:${t.color || '#0071e3'};flex:0 0 auto;"></i>
      <span style="font-size:12px;font-weight:700;color:${t.color || '#3a3a3c'};letter-spacing:0;">${esc(label)}</span>
      <span style="font-size:12px;font-weight:700;font-variant-numeric:tabular-nums;color:#1d1d1f;letter-spacing:0;">${pct}%</span>
    </span>`
  }).join('')

  const headHtml = `<div style="display:grid;grid-template-columns:${COL};column-gap:${COL_GAP};align-items:center;padding:${ROW_PAD};background:#f3f1ec;color:#8e8e93;font-size:11px;font-weight:700;white-space:nowrap;border-bottom:1px solid rgba(0,0,0,.06);letter-spacing:0;box-sizing:border-box;">
    <span>#</span>
    <span>代码</span>
    <span>名称</span>
    <span style="text-align:right;">今日</span>
    <span style="text-align:right;">数量</span>
    <span style="text-align:right;">仓位</span>
    <span>题材</span>
    <span>技术</span>
    <span>估值</span>
    <span>评价</span>
    <span>建议</span>
  </div>`

  const rowHtml = list.map((row, i) => {
    const w = Number(row.weightPct || 0)
    // 题材完整展示，不用省略号；列宽已按 4～5 字行业名留足
    const themePill = row.theme
      ? `<span style="display:inline-block;padding:3px 9px;border-radius:999px;background:${row.themeBg || 'rgba(0,0,0,.05)'};color:${row.themeColor || '#515154'};border:1px solid ${row.themeBorder || 'rgba(0,0,0,.08)'};font-size:11px;font-weight:700;white-space:nowrap;letter-spacing:0;line-height:1.4;box-sizing:border-box;">${esc(row.theme)}</span>`
      : `<span style="color:#b0b0b5;font-size:11px;">—</span>`

    const techHits = Array.isArray(row.techHits) ? row.techHits.filter(Boolean).slice(0, 4) : []
    let techHtml
    if (techHits.length) {
      techHtml = `<div style="display:flex;flex-wrap:wrap;gap:4px;min-width:0;">${techHits.map((label) =>
        `<span style="display:inline-block;padding:2px 7px;border-radius:999px;background:rgba(0,113,227,.08);color:#0071e3;border:1px solid rgba(0,113,227,.16);font-size:10px;font-weight:700;line-height:1.35;white-space:nowrap;letter-spacing:0;">${esc(label)}</span>`
      ).join('')}</div>`
    } else {
      const techText = String(row.tech || '').trim()
      techHtml = techText
        ? `<span style="${wrapCellStyle(false)}">${esc(techText)}</span>`
        : `<span style="color:#b0b0b5;font-size:11px;">—</span>`
    }

    const valText = row.valuation || '—'
    const verdictText = String(row.verdict || '').trim() || '—'
    const adviceRaw = String(row.advice || '').trim()
    const adviceText = adviceRaw || '—'
    const zebra = i % 2 === 0 ? 'background:#ffffff;' : 'background:#faf8f4;'
    return `<div style="display:grid;grid-template-columns:${COL};column-gap:${COL_GAP};align-items:center;padding:${ROW_PAD};min-height:48px;${zebra}border-bottom:1px solid rgba(0,0,0,.05);letter-spacing:0;box-sizing:border-box;">
      <span style="${cellBase('color:#b0b0b5;font-weight:700;font-variant-numeric:tabular-nums;')}">${i + 1}</span>
      <span style="${cellBase('font-weight:700;font-variant-numeric:tabular-nums;color:#1d1d1f;')}">${esc(row.code)}</span>
      <span style="${nameCellStyle()}">${esc(row.name || '')}</span>
      <span style="${cellBase(`text-align:right;font-weight:700;font-variant-numeric:tabular-nums;color:${pctColor(row.pctChg)};`)}">${esc(fmtPct(row.pctChg))}</span>
      <span style="${cellBase('text-align:right;font-variant-numeric:tabular-nums;font-weight:700;color:#1d1d1f;')}">${esc(fmtQty(row.quantity))}</span>
      <div style="min-width:0;padding-right:4px;">
        <div style="text-align:right;font-weight:700;font-variant-numeric:tabular-nums;color:#1d1d1f;white-space:nowrap;letter-spacing:0;line-height:1.3;">${esc(fmtWeight(row.weightPct))}</div>
        <div style="margin-top:5px;height:4px;border-radius:999px;background:rgba(0,0,0,.06);overflow:hidden;">
          <i style="display:block;height:100%;width:${Math.min(Math.max(w, 0), 100)}%;background:${row.themeColor || '#0071e3'};border-radius:999px;"></i>
        </div>
      </div>
      <div style="min-width:0;overflow:visible;padding:0 2px;">${themePill}</div>
      <div style="min-width:0;">${techHtml}</div>
      <span style="${cellBase(`font-weight:700;font-size:11px;color:${valText === '—' ? '#b0b0b5' : valuationColor(row.valuationLevel)};`)}">${esc(valText)}</span>
      <span style="${cellBase(`font-weight:700;font-size:11px;color:${verdictText === '—' ? '#b0b0b5' : verdictColor(row.verdict)};`)}">${esc(verdictText)}</span>
      <span style="${wrapCellStyle(!adviceRaw)}">${esc(adviceText)}</span>
    </div>`
  }).join('')

  const moreNote = (rows || []).length > list.length
    ? `<div style="padding:10px;text-align:center;color:#8e8e93;font-size:11px;white-space:nowrap;letter-spacing:0;">另有 ${(rows || []).length - list.length} 只未展示</div>`
    : ''

  root.innerHTML = `
    <div style="position:absolute;inset:0;pointer-events:none;background:
      radial-gradient(ellipse 80% 55% at 0% 0%, rgba(180,120,60,.10), transparent 55%),
      radial-gradient(ellipse 70% 50% at 100% 100%, rgba(0,113,227,.06), transparent 50%),
      linear-gradient(165deg, #faf7f1 0%, #f3efe8 50%, #ebeff5 100%);"></div>
    <div style="position:relative;z-index:1;padding:28px 26px 20px;letter-spacing:0;box-sizing:border-box;">
      <div style="display:flex;justify-content:space-between;align-items:flex-end;gap:16px;margin-bottom:14px;">
        <div style="min-width:0;">
          <div style="display:flex;align-items:center;gap:10px;white-space:nowrap;">
            <span style="font-size:26px;font-weight:700;letter-spacing:0.08em;line-height:1;color:#1d1d1f;font-family:Arial,'Microsoft YaHei',sans-serif;">APEX</span>
            <span style="width:1px;height:14px;background:rgba(0,0,0,.14);"></span>
            <span style="font-size:13px;font-weight:700;color:#6e6e73;">真实持仓</span>
          </div>
          <div style="margin-top:8px;color:#86868b;font-size:12px;white-space:nowrap;">仓位 · 题材 · 技术/估值 · 评价建议 · 不含金额</div>
        </div>
        <div style="text-align:right;flex:0 0 auto;white-space:nowrap;">
          <div style="font-weight:700;color:#1d1d1f;font-size:14px;font-variant-numeric:tabular-nums;">${esc(titleDate)}</div>
          <div style="margin-top:8px;display:inline-flex;gap:8px;align-items:center;">
            <span style="padding:4px 10px;border-radius:999px;background:rgba(0,0,0,.05);font-size:12px;font-weight:700;">${esc(count)} 只</span>
            <span style="padding:4px 10px;border-radius:999px;background:rgba(0,113,227,.10);color:#0071e3;font-size:12px;font-weight:700;">题材命中 ${esc(themeHitCount)}</span>
            ${todayPct != null ? `<span style="padding:4px 10px;border-radius:999px;background:rgba(0,0,0,.05);font-size:12px;font-weight:700;color:${pctColor(todayPct)};">今日 ${esc(fmtPct(todayPct))}</span>` : ''}
          </div>
        </div>
      </div>

      <div style="margin-bottom:12px;padding:14px 16px;border-radius:14px;background:rgba(255,255,255,.88);border:1px solid rgba(0,0,0,.06);box-sizing:border-box;">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px;white-space:nowrap;">
          <span style="font-size:13px;font-weight:700;color:#1d1d1f;">整体持仓分布</span>
          <span style="font-size:11px;color:#86868b;">按市值权重 · 与持仓题材列一致</span>
        </div>
        <div style="display:flex;width:100%;height:10px;border-radius:999px;overflow:hidden;background:rgba(0,0,0,.06);">
          ${distSegs || '<i style="display:block;width:100%;height:100%;background:#d1d1d6;"></i>'}
        </div>
        <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:12px;">
          ${distChips || '<span style="color:#8e8e93;font-size:12px;">暂无分布数据</span>'}
        </div>
      </div>

      <div style="border-radius:14px;overflow:hidden;border:1px solid rgba(0,0,0,.06);background:rgba(255,255,255,.88);">
        ${headHtml}
        ${rowHtml || '<div style="padding:28px;text-align:center;color:#8e8e93;">暂无持仓</div>'}
        ${moreNote}
      </div>

      <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:12px;padding-top:10px;border-top:1px solid rgba(0,0,0,.08);white-space:nowrap;">
        <div style="display:flex;align-items:center;gap:8px;">
          <span style="font-size:13px;font-weight:700;letter-spacing:0.08em;color:#1d1d1f;font-family:Arial,'Microsoft YaHei',sans-serif;">APEX</span>
          <span style="font-size:11px;color:#8e8e93;">本地量化台</span>
        </div>
        <span style="font-size:11px;color:#8e8e93;">${esc(titleDate)} · 仅展示占比与研判 · 不构成投资建议</span>
      </div>
    </div>
  `
  return root
}

/**
 * @param {HTMLElement} sheet
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountHoldingShareSheet(sheet) {
  const host = document.createElement('div')
  host.setAttribute('data-holding-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    `width:${HOLDING_SHARE_WIDTH}px`,
    'pointer-events:none',
    'z-index:2147483000',
    'overflow:visible',
    'background:#f7f4ee',
  ].join(';')
  host.appendChild(sheet)
  document.body.appendChild(host)
  void host.offsetHeight
  return {
    host,
    sheet,
    dispose() {
      host.remove()
    },
  }
}
