"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const api_1 = require("../../utils/api");
const format_1 = require("../../utils/format");
Page({
    data: { loading: true, error: '', code: '', detail: {}, chartBars: [] },
    onLoad(options) { if (options.code) {
        this.setData({ code: options.code });
        this.loadStock(options.code);
    } },
    async loadStock(code) {
        this.setData({ loading: true, error: '' });
        try {
            const detail = await (0, api_1.request)(`/api/stock/${code}`, { barLimit: 30, refresh: false });
            const chartBars = (detail.bars || []).slice(-8);
            const maxClose = Math.max(...chartBars.map(item => item.close || 0), 1);
            chartBars.forEach(item => { item.height = Math.max(18, Math.round(((item.close || 0) / maxClose) * 150)); });
            this.setData({ detail, chartBars });
        }
        catch (error) {
            this.setData({ error: error instanceof Error ? error.message : '详情加载失败' });
        }
        finally {
            this.setData({ loading: false });
        }
    },
    reloadStock() { if (this.data.code)
        this.loadStock(this.data.code); },
    openSearch() { wx.navigateTo({ url: '/pages/search/search' }); },
    numberText: format_1.numberText, percentText: format_1.percentText, changeClass: format_1.changeClass,
});
