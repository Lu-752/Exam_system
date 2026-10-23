import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { MenuNode, UserInfo } from '@/api/types'

const TOKEN_KEY = 'exam_token'
const USER_KEY = 'exam_user'

function readUser(): UserInfo | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as UserInfo) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
    menus: [] as MenuNode[],
    menusLoaded: false
  }),
  getters: {
    isLogin: (s) => !!s.token,
    displayName: (s) => s.user?.name || '未登录',
    avatarText: (s) => s.user?.avatar || 'U',
    isStudent: (s) => (s.user?.roles ?? []).includes('STUDENT'),
    isAdmin: (s) => (s.user?.roles ?? []).includes('SUPER_ADMIN')
  },
  actions: {
    async login(username: string, password: string) {
      const res = await authApi.login(username, password)
      this.token = res.token
      this.user = res.user
      localStorage.setItem(TOKEN_KEY, res.token)
      localStorage.setItem(USER_KEY, JSON.stringify(res.user))
      this.menusLoaded = false
      await this.loadMenus()
      return res
    },

    async loadMenus() {
      if (this.menusLoaded) return
      try {
        this.menus = await authApi.menus()
        this.menusLoaded = true
      } catch {
        // 后端不可用时清空，由布局层回退到静态菜单
        this.menus = []
      }
    },

    async logout() {
      try {
        await authApi.logout()
      } catch {
        // 令牌已失效时忽略
      }
      this.token = ''
      this.user = null
      this.menus = []
      this.menusLoaded = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },

    hasPerm(code: string) {
      const perms = this.user?.perms ?? []
      return perms.includes('*') || perms.includes(code)
    }
  }
})
