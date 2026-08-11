import axios from 'axios'
import { API_BASE } from './baseUrl'
import { beginRequestActivity } from '../utils/appActivity'

const http = axios.create({
  baseURL: API_BASE,
  timeout: 60000,
})

function finishRequestActivity(config) {
  config?.finishAppActivity?.()
}

http.interceptors.request.use((config) => {
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
    return Promise.reject(error)
  },
)

export default http
