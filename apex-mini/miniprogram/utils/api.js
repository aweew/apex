"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.request = request;
const app = getApp();
function request(path, data) {
    const url = `${app.globalData.apiBaseUrl}${path}`;
    return new Promise((resolve, reject) => {
        wx.request({
            url,
            data,
            timeout: 15000,
            success: response => {
                if (response.statusCode >= 200 && response.statusCode < 300 && response.data.code === 0) {
                    resolve(response.data.data);
                    return;
                }
                reject(new Error(response.data.msg || response.data.message || '服务暂不可用'));
            },
            fail: error => reject(new Error(`${error.errMsg || '无法连接服务'}：${url}`)),
        });
    });
}
