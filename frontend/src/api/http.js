import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const LOCALE_KEY = 'scn-locale'

export const http = axios.create({
  baseURL: '/api',
  timeout: 12000
})

http.interceptors.request.use((config) => {
  const locale = typeof localStorage === 'undefined' ? 'zh-CN' : localStorage.getItem(LOCALE_KEY) || 'zh-CN'
  config.headers = config.headers || {}
  config.headers['Accept-Language'] = locale
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.success === false) {
      return Promise.reject(new Error(body.message || 'Request failed'))
    }
    return body?.data ?? body
  },
  (error) => Promise.reject(error)
)
