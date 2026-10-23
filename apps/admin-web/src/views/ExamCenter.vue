<template>
  <div class="page-head">
    <div class="page-title">考试中心</div>
    <div class="head-actions">
      <el-button @click="load">刷新</el-button>
    </div>
  </div>

  <div class="card tools">
    <el-input v-model="keyword" placeholder="搜索考试名称" clearable style="width: 240px">
      <template #prefix><Icon name="search" /></template>
    </el-input>
    <el-radio-group v-model="statusFilter" size="small">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="ONGOING">进行中</el-radio-button>
      <el-radio-button value="UPCOMING">未开始</el-radio-button>
      <el-radio-button value="DONE">已交卷</el-radio-button>
    </el-radio-group>
    <span class="tools-count">
      共 {{ plans.length }} 场，当前显示 {{ filteredPlans.length }} 场
    </span>
  </div>

  <div v-loading="loading" class="grid">
    <div v-for="p in filteredPlans" :key="p.id" class="exam-card" :class="statusClass(p)">
      <div class="ec-head">
        <span class="ec-name">{{ p.name }}</span>
        <el-tag :type="statusType(p.status)" size="small" effect="light">{{ statusText(p.status) }}</el-tag>
      </div>

      <div class="ec-meta">
        <span class="chip">{{ p.questionCount }} 题</span>
        <span class="chip">满分 {{ p.totalScore }}</span>
        <span class="chip">及格 {{ p.passScore }}</span>
        <span class="chip">{{ Math.round(p.durationSec / 60) }} 分钟</span>
      </div>

      <div class="ec-time">
        <span>{{ short(p.startAt) }} ~ {{ short(p.endAt) }}</span>
        <span class="countdown" :class="countdownClass(p)">{{ countdownText(p) }}</span>
      </div>

      <div class="ec-foot">
        <template v-if="p.attemptStatus === 'SUBMITTED'">
          <template v-if="p.scorePublished === false">
            <span class="ec-score pending">已交卷 · 成绩待发布</span>
            <el-button size="small" disabled>待发布</el-button>
          </template>
          <template v-else>
            <span class="ec-score" :class="{ fail: p.passed === false }">
              {{ p.attemptScore }} 分 · {{ p.passed ? '及格' : '不及格' }}
            </span>
            <el-button type="primary" plain size="small" @click="viewResult(p)">查看成绩</el-button>
          </template>
        </template>
        <template v-else-if="p.attemptStatus === 'IN_PROGRESS'">
          <span class="ec-score">作答中</span>
          <el-button type="primary" size="small" @click="take(p)">继续答题</el-button>
        </template>
        <template v-else-if="p.status === 'ONGOING'">
          <span class="ec-hint">开始后计时，中途退出不保留作答</span>
          <el-button type="primary" size="small" @click="confirmTake(p)">开始考试</el-button>
        </template>
        <template v-else>
          <span class="ec-hint">{{ p.status === 'UPCOMING' ? '尚未开放，请留意开始时间' : '本场考试已结束' }}</span>
          <el-button size="small" disabled>{{ p.status === 'UPCOMING' ? '未开始' : '已结束' }}</el-button>
        </template>
      </div>
    </div>

    <div v-if="!loading && !filteredPlans.length" class="empty">
      <el-empty :description="hasFilter ? '没有匹配的考试，换个关键字试试' : '暂无可参加的考试'">
        <el-button v-if="hasFilter" plain @click="clearFilter">清空筛选</el-button>
        <el-button v-else type="primary" plain @click="router.push('/signup')">去报名中心看看</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import { listMyExams, type Plan } from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const plans = ref<Plan[]>([])
const keyword = ref('')
const statusFilter = ref<'' | 'ONGOING' | 'UPCOMING' | 'DONE'>('')
const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null

const hasFilter = computed(() => !!keyword.value.trim() || !!statusFilter.value)

const filteredPlans = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return plans.value.filter((p) => {
    if (kw && !p.name.toLowerCase().includes(kw)) return false
    if (statusFilter.value === 'DONE') return p.attemptStatus === 'SUBMITTED'
    if (statusFilter.value === 'ONGOING') return p.status === 'ONGOING' && p.attemptStatus !== 'SUBMITTED'
    if (statusFilter.value === 'UPCOMING') return p.status === 'UPCOMING'
    return true
  })
})

function clearFilter() {
  keyword.value = ''
  statusFilter.value = ''
}

const statusText = (s: Plan['status']) => ({ UPCOMING: '未开始', ONGOING: '进行中', FINISHED: '已结束' }[s])
const statusType = (s: Plan['status']) =>
  ({ UPCOMING: 'warning', ONGOING: 'success', FINISHED: 'info' }[s] as 'warning' | 'success' | 'info')
const statusClass = (p: Plan) => ({
  ongoing: p.status === 'ONGOING' && p.attemptStatus !== 'SUBMITTED',
  done: p.attemptStatus === 'SUBMITTED'
})

/** 2026-10-23 09:00:00 → 10-23 09:00 */
function short(s: string) {
  return (s || '').slice(5, 16)
}

function ts(s: string) {
  return new Date((s || '').replace(/-/g, '/')).getTime()
}

function countdownText(p: Plan) {
  if (p.status === 'UPCOMING') {
    const diff = ts(p.startAt) - now.value
    return diff > 0 ? `${human(diff)}后开始` : '即将开始'
  }
  if (p.status === 'ONGOING') {
    const diff = ts(p.endAt) - now.value
    return diff > 0 ? `剩余 ${human(diff)}` : '已到结束时间'
  }
  return '已结束'
}

function countdownClass(p: Plan) {
  if (p.status !== 'ONGOING') return ''
  const diff = ts(p.endAt) - now.value
  return diff <= 30 * 60 * 1000 ? 'urgent' : ''
}

function human(ms: number) {
  const days = ms / 86400000
  if (days > 90) return '长期开放'
  const m = Math.floor(ms / 60000)
  const d = Math.floor(m / 1440)
  const h = Math.floor((m % 1440) / 60)
  const mm = m % 60
  if (d > 0) return `${d} 天${h > 0 ? ` ${h} 小时` : ''}`
  if (h > 0) return `${h} 小时${mm > 0 ? ` ${mm} 分` : ''}`
  return `${Math.max(1, mm)} 分钟`
}

async function load() {
  loading.value = true
  try {
    plans.value = await listMyExams()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function confirmTake(p: Plan) {
  try {
    await ElMessageBox.confirm(
      `共 ${p.questionCount} 题，满分 ${p.totalScore} 分，及格 ${p.passScore} 分，限时 ${Math.round(p.durationSec / 60)} 分钟。` +
        '开始后立即计时，中途退出不保留作答。',
      `开始「${p.name}」？`,
      { type: 'warning', confirmButtonText: '开始作答', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  take(p)
}

function take(p: Plan) {
  router.push(`/exam-take/${p.id}`)
}

function viewResult(p: Plan) {
  if (p.attemptId) router.push(`/exam-result/${p.attemptId}`)
}

onMounted(() => {
  load()
  ticker = setInterval(() => (now.value = Date.now()), 30000)
})

onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
})
</script>

<style scoped>
.tip { font-size: 12px; margin-right: 4px; }
.tools { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tools-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 12px; }
.exam-card {
  position: relative;
  background: #fff; border-radius: var(--radius); padding: 16px 18px 14px 20px;
  box-shadow: 0 1px 2px rgba(31, 36, 48, .04);
  border: 1px solid var(--border);
  display: flex; flex-direction: column; gap: 10px;
  transition: box-shadow .18s, transform .18s;
  overflow: hidden;
}
.exam-card::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px;
  background: #dfe6f5;
}
.exam-card.ongoing::before { background: linear-gradient(180deg, #3fa06a, #2f7a4f); }
.exam-card.done::before { background: linear-gradient(180deg, #5b8cff, #3d6ef5); }
.exam-card:hover { box-shadow: 0 6px 18px rgba(31, 36, 48, .08); transform: translateY(-1px); }

.ec-head { display: flex; align-items: flex-start; gap: 8px; }
.ec-name { font-size: 14.5px; font-weight: 600; flex: 1; line-height: 1.5; }

.ec-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  font-size: 11.5px; color: var(--text-3);
  background: #f5f7fc; border-radius: 4px; padding: 2px 7px;
}

.ec-time {
  display: flex; align-items: center; gap: 10px;
  font-size: 12px; color: var(--text-3);
}
.countdown {
  margin-left: auto; font-size: 11.5px; color: var(--text-3);
  background: #f7f9fd; border-radius: 4px; padding: 2px 7px;
}
.countdown.urgent { color: #c0392b; background: #fdf0ef; font-weight: 600; }

.ec-foot {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  margin-top: 2px; padding-top: 10px; border-top: 1px dashed var(--border);
}
.ec-score { font-size: 12.5px; color: var(--brand); font-weight: 600; }
.ec-score.fail { color: #c0392b; }
.ec-score.pending { color: #b98a2f; }
.ec-hint { font-size: 11.5px; color: var(--text-3); }

.empty { grid-column: 1 / -1; }
</style>
