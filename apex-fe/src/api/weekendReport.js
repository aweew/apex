import http from './http'

/**
 * 读取最新周末消息面专题研报。
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function fetchWeekendMarketReport() {
  return http.get('/api/weekend-report', { timeout: 90000 })
}

/**
 * 使用最新资讯和行情重新生成周末消息面专题研报。
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function refreshWeekendMarketReport() {
  return http.post('/api/weekend-report/refresh', null, { timeout: 120000 })
}
