import axios from 'axios'

const http = axios.create({
  // 开发环境直连后端，避免本机代理干扰 Vite proxy
  baseURL: import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8080/apex',
  timeout: 60000,
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (error) => Promise.reject(error),
)

export default http
