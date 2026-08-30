/**
 * 判断是否展示组合盘中收益。
 *
 * @param {object|null|undefined} calendar A 股交易日历
 * @returns {boolean} 日历明确为非交易日时隐藏
 */
export function shouldShowPortfolioIntraday(calendar) {
  return calendar?.tradingDay !== false
}

/**
 * 整理盘中收益点并计算页面摘要。
 *
 * @param {Array<object>} rows 后端盘中快照
 * @returns {object} 图表序列与摘要
 */
export function buildPortfolioIntradaySeries(rows) {
  const validRows = []
  for (const row of Array.isArray(rows) ? rows : []) {
    const snapshotTime = String(row?.snapshotTime || '')
    const returnRate = row?.todayPct == null ? Number.NaN : Number(row.todayPct)
    if (!snapshotTime || !Number.isFinite(returnRate)) continue
    validRows.push({
      time: snapshotTime.slice(11, 16),
      returnRate,
      pnl: row?.todayPnl == null ? null : Number(row.todayPnl),
    })
  }

  if (!validRows.length) {
    return {
      times: [],
      returnRates: [],
      pnls: [],
      latestReturnRate: null,
      highestReturnRate: null,
      lowestReturnRate: null,
      amplitude: null,
      latestPnl: null,
    }
  }

  const returnRates = validRows.map((row) => row.returnRate)
  const highestReturnRate = Math.max(...returnRates)
  const lowestReturnRate = Math.min(...returnRates)
  const latestRow = validRows[validRows.length - 1]
  return {
    times: validRows.map((row) => row.time),
    returnRates,
    pnls: validRows.map((row) => row.pnl),
    latestReturnRate: latestRow.returnRate,
    highestReturnRate,
    lowestReturnRate,
    amplitude: Number((highestReturnRate - lowestReturnRate).toFixed(4)),
    latestPnl: Number.isFinite(latestRow.pnl) ? latestRow.pnl : null,
  }
}
