/**
 * 灵极 Apex 品牌标识（单一事实源）
 * 中文名：灵极 · 英文名：Apex · Slogan：灵极既定，只问顶峰
 *
 * 资源：
 * - mark：Arc 标志（透明底，导航）
 * - markShare：Arc App 图标（分享截图专用）
 * - lockup：完整横版标识
 * - solid：蓝底完整标识
 */

export const BRAND = {
  nameZh: '灵极',
  nameEn: 'Apex',
  slogan: '灵极既定，只问顶峰',
  product: '本地量化台',
  /** 文档 / 浏览器标题 */
  documentTitle: '灵极 Apex · 量化决策中枢',
  /** 页眉短标语（导航窄位） */
  taglineShort: '只问顶峰',
  assets: {
    mark: '/brand/arc-mark.svg',
    markShare: '/brand/arc-app-icon.svg',
    lockup: '/brand/arc-lockup.svg',
    solid: '/brand/arc-solid.svg',
  },
}

/**
 * 解析品牌图片绝对地址（分享截图更稳）
 * @param {'mark'|'markShare'|'lockup'|'solid'} [key]
 */
export function brandAssetUrl(key = 'mark') {
  const path = BRAND.assets[key] || BRAND.assets.mark
  if (typeof window !== 'undefined' && window.location?.origin) {
    return `${window.location.origin}${path}`
  }
  return path
}

/**
 * 预加载品牌图，避免分享截图时图标空白
 * @param {Array<'mark'|'markShare'|'lockup'|'solid'>} [keys]
 */
export function preloadBrandAssets(keys = ['markShare']) {
  const urls = keys.map((k) => brandAssetUrl(k))
  return Promise.all(
    urls.map(
      (src) =>
        new Promise((resolve) => {
          const img = new Image()
          img.onload = () => resolve(src)
          img.onerror = () => resolve(src)
          img.src = src
        }),
    ),
  )
}

/**
 * 页面 eyebrow：灵极 · Module
 * @param {string} moduleLabel
 */
export function brandEyebrow(moduleLabel) {
  return `${BRAND.nameZh} · ${moduleLabel}`
}

/**
 * 分享图页眉：仅一处 Logo（深色圆底清晰版）+ 文案
 * @param {{ subtitle?: string, theme?: 'light'|'dark', markSize?: number }} opts
 */
export function shareBrandLockupHtml(opts = {}) {
  const { subtitle = '', theme = 'light', markSize = 48 } = opts
  const isDark = theme === 'dark'
  const zhColor = isDark ? '#f5f5f7' : '#1d1d1f'
  const enColor = isDark ? 'rgba(245,245,247,.62)' : '#6e6e73'
  const subColor = isDark ? 'rgba(245,245,247,.62)' : '#515154'
  const rule = isDark ? 'rgba(245,245,247,.22)' : 'rgba(0,0,0,.14)'
  const enSize = Math.max(12, Math.round(markSize * 0.3))
  const zhSize = Math.max(18, Math.round(markSize * 0.42))
  const subSize = Math.max(13, Math.round(markSize * 0.3))
  const markSrc = brandAssetUrl('markShare')

  const subPart = subtitle
    ? `<span style="width:1px;height:${Math.round(markSize * 0.42)}px;background:${rule};"></span>
       <span style="font-size:${subSize}px;font-weight:700;color:${subColor};">${subtitle}</span>`
    : ''

  return `<div style="display:flex;align-items:center;gap:12px;white-space:nowrap;">
    <img src="${markSrc}" alt="${BRAND.nameZh}" width="${markSize}" height="${markSize}" style="width:${markSize}px;height:${markSize}px;object-fit:contain;flex:0 0 auto;display:block;border-radius:22%;" crossorigin="anonymous" />
    <div style="display:flex;align-items:baseline;gap:8px;min-width:0;">
      <span style="font-size:${zhSize}px;font-weight:750;letter-spacing:0.06em;line-height:1;color:${zhColor};font-family:'Noto Sans SC','PingFang SC','Microsoft YaHei',sans-serif;">${BRAND.nameZh}</span>
      <span style="font-size:${enSize}px;font-weight:700;letter-spacing:0.14em;line-height:1;color:${enColor};font-family:Arial,'Plus Jakarta Sans',sans-serif;text-transform:uppercase;">${BRAND.nameEn}</span>
      ${subPart}
    </div>
  </div>`
}

/**
 * 分享图页脚：仅口号 + 免责（不再放 Logo，避免重复）
 * @param {{ note?: string, theme?: 'light'|'dark' }} opts
 */
export function shareBrandFooterHtml(opts = {}) {
  const { note = '仅供研究参考 · 不构成投资建议', theme = 'light' } = opts
  const isDark = theme === 'dark'
  const sloganColor = isDark ? 'rgba(245,245,247,.72)' : '#3a3a3c'
  const noteColor = isDark ? 'rgba(245,245,247,.38)' : '#8e8e93'
  const border = isDark ? 'rgba(255,255,255,.08)' : 'rgba(0,0,0,.08)'

  return `<div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-top:14px;padding-top:10px;border-top:1px solid ${border};white-space:nowrap;">
    <span style="font-size:12px;font-weight:700;letter-spacing:0.04em;color:${sloganColor};">${BRAND.slogan}</span>
    <span style="font-size:11px;color:${noteColor};flex:0 0 auto;">${note}</span>
  </div>`
}

/**
 * 简短页脚左标（纯文字，无 Logo）
 * @param {{ theme?: 'light'|'dark' }} opts
 */
export function shareBrandMarkInlineHtml(opts = {}) {
  const { theme = 'light' } = opts
  const isDark = theme === 'dark'
  const zhColor = isDark ? '#f5f5f7' : '#1d1d1f'
  const enColor = isDark ? 'rgba(245,245,247,.45)' : '#8e8e93'
  return `<span style="font-size:12px;font-weight:700;letter-spacing:0.04em;color:${zhColor};">${BRAND.nameZh}</span>
    <span style="font-size:11px;letter-spacing:0.1em;color:${enColor};font-family:Arial,sans-serif;text-transform:uppercase;">${BRAND.nameEn}</span>`
}
