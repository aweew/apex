import http from './http'

/** 查询个股六类因子与 Alpha 评分 */
export function fetchFactorCenter(code) {
  return http.get(`/api/factors/${encodeURIComponent(code)}`)
}
