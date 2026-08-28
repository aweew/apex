import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const stockSource = await readFile(new URL('./StockView.vue', import.meta.url), 'utf8')

test('visible K-line prices retain breathing room above highs and below lows', () => {
  assert.match(
    stockSource,
    /const pad = span > 0 \? span \* 0\.08 : Math\.max\(Math\.abs\(max\) \* 0\.02, 0\.01\)/,
  )
})

test('horizontal chart gestures stay inside the chart while vertical page scrolling remains available', () => {
  assert.match(
    stockSource,
    /\.chart\s*\{[\s\S]*?touch-action:\s*pan-y;[\s\S]*?overscroll-behavior-x:\s*contain;/,
  )
  assert.doesNotMatch(stockSource, /touch-action:\s*manipulation;/)
  assert.match(stockSource, /type:\s*'inside',[\s\S]*?zoomOnMouseWheel:\s*false,[\s\S]*?moveOnMouseWheel:\s*false,/)
})

test('stock indicators start collapsed instead of restoring an expanded state', () => {
  assert.match(stockSource, /const metaExpanded = ref\(false\)/)
  assert.doesNotMatch(stockSource, /META_EXPAND_KEY/)
  assert.doesNotMatch(stockSource, /apex\.stock\.metaExpanded/)
  assert.match(stockSource, /class="meta-toggle"[\s\S]*?:aria-expanded="metaExpanded"[\s\S]*?aria-controls="stock-meta-details"/)
})

test('intraday polling pauses while the page is hidden and resumes on return', () => {
  assert.match(stockSource, /if \(!isIntraday\.value \|\| document\.hidden\) return/)
  assert.match(stockSource, /if \(!document\.hidden && isIntraday\.value\) loadIntraday\(true\)/)
  assert.doesNotMatch(stockSource, /activeTab\.value === 'chart'/)
  assert.match(stockSource, /function onDocumentVisibilityChange\(\)/)
  assert.match(stockSource, /document\.addEventListener\('visibilitychange', onDocumentVisibilityChange\)/)
  assert.match(stockSource, /document\.removeEventListener\('visibilitychange', onDocumentVisibilityChange\)/)
})

test('K-line toolbar exposes icon zoom controls with accessible labels', () => {
  assert.match(stockSource, /import \{[^}]*RefreshLeft[^}]*ZoomIn[^}]*ZoomOut[^}]*\} from '@element-plus\/icons-vue'/s)
  assert.match(stockSource, /class="chart-zoom-controls"[^>]*aria-label="K线缩放"/)
  assert.match(stockSource, /:icon="ZoomOut"[^>]*aria-label="缩小K线"/s)
  assert.match(stockSource, /:icon="ZoomIn"[^>]*aria-label="放大K线"/s)
  assert.match(stockSource, /:icon="RefreshLeft"[^>]*aria-label="还原默认视野"/s)
  assert.match(stockSource, /@click="zoomChart\('out'\)"/)
  assert.match(stockSource, /@click="zoomChart\('in'\)"/)
  assert.match(stockSource, /class="chart-stage"[\s\S]*?class="chart-zoom-controls"/)
  assert.match(
    stockSource,
    /\.chart-zoom-controls\s*\{[^}]*position:\s*absolute;[^}]*bottom:\s*46px;[^}]*opacity:\s*0\.82;/s,
  )
  assert.match(stockSource, /\.chart-zoom-controls :deep\(\.chart-zoom-button\.el-button\)\s*\{[^}]*background:\s*rgba\(255,\s*255,\s*255,\s*0\.76\);/s)
  assert.doesNotMatch(stockSource, /class="chart-hint"/)
})

test('K-line period controls sit directly above the chart instead of above secondary indicators', () => {
  const toolbarIndex = stockSource.indexOf('class="chart-toolbar"')
  const periodControlsIndex = stockSource.indexOf('class="chart-primary-controls"')
  const chartStageIndex = stockSource.indexOf('class="chart-stage"')

  assert.ok(toolbarIndex > 0)
  assert.ok(periodControlsIndex > toolbarIndex)
  assert.ok(chartStageIndex > periodControlsIndex)
  assert.match(stockSource, /\.chart-primary-controls\s*\{[^}]*margin:\s*0 0 4px;/s)
  assert.match(stockSource, /@media \(max-width: 820px\)[\s\S]*?\.period-mode\s*\{[^}]*order:\s*4;/s)
})

test('K-line zoom buttons dispatch the next data window and disable at boundaries', () => {
  assert.match(stockSource, /function zoomChart\(direction\)/)
  assert.match(stockSource, /nextKlineZoomRange\([\s\S]*?direction,[\s\S]*?MIN_VISIBLE_BARS/s)
  assert.match(stockSource, /chart\.dispatchAction\(\{[\s\S]*?type: 'dataZoom',[\s\S]*?start: nextZoom\.start,[\s\S]*?end: nextZoom\.end/s)
  assert.match(stockSource, /:disabled="!canZoomOut"/)
  assert.match(stockSource, /:disabled="!canZoomIn"/)
  assert.equal(
    (stockSource.match(/minValueSpan: Math\.min\(MIN_VISIBLE_BARS, list\.length\)/g) || []).length,
    2,
  )
})

test('mobile K-line zoom controls keep stable touch targets', () => {
  assert.match(stockSource, /\.chart-zoom-button\s*\{[^}]*width:\s*32px;[^}]*height:\s*32px;/s)
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.chart-zoom-button\s*\{[^}]*width:\s*44px;[^}]*height:\s*44px;/s,
  )
})
