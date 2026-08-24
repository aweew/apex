import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const syncSource = await readFile(new URL('./SyncView.vue', import.meta.url), 'utf8')

test('sync task actions use compact buttons instead of global mobile button height', () => {
  const mobileStyles = syncSource.slice(syncSource.indexOf('@media (max-width: 560px)'))

  assert.match(
    mobileStyles,
    /\.task-actions :deep\(\.el-button\)\s*\{[\s\S]*?height:\s*36px;[\s\S]*?min-height:\s*36px;[\s\S]*?min-width:\s*64px;[\s\S]*?padding:\s*0 14px;/,
  )
  assert.match(
    syncSource,
    /\.task-actions :deep\(\.el-button \+ \.el-button\)\s*\{[\s\S]*?margin-left:\s*0;/,
  )
})

test('sync task log action uses a same-size secondary button', () => {
  assert.match(
    syncSource,
    /v-if="task\.latestJob"\s+size="small"\s+type="primary"\s+plain\s+:loading="detailLoadingJobId === task\.latestJob\.id"/,
  )
})

test('sync task cards separate last execution from data health and keep controls aligned', () => {
  assert.match(syncSource, /上次\{\{ statusLabel\(task\.latestJob\.status\) \}\}/)
  assert.match(syncSource, /最近完整成功 \{\{ fmtTime\(task\.lastSuccessAt\) \}\}/)
  assert.match(syncSource, /if \(level === 'GREEN'\) return '数据正常'/)
  assert.match(syncSource, /if \(level === 'YELLOW'\) return '数据待更新'/)
  assert.match(
    syncSource,
    /\.task-card\s*\{[\s\S]*?display:\s*flex;[\s\S]*?flex-direction:\s*column;[\s\S]*?min-height:\s*198px;/,
  )
  assert.match(
    syncSource,
    /\.task-health\s*\{[\s\S]*?min-height:\s*20px;[\s\S]*?margin-top:\s*auto;/,
  )
  assert.match(
    syncSource,
    /\.health-label\s*\{[\s\S]*?flex-shrink:\s*0;[\s\S]*?white-space:\s*nowrap;/,
  )
  assert.match(
    syncSource,
    /\.health-time\s*\{[\s\S]*?min-width:\s*0;[\s\S]*?overflow:\s*hidden;[\s\S]*?text-overflow:\s*ellipsis;/,
  )
})

test('recent sync start times keep date and time on stable separate lines', () => {
  assert.match(
    syncSource,
    /<time v-if="row\.startedAt" class="job-start-time">\s*<span>\{\{ fmtTime\(row\.startedAt\)\.slice\(0, 10\) \}\}<\/span>\s*<span>\{\{ fmtTime\(row\.startedAt\)\.slice\(11\) \}\}<\/span>\s*<\/time>/,
  )
  assert.match(
    syncSource,
    /\.job-start-time\s*\{[\s\S]*?display:\s*grid;[\s\S]*?white-space:\s*nowrap;[\s\S]*?font-variant-numeric:\s*tabular-nums;/,
  )
})
