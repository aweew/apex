import http from './http'

export function runDaily(date) {
  return http.post('/api/daily/run', null, { params: { date } })
}

export function listDaily(date) {
  return http.get(`/api/daily/${date}`)
}

export function createJournal(payload) {
  return http.post('/api/journal', payload)
}

export function journalFromAction(actionId, price, quantity) {
  return http.post(`/api/journal/from-action/${actionId}`, null, { params: { price, quantity } })
}

export function latestJournal(limit = 50) {
  return http.get('/api/journal', { params: { limit } })
}
