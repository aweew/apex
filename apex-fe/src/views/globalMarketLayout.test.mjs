import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const indexSource = await readFile(new URL('./IndexBoardView.vue', import.meta.url), 'utf8')
const mobileStyles = indexSource.slice(indexSource.indexOf('@media (max-width: 720px)'))

test('market navigation exposes a global overview before regional markets', () => {
  assert.match(indexSource, /const marketTabs = \[[\s\S]*?key: 'global', label: '全球'[\s\S]*?key: 'cn'/)
  assert.match(indexSource, /key: 'us', label: '美股'/)
  assert.doesNotMatch(indexSource, /key: 'us', label: '美国'/)
  assert.match(indexSource, /marketTab === 'global'/)
  assert.match(indexSource, /全球市场 · 指数分布与区域走势/)
})

test('market navigation separates index quotes from A-share analysis', () => {
  assert.match(
    indexSource,
    /class="market-nav-group index-market-nav"[\s\S]*?class="market-nav-label">指数行情<[\s\S]*?v-for="item in marketTabs"/,
  )
  assert.match(
    indexSource,
    /class="market-nav-group analysis-market-nav"[\s\S]*?class="market-nav-label">A股分析<[\s\S]*?>板块<\/button>[\s\S]*?>资金流<\/button>/,
  )
  assert.match(mobileStyles, /\.market-nav\s*\{[\s\S]*?flex-direction:\s*column;/)
  assert.match(mobileStyles, /\.market-nav-group\s*\{[\s\S]*?width:\s*100%;/)
})

test('global overview maps market hubs and keeps quote freshness visible', () => {
  assert.match(indexSource, /class="global-overview"/)
  assert.match(indexSource, /class="world-market-map"/)
  assert.match(indexSource, /v-for="hub in globalMarketHubs"/)
  assert.match(indexSource, /v-if="globalMarketTime"/)
  assert.match(indexSource, /staleDataTime/)
  assert.match(indexSource, /覆盖指数/)
  assert.match(indexSource, /function fmtPointChange\(item\)[\s\S]*?derivePointChange\(item\)/)
  assert.match(indexSource, /fmtPointChange\(hub\.primary\)/)
  assert.match(indexSource, /return `约 \$\{sign\}\$\{fmtNum\(pointChange\)\}`/)
  assert.match(indexSource, /title="按常规交易时段判断">当前时段/)
})

test('global market nodes and regional quotes remain actionable', () => {
  assert.match(indexSource, /selectGlobalHub\(hub\)/)
  assert.match(indexSource, /v-for="item in hub\.items"/)
  assert.match(indexSource, /selectIndex\(item\.code\)/)
  assert.match(indexSource, /class="chart-panel global-chart"/)
})

test('mobile global overview uses a stable map canvas and readable market nodes', () => {
  assert.match(mobileStyles, /\.world-market-map\s*\{[\s\S]*?min-height:\s*640px;/)
  assert.match(mobileStyles, /\.global-node\s*\{[\s\S]*?width:\s*132px;/)
  assert.match(mobileStyles, /\.global-region-grid\s*\{[\s\S]*?grid-template-columns:\s*1fr;/)
  assert.match(mobileStyles, /\.global-node-name\s*\{[\s\S]*?overflow-wrap:\s*anywhere;/)
})
