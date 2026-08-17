import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const globalStyles = await readFile(new URL('./style.css', import.meta.url), 'utf8')

test('shared page title and module label use visual center alignment', () => {
  assert.match(
    globalStyles,
    /\.header > div:first-child\s*\{[^}]*align-items:\s*center;/,
  )
})
