import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const appSource = await readFile(new URL('../App.vue', import.meta.url), 'utf8')

test('command center searches routes and stocks from the same overlay', () => {
  assert.match(appSource, /const commandResults = computed/)
  assert.match(appSource, /const filteredRouteCommands = computed/)
  assert.match(appSource, /goCommand\(item\)/)
  assert.match(appSource, /命令中心/)
  assert.match(appSource, /页面与操作/)
  assert.match(appSource, /股票/)
})

test('command center supports keyboard selection instead of only opening the first stock result', () => {
  assert.match(appSource, /function moveCommandSelection\(step\)/)
  assert.match(appSource, /@keydown\.down\.prevent="moveCommandSelection\(1\)"/)
  assert.match(appSource, /@keydown\.up\.prevent="moveCommandSelection\(-1\)"/)
  assert.match(appSource, /@keydown\.enter\.prevent="runSelectedCommand"/)
  assert.match(appSource, /:aria-selected="index === commandSelection"/)
})

test('navigation renders the current page data freshness instead of only service health', () => {
  assert.match(appSource, /dataFreshness/)
  assert.match(appSource, /data-status/)
  assert.match(appSource, /dataFreshness\.label/)
})
