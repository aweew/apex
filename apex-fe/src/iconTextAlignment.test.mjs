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

test('mobile page actions use compact visual heights with distinct icon controls', () => {
  const mobileStyles = globalStyles.slice(globalStyles.indexOf('@media (max-width: 560px)'))

  assert.match(
    mobileStyles,
    /@media \(max-width: 900px\)\s*\{[\s\S]*?\.page \.el-button:not\(\.is-link\):not\(\.is-text\):not\(\.is-circle\)\s*\{[^}]*height:\s*36px !important;[^}]*min-height:\s*36px !important;[^}]*padding:\s*0 12px;[^}]*border-radius:\s*6px;/s,
  )
  assert.match(
    mobileStyles,
    /\.page \.el-button\.is-circle\s*\{[^}]*width:\s*40px !important;[^}]*min-width:\s*40px !important;[^}]*height:\s*40px !important;[^}]*min-height:\s*40px !important;[^}]*padding:\s*0;/s,
  )
  assert.match(
    mobileStyles,
    /\.page \.el-segmented\s*\{[^}]*min-height:\s*36px !important;[^}]*padding:\s*3px;/s,
  )
  assert.match(
    mobileStyles,
    /\.page \.el-radio-button__inner\s*\{[^}]*height:\s*36px !important;[^}]*min-height:\s*36px !important;/s,
  )
})
