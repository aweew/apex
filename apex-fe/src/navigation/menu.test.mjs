import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

import { MAIN_NAV_GROUPS, MOBILE_BOTTOM_NAV_ITEMS, PRIMARY_SHORTCUTS } from './menu.js'

const routerSource = await readFile(new URL('../router/index.js', import.meta.url), 'utf8')

test('main navigation keeps only the consolidated high-frequency entries', () => {
  assert.deepEqual(
    MAIN_NAV_GROUPS.map((group) => ({
      label: group.label,
      items: group.items.map((item) => [item.to, item.label]),
    })),
    [
      {
        label: '工作台',
        items: [
          ['/dashboard', '看板'],
          ['/decision', '智能决策'],
          ['/watchlist', '自选'],
          ['/observe', '观察池'],
          ['/portfolio', '组合'],
          ['/paper', '模拟盘'],
        ],
      },
      {
        label: '市场',
        items: [
          ['/market', '行情'],
          ['/screener', '股票'],
          ['/limit-up', '连板天梯'],
          ['/news', '资讯'],
        ],
      },
      {
        label: '工具',
        items: [
          ['/backtest', '回测'],
          ['/sync', '同步'],
        ],
      },
    ],
  )
})

test('standalone and low-frequency pages stay out of the main navigation', () => {
  const paths = MAIN_NAV_GROUPS.flatMap((group) => group.items.map((item) => item.to))

  for (const hiddenPath of ['/pre-market-report', '/ai-center', '/config', '/factors', '/holding', '/valuation', '/signals', '/pipeline', '/daily', '/hot', '/capital-flow', '/sector']) {
    assert.equal(paths.includes(hiddenPath), false, hiddenPath)
  }
})

test('legacy factor links redirect into the stock detail factor tab', () => {
  assert.doesNotMatch(routerSource, /const FactorCenterView =/)
  assert.match(routerSource, /path: '\/factors',[\s\S]*?redirect: \(to\) =>/)
  assert.match(routerSource, /query: \{ tab: 'factors' \}/)
})

test('primary shortcuts point to the consolidated workbench', () => {
  assert.deepEqual(PRIMARY_SHORTCUTS, {
    1: '/dashboard',
    2: '/decision',
    3: '/observe',
    4: '/portfolio',
  })
})

test('mobile bottom navigation exposes the five primary touch destinations', () => {
  assert.deepEqual(
    MOBILE_BOTTOM_NAV_ITEMS.map((item) => [item.to || item.action, item.label, item.icon]),
    [
      ['/dashboard', '看板', 'DataBoard'],
      ['/market', '行情', 'TrendCharts'],
      ['/screener', '股票', 'Histogram'],
      ['/portfolio', '组合', 'Briefcase'],
      ['/holding', '我的', 'User'],
    ],
  )
})
