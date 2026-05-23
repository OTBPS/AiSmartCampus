import { http } from './http'

export const authApi = {
  login: (payload) => http.post('/auth/login', payload),
  register: (payload) => http.post('/auth/register', payload),
  me: () => http.get('/auth/me')
}

export const poiApi = {
  list: (params) => http.get('/pois', { params }),
  get: (id) => http.get(`/pois/${id}`),
  create: (payload) => http.post('/pois/admin', payload),
  update: (id, payload) => http.put(`/pois/admin/${id}`, payload),
  remove: (id) => http.delete(`/pois/admin/${id}`),
  updateStatus: (id, payload) => http.put(`/pois/admin/${id}/status`, payload),
  uploadImage: (id, file) => {
    const formData = new FormData()
    formData.append('image', file)
    return http.post(`/pois/admin/${id}/image`, formData)
  },
  deleteImage: (id) => http.delete(`/pois/admin/${id}/image`)
}

export const aiApi = {
  chat: (message, locale, routeContext) => http.post('/ai/chat', routeContext ? { message, locale, routeContext } : { message, locale }),
  logs: () => http.get('/ai/admin/logs'),
  mineLogs: () => http.get('/ai/logs/mine'),
  clearMineLogs: () => http.delete('/ai/logs/mine')
}

export const feedbackApi = {
  submit: (payload) => http.post('/feedback', payload),
  mine: () => http.get('/feedback/mine'),
  adminList: (params) => http.get('/feedback/admin', { params }),
  review: (id, payload) => http.put(`/feedback/admin/${id}/review`, payload)
}

export const discoverApi = {
  posts: (params) => http.get('/discover/posts', { params }),
  post: (id) => http.get(`/discover/posts/${id}`),
  create: (payload) => http.post('/discover/posts', payload),
  update: (id, payload) => http.put(`/discover/posts/${id}`, payload),
  uploadImages: (id, files) => {
    const formData = new FormData()
    files.forEach((file) => formData.append('images', file))
    return http.post(`/discover/posts/${id}/images`, formData)
  },
  deleteImage: (postId, imageId) => http.delete(`/discover/posts/${postId}/images/${imageId}`),
  remove: (id) => http.delete(`/discover/posts/${id}`),
  like: (id) => http.post(`/discover/posts/${id}/like`),
  unlike: (id) => http.delete(`/discover/posts/${id}/like`),
  favorite: (id) => http.post(`/discover/posts/${id}/favorite`),
  unfavorite: (id) => http.delete(`/discover/posts/${id}/favorite`),
  addComment: (id, payload) => http.post(`/discover/posts/${id}/comments`, payload),
  deleteComment: (id) => http.delete(`/discover/comments/${id}`),
  mine: () => http.get('/discover/mine'),
  favorites: () => http.get('/discover/favorites'),
  adminPosts: () => http.get('/discover/admin/posts'),
  adminDelete: (id) => http.delete(`/discover/admin/posts/${id}`)
}

export const weatherApi = {
  campus: (params) => http.get('/weather/campus', { params })
}

export const accountApi = {
  list: (params) => http.get('/admin/accounts', { params }),
  create: (payload) => http.post('/admin/accounts', payload),
  update: (id, payload) => http.put(`/admin/accounts/${id}`, payload),
  deactivate: (id) => http.delete(`/admin/accounts/${id}`)
}

export const adminApi = {
  dashboard: () => http.get('/admin/dashboard')
}
