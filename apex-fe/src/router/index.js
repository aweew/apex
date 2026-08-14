import { createRouter, createWebHistory } from 'vue-router'
import { resolveScrollPosition } from './scrollBehavior.js'
import { beginNavigationActivity } from '../utils/appActivity'
import { getAccessToken } from '../api/auth'

const DashboardView = () => import('../views/DashboardView.vue')
const WatchlistView = () => import('../views/WatchlistView.vue')
const SignalView = () => import('../views/SignalView.vue')
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
const ObserveView = () => import('../views/ObserveView.vue')
const HotView = () => import('../views/HotView.vue')
const NewsView = () => import('../views/NewsView.vue')
const IndexBoardView = () => import('../views/IndexBoardView.vue')
const SectorBoardView = () => import('../views/SectorBoardView.vue')
const LimitUpLadderView = () => import('../views/LimitUpLadderView.vue')
const SyncView = () => import('../views/SyncView.vue')
const LoginView = () => import('../views/LoginView.vue')
const RegisterView = () => import('../views/RegisterView.vue')

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
    { path: '/decision', name: 'decision', component: DecisionView },
    { path: '/market', name: 'market', component: IndexBoardView },
    { path: '/heatmap', redirect: { path: '/market', hash: '#heatmap' } },
    { path: '/sector', name: 'sector', component: SectorBoardView },
    { path: '/limit-up', name: 'limitUp', component: LimitUpLadderView },
    { path: '/sync', name: 'sync', component: SyncView },
    { path: '/hot', name: 'hot', component: HotView },
    { path: '/news', name: 'news', component: NewsView },
    { path: '/holding', name: 'holding', component: HoldingView },
    { path: '/portfolio', name: 'portfolio', component: PortfolioView },
    { path: '/observe', name: 'observe', component: ObserveView },
    { path: '/pipeline', name: 'pipeline', component: PipelineView },
    { path: '/screener', name: 'screener', component: ScreenerView },
    { path: '/valuation', name: 'valuation', component: ValuationView },
    { path: '/watchlist', name: 'watchlist', component: WatchlistView },
    { path: '/stock/:code?', name: 'stock', component: StockView },
    { path: '/signals', name: 'signals', component: SignalView },
    { path: '/backtest', name: 'backtest', component: BacktestView },
    { path: '/paper', name: 'paper', component: PaperView },
    { path: '/daily', name: 'daily', component: DailyView },
    { path: '/config', name: 'config', component: ConfigView },
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
  navigationFinishes.set(to, beginNavigationActivity())
})

function finishNavigation(to) {
  navigationFinishes.get(to)?.()
  navigationFinishes.delete(to)
}

router.afterEach((to) => finishNavigation(to))
router.onError((_error, to) => finishNavigation(to))

export default router
