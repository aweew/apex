import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')

test('mobile search overlay position is not coupled to visual viewport scrolling', () => {
  assert.doesNotMatch(appSource, /visualViewport\?\.addEventListener\('scroll'/)
  assert.doesNotMatch(appSource, /--search-viewport-top/)
})

test('mobile search overlay uses the native dynamic viewport height', () => {
  assert.match(appSource, /height:\s*100dvh;/)
})

test('mobile search overlay stays above the sticky navigation', () => {
  assert.match(appSource, /\.nav\s*\{[\s\S]*?z-index:\s*100;/)
  assert.match(
    appSource,
    /@media \(max-width: 900px\)[\s\S]*?\.search-layer\s*\{[\s\S]*?z-index:\s*101;/,
  )
})
