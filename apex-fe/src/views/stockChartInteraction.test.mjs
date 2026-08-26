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
})

test('intraday polling pauses while the page is hidden and resumes on return', () => {
  assert.match(stockSource, /if \(!isIntraday\.value \|\| document\.hidden\) return/)
  assert.match(stockSource, /if \(!document\.hidden && isIntraday\.value && activeTab\.value === 'chart'\)/)
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
