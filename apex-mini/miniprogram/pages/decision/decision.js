"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const api_1 = require("../../utils/api");
function actionLabel(action) {
    return { BUY: '建议买入', ADD: '建议加仓', SELL: '建议卖出', REDUCE: '建议减仓', HOLD: '继续持有', WATCH: '继续观察' }[action || ''] || action || '待定';
}
function actionClass(action) {
    if (action === 'BUY' || action === 'ADD')
        return 'action-buy';
    if (action === 'SELL' || action === 'REDUCE')
        return 'action-sell';
    if (action === 'WATCH')
        return 'action-watch';
    return 'action-hold';
}
function buildItems(items) {
    return (items || []).map((item) => ({
        ...item,
        actionText: actionLabel(item.action), actionClass: actionClass(item.action),
        scoreText: item.score === undefined || item.score === null ? '--' : Number(item.score).toFixed(0),
        weightText: item.suggestedWeight === undefined || item.suggestedWeight === null ? '--' : `${(Number(item.suggestedWeight) * 100).toFixed(1)}%`,
        strategyText: item.strategyId === 'RISK' ? '风控' : (item.strategyId || '--'),
        valuationText: item.valuationLabel || '--',
        entryText: item.entryGatePassed === true ? '通过' : item.entryGatePassed === false ? '观察' : '',
        linkClass: item.linkHint?.includes('降权') ? 'tag-negative' : 'tag-positive',
        detailText: item.exitRule || item.reason || '规则匹配',
    }));
}
Page({
    data: {
        loading: true, error: '', activeTab: 'buy', decision: {},
        buyItems: [], sellItems: [], holdItems: [],
    },
    onShow() { this.loadPage(); },
    async loadPage() {
        this.setData({ loading: true, error: '' });
        try {
            const decision = await (0, api_1.request)('/api/decision/today', { groupName: '我的自选' });
            const allItems = decision.items || [];
            this.setData({
                decision,
                buyItems: buildItems(decision.buys || allItems.filter((item) => item.action === 'BUY' || item.action === 'ADD')),
                sellItems: buildItems(decision.sells || allItems.filter((item) => item.action === 'SELL' || item.action === 'REDUCE')),
                holdItems: buildItems(decision.holds || allItems.filter((item) => item.action === 'HOLD' || item.action === 'WATCH')),
            });
        }
        catch (error) {
            this.setData({ error: error instanceof Error ? error.message : '决策加载失败' });
        }
        finally {
            this.setData({ loading: false });
        }
    },
    switchTab(event) { this.setData({ activeTab: event.currentTarget.dataset.tab }); },
    openStock(event) {
        const code = event.currentTarget.dataset.code;
        if (code)
            wx.navigateTo({ url: `/pages/stock/stock?code=${code}` });
    },
});
