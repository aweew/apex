import assert from 'node:assert/strict'
import test from 'node:test'
import {
  clearSignalPageCache,
  readSignalListCache,
  readSignalOverviewCache,
  writeSignalListCache,
  writeSignalOverviewCache,
} from './signalPageCache.js'

test('signal page cache reuses overview data during route remounts', () => {
  clearSignalPageCache()
  const overview = { universeCount: 120, stats: { buyCount: 8 } }

  writeSignalOverviewCache(overview)

  assert.deepEqual(readSignalOverviewCache(), overview)
})

test('signal list cache distinguishes server-side filter combinations', () => {
  clearSignalPageCache()
  const buyQuery = { dedupeByCode: true, minScore: 70, side: 'BUY' }
  const sellQuery = { dedupeByCode: true, minScore: 70, side: 'SELL' }

  writeSignalListCache(buyQuery, [{ code: '600000' }])

  assert.deepEqual(readSignalListCache(buyQuery), [{ code: '600000' }])
  assert.equal(readSignalListCache(sellQuery), undefined)
})
