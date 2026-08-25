import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { BRAND, brandEyebrow } from './identity.js'

const brandedFiles = [
  '../../index.html',
  '../../public/brand/arc-lockup-stacked.svg',
  '../../public/brand/arc-lockup.svg',
  '../../public/brand/arc-solid.svg',
  '../../public/brand/logo-concepts.html',
  '../components/GlossaryPanel.vue',
  '../glossary/terms.js',
  '../utils/glossaryShareSheet.js',
  '../utils/holdingShareSheet.js',
  '../utils/marketShareSheet.js',
  '../views/HeatmapView.vue',
  '../views/IndexBoardView.vue',
  '../views/LimitUpLadderView.vue',
  '../../../apex-mini/miniprogram/app.json',
  '../../../apex-mini/miniprogram/pages/decision/decision.json',
  '../../../apex-mini/miniprogram/pages/index/index.wxml',
  '../../../apex-mini/miniprogram/pages/ladder/ladder.json',
  '../../../apex-mini/miniprogram/pages/portfolio/portfolio.json',
]
const loginSource = await readFile(new URL('../views/LoginView.vue', import.meta.url), 'utf8')
const indexSource = await readFile(new URL('../../index.html', import.meta.url), 'utf8')
const miniIndexSource = await readFile(new URL('../../../apex-mini/miniprogram/pages/index/index.wxml', import.meta.url), 'utf8')

test('brand identity uses Lingji as the canonical Chinese name', () => {
  assert.equal(BRAND.nameZh, '灵极')
  assert.equal(BRAND.nameEn, 'Apex')
  assert.equal(BRAND.slogan, '灵极 Apex｜洞见·观变')
  assert.equal(BRAND.taglineShort, '洞见 · 观变')
  assert.equal(BRAND.documentTitle, '灵极 Apex｜洞见·观变')
  assert.match(indexSource, new RegExp(`<title>${BRAND.documentTitle}</title>`))
  assert.match(miniIndexSource, new RegExp(BRAND.slogan))
  assert.equal(brandEyebrow('策略实验室'), '灵极 · 策略实验室')
})

test('user-facing brand surfaces no longer contain the old Chinese name', async () => {
  for (const relativePath of brandedFiles) {
    const source = await readFile(new URL(relativePath, import.meta.url), 'utf8')
    assert.doesNotMatch(source, /灵枢/, relativePath)
  }
})

test('login copy reuses the canonical Chinese brand without English spacing', () => {
  assert.doesNotMatch(loginSource, /brand-name=/)
  assert.match(loginSource, /:description="`进入\$\{BRAND\.nameZh\}量化研究平台`"/)
  assert.match(loginSource, />进入\{\{ BRAND\.nameZh \}\}<\/el-button>/)
})
