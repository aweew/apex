import http from './http'

export function fetchHotOverview(limit = 40) {
  return http.get('/api/hot/overview', { params: { limit } })
}

export function fetchHotList(source = 'eastmoney', limit = 40) {
  return http.get('/api/hot/list', { params: { source, limit } })
}

export function refreshHot(sources = 'eastmoney,xueqiu,baidu', limit = 50) {
  return http.post(
    '/api/hot/refresh',
    null,
    { params: { sources, limit }, timeout: 300000 },
  )
}
