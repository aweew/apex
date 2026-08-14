"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const api_1 = require("../../utils/api");
const format_1 = require("../../utils/format");
Page({
    data: { keyword: '', loading: false, searched: false, stocks: [] },
    onInput(event) { this.setData({ keyword: event.detail.value }); },
    async search() {
        const keyword = this.data.keyword.trim();
        if (!keyword) {
            wx.showToast({ title: '请输入名称或代码', icon: 'none' });
            return;
        }
        this.setData({ loading: true, searched: true });
        try {
            this.setData({ stocks: await (0, api_1.request)('/api/stock/search', { q: keyword, limit: 20 }) });
        }
        catch (error) {
            wx.showToast({ title: error instanceof Error ? error.message : '搜索失败', icon: 'none' });
        }
        finally {
            this.setData({ loading: false });
        }
    },
    openStock(event) { wx.navigateTo({ url: `/pages/stock/stock?code=${event.currentTarget.dataset.code}` }); },
    numberText: format_1.numberText,
});
