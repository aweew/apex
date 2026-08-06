import { domToBlob, domToPng } from 'modern-screenshot'

/**
 * 等待节点内图片加载完成，避免分享截图丢品牌 Logo
 * @param {HTMLElement} el
 * @param {number} [timeoutMs]
 */
export async function waitForImages(el, timeoutMs = 4000) {
  if (!el?.querySelectorAll) return
  const imgs = Array.from(el.querySelectorAll('img'))
  if (!imgs.length) return
  await Promise.all(
    imgs.map(
      (img) =>
        new Promise((resolve) => {
          if (img.complete && img.naturalWidth > 0) {
            resolve()
            return
          }
          const done = () => resolve()
          img.addEventListener('load', done, { once: true })
          img.addEventListener('error', done, { once: true })
          setTimeout(done, timeoutMs)
        }),
    ),
  )
}

/**
 * 将 DOM 节点截成 PNG Blob
 * @param {HTMLElement} el
 * @param {object} [opts]
 * @returns {Promise<Blob>}
 */
export async function captureElementBlob(el, opts = {}) {
  if (!el || typeof el.cloneNode !== 'function') {
    throw new Error('截图节点不存在或不是 DOM 元素')
  }
  await waitForImages(el)
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
  if (!el || typeof el.cloneNode !== 'function') {
    throw new Error('截图节点不存在或不是 DOM 元素')
  }
  await waitForImages(el)
  return domToPng(el, {
    scale: 2,
    backgroundColor: '#ffffff',
    ...opts,
  })
}

/**
 * 截图前把窗口与祖先滚动条归零，避免长图顶部被裁成空白
 * @param {HTMLElement} el
 * @returns {() => void}
 */
export function resetScrollForCapture(el) {
  const restores = []
  const winX = window.scrollX || window.pageXOffset || 0
  const winY = window.scrollY || window.pageYOffset || 0
  if (winX || winY) {
    window.scrollTo(0, 0)
    restores.push(() => window.scrollTo(winX, winY))
  }
  let node = el
  while (node && node !== document.documentElement) {
    if (node.scrollTop || node.scrollLeft) {
      const top = node.scrollTop
      const left = node.scrollLeft
      const target = node
      target.scrollTop = 0
      target.scrollLeft = 0
      restores.push(() => {
        target.scrollTop = top
        target.scrollLeft = left
      })
    }
    node = node.parentElement
  }
  return () => {
    for (let i = restores.length - 1; i >= 0; i -= 1) restores[i]()
  }
}

/**
 * 为页面长图展开滚动裁剪（el-table 等），返回还原函数
 * @param {HTMLElement} root
 * @param {{ minTableWidth?: number }} [opts]
 * @returns {() => void}
 */
export function prepareLongCapture(root, opts = {}) {
  if (!root) return () => {}
  const minTableWidth = Math.max(0, Number(opts.minTableWidth) || 0)
  const patches = []
  const patch = (node, styles) => {
    if (!node) return
    const prev = {}
    for (const [key, value] of Object.entries(styles)) {
      prev[key] = node.style.getPropertyValue(key) || ''
      prev[`__pri_${key}`] = node.style.getPropertyPriority(key)
      node.style.setProperty(key, value, 'important')
    }
    patches.push(() => {
      for (const key of Object.keys(styles)) {
        const pri = prev[`__pri_${key}`]
        if (prev[key]) node.style.setProperty(key, prev[key], pri || '')
        else node.style.removeProperty(key)
      }
    })
  }

  patch(root, {
    overflow: 'visible',
    height: 'auto',
    'max-height': 'none',
    'min-height': '0',
    position: 'relative',
    top: 'auto',
    transform: 'none',
    ...(minTableWidth
      ? {
          width: `${minTableWidth}px`,
          'min-width': `${minTableWidth}px`,
        }
      : {}),
  })

  // sticky/fixed 在克隆截图时容易把顶部顶飞或裁切
  root.querySelectorAll('*').forEach((node) => {
    const pos = window.getComputedStyle(node).position
    if (pos === 'sticky' || pos === 'fixed') {
      patch(node, { position: 'static', top: 'auto', bottom: 'auto' })
    }
  })

  const scrollers = root.querySelectorAll(
    '.el-table__body-wrapper, .el-table__header-wrapper, .el-table__footer-wrapper, .el-scrollbar__wrap, .el-table--scrollable-x .el-table__body-wrapper',
  )
  scrollers.forEach((node) => {
    patch(node, {
      overflow: 'visible',
      height: 'auto',
      'max-height': 'none',
    })
  })

  root.querySelectorAll('.el-table').forEach((table) => {
    const parentW = root.getBoundingClientRect().width || 0
    const w = Math.max(table.scrollWidth || 0, table.offsetWidth || 0, parentW, minTableWidth)
    patch(table, { width: `${Math.ceil(w)}px` })
  })

  // 固定列克隆层会在截图里叠影/裁切，长图只保留主表
  root.querySelectorAll('.el-table__fixed, .el-table__fixed-right, .el-table__fixed-left').forEach((node) => {
    patch(node, { display: 'none' })
  })

  // Element Plus 默认 .cell nowrap，会把技术标签挤叠；截图时放开换行
  root.querySelectorAll('.el-table .cell').forEach((node) => {
    patch(node, {
      'white-space': 'normal',
      overflow: 'visible',
      'text-overflow': 'clip',
      'line-height': '1.35',
    })
  })
  root.querySelectorAll('.el-table__body td.el-table__cell').forEach((node) => {
    patch(node, { 'vertical-align': 'top' })
  })

  root.querySelectorAll('.advice').forEach((node) => {
    patch(node, {
      'white-space': 'normal',
      'word-break': 'break-word',
      overflow: 'visible',
      'text-overflow': 'clip',
    })
  })

  return () => {
    for (let i = patches.length - 1; i >= 0; i -= 1) patches[i]()
  }
}

/**
 * 将 echarts/canvas 冻成 img，避免截图时空画布丢题材饼图/曲线
 * @param {HTMLElement} root
 * @returns {() => void}
 */
export function freezeCanvasesForCapture(root) {
  if (!root) return () => {}
  const replacements = []
  root.querySelectorAll('canvas').forEach((canvas) => {
    try {
      if (!canvas.width || !canvas.height) return
      const dataUrl = canvas.toDataURL('image/png')
      if (!dataUrl || dataUrl === 'data:,') return
      const img = document.createElement('img')
      img.src = dataUrl
      img.alt = ''
      img.setAttribute('data-capture-freeze', '1')
      const cs = window.getComputedStyle(canvas)
      img.style.cssText = [
        `width:${cs.width || `${canvas.width}px`}`,
        `height:${cs.height || `${canvas.height}px`}`,
        'display:block',
        'max-width:100%',
      ].join(';')
      const parent = canvas.parentNode
      if (!parent) return
      parent.insertBefore(img, canvas)
      canvas.style.setProperty('display', 'none', 'important')
      replacements.push(() => {
        canvas.style.removeProperty('display')
        img.remove()
      })
    } catch {
      // ignore tainted canvas
    }
  })
  return () => {
    for (let i = replacements.length - 1; i >= 0; i -= 1) replacements[i]()
  }
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
