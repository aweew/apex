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

test('shared page headers use balanced title and module label sizes', () => {
  assert.match(
    globalStyles,
    /\.page\s*\{[^}]*--page-title-size:\s*28px;[^}]*--page-module-size:\s*16px;/,
  )
  assert.match(
    globalStyles,
    /\.page \.header > div:first-child > h1\s*\{[^}]*font-size:\s*var\(--page-title-size\) !important;[^}]*font-weight:\s*650 !important;/,
  )
  assert.match(
    globalStyles,
    /\.page \.header > div:first-child > \.eyebrow\s*\{[^}]*font-size:\s*var\(--page-module-size\) !important;[^}]*font-weight:\s*700 !important;/,
  )
  assert.doesNotMatch(globalStyles, /\.header h1\s*\{[^}]*font-size:\s*clamp\(/)
})

test('comfortable density keeps one responsive page header hierarchy', () => {
  assert.doesNotMatch(globalStyles, /\.shell\.dense/)
  assert.match(
    globalStyles,
    /@media \(max-width: 560px\)\s*\{[\s\S]*?\.page\s*\{[^}]*--page-title-size:\s*23px;[^}]*--page-module-size:\s*14px;/,
  )
})
