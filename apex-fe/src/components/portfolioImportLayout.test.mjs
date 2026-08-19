import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import '../api/portfolioImageImport.test.mjs'

const source = await readFile(new URL('./PortfolioImportDialog.vue', import.meta.url), 'utf8')
const portfolioSource = await readFile(new URL('../views/PortfolioView.vue', import.meta.url), 'utf8')

test('portfolio import exposes target, merge semantics, preview, and row-level errors', () => {
  assert.match(source, /class="import-target"/)
  assert.match(source, /不会删除其他持仓/)
  assert.match(source, /class="import-preview"/)
  assert.match(source, /row\.error \|\| row\.serverError/)
  assert.match(source, /再次导入/)
})

test('portfolio import accepts a screenshot and exposes recognition feedback', () => {
  assert.match(source, /recognizePortfolioImage/)
  assert.match(source, /accept="image\/png,image\/jpeg,image\/webp"/)
  assert.match(source, /@drop\.prevent="onImageDrop"/)
  assert.match(source, /@paste="onDialogPaste"/)
  assert.match(source, /正在识别截图/)
  assert.match(source, /recognitionError/)
  assert.match(source, /识别结果仅供预览/)
})

test('portfolio import locks mutable controls while a request is in flight', () => {
  assert.match(source, /function updateDialogVisible\(visible\)/)
  assert.match(source, /if \(busy\.value && !visible\) return/)
  assert.match(source, /:disabled="busy"/)
  assert.match(source, /row\.status === 'success' \|\| busy/)
})

test('portfolio import keeps desktop and mobile layouts distinct', () => {
  assert.match(source, /class="import-desktop-table"/)
  assert.match(source, /class="import-mobile-list"/)
  assert.match(source, /@media \(max-width: 640px\)/)
  assert.match(source, /width: calc\(100% - 24px\) !important/)
  assert.match(source, /max-height: calc\(100dvh - 24px\)/)
  assert.match(source, /flex-direction: column/)
  assert.match(source, /flex: 1 1 auto/)
  assert.match(source, /min-height: 44px/)
})

test('portfolio view delegates import behavior to the dedicated dialog', () => {
  assert.match(portfolioSource, /import PortfolioImportDialog from '\.\.\/components\/PortfolioImportDialog\.vue'/)
  assert.match(portfolioSource, /<PortfolioImportDialog/)
  assert.doesNotMatch(portfolioSource, /v-model="importText"/)
})
