import http from './http'

export function getTradingCalendar(date, recent = 10) {
  return http.get('/api/market/calendar', { params: { date, recent } })
}

export function getMarketBoard(groupName = '我的自选', limit = 8) {
  return http.get('/api/market/board', { params: { groupName, limit } })
}

export function fetchMarketBriefing() {
  return http.get('/api/market/briefing')
}
