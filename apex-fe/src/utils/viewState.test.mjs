import assert from 'node:assert/strict'
import test from 'node:test'
import { ref } from 'vue'

import { readViewState, useSessionViewState, writeViewState } from './viewState.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  }
}

test('view state round-trips filters in session storage', () => {
  const storage = memoryStorage()
  writeViewState('watchlist', { keyword: '银行', onlyHasBars: true }, storage)
  assert.deepEqual(readViewState('watchlist', storage), { keyword: '银行', onlyHasBars: true })
})

test('view state ignores malformed stored values', () => {
  const storage = memoryStorage()
  storage.setItem('apex.viewState.watchlist', '{broken')
  assert.deepEqual(readViewState('watchlist', storage), {})
})

test('session view state restores fields and persists later changes', () => {
  const storage = memoryStorage()
  writeViewState('screener', { activeTab: 'custom', form: { peMax: '20' } }, storage)
  const activeTab = ref('market')
  const form = ref({ peMin: '', peMax: '', excludeSt: true })

  useSessionViewState('screener', { activeTab, form }, storage)
  assert.equal(activeTab.value, 'custom')
  assert.deepEqual(form.value, { peMin: '', peMax: '20', excludeSt: true })

  form.value.peMin = '5'
  assert.deepEqual(readViewState('screener', storage).form, {
    peMin: '5',
    peMax: '20',
    excludeSt: true,
  })
})
