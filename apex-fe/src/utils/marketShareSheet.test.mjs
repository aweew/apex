import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const sheetSource = await readFile(new URL('./marketShareSheet.js', import.meta.url), 'utf8')
const indexBoardSource = await readFile(new URL('../views/IndexBoardView.vue', import.meta.url), 'utf8')

test('market share sheet uses a neutral public layout without a market stance', () => {
  assert.match(sheetSource, /background:#edf2f7/)
  assert.doesNotMatch(sheetSource, /立场/)
  assert.match(sheetSource, /交易日 \$\{esc\(titleDate\)\}/)
})

test('market share sheet includes a same-day industry heatmap', () => {
  assert.match(sheetSource, /data-market-share-heatmap/)
  assert.match(sheetSource, /export function renderMarketShareHeatmap/)
  assert.match(indexBoardSource, /fetchMarketHeatmap/)
  assert.match(indexBoardSource, /snapshotStamp\(response\.data\) === titleDate/)
  assert.match(indexBoardSource, /renderMarketShareHeatmap\(sheet, heatmap\?\.nodes\)/)
})
