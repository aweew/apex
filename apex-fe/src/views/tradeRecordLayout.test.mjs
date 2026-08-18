import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./TradeRecordView.vue', import.meta.url), 'utf8')
const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const portfolioSource = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')
const holdingSource = await readFile(new URL('./HoldingView.vue', import.meta.url), 'utf8')
const tradeDialogSource = await readFile(new URL('../components/HoldingTradeDialog.vue', import.meta.url), 'utf8')
const portfolioApiSource = await readFile(new URL('../api/portfolio.js', import.meta.url), 'utf8')
const holdingApiSource = await readFile(new URL('../api/holding.js', import.meta.url), 'utf8')

test('trade record workspace exposes provenance, estimates and post-sell performance', () => {
  assert.match(source, /交易记录/)
  assert.match(source, /SecurityMarketBadge/)
  assert.match(source, /portfolioId/)
  assert.match(source, /estimated/)
  assert.match(source, /latestReturnPct/)
  assert.match(source, /maxRisePct/)
  assert.match(source, /maxFallPct/)
})

test('trade record workspace is routed and reachable from portfolios', () => {
  assert.match(routerSource, /path:\s*['"]\/trades['"]/)
  assert.match(portfolioSource, /router\.push\(['"]\/trades['"]\)/)
})

test('mobile trade records use cards instead of a squeezed desktop table', () => {
  assert.match(source, /trade-mobile-list/)
  assert.match(source, /@media \(max-width: 820px\)/)
  assert.match(source, /\.trade-table[\s\S]*display:\s*none/)
})

test('portfolio and holding workspaces use the shared buy and sell dialog', () => {
  assert.match(portfolioSource, /HoldingTradeDialog/)
  assert.match(holdingSource, /HoldingTradeDialog/)
  assert.match(portfolioSource, />买入</)
  assert.match(portfolioSource, />卖出</)
  assert.match(holdingSource, />买入</)
  assert.match(holdingSource, />卖出</)
  assert.match(tradeDialogSource, /value="BUY"/)
  assert.match(tradeDialogSource, /value="SELL"/)
  assert.match(tradeDialogSource, /form\.tradePrice/)
  assert.match(tradeDialogSource, /form\.tradeTime/)
})

test('trade APIs are explicit and holding pages no longer expose delete actions', () => {
  assert.match(portfolioApiSource, /\/api\/portfolio\/\$\{portfolioId\}\/holding\/trade/)
  assert.match(holdingApiSource, /\/api\/holding\/trade/)
  assert.doesNotMatch(portfolioSource, />删除</)
  assert.doesNotMatch(portfolioSource, /删除持仓/)
  assert.doesNotMatch(holdingSource, />删除</)
  assert.doesNotMatch(holdingSource, /删除持仓/)
})

test('editing a holding cannot directly change quantity or cost', () => {
  const portfolioEditDialog = portfolioSource.slice(
    portfolioSource.indexOf(':title="\'\u7f16\u8f91持仓\'"'),
    portfolioSource.indexOf('</el-dialog>', portfolioSource.indexOf(':title="\'\u7f16\u8f91持仓\'"')),
  )
  const holdingEditDialog = holdingSource.slice(
    holdingSource.indexOf(':title="\'\u7f16\u8f91持仓\'"'),
    holdingSource.indexOf('</el-dialog>', holdingSource.indexOf(':title="\'\u7f16\u8f91持仓\'"')),
  )
  assert.doesNotMatch(portfolioEditDialog, /form\.quantity|form\.costPrice/)
  assert.doesNotMatch(holdingEditDialog, /form\.quantity|form\.costPrice/)
})
