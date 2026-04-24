import { defineStore } from 'pinia'

interface AuthState {
  token: string | null
  username: string | null
  role: string | null
  uid: number | null
  serverStartTime: number | null  // 服务器启动时间，用于检测后端是否重启
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('token'),
    username: localStorage.getItem('username'),
    role: localStorage.getItem('role'),
    uid: localStorage.getItem('uid') ? Number(localStorage.getItem('uid')) : null,
    serverStartTime: localStorage.getItem('serverStartTime') ? Number(localStorage.getItem('serverStartTime')) : null,
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN',
    // 兼容旧会话：即使 localStorage 里还没有 uid，也能从 token 里即时解析
    currentUid: (state): number | null => {
      if (typeof state.uid === 'number' && !Number.isNaN(state.uid)) return state.uid
      if (!state.token) return null
      try {
        const payload = JSON.parse(atob(state.token.split('.')[1]))
        const uid = payload?.uid
        return typeof uid === 'number' ? uid : null
      } catch {
        return null
      }
    },
  },
  actions: {
    setAuth(token: string, username?: string) {
      this.token = token
      if (username) this.username = username
      localStorage.setItem('token', token)
      if (username) localStorage.setItem('username', username)
      // try decode role/uid from JWT (base64 without validation)
      try {
        const payload = JSON.parse(atob(token.split('.')[1]))
        const role = payload?.role
        if (role) {
          this.role = role
          localStorage.setItem('role', role)
        }
        const uid = payload?.uid
        if (typeof uid === 'number') {
          this.uid = uid
          localStorage.setItem('uid', String(uid))
        }
      } catch {}
    },
    setServerStartTime(startTime: number) {
      this.serverStartTime = startTime
      localStorage.setItem('serverStartTime', String(startTime))
    },
    clear() {
      this.token = null
      this.username = null
      this.role = null
      this.uid = null
      this.serverStartTime = null
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
      localStorage.removeItem('uid')
      localStorage.removeItem('serverStartTime')
    },
  },
})


