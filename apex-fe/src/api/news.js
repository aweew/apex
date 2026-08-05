import http from './http'

export function fetchNewsOverview(source = 'all', limit = 80, keyword) {
  return http.get('/api/news/overview', {
    params: { source, limit, keyword: keyword || undefined },
  })
}

export function fetchNewsList(source = 'eastmoney', limit = 80) {
  return http.get('/api/news/list', { params: { source, limit } })
}

export function refreshNews(sources = 'eastmoney,cls,ths,sina', limit = 80) {
  return http.post('/api/news/refresh', null, { params: { sources, limit } })
}

/**
 * 今日消息面
 * @param {number} cardLimit
 * @param {boolean} forceLlm
 */
export function fetchNewsPulse(cardLimit = 9, forceLlm = false) {
  return http.get('/api/news/pulse', {
    params: { cardLimit, forceLlm },
    timeout: 60000,
  })
}
