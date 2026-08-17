import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createLatestLoader,
  createSerialPoller,
  findRunningSyncJob,
  shouldSwitchToRunningJob,
} from './syncPolling.mjs'

function deferred() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

test('running job comes from task overview when it is absent from recent jobs', () => {
  const recentJobs = Array.from({ length: 20 }, (_, index) => ({
    id: 200 - index,
    status: 'SUCCESS',
  }))
  const runningJob = { id: 101, status: 'PENDING' }

  const result = findRunningSyncJob({
    recentJobs,
    tasks: [{ taskType: 'CLOSE_BUNDLE', running: true, latestJob: runningJob }],
  })

  assert.equal(result, runningJob)
})

test('completed pinned detail switches to a newly running background job', () => {
  const activeJob = { id: 88, status: 'SUCCESS' }

  assert.equal(shouldSwitchToRunningJob(activeJob, 88), true)
  assert.equal(shouldSwitchToRunningJob({ id: 101, status: 'RUNNING' }, 101), false)
})

test('latest loader discards a response completed after a newer request', async () => {
  const firstResponse = deferred()
  const secondResponse = deferred()
  let requestCount = 0
  const loadLatest = createLatestLoader(() => {
    requestCount += 1
    return requestCount === 1 ? firstResponse.promise : secondResponse.promise
  })

  const firstLoad = loadLatest()
  const secondLoad = loadLatest()
  secondResponse.resolve({ version: 2 })
  assert.deepEqual(await secondLoad, { version: 2 })

  firstResponse.resolve({ version: 1 })
  assert.equal(await firstLoad, null)
})

test('serial poller schedules the next request only after the current request completes', async () => {
  const firstPoll = deferred()
  const scheduled = []
  const timerApi = {
    setTimeout(callback) {
      const timer = { callback }
      scheduled.push(timer)
      return timer
    },
    clearTimeout(timer) {
      const index = scheduled.indexOf(timer)
      if (index >= 0) scheduled.splice(index, 1)
    },
  }
  let pollCount = 0
  const poller = createSerialPoller(
    () => {
      pollCount += 1
      return pollCount === 1 ? firstPoll.promise : false
    },
    2000,
    timerApi,
  )

  poller.start()
  assert.equal(scheduled.length, 1)
  const firstTick = scheduled.shift().callback()
  assert.equal(pollCount, 1)
  assert.equal(scheduled.length, 0)

  firstPoll.resolve(true)
  await firstTick
  assert.equal(scheduled.length, 1)

  const secondTick = scheduled.shift().callback()
  await secondTick
  assert.equal(pollCount, 2)
  assert.equal(scheduled.length, 0)
  assert.equal(poller.isRunning(), false)
})

test('starting a new job while the previous poll finishes keeps polling active', async () => {
  const currentPoll = deferred()
  const scheduled = []
  const timerApi = {
    setTimeout(callback) {
      const timer = { callback }
      scheduled.push(timer)
      return timer
    },
    clearTimeout() {},
  }
  const poller = createSerialPoller(() => currentPoll.promise, 2000, timerApi)

  poller.start()
  const currentTick = scheduled.shift().callback()
  poller.start()
  currentPoll.resolve(false)
  await currentTick

  assert.equal(poller.isRunning(), true)
  assert.equal(scheduled.length, 1)
})
