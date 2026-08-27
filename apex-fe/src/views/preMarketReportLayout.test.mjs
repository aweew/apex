import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PreMarketReportView.vue', import.meta.url), 'utf8')

test('pre-market report exposes generation state without listing missing data', () => {
  assert.doesNotMatch(source, /report\.missingData/)
  assert.doesNotMatch(source, /本次数据缺口|以下项目不会被当作中性数据参与判断/)
  assert.match(source, /report\.value\?\.reportSource/)
  assert.match(source, /report\.portfolioCount/)
})

test('pre-market report presents decision first and renders parsed sections', () => {
  assert.match(source, /parsePreMarketReport/)
  assert.match(source, /class="decision-brief"/)
  assert.match(source, /今日判断/)
  assert.match(source, /优先方向/)
  assert.match(source, /最大风险/)
  assert.match(source, /v-for="section in primarySections"/)
  assert.doesNotMatch(source, /<article>\{\{ report\.content \}\}<\/article>/)
})

test('pre-market report renders holding reminders as scannable visual cards', () => {
  assert.match(source, /PreMarketHoldingCard/)
  assert.match(source, /section\.holdings/)
  assert.match(source, /v-for="holding in section\.holdings"/)
  assert.match(source, /class="holding-grid"/)
})

test('pre-market report keeps secondary sections collapsible and mobile safe', () => {
  assert.match(source, /<details[^>]*class="secondary-section"/)
  assert.match(source, /overflow-wrap:\s*anywhere/)
  assert.match(source, /@media \(max-width: 760px\)/)
  assert.doesNotMatch(source, /<el-table/)
})

test('pre-market report exposes a complete long-image sharing workflow', () => {
  assert.match(source, /PreMarketReportShareSheet/)
  assert.match(source, /captureElementBlob/)
  assert.match(source, /copyImageBlob/)
  assert.match(source, /downloadBlob/)
  assert.match(source, /shareFilename\('apex_pre_market'/)
  assert.match(source, />分享长图<\/el-button>/)
  assert.match(source, /title="分享盘前观点长图"/)
  assert.match(source, />复制图片<\/el-button>/)
  assert.match(source, />下载 PNG<\/el-button>/)
})
