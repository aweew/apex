import { request } from '../../utils/api'

interface MarketIndex { name?: string; close?: number; pctChg?: number; direction?: string }
interface MarketEffect { avgPctChg?: number; medianPctChg?: number; equalWeightPctChg?: number; microPctChg?: number; csi2000PctChg?: number; hs300PctChg?: number; hint?: string }
interface MarketTheme { name?: string; pctChg?: number }
interface MarketBrief {
  asOf?: string; stance?: string; stanceScore?: number; stanceReason?: string; positionAdvice?: string; dataLevel?: string
  indexLines?: string[]; indexes?: MarketIndex[]; hotThemes?: string[]; hotThemeItems?: MarketTheme[]; tips?: string[]; effect?: MarketEffect
  indexVolumeText?: string; breadthUp?: number; breadthDown?: number; breadthFlat?: number; limitUpCount?: number; limitDownCount?: number
}
interface HomeAction {
  code: string; name: string; strategyId?: string; score?: number; suggestedWeight?: number; reason?: string; exitRule?: string
  valuationLabel?: string; executableHint?: boolean; linkHint?: string; mainlineMatch?: boolean; mainlineName?: string
}
interface ObserveAlert { id?: string; code: string; name?: string; status?: string }
interface DashboardHome {
  market?: MarketBrief
  decision?: { actionDate?: string; hasToday?: boolean; buyCount?: number; sellCount?: number; executableCount?: number; riskNote?: string; topBuys?: HomeAction[]; topSells?: HomeAction[] }
  observeAlerts?: ObserveAlert[]
  dataHealth?: { level?: string; suggestion?: string; barsStaleCount?: number; barsEmptyCount?: number; watchlistCount?: number }
}

function toPercent(value?: number) {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return '--'
  const numberValue = Number(value)
  return `${numberValue > 0 ? '+' : ''}${numberValue.toFixed(2)}%`
}

function toWeight(value?: number) {
  if (value === undefined || value === null || Number.isNaN(Number(value))) return '--'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function direction(value?: number) {
  const numberValue = Number(value)
  if (Number.isNaN(numberValue) || numberValue === 0) return 'flat'
  return numberValue > 0 ? 'up' : 'down'
}

function buildIndexItems(market?: MarketBrief) {
  if (market?.indexes?.length) {
    return market.indexes.slice(0, 4).map((item) => ({
      name: item.name || '--',
      close: item.close === undefined || item.close === null ? '--' : Number(item.close).toFixed(2),
      pct: toPercent(item.pctChg),
      direction: item.direction || direction(item.pctChg),
    }))
  }
  return (market?.indexLines || []).slice(0, 4).map((line) => ({ name: line, close: '', pct: '', direction: 'flat' }))
}

function buildActionItems(actions?: HomeAction[]) {
  return (actions || []).map((item) => ({
    ...item,
    scoreText: item.score === undefined || item.score === null ? '--' : Number(item.score).toFixed(0),
    weightText: toWeight(item.suggestedWeight),
    valuationText: item.valuationLabel || '--',
    strategyText: item.strategyId === 'RISK' ? '风控' : (item.strategyId || '--'),
    triggerText: item.exitRule || item.reason || '暂无触发条件',
    linkClass: item.linkHint?.includes('降权') ? 'tag-negative' : 'tag-positive',
  }))
}

function buildEffectItems(effect?: MarketEffect) {
  if (!effect) return []
  return [
    { label: '平均股价', value: effect.avgPctChg }, { label: '中位数', value: effect.medianPctChg },
    { label: '全A等权', value: effect.equalWeightPctChg }, { label: '微盘股', value: effect.microPctChg ?? effect.csi2000PctChg },
    { label: '沪深300', value: effect.hs300PctChg },
  ].map((item) => ({ label: item.label, text: toPercent(item.value), direction: direction(item.value) }))
}

function buildThemes(market?: MarketBrief) {
  if (market?.hotThemeItems?.length) {
    return market.hotThemeItems.map((item) => ({ name: item.name || '--', pct: toPercent(item.pctChg), direction: direction(item.pctChg) }))
  }
  return (market?.hotThemes || []).map((name) => ({ name, pct: '', direction: 'flat' }))
}

Page({
  data: {
    loading: true, error: '', scoreProgress: 0,
    home: {} as DashboardHome,
    indexItems: [] as ReturnType<typeof buildIndexItems>,
    buyItems: [] as ReturnType<typeof buildActionItems>, sellItems: [] as ReturnType<typeof buildActionItems>,
    effectItems: [] as ReturnType<typeof buildEffectItems>, themeItems: [] as ReturnType<typeof buildThemes>,
    observeAlerts: [] as ObserveAlert[], tips: [] as string[],
  },
  onShow() { this.loadPage() },
  onPullDownRefresh() { this.loadPage() },
  async loadPage() {
    this.setData({ loading: true, error: '' })
    try {
      const home = await request<DashboardHome>('/api/dashboard/home', { groupName: '我的自选' })
      this.setData({
        home,
        scoreProgress: home.market?.stanceScore || 0,
        indexItems: buildIndexItems(home.market),
        buyItems: buildActionItems(home.decision?.topBuys),
        sellItems: buildActionItems(home.decision?.topSells),
        effectItems: buildEffectItems(home.market?.effect),
        themeItems: buildThemes(home.market),
        observeAlerts: home.observeAlerts || [],
        tips: home.market?.tips || [],
      })
    } catch (error) {
      this.setData({ error: error instanceof Error ? error.message : '行情加载失败' })
    } finally {
      this.setData({ loading: false })
      wx.stopPullDownRefresh()
    }
  },
  openSearch() { wx.navigateTo({ url: '/pages/search/search' }) },
  openStock(event: WechatMiniprogram.BaseEvent) {
    const code = event.currentTarget.dataset.code as string
    if (code) wx.navigateTo({ url: `/pages/stock/stock?code=${code}` })
  },
  openDecision() { wx.switchTab({ url: '/pages/decision/decision' }) },
  openPortfolio() { wx.switchTab({ url: '/pages/portfolio/portfolio' }) },
  openLadder() { wx.switchTab({ url: '/pages/ladder/ladder' }) },
})
