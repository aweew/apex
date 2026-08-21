import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const decisionSource = await readFile(new URL('./DecisionView.vue', import.meta.url), 'utf8')

test('decision buys show verifiable stock highlights and recent news on desktop and mobile', () => {
  assert.match(decisionSource, /label="亮点 \/ 消息面"/)
  assert.ok((decisionSource.match(/row\.highlights\?\.length/g) || []).length >= 2)
  assert.ok((decisionSource.match(/row\.recentNews\?\.length/g) || []).length >= 2)
  assert.match(decisionSource, /消息面 · \{\{ row\.newsSummary \}\}/)
  assert.match(decisionSource, /class="decision-mobile-insight"/)
})

test('recent stock news preserves source and published time before the original title', () => {
  assert.match(decisionSource, /newsSourceLabel\(news\.source\) \}\} \{\{ fmtNewsTime\(news\.publishedAt\) \}\}/)
  assert.match(decisionSource, /:href="news\.url \|\| undefined"/)
  assert.match(decisionSource, /\.stock-news-item\s*\{[^}]*grid-template-columns:\s*auto minmax\(0, 1fr\);/)
})
