import assert from 'node:assert/strict'
import test from 'node:test'

import { stockSyncSummary, synchronizeStockData } from './stockSync.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

test('stock detail starts bars and quote sync in parallel before refreshing detail', async () => {
  const bars = deferred()
  const quote = deferred()
  const calls = []
  const progress = []
  const syncing = synchronizeStockData({
    code: '605358',
    syncBars: () => {
      calls.push('bars')
      return bars.promise
    },
    syncQuote: () => {
      calls.push('quote')
      return quote.promise
    },
    fetchDetail: () => {
      calls.push('detail')
      return Promise.resolve({ data: { basic: { code: '605358' } } })
    },
    onProgress: (state) => progress.push(state),
  })

  await Promise.resolve()
  assert.deepEqual(calls, ['bars', 'quote'])
  assert.equal(progress[0].bars, 'running')
  assert.equal(progress[0].quote, 'running')

  quote.resolve({ data: { latestPrice: 49.62 } })
  bars.resolve({ data: { successCount: 1, failCount: 0, barCount: 277 } })
  const result = await syncing

  assert.deepEqual(calls, ['bars', 'quote', 'detail'])
  assert.equal(result.bars.ok, true)
  assert.equal(result.quote.ok, true)
  assert.equal(result.detail.ok, true)
  assert.deepEqual(stockSyncSummary(result), {
    type: 'success',
    text: '日线 277 根 · 行情已更新',
  })
})

test('stock detail treats a fulfilled bar response with failCount as a failure', async () => {
  const result = await synchronizeStockData({
    code: '605358',
    syncBars: async () => ({
      data: { successCount: 0, failCount: 1, barCount: 0, details: ['605358 TIMEOUT'] },
    }),
    syncQuote: async () => ({ data: { latestPrice: 49.62 } }),
    fetchDetail: async () => ({ data: { basic: { code: '605358' } } }),
  })

  assert.equal(result.bars.ok, false)
  assert.equal(result.bars.error, '日线同步超时')
  assert.deepEqual(stockSyncSummary(result), {
    type: 'warning',
    text: '日线失败 · 行情已更新',
  })
})

test('stock detail shows that an existing bar sync is still running', async () => {
  const result = await synchronizeStockData({
    code: '605358',
    syncBars: async () => ({
      data: { successCount: 1, failCount: 0, barCount: 0, details: ['605358 SYNCING'] },
    }),
    syncQuote: async () => ({ data: { latestPrice: 49.62 } }),
    fetchDetail: async () => ({ data: { basic: { code: '605358' } } }),
  })

  assert.deepEqual(stockSyncSummary(result), {
    type: 'warning',
    text: '日线同步中 · 行情已更新',
  })
})

test('stock detail reports request timeouts in Chinese and still refreshes local data', async () => {
  const timeoutError = Object.assign(new Error('timeout of 210000ms exceeded'), { code: 'ECONNABORTED' })
  const result = await synchronizeStockData({
    code: '605358',
    syncBars: async () => { throw timeoutError },
    syncQuote: async () => { throw new Error('行情源不可用') },
    fetchDetail: async () => ({ data: { basic: { code: '605358' } } }),
  })

  assert.equal(result.bars.error, '日线同步超时')
  assert.equal(result.quote.error, '行情源不可用')
  assert.equal(result.detail.ok, true)
  assert.deepEqual(stockSyncSummary(result), {
    type: 'error',
    text: '日线和行情同步均失败',
  })
})
