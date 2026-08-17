import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const observeSource = await readFile(new URL('./ObserveView.vue', import.meta.url), 'utf8')

test('observe auto decision uses the background decision job instead of a long request', () => {
  assert.match(observeSource, /startSyncJob\(\{ taskType: 'DECISION' \}\)/)
  assert.match(observeSource, /fetchSyncJob\(jobId\)/)
  assert.doesNotMatch(observeSource, /autoDecideObserve\(/)
})

test('observe page shows real decision progress and resumes its own job after reload', () => {
  assert.match(observeSource, /class="decision-progress"/)
  assert.match(observeSource, /decisionJob\?\.progressPct/)
  assert.match(observeSource, /decisionJob\?\.message/)
  assert.match(observeSource, /decisionElapsedSeconds/)
  assert.match(observeSource, /sessionStorage\.setItem\(DECISION_JOB_SESSION_KEY/)
  assert.match(observeSource, /sessionStorage\.getItem\(DECISION_JOB_SESSION_KEY\)/)
})
