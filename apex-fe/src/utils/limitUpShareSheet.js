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
  root.setAttribute('data-ver', 'inline-v3')
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
      return `<span style="display:inline-block;flex:0 0 auto;width:max-content;padding:2px 8px;border-radius:999px;border:1px solid ${on ? 'rgba(196,86,86,.45)' : 'rgba(0,0,0,.08)'};background:${on ? 'rgba(196,86,86,.1)' : '#fff'};font-size:11px;line-height:1.4;color:${on ? '#c45656' : '#3a3a3c'};white-space:nowrap;word-break:keep-all;">${esc(t.theme)}&nbsp;<b style="color:#c45656;font-size:10px;font-weight:700;">${esc(t.count)}</b></span>`
    })
    .join('')

  const tiersHtml = (tiers || [])
    .map((tier) => {
      const cards = (tier.stocks || [])
        .map((s) => {
          const time = esc(s.lastSealTime || s.firstSealTime || '--:--')
          const name = esc(s.name || s.code || '-')
          const theme = esc(s.theme || '-')
          const badges = []
          if (Number(s.lianban) > 1) {
            badges.push(`<i style="display:inline-flex;align-items:center;justify-content:center;min-width:12px;height:12px;padding:0 2px;border-radius:2px;background:#409eff;color:#fff;font-size:8px;font-style:normal;font-weight:700;line-height:1;">${esc(s.lianban)}</i>`)
          }
          if (Number(s.breakCount) > 0) {
            badges.push('<i style="display:inline-flex;align-items:center;justify-content:center;min-width:12px;height:12px;padding:0 2px;border-radius:2px;background:#e6a23c;color:#fff;font-size:8px;font-style:normal;font-weight:700;line-height:1;">炸</i>')
          }
          const pctText = fmtPctChg(s.pctChg)
          const pctHtml = pctText
            ? `<span style="flex:0 0 auto;font-size:9px;font-weight:700;color:#c45656;font-variant-numeric:tabular-nums;white-space:nowrap;line-height:1.2;">${esc(pctText)}</span>`
            : ''
          const meta = []
          if (s.sealAmount != null) meta.push(`封 ${fmtSealAmount(s.sealAmount)}`)
          if (s.turnoverRate != null) meta.push(`换 ${fmtRate(s.turnoverRate)}`)
          return `<div style="box-sizing:border-box;width:100px;padding:4px 5px 3px;border:1px solid #ebebef;border-radius:5px;background:#fff;">
            <div style="display:flex;justify-content:space-between;align-items:center;gap:2px;">
              <span style="font-size:8px;color:#aeaeb2;white-space:nowrap;">${time}</span>
              <span style="display:inline-flex;gap:2px;">${badges.join('')}</span>
            </div>
            <div style="margin-top:1px;font-size:11px;font-weight:700;color:#1d1d1f;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.25;">${name}</div>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:3px;min-width:0;margin-top:1px;">
              <div style="flex:1 1 auto;min-width:0;font-size:8px;color:#86868b;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${theme}</div>
              ${pctHtml}
            </div>
            <div style="margin-top:1px;font-size:8px;color:#aeaeb2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;line-height:1.2;">${esc(meta.join(' '))}</div>
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
