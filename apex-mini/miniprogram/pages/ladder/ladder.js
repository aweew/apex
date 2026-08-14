"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const api_1 = require("../../utils/api");
Page({ data: { loading: true, error: '', ladder: {} }, onShow() { this.loadPage(); }, async loadPage() { this.setData({ loading: true, error: '' }); try {
        this.setData({ ladder: await (0, api_1.request)('/api/limit-up/ladder') });
    }
    catch (error) {
        this.setData({ error: error instanceof Error ? error.message : '连板天梯加载失败' });
    }
    finally {
        this.setData({ loading: false });
    } }, openStock(event) { const code = event.currentTarget.dataset.code; if (code)
        wx.navigateTo({ url: `/pages/stock/stock?code=${code}` }); } });
