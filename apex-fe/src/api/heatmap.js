import http from './http'

/** 大盘云图色块 */
export function fetchMarketHeatmap({
  type = 'INDUSTRY',
  colorBy = 'pctChg',
  sizeBy,
  limit = 80,
} = {}) {
  return http.get('/api/market/heatmap', {
    params: { type, colorBy, sizeBy, limit },
  })
}

/** 行业成分下钻 */
export function fetchHeatmapIndustryStocks(industry, limit = 40) {
  return http.get('/api/market/heatmap/industry-stocks', {
    params: { industry, limit },
  })
}
