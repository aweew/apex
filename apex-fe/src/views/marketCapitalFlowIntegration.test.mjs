import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const marketSource = await readFile(new URL('./IndexBoardView.vue', import.meta.url), 'utf8')
const capitalFlowSource = await readFile(new URL('./CapitalFlowView.vue', import.meta.url), 'utf8')
const sectorSource = await readFile(new URL('./SectorBoardView.vue', import.meta.url), 'utf8')
const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')
const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')

test('market center contains a capital-flow tab with the complete capital flow view', () => {
  assert.match(marketSource, /import CapitalFlowView from '\.\/CapitalFlowView\.vue'/)
  assert.match(marketSource, /marketTab === 'capital-flow'/)
  assert.match(marketSource, /<CapitalFlowView v-else-if="marketTab === 'capital-flow'" embedded \/>/)
  assert.match(capitalFlowSource, /defineProps\(\{[\s\S]*?embedded:/)
  assert.match(capitalFlowSource, /v-if="!embedded" class="header capital-flow-header"/)
  assert.match(capitalFlowSource, /v-else class="capital-flow-embedded-header"/)
})

test('legacy capital flow links redirect into the market capital-flow tab', () => {
  assert.match(routerSource, /path: '\/capital-flow',[\s\S]*?redirect: \(to\) => \(\{[\s\S]*?path: '\/market',[\s\S]*?tab: 'capital-flow'/)
})

test('market center supports direct navigation to the dragon tiger section', () => {
  assert.match(capitalFlowSource, /id="dragon-tiger"/)
  assert.match(marketSource, /'#dragon-tiger'/)
  assert.match(marketSource, /scrollIntoView\(\{ behavior: 'smooth', block: 'start' \}\)/)
})

test('market center contains the complete sector board as a tab', () => {
  assert.match(marketSource, /import SectorBoardView from '\.\/SectorBoardView\.vue'/)
  assert.match(marketSource, /marketTab === 'sector'/)
  assert.match(marketSource, /<SectorBoardView v-else embedded \/>/)
  assert.match(sectorSource, /defineProps\(\{[\s\S]*?embedded:/)
})

test('legacy sector links redirect into the market sector tab', () => {
  assert.match(routerSource, /path: '\/sector',[\s\S]*?redirect: \(to\) => \(\{[\s\S]*?path: '\/market',[\s\S]*?tab: 'sector'/)
})

test('navigation and command search no longer expose capital flow or sector as standalone pages', () => {
  assert.doesNotMatch(appSource, /to: '\/capital-flow'/)
  assert.doesNotMatch(appSource, /to: '\/sector'/)
  assert.match(appSource, /to: '\/market', label: '行情',[\s\S]*?资金 北向 主力 龙虎榜 板块 行业 概念 题材/)
})
