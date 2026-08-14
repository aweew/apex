"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const api_1 = require("../../utils/api");
const format_1 = require("../../utils/format");
Page({ data: { loading: true, error: '', portfolios: [] }, onShow() { this.loadPage(); }, async loadPage() { this.setData({ loading: true, error: '' }); try {
        this.setData({ portfolios: await (0, api_1.request)('/api/portfolio/list') });
    }
    catch (error) {
        this.setData({ error: error instanceof Error ? error.message : '组合加载失败' });
    }
    finally {
        this.setData({ loading: false });
    } }, numberText: format_1.numberText, percentText: format_1.percentText, changeClass: format_1.changeClass });
