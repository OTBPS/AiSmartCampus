import { http } from './http'

export const authApi = {
  login: (payload) => http.post('/auth/login', payload),
  register: (payload) => http.post('/auth/register', payload),
  me: () => http.get('/auth/me')
}

export const poiApi = {
  list: (params) => http.get('/pois', { params }),
  create: (payload) => http.post('/pois/admin', payload),
  update: (id, payload) => http.put(`/pois/admin/${id}`, payload),
  updateStatus: (id, payload) => http.put(`/pois/admin/${id}/status`, payload)
}

export const aiApi = {
  chat: (message) => http.post('/ai/chat', { message }),
  logs: () => http.get('/ai/admin/logs')
}

export const feedbackApi = {
  submit: (payload) => http.post('/feedback', payload),
  mine: () => http.get('/feedback/mine'),
  adminList: (params) => http.get('/feedback/admin', { params }),
  review: (id, payload) => http.put(`/feedback/admin/${id}/review`, payload)
}

export const discoverApi = {
  posts: () => http.get('/discover/posts'),
  adminPosts: () => http.get('/discover/admin/posts'),
  create: (payload) => http.post('/discover/admin/posts', payload),
  update: (id, payload) => http.put(`/discover/admin/posts/${id}`, payload)
}

export const adminApi = {
  dashboard: () => http.get('/admin/dashboard')
}

