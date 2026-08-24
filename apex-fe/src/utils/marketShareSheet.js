/**
 * 行情中心分享画布：inline 样式截图；品牌统一为灵极 Apex。
 */

import * as echarts from 'echarts'
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

function mixHex(left, right, ratio) {
  const leftValue = parseInt(left.slice(1), 16)
  const rightValue = parseInt(right.slice(1), 16)
  const red = Math.round(((leftValue >> 16) & 255) + ((((rightValue >> 16) & 255) - ((leftValue >> 16) & 255)) * ratio))
  const green = Math.round(((leftValue >> 8) & 255) + ((((rightValue >> 8) & 255) - ((leftValue >> 8) & 255)) * ratio))
  const blue = Math.round((leftValue & 255) + (((rightValue & 255) - (leftValue & 255)) * ratio))
  return `#${((1 << 24) + (red << 16) + (green << 8) + blue).toString(16).slice(1)}`
}

function heatColor(value) {
  const pctChg = Number(value)
  if (!Number.isFinite(pctChg) || Math.abs(pctChg) < 0.08) return '#dce3eb'
  const intensity = Math.pow(Math.abs(Math.max(-5, Math.min(5, pctChg))) / 5, 0.72)
  if (pctChg > 0) return mixHex('#f9d8d8', '#df4d57', 0.35 + intensity * 0.65)
  return mixHex('#d4eee1', '#1b9866', 0.35 + intensity * 0.65)
}

function heatLabelColor(background) {
  const hex = background.slice(1)
  const red = parseInt(hex.slice(0, 2), 16)
  const green = parseInt(hex.slice(2, 4), 16)
  const blue = parseInt(hex.slice(4, 6), 16)
  const luma = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255
  return luma > 0.62 ? '#243047' : '#ffffff'
}

/**
 * @param {object} payload
 * @returns {HTMLElement}
 */
export function buildMarketShareSheet(payload) {
  const {
    titleDate = '',
    volumeText = '--',
    volumeLabel = '',
    breadth = null,
    limitPair = null,
    indexes = [],
    effectMetrics = [],
    hint = '',
    industries = [],
    concepts = [],
    heatmap = null,
  } = payload || {}

  const root = document.createElement('div')
  root.setAttribute('data-market-share-sheet', '1')
  root.style.cssText = [
    'box-sizing:border-box',
    'width:960px',
    'padding:0',
    'background:#edf2f7',
    'color:#1d2939',
    'font-family:"Plus Jakarta Sans","PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    'font-size:12px',
    'line-height:1.45',
    'position:relative',
    'overflow:hidden',
  ].join(';')

  const indexHtml = (indexes || []).slice(0, 4).map((row) => {
    const close = row.closePrice ?? row.close
    const d = dir(row.pctChg)
    const accent = d === 'up' ? '#d6434c' : d === 'down' ? '#16845b' : '#667085'
    return `<div style="box-sizing:border-box;position:relative;padding:16px 16px 14px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;overflow:hidden;box-shadow:0 2px 8px rgba(16,24,40,.04);">
      <div style="position:absolute;left:0;top:0;bottom:0;width:3px;background:${accent};"></div>
      <div style="font-size:12px;font-weight:600;color:#667085;letter-spacing:.02em;">${esc(row.name || '--')}</div>
      <div style="margin-top:8px;font-size:26px;font-weight:700;font-variant-numeric:tabular-nums;color:#172033;">${esc(fmtNum(close))}</div>
      <div style="margin-top:4px;font-size:14px;font-weight:700;font-variant-numeric:tabular-nums;color:${accent};">${esc(fmtPct(row.pctChg))}</div>
    </div>`
  }).join('')

  const breadthText = breadth
    ? `<span style="color:#d6434c;font-weight:700;">${esc(breadth.up)}</span>
       <span style="color:#98a2b3;"> / </span>
       <span style="color:#475467;font-weight:650;">${esc(breadth.flat)}</span>
       <span style="color:#98a2b3;"> / </span>
       <span style="color:#16845b;font-weight:700;">${esc(breadth.down)}</span>`
    : '--'

  const limitText = limitPair
    ? `<span style="color:#d6434c;font-weight:700;">${esc(limitPair.up ?? '--')}</span>
       <span style="color:#98a2b3;"> / </span>
       <span style="color:#16845b;font-weight:700;">${esc(limitPair.down ?? '--')}</span>`
    : '--'

  const effectHtml = (effectMetrics || []).map((m) => {
    const accent = pctColor(m.value)
    return `<div style="box-sizing:border-box;padding:12px 10px;border-radius:7px;background:#f8fafc;border:1px solid #e7edf3;">
      <div style="font-size:11px;font-weight:600;color:#667085;letter-spacing:.02em;">${esc(m.label)}</div>
      <div style="margin-top:6px;font-size:18px;font-weight:720;font-variant-numeric:tabular-nums;letter-spacing:-.02em;color:${accent};">${esc(fmtPct(m.value))}</div>
    </div>`
  }).join('')

  const listBlock = (title, rows) => {
    const list = (rows || []).slice(0, 5)
    if (!list.length) return ''
    const items = list.map((row, i) => `
      <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px 0;${i < list.length - 1 ? 'border-bottom:1px solid #edf1f5;' : ''}">
        <div style="display:flex;align-items:center;gap:8px;min-width:0;">
          <span style="flex:0 0 auto;width:16px;font-size:11px;font-weight:700;color:#98a2b3;font-variant-numeric:tabular-nums;">${i + 1}</span>
          <span style="color:#344054;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${esc(row.name || '--')}</span>
        </div>
        <span style="flex:0 0 auto;font-weight:700;font-variant-numeric:tabular-nums;color:${pctColor(row.pctChg)};">${esc(fmtPct(row.pctChg))}</span>
      </div>
    `).join('')
    return `<div style="flex:1;min-width:0;padding:14px 16px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;box-shadow:0 2px 8px rgba(16,24,40,.04);">
      <div style="font-size:12px;font-weight:700;color:#475467;letter-spacing:.04em;margin-bottom:4px;">${esc(title)}</div>
      ${items}
    </div>`
  }

  root.innerHTML = `
    <div style="position:relative;padding:28px 30px 22px;">
      <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:18px;">
        <div>
          ${shareBrandLockupHtml({ subtitle: '行情中心', theme: 'light', markSize: 48 })}
          <div style="margin-top:9px;font-size:12px;color:#667085;font-variant-numeric:tabular-nums;">交易日 ${esc(titleDate)}</div>
        </div>
        <div style="text-align:right;flex:0 0 auto;padding-top:4px;">
          <div style="font-size:12px;font-weight:700;color:#344054;letter-spacing:.06em;">A 股市场概览</div>
          <div style="margin-top:6px;font-size:11px;color:#98a2b3;">指数 · 广度 · 板块</div>
        </div>
      </div>

      <div style="height:1px;background:#dbe3ec;margin-bottom:18px;"></div>

      <div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:12px;">
        ${indexHtml || '<div style="grid-column:1/-1;padding:24px;text-align:center;color:#98a2b3;">暂无指数</div>'}
      </div>

      <div style="display:grid;grid-template-columns:1.15fr 1.45fr 0.95fr;gap:10px;margin-bottom:12px;">
        <div style="padding:14px 16px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;">
          <div style="font-size:11px;font-weight:650;color:#667085;letter-spacing:.04em;">三市成交</div>
          <div style="margin-top:8px;font-size:20px;font-weight:720;font-variant-numeric:tabular-nums;color:#172033;">${esc(volumeText)}</div>
          ${volumeLabel ? `<div style="margin-top:4px;font-size:11px;color:#667085;">${esc(volumeLabel)}</div>` : ''}
        </div>
        <div style="padding:14px 16px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;">
          <div style="font-size:11px;font-weight:650;color:#667085;letter-spacing:.04em;">涨跌家数</div>
          <div style="margin-top:8px;font-size:18px;font-variant-numeric:tabular-nums;">${breadthText}</div>
        </div>
        <div style="padding:14px 16px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;">
          <div style="font-size:11px;font-weight:650;color:#667085;letter-spacing:.04em;">涨跌停</div>
          <div style="margin-top:8px;font-size:18px;font-variant-numeric:tabular-nums;">${limitText}</div>
        </div>
      </div>

      ${effectHtml ? `
      <div style="margin-bottom:12px;padding:14px 16px 16px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;">
        <div style="display:flex;align-items:baseline;justify-content:space-between;gap:10px;margin-bottom:10px;">
          <span style="font-size:12px;font-weight:700;letter-spacing:.04em;color:#475467;">赚钱效应</span>
          ${hint ? `<span style="font-size:11px;color:#98a2b3;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:62%;">${esc(hint)}</span>` : ''}
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

      ${heatmap?.nodes?.length ? `
      <div style="margin-top:12px;padding:14px 16px 16px;border-radius:8px;background:#ffffff;border:1px solid #dfe6ee;">
        <div style="display:flex;align-items:baseline;justify-content:space-between;margin-bottom:10px;">
          <span style="font-size:12px;font-weight:700;letter-spacing:.04em;color:#475467;">行业云图</span>
          <span style="font-size:11px;color:#98a2b3;">${esc(heatmap.tradeDate || titleDate)} · 按流通市值</span>
        </div>
        <div data-market-share-heatmap style="width:100%;height:290px;background:#f4f7fa;border-radius:6px;overflow:hidden;"></div>
      </div>` : ''}

      ${shareBrandFooterHtml({ theme: 'light', note: `${esc(titleDate)} · 仅供研究参考 · 不构成投资建议` })}
    </div>
  `
  return root
}

/**
 * 渲染截图内的行业云图，并返回用于销毁临时画布的实例。
 *
 * @param {HTMLElement} sheet 分享画布
 * @param {Array<object>} nodes 行业节点
 * @returns {object | null}
 */
export function renderMarketShareHeatmap(sheet, nodes = []) {
  const chartNode = sheet?.querySelector('[data-market-share-heatmap]')
  if (!chartNode || !nodes.length) return null
  const chart = echarts.init(chartNode, null, { renderer: 'canvas' })
  const tree = nodes.map((node) => {
    const color = heatColor(node.pctChg)
    return {
      name: node.name || '--',
      value: Math.max(Number(node.value ?? node.circMv) || 1, 1),
      pctChg: node.pctChg,
      itemStyle: { color, borderColor: '#f4f7fa', borderWidth: 2, gapWidth: 2 },
      label: { color: heatLabelColor(color) },
    }
  })
  chart.setOption({
    animation: false,
    backgroundColor: '#f4f7fa',
    series: [{
      type: 'treemap',
      roam: false,
      nodeClick: false,
      breadcrumb: { show: false },
      top: 2,
      right: 2,
      bottom: 2,
      left: 2,
      label: {
        show: true,
        position: 'inside',
        padding: [3, 4],
        overflow: 'truncate',
        lineOverflow: 'truncate',
        formatter(params) {
          const pctChg = Number(params.data?.pctChg)
          const pctText = Number.isFinite(pctChg) ? `${pctChg > 0 ? '+' : ''}${pctChg.toFixed(2)}%` : ''
          return pctText ? `${params.name}\n${pctText}` : params.name
        },
        fontSize: 12,
        fontWeight: 700,
        lineHeight: 14,
      },
      itemStyle: { borderColor: '#f4f7fa', borderWidth: 2, gapWidth: 2 },
      emphasis: { disabled: true },
      data: tree,
    }],
  })
  chart.resize()
  return chart
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
    'background:#edf2f7',
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
