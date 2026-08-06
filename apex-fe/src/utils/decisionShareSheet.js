/**
 * 智能决策分享画布：市场立场 + 买卖清单（inline 样式，便于截图）
 */

import { shareBrandFooterHtml, shareBrandLockupHtml } from '../brand/identity.js'
import { isOutcomeBoard } from './hotTheme.js'

export const DECISION_SHARE_WIDTH = 960

/** A 股行情色：截图里用高饱和，避免灰粉/墨绿发脏 */
const C_UP = '#E93030'
const C_DOWN = '#00A870'
const C_UP_SOFT = 'rgba(233,48,48,.12)'
const C_DOWN_SOFT = 'rgba(0,168,112,.12)'
const C_INK = '#1d1d1f'
const C_MUTED = '#636366'

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function fmtPct(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  if (Math.abs(n) <= 1) return `${(n * 100).toFixed(1)}%`
  return `${n.toFixed(1)}%`
}

function fmtScore(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toFixed(1)
}

function stanceStyle(stance) {
  // 深色墨底 + 侧边色条，避免粉红/淡彩「AI 胶囊」感
  if (stance === '进攻') {
    return {
      bg: '#1c1c1e',
      fg: '#f5f5f7',
      muted: 'rgba(245,245,247,.62)',
      border: 'rgba(0,0,0,.2)',
      accent: C_UP,
    }
  }
  if (stance === '防守') {
    return {
      bg: '#1c1c1e',
      fg: '#f5f5f7',
      muted: 'rgba(245,245,247,.62)',
      border: 'rgba(0,0,0,.2)',
      accent: '#2F6FED',
    }
  }
  return {
    bg: '#ffffff',
    fg: C_INK,
    muted: '#86868b',
    border: 'rgba(0,0,0,.1)',
    accent: '#8e8e93',
  }
}

function scoreColor(v) {
  const n = Number(v)
  if (Number.isNaN(n)) return C_MUTED
  if (n >= 75) return C_UP
  if (n >= 60) return '#C47F17'
  return C_MUTED
}

/**
 * @param {object} payload
 * @returns {HTMLElement}
 */
export function buildDecisionShareSheet(payload = {}) {
  const {
    titleDate = '',
    groupName = '',
    stance = '均衡',
    stanceScore = null,
    stanceReason = '',
    positionAdvice = '',
    hotThemes = [],
    buyCount = 0,
    sellCount = 0,
    holdCount = 0,
    executableCount = 0,
    aiSummary = '',
    aiStance = '',
    aiModel = '',
    buys = [],
    sells = [],
  } = payload

  const root = document.createElement('div')
  root.setAttribute('data-decision-share-sheet', '1')
  root.style.cssText = [
    `width:${DECISION_SHARE_WIDTH}px`,
    'box-sizing:border-box',
    'position:relative',
    'overflow:visible',
    'font-family:"Plus Jakarta Sans","PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    'color:#1d1d1f',
    'letter-spacing:0',
  ].join(';')

  const ss = stanceStyle(stance)
  const buyList = (buys || []).slice(0, 10)
  const sellList = (sells || []).slice(0, 8)

  const themeHtml = (hotThemes || [])
    .filter((t) => {
      const name = typeof t === 'string' ? t : (t?.name || t?.label || '')
      return name && !isOutcomeBoard(name)
    })
    .map((t) => {
      const name = typeof t === 'string' ? t : (t.name || t.label || '')
      const sign = typeof t === 'object' && t != null ? (t.sign || '') : ''
      const abs = typeof t === 'object' && t != null ? (t.abs || '') : ''
      const dir = typeof t === 'object' && t != null ? (t.pctDir || '') : ''
      const pctColor = dir === 'up' ? C_UP : dir === 'down' ? C_DOWN : C_MUTED
      // 截图引擎对 transform 支持差：用 table-cell + vertical-align 做光学对齐
      const pctHtml = abs
        ? `<span style="display:inline-table;color:${pctColor};font-weight:700;font-variant-numeric:tabular-nums;font-feature-settings:'tnum' 1;letter-spacing:0;line-height:1;white-space:nowrap;vertical-align:middle;">${sign ? `<span style="display:table-cell;vertical-align:middle;padding-bottom:2px;line-height:1;font-size:12px;">${esc(sign)}</span>` : ''}<span style="display:table-cell;vertical-align:middle;line-height:1;font-size:12px;">${esc(abs)}%</span></span>`
        : ''
      return `<span style="display:inline-flex;align-items:center;gap:6px;padding:4px 10px;border-radius:8px;background:#fff;border:1px solid rgba(0,0,0,.06);color:#3a3a3c;font-size:12px;font-weight:600;white-space:nowrap;"><span style="color:#3a3a3c;">${esc(name)}</span>${pctHtml}</span>`
    })
    .join('')

  const pill = (text, tone = 'neutral') => {
    const tones = {
      neutral: { bg: 'rgba(0,0,0,.04)', fg: '#636366' },
      warm: { bg: 'rgba(196,127,23,.12)', fg: '#A66B0E' },
      danger: { bg: C_UP_SOFT, fg: C_UP },
      ok: { bg: C_DOWN_SOFT, fg: C_DOWN },
    }
    const t = tones[tone] || tones.neutral
    return `<span style="display:inline-block;padding:2px 8px;border-radius:999px;background:${t.bg};color:${t.fg};font-size:11px;font-weight:600;line-height:1.45;white-space:nowrap;">${esc(text)}</span>`
  }

  const buyCards = buyList
    .map((row, i) => {
      const strategies = Array.isArray(row.strategies) && row.strategies.length
        ? row.strategies.join('+')
        : row.strategyId || '-'
      const reason = String(row.reason || row.scoreExplain || '').trim()
      const flagList = Array.isArray(row.riskFlags) ? row.riskFlags.filter(Boolean) : []
      const tags = []
      if (strategies && strategies !== '-') tags.push(pill(strategies, 'neutral'))
      if (row.mainlineMatch) tags.push(pill(`主线${row.mainlineName || ''}`, 'warm'))
      if (row.valuationLabel) tags.push(pill(`估·${row.valuationLabel}`, 'warm'))
      if (row.executableHint) tags.push(pill('可执行', 'danger'))
      for (const f of flagList) tags.push(pill(f, 'warm'))
      return `<div style="box-sizing:border-box;padding:12px 14px;border:1px solid #ebebef;border-radius:12px;background:#fff;">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;">
          <div style="min-width:0;flex:1;">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span style="flex:0 0 auto;width:16px;color:#b0b0b5;font-size:12px;font-weight:700;font-variant-numeric:tabular-nums;line-height:1;">${i + 1}</span>
              <b style="font-size:15px;font-weight:750;letter-spacing:.02em;white-space:nowrap;line-height:1.2;">${esc(row.code)}</b>
              <span style="color:#3a3a3c;font-size:13px;font-weight:600;white-space:nowrap;line-height:1.2;">${esc(row.name || '')}</span>
            </div>
            ${tags.length ? `<div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;padding-left:24px;">${tags.join('')}</div>` : ''}
          </div>
          <div style="flex:0 0 auto;min-width:56px;text-align:right;">
            <div style="font-size:17px;font-weight:780;font-variant-numeric:tabular-nums;color:${scoreColor(row.score)};line-height:1.15;">${esc(fmtScore(row.score))}</div>
            <div style="margin-top:3px;font-size:11px;color:#8e8e93;white-space:nowrap;line-height:1.2;">仓 ${esc(fmtPct(row.suggestedWeight))}</div>
          </div>
        </div>
        ${reason ? `<div style="margin-top:10px;padding-top:10px;border-top:1px solid #f0f0f2;color:#636366;font-size:12px;line-height:1.55;word-break:break-word;white-space:normal;">${esc(reason)}</div>` : ''}
      </div>`
    })
    .join('')

  const sellCards = sellList
    .map((row) => {
      const exitText = String(row.exitRule || row.reason || '').trim()
      return `<div style="padding:10px 0;border-bottom:1px solid rgba(0,0,0,.05);">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;">
          <div style="min-width:0;flex:1;display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
            <b style="font-size:13px;white-space:nowrap;">${esc(row.code)}</b>
            <span style="color:#3a3a3c;white-space:nowrap;">${esc(row.name || '')}</span>
            <span style="color:#86868b;font-size:11px;white-space:nowrap;">${esc(row.strategyId || '')}</span>
          </div>
          <div style="flex:0 0 auto;font-weight:750;font-variant-numeric:tabular-nums;color:${C_DOWN};white-space:nowrap;">${esc(fmtScore(row.score))}</div>
        </div>
        ${exitText ? `<div style="margin-top:6px;font-size:12px;line-height:1.5;color:#636366;word-break:break-word;white-space:normal;">${esc(exitText)}</div>` : ''}
      </div>`
    })
    .join('')

  const buyMore =
    (buys || []).length > buyList.length
      ? `<div style="margin-top:-6px;margin-bottom:12px;color:#8e8e93;font-size:11px;text-align:center;">另有 ${(buys || []).length - buyList.length} 条买入未展示</div>`
      : ''
  const sellMore =
    (sells || []).length > sellList.length
      ? `<div style="margin-top:6px;color:#8e8e93;font-size:11px;text-align:center;">另有 ${(sells || []).length - sellList.length} 条卖出未展示</div>`
      : ''

  const aiBlock = aiSummary
    ? `<div style="margin-bottom:14px;padding:14px 16px;border-radius:14px;background:linear-gradient(165deg, rgba(0,113,227,.06), rgba(0,113,227,.02));border:1px solid rgba(0,113,227,.12);">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:8px;flex-wrap:wrap;">
          <span style="flex:0 0 auto;font-size:12px;font-weight:750;letter-spacing:.04em;color:#0058b0;white-space:nowrap;">AI&nbsp;详细总结</span>
          <div style="display:inline-flex;align-items:center;gap:6px;flex-wrap:wrap;justify-content:flex-end;">
            ${aiModel ? `<span style="padding:2px 8px;border-radius:999px;background:rgba(0,0,0,.05);color:#3a3a3c;font-size:11px;font-weight:650;white-space:nowrap;">${esc(aiModel)}</span>` : ''}
            ${aiStance ? `<span style="padding:2px 8px;border-radius:999px;background:rgba(0,113,227,.1);color:#0058b0;font-size:11px;font-weight:650;white-space:nowrap;">${esc(aiStance)}</span>` : ''}
          </div>
        </div>
        <div style="font-size:13px;line-height:1.65;color:#3a3a3c;word-break:break-word;white-space:pre-wrap;">${esc(String(aiSummary).trim())}</div>
      </div>`
    : ''

  root.innerHTML = `
    <div style="position:absolute;inset:0;pointer-events:none;background:
      radial-gradient(ellipse 65% 45% at 0% 0%, rgba(28,28,30,.06), transparent 55%),
      radial-gradient(ellipse 50% 40% at 100% 0%, rgba(61,125,214,.06), transparent 50%),
      linear-gradient(165deg, #f6f7f9 0%, #f3f4f6 55%, #eef0f3 100%);"></div>
    <div style="position:relative;z-index:1;padding:28px 28px 22px;box-sizing:border-box;">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:10px;">
        <div style="min-width:0;flex:1;">
          ${shareBrandLockupHtml({ subtitle: '智能决策', markSize: 48 })}
        </div>
        <div style="text-align:right;flex:0 0 auto;">
          <div style="display:inline-flex;align-items:stretch;overflow:hidden;border-radius:10px;background:${ss.bg};border:1px solid ${ss.border};box-shadow:0 1px 2px rgba(0,0,0,.06);">
            <div style="width:4px;flex:0 0 auto;background:${ss.accent};"></div>
            <div style="display:inline-flex;align-items:center;gap:14px;padding:10px 14px 10px 12px;">
              <div style="text-align:left;">
                <div style="font-size:10px;font-weight:650;letter-spacing:.08em;color:${ss.muted};white-space:nowrap;">市场立场</div>
                <div style="margin-top:3px;font-size:18px;font-weight:780;color:${ss.fg};letter-spacing:.04em;white-space:nowrap;line-height:1.1;">${esc(stance || '均衡')}</div>
              </div>
              <div style="width:1px;align-self:stretch;background:${stance === '均衡' ? 'rgba(0,0,0,.08)' : 'rgba(255,255,255,.18)'};"></div>
              <div style="text-align:right;min-width:44px;">
                <div style="font-size:22px;font-weight:800;font-variant-numeric:tabular-nums;color:${ss.fg};line-height:1;">${esc(stanceScore ?? '--')}</div>
                <div style="margin-top:2px;font-size:10px;color:${ss.muted};letter-spacing:.04em;">/100</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div style="margin-bottom:14px;color:#636366;font-size:12px;line-height:1.5;white-space:nowrap;">
        ${esc(titleDate)}${groupName ? `&nbsp;·&nbsp;${esc(groupName)}` : ''}&nbsp;·&nbsp;买&nbsp;${esc(buyCount)}&nbsp;/&nbsp;卖&nbsp;${esc(sellCount)}&nbsp;/&nbsp;持有&nbsp;${esc(holdCount)}
      </div>

      ${(stanceReason || positionAdvice || themeHtml) ? `
      <div style="margin-bottom:14px;padding:12px 14px;border-radius:12px;background:rgba(255,255,255,.88);border:1px solid rgba(0,0,0,.06);">
        ${stanceReason ? `<div style="font-size:13px;line-height:1.55;color:#3a3a3c;word-break:break-word;white-space:normal;">${esc(String(stanceReason).trim())}</div>` : ''}
        ${positionAdvice ? `<div style="margin-top:6px;font-size:12px;font-weight:650;color:#1d1d1f;word-break:break-word;white-space:normal;">${esc(String(positionAdvice).trim())}</div>` : ''}
        ${themeHtml ? `<div style="display:flex;flex-wrap:wrap;gap:6px;margin-top:10px;">${themeHtml}</div>` : ''}
      </div>` : ''}

      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px;">
        <span style="padding:4px 10px;border-radius:999px;background:${C_UP_SOFT};color:${C_UP};font-size:12px;font-weight:700;white-space:nowrap;">买入 ${esc(buyCount)}</span>
        <span style="padding:4px 10px;border-radius:999px;background:${C_DOWN_SOFT};color:${C_DOWN};font-size:12px;font-weight:700;white-space:nowrap;">卖出 ${esc(sellCount)}</span>
        <span style="padding:4px 10px;border-radius:999px;background:rgba(0,0,0,.05);color:#3a3a3c;font-size:12px;font-weight:650;white-space:nowrap;">持有 ${esc(holdCount)}</span>
        <span style="padding:4px 10px;border-radius:999px;background:rgba(196,127,23,.14);color:#A66B0E;font-size:12px;font-weight:650;white-space:nowrap;">可执行 ${esc(executableCount)}</span>
      </div>

      ${aiBlock}

      <div style="display:flex;align-items:baseline;justify-content:space-between;gap:12px;margin:0 0 8px;">
        <div style="font-size:13px;font-weight:750;color:#1d1d1f;">建议买入</div>
        <div style="font-size:11px;color:#8e8e93;white-space:nowrap;">${esc(buyList.length)} / ${esc(buyCount || (buys || []).length || 0)}</div>
      </div>
      <div style="display:flex;flex-direction:column;gap:8px;margin-bottom:14px;">
        ${buyCards || '<div style="padding:24px;text-align:center;color:#8e8e93;border:1px dashed #e5e5ea;border-radius:12px;">暂无买入机会</div>'}
      </div>
      ${buyMore}

      ${sellList.length ? `
      <div style="margin:16px 0 6px;font-size:13px;font-weight:750;color:#1d1d1f;">建议卖出</div>
      <div style="padding:4px 14px 8px;border-radius:12px;background:rgba(255,255,255,.88);border:1px solid rgba(0,0,0,.06);">
        ${sellCards}
      </div>
      ${sellMore}` : ''}

      ${shareBrandFooterHtml({ note: `${esc(titleDate)} · 仅供研究参考 · 不构成投资建议` })}
    </div>
  `
  return root
}

/**
 * @param {HTMLElement} sheet
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountDecisionShareSheet(sheet) {
  const host = document.createElement('div')
  host.setAttribute('data-decision-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    `width:${DECISION_SHARE_WIDTH}px`,
    'pointer-events:none',
    'z-index:2147483000',
    'overflow:visible',
    'background:#f7f9fc',
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
