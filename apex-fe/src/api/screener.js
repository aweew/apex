import http from './http'

export function runScreener(payload) {
  return http.post('/api/screener/run', payload || {})
}

/** 全市场 / 股票池数量 */
export function fetchScreenerMeta() {
  return http.get('/api/screener/meta')
}

/** 分页浏览全市场股票 */
export function fetchScreenerMarket(params = {}) {
  return http.get('/api/screener/market', { params })
}

/** 系统模板与当前用户策略 */
export function fetchScreenerStrategies() {
  return http.get('/api/screener/strategies')
}

/** 运行系统模板或用户策略 */
export function runScreenerStrategy(payload) {
  return http.post('/api/screener/strategy-run', payload)
}

/** 新建用户策略 */
export function createScreenerStrategy(payload) {
  return http.post('/api/screener/strategies', payload)
}

/** 更新用户策略 */
export function updateScreenerStrategy(id, payload) {
  return http.put(`/api/screener/strategies/${id}`, payload)
}

/** 复制系统模板 */
export function copyScreenerTemplate(templateKey) {
  return http.post(`/api/screener/strategies/templates/${templateKey}/copy`)
}

/** 复制用户策略 */
export function copyScreenerStrategy(id) {
  return http.post(`/api/screener/strategies/${id}/copy`)
}

/** 启停用户策略 */
export function toggleScreenerStrategy(id, enabled) {
  return http.post(`/api/screener/strategies/${id}/toggle`, null, { params: { enabled } })
}

/** 调整用户策略顺序 */
export function reorderScreenerStrategies(strategyIds) {
  return http.post('/api/screener/strategies/reorder', { strategyIds })
}

/** 删除用户策略 */
export function deleteScreenerStrategy(id) {
  return http.delete(`/api/screener/strategies/${id}`)
}
