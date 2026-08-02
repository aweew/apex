import http from './http'

export function fetchSectorBoard({
  type = 'INDUSTRY',
  sortBy,
  order = 'desc',
  limit = 100,
  tradeDate,
} = {}) {
  return http.get('/api/sector/board', {
    params: { type, sortBy, order, limit, tradeDate },
  })
}

export function fetchSectorConstituents(
  code,
  { type = 'INDUSTRY', sortBy = 'pctChg', order = 'desc', tradeDate } = {},
) {
  return http.get(`/api/sector/${encodeURIComponent(code)}/constituents`, {
    params: { type, sortBy, order, tradeDate },
  })
}

export function refreshSectorBoard(types = 'INDUSTRY,CONCEPT,THEME') {
  return http.post(
    '/api/sector/refresh',
    null,
    { params: { types }, timeout: 360000 },
  )
}

export function fetchSectorMainline({ tradeDate, limit = 8 } = {}) {
  return http.get('/api/sector/mainline', {
    params: { tradeDate, limit },
  })
}

export function refreshSectorConstituents(code, type = 'INDUSTRY') {
  return http.post(
    `/api/sector/${encodeURIComponent(code)}/constituents/refresh`,
    null,
    { params: { type }, timeout: 240000 },
  )
}

export function fetchSectorRotation({ days = 10, type = 'INDUSTRY' } = {}) {
  return http.get('/api/sector/rotation', { params: { days, type } })
}
