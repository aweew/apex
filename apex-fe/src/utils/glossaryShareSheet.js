/**
 * 名词百科分享画布：关键样式走 inline，避免截图克隆丢失 class CSS；必须带灵枢 Apex 品牌信息。
 */

import { BRAND, shareBrandFooterHtml, shareBrandLockupHtml } from '../brand/identity.js'

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * @param {object} payload
 * @param {{id?:string,title:string,category?:string,short?:string,detail?:string,tip?:string,aliases?:string[]}} payload.term
 * @param {string} [payload.titleDate]
 * @returns {HTMLElement}
 */
export function buildGlossaryShareSheet(payload) {
  const { term = {}, titleDate = '' } = payload || {}
  const aliases = Array.isArray(term.aliases) ? term.aliases.filter(Boolean) : []
  const tip = term.tip ? String(term.tip).trim() : ''
  const short = term.short ? String(term.short).trim() : ''
  const detail = term.detail ? String(term.detail).trim() : ''

  const root = document.createElement('div')
  root.setAttribute('data-glossary-share-sheet', '1')
  root.style.cssText = [
    'box-sizing:border-box',
    'width:680px',
    'padding:32px 36px 24px',
    'background:#f7f4ee',
    'color:#1d1d1f',
    'font-family:"PingFang SC","Microsoft YaHei","Noto Sans SC",sans-serif',
    'font-size:13px',
    'line-height:1.55',
    'position:relative',
    'overflow:hidden',
  ].join(';')

  const tipHtml = tip
    ? `<div style="margin-top:14px;padding:10px 12px;border-radius:10px;background:rgba(0,113,227,.08);color:#3a3a3c;font-size:12px;line-height:1.55;">${esc(tip)}</div>`
    : ''
  const aliasesHtml = aliases.length
    ? `<div style="margin-top:14px;color:#8e8e93;font-size:12px;">也叫：${esc(aliases.join(' · '))}</div>`
    : ''

  root.innerHTML = `
    <div style="position:absolute;inset:0;pointer-events:none;background:
      radial-gradient(ellipse 80% 55% at 0% 0%, rgba(180,120,60,.14), transparent 55%),
      radial-gradient(ellipse 70% 50% at 100% 100%, rgba(40,80,120,.1), transparent 50%),
      linear-gradient(165deg, #faf7f1 0%, #f0ebe3 48%, #e8eef4 100%);"></div>
    <div style="position:relative;z-index:1;">
      <div style="display:flex;align-items:baseline;justify-content:space-between;gap:12px;margin-bottom:22px;">
        ${shareBrandLockupHtml({ subtitle: '名词百科', markSize: 48 })}
        <div style="text-align:right;color:#8e8e93;font-size:11px;font-variant-numeric:tabular-nums;">
          ${esc(titleDate || '')}
        </div>
      </div>
      <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:14px;">
        <span style="display:inline-flex;padding:3px 10px;border-radius:999px;background:rgba(0,0,0,.06);color:#3a3a3c;font-size:12px;font-weight:650;">${esc(term.category || '词条')}</span>
        <span style="display:inline-flex;padding:3px 10px;border-radius:999px;background:rgba(0,113,227,.1);color:#0071e3;font-size:12px;font-weight:650;">${esc(BRAND.nameZh)} Glossary</span>
      </div>
      <h2 style="margin:0 0 12px;font-size:26px;font-weight:750;letter-spacing:-.03em;line-height:1.25;">${esc(term.title || '未命名词条')}</h2>
      ${short ? `<p style="margin:0 0 10px;font-size:15px;line-height:1.65;color:#1d1d1f;">${esc(short)}</p>` : ''}
      ${detail ? `<p style="margin:0;font-size:13px;line-height:1.7;color:#3a3a3c;">${esc(detail)}</p>` : ''}
      ${tipHtml}
      ${aliasesHtml}
      ${shareBrandFooterHtml({ note: `来自 ${BRAND.nameZh} ${BRAND.product} · 仅供研究参考 · 不构成投资建议` })}
    </div>
  `
  return root
}

/**
 * 挂载分享画布并返回卸载函数
 * @param {HTMLElement} sheet
 * @returns {{ host: HTMLElement, sheet: HTMLElement, dispose: () => void }}
 */
export function mountGlossaryShareSheet(sheet) {
  const host = document.createElement('div')
  host.setAttribute('data-glossary-share-host', '1')
  host.style.cssText = [
    'position:fixed',
    'left:-10000px',
    'top:0',
    'width:680px',
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
