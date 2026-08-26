import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const dashboardSource = await readFile(new URL('./DashboardView.vue', import.meta.url), 'utf8')
const indexBoardSource = await readFile(new URL('./IndexBoardView.vue', import.meta.url), 'utf8')
const marketEffectDtoSource = await readFile(
  new URL('../../../apex-be/src/main/java/com/awe/apex/quant/domain/dto/MarketEffectResp.java', import.meta.url),
  'utf8',
)
const marketBriefingServiceSource = await readFile(
  new URL('../../../apex-be/src/main/java/com/awe/apex/quant/service/impl/MarketBriefingServiceImpl.java', import.meta.url),
  'utf8',
)

test('money effect includes CSI 1000 from the live quote response', () => {
  assert.match(marketEffectDtoSource, /private BigDecimal csi1000PctChg;/)
  assert.match(marketBriefingServiceSource, /1\.000852/)
  assert.match(marketBriefingServiceSource, /indexMap\.get\("000852"\)/)
  assert.match(marketBriefingServiceSource, /\.csi1000PctChg\(csi1000Pct\)/)
})

test('dashboard and index board render CSI 1000 in the money effect metrics', () => {
  assert.match(dashboardSource, /title="000852 中证1000"/)
  assert.match(dashboardSource, /fmtIndexPct\(effect\.csi1000PctChg\)/)
  assert.match(indexBoardSource, /label: '中证1000', tip: '000852', value: e\.csi1000PctChg/)
})

test('six money effect metrics stay readable on desktop and phone layouts', () => {
  assert.match(dashboardSource, /\.effect-grid\s*\{[^}]*grid-template-columns:\s*repeat\(6,\s*minmax\(0,\s*1fr\)\);/s)
  assert.match(indexBoardSource, /\.pulse-effect-grid\s*\{[^}]*grid-template-columns:\s*repeat\(6,\s*minmax\(0,\s*1fr\)\);/s)

  const phoneStyles = indexBoardSource.slice(
    indexBoardSource.indexOf('@media (max-width: 560px)'),
    indexBoardSource.indexOf('@media (min-width: 360px)'),
  )
  assert.match(phoneStyles, /\.pulse-effect-grid\s*\{[\s\S]*?grid-template-columns:\s*repeat\(3,\s*minmax\(0,\s*1fr\)\);/)
})
