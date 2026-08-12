let overviewCache
const signalListCache = new Map()

function buildSignalListCacheKey(query) {
  return JSON.stringify({
    dedupeByCode: Boolean(query.dedupeByCode),
    minScore: Number(query.minScore) || 0,
    side: query.side || '',
  })
}

export function readSignalOverviewCache() {
  return overviewCache
}

export function writeSignalOverviewCache(overview) {
  overviewCache = overview
}

export function readSignalListCache(query) {
  return signalListCache.get(buildSignalListCacheKey(query))
}

export function writeSignalListCache(query, rows) {
  signalListCache.set(buildSignalListCacheKey(query), rows)
}

export function clearSignalPageCache() {
  overviewCache = undefined
  signalListCache.clear()
}
