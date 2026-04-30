import { defineStore } from 'pinia'

const saved = JSON.parse(localStorage.getItem('scn-auth') || 'null')

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: saved?.token || '',
    user: saved?.user || null
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    setSession(data) {
      this.token = data.token
      this.user = {
        id: data.userId,
        username: data.username,
        displayName: data.displayName,
        role: data.role
      }
      localStorage.setItem('scn-auth', JSON.stringify({ token: this.token, user: this.user }))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('scn-auth')
    }
  }
})

