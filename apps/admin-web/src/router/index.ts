import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '首页' } },
      { path: 'questions', name: 'questions', component: () => import('@/views/Questions.vue'), meta: { title: '题库与试题' } },
      { path: 'papers', name: 'papers', component: () => import('@/views/Papers.vue'), meta: { title: '试卷管理' } },
      { path: 'strategies', name: 'strategies', component: () => import('@/views/Strategies.vue'), meta: { title: '组卷策略' } },
      { path: 'records', name: 'records', component: () => import('@/views/Records.vue'), meta: { title: '交卷记录' } },
      { path: 'exam-plans', name: 'exam-plans', component: () => import('@/views/ExamPlans.vue'), meta: { title: '考试计划' } },
      { path: 'proctor', name: 'proctor', component: () => import('@/views/Proctor.vue'), meta: { title: '考试监控' } },
      { path: 'marking', name: 'marking', component: () => import('@/views/Marking.vue'), meta: { title: '考试阅卷' } },
      { path: 'users', name: 'users', component: () => import('@/views/Users.vue'), meta: { title: '用户管理' } },
      { path: 'roles', name: 'roles', component: () => import('@/views/Roles.vue'), meta: { title: '角色管理' } },
      { path: 'orgs', name: 'orgs', component: () => import('@/views/Organization.vue'), meta: { title: '组织机构' } },
      { path: 'signup-review', name: 'signup-review', component: () => import('@/views/SignupReview.vue'), meta: { title: '报名审核' } },
      { path: 'signup', name: 'signup', component: () => import('@/views/SignupCenter.vue'), meta: { title: '报名中心' } },
      { path: 'practice', name: 'practice', component: () => import('@/views/Practice.vue'), meta: { title: '练习中心' } },
      { path: 'profile', name: 'profile', component: () => import('@/views/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'exam-center', name: 'exam-center', component: () => import('@/views/ExamCenter.vue'), meta: { title: '考试中心' } },
      { path: 'outlines', name: 'outlines', component: () => import('@/views/Outlines.vue'), meta: { title: '大纲管理' } },
      { path: 'dicts', name: 'dicts', component: () => import('@/views/Dicts.vue'), meta: { title: '字典管理' } },
      { path: 'params', name: 'params', component: () => import('@/views/Params.vue'), meta: { title: '系统参数' } }
    ]
  },
  { path: '/exam-take/:planId', name: 'exam-take', component: () => import('@/views/ExamTake.vue'), meta: { title: '在线考试' } },
  { path: '/exam-result/:attemptId', name: 'exam-result', component: () => import('@/views/ExamResult.vue'), meta: { title: '考试成绩' } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue'), meta: { title: '页面不存在' } }
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/** 学员端可访问的页面（其余一律回考试中心） */
const STUDENT_ALLOWED = ['/exam-center', '/exam-take', '/exam-result', '/practice', '/signup', '/profile']

/** 全局导航状态：顶部进度条 + 标题 */
export const navState = reactive({ pending: false })

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 仅开发环境：带 ?demo=1 以管理员、?demo=student 以学员自动登录，便于截图与演示
  if (import.meta.env.DEV && !auth.isLogin && to.query.demo) {
    const asStudent = to.query.demo === 'student'
    await auth.login(asStudent ? 'student' : 'admin', 'Mima3700%')
  }

  if (to.meta.public) {
    return auth.isLogin && to.name === 'login' ? { name: 'dashboard' } : true
  }
  if (!auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 学员账号只保留考试相关页面
  if (auth.isStudent && !STUDENT_ALLOWED.some((p) => to.path.startsWith(p))) {
    return { path: '/exam-center' }
  }
  return true
})

router.beforeResolve(() => {
  navState.pending = true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 考试系统` : '考试系统'
  // 稍微延迟收起，避免极快跳转时进度条闪烁
  window.setTimeout(() => (navState.pending = false), 220)
})

router.onError(() => {
  navState.pending = false
})
