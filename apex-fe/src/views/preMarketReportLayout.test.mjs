import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PreMarketReportView.vue', import.meta.url), 'utf8')
const sectionSource = await readFile(new URL('../components/PreMarketReportSections.vue', import.meta.url), 'utf8')

test('pre-market report exposes generation state without listing missing data', () => {
  assert.doesNotMatch(source, /report\.missingData/)
  assert.doesNotMatch(source, /本次数据缺口|以下项目不会被当作中性数据参与判断/)
  assert.match(source, /report\.value\?\.reportSource/)
  assert.match(source, /report\.portfolioCount/)
  assert.match(source, /report\.value\?\.marketDataAsOf/)
  assert.match(source, /report\.focusChanges/)
  assert.match(source, /预生成/)
})

test('pre-market report presents one editorial reading flow instead of a status dashboard', () => {
  assert.match(source, /parsePreMarketReport/)
  assert.match(source, /class="report-thesis"/)
  assert.match(source, /PreMarketReportSections/)
  assert.match(source, /class="market-temperature"/)
  assert.match(source, /核心观点/)
  assert.match(source, /最大风险/)
  assert.match(source, /:sections="reportDocument\.sections"/)
  assert.match(sectionSource, /v-for="section in sections"/)
  assert.doesNotMatch(source, /class="report-status"/)
  assert.doesNotMatch(source, /<details/)
  assert.doesNotMatch(source, /<article>\{\{ report\.content \}\}<\/article>/)
})

test('pre-market report renders holding reminders as scannable visual cards', () => {
  assert.match(sectionSource, /PreMarketHoldingCard/)
  assert.match(sectionSource, /section\.holdings/)
  assert.match(sectionSource, /sortedHoldings\(section\)/)
  assert.match(sectionSource, /class="holding-grid"/)
  assert.match(sectionSource, /class="holding-action-grid"/)
  assert.match(sectionSource, /class="portfolio-risk-block"/)
  assert.match(sectionSource, /处理顺序/)
  assert.match(sectionSource, /:priority="index \+ 1"/)
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
