const app = getApp<IAppOption>()

interface ApiResult<T> {
  code: number
  msg?: string
  message?: string
  data: T
}

export function request<T>(path: string, data?: Record<string, string | number | boolean>): Promise<T> {
  const url = `${app.globalData.apiBaseUrl}${path}`
  return new Promise((resolve, reject) => {
    wx.request<ApiResult<T>>({
      url,
      data,
      timeout: 15000,
      success: response => {
        if (response.statusCode >= 200 && response.statusCode < 300 && response.data.code === 0) {
          resolve(response.data.data)
          return
        }
        reject(new Error(response.data.msg || response.data.message || '服务暂不可用'))
      },
      fail: error => reject(new Error(`${error.errMsg || '无法连接服务'}：${url}`)),
    })
  })
}
