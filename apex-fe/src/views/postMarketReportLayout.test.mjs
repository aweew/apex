import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const viewSource = await readFile(new URL('./PostMarketReportView.vue', import.meta.url), 'utf8')
const apiSource = await readFile(new URL('../api/postMarketReport.js', import.meta.url), 'utf8')
const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')
const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')

test('post-market report exposes latest-only API and route contracts', () => {
  assert.match(apiSource, /http\.get\('\/api\/post-market-report'/)
  assert.match(apiSource, /http\.post\('\/api\/post-market-report\/refresh'/)
  assert.doesNotMatch(apiSource, /tradeDate/)
  assert.match(routerSource, /path: '\/post-market-report'/)
})

test('post-market report renders the six market review sections', () => {
  for (const section of ['大盘', '板块', '主线', '明星个股', '龙虎榜', '知名游资']) {
    assert.match(viewSource, new RegExp(section))
  }
  assert.match(viewSource, /marketSnapshot/)
  assert.match(viewSource, /industryBoards/)
  assert.match(viewSource, /conceptBoards/)
  assert.match(viewSource, /mainlines/)
  assert.match(viewSource, /starStocks/)
  assert.match(viewSource, /dragonTigerItems/)
  assert.match(viewSource, /activeSeats/)
  assert.match(viewSource, /qualityWarnings/)
  assert.match(viewSource, /missingData/)
})

test('post-market report uses dense desktop tables and mobile cards safely', () => {
  assert.match(viewSource, /class="post-market-table"/)
  assert.match(viewSource, /class="post-market-mobile-list"/)
  assert.match(viewSource, /@media \(max-width: 760px\)/)
  assert.match(viewSource, /overflow-wrap:\s*anywhere/)
  assert.match(viewSource, /grid-template-columns:\s*minmax\(0, 1fr\)/)
})

test('post-market report keeps a generation action when the latest report is absent', () => {
  assert.match(viewSource, /reportWindowOpen && !loading && !report/)
  assert.match(viewSource, /盘后总结尚未生成/)
  assert.match(viewSource, /@click="refreshReport">立即生成/)
})

test('dashboard and command search expose post-market report without permanent navigation', () => {
  assert.match(appSource, /to: '\/post-market-report', label: '盘后总结'/)
  assert.match(appSource, /item\.to !== '\/post-market-report' \|\| isPostMarketReportVisible/)
  assert.match(dashboardSource, /fetchPostMarketReport/)
  assert.match(dashboardSource, /router\.push\('\/post-market-report'\)/)
  assert.match(dashboardSource, /查看盘后总结/)
})

test('dashboard exposes a dragon tiger shortcut beside the post-market report action', () => {
  assert.match(
    dashboardSource,
    /查看盘后总结[\s\S]*?router\.push\(\{ path: '\/market', query: \{ tab: 'capital-flow' \}, hash: '#dragon-tiger' \}\)[\s\S]*?龙虎榜/,
  )
})
