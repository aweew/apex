export function isActiveSyncJob(job) {
  return job?.status === 'RUNNING' || job?.status === 'PENDING'
}

export function findRunningSyncJob(overview) {
  const runningTask = (overview?.tasks || []).find(
    (task) => task.running && isActiveSyncJob(task.latestJob),
  )
  if (runningTask) return runningTask.latestJob
  return (overview?.recentJobs || []).find(isActiveSyncJob) || null
}

export function shouldSwitchToRunningJob(activeJob, pinnedJobId) {
  return pinnedJobId !== activeJob?.id || !isActiveSyncJob(activeJob)
}

export function createLatestLoader(load) {
  let latestRequestId = 0
  return async (...args) => {
    const requestId = ++latestRequestId
    try {
      const response = await load(...args)
      return requestId === latestRequestId ? response : null
    } catch (error) {
      if (requestId !== latestRequestId) return null
      throw error
    }
  }
}

export function createSerialPoller(poll, delay = 2000, timerApi = globalThis) {
  let active = false
  let timer = null
  let restartRequested = false

  async function tick() {
    timer = null
    if (!active) return

    let keepPolling = true
    try {
      keepPolling = (await poll()) !== false
    } catch {
      keepPolling = true
    }

    if (active && (keepPolling || restartRequested)) {
      restartRequested = false
      timer = timerApi.setTimeout(tick, delay)
    } else {
      active = false
      restartRequested = false
    }
  }

  return {
    start() {
      if (active) {
        restartRequested = true
        return
      }
      active = true
      restartRequested = false
      timer = timerApi.setTimeout(tick, delay)
    },
    stop() {
      active = false
      restartRequested = false
      if (timer !== null) timerApi.clearTimeout(timer)
      timer = null
    },
    isRunning() {
      return active
    },
  }
}
