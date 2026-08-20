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

test('sync task log action remains a lightweight text action', () => {
  assert.match(
    syncSource,
    /\.task-actions :deep\(\.el-button\.is-link\)\s*\{[\s\S]*?min-width:\s*auto;[\s\S]*?background:\s*transparent;[\s\S]*?border-color:\s*transparent;/,
  )
})
