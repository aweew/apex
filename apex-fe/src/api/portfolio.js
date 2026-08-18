import http from './http'

export function listPortfolios(includeArchived = false) {
  return http.get('/api/portfolio/list', { params: { includeArchived } })
}

export function savePortfolio(payload) {
  return http.post('/api/portfolio/save', payload)
}

export function sortPortfolios(portfolioIds) {
  return http.post('/api/portfolio/sort', { portfolioIds })
}

export function removePortfolio(id) {
  return http.delete(`/api/portfolio/${id}`)
}

export function portfolioDetail(id) {
  return http.get(`/api/portfolio/${id}/detail`)
}

export function savePortfolioHolding(portfolioId, payload) {
  return http.post(`/api/portfolio/${portfolioId}/holding/save`, payload)
}

export function tradePortfolioHolding(portfolioId, payload) {
  return http.post(`/api/portfolio/${portfolioId}/holding/trade`, payload)
}

export function removePortfolioHolding(portfolioId, holdingId) {
  return http.delete(`/api/portfolio/${portfolioId}/holding/${holdingId}`)
}

export function importPortfolioHoldings(portfolioId, text) {
  return http.post(`/api/portfolio/${portfolioId}/import`, { text })
}

export function snapshotPortfolio(portfolioId) {
  return http.post(`/api/portfolio/${portfolioId}/snapshot`)
}

export function snapshotAllPortfolios() {
  return http.post('/api/portfolio/snapshot-all')
}

export function listPortfolioDaily(portfolioId, days = 60) {
  return http.get(`/api/portfolio/${portfolioId}/daily`, { params: { days } })
}

export function refreshPortfolioQuotes(portfolioId, onlyMissing = false) {
  return http.post(`/api/portfolio/${portfolioId}/refresh-quotes`, null, {
    params: { onlyMissing },
    timeout: 180000,
  })
}

/** 一键刷新全部活跃组合行情（代码去重） */
export function refreshAllPortfolioQuotes(onlyMissing = false) {
  return http.post('/api/portfolio/refresh-quotes-all', null, {
    params: { onlyMissing },
    timeout: 300000,
  })
}
