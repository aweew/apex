import { request } from '../../utils/api'
import { numberText } from '../../utils/format'

interface SearchStock { code: string; name: string; market?: string; latestPrice?: number }

Page({
  data: { keyword: '', loading: false, searched: false, stocks: [] as SearchStock[] },
  onInput(event: WechatMiniprogram.Input) { this.setData({ keyword: event.detail.value }) },
  async search() {
    const keyword = this.data.keyword.trim()
    if (!keyword) { wx.showToast({ title: '请输入名称或代码', icon: 'none' }); return }
    this.setData({ loading: true, searched: true })
    try { this.setData({ stocks: await request<SearchStock[]>('/api/stock/search', { q: keyword, limit: 20 }) }) }
    catch (error) { wx.showToast({ title: error instanceof Error ? error.message : '搜索失败', icon: 'none' }) }
    finally { this.setData({ loading: false }) }
  },
  openStock(event: WechatMiniprogram.BaseEvent) { wx.navigateTo({ url: `/pages/stock/stock?code=${event.currentTarget.dataset.code}` }) },
  numberText,
})
