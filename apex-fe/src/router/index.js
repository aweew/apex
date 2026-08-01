import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import WatchlistView from '../views/WatchlistView.vue'
import SignalView from '../views/SignalView.vue'
import BacktestView from '../views/BacktestView.vue'
import PaperView from '../views/PaperView.vue'
import DailyView from '../views/DailyView.vue'
import ConfigView from '../views/ConfigView.vue'
import StockView from '../views/StockView.vue'
import PipelineView from '../views/PipelineView.vue'
import ScreenerView from '../views/ScreenerView.vue'
import DecisionView from '../views/DecisionView.vue'
import HoldingView from '../views/HoldingView.vue'
import HotView from '../views/HotView.vue'
import NewsView from '../views/NewsView.vue'
import IndexBoardView from '../views/IndexBoardView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', name: 'dashboard', component: DashboardView },
    { path: '/decision', name: 'decision', component: DecisionView },
    { path: '/market', name: 'market', component: IndexBoardView },
    { path: '/hot', name: 'hot', component: HotView },
    { path: '/news', name: 'news', component: NewsView },
    { path: '/holding', name: 'holding', component: HoldingView },
    { path: '/pipeline', name: 'pipeline', component: PipelineView },
    { path: '/screener', name: 'screener', component: ScreenerView },
    { path: '/watchlist', name: 'watchlist', component: WatchlistView },
    { path: '/stock/:code?', name: 'stock', component: StockView },
    { path: '/signals', name: 'signals', component: SignalView },
    { path: '/backtest', name: 'backtest', component: BacktestView },
    { path: '/paper', name: 'paper', component: PaperView },
    { path: '/daily', name: 'daily', component: DailyView },
    { path: '/config', name: 'config', component: ConfigView },
  ],
})

export default router
