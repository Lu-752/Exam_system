<template>
  <div class="page-head">
    <div class="page-title">交卷记录</div>
    <div class="head-actions">
      <el-select v-model="planId" placeholder="全部考试" clearable style="width: 240px" @change="load">
        <el-option v-for="p in plans" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 130px" @change="load">
        <el-option label="答题中" value="IN_PROGRESS" />
        <el-option label="已交卷" value="SUBMITTED" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索考生姓名" clearable style="width: 180px"
                @keyup.enter="load" @clear="load" />
      <el-button @click="load">查询</el-button>
    </div>
  </div>

  <div class="card">
    <el-table v-loading="loading" :data="paged" border stripe empty-text="暂无答卷记录">
      <el-table-column prop="studentName" label="考生" width="120" />
      <el-table-column prop="planName" label="考试" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'warning'" size="small" effect="light">
            {{ row.status === 'SUBMITTED' ? '已交卷' : '答题中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="得分" width="130" align="center">
        <template #default="{ row }">
          <span v-if="row.status !== 'SUBMITTED'" class="muted">—</span>
          <span v-else :class="row.passed ? 'ok' : 'bad'">{{ row.score }} / {{ row.totalScore }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'SUBMITTED'" :type="row.passed ? 'success' : 'danger'" size="small" effect="plain">
            {{ row.passed ? '及格' : '不及格' }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="主观题" width="100" align="center">
        <template #default="{ row }">
          <span v-if="!row.essayCount" class="muted">—</span>
          <el-tag v-else :type="row.essayMarked >= row.essayCount ? 'success' : 'warning'" size="small" effect="light">
            {{ row.essayMarked }} / {{ row.essayCount }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="切屏" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.switchCount >= 3" type="danger" size="small" effect="light">
            {{ row.switchCount }} 次
          </el-tag>
          <span v-else-if="row.switchCount" class="warn-text">{{ row.switchCount }} 次</span>
          <span v-else class="muted">0</span>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" label="开考时间" width="170" />
      <el-table-column prop="submittedAt" label="交卷时间" width="170">
        <template #default="{ row }">{{ row.submittedAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :disabled="row.status !== 'SUBMITTED'"
                     @click="openDetail(row)">答卷</el-button>
          <el-button link type="primary" size="small" :disabled="!row.essayCount" @click="toMarking">批阅</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="rows.length"
                     layout="total, prev, pager, next" background hide-on-single-page />
    </div>
  </div>

  <el-drawer v-model="drawer" :title="`答卷 · ${detail?.studentName ?? ''}`" size="760px">
    <div v-if="detail" class="detail">
      <div class="d-head">
        <span>{{ detail.planName }}</span>
        <span class="muted">交卷 {{ detail.submittedAt }}</span>
        <span class="d-score" :class="detail.passed ? 'ok' : 'bad'">
          {{ detail.score }} / {{ detail.totalScore }} 分 · {{ detail.passed ? '及格' : '不及格' }}
        </span>
      </div>

      <div v-for="(it, i) in detail.items" :key="it.answerId" class="item">
        <div class="i-head">
          <span class="ino">{{ i + 1 }}</span>
          <el-tag size="small" effect="plain">{{ TYPE_LABEL[it.type] }}</el-tag>
          <span class="i-score" :class="Number(it.score) > 0 ? 'ok' : 'bad'">{{ it.score }} / {{ it.fullScore }}</span>
        </div>
        <div class="i-stem">{{ it.stem }}</div>
        <div class="i-row"><span class="lbl">考生作答</span><span class="val">{{ it.myAnswer || '（未作答）' }}</span></div>
        <div class="i-row"><span class="lbl">正确答案</span><span class="val">{{ it.correctAnswer || '—' }}</span></div>
        <div v-if="it.comment" class="i-row"><span class="lbl">评语</span><span class="val">{{ it.comment }}</span></div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getReview, listAttempts, listPlans, TYPE_LABEL,
  type AttemptReview, type AttemptRow, type Plan
} from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const rows = ref<AttemptRow[]>([])
const plans = ref<Plan[]>([])
const planId = ref<number>()
const status = ref<string>()
const keyword = ref('')

const page = ref(1)
const pageSize = 15
const paged = computed(() => rows.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const drawer = ref(false)
const detail = ref<AttemptReview | null>(null)

async function load() {
  loading.value = true
  page.value = 1
  try {
    rows.value = await listAttempts({
      planId: planId.value,
      status: status.value || undefined,
      keyword: keyword.value || undefined
    })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function openDetail(row: AttemptRow) {
  drawer.value = true
  detail.value = null
  try {
    detail.value = await getReview(row.attemptId)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function toMarking() {
  router.push('/marking')
}

onMounted(async () => {
  await load()
  try {
    plans.value = await listPlans()
  } catch {
    plans.value = []
  }
})
</script>

<style scoped>
.ok { color: #17a673; font-weight: 600; }
.bad { color: #e5484d; font-weight: 600; }
.muted { color: var(--text-3); }
.warn-text { color: #c8842a; }
.pager { display: flex; justify-content: flex-end; padding: 12px 4px 2px; }

.detail { font-size: 13px; }
.d-head {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  padding-bottom: 12px; margin-bottom: 6px; border-bottom: 1px solid var(--border);
}
.d-score { margin-left: auto; font-weight: 600; }

.item { padding: 14px 0; border-bottom: 1px dashed var(--border); }
.item:last-child { border-bottom: 0; }
.i-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ino {
  width: 20px; height: 20px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: #eef2fb; color: var(--text-2); font-size: 12px;
}
.i-score { margin-left: auto; font-size: 12.5px; }
.i-stem { line-height: 1.75; margin-bottom: 8px; color: #1f2b4d; }
.i-row { display: flex; gap: 10px; padding: 4px 0; font-size: 12.5px; }
.lbl { color: var(--text-3); flex: 0 0 66px; }
.val { color: var(--text); white-space: pre-wrap; line-height: 1.7; }
</style>
