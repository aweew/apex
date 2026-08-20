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

test('capital flow page keeps all four datasets identifiable on mobile', () => {
  assert.match(viewSource, /class="northbound-summary"/)
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

test('northbound non-disclosure is explained without treating empty amounts as zero', () => {
  assert.match(viewSource, /NOT_DISCLOSED[\s\S]*?当前不再公开披露净买入/)
  assert.match(viewSource, /formatNorthboundAmount\(northboundFlow\.netBuyAmount\)/)
})

test('mobile tables become vertical cards without horizontal overlap', () => {
  assert.match(mobileStyles, /\.desktop-flow-table\s*\{[\s\S]*?display:\s*none;/)
  assert.match(mobileStyles, /\.mobile-flow-list\s*\{[\s\S]*?display:\s*grid;/)
  assert.match(mobileStyles, /\.flow-card\s*\{[\s\S]*?min-width:\s*0;/)
  assert.match(mobileStyles, /\.flow-card-metrics\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(mobileStyles, /\.flow-card-value\s*\{[\s\S]*?overflow-wrap:\s*anywhere;/)
})

test('mobile stock flow cards retain medium and small order flows', () => {
  assert.match(stockMobileSource, /formatCapitalAmount\(row\.mediumNetInflow\)/)
  assert.match(stockMobileSource, /formatCapitalAmount\(row\.smallNetInflow\)/)
})

test('mobile dragon tiger cards keep distinct reasons and complete buy-sell amounts', () => {
  assert.match(dragonTigerMobileSource, /:key="`\$\{row\.code\}-\$\{row\.tradeDate\}-\$\{row\.reason\}`"/)
  assert.match(dragonTigerMobileSource, /formatCapitalAmount\(row\.buyAmount\)/)
  assert.match(dragonTigerMobileSource, /formatCapitalAmount\(row\.sellAmount\)/)
})

test('mobile refresh control has a stable touch target', () => {
  assert.match(mobileStyles, /\.capital-refresh-button\s*\{[\s\S]*?width:\s*44px;[\s\S]*?height:\s*44px;/)
})
