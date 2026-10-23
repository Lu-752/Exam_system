<template>
  <div class="page-head">
    <div class="page-title">考试监控</div>
    <div class="head-actions">
      <el-select v-model="planId" placeholder="选择考试" style="width: 280px" @change="load">
        <el-option v-for="p in plans" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>
  </div>

  <div class="card">
    <div class="stats">
      <div class="stat"><span class="s-label">在考</span><span class="s-num">{{ counts.ongoing }}</span></div>
      <div class="stat"><span class="s-label">已交卷</span><span class="s-num">{{ counts.submitted }}</span></div>
      <div class="stat"><span class="s-label">平均分</span><span class="s-num">{{ counts.avg }}</span></div>
      <div class="stat">
        <span class="s-label">切屏异常</span>
        <span class="s-num" :class="{ danger: counts.suspicious > 0 }">{{ counts.suspicious }}</span>
      </div>
      <div class="stat"><span class="s-label">自动刷新</span><span class="s-num small">{{ auto ? '10s' : '关' }}</span></div>
    </div>

    <div class="tools">
      <el-input v-model="keyword" placeholder="搜索考生姓名" clearable size="small" style="width: 190px">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-radio-group v-model="statusFilter" size="small">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="IN_PROGRESS">答题中</el-radio-button>
        <el-radio-button value="SUBMITTED">已交卷</el-radio-button>
        <el-radio-button value="SUSPICIOUS">切屏异常</el-radio-button>
      </el-radio-group>
      <span class="tools-count">{{ filteredRows.length }} 人</span>
    </div>

    <el-table v-loading="loading" :data="filteredRows" border stripe empty-text="该考试暂无考生作答"
              :row-class-name="rowClass">
      <el-table-column prop="studentName" label="考生" width="130" />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'IN_PROGRESS' ? 'success' : 'info'" size="small" effect="light">
            {{ row.status === 'IN_PROGRESS' ? '答题中' : '已交卷' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" label="开考时间" width="170" />
      <el-table-column label="剩余时间" width="120" align="center">
        <template #default="{ row }">
          <span :class="{ warn: row.status === 'IN_PROGRESS' && row.remainingSec <= 300 }">
            {{ row.status === 'IN_PROGRESS' ? clock(row.remainingSec) : '—' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="answeredCount" label="已答题数" width="110" align="center" />
      <el-table-column label="切屏" width="120" align="center">
        <template #default="{ row }">
          <span v-if="!row.switchCount" class="muted">0</span>
          <el-tooltip v-else :content="`最近一次：${row.lastSwitchAt || '—'}`" placement="top">
            <el-tag :type="row.suspicious ? 'danger' : 'warning'" size="small" effect="light">
              {{ row.switchCount }} 次
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="得分" width="100" align="center">
        <template #default="{ row }">{{ row.score ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 'IN_PROGRESS'" link type="danger" size="small"
                     @click="force(row)">强制交卷</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import { forceSubmit, listPlans, listProctorRows, type Plan, type ProctorRow } from '@/api/exam'

const route = useRoute()
const plans = ref<Plan[]>([])
const planId = ref<number | undefined>()
const rows = ref<ProctorRow[]>([])
const loading = ref(false)
const auto = ref(true)
const keyword = ref('')
const statusFilter = ref<'' | 'IN_PROGRESS' | 'SUBMITTED' | 'SUSPICIOUS'>('')
let timer: ReturnType<typeof setInterval> | null = null

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    if (statusFilter.value === 'SUSPICIOUS') {
      if (!r.suspicious) return false
    } else if (statusFilter.value && r.status !== statusFilter.value) {
      return false
    }
    return !kw || (r.studentName || '').toLowerCase().includes(kw)
  })
})

const rowClass = ({ row }: { row: ProctorRow }) => (row.suspicious ? 'row-warn' : '')

const counts = computed(() => {
  const ongoing = rows.value.filter((r) => r.status === 'IN_PROGRESS').length
  const submitted = rows.value.filter((r) => r.status === 'SUBMITTED')
  const suspicious = rows.value.filter((r) => r.suspicious).length
  const avg = submitted.length
    ? (submitted.reduce((s, r) => s + Number(r.score ?? 0), 0) / submitted.length).toFixed(1)
    : '—'
  return { ongoing, submitted: submitted.length, avg, suspicious }
})

function clock(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

async function load() {
  if (!planId.value) return
  loading.value = true
  try {
    rows.value = await listProctorRows(planId.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function force(row: ProctorRow) {
  try {
    await ElMessageBox.confirm(`确认强制「${row.studentName}」交卷？系统将按已保存的作答结算成绩。`,
      '强制交卷', { type: 'warning', confirmButtonText: '强制交卷' })
  } catch {
    return
  }
  try {
    await forceSubmit(row.attemptId)
    ElMessage.success('已强制交卷')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  try {
    plans.value = await listPlans()
    const q = Number(route.query.planId)
    const hit = plans.value.find((p) => p.id === q)
    if (hit) planId.value = hit.id
    else if (plans.value.length) planId.value = plans.value[0].id
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
  timer = setInterval(() => {
    if (auto.value) load()
  }, 10000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.stats { display: flex; gap: 26px; margin-bottom: 14px; }
.tools { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tools-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.stat { display: flex; align-items: baseline; gap: 6px; }
.s-label { color: var(--text-3); font-size: 12px; }
.s-num { font-size: 20px; font-weight: 600; color: #1f2b4d; }
.s-num.small { font-size: 13px; color: var(--text-3); font-weight: 400; }
.warn { color: #d14343; }
.s-num.danger { color: #d14343; }
:deep(.row-warn) td { background: #fff6f6 !important; }
</style>
