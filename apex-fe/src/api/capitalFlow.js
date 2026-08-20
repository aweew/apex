import http from './http'

export function fetchCapitalFlowOverview(limit = 20) {
  return http.get('/api/capital-flow/overview', {
    params: { limit },
  })
}

export function refreshCapitalFlow(mode = 'all') {
  return http.post('/api/capital-flow/refresh', null, {
    params: { mode },
    timeout: 360000,
  })
}
