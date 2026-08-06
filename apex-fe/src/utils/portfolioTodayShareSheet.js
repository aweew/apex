/**
 * 多组合今日战绩拼图分享（涨跌幅% + 今日盈亏额 + 仓位占比）
 */

import { shareBrandFooterHtml, shareBrandLockupHtml } from '../brand/identity.js'

export const PORTFOLIO_TODAY_SHARE_WIDTH = 1080

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

function fmtMoney(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  const sign = n > 0 ? '+' : ''
  const abs = Math.abs(n)
  if (abs >= 10000) {
    return `${sign}${(n / 10000).toFixed(2)}万`
  }
  return `${sign}${n.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  })}`
}

function fmtWeight(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return `${n.toFixed(1)}%`
}

function pctColor(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return '#6e6e73'
  return n > 0 ? '#c23a3a' : '#1f7a4d'
}

function pctBg(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'rgba(0,0,0,.04)'
  return n > 0 ? 'rgba(194,58,58,.10)' : 'rgba(31,122,77,.10)'
}

/**
 * @param {{ titleDate: string, portfolios: Array }} opts
 */
export function buildPortfolioTodayShareSheet(opts = {}) {
  const titleDate = opts.titleDate || new Date().toISOString().slice(0, 10)
  const portfolios = Array.isArray(opts.portfolios) ? opts.portfolios : []

  const root = document.createElement('div')
  root.setAttribute('data-portfolio-today-share', '1')
  root.style.cssText = [
    `width:${PORTFOLIO_TODAY_SHARE_WIDTH}px`,
    'box-sizing:border-box',
    'position:relative',
    'overflow:hidden',
    'font-family:"Microsoft YaHei","PingFang SC","Noto Sans SC",sans-serif',
    'color:#1d1d1f',
    'letter-spacing:0',
  ].join(';')

  const cards = portfolios
    .map((pf) => {
      const tops = (pf.topHoldings || []).slice(0, 3)
      const toneVal = pf.todayPct != null ? pf.todayPct : pf.todayPnl
      const topHtml = tops.length
        ? tops
            .map((h, i) => {
              return `<div style="display:grid;grid-template-columns:22px 1fr auto;gap:8px;align-items:center;padding:7px 0;border-top:1px solid rgba(0,0,0,.05);">
            <span style="color:#b0b0b5;font-size:11px;font-weight:700;">${i + 1}</span>
            <div style="min-width:0;">
              <div style="font-size:13px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(h.name || h.code || '')}</div>
              <div style="font-size:11px;color:#86868b;font-variant-numeric:tabular-nums;">${esc(h.code || '')} · 仓 ${esc(fmtWeight(h.weightPct))}</div>
            </div>
            <span style="font-size:13px;font-weight:750;font-variant-numeric:tabular-nums;color:${pctColor(h.pctChg)};">${esc(fmtPct(h.pctChg))}</span>
          </div>`
            })
            .join('')
        : `<div style="padding:10px 0 2px;color:#8e8e93;font-size:12px;">暂无持仓</div>`

      return `<div style="padding:16px 16px 12px;border-radius:16px;background:rgba(255,255,255,.92);border:1px solid rgba(0,0,0,.06);box-sizing:border-box;">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px;">
          <div style="min-width:0;flex:1;">
            <div style="font-size:22px;font-weight:800;letter-spacing:-.02em;line-height:1.25;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(pf.name || '组合')}</div>
            <div style="margin-top:5px;font-size:12px;color:#86868b;">${esc(pf.positionCount || 0)} 只持仓</div>
          </div>
          <div style="text-align:right;flex:0 0 auto;padding:8px 12px;border-radius:14px;background:${pctBg(toneVal)};">
            <div style="font-size:22px;font-weight:800;font-variant-numeric:tabular-nums;color:${pctColor(toneVal)};line-height:1.1;letter-spacing:-.02em;">${esc(fmtPct(pf.todayPct))}</div>
            <div style="margin-top:4px;font-size:14px;font-weight:700;font-variant-numeric:tabular-nums;color:${pctColor(pf.todayPnl != null ? pf.todayPnl : toneVal)};line-height:1.2;">${esc(fmtMoney(pf.todayPnl))}</div>
          </div>
        </div>
        <div style="font-size:11px;font-weight:700;color:#6e6e73;margin-bottom:2px;">前三仓位</div>
        ${topHtml}
      </div>`
    })
    .join('')

  const avg =
    portfolios.length > 0
      ? portfolios.reduce((s, p) => s + (Number(p.todayPct) || 0), 0) / portfolios.length
      : null

  root.innerHTML = `
    <div style="position:absolute;inset:0;pointer-events:none;background:
      radial-gradient(ellipse 70% 50% at 0% 0%, rgba(0,113,227,.10), transparent 55%),
      radial-gradient(ellipse 60% 45% at 100% 100%, rgba(52,199,89,.08), transparent 50%),
      linear-gradient(165deg, #f7f9fc 0%, #f2f4f8 55%, #eef2f7 100%);"></div>
    <div style="position:relative;z-index:1;padding:28px 26px 22px;box-sizing:border-box;">
      <div style="display:flex;justify-content:space-between;align-items:flex-end;gap:14px;margin-bottom:16px;">
        <div>
          ${shareBrandLockupHtml({ subtitle: '组合今日战绩', markSize: 48 })}
        </div>
        <div style="text-align:right;white-space:nowrap;">
          <div style="font-size:14px;font-weight:700;font-variant-numeric:tabular-nums;">${esc(titleDate)}</div>
          <div style="margin-top:6px;display:inline-flex;gap:8px;">
            <span style="padding:3px 9px;border-radius:999px;background:rgba(0,0,0,.05);font-size:12px;font-weight:700;">${esc(portfolios.length)} 组</span>
            <span style="padding:3px 9px;border-radius:999px;background:${pctBg(avg)};color:${pctColor(avg)};font-size:12px;font-weight:700;">均 ${esc(fmtPct(avg))}</span>
          </div>
        </div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
        ${cards || '<div style="grid-column:1/-1;padding:40px;text-align:center;color:#8e8e93;">请先勾选组合</div>'}
      </div>
      ${shareBrandFooterHtml({ note: `${esc(titleDate)} · 仅供研究参考 · 不构成投资建议` })}
    </div>
  `
  return root
}

export function mountPortfolioTodayShareSheet(sheet) {
  const host = document.createElement('div')
  host.style.cssText = 'position:fixed;left:-100000px;top:0;z-index:-1;pointer-events:none;opacity:1;'
  host.appendChild(sheet)
  document.body.appendChild(host)
  return {
    host,
    dispose() {
      host.remove()
    },
  }
}
