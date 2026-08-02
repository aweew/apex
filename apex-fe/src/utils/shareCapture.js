import { domToBlob, domToPng } from 'modern-screenshot'

/**
 * 将 DOM 节点截成 PNG Blob
 * @param {HTMLElement} el
 * @param {object} [opts]
 * @returns {Promise<Blob>}
 */
export async function captureElementBlob(el, opts = {}) {
  if (!el) throw new Error('截图节点不存在')
  const blob = await domToBlob(el, {
    scale: 2,
    backgroundColor: '#ffffff',
    ...opts,
  })
  if (!blob) throw new Error('截图生成失败')
  return blob
}

/**
 * 将 DOM 节点截成 PNG DataURL（预览用）
 * @param {HTMLElement} el
 * @param {object} [opts]
 * @returns {Promise<string>}
 */
export async function captureElementPng(el, opts = {}) {
  if (!el) throw new Error('截图节点不存在')
  return domToPng(el, {
    scale: 2,
    backgroundColor: '#ffffff',
    ...opts,
  })
}

/**
 * 触发浏览器下载
 * @param {Blob} blob
 * @param {string} filename
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || `apex-share-${Date.now()}.png`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/**
 * 复制图片到剪贴板（不支持时抛错）
 * @param {Blob} blob
 */
export async function copyImageBlob(blob) {
  if (!navigator.clipboard || typeof ClipboardItem === 'undefined') {
    throw new Error('当前浏览器不支持复制图片，请改用下载')
  }
  const pngBlob = blob.type === 'image/png'
    ? blob
    : new Blob([await blob.arrayBuffer()], { type: 'image/png' })
  await navigator.clipboard.write([new ClipboardItem({ 'image/png': pngBlob })])
}

/**
 * 生成分享文件名
 * @param {string} prefix
 * @param {string} [title]
 */
export function shareFilename(prefix, title) {
  const safe = String(title || '')
    .replace(/[\\/:*?"<>|]/g, '')
    .replace(/\s+/g, '_')
    .slice(0, 24)
  const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  return `${prefix || 'apex'}_${safe || 'share'}_${stamp}.png`
}
