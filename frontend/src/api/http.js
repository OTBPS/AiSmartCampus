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
  (error) => {
    const status = error.response?.status
    const url = error.config?.url || ''
    const isAuthEndpoint = url.startsWith('/auth')

    if (status === 401 && !isAuthEndpoint) {
      const auth = useAuthStore()
      auth.logout()
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
        window.location.assign(`/login?redirect=${encodeURIComponent(currentPath)}`)
      }
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    if (status === 403) {
      return Promise.reject(new Error('没有权限执行该操作'))
    }

    const message = error.response?.data?.message || error.message || 'Request failed'
    return Promise.reject(new Error(message))
  }
)
