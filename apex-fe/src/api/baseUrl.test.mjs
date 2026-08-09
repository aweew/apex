import assert from 'node:assert/strict'
import test from 'node:test'

import { buildApiUrl, resolveApiBase } from './baseUrl.js'

test('uses an explicitly configured API base in every environment', () => {
  assert.equal(resolveApiBase('https://api.example.com/apex/', false), 'https://api.example.com/apex')
})

test('keeps the local backend default in development', () => {
  assert.equal(resolveApiBase('', true), 'http://127.0.0.1:8080/apex')
})

test('uses the same-origin API path in production', () => {
  assert.equal(resolveApiBase(undefined, false), '/apex')
  assert.equal(buildApiUrl('/api/export/observe', '/apex'), '/apex/api/export/observe')
})

test('joins API paths without duplicate slashes', () => {
  assert.equal(
    buildApiUrl('api/export/watchlist?groupName=A', 'https://api.example.com/apex/'),
    'https://api.example.com/apex/api/export/watchlist?groupName=A',
  )
})
