import { request } from '../../utils/api'
import { changeClass, numberText, percentText } from '../../utils/format'

interface Basic { code?: string; name?: string; market?: string; industry?: string; latestPrice?: number; pctChg?: number; peTtm?: number; pb?: number; totalMv?: number }
interface Bar { tradeDate?: string; close?: number; height?: number }
interface StockDetail { basic?: Basic; bars?: Bar[]; volumeRatio?: number; rs20VsHs300?: number; barStatus?: string; note?: string }

Page({
  data: { loading: true, error: '', code: '', detail: {} as StockDetail, chartBars: [] as Bar[] },
  onLoad(options) { if (options.code) { this.setData({ code: options.code }); this.loadStock(options.code) } },
  async loadStock(code: string) {
    this.setData({ loading: true, error: '' })
    try {
      const detail = await request<StockDetail>(`/api/stock/${code}`, { barLimit: 30, refresh: false })
      const chartBars = (detail.bars || []).slice(-8)
      const maxClose = Math.max(...chartBars.map(item => item.close || 0), 1)
      chartBars.forEach(item => { item.height = Math.max(18, Math.round(((item.close || 0) / maxClose) * 150)) })
      this.setData({ detail, chartBars })
    }
    catch (error) { this.setData({ error: error instanceof Error ? error.message : '详情加载失败' }) }
    finally { this.setData({ loading: false }) }
  },
  reloadStock() { if (this.data.code) this.loadStock(this.data.code) },
  openSearch() { wx.navigateTo({ url: '/pages/search/search' }) },
  numberText, percentText, changeClass,
})
