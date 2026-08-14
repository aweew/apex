import { request } from '../../utils/api'
import { numberText, percentText, changeClass } from '../../utils/format'
interface Portfolio { name: string; positionCount?: number; totalEquity?: number; todayPnl?: number; todayPct?: number; note?: string }
Page({ data: { loading: true, error: '', portfolios: [] as Portfolio[] }, onShow() { this.loadPage() }, async loadPage() { this.setData({ loading: true, error: '' }); try { this.setData({ portfolios: await request<Portfolio[]>('/api/portfolio/list') }) } catch (error) { this.setData({ error: error instanceof Error ? error.message : '组合加载失败' }) } finally { this.setData({ loading: false }) } }, numberText, percentText, changeClass })
