<template>
  <div class="page-head">
    <div class="page-title">首页</div>
    <div class="head-actions">
      <el-button type="primary" @click="router.push('/exam-plans')">发布考试</el-button>
    </div>
  </div>

  <div class="banner">
    <div>
      <h2>欢迎使用考试系统</h2>
      <p>
        一站式完成考试大纲、题库命题、组卷策略、考试计划、在线考试、实时监考与自动阅卷的全流程管理。
        <template v-if="ongoing > 0">当前有 {{ ongoing }} 场考试正在进行，请及时关注考试动态。</template>
      </p>
    </div>
    <el-button class="banner-btn" @click="router.push('/exam-plans')">进入考试计划</el-button>
  </div>

  <div class="card">
    <div class="card-head">
      <span class="card-title">数据概览</span>
      <span class="card-date">{{ today }}</span>
    </div>
    <div v-loading="loading" class="grid">
      <div v-for="it in items" :key="it.label" class="cell">
        <div class="cell-label">{{ it.label }}</div>
        <div class="cell-num">{{ it.value }}</div>
      </div>
    </div>
    <div class="note">* 数据来源：后端实时聚合（/api/v1/dashboard/overview）</div>
  </div>

  <div class="card">
    <div class="card-head">
      <span class="card-title">待办事项</span>
    </div>
    <div class="todo">
      <div v-for="t in todos" :key="t.text" class="todo-item">
        <span class="todo-dot" :class="t.level" />
        <span class="todo-text">{{ t.text }}</span>
        <span class="todo-time">{{ t.time }}</span>
        <el-button link type="primary" @click="router.push(t.to)">去处理</el-button>
      </div>
      <el-empty v-if="!todos.length" description="暂无待办" :image-size="60" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getOverview, listPendingMarking, listPlans } from '@/api/exam'

const router = useRouter()
const loading = ref(true)
const items = ref<{ label: string; value: string }[]>([])
const ongoing = ref(0)
const todos = ref<{ text: string; time: string; level: string; to: string }[]>([])

const today = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${week}`
})

onMounted(async () => {
  try {
    const [ov, pending, plans] = await Promise.all([
      getOverview(),
      listPendingMarking().catch(() => []),
      listPlans().catch(() => [])
    ])
    items.value = ov.items

    ongoing.value = plans.filter((p) => p.status === 'ONGOING').length

    const list: { text: string; time: string; level: string; to: string }[] = []
    if (pending.length) {
      list.push({ text: `${pending.length} 份主观题答卷待批阅`, time: '待处理', level: 'warn', to: '/marking' })
    }
    plans
      .filter((p) => p.status === 'ONGOING')
      .slice(0, 2)
      .forEach((p) => list.push({ text: `「${p.name}」正在进行中`, time: '实时', level: 'info', to: '/proctor' }))
    plans
      .filter((p) => p.status === 'UPCOMING')
      .slice(0, 2)
      .forEach((p) =>
        list.push({ text: `「${p.name}」将于 ${p.startAt} 开始`, time: '待开考', level: 'info', to: '/exam-plans' })
      )
    todos.value = list
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.banner {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 22px 24px;
  margin-bottom: 12px;
  border-radius: var(--radius);
  color: #fff;
  background: linear-gradient(100deg, #2f4a95 0%, #3d6ef5 100%);
}
.banner h2 { margin: 0 0 8px; font-size: 18px; font-weight: 600; }
.banner p { margin: 0; max-width: 780px; font-size: 12.5px; line-height: 1.75; color: rgba(255, 255, 255, .82); }
.banner-btn {
  margin-left: auto;
  background: rgba(255, 255, 255, .16);
  border-color: rgba(255, 255, 255, .32);
  color: #fff;
}
.banner-btn:hover { background: rgba(255, 255, 255, .26); border-color: #fff; color: #fff; }

.grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  min-height: 90px;
}
.cell {
  padding: 14px 16px;
  border-radius: 8px;
  background: #f8faff;
  border: 1px solid #eef2fb;
}
.cell-label { color: var(--text-3); font-size: 12px; margin-bottom: 8px; }
.cell-num { font-size: 21px; font-weight: 600; letter-spacing: .3px; color: #1f2b4d; }

.note { margin-top: 12px; color: var(--text-3); font-size: 12px; }

.todo { display: flex; flex-direction: column; }
.todo-item {
  display: flex; align-items: center; gap: 10px;
  padding: 11px 2px;
  border-bottom: 1px dashed var(--border);
  font-size: 12.5px;
}
.todo-item:last-child { border-bottom: 0; }
.todo-dot { width: 6px; height: 6px; border-radius: 50%; flex: 0 0 auto; }
.todo-dot.warn { background: #f5a524; }
.todo-dot.info { background: var(--brand); }
.todo-text { color: var(--text); }
.todo-time { margin-left: auto; color: var(--text-3); font-size: 12px; }
</style>
