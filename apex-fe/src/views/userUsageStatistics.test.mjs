import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import { resolveUsageModule } from '../utils/userUsage.js'

const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const viewSource = await readFile(new URL('./UserUsageView.vue', import.meta.url), 'utf8')

test('usage tracking maps application routes to stable business modules', () => {
  assert.equal(resolveUsageModule({ name: 'dashboard' }), 'DASHBOARD')
  assert.equal(resolveUsageModule({ name: 'stock' }), 'STOCK')
  assert.equal(resolveUsageModule({ name: 'login' }), '')
  assert.equal(resolveUsageModule({ name: 'unknown' }), '')
})

test('usage page and navigation enforce the administrator boundary', () => {
  assert.match(routerSource, /path: '\/usage'/)
  assert.match(routerSource, /meta: \{ requiresAdmin: true \}/)
  assert.match(routerSource, /to\.meta\.requiresAdmin[\s\S]*?getCurrentUser\(\)\?\.role !== 'ADMIN'/)
  assert.match(appSource, /const isAdmin = computed\(\(\) => currentUser\.value\?\.role === 'ADMIN'\)/)
  assert.match(appSource, /v-if="isAdmin"[\s\S]*?to="\/usage"/)
  assert.match(appSource, /item\.adminOnly && !isAdmin\.value/)
})

test('authenticated route changes report page views without blocking navigation', () => {
  assert.match(appSource, /resolveUsageModule\(route\)/)
  assert.match(appSource, /void recordPageView\(moduleCode\)\.catch/)
  assert.match(appSource, /watch\(\s*\(\) => route\.fullPath/)
})

test('administrator dashboard exposes period controls, trend, ranking, and responsive user detail', () => {
  assert.match(viewSource, /<el-segmented[\s\S]*?:options="periodOptions"/)
  assert.match(viewSource, /role="img"[\s\S]*?aria-label="用户活跃趋势"/)
  assert.match(viewSource, /class="module-ranking"/)
  assert.match(viewSource, /<el-table[\s\S]*?:data="overview\.users"/)
  assert.match(viewSource, /class="mobile-user-list"/)
  assert.match(viewSource, /@media \(max-width: 720px\)/)
})
