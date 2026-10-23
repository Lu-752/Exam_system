<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand" @click="router.push(auth.isStudent ? '/exam-center' : '/dashboard')">
        <span class="logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M2.5 9.3 12 4.8l9.5 4.5L12 13.8z" />
            <path d="M6.6 11.2v4.4c0 1.7 2.4 2.9 5.4 2.9s5.4-1.2 5.4-2.9v-4.4" />
            <path d="M20.7 10.1v4.6" />
          </svg>
        </span>
        <span>学生考试系统</span>
      </div>

      <div class="topbar-right">
        <el-dropdown trigger="click" @command="onBellCommand">
          <button class="icon-btn" title="消息通知">
            <Icon name="bell" />
            <span v-if="unread" class="dot-red" />
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="m1">您发布的「消防安全知识竞赛」已有 3 人交卷</el-dropdown-item>
              <el-dropdown-item command="m2">「电工安全操作规范考试」今晚 20:00 开始</el-dropdown-item>
              <el-dropdown-item command="m3">学员张伟提交了 1 条试题纠错申请</el-dropdown-item>
              <el-dropdown-item command="readAll" divided>全部标为已读</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown trigger="click" @command="onUserCommand">
          <button class="icon-btn tb-user">
            <span class="avatar">{{ auth.avatarText }}</span>
            {{ auth.displayName }}
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="body">
      <aside class="sidebar" :class="{ collapsed: app.sidebarCollapsed }">
        <nav class="menu">
          <template v-for="m in menus" :key="m.key">
            <a v-if="m.to" class="menu-item" :class="{ active: isActive(m.to) }" @click="router.push(m.to)">
              <Icon :name="m.icon" />
              <span v-show="!app.sidebarCollapsed" class="label">{{ m.label }}</span>
            </a>

            <div v-else class="menu-group">
              <div class="menu-item" :class="{ open: isOpen(m.key) }" @click="toggleGroup(m.key)">
                <Icon :name="m.icon" />
                <span v-show="!app.sidebarCollapsed" class="label">{{ m.label }}</span>
                <Icon v-show="!app.sidebarCollapsed" name="chevron" class="chev" />
              </div>
              <div v-show="!app.sidebarCollapsed && isOpen(m.key)" class="menu-sub">
                <a v-for="it in m.items" :key="it.label" class="sub-item"
                   :class="{ active: isActive(it.to), todo: !it.to }"
                   @click="goItem(it)">
                  <span class="sub-label">{{ it.label }}</span>
                  <span v-if="!it.to" class="soon">待上线</span>
                </a>
              </div>
            </div>
          </template>
        </nav>

        <button class="collapse-btn" :title="app.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
                @click="app.toggleSidebar()">
          <span :style="{ transform: app.sidebarCollapsed ? 'rotate(180deg)' : 'none' }">‹</span>
        </button>
      </aside>

      <main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="route.fullPath.split('?')[0]" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Icon from '@/components/Icon.vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

interface MenuItem { label: string; to?: string }
interface MenuGroup { key: string; label: string; icon: string; to?: string; items?: MenuItem[] }

const MENUS: MenuGroup[] = [
  { key: 'home', label: '首页', icon: 'home', to: '/dashboard' },
  {
    key: 'student', label: '学员中心', icon: 'users',
    items: [
      { label: '考试中心', to: '/exam-center' },
      { label: '报名中心', to: '/signup' },
      { label: '练习中心', to: '/practice' },
      { label: '个人中心', to: '/profile' }
    ]
  },
  {
    key: 'exam', label: '考试中心', icon: 'doc',
    items: [
      { label: '大纲管理', to: '/outlines' },
      { label: '题库与试题', to: '/questions' },
      { label: '组卷策略', to: '/strategies' },
      { label: '试卷管理', to: '/papers' },
      { label: '考试计划', to: '/exam-plans' },
      { label: '考试报名', to: '/signup-review' },
      { label: '考试监控', to: '/proctor' },
      { label: '交卷记录', to: '/records' },
      { label: '考试阅卷', to: '/marking' }
    ]
  },
  {
    key: 'learn', label: '学习中心', icon: 'chart',
    items: [
      { label: '学员管理', to: '/students' },
      { label: '成绩统计', to: '/statistics' },
      { label: '成绩分析', to: '/analysis' }
    ]
  },
  {
    key: 'system', label: '系统管理', icon: 'setting',
    items: [
      { label: '用户管理', to: '/users' },
      { label: '角色权限', to: '/roles' },
      { label: '组织机构', to: '/orgs' },
      { label: '字典管理', to: '/dicts' },
      { label: '系统参数', to: '/params' }
    ]
  }
]

/** 后端菜单路径 → 前端已实现路由；未实现的菜单项保持置灰 */
const ROUTE_MAP: Record<string, string> = {
  '/dashboard': '/dashboard',
  '/admin/students': '/students',
  '/admin/question-banks': '/questions',
  '/admin/questions': '/questions',
  '/admin/papers': '/papers',
  '/admin/strategies': '/strategies',
  '/admin/records': '/records',
  '/admin/exam-plans': '/exam-plans',
  '/admin/monitor': '/proctor',
  '/admin/marking': '/marking',
  '/admin/scores': '/statistics',
  '/admin/analysis': '/analysis',
  '/admin/signups': '/signup-review',
  '/system/users': '/users',
  '/system/roles': '/roles',
  '/system/orgs': '/orgs',
  '/student/exam-center': '/exam-center',
  '/student/signup': '/signup',
  '/student/practice': '/practice',
  '/student/profile': '/profile',
  '/admin/outlines': '/outlines',
  '/system/dicts': '/dicts',
  '/system/params': '/params'
}

/** 后端菜单路径 → 前端路由；未映射时开发期 console 告警（避免悄悄变「待上线」） */
function routeOf(path?: string) {
  if (!path) return undefined
  const to = ROUTE_MAP[path]
  if (!to && import.meta.env.DEV) console.warn('[menu] 未映射的后端菜单路径:', path)
  return to
}

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const auth = useAuthStore()
const unread = ref(true)

/** 优先使用后端按权限下发的菜单树，接口不可用时回退到静态菜单 */
const menus = computed<MenuGroup[]>(() => {
  if (!auth.menus.length) {
    return auth.isStudent ? MENUS.filter((m) => m.key === 'student') : MENUS
  }

  const mapped = auth.menus.map((r) => {
    const children = (r.children ?? []).filter((c) => c.type !== 'BUTTON')
    const icon = r.icon || 'doc'

    if (r.path && children.length === 0) {
      return { key: `m${r.id}`, label: r.name, icon, to: routeOf(r.path) }
    }
    return {
      key: `m${r.id}`,
      label: r.name,
      icon,
      items: children.map((c) => ({
        label: c.name,
        to: routeOf(c.path)
      }))
    }
  })

  // 「题库管理 / 试题管理」指向同一页面，去重避免重复高亮
  return mapped.map((g) => ({
    ...g,
    items: g.items?.filter((it, i, arr) => !it.to || arr.findIndex((x) => x.to === it.to) === i)
  }))
})

/** 当前路由是否落在该菜单项上（含详情页） */
function isActive(path?: string) {
  if (!path) return false
  if (path === '/exam-center') return ['/exam-center', '/exam-take', '/exam-result'].includes(route.path)
  return route.path === path || route.path.startsWith(path + '/')
}

const openKeys = ref<string[]>([])

/** 窄屏自动收起侧边栏，把空间留给内容 */
function syncNarrow() {
  if (window.innerWidth < 1024 && !app.sidebarCollapsed) app.setCollapsed(true)
}

onMounted(async () => {
  syncNarrow()
  window.addEventListener('resize', syncNarrow)
  await auth.loadMenus()
  if (!openKeys.value.length) {
    openKeys.value = menus.value.filter((m) => m.items?.length).map((m) => m.key)
  }
  if (auth.isStudent) openKeys.value = ['student']
})

onBeforeUnmount(() => window.removeEventListener('resize', syncNarrow))

const isOpen = (key: string) => openKeys.value.includes(key)
function toggleGroup(key: string) {
  openKeys.value = isOpen(key) ? openKeys.value.filter((k) => k !== key) : [...openKeys.value, key]
}

function goItem(it: MenuItem) {
  if (it.to) {
    router.push(it.to)
    return
  }
  ElMessage.info(`「${it.label}」正在建设中，请先使用已上线的功能`)
}

function onBellCommand(cmd: string) {
  unread.value = false
  ElMessage.success(cmd === 'readAll' ? '已全部标记为已读' : '消息已读')
}

async function onUserCommand(cmd: string) {
  if (cmd === 'logout') {
    await auth.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
    return
  }
  if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.shell { height: 100%; display: flex; flex-direction: column; }

.topbar {
  height: var(--topbar-h);
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 26px;
  padding: 0 18px;
  background: linear-gradient(90deg, var(--topbar-from), var(--topbar-to));
  color: #fff;
}
.brand { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; cursor: pointer; letter-spacing: .3px; }
.logo { width: 22px; height: 22px; display: inline-flex; opacity: .95; }
.logo svg { width: 100%; height: 100%; }
.topbar-right { margin-left: auto; display: flex; align-items: center; gap: 6px; }

.icon-btn {
  position: relative;
  display: inline-flex; align-items: center; gap: 6px;
  height: 32px; padding: 0 9px;
  background: transparent; border: 0; border-radius: 6px;
  color: rgba(255, 255, 255, .9); font-size: 13px; font-family: inherit;
  cursor: pointer;
}
.icon-btn:hover { background: rgba(255, 255, 255, .14); }
.dot-red {
  position: absolute; top: 6px; right: 6px;
  width: 6px; height: 6px; border-radius: 50%;
  background: #ff4d4f; box-shadow: 0 0 0 2px rgba(38, 53, 92, .8);
}
.avatar {
  width: 22px; height: 22px; border-radius: 50%;
  background: rgba(255, 255, 255, .9); color: var(--topbar-from);
  font-size: 12px; font-weight: 600;
  display: inline-flex; align-items: center; justify-content: center;
}

.body { flex: 1; display: flex; min-height: 0; }

.sidebar {
  width: var(--sidebar-w);
  flex: 0 0 auto;
  background: #fff;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: width .18s ease;
}
.sidebar.collapsed { width: var(--sidebar-w-sm); }
.menu { flex: 1; overflow-y: auto; padding: 10px 8px; }
.menu-item {
  display: flex; align-items: center; gap: 9px;
  height: 38px; padding: 0 10px; margin-bottom: 2px;
  border-radius: 8px; color: var(--text-2); font-size: 13px;
  cursor: pointer; user-select: none;
}
.menu-item:hover { background: #f3f6ff; color: var(--brand); }
.menu-item.active { background: var(--brand-soft); color: var(--brand); font-weight: 600; }
.menu-item .label { flex: 1; white-space: nowrap; }
.menu-item .chev { transition: transform .18s; opacity: .55; }
.menu-item.open .chev { transform: rotate(180deg); }

.menu-sub { padding: 2px 0 6px 34px; }
.sub-item {
  display: flex; align-items: center; gap: 6px;
  height: 32px; line-height: 32px;
  padding: 0 8px; border-radius: 6px;
  color: var(--text-2); font-size: 12.5px; cursor: pointer;
  white-space: nowrap; overflow: hidden;
}
.sub-item .sub-label { overflow: hidden; text-overflow: ellipsis; }
.sub-item .soon {
  margin-left: auto; flex: 0 0 auto;
  font-size: 10.5px; color: #b98a2f; background: #fdf6e7;
  border: 1px solid #f3e2bd; border-radius: 4px; padding: 0 4px;
  line-height: 15px;
}
.sub-item:hover { background: #f5f7fc; color: var(--brand); }
.sub-item.active { color: var(--brand); background: var(--brand-soft); font-weight: 600; }
.sub-item.todo { color: var(--text-3); }
.sub-item.todo:hover { color: var(--text-2); }

.collapse-btn {
  height: 34px; margin: 6px 8px 10px;
  border: 1px solid var(--border); border-radius: 8px;
  background: #fff; color: var(--text-3); cursor: pointer;
  font-size: 14px; line-height: 1;
}
.collapse-btn:hover { color: var(--brand); border-color: #c9d6ff; }

.main { flex: 1; min-width: 0; overflow-y: auto; padding: 0 18px 18px; }

/* 路由切换的轻量过渡，避免内容“硬切” */
.fade-slide-enter-active { transition: opacity .18s ease, transform .18s ease; }
.fade-slide-leave-active { transition: opacity .12s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(6px); }
.fade-slide-leave-to { opacity: 0; }
</style>
