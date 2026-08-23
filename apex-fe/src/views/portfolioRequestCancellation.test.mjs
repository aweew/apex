import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./PortfolioView.vue', import.meta.url), 'utf8')

test('portfolio detail aborts an older request before loading a newly selected portfolio', () => {
  assert.match(source, /let detailRequestController = null/)
  assert.match(source, /detailRequestController\?\.abort\(\)/)
  assert.match(source, /const requestController = new AbortController\(\)[\s\S]*?detailRequestController = requestController/)
  assert.match(source, /portfolioDetail\(id, \{ signal: requestController\.signal \}\)/)
  assert.match(source, /listPortfolioDaily\(id, 60, \{ signal: requestController\.signal \}\)/)
  assert.match(source, /listPortfolioIntraday\(id, \{ signal: requestController\.signal \}\)/)
  assert.match(source, /if \(e\?\.code !== 'ERR_CANCELED'\)/)
  assert.match(source, /if \(detailRequestController === requestController\) detailLoading\.value = false/)
})

test('portfolio detail aborts its pending request when the view is unmounted', () => {
  assert.match(source, /onBeforeUnmount\(\(\) => \{[\s\S]*?detailRequestController\?\.abort\(\)/)
})
