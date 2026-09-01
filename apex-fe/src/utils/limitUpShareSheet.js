/**
 * 连板天梯分享画布：全部关键样式走 inline，避免截图克隆丢失 class CSS
 */

import { shareBrandFooterHtml, shareBrandLockupHtml } from '../brand/identity.js'

export const LIMIT_UP_SHARE_WIDTH = {
  desktop: 1180,
  mobile: 1080,
}

export const LIMIT_UP_CLIPBOARD_MAX_WIDTH = 1280

export function limitUpCaptureScale({
  width,
  height,
  devicePixelRatio = 1,
  intent = 'download',
} = {}) {
  const safeWidth = Math.max(1, Number(width) || 1)
  const safeHeight = Math.max(1, Number(height) || 1)
  const maxEdgeScale = 14000 / Math.max(safeWidth, safeHeight)
  const preferredScale = intent === 'preview'
    ? 1
    : intent === 'clipboard'
      ? LIMIT_UP_CLIPBOARD_MAX_WIDTH / safeWidth
      : Math.max(Number(devicePixelRatio) || 1, 2)
  return Math.max(1, Math.min(preferredScale, maxEdgeScale))
}

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function fmtRate(v) {
  if (v == null || v === '') return ''
  const n = Number(v)
  if (Number.isNaN(n)) return ''
  if (Math.abs(n - Math.round(n)) < 0.05) return `${Math.round(n)}%`
  return `${n.toFixed(1)}%`
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

function badgeHtml(text, bg, mobile) {
  const minW = mobile ? 24 : 14
  const h = mobile ? 24 : 14
  const fs = mobile ? 14 : 8
  return `<i style="display:inline-flex;align-items:center;justify-content:center;box-sizing:border-box;flex-shrink:0;min-width:${minW}px;height:${h}px;padding:0 3px;border-radius:2px;background:${bg};color:#fff;font-size:${fs}px;font-style:normal;font-weight:${mobile ? 600 : 700};line-height:1;">${esc(text)}</i>`
}

/**
 * @param {object} payload
 * @param {string} payload.titleDate
 * @param {string} [payload.tradeDate]
 * @param {string} [payload.activeTheme]
 * @param {number} [payload.totalCount]
 * @param {Array<{theme:string,count:number}>} payload.themes
 * @param {Array} payload.tiers
 * @param {'desktop'|'mobile'} [payload.layout]
 * @returns {HTMLElement}
 */
export function buildLimitUpShareSheet(payload) {
  const {
    titleDate = '',
    tradeDate = '',
    activeTheme = '',
    totalCount = null,
    themes = [],
    tiers = [],
    layout = 'desktop',
  } = payload || {}

  const mobile = layout === 'mobile'
  const width = mobile ? LIMIT_UP_SHARE_WIDTH.mobile : LIMIT_UP_SHARE_WIDTH.desktop
  const pad = mobile ? '48px 40px 72px' : '28px 28px 20px'
  const cardW = mobile ? 170 : 100
  const tierSideW = mobile ? 92 : null
  const cardRowH = mobile ? 116 : 72
  const markSize = mobile ? 60 : 44
  const titleFs = mobile ? 28 : 18
  const tierTitleFs = mobile ? 24 : 15
  const nameFs = mobile ? 22 : 11
  const cardThemeFs = mobile ? 16 : 8
  const themeChipFs = mobile ? 18 : 11
  const cardNameWeight = mobile ? 600 : 700
  const accentWeight = mobile ? 600 : 700
  const mediumWeight = mobile ? 600 : 650

  let total = 0
  for (const tier of tiers) {
    for (const s of tier.stocks || []) {
      if (!s.failed) total += 1
    }
  }
  if (!activeTheme && totalCount != null && Number.isFinite(Number(totalCount))) {
    total = Number(totalCount)
  }

  const root = document.createElement('div')
  root.setAttribute('data-lu-share-sheet', '1')
  root.setAttribute('data-ver', mobile ? 'inline-v13-m' : 'inline-v6')
  root.setAttribute('data-layout', mobile ? 'mobile' : 'desktop')
  root.style.cssText = [
    'box-sizing:border-box',
    `width:${width}px`,
    `padding:${pad}`,
    'background:#ffffff',
    'color:#1d1d1f',
    'font-family:"PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    `font-size:${mobile ? 18 : 12}px`,
    'line-height:1.35',
  ].join(';')

  const themeHtml = (themes || [])
    .slice(0, mobile ? 12 : 16)
    .map((t) => {
      const on = activeTheme && activeTheme === t.theme
      const tone = themeTone(t.theme)
      return `<span style="display:inline-block;flex:0 0 auto;width:max-content;padding:2px 7px;border-radius:999px;border:1px solid ${tone.border};background:${on ? tone.bg : '#fff'};font-size:${themeChipFs}px;line-height:1.4;color:${tone.color};font-weight:${on ? mediumWeight : 400};white-space:nowrap;word-break:keep-all;">${esc(t.theme)}&nbsp;<b style="color:${tone.color};font-size:${Math.max(9, themeChipFs - 1)}px;font-weight:${accentWeight};">${esc(t.count)}</b></span>`
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
          if (!failed && s.yizi) badges.push(badgeHtml('一', '#c45656', mobile))
          if (Number(s.lianban) > 1) {
            badges.push(badgeHtml(String(s.lianban), failed ? '#d1d1d6' : '#409eff', mobile))
          }
          if (!failed && Number(s.breakCount) > 0) badges.push(badgeHtml('炸', '#e6a23c', mobile))
          const pctText = fmtPctChg(s.pctChg)
          const pctN = Number(s.pctChg)
          const pctColor = Number.isNaN(pctN) || pctN === 0
            ? '#86868b'
            : pctN > 0 ? (mobile ? '#c97a7a' : '#c45656') : (mobile ? '#72a57a' : '#3d9a4a')
          const pctHtml = pctText
            ? `<span style="flex:0 0 auto;font-size:${mobile ? 16 : 9}px;font-weight:${accentWeight};color:${pctColor};font-variant-numeric:tabular-nums;white-space:nowrap;line-height:1.2;">${esc(pctText)}</span>`
            : ''
          const meta = []
          if (!failed && s.sealAmount != null) meta.push(`封 ${fmtSealAmount(s.sealAmount)}`)
          if (!failed && s.turnoverRate != null) meta.push(`换 ${fmtRate(s.turnoverRate)}`)
          const cardBorder = failed ? '1px solid #f0f0f2' : '1px solid #ebebef'
          const cardBg = failed ? '#fcfcfd' : '#fff'
          const themeHtmlInner = s.theme
            ? `<span style="flex:1 1 auto;min-width:0;font-size:${cardThemeFs}px;font-weight:${mediumWeight};color:${tone.color};opacity:${mobile ? 0.72 : 1};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.35;">${esc(theme)}</span>`
            : `<span style="flex:1 1 auto;min-width:0;font-size:${cardThemeFs}px;color:#86868b;">-</span>`
          const failX = failed
            ? `<span style="position:absolute;inset:-2px;display:flex;align-items:center;justify-content:center;font-size:${mobile ? 84 : 60}px;font-weight:200;line-height:1;color:rgba(60,60,67,.16);pointer-events:none;font-family:'Helvetica Neue',Arial,sans-serif;">×</span>`
            : ''
          const contentOpacity = failed ? 'opacity:.52;' : ''
          return `<div style="box-sizing:border-box;position:relative;overflow:hidden;width:${cardW}px;height:${cardRowH}px;padding:${mobile ? '8px 10px 7px' : '4px 5px 3px'};border:${cardBorder};border-radius:${mobile ? 8 : 5}px;background:${cardBg};">
            ${failX}
            <div style="position:relative;z-index:1;${contentOpacity}">
            <div style="display:flex;justify-content:space-between;align-items:center;gap:2px;min-height:${mobile ? 17 : 11}px;">
              <span style="font-size:${mobile ? 14 : 8}px;color:#aeaeb2;white-space:nowrap;">${time}</span>
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:${mobile ? 5 : 3}px;min-width:0;margin-top:1px;height:${mobile ? 28 : 16}px;">
              <div style="flex:1 1 auto;min-width:0;font-size:${nameFs}px;font-weight:${cardNameWeight};color:${mobile ? '#111318' : '#1d1d1f'};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:${mobile ? 28 : 16}px;height:${mobile ? 28 : 16}px;">${name}</div>
              <span style="display:inline-flex;flex:0 0 auto;flex-wrap:nowrap;gap:${mobile ? 3 : 2}px;align-items:center;height:${mobile ? 24 : 16}px;">${badges.join('')}</span>
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:3px;min-width:0;margin-top:1px;">
              ${themeHtmlInner}
              ${pctHtml}
            </div>
            ${meta.length ? `<div style="display:flex;gap:${mobile ? 8 : 6}px;margin-top:${mobile ? 3 : 1}px;font-size:${mobile ? 14 : 8}px;color:#aeaeb2;white-space:nowrap;overflow:hidden;line-height:1.2;font-variant-numeric:tabular-nums;">${meta.map((m) => `<span style="flex:0 0 auto;white-space:nowrap;">${esc(m).replace(' ', '&nbsp;')}</span>`).join('')}</div>` : ''}
            </div>
          </div>`
        })
        .join('')

      const promoteLabelText = tier.promoteLabel ? String(tier.promoteLabel) : ''
      const promoteRateText = tier.promoteRate != null ? fmtRate(tier.promoteRate) : ''
      const promoteHtml = mobile
        ? `${promoteLabelText ? `<div style="margin-top:5px;font-size:15px;font-weight:600;color:#3d9a4a;line-height:1.15;white-space:nowrap;">${esc(promoteLabelText)}</div>` : ''}${promoteRateText ? `<div style="font-size:17px;font-weight:650;color:#3d9a4a;line-height:1.15;white-space:nowrap;font-variant-numeric:tabular-nums;">${esc(promoteRateText)}</div>` : ''}`
        : `${promoteLabelText ? `<div style="margin-top:1px;font-size:10px;font-weight:650;color:#3d9a4a;line-height:1.15;white-space:nowrap;">${esc(promoteLabelText)}</div>` : ''}${promoteRateText ? `<div style="font-size:10px;font-weight:650;color:#3d9a4a;line-height:1.15;white-space:nowrap;font-variant-numeric:tabular-nums;">${esc(promoteRateText)}</div>` : ''}`
      const tierSideStyle = mobile
        ? `display:flex;flex-direction:column;justify-content:center;align-items:center;align-self:stretch;box-sizing:border-box;width:${tierSideW}px;min-width:${tierSideW}px;min-height:${cardRowH}px;height:auto;padding:10px 8px 10px 6px;border:0;border-right:3px solid rgba(196,86,86,.3);background:#fffafa;text-align:center;overflow:hidden;`
        : `display:flex;flex-direction:column;justify-content:center;align-items:flex-start;box-sizing:border-box;width:max-content;min-height:${cardRowH}px;height:${cardRowH}px;padding:0;text-align:left;`

      return `<section style="display:grid;grid-template-columns:${mobile ? `${tierSideW}px` : 'max-content'} 1fr;column-gap:${mobile ? 8 : 6}px;align-items:${mobile ? 'stretch' : 'start'};margin-bottom:${mobile ? 7 : 8}px;padding:2px 0 ${mobile ? 7 : 8}px;border-bottom:1px solid #f0f0f2;">
        <aside style="${tierSideStyle}">
          <div style="font-size:${tierTitleFs}px;font-weight:${mobile ? 700 : 800};color:#c45656;line-height:1.15;white-space:nowrap;">${esc(tier.title)}</div>
          ${promoteHtml}
          <div style="margin-top:${mobile ? 5 : 1}px;font-size:${mobile ? 15 : 10}px;color:#aeaeb2;white-space:nowrap;line-height:1.2;">${esc(tier.count ?? tier.stocks?.length ?? 0)}${mobile ? '' : ' '}家</div>
        </aside>
        <div style="display:flex;flex-wrap:wrap;gap:${mobile ? 4 : 5}px;align-content:flex-start;">${cards}</div>
      </section>`
    })
    .join('')

  root.innerHTML = `
    <header style="margin-bottom:${mobile ? 18 : 12}px;padding:${mobile ? '20px 24px 18px' : '12px 14px 10px'};border:1px solid #eee;border-radius:${mobile ? 14 : 12}px;background:linear-gradient(180deg,#fff8f6 0%,#fff 70%);">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px;margin-bottom:8px;">
        <div>
          ${shareBrandLockupHtml({ subtitle: '连板天梯', markSize })}
          <h2 style="margin:8px 0 0;font-size:${titleFs}px;font-weight:${mobile ? 700 : 750};color:#c45656;">${esc(titleDate)} A股 连板天梯</h2>
        </div>
        <span style="font-size:${mobile ? 16 : 11}px;color:#86868b;white-space:nowrap;padding-top:4px;">${esc(total)} 家${activeTheme ? ` · ${esc(activeTheme)}` : ''}</span>
      </div>
      ${themeHtml ? `<div style="display:flex;flex-wrap:wrap;align-items:center;gap:5px;width:100%;">${themeHtml}</div>` : ''}
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
 * @param {number} [hostWidth]
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountShareSheet(sheet, hostWidth) {
  const width = hostWidth || LIMIT_UP_SHARE_WIDTH.desktop
  const host = document.createElement('div')
  host.setAttribute('data-lu-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    `width:${width}px`,
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
