import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./ApexAiView.vue', import.meta.url), 'utf8')

test('Apex AI is a real analyst workspace with context and structured evidence', () => {
  assert.match(source, /Apex AI/)
  assert.match(source, /小灵/)
  assert.match(source, /analyzeWithXiaoling/)
  assert.match(source, /analysis\.contributors/)
  assert.match(source, /analysis\.metrics/)
  assert.match(source, /analysis\.suggestions/)
  assert.match(source, /dataLevel/)
  assert.match(source, /askSuggested\(prompt, message\.analysis\.analysisType\)/)
  assert.match(source, /portfolioId:\s*analysisMode\.value === 'PORTFOLIO' \? selectedPortfolioId\.value : null/)
  assert.match(source, /strategyId:\s*analysisMode\.value === 'STRATEGY' \? selectedStrategyId\.value \|\| null : null/)
})

test('Apex AI exposes portfolio and strategy analysis without a marketing hero', () => {
  assert.match(source, /PORTFOLIO/)
  assert.match(source, /STRATEGY/)
  assert.match(source, /为什么今天收益/)
  assert.match(source, /策略最近为什么失效/)
  assert.doesNotMatch(source, /class="hero/)
})

test('Apex AI has stable desktop and mobile workbench geometry', () => {
  assert.match(source, /\.ai-workbench\s*\{[^}]*grid-template-columns:\s*minmax\(220px,\s*280px\) minmax\(0,\s*1fr\);/s)
  assert.match(source, /\.analysis-layout\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1\.35fr\) minmax\(240px,\s*0\.65fr\);/s)
  assert.match(source, /@media \(max-width: 900px\)[\s\S]*?\.ai-workbench\s*\{[^}]*grid-template-columns:\s*1fr;/s)
  assert.match(source, /@media \(max-width: 640px\)[\s\S]*?\.analysis-layout\s*\{[^}]*grid-template-columns:\s*1fr;/s)
  assert.match(source, /min-height:\s*44px/)
})

test('Apex AI shows an assistant card immediately while analysis is pending', () => {
  assert.match(source, /const pendingMessageId = `\$\{Date\.now\(\)\}-assistant`/)
  assert.match(source, /messages\.value\.push\([\s\S]*role: 'user'[\s\S]*role: 'assistant',[\s\S]*pending: true[\s\S]*\)/)
  assert.match(source, /message\.role === 'assistant' && message\.pending/)
  assert.match(source, /messages\.value\.splice\(pendingMessageIndex, 1, assistantMessage\)/)
  assert.match(source, /if \(!response\.data\) throw new Error\('分析结果为空，请稍后重试'\)/)
  assert.match(source, /if \(pendingMessageIndex >= 0\) messages\.value\.splice/)
  assert.doesNotMatch(source, /v-if="analyzing" class="thinking-message"/)
})
