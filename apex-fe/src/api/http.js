import axios from 'axios'
import { API_BASE } from './baseUrl'
import { beginRequestActivity } from '../utils/appActivity'
import { clearSession, getAccessToken } from './auth'

const http = axios.create({
  baseURL: API_BASE,
  timeout: 60000,
})

function finishRequestActivity(config) {
  config?.finishAppActivity?.()
}

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  if (config.activity !== false) {
    config.finishAppActivity = beginRequestActivity()
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    finishRequestActivity(response.config)
    const body = response.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (error) => {
    finishRequestActivity(error.config)
    if (error.response?.status === 401 || error.response?.status === 403) {
      clearSession()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.assign(`/login?redirect=${encodeURIComponent(window.location.pathname + window.location.search)}`)
      }
    }
    return Promise.reject(error)
  },
)

export default http
