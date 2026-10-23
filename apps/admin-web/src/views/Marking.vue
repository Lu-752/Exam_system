<template>
  <div class="page-head">
    <div class="page-title">考试阅卷</div>
    <div class="head-actions">
      <span class="muted tip">共 {{ rows.length }} 份待批阅（含主观题的已交卷答卷）</span>
      <el-button @click="load">刷新</el-button>
    </div>
  </div>

  <div class="card">
    <div class="tools">
      <el-input v-model="keyword" placeholder="搜索考生 / 考试名称" clearable size="small" style="width: 220px">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-radio-group v-model="statusFilter" size="small">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="TODO">待批阅</el-radio-button>
        <el-radio-button value="DONE">已批完</el-radio-button>
      </el-radio-group>
      <span class="tools-count">{{ filteredRows.length }} 份</span>
    </div>

    <el-table v-loading="loading" :data="filteredRows" border stripe row-key="attemptId"
              empty-text="暂无待批阅的主观题答卷" @row-click="open">
      <el-table-column prop="studentName" label="考生" width="120" />
      <el-table-column prop="planName" label="考试" min-width="220" show-overflow-tooltip />
      <el-table-column prop="submittedAt" label="交卷时间" width="170" />
      <el-table-column label="主观题进度" width="150" align="center">
        <template #default="{ row }">
          <div class="prog">
            <el-progress :percentage="Math.round((row.essayMarked / Math.max(1, row.essayTotal)) * 100)"
                         :stroke-width="6" :show-text="false"
                         :status="row.essayMarked >= row.essayTotal ? 'success' : undefined" />
            <span class="prog-txt">{{ row.essayMarked }} / {{ row.essayTotal }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="当前得分" width="120" align="center">
        <template #default="{ row }">{{ row.score }} / {{ row.totalScore }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click.stop="open(row)">
            {{ row.essayMarked >= row.essayTotal ? '复核' : '批阅' }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无待批阅答卷，全部批完啦">
          <el-button size="small" @click="load">刷新列表</el-button>
        </el-empty>
      </template>
    </el-table>
  </div>

  <el-dialog v-model="dialog" :title="`批阅 · ${review?.studentName || ''}`" width="820px" top="5vh">
    <div v-if="review" class="review-head">
      <div class="rh-left">
        <div class="rh-plan">{{ review.planName }}</div>
        <div class="muted rh-sub">交卷 {{ review.submittedAt }} · 主观题 {{ markedCount }} / {{ essays.length }}</div>
      </div>
      <span class="total">{{ review.score }} / {{ review.totalScore }} 分</span>
    </div>

    <div v-for="(it, i) in essays" :key="it.answerId" class="item">
      <div class="ihead">
        <span class="ino">{{ i + 1 }}</span>
        <el-tag size="small" effect="plain">{{ TYPE_LABEL[it.type] }}</el-tag>
        <span class="ifull">满分 {{ it.fullScore }}</span>
        <el-tag v-if="it.manual" size="small" type="success" effect="light">已评 {{ it.score }} 分</el-tag>
        <el-tag v-else size="small" type="warning" effect="plain">未评</el-tag>
      </div>
      <div class="istem">{{ it.stem }}</div>
      <div class="ans-box">
        <div class="ans-label">考生作答</div>
        <div class="ans-text">{{ it.myAnswer || '（未作答）' }}</div>
      </div>
      <div v-if="it.correctAnswer" class="ref">参考答案：{{ it.correctAnswer }}</div>

      <div class="score-row">
        <span class="muted">评分</span>
        <el-input-number v-model="drafts[it.answerId].score" :min="0" :max="Number(it.fullScore)" size="small"
                         controls-position="right" style="width: 96px"
                         @keyup.enter="save(it)" />
        <span class="muted">/ {{ it.fullScore }}</span>
        <el-button size="small" text @click="quick(it, 0)">0 分</el-button>
        <el-button size="small" text @click="quick(it, Number(it.fullScore))">满分</el-button>
        <el-input v-model="drafts[it.answerId].comment" size="small" placeholder="评语（选填，学员成绩页可见）"
                  style="flex: 1; min-width: 140px" @keyup.enter="save(it)" />
        <el-button type="primary" size="small" :loading="saving === it.answerId" @click="save(it)">保存</el-button>
      </div>
    </div>

    <el-empty v-if="!essays.length" description="本答卷没有主观题" />

    <template #footer>
      <span class="foot-tip">回车快速保存当前题 · 全部评完后可继续下一份</span>
      <el-button @click="dialog = false">关闭</el-button>
      <el-button type="primary" plain :disabled="!hasNext" @click="saveAndNext">
        保存并批下一份
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  getReview, listPendingMarking, saveScore, TYPE_LABEL,
  type AttemptReview, type PendingReview, type ReviewItem
} from '@/api/exam'

const loading = ref(false)
const saving = ref<number | null>(null)
const rows = ref<PendingReview[]>([])
const dialog = ref(false)
const review = ref<AttemptReview | null>(null)
const drafts = reactive<Record<number, { score: number; comment: string }>>({})
const keyword = ref('')
const statusFilter = ref<'' | 'TODO' | 'DONE'>('')

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    const done = r.essayMarked >= r.essayTotal
    if (statusFilter.value === 'TODO' && done) return false
    if (statusFilter.value === 'DONE' && !done) return false
    return !kw || (r.studentName || '').toLowerCase().includes(kw)
      || (r.planName || '').toLowerCase().includes(kw)
  })
})

const essays = computed<ReviewItem[]>(() => (review.value?.items ?? []).filter((i) => i.type === 'ESSAY'))
const markedCount = computed(() => essays.value.filter((i) => i.manual).length)

/** 是否还有下一份待批阅 */
const hasNext = computed(() =>
  !!review.value && rows.value.some((r) => r.attemptId !== review.value?.attemptId)
)

async function load() {
  loading.value = true
  try {
    rows.value = await listPendingMarking()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function open(row: PendingReview) {
  dialog.value = true
  try {
    const r = await getReview(row.attemptId)
    apply(r)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function apply(r: AttemptReview) {
  review.value = r
  r.items.filter((i) => i.type === 'ESSAY').forEach((i) => {
    drafts[i.answerId] = { score: Number(i.score ?? 0), comment: i.comment ?? '' }
  })
}

function quick(it: ReviewItem, v: number) {
  drafts[it.answerId].score = v
}

async function save(it: ReviewItem) {
  const d = drafts[it.answerId]
  if (d.score === null || d.score === undefined) return
  saving.value = it.answerId
  try {
    const r = await saveScore(it.answerId, d.score, d.comment)
    apply(r)
    ElMessage.success('已保存，总分已重算')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = null
  }
}

/** 保存当前答卷里所有改了分的题，再打开下一份 */
async function saveAndNext() {
  if (!review.value) return
  const changed = essays.value.filter((it) => {
    const d = drafts[it.answerId]
    return d && (!it.manual || Number(it.score) !== d.score || (it.comment ?? '') !== d.comment)
  })
  for (const it of changed) {
    const d = drafts[it.answerId]
    try {
      await saveScore(it.answerId, d.score, d.comment)
    } catch (e) {
      ElMessage.error((e as Error).message)
      return
    }
  }
  await load()
  const next = rows.value.find((r) => r.attemptId !== review.value?.attemptId)
  if (next) {
    ElMessage.success(changed.length ? `已保存 ${changed.length} 道，继续下一份` : '继续下一份')
    await open(next)
  } else {
    ElMessage.success('全部批完啦')
    dialog.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip { font-size: 12px; margin-right: 4px; }
.tools { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tools-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.prog { display: flex; align-items: center; gap: 8px; }
.prog .el-progress { flex: 1; }
.prog-txt { font-size: 11.5px; color: var(--text-3); flex: 0 0 auto; }

.review-head {
  display: flex; gap: 14px; align-items: center;
  padding-bottom: 12px; margin-bottom: 6px;
  border-bottom: 1px solid var(--border);
}
.rh-plan { font-size: 14px; font-weight: 600; }
.rh-sub { font-size: 12px; margin-top: 2px; }
.review-head .total { margin-left: auto; color: var(--brand); font-weight: 700; font-size: 15px; }

.item { padding: 14px 0; border-bottom: 1px dashed var(--border); }
.item:last-child { border-bottom: 0; }
.ihead { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ino {
  width: 20px; height: 20px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: #eef2fb; color: var(--text-2); font-size: 12px;
}
.ifull { margin-left: auto; color: var(--text-3); font-size: 12px; }
.istem { font-size: 13px; line-height: 1.75; margin-bottom: 8px; white-space: pre-wrap; }
.ans-box { background: #f8faff; border-radius: 6px; padding: 10px 12px; }
.ans-label { font-size: 12px; color: var(--text-3); margin-bottom: 4px; }
.ans-text { font-size: 13px; line-height: 1.75; white-space: pre-wrap; }
.ref { margin-top: 8px; font-size: 12.5px; color: var(--text-2); line-height: 1.7; }
.score-row { display: flex; align-items: center; gap: 8px; margin-top: 10px; }
.foot-tip { float: left; line-height: 32px; font-size: 12px; color: var(--text-3); }
</style>
