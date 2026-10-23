import { defineStore } from 'pinia'

const KEY = 'exam_sidebar_collapsed'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: localStorage.getItem(KEY) === '1'
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem(KEY, this.sidebarCollapsed ? '1' : '0')
    },
    setCollapsed(v: boolean) {
      this.sidebarCollapsed = v
      localStorage.setItem(KEY, v ? '1' : '0')
    }
  }
})
