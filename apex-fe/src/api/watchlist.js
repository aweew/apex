import http from './http'

export function fetchWatchlist(groupName) {
  return http.get('/api/watchlist', { params: { groupName } })
}

export function importWatchlist(payload) {
  return http.post('/api/watchlist/import', payload)
}

export function addWatchlistCodes(payload) {
  return http.post('/api/watchlist/add', payload)
}

export function refreshQuotes(groupName, limit = 40, preferMissing = true) {
  return http.post('/api/watchlist/refresh-quotes', null, {
    params: { groupName, limit, preferMissing },
    timeout: 180000,
  })
}

export function fillQuotes(groupName, rounds = 3, limit = 40) {
  return http.post('/api/watchlist/fill-quotes', null, {
    params: { groupName, rounds, limit },
    timeout: 300000,
  })
}

export function watchlistMovers(groupName, threshold = 5, limit = 10) {
  return http.get('/api/watchlist/movers', { params: { groupName, threshold, limit } })
}

export function watchlistCorrelation(groupName, limit = 8, lookback = 60) {
  return http.get('/api/watchlist/correlation', { params: { groupName, limit, lookback } })
}
