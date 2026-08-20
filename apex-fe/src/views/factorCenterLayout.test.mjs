import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./FactorCenterView.vue', import.meta.url), 'utf8')

test('factor center exposes six factor categories and fixed alpha weights', () => {
  assert.match(source, /v-for="category in detail\.categories"/)
  assert.match(source, /Alpha Score/)
  assert.match(source, /Momentum 30%/)
  assert.match(source, /ROE 20%/)
  assert.match(source, /Earnings Growth 20%/)
  assert.match(source, /Volume 15%/)
  assert.match(source, /Market Strength 15%/)
})

test('factor center keeps missing data visible instead of rendering a fabricated value', () => {
  assert.match(source, /factor\.status === 'MISSING'/)
  assert.match(source, /暂无数据/)
  assert.match(source, /detail\.coverage/)
  assert.match(source, /detail\.value\?\.alphaScore == null/)
  assert.match(source, /component\.asOf \? `截至 \$\{component\.asOf\}` : '时点缺失'/)
  assert.match(source, /factor\.asOf \? `截至 \$\{factor\.asOf\}` : '时点缺失'/)
})

test('factor center resolves names and ignores stale stock responses', () => {
  assert.match(source, /let requestSeq = 0/)
  assert.match(source, /const currentRequest = \+\+requestSeq/)
  assert.match(source, /if \(currentRequest !== requestSeq\) return/)
  assert.match(source, /async function resolveSecurityCode\(query\)/)
  assert.match(source, /candidates\.find\(\(stock\) => String\(stock\.name/)
})

test('factor center uses stable responsive grids and mobile touch targets', () => {
  assert.match(source, /\.factor-layout\s*\{[\s\S]*?grid-template-columns:\s*minmax\(260px, 340px\) minmax\(0, 1fr\);/)
  assert.match(source, /\.factor-categories\s*\{[\s\S]*?grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\);/)
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.factor-layout,[\s\S]*?\.factor-categories\s*\{[\s\S]*?grid-template-columns:\s*minmax\(0, 1fr\);/)
  assert.match(source, /@media \(max-width: 820px\)[\s\S]*?\.factor-query :deep\(\.el-input__wrapper\)[\s\S]*?min-height:\s*44px;/)
})
