import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const source = await readFile(new URL('./portfolio.js', import.meta.url), 'utf8')

test('screenshot preview uses multipart form data and the non-persistent preview endpoint', () => {
  assert.match(source, /function recognizePortfolioImage\(portfolioId, file\)/)
  assert.match(source, /new FormData\(\)/)
  assert.match(source, /formData\.append\('file', file\)/)
  assert.match(source, /\/import\/image\/preview/)
  assert.match(source, /timeout: 90000/)
})
