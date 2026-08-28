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

test('pre-market report presents one editorial reading flow instead of a status dashboard', () => {
  assert.match(source, /parsePreMarketReport/)
  assert.match(source, /class="report-thesis"/)
  assert.match(source, /核心观点/)
  assert.match(source, /最大风险/)
  assert.match(source, /v-for="section in reportDocument\.sections"/)
  assert.doesNotMatch(source, /class="report-status"/)
  assert.doesNotMatch(source, /<details/)
  assert.doesNotMatch(source, /<article>\{\{ report\.content \}\}<\/article>/)
})

test('pre-market report renders holding reminders as scannable visual cards', () => {
  assert.match(source, /PreMarketHoldingCard/)
  assert.match(source, /section\.holdings/)
  assert.match(source, /v-for="holding in section\.holdings"/)
  assert.match(source, /class="holding-grid"/)
})

test('pre-market report keeps the full article readable and mobile safe', () => {
  assert.match(source, /class="report-article"/)
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
  assert.match(source, />分享<\/el-button>/)
  assert.match(source, /title="分享盘前观点长图"/)
  assert.match(source, /复制图片/)
  assert.match(source, /下载 PNG/)
})
