/**
 * 涨停复盘分享画布：全部关键样式走 inline，避免截图克隆丢失 class CSS
 */

import { shareBrandFooterHtml, shareBrandLockupHtml } from '../brand/identity.js'

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function fmtRate(v) {
  if (v == null || v === '') return ''
  return `${Number(v).toFixed(1)}%`
}

function fmtPctChg(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return ''
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function fmtSealAmount(v) {
  if (v == null || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Math.abs(n) >= 1e8) return `${(n / 1e8).toFixed(2)}亿`
  if (Math.abs(n) >= 1e4) return `${(n / 1e4).toFixed(0)}万`
  return n.toFixed(0)
}

const THEME_PALETTE = [
  { color: '#c43d4a', bg: 'rgba(196, 61, 74, 0.12)', border: 'rgba(196, 61, 74, 0.32)' },
  { color: '#1f8a4c', bg: 'rgba(31, 138, 76, 0.12)', border: 'rgba(31, 138, 76, 0.30)' },
  { color: '#0a66c2', bg: 'rgba(10, 102, 194, 0.12)', border: 'rgba(10, 102, 194, 0.30)' },
  { color: '#b36b00', bg: 'rgba(179, 107, 0, 0.14)', border: 'rgba(179, 107, 0, 0.32)' },
  { color: '#6b4fbb', bg: 'rgba(107, 79, 187, 0.12)', border: 'rgba(107, 79, 187, 0.30)' },
  { color: '#c45c26', bg: 'rgba(196, 92, 38, 0.12)', border: 'rgba(196, 92, 38, 0.30)' },
  { color: '#0d8a8a', bg: 'rgba(13, 138, 138, 0.12)', border: 'rgba(13, 138, 138, 0.30)' },
  { color: '#a83d7a', bg: 'rgba(168, 61, 122, 0.12)', border: 'rgba(168, 61, 122, 0.30)' },
  { color: '#3d6b9a', bg: 'rgba(61, 107, 154, 0.12)', border: 'rgba(61, 107, 154, 0.30)' },
  { color: '#7a8a1f', bg: 'rgba(122, 138, 31, 0.12)', border: 'rgba(122, 138, 31, 0.30)' },
]

function themeTone(theme) {
  const s = String(theme || '')
  if (!s) return { color: '#86868b', bg: 'rgba(0,0,0,0.04)', border: 'rgba(0,0,0,0.08)' }
  let h = 0
  for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) | 0
  return THEME_PALETTE[Math.abs(h) % THEME_PALETTE.length]
}

function badgeHtml(text, bg) {
  return `<i style="display:inline-flex;align-items:center;justify-content:center;min-width:12px;height:12px;padding:0 2px;border-radius:2px;background:${bg};color:#fff;font-size:8px;font-style:normal;font-weight:700;line-height:1;">${esc(text)}</i>`
}

/**
 * @param {object} payload
 * @param {string} payload.titleDate
 * @param {string} [payload.tradeDate]
 * @param {string} [payload.activeTheme]
 * @param {Array<{theme:string,count:number}>} payload.themes
 * @param {Array} payload.tiers
 * @returns {HTMLElement}
 */
export function buildLimitUpShareSheet(payload) {
  const {
    titleDate = '',
    tradeDate = '',
    activeTheme = '',
    themes = [],
    tiers = [],
  } = payload || {}

  let total = 0
  for (const tier of tiers) total += tier.stocks?.length || 0

  const root = document.createElement('div')
  root.setAttribute('data-lu-share-sheet', '1')
  root.setAttribute('data-ver', 'inline-v4')
  root.style.cssText = [
    'box-sizing:border-box',
    'width:1180px',
    'padding:28px 28px 20px',
    'background:#ffffff',
    'color:#1d1d1f',
    'font-family:"PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    'font-size:12px',
    'line-height:1.35',
  ].join(';')

  const themeHtml = (themes || [])
    .slice(0, 16)
    .map((t) => {
      const on = activeTheme && activeTheme === t.theme
      const tone = themeTone(t.theme)
      return `<span style="display:inline-block;flex:0 0 auto;width:max-content;padding:2px 8px;border-radius:999px;border:1px solid ${tone.border};background:${on ? tone.bg : '#fff'};font-size:11px;line-height:1.4;color:${tone.color};font-weight:${on ? 650 : 400};white-space:nowrap;word-break:keep-all;">${esc(t.theme)}&nbsp;<b style="color:${tone.color};font-size:10px;font-weight:700;">${esc(t.count)}</b></span>`
    })
    .join('')

  const tiersHtml = (tiers || [])
    .map((tier) => {
      const cards = (tier.stocks || [])
        .map((s) => {
          const failed = !!s.failed
          const time = failed ? '' : esc(s.lastSealTime || s.firstSealTime || '--:--')
          const name = esc(s.name || s.code || '-')
          const theme = s.theme || '-'
          const tone = themeTone(s.theme)
          const badges = []
          if (!failed && s.yizi) badges.push(badgeHtml('一', '#c45656'))
          if (Number(s.lianban) > 1) {
            badges.push(badgeHtml(String(s.lianban), failed ? '#d1d1d6' : '#409eff'))
          }
          if (!failed && Number(s.breakCount) > 0) badges.push(badgeHtml('炸', '#e6a23c'))
          const pctText = fmtPctChg(s.pctChg)
          const pctN = Number(s.pctChg)
          const pctColor = Number.isNaN(pctN) || pctN === 0
            ? '#86868b'
            : pctN > 0 ? '#c45656' : '#3d9a4a'
          const pctHtml = pctText
            ? `<span style="flex:0 0 auto;font-size:9px;font-weight:700;color:${pctColor};font-variant-numeric:tabular-nums;white-space:nowrap;line-height:1.2;">${esc(pctText)}</span>`
            : ''
          const meta = []
          if (!failed && s.sealAmount != null) meta.push(`封 ${fmtSealAmount(s.sealAmount)}`)
          if (!failed && s.turnoverRate != null) meta.push(`换 ${fmtRate(s.turnoverRate)}`)
          const cardBorder = failed ? '1px solid #f0f0f2' : '1px solid #ebebef'
          const cardBg = failed ? '#fcfcfd' : '#fff'
          const nameColor = '#1d1d1f'
          const themeHtmlInner = s.theme
            ? `<span style="flex:1 1 auto;min-width:0;font-size:8px;font-weight:650;color:${tone.color};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.35;">${esc(theme)}</span>`
            : `<span style="flex:1 1 auto;min-width:0;font-size:8px;color:#86868b;">-</span>`
          const failX = failed
            ? `<span style="position:absolute;inset:-2px;display:flex;align-items:center;justify-content:center;font-size:60px;font-weight:200;line-height:1;color:rgba(60,60,67,.16);pointer-events:none;font-family:'Helvetica Neue',Arial,sans-serif;">×</span>`
            : ''
          const contentOpacity = failed ? 'opacity:.52;' : ''
          return `<div style="box-sizing:border-box;position:relative;overflow:hidden;width:100px;padding:4px 5px 3px;border:${cardBorder};border-radius:5px;background:${cardBg};">
            ${failX}
            <div style="position:relative;z-index:1;${contentOpacity}">
            <div style="display:flex;justify-content:space-between;align-items:center;gap:2px;min-height:11px;">
              <span style="font-size:8px;color:#aeaeb2;white-space:nowrap;">${time}</span>
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:3px;min-width:0;margin-top:1px;">
              <div style="flex:1 1 auto;min-width:0;font-size:11px;font-weight:700;color:${nameColor};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.25;">${name}</div>
              <span style="display:inline-flex;flex:0 0 auto;gap:2px;">${badges.join('')}</span>
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:3px;min-width:0;margin-top:1px;">
              ${themeHtmlInner}
              ${pctHtml}
            </div>
            ${meta.length ? `<div style="margin-top:1px;font-size:8px;color:#aeaeb2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.2;">${esc(meta.join(' '))}</div>` : ''}
            </div>
          </div>`
        })
        .join('')

      const promoteBits = []
      if (tier.promoteLabel) promoteBits.push(esc(tier.promoteLabel))
      if (tier.promoteRate != null) promoteBits.push(esc(fmtRate(tier.promoteRate)))
      const promote = promoteBits.length
        ? `<div style="margin-top:2px;font-size:10px;font-weight:650;color:#3d9a4a;line-height:1.25;white-space:nowrap;">${promoteBits.join(' ')}</div>`
        : ''

      return `<section style="display:grid;grid-template-columns:58px 1fr;gap:8px;margin-bottom:8px;padding:2px 0 8px;border-bottom:1px solid #f0f0f2;">
        <aside style="padding-top:2px;text-align:left;">
          <div style="font-size:16px;font-weight:800;color:#c45656;line-height:1.15;">${esc(tier.title)}</div>
          ${promote}
          <div style="margin-top:2px;font-size:10px;color:#aeaeb2;">${esc(tier.count ?? tier.stocks?.length ?? 0)} 家</div>
        </aside>
        <div style="display:flex;flex-wrap:wrap;gap:5px;align-content:flex-start;">${cards}</div>
      </section>`
    })
    .join('')

  root.innerHTML = `
    <header style="margin-bottom:12px;padding:12px 14px 10px;border:1px solid #eee;border-radius:12px;background:linear-gradient(180deg,#fff8f6 0%,#fff 70%);">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:8px;">
        <div>
          ${shareBrandLockupHtml({ subtitle: '涨停复盘', markSize: 44 })}
          <h2 style="margin:8px 0 0;font-size:18px;font-weight:750;color:#c45656;">${esc(titleDate)} A股 涨停复盘</h2>
        </div>
        <span style="font-size:11px;color:#86868b;white-space:nowrap;padding-top:4px;">${esc(total)} 家${activeTheme ? ` · ${esc(activeTheme)}` : ''}</span>
      </div>
      ${themeHtml ? `<div style="display:flex;flex-wrap:wrap;align-items:center;gap:6px;width:100%;">${themeHtml}</div>` : ''}
    </header>
    <div>${tiersHtml || '<div style="padding:40px;text-align:center;color:#86868b;">暂无数据</div>'}</div>
    ${shareBrandFooterHtml({ note: `${esc(tradeDate)} · 仅供研究参考 · 不构成投资建议` })}
  `
  return root
}

/** @deprecated 使用 buildLimitUpShareSheet */
export function buildLimitUpShareHtml(payload) {
  return buildLimitUpShareSheet(payload).outerHTML
}

/**
 * 挂载分享画布并返回卸载函数
 * @param {HTMLElement} sheet
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountShareSheet(sheet) {
  const host = document.createElement('div')
  host.setAttribute('data-lu-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    'width:1180px',
    'pointer-events:none',
    'z-index:2147483000',
    'overflow:visible',
    'background:#ffffff',
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
