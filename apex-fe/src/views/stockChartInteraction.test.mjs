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
  assert.match(
    stockSource,
    /type:\s*'inside',[\s\S]*?zoomOnMouseWheel:\s*'ctrl',[\s\S]*?moveOnMouseWheel:\s*false,[\s\S]*?moveOnMouseMove:\s*!isMobileChart\.value,[\s\S]*?preventDefaultMouseMove:\s*!isMobileChart\.value,/,
  )
  assert.match(stockSource, /bindChartWheelScroll\(chart\.getDom\(\)\)/)
  assert.match(stockSource, /function unbindChartWheel\(\)/)
})

test('mobile pinch zoom is damped and merged by animation frame', () => {
  assert.match(stockSource, /const MOBILE_PINCH_SENSITIVITY = 0\.45/)
  assert.match(stockSource, /onPinchStart:\s*startMobilePinch/)
  assert.match(stockSource, /onPinch:\s*queueMobilePinch/)
  assert.match(stockSource, /onPinchEnd:\s*finishMobilePinch/)
  assert.match(stockSource, /requestAnimationFrame\(applyMobilePinch\)/)
  assert.match(stockSource, /pinchKlineZoomRange\([\s\S]*?MOBILE_PINCH_SENSITIVITY/s)
  assert.match(stockSource, /if \(!mobilePinchState\) updateVisibleWindow\(\)/)
  assert.match(stockSource, /function finishMobilePinch\(\)[\s\S]*?updateVisibleWindow\(\)/s)
})

test('stock indicators start collapsed instead of restoring an expanded state', () => {
  assert.match(stockSource, /const metaExpanded = ref\(false\)/)
  assert.doesNotMatch(stockSource, /META_EXPAND_KEY/)
  assert.doesNotMatch(stockSource, /apex\.stock\.metaExpanded/)
  assert.match(
    stockSource,
    /class="meta-toggle"[\s\S]*?:class="\{ 'is-expanded': metaExpanded \}"[\s\S]*?:aria-expanded="metaExpanded"[\s\S]*?aria-controls="stock-meta-details"/,
  )
  assert.match(
    stockSource,
    /class="meta-toggle"[\s\S]*?<Transition name="quote-meta">[\s\S]*?id="stock-meta-details"[\s\S]*?v-if="basic && metaExpanded"[\s\S]*?class="quote-meta-details"[\s\S]*?<\/aside>/,
  )
  assert.doesNotMatch(stockSource, /v-show="metaExpanded" class="meta"/)
  assert.match(stockSource, /\.quote-meta-details\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/s)
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
  assert.match(stockSource, /class="chart-zoom-controls [^"]+"[^>]*aria-label="K线缩放"/)
  assert.match(stockSource, /:icon="ZoomOut"[^>]*aria-label="缩小K线"/s)
  assert.match(stockSource, /:icon="ZoomIn"[^>]*aria-label="放大K线"/s)
  assert.match(stockSource, /:icon="RefreshLeft"[^>]*aria-label="还原默认视野"/s)
  assert.match(stockSource, /@click="zoomChart\('out'\)"/)
  assert.match(stockSource, /@click="zoomChart\('in'\)"/)
  assert.match(
    stockSource,
    /class="chart-canvas-head"[\s\S]*?class="period-mode period-mode--chart"[\s\S]*?class="chart-legend"[\s\S]*?class="chart-data-status"[\s\S]*?class="chart-zoom-controls chart-zoom-controls--desktop"/,
  )
  assert.match(
    stockSource,
    /\.chart-canvas-head\s*\{[^}]*display:\s*grid;[^}]*grid-template-columns:\s*auto minmax\(0, 1fr\) auto auto;[^}]*align-items:\s*center;/s,
  )
  assert.match(stockSource, /\.chart-zoom-controls--desktop\s*\{[^}]*justify-self:\s*end;/s)
  assert.doesNotMatch(stockSource, /\.chart-zoom-controls\s*\{[^}]*position:\s*absolute;/s)
  assert.match(stockSource, /\.chart-zoom-controls :deep\(\.chart-zoom-button\.el-button\)\s*\{[^}]*background:\s*transparent;/s)
  assert.match(stockSource, /legend:\s*\{[^}]*show:\s*false,/s)
  assert.match(stockSource, /v-for="item in chartLegendItems"/)
  assert.match(stockSource, /@click="toggleChartLegend\(item\)"/)
  assert.doesNotMatch(stockSource, /class="chart-hint"/)
})

test('K-line period controls share the desktop chart header with the compact legend', () => {
  const toolbarIndex = stockSource.indexOf('class="chart-toolbar"')
  const periodControlsIndex = stockSource.indexOf('class="chart-primary-controls"')
  const chartStageIndex = stockSource.indexOf('class="chart-stage"')

  assert.ok(toolbarIndex > 0)
  assert.ok(periodControlsIndex > toolbarIndex)
  assert.ok(chartStageIndex > periodControlsIndex)
  assert.match(
    stockSource,
    /v-if="isIntraday" class="chart-primary-controls"[\s\S]*?class="period-mode period-mode--toolbar"[\s\S]*?class="chart-data-status"/s,
  )
  assert.match(
    stockSource,
    /class="chart-canvas-head"[\s\S]*?class="period-mode period-mode--chart"[\s\S]*?<el-radio-button value="day">日K<\/el-radio-button>[\s\S]*?<el-radio-button value="week">周K<\/el-radio-button>[\s\S]*?<el-radio-button value="month">月K<\/el-radio-button>[\s\S]*?class="chart-legend"/s,
  )
  assert.doesNotMatch(stockSource, /class="chart-advanced-controls"/)
  assert.doesNotMatch(stockSource, /class="ma-checks"/)
  assert.match(stockSource, /const DISPLAY_MA_NAMES = \['MA5', 'MA10', 'MA20'\]/)
  assert.match(
    stockSource,
    /MA_META\.filter\(\(item\) => DISPLAY_MA_NAMES\.includes\(item\.name\)\)/,
  )
  assert.match(
    stockSource,
    /label: `\$\{String\(item\.name\)\.replace\(\/\^MA\/i, ''\)\}\$\{klinePeriod\.value === 'day' \? '日' : periodUnit\.value\}`/,
  )
  assert.match(stockSource, /<span>\{\{ item\.label \}\}<\/span>/)
  assert.match(
    stockSource,
    /class="chart-legend"[\s\S]*?v-for="item in chartLegendItems"[\s\S]*?class="chart-legend-item chart-td-toggle"[\s\S]*?<span>神奇九转<\/span>/s,
  )
  assert.match(
    stockSource,
    /<el-checkbox[\s\S]*?v-if="showTd9"[\s\S]*?:model-value="tdShowMode === 'key'"[\s\S]*?@change="tdShowMode = \$event \? 'key' : 'all'"[\s\S]*?>仅看 8\/9<\/el-checkbox>/s,
  )
  assert.doesNotMatch(stockSource, /class="chart-td-mode"/)
  assert.match(
    stockSource,
    /\.chart-td-toggle i\s*\{[^}]*display:\s*inline-grid;[^}]*place-items:\s*center;[^}]*line-height:\s*1;/s,
  )
  assert.match(
    stockSource,
    /\.chart-td-filter\s*\{[^}]*height:\s*26px;[^}]*margin-left:\s*0;/s,
  )
  assert.match(
    stockSource,
    /\.chart-primary-controls\s*\{[^}]*width:\s*100%;[^}]*padding:\s*6px 10px;[^}]*border:\s*1px solid var\(--line\);[^}]*background:\s*#f8fafc;/s,
  )
  assert.match(
    stockSource,
    /\.period-mode :deep\(\.el-radio-button__inner\)\s*\{[^}]*height:\s*26px;[^}]*min-height:\s*26px;[^}]*padding:\s*0 10px;[^}]*font-size:\s*11px;/s,
  )
  assert.doesNotMatch(stockSource, /class="chart-signal-summary"/)
  assert.doesNotMatch(stockSource, /const periodMeta = computed/)
  assert.doesNotMatch(stockSource, /最近 MACD|最近上涨九转|最近下跌九转/)
  assert.match(stockSource, /@media \(max-width: 820px\)[\s\S]*?\.period-mode\s*\{[^}]*order:\s*4;/s)
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.chart-canvas-head \.period-mode--chart\s*\{[^}]*grid-row:\s*2;[^}]*grid-column:\s*1 \/ -1;/s,
  )
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.period-mode :deep\(\.el-radio-button__inner\)\s*\{[^}]*height:\s*36px;[^}]*min-height:\s*36px;[^}]*font-size:\s*13px;/s,
  )
})

test('K-line status shows the quote timestamp to seconds without a redundant prefix', () => {
  assert.doesNotMatch(stockSource, /const lastBarDate = ref\(''\)/)
  assert.doesNotMatch(stockSource, /lastBarDate\.value\s*=/)
  assert.match(
    stockSource,
    /return String\(basic\.value\?\.quoteTime \|\| ''\)\.replace\('T', ' '\)\.slice\(0, 19\)/,
  )
  assert.doesNotMatch(stockSource, /数据截至/)
  assert.match(stockSource, /if \(!isIntraday\.value \|\| !intraday\.value\) return ''/)
  assert.match(
    stockSource,
    /class="chart-canvas-head"[\s\S]*?v-if="dailyDataTime" class="chart-data-status"[\s\S]*?<span class="daily-data-time">\{\{ dailyDataTime \}\}<\/span>/s,
  )
  assert.match(stockSource, /v-if="isIntraday && intradayDataTime" class="intraday-asof"/)
  assert.match(stockSource, /\.chart-data-status\s*\{[^}]*padding-right:\s*8px;/s)
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.daily-data-time,\s*\.intraday-asof\s*\{[^}]*order:\s*2;/s,
  )
})

test('dense MACD cross markers are spaced and labels avoid collisions', () => {
  assert.match(stockSource, /const markerCrosses = spaceChartSignals\(crosses, 5\)/)
  assert.match(stockSource, /if \(markerCrosses\[i\] === 'golden'\) goldenPoints\.push/)
  assert.match(stockSource, /if \(markerCrosses\[i\] === 'death'\) deathPoints\.push/)
  assert.match(
    stockSource,
    /name: '金叉',[\s\S]*?symbolOffset: \[0, -5\],[\s\S]*?labelLayout: \{ hideOverlap: true \},/s,
  )
  assert.match(
    stockSource,
    /name: '死叉',[\s\S]*?symbolOffset: \[0, 5\],[\s\S]*?labelLayout: \{ hideOverlap: true \},/s,
  )
})

test('K-line renders confirmed MACD top and bottom divergence markers', () => {
  assert.match(stockSource, /detectMacdDivergences\(highs, lows, closes, dif\)/)
  assert.match(stockSource, /const spacedDivergences = spaceChartSignals\(divergenceSignals, 8\)/)
  assert.match(stockSource, /name: '顶背离',[\s\S]*?symbol: 'pin',[\s\S]*?formatter: '顶'[\s\S]*?labelLayout: \{ hideOverlap: false \}/s)
  assert.match(stockSource, /name: '底背离',[\s\S]*?symbol: 'pin',[\s\S]*?formatter: '底'[\s\S]*?labelLayout: \{ hideOverlap: false \}/s)
  assert.match(stockSource, /MACD 顶背离（已确认）/)
  assert.match(stockSource, /MACD 底背离（已确认）/)
})

test('K-line surfaces the dedicated key resistance level', () => {
  assert.match(stockSource, /priceStructure\.keyResistance/)
  assert.match(stockSource, />\s*关键阻力 \{\{ fmtNum\(priceStructure\.keyResistance\.price\) \}\}/)
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
  assert.match(stockSource, /\.chart-zoom-button\s*\{[^}]*width:\s*28px;[^}]*height:\s*28px;/s)
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.chart-zoom-button\s*\{[^}]*width:\s*44px;[^}]*height:\s*44px;/s,
  )
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.chart-zoom-controls\s*\{[^}]*order:\s*2;[^}]*justify-self:\s*end;/s,
  )
})

test('mobile K-line tooltip lingers, fades, and uses a compact translucent card', () => {
  assert.match(
    stockSource,
    /bindLongPress\(\{[\s\S]*?onDeactivate:[\s\S]*?deactivateDelay:\s*500,/s,
  )
  assert.match(stockSource, /transitionDuration:\s*isMobileChart\.value \? 0\.36 : 0,/)
  assert.match(stockSource, /transition:opacity 0\.18s ease,visibility 0\.18s ease;/)
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.kline-tip__card\s*\{[^}]*width:\s*min\(232px, calc\(100vw - 24px\)\);[^}]*padding:\s*8px 10px 7px;[^}]*background:\s*rgba\(255, 255, 255, 0\.92\);[^}]*font-size:\s*10px;[^}]*line-height:\s*1\.2;/s,
  )
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.kline-tip__price\s*\{[^}]*font-size:\s*18px;/s,
  )
  assert.match(
    stockSource,
    /@media \(max-width: 820px\)[\s\S]*?\.kline-tip__chip\s*\{[^}]*padding:\s*2px 5px;[^}]*font-size:\s*9px;/s,
  )
})

test('mobile K-line release hides both the tooltip and crosshair', () => {
  assert.match(
    stockSource,
    /const hideChartInspection = \(\) => \{[\s\S]*?type: 'updateAxisPointer',[\s\S]*?currTrigger: 'leave'[\s\S]*?type: 'hideTip'[\s\S]*?onDeactivate: hideChartInspection,/s,
  )
})
