import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('dashboard places overnight market and news before action panels', () => {
  const contextIndex = dashboardSource.indexOf('隔夜与今日消息')
  const actionIndex = dashboardSource.indexOf('<div class="two-col">')

  assert.ok(contextIndex > 0)
  assert.ok(actionIndex > contextIndex)
  assert.match(dashboardSource, /home\.value\?\.morningBriefing/)
  assert.match(dashboardSource, /aria-label="隔夜美股与今日消息面"/)
})

test('dashboard morning context has a compact responsive layout', () => {
  assert.match(dashboardSource, /\.morning-context-grid\s*\{[^}]*grid-template-columns:/s)
  assert.match(
    dashboardSource,
    /@media \(max-width: 900px\)[\s\S]*\.morning-context-grid\s*\{[^}]*grid-template-columns:\s*1fr/s,
  )
  assert.match(dashboardSource, /v-for="quote in overnightQuotes"/)
  assert.match(dashboardSource, /v-for="item in morningNewsCards"/)
})
