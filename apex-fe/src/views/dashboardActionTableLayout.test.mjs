import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('desktop decision table uses the shared name-first stock identity', () => {
  const tableStart = dashboardSource.indexOf(':data="topBuys"')
  const tableEnd = dashboardSource.indexOf('</el-table>', tableStart)
  const decisionTable = dashboardSource.slice(tableStart, tableEnd)

  assert.match(decisionTable, /<el-table-column prop="name" label="股票"[\s\S]*?<StockIdentity :security="row" interactive compact/)
  assert.doesNotMatch(decisionTable, /label="代码"|label="名称"/)
})

test('desktop decision table reserves one complete cue column for linkage and mainline tags', () => {
  const tableStart = dashboardSource.indexOf(':data="topBuys"')
  const tableEnd = dashboardSource.indexOf('</el-table>', tableStart)
  const decisionTable = dashboardSource.slice(tableStart, tableEnd)

  assert.match(decisionTable, /label="联动" width="124" class-name="action-cues-col"/)
  assert.match(decisionTable, /class="action-cues"[\s\S]*?row\.linkHint[\s\S]*?row\.mainlineMatch/)
  assert.doesNotMatch(decisionTable, /<el-table-column label="主线"/)
  assert.match(dashboardSource, /\.action-cues :deep\(\.link-hint-tag\)\s*\{[\s\S]*?white-space:\s*nowrap;/)
})

test('desktop action panels stay inside the dashboard container', () => {
  assert.match(
    dashboardSource,
    /\.two-col\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/,
  )
  assert.match(
    dashboardSource,
    /\.action-panel\s*\{[^}]*min-width:\s*0;/,
  )
})
