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
