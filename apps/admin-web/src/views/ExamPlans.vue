<template>
  <div class="page-head">
    <div class="page-title">考试计划</div>
    <div class="head-actions">
      <el-button type="primary" @click="openCreate()">新建考试</el-button>
    </div>
  </div>

  <div class="card">
    <div class="list-tools">
      <el-input v-model="keyword" placeholder="搜索考试名称 / 试卷" clearable style="width: 260px">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 130px">
        <el-option label="未开始" value="UPCOMING" />
        <el-option label="进行中" value="ONGOING" />
        <el-option label="已结束" value="FINISHED" />
      </el-select>
      <span class="tools-count">{{ filteredPlans.length }} 场</span>
    </div>

    <el-table v-loading="loading" :data="filteredPlans" border stripe empty-text="暂无考试计划">
      <el-table-column prop="name" label="考试名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="paperName" label="试卷" min-width="170" show-overflow-tooltip />
      <el-table-column label="考试时间" width="300">
        <template #default="{ row }">
          <div class="time-cell">{{ row.startAt }}</div>
          <div class="time-cell muted">至 {{ row.endAt }}</div>
        </template>
      </el-table-column>
      <el-table-column label="时长" width="80" align="center">
        <template #default="{ row }">{{ Math.round(row.durationSec / 60) }} 分钟</template>
      </el-table-column>
      <el-table-column label="题量/总分" width="100" align="center">
        <template #default="{ row }">{{ row.questionCount }} / {{ row.totalScore }}</template>
      </el-table-column>
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" effect="light">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="我的作答" width="110" align="center">
        <template #default="{ row }">
          <span v-if="row.attemptStatus === 'SUBMITTED'" class="score">{{ row.attemptScore }} 分</span>
          <span v-else-if="row.attemptStatus === 'IN_PROGRESS'" class="muted">作答中</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="成绩发布" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.scorePublished ? 'success' : 'warning'" size="small" effect="light">
            {{ row.scorePublished ? '已发布' : '待发布' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" align="center" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ONGOING' && row.attemptStatus !== 'SUBMITTED'" link type="primary"
                     size="small" @click="take(row)">去考试</el-button>
          <el-button v-else-if="row.attemptStatus === 'SUBMITTED'" link type="primary" size="small"
                     @click="viewResult(row)">查看成绩</el-button>
          <el-button link type="primary" size="small" @click="router.push(`/proctor?planId=${row.id}`)">监考</el-button>
          <el-button link type="primary" size="small" @click="router.push(`/statistics?planId=${row.id}`)">成绩</el-button>
          <el-button v-if="!row.scorePublished" link type="warning" size="small" @click="publish(row)">发布成绩</el-button>
          <el-button v-else link size="small" @click="unpublish(row)">撤回发布</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialog" title="新建考试" width="620px" :close-on-click-modal="false">
    <el-form label-width="96px">
      <el-form-item label="考试名称" required>
        <el-input v-model="form.name" placeholder="如 2026 年度消防安全知识竞赛" />
      </el-form-item>
      <el-form-item label="试卷" required>
        <el-select v-model="form.paperId" placeholder="请选择试卷" style="width: 100%">
          <el-option v-for="p in papers" :key="p.id" :label="`${p.name}（${p.questionCount} 题 / ${p.totalScore} 分）`"
                     :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始时间" required>
        <el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                        placeholder="选择开始时间" style="width: 100%" />
      </el-form-item>
      <el-form-item label="结束时间" required>
        <el-date-picker v-model="form.endAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                        placeholder="选择结束时间" style="width: 100%" />
      </el-form-item>
      <el-form-item label="答题时长">
        <el-input-number v-model="form.minutes" :min="5" :max="600" />
        <span class="muted" style="margin-left: 10px">分钟（超过结束时间也会被强制收卷）</span>
      </el-form-item>
      <el-form-item label="及格分">
        <el-input-number v-model="form.passScore" :min="1" :max="1000" />
      </el-form-item>
      <el-form-item label="可考次数">
        <el-input-number v-model="form.maxAttempts" :min="1" :max="10" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">发布考试</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import { createPlan, listPapers, listPlans, publishScore, unpublishScore, type Paper, type Plan } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const plans = ref<Plan[]>([])
const papers = ref<Paper[]>([])
const keyword = ref('')
const statusFilter = ref<Plan['status'] | ''>('')

const filteredPlans = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return plans.value.filter((p) => {
    if (statusFilter.value && p.status !== statusFilter.value) return false
    if (!kw) return true
    return p.name.toLowerCase().includes(kw) || (p.paperName || '').toLowerCase().includes(kw)
  })
})

const dialog = ref(false)
const form = reactive({
  name: '',
  paperId: undefined as number | undefined,
  startAt: '',
  endAt: '',
  minutes: 30,
  passScore: 60,
  maxAttempts: 1
})

const statusText = (s: Plan['status']) => ({ UPCOMING: '未开始', ONGOING: '进行中', FINISHED: '已结束' }[s])
const statusType = (s: Plan['status']) =>
  ({ UPCOMING: 'warning', ONGOING: 'success', FINISHED: 'info' }[s] as 'warning' | 'success' | 'info')

async function load() {
  loading.value = true
  try {
    plans.value = await listPlans()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function openCreate(prePaperId?: number) {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const fmt = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`

  form.name = ''
  form.paperId = prePaperId
  form.startAt = fmt(now)
  form.endAt = fmt(new Date(now.getTime() + 24 * 3600 * 1000))
  form.minutes = 30
  form.passScore = 60
  form.maxAttempts = 1
  dialog.value = true
  try {
    papers.value = await listPapers()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入考试名称')
  if (!form.paperId) return ElMessage.warning('请选择试卷')
  if (!form.startAt || !form.endAt) return ElMessage.warning('请选择考试时间')

  saving.value = true
  try {
    await createPlan({
      name: form.name,
      paperId: form.paperId,
      startAt: form.startAt,
      endAt: form.endAt,
      durationSec: form.minutes * 60,
      passScore: form.passScore,
      maxAttempts: form.maxAttempts
    })
    ElMessage.success('考试已发布，学员可在考试中心看到')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

function take(row: Plan) {
  router.push(`/exam-take/${row.id}`)
}

async function viewResult(row: Plan) {
  if (row.attemptId) router.push(`/exam-result/${row.attemptId}`)
}

/** 发布成绩（学员才开始可见分数与解析） */
async function publish(row: Plan) {
  try {
    await ElMessageBox.confirm(
      `发布「${row.name}」的成绩后，已交卷学员即可查看分数与答案解析。若仍有主观题未批阅，建议先完成阅卷。`,
      '发布成绩', { type: 'warning', confirmButtonText: '确认发布' }
    )
  } catch {
    return
  }
  try {
    await publishScore(row.id)
    ElMessage.success('成绩已发布')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function unpublish(row: Plan) {
  try {
    await ElMessageBox.confirm(`撤回后学员将看不到「${row.name}」的分数，确认撤回？`, '撤回发布',
      { type: 'warning', confirmButtonText: '确认撤回' })
  } catch {
    return
  }
  try {
    await unpublishScore(row.id)
    ElMessage.success('已撤回，学员端显示为待发布')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await load()
  // 从「试卷管理 → 排考试」跳转过来：直接打开新建弹窗并预选试卷
  if (route.query.new === '1') {
    const pid = route.query.paperId ? Number(route.query.paperId) : undefined
    await openCreate(pid)
    router.replace({ path: '/exam-plans' })
  }
})
</script>

<style scoped>
.time-cell { font-size: 12.5px; line-height: 1.5; }
.score { color: var(--brand); font-weight: 600; }
</style>
