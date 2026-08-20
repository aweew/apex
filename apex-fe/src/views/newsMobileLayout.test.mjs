import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const newsSource = await readFile(new URL('./NewsView.vue', import.meta.url), 'utf8')

test('mobile news source filters use a balanced compact grid', () => {
  assert.match(newsSource, /class="source-filter-group"/)
  assert.match(newsSource, /class="source-filter-count">\(\{\{ counts\[tab\.key\] \?\? 0 \}\}\)<\/small>/)
  assert.match(
    newsSource,
    /@media \(max-width: 560px\) \{[\s\S]*?\.source-filter-group\s*\{[\s\S]*?display:\s*grid;[\s\S]*?grid-template-columns:\s*repeat\(3, minmax\(0, 1fr\)\);/,
  )
  assert.match(
    newsSource,
    /\.source-filter-group :deep\(\.el-radio-button__inner\)\s*\{[\s\S]*?min-height:\s*44px;[\s\S]*?border-radius:\s*6px;/,
  )
})

test('news card keeps its original-link target while rendering a smaller link icon', () => {
  assert.match(newsSource, /class="op-btn op-btn--link"/)
  assert.match(newsSource, /\.op-btn--link \.el-icon\s*\{[\s\S]*?font-size:\s*14px;/)
})
