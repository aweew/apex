import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const globalStyles = await readFile(new URL('./style.css', import.meta.url), 'utf8')

test('shared page title and module label use one visual center', () => {
  assert.match(
    globalStyles,
    /\.header > div:first-child\s*\{[^}]*align-items:\s*center;[^}]*column-gap:\s*8px;/,
  )
  assert.match(
    globalStyles,
    /\.header > div:first-child > h1::after\s*\{[^}]*display:\s*inline-block;[^}]*content:\s*"·";[^}]*transform:\s*translateY\(-0\.14em\);/,
  )
})

test('shared page headers use balanced title and module label sizes', () => {
  assert.match(
    globalStyles,
    /\.page\s*\{[^}]*--page-title-size:\s*28px;[^}]*--page-module-size:\s*16px;/,
  )
  assert.match(
    globalStyles,
    /\.page \.header > div:first-child > h1\s*\{[^}]*margin:\s*0 !important;[^}]*font-size:\s*var\(--page-title-size\) !important;[^}]*font-weight:\s*650 !important;[^}]*line-height:\s*1\.15 !important;/,
  )
  assert.match(
    globalStyles,
    /\.page \.header > div:first-child > \.eyebrow\s*\{[^}]*color:\s*var\(--accent\) !important;[^}]*font-size:\s*var\(--page-module-size\) !important;[^}]*font-weight:\s*700 !important;[^}]*line-height:\s*1 !important;/,
  )
  assert.doesNotMatch(globalStyles, /\.header h1\s*\{[^}]*font-size:\s*clamp\(/)
})

test('shared page subtitles and glossary titles keep one header rhythm', () => {
  assert.match(
    globalStyles,
    /\.page \.header > div:first-child > p:not\(\.eyebrow\)\s*\{[^}]*flex:\s*0 0 100% !important;[^}]*width:\s*100% !important;[^}]*max-width:\s*none !important;[^}]*margin:\s*8px 0 0 !important;[^}]*font-size:\s*13px !important;[^}]*line-height:\s*1\.55 !important;/,
  )
  assert.match(
    globalStyles,
    /\.page \.header h1 \.term-tip\s*\{[^}]*border-bottom:\s*0;/,
  )
})

test('comfortable density keeps one responsive page header hierarchy', () => {
  assert.doesNotMatch(globalStyles, /\.shell\.dense/)
  assert.match(
    globalStyles,
    /@media \(max-width: 560px\)\s*\{[\s\S]*?\.page\s*\{[^}]*--page-title-size:\s*23px;[^}]*--page-module-size:\s*14px;/,
  )
})
