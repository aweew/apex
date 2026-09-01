import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const viewSource = await readFile(new URL('./CapitalFlowView.vue', import.meta.url), 'utf8')
const mobileStyles = viewSource.slice(viewSource.indexOf('@media (max-width: 720px)'))
const stockMobileSource = viewSource.slice(
  viewSource.indexOf('<div v-if="stockFlows.length" class="mobile-flow-list">'),
  viewSource.indexOf('<el-empty v-if="!stockFlows.length"'),
)
const dragonTigerMobileSource = viewSource.slice(
  viewSource.indexOf('<div v-if="dragonTigerItems.length" class="mobile-flow-list">'),
  viewSource.indexOf('<el-empty v-if="!dragonTigerItems.length"'),
)

test('capital flow sections prioritize dragon tiger, sectors, then stock flows', () => {
  const dragonTigerIndex = viewSource.indexOf('class="flow-section dragon-tiger-section"')
  const sectorFlowIndex = viewSource.indexOf('class="flow-section sector-flow-section"')
  const stockFlowIndex = viewSource.indexOf('class="flow-section stock-flow-section"')

  assert.ok(dragonTigerIndex < sectorFlowIndex)
  assert.ok(sectorFlowIndex < stockFlowIndex)
})

test('capital flow page removes northbound presentation and keeps the remaining datasets identifiable', () => {
  assert.doesNotMatch(viewSource, /northbound-summary|北向资金|北向披露|formatNorthboundAmount/)
  assert.match(viewSource, /class="flow-section stock-flow-section"/)
  assert.match(viewSource, /class="sector-flow-grid"/)
  assert.match(viewSource, /class="flow-section dragon-tiger-section"/)
  assert.match(viewSource, /<StockIdentity[\s\S]*?:interactive="true"/)
  assert.match(viewSource, /formatCapitalAmount\(row\.amount\)/)
})

test('administrators can refresh flow, dragon tiger, or all datasets', () => {
  assert.match(viewSource, /const isAdmin = computed\(\(\) => currentUser\?\.role === 'ADMIN'\)/)
  assert.match(viewSource, /refreshCapitalFlow\(mode\)/)
  assert.match(viewSource, /@command="onRefresh"/)
  assert.match(viewSource, /command="flow"/)
  assert.match(viewSource, /command="lhb"/)
  assert.match(viewSource, /command="all"/)
})

test('mobile tables become vertical cards without horizontal overlap', () => {
  assert.match(mobileStyles, /\.desktop-flow-table\s*\{[\s\S]*?display:\s*none;/)
  assert.match(mobileStyles, /\.mobile-flow-list\s*\{[\s\S]*?display:\s*grid;/)
  assert.match(mobileStyles, /\.flow-card\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(mobileStyles, /\.flow-card-metrics\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(mobileStyles, /\.flow-card-value\s*\{[\s\S]*?overflow-wrap:\s*anywhere;/)
})

test('mobile sector flow rankings use two compact cards per row', () => {
  assert.match(mobileStyles, /\.sector-flow-grid\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);/)
  assert.match(mobileStyles, /\.sector-column \.mobile-flow-list\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(mobileStyles, /\.sector-flow-card\s*\{[\s\S]*?padding:\s*10px;/)
})

test('mobile stock flow cards retain medium and small order flows', () => {
  assert.match(stockMobileSource, /formatCapitalAmount\(row\.mediumNetInflow\)/)
  assert.match(stockMobileSource, /formatCapitalAmount\(row\.smallNetInflow\)/)
})

test('stock flow desktop columns and mobile cards share one sortable result', () => {
  assert.match(viewSource, /const sortedStockFlows = computed/)
  assert.match(viewSource, /sortStockFlowItems\([\s\S]*?stockFlows\.value/)
  assert.match(viewSource, /:data="sortedStockFlows"[\s\S]*?@sort-change="onStockFlowSortChange"/)
  for (const prop of ['name', 'pctChg', 'mainNetInflow', 'mainNetInflowPct', 'superLargeNetInflow', 'largeNetInflow', 'mediumNetInflow', 'smallNetInflow']) {
    assert.match(viewSource, new RegExp(`prop="${prop}"[\\s\\S]*?sortable="custom"`))
  }
  assert.match(stockMobileSource, /v-for="row in sortedStockFlows"/)
  assert.match(viewSource, /class="stock-sort-mobile"/)
})

test('mobile dragon tiger cards keep distinct reasons and complete buy-sell amounts', () => {
  assert.match(dragonTigerMobileSource, /:key="`\$\{row\.code\}-\$\{row\.tradeDate\}-\$\{row\.reason\}`"/)
  assert.match(dragonTigerMobileSource, /class="dragon-card-primary"[\s\S]*?formatCapitalAmount\(row\.netBuyAmount\)/)
  assert.match(dragonTigerMobileSource, /class="dragon-card-market"[\s\S]*?formatCapitalPrice\(row\.closePrice\)[\s\S]*?formatCapitalPercent\(row\.turnoverRate\)[\s\S]*?formatCapitalAmount\(row\.amount\)/)
  assert.match(dragonTigerMobileSource, /class="dragon-card-flow"/)
  assert.match(dragonTigerMobileSource, /formatCapitalAmount\(row\.buyAmount\)/)
  assert.match(dragonTigerMobileSource, /formatCapitalAmount\(row\.sellAmount\)/)
  assert.match(dragonTigerMobileSource, /class="dragon-reason-label">上榜原因/)
  assert.match(mobileStyles, /\.dragon-tiger-card\s*\{[\s\S]*?border-top:\s*3px solid #94a3b8;/)
  assert.match(mobileStyles, /\.dragon-card-market\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/)
  assert.match(mobileStyles, /\.dragon-card-flow\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
})

test('dragon tiger desktop columns and mobile cards share one sortable result', () => {
  assert.match(viewSource, /const sortedDragonTigerItems = computed/)
  assert.match(viewSource, /sortDragonTigerItems\([\s\S]*?dragonTigerItems\.value/)
  assert.match(viewSource, /:data="sortedDragonTigerItems"[\s\S]*?@sort-change="onDragonTigerSortChange"/)
  for (const prop of ['name', 'closePrice', 'pctChg', 'turnoverRate', 'netBuyAmount', 'buyAmount', 'sellAmount', 'amount']) {
    assert.match(viewSource, new RegExp(`prop="${prop}"[\\s\\S]*?sortable="custom"`))
  }
  assert.match(dragonTigerMobileSource, /v-for="row in sortedDragonTigerItems"/)
  assert.match(viewSource, /class="dragon-sort-mobile"/)
})

test('mobile action controls use consistent compact sizing', () => {
  assert.equal(viewSource.match(/<span class="refresh-label">刷新<\/span>/g)?.length, 2)
  assert.doesNotMatch(viewSource, /class="sort-direction-button"[\s\S]*?\bcircle\b/)
  assert.match(mobileStyles, /\.capital-refresh-button\s*\{[\s\S]*?width:\s*40px;[\s\S]*?height:\s*40px;[\s\S]*?border-radius:\s*6px;/)
  assert.match(mobileStyles, /\.dragon-sort-mobile :deep\(\.el-select__wrapper\)[\s\S]*?min-height:\s*36px;[\s\S]*?border-radius:\s*6px;/)
  assert.match(mobileStyles, /:deep\(\.sort-direction-button\)\s*\{[\s\S]*?width:\s*36px;[\s\S]*?height:\s*36px;/)
})
