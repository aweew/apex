export const MAIN_NAV_GROUPS = [
  {
    label: '工作台',
    items: [
      { to: '/dashboard', label: '看板' },
      { to: '/decision', label: '决策', activePaths: ['/decision', '/signals'] },
      { to: '/watchlist', label: '自选' },
      { to: '/observe', label: '观察池' },
      { to: '/portfolio', label: '组合' },
      { to: '/paper', label: '模拟盘' },
    ],
  },
  {
    label: '市场',
    items: [
      { to: '/market', label: '行情' },
      { to: '/screener', label: '股票' },
      { to: '/limit-up', label: '连板天梯' },
      { to: '/news', label: '资讯' },
    ],
  },
  {
    label: '工具',
    items: [
      { to: '/backtest', label: '回测' },
      { to: '/sync', label: '同步' },
    ],
  },
]

export const MOBILE_BOTTOM_NAV_ITEMS = [
  { to: '/dashboard', label: '看板', icon: 'DataBoard', activePaths: ['/dashboard'] },
  { to: '/market', label: '行情', icon: 'TrendCharts', activePaths: ['/market'] },
  {
    to: '/screener',
    label: '股票',
    icon: 'Histogram',
    activePaths: ['/screener', '/watchlist', '/observe', '/stock'],
  },
  {
    to: '/portfolio',
    label: '组合',
    icon: 'Briefcase',
    activePaths: ['/portfolio', '/paper', '/holding', '/trades'],
  },
  { action: 'menu', label: '我的', icon: 'User' },
]

export const PRIMARY_SHORTCUTS = {
  1: '/dashboard',
  2: '/decision',
  3: '/observe',
  4: '/portfolio',
}
