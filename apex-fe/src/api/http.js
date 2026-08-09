import axios from 'axios'
import { API_BASE } from './baseUrl'

const http = axios.create({
  baseURL: API_BASE,
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
