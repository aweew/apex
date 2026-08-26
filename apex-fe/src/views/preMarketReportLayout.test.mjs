import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PreMarketReportView.vue', import.meta.url), 'utf8')

test('pre-market report exposes generation state and explicit data gaps', () => {
  assert.match(source, /report\.missingData/)
  assert.match(source, /以下项目不会被当作中性数据参与判断/)
  assert.match(source, /report\.value\?\.reportSource/)
  assert.match(source, /report\.portfolioCount/)
})

test('pre-market report keeps long content readable on mobile without tables', () => {
  assert.match(source, /white-space:\s*pre-wrap/)
  assert.match(source, /overflow-wrap:\s*anywhere/)
  assert.match(source, /@media \(max-width: 760px\)/)
  assert.doesNotMatch(source, /<el-table/)
})
