function syncErrorMessage(error, fallback) {
  const message = String(error?.message || '')
  if (error?.code === 'ECONNABORTED' || /timeout/i.test(message)) {
    return `${fallback.replace(/失败$/, '')}超时`
  }
  return message || fallback
}

function validateBarSync(response) {
  const data = response?.data || {}
  const successCount = Number(data.successCount)
  const failCount = Number(data.failCount)
  if ((Number.isFinite(failCount) && failCount > 0)
    || (Number.isFinite(successCount) && successCount === 0)) {
    const failedDetail = Array.isArray(data.details)
      ? data.details.find((item) => /FAIL|TIMEOUT/i.test(String(item)))
      : ''
    throw new Error(failedDetail || '日线同步失败')
  }
  return data
}

/**
 * 并行同步单股日线和行情，并在两路结束后读取最新详情。
 *
 * @param {object} options 同步依赖与回调
 * @return {Promise<object>} 各阶段同步结果
 */
export async function synchronizeStockData(options) {
  const {
    code,
    syncBars,
    syncQuote,
    fetchDetail,
    onProgress = () => {},
  } = options
  const progress = { bars: 'running', quote: 'running', detail: 'pending' }
  const emitProgress = () => onProgress({ ...progress })
  emitProgress()

  const settle = async (key, fallback, task, validate = (value) => value) => {
    try {
      const value = validate(await task())
      progress[key] = 'success'
      emitProgress()
      return { ok: true, value }
    } catch (error) {
      progress[key] = 'error'
      emitProgress()
      return { ok: false, error: syncErrorMessage(error, fallback) }
    }
  }

  const [bars, quote] = await Promise.all([
    settle('bars', '日线同步失败', () => syncBars({ codes: [code] }), validateBarSync),
    settle('quote', '行情同步失败', () => syncQuote(code)),
  ])

  progress.detail = 'running'
  emitProgress()
  const detail = await settle('detail', '页面刷新失败', () => fetchDetail(code))

  return { bars, quote, detail, progress: { ...progress } }
}

/**
 * 生成同步完成后的简短结果文案。
 *
 * @param {object} result 同步结果
 * @return {{type: string, text: string}} 提示级别和文案
 */
export function stockSyncSummary(result) {
  const barCount = Number(result?.bars?.value?.barCount)
  const barText = result?.bars?.ok
    ? `日线 ${Number.isFinite(barCount) ? barCount : 0} 根`
    : '日线失败'
  const quoteText = result?.quote?.ok ? '行情已更新' : '行情失败'
  const detailText = result?.detail?.ok ? '' : '，页面刷新失败'
  const successCount = Number(result?.bars?.ok) + Number(result?.quote?.ok)

  if (successCount === 2 && result?.detail?.ok) {
    return { type: 'success', text: `${barText} · ${quoteText}` }
  }
  if (successCount === 0) {
    return { type: 'error', text: `日线和行情同步均失败${detailText}` }
  }
  return { type: 'warning', text: `${barText} · ${quoteText}${detailText}` }
}
