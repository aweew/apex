import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const syncViewUrl = new URL('./SyncView.vue', import.meta.url)

test('view log action exposes loading feedback and scrolls the detail panel into view', async () => {
  const source = await readFile(syncViewUrl, 'utf8')

  assert.match(source, /const detailPanelRef = ref\(null\)/)
  assert.match(source, /const detailLoadingJobId = ref\(null\)/)
  assert.match(
    source,
    /async function selectJob\(job\)[\s\S]*?detailLoadingJobId\.value = job\.id[\s\S]*?await nextTick\(\)[\s\S]*?detailPanelRef\.value\?\.scrollIntoView/,
  )
  assert.match(source, /ref="detailPanelRef"/)
  assert.match(source, /v-loading="detailLoadingJobId != null"/)
  assert.match(source, /:loading="detailLoadingJobId === task\.latestJob\.id"/)
})
