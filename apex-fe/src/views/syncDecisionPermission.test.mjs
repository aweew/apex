import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const syncSource = await readFile(new URL('./SyncView.vue', import.meta.url), 'utf8')

test('sync center reserves shared task starts for administrators', () => {
  assert.match(syncSource, /import \{ getCurrentUser \} from '\.\.\/api\/auth'/)
  assert.match(syncSource, /const isAdmin = computed\(\(\) => currentUser\?\.role === 'ADMIN'\)/)
  assert.match(syncSource, /v-if="isAdmin"[\s\S]*?@click="onStart\(task\)"/)
  assert.match(syncSource, /v-if="isAdmin" class="custom-box"/)
})

test('ordinary users see that decision generation is system managed', () => {
  assert.match(syncSource, /task\.taskType === 'DECISION' && !isAdmin/)
  assert.match(syncSource, /系统共享生成/)
})
