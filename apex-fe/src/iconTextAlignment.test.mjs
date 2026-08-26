import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const globalStyles = await readFile(new URL('./style.css', import.meta.url), 'utf8')

test('inline icons align with adjacent text through the shared page styles', () => {
  assert.match(
    globalStyles,
    /\.page \.el-icon\s*\{[^}]*vertical-align:\s*middle;/s,
  )
  assert.match(
    globalStyles,
    /\.page :where\(button, a, \.el-link\) > svg:not\(:only-child\)\s*\{[^}]*vertical-align:\s*middle;/s,
  )
})
