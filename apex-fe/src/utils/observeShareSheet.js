/**
 * 观察池分享画布：关键样式走 inline，避免截图克隆丢失 class CSS
 */

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function fmtNum(v) {
  if (v == null || v === '') return '--'
  const n = Number(v)
  if (Number.isNaN(n)) return '--'
  return n.toFixed(2)
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
  if (Number.isNaN(n) || n === 0) return '#636366'
  return n > 0 ? '#c45656' : '#3d9a4a'
}

function sideMeta(side) {
  if (side === 'MOOD') return { tag: '情', bg: 'rgba(64,158,255,.12)', fg: '#2f6fed' }
  if (side === 'SELL') return { tag: '卖', bg: 'rgba(61,154,74,.12)', fg: '#2f7d3a' }
  return { tag: '买', bg: 'rgba(196,86,86,.12)', fg: '#c45656' }
}

/**
 * @param {object} payload
 * @param {string} payload.titleDate
 * @param {string} [payload.filterText]
 * @param {{buy:number,mood:number,sell:number,buyReady:number}} payload.stats
 * @param {Array} payload.rows
 * @returns {HTMLElement}
 */
export function buildObserveShareSheet(payload) {
  const {
    titleDate = '',
    filterText = '',
    stats = {},
    rows = [],
  } = payload || {}

  const list = (rows || []).slice(0, 24)
  const root = document.createElement('div')
  root.setAttribute('data-observe-share-sheet', '1')
  root.style.cssText = [
    'box-sizing:border-box',
    'width:920px',
    'padding:26px 24px 18px',
    'background:#ffffff',
    'color:#1d1d1f',
    'font-family:"PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    'font-size:12px',
    'line-height:1.4',
  ].join(';')

  const cardsHtml = list
    .map((row) => {
      const side = sideMeta(row.side)
      const reason = esc((row.reason || '').slice(0, 64) || '—')
      const strategy = esc(row.strategy || '策略')
      const action = esc(row.action || '观察')
      const val = row.valuationLabel ? esc(`估·${row.valuationLabel}`) : ''
      return `<div style="box-sizing:border-box;padding:10px 12px;border:1px solid #ebebef;border-radius:10px;background:#fff;">
        <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:10px;">
          <div style="min-width:0;display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
            <span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:4px;background:${side.bg};color:${side.fg};font-size:11px;font-weight:700;">${side.tag}</span>
            <b style="font-size:14px;font-weight:700;letter-spacing:.02em;">${esc(row.code)}</b>
            <span style="color:#3a3a3c;font-size:13px;">${esc(row.name || '')}</span>
            <span style="padding:1px 6px;border-radius:999px;background:rgba(0,0,0,.05);color:#3a3a3c;font-size:11px;font-weight:600;">${action}</span>
            <span style="color:#636366;font-size:11px;">${strategy}</span>
            ${val ? `<span style="color:#8a6d3b;font-size:11px;">${val}</span>` : ''}
          </div>
          <div style="flex:0 0 auto;text-align:right;">
            <div style="font-size:15px;font-weight:700;font-variant-numeric:tabular-nums;color:${pctColor(row.pctChg)};">${esc(fmtNum(row.latestPrice))}</div>
            <div style="font-size:11px;font-weight:650;font-variant-numeric:tabular-nums;color:${pctColor(row.pctChg)};">${esc(fmtPct(row.pctChg))}</div>
          </div>
        </div>
        <div style="margin-top:6px;color:#636366;font-size:11px;line-height:1.45;">${reason}</div>
        <div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:8px;">
          <div style="padding:6px 8px;border-radius:8px;background:#f7f7f8;">
            <div style="color:#8e8e93;font-size:10px;">${row.side === 'MOOD' ? '现价参考' : '触发'}</div>
            <div style="font-weight:700;font-variant-numeric:tabular-nums;">${esc(fmtNum(row.triggerPrice))} <span style="color:#8e8e93;font-weight:500;font-size:10px;">${esc(fmtPct(row.pctToTrigger))}</span></div>
          </div>
          <div style="padding:6px 8px;border-radius:8px;background:#f7f7f8;">
            <div style="color:#8e8e93;font-size:10px;">${row.side === 'MOOD' ? '退潮' : '止损'}</div>
            <div style="font-weight:700;font-variant-numeric:tabular-nums;">${esc(fmtNum(row.stopLoss))} <span style="color:#8e8e93;font-weight:500;font-size:10px;">${esc(fmtPct(row.pctToStop))}</span></div>
          </div>
          <div style="padding:6px 8px;border-radius:8px;background:#f7f7f8;">
            <div style="color:#8e8e93;font-size:10px;">${row.side === 'MOOD' ? '修复' : '目标'}</div>
            <div style="font-weight:700;font-variant-numeric:tabular-nums;">${esc(fmtNum(row.targetPrice))} <span style="color:#8e8e93;font-weight:500;font-size:10px;">${esc(fmtPct(row.pctToTarget))}</span></div>
          </div>
        </div>
        ${row.statusHint ? `<div style="margin-top:6px;color:#8e8e93;font-size:10px;">${esc(String(row.statusHint).slice(0, 80))}</div>` : ''}
      </div>`
    })
    .join('')

  const moreNote = (rows || []).length > list.length
    ? `<div style="margin-top:8px;color:#8e8e93;font-size:11px;text-align:center;">另有 ${(rows || []).length - list.length} 条未展示，请到观察池查看全部</div>`
    : ''

  root.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:flex-end;gap:12px;margin-bottom:14px;padding-bottom:12px;border-bottom:1px solid #eee;">
      <div>
        <div style="font-size:11px;letter-spacing:.04em;color:#8e8e93;">Apex · Observe</div>
        <div style="margin-top:2px;font-size:22px;font-weight:750;letter-spacing:.02em;">观察池</div>
        <div style="margin-top:4px;color:#636366;font-size:12px;">${esc(filterText || '当前筛选标的')} · 优先处理接近 / 可执行</div>
      </div>
      <div style="text-align:right;color:#636366;font-size:12px;font-variant-numeric:tabular-nums;">
        <div style="font-weight:700;color:#1d1d1f;">${esc(titleDate)}</div>
        <div style="margin-top:2px;">共 ${list.length} 条</div>
      </div>
    </div>
    <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px;">
      <span style="padding:4px 10px;border-radius:999px;background:rgba(196,86,86,.1);color:#c45656;font-size:12px;font-weight:650;">买入 ${esc(stats.buy ?? 0)}</span>
      <span style="padding:4px 10px;border-radius:999px;background:rgba(64,158,255,.1);color:#2f6fed;font-size:12px;font-weight:650;">情绪 ${esc(stats.mood ?? 0)}</span>
      <span style="padding:4px 10px;border-radius:999px;background:rgba(61,154,74,.1);color:#2f7d3a;font-size:12px;font-weight:650;">卖出 ${esc(stats.sell ?? 0)}</span>
      <span style="padding:4px 10px;border-radius:999px;background:rgba(230,162,60,.14);color:#b8821a;font-size:12px;font-weight:650;">优先 ${esc(stats.buyReady ?? 0)}</span>
    </div>
    <div style="display:flex;flex-direction:column;gap:8px;">
      ${cardsHtml || '<div style="padding:28px;text-align:center;color:#8e8e93;">暂无观察标的</div>'}
    </div>
    ${moreNote}
    <div style="display:flex;justify-content:space-between;margin-top:16px;padding-top:10px;border-top:1px solid #eee;color:#8e8e93;font-size:10px;">
      <span>Apex · 观察池</span>
      <span>${esc(titleDate)} · 仅供研究，不构成投资建议</span>
    </div>
  `
  return root
}

/**
 * 挂载分享画布并返回卸载函数
 * @param {HTMLElement} sheet
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountObserveShareSheet(sheet) {
  const host = document.createElement('div')
  host.setAttribute('data-observe-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    'width:920px',
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
