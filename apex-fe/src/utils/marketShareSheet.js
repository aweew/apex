/**
 * 行情中心分享画布：inline 样式截图；品牌统一为灵枢 Apex。
 */

import { shareBrandFooterHtml, shareBrandLockupHtml } from '../brand/identity.js'

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function fmtNum(v, digits = 2) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toLocaleString('zh-CN', {
    maximumFractionDigits: digits,
    minimumFractionDigits: digits,
  })
}

function fmtPct(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  const sign = n > 0 ? '+' : ''
  return `${sign}${n.toFixed(2)}%`
}

function pctColor(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return '#5c5c5c'
  return n > 0 ? '#c23a3a' : '#1f7a4d'
}

function dir(v) {
  const n = Number(v)
  if (Number.isNaN(n) || n === 0) return 'flat'
  return n > 0 ? 'up' : 'down'
}

/**
 * @param {object} payload
 * @returns {HTMLElement}
 */
export function buildMarketShareSheet(payload) {
  const {
    titleDate = '',
    message = '',
    stance = '',
    volumeText = '--',
    volumeLabel = '',
    breadth = null,
    limitPair = null,
    indexes = [],
    effectMetrics = [],
    hint = '',
    industries = [],
    concepts = [],
  } = payload || {}

  const root = document.createElement('div')
  root.setAttribute('data-market-share-sheet', '1')
  root.style.cssText = [
    'box-sizing:border-box',
    'width:960px',
    'padding:0',
    'background:#0f1419',
    'color:#f5f5f7',
    'font-family:"Plus Jakarta Sans","PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    'font-size:12px',
    'line-height:1.45',
    'position:relative',
    'overflow:hidden',
  ].join(';')

  const indexHtml = (indexes || []).slice(0, 4).map((row) => {
    const close = row.closePrice ?? row.close
    const d = dir(row.pctChg)
    const accent = d === 'up' ? '#c23a3a' : d === 'down' ? '#1f7a4d' : '#6e6e73'
    return `<div style="box-sizing:border-box;position:relative;padding:16px 16px 14px;border-radius:14px;background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);overflow:hidden;">
      <div style="position:absolute;left:0;top:0;bottom:0;width:3px;background:${accent};"></div>
      <div style="font-size:12px;font-weight:600;color:rgba(245,245,247,.62);letter-spacing:.02em;">${esc(row.name || '--')}</div>
      <div style="margin-top:8px;font-size:26px;font-weight:700;font-variant-numeric:tabular-nums;letter-spacing:-.03em;color:#f5f5f7;">${esc(fmtNum(close))}</div>
      <div style="margin-top:4px;font-size:14px;font-weight:700;font-variant-numeric:tabular-nums;color:${accent};">${esc(fmtPct(row.pctChg))}</div>
    </div>`
  }).join('')

  const breadthText = breadth
    ? `<span style="color:#c23a3a;font-weight:700;">${esc(breadth.up)}</span>
       <span style="color:rgba(245,245,247,.28);"> / </span>
       <span style="color:rgba(245,245,247,.55);font-weight:650;">${esc(breadth.flat)}</span>
       <span style="color:rgba(245,245,247,.28);"> / </span>
       <span style="color:#2bb673;font-weight:700;">${esc(breadth.down)}</span>`
    : '--'

  const limitText = limitPair
    ? `<span style="color:#c23a3a;font-weight:700;">${esc(limitPair.up ?? '--')}</span>
       <span style="color:rgba(245,245,247,.28);"> / </span>
       <span style="color:#2bb673;font-weight:700;">${esc(limitPair.down ?? '--')}</span>`
    : '--'

  const effectHtml = (effectMetrics || []).map((m) => {
    const accent = pctColor(m.value)
    return `<div style="box-sizing:border-box;padding:12px 10px;border-radius:12px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.07);">
      <div style="font-size:11px;font-weight:600;color:rgba(245,245,247,.55);letter-spacing:.02em;">${esc(m.label)}</div>
      <div style="margin-top:6px;font-size:18px;font-weight:720;font-variant-numeric:tabular-nums;letter-spacing:-.02em;color:${accent};">${esc(fmtPct(m.value))}</div>
    </div>`
  }).join('')

  const listBlock = (title, rows) => {
    const list = (rows || []).slice(0, 5)
    if (!list.length) return ''
    const items = list.map((row, i) => `
      <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px 0;${i < list.length - 1 ? 'border-bottom:1px solid rgba(255,255,255,.06);' : ''}">
        <div style="display:flex;align-items:center;gap:8px;min-width:0;">
          <span style="flex:0 0 auto;width:16px;font-size:11px;font-weight:700;color:rgba(245,245,247,.35);font-variant-numeric:tabular-nums;">${i + 1}</span>
          <span style="color:rgba(245,245,247,.88);font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${esc(row.name || '--')}</span>
        </div>
        <span style="flex:0 0 auto;font-weight:700;font-variant-numeric:tabular-nums;color:${pctColor(row.pctChg)};">${esc(fmtPct(row.pctChg))}</span>
      </div>
    `).join('')
    return `<div style="flex:1;min-width:0;padding:14px 16px;border-radius:14px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08);">
      <div style="font-size:12px;font-weight:700;color:rgba(245,245,247,.7);letter-spacing:.04em;margin-bottom:4px;">${esc(title)}</div>
      ${items}
    </div>`
  }

  root.innerHTML = `
    <div style="position:absolute;inset:0;pointer-events:none;background:
      radial-gradient(ellipse 70% 50% at 0% 0%, rgba(0,113,227,.22), transparent 55%),
      radial-gradient(ellipse 55% 45% at 100% 10%, rgba(196,86,86,.12), transparent 50%),
      radial-gradient(ellipse 60% 40% at 80% 100%, rgba(31,122,77,.12), transparent 55%),
      linear-gradient(165deg, #141a22 0%, #0f1419 48%, #0c1015 100%);"></div>
    <div style="position:relative;z-index:1;padding:28px 30px 22px;">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:20px;">
        <div>
          ${shareBrandLockupHtml({ subtitle: '行情中心', theme: 'dark', markSize: 48 })}
          <div style="margin-top:8px;color:rgba(245,245,247,.48);font-size:12px;max-width:560px;">${esc(message || '沪深市场总览 · 赚钱效应 · 板块热力')}</div>
        </div>
        <div style="text-align:right;flex:0 0 auto;">
          <div style="font-weight:650;color:#f5f5f7;font-size:14px;font-variant-numeric:tabular-nums;letter-spacing:.02em;">${esc(titleDate)}</div>
          ${stance ? `<div style="margin-top:8px;display:inline-block;padding:4px 10px;border-radius:999px;background:rgba(0,113,227,.18);border:1px solid rgba(0,113,227,.28);color:#9cc7f5;font-size:11px;font-weight:650;">立场 ${esc(stance)}</div>` : ''}
        </div>
      </div>

      <div style="height:1px;background:linear-gradient(90deg, rgba(0,113,227,.45), rgba(255,255,255,.08) 40%, transparent);margin-bottom:18px;"></div>

      <div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:12px;">
        ${indexHtml || '<div style="grid-column:1/-1;padding:24px;text-align:center;color:rgba(245,245,247,.4);">暂无指数</div>'}
      </div>

      <div style="display:grid;grid-template-columns:1.15fr 1.45fr 0.95fr;gap:10px;margin-bottom:12px;">
        <div style="padding:14px 16px;border-radius:14px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08);">
          <div style="font-size:11px;font-weight:650;color:rgba(245,245,247,.5);letter-spacing:.04em;">三市成交</div>
          <div style="margin-top:8px;font-size:20px;font-weight:720;font-variant-numeric:tabular-nums;letter-spacing:-.02em;color:#f5f5f7;">${esc(volumeText)}</div>
          ${volumeLabel ? `<div style="margin-top:4px;font-size:11px;color:rgba(245,245,247,.45);">${esc(volumeLabel)}</div>` : ''}
        </div>
        <div style="padding:14px 16px;border-radius:14px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08);">
          <div style="font-size:11px;font-weight:650;color:rgba(245,245,247,.5);letter-spacing:.04em;">涨跌家数</div>
          <div style="margin-top:8px;font-size:18px;font-variant-numeric:tabular-nums;">${breadthText}</div>
        </div>
        <div style="padding:14px 16px;border-radius:14px;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.08);">
          <div style="font-size:11px;font-weight:650;color:rgba(245,245,247,.5);letter-spacing:.04em;">涨跌停</div>
          <div style="margin-top:8px;font-size:18px;font-variant-numeric:tabular-nums;">${limitText}</div>
        </div>
      </div>

      ${effectHtml ? `
      <div style="margin-bottom:12px;padding:14px 16px 16px;border-radius:14px;background:linear-gradient(180deg, rgba(255,255,255,.05), rgba(255,255,255,.025));border:1px solid rgba(255,255,255,.09);">
        <div style="display:flex;align-items:baseline;justify-content:space-between;gap:10px;margin-bottom:10px;">
          <span style="font-size:12px;font-weight:700;letter-spacing:.04em;color:rgba(245,245,247,.78);">赚钱效应</span>
          ${hint ? `<span style="font-size:11px;color:rgba(245,245,247,.42);overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:62%;">${esc(hint)}</span>` : ''}
        </div>
        <div style="display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;">
          ${effectHtml}
        </div>
      </div>` : ''}

      ${(industries?.length || concepts?.length) ? `
      <div style="display:flex;gap:10px;margin-bottom:4px;">
        ${listBlock('行业涨幅 TOP', industries)}
        ${listBlock('概念涨幅 TOP', concepts)}
      </div>` : ''}

      ${shareBrandFooterHtml({ theme: 'dark', note: `${esc(titleDate)} · 仅供研究参考 · 不构成投资建议` })}
    </div>
  `
  return root
}

/**
 * @param {HTMLElement} sheet
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountMarketShareSheet(sheet) {
  const host = document.createElement('div')
  host.setAttribute('data-market-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    'width:960px',
    'pointer-events:none',
    'z-index:2147483000',
    'overflow:visible',
    'background:#0f1419',
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
