import { createRouter, createWebHistory } from 'vue-router'
import { resolveScrollPosition } from './scrollBehavior.js'
import { beginNavigationActivity } from '../utils/appActivity'
import { getAccessToken, getCurrentUser } from '../api/auth'

const DashboardView = () => import('../views/DashboardView.vue')
const WatchlistView = () => import('../views/WatchlistView.vue')
const SignalView = () => import('../views/SignalView.vue')
const SignalDetailView = () => import('../views/SignalDetailView.vue')
const BacktestView = () => import('../views/BacktestView.vue')
const PaperView = () => import('../views/PaperView.vue')
const DailyView = () => import('../views/DailyView.vue')
const ConfigView = () => import('../views/ConfigView.vue')
const StockView = () => import('../views/StockView.vue')
const PipelineView = () => import('../views/PipelineView.vue')
const ScreenerView = () => import('../views/ScreenerView.vue')
const ValuationView = () => import('../views/ValuationView.vue')
const DecisionView = () => import('../views/DecisionView.vue')
const HoldingView = () => import('../views/HoldingView.vue')
const PortfolioView = () => import('../views/PortfolioView.vue')
const TradeRecordView = () => import('../views/TradeRecordView.vue')
const ObserveView = () => import('../views/ObserveView.vue')
const HotView = () => import('../views/HotView.vue')
const NewsView = () => import('../views/NewsView.vue')
const IndexBoardView = () => import('../views/IndexBoardView.vue')
const LimitUpLadderView = () => import('../views/LimitUpLadderView.vue')
const SyncView = () => import('../views/SyncView.vue')
const LoginView = () => import('../views/LoginView.vue')
const RegisterView = () => import('../views/RegisterView.vue')
const ApexAiView = () => import('../views/ApexAiView.vue')
const PreMarketReportView = () => import('../views/PreMarketReportView.vue')
const WeekendReportView = () => import('../views/WeekendReportView.vue')
const PostMarketReportView = () => import('../views/PostMarketReportView.vue')
const UserUsageView = () => import('../views/UserUsageView.vue')

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    const position = resolveScrollPosition(savedPosition)
    if (!savedPosition) return position
    return new Promise((resolve) => {
      window.setTimeout(() => resolve(position), 120)
    })
  },
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', name: 'dashboard', component: DashboardView },
    { path: '/pre-market-report', name: 'preMarketReport', component: PreMarketReportView },
    { path: '/weekend-report', name: 'weekendReport', component: WeekendReportView },
    { path: '/post-market-report', name: 'postMarketReport', component: PostMarketReportView },
    { path: '/ai-center', name: 'apexAi', component: ApexAiView },
    { path: '/decision', name: 'decision', component: DecisionView },
    { path: '/market', name: 'market', component: IndexBoardView },
    { path: '/heatmap', redirect: { path: '/market', hash: '#heatmap' } },
    {
      path: '/sector',
      redirect: (to) => ({
        path: '/market',
        query: { ...to.query, tab: 'sector' },
        hash: to.hash,
      }),
    },
    {
      path: '/capital-flow',
      redirect: (to) => ({
        path: '/market',
        query: { ...to.query, tab: 'capital-flow' },
        hash: to.hash,
      }),
    },
    { path: '/limit-up', name: 'limitUp', component: LimitUpLadderView },
    { path: '/sync', name: 'sync', component: SyncView },
    { path: '/hot', name: 'hot', component: HotView },
    { path: '/news', name: 'news', component: NewsView },
    { path: '/holding', name: 'holding', component: HoldingView },
    { path: '/portfolio', name: 'portfolio', component: PortfolioView },
    { path: '/trades', name: 'trades', component: TradeRecordView },
    { path: '/observe', name: 'observe', component: ObserveView },
    { path: '/pipeline', name: 'pipeline', component: PipelineView },
    { path: '/screener', name: 'screener', component: ScreenerView },
    {
      path: '/factors',
      redirect: (to) => ({
        path: `/stock/${String(to.query.code || '600519').replace(/\D/g, '').slice(0, 6) || '600519'}`,
        query: { tab: 'factors' },
      }),
    },
    { path: '/valuation', name: 'valuation', component: ValuationView },
    { path: '/watchlist', name: 'watchlist', component: WatchlistView },
    { path: '/stock/:code?', name: 'stock', component: StockView },
    { path: '/signals/:code', name: 'signalDetail', component: SignalDetailView },
    { path: '/signals', name: 'signals', component: SignalView },
    { path: '/backtest', name: 'backtest', component: BacktestView },
    { path: '/paper', name: 'paper', component: PaperView },
    { path: '/daily', name: 'daily', component: DailyView },
    { path: '/config', name: 'config', component: ConfigView },
    { path: '/usage', name: 'usage', component: UserUsageView, meta: { requiresAdmin: true } },
  ],
})

const navigationFinishes = new WeakMap()

router.beforeEach((to) => {
  if (!to.meta.public && !getAccessToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public && getAccessToken() && to.name === 'login') {
    return { path: typeof to.query.redirect === 'string' ? to.query.redirect : '/dashboard' }
  }
  if (to.meta.requiresAdmin && getCurrentUser()?.role !== 'ADMIN') {
    return { path: '/dashboard' }
  }
  navigationFinishes.set(to, beginNavigationActivity())
})

function finishNavigation(to) {
  navigationFinishes.get(to)?.()
  navigationFinishes.delete(to)
}

router.afterEach((to) => finishNavigation(to))
router.onError((_error, to) => finishNavigation(to))

export default router
