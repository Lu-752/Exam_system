<template>
  <div class="page-head">
    <div class="page-title">成绩统计</div>
    <div class="head-actions">
      <el-select v-model="planId" placeholder="选择考试计划" style="width: 280px" @change="loadDetail">
        <el-option v-for="p in plans" :key="p.planId" :label="p.planName" :value="p.planId" />
      </el-select>
      <el-button @click="router.push(`/analysis?planId=${planId}`)">题目分析</el-button>
      <el-button @click="refresh">刷新</el-button>
    </div>
  </div>

  <div class="stat-grid">
    <div class="stat">
      <div class="s-label">参考人次</div>
      <div class="s-num">{{ summary?.attemptCount ?? 0 }}</div>
      <div class="s-sub">已交卷 {{ summary?.submittedCount ?? 0 }}</div>
    </div>
    <div class="stat">
      <div class="s-label">及格率</div>
      <div class="s-num brand">{{ fmt(summary?.passRate) }}%</div>
      <div class="s-sub">及格 {{ summary?.passCount ?? 0 }} 人 · 及格线 {{ summary?.passScore ?? '-' }}</div>
    </div>
    <div class="stat">
      <div class="s-label">平均分</div>
      <div class="s-num">{{ fmt(summary?.avgScore) }}</div>
      <div class="s-sub">满分 {{ summary?.totalScore ?? '-' }}</div>
    </div>
    <div class="stat">
      <div class="s-label">最高 / 最低</div>
      <div class="s-num">{{ fmt(summary?.maxScore) }} <span class="slash">/</span> {{ fmt(summary?.minScore) }}</div>
      <div class="s-sub">{{ summary?.paperName || '—' }}</div>
    </div>
  </div>

  <div class="card">
    <div class="card-head">
      <span class="card-title">分数段分布</span>
      <span class="card-date">按得分率划分</span>
    </div>
    <div v-loading="loading" class="bars">
      <div v-for="b in detail?.distribution ?? []" :key="b.label" class="bar-row">
        <span class="bar-label">{{ b.label }}</span>
        <div class="bar-track">
          <div class="bar-fill" :style="{ width: barWidth(b.count) }" />
        </div>
        <span class="bar-count">{{ b.count }} 人</span>
      </div>
    </div>
  </div>

  <div class="card">
    <div class="card-head">
      <span class="card-title">成绩排名</span>
      <span class="card-date">共 {{ detail?.rows.length ?? 0 }} 条答卷</span>
    </div>
    <el-table v-loading="loading" :data="detail?.rows ?? []" border stripe empty-text="暂无答卷">
      <el-table-column label="排名" width="70" align="center">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="studentName" label="考生" width="140" />
      <el-table-column label="得分" width="140" align="center">
        <template #default="{ row }">
          <span v-if="row.score === null" class="muted">—</span>
          <span v-else :class="row.passed ? 'ok' : 'bad'">{{ row.score }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status !== 'SUBMITTED'" type="info" size="small" effect="light">进行中</el-tag>
          <el-tag v-else :type="row.passed ? 'success' : 'danger'" size="small" effect="light">
            {{ row.passed ? '及格' : '不及格' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" label="开始时间" width="180" />
      <el-table-column prop="submittedAt" label="交卷时间" width="180" />
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" :disabled="row.status !== 'SUBMITTED'"
                     @click="router.push(`/exam-result/${row.attemptId}`)">答卷</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPlanStats, listPlanStats, type PlanDetail, type PlanStat } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const plans = ref<PlanStat[]>([])
const planId = ref<number>()
const detail = ref<PlanDetail | null>(null)

const summary = computed(() => detail.value?.summary)
const maxCount = computed(() =>
  Math.max(1, ...(detail.value?.distribution ?? []).map((b) => b.count))
)

const fmt = (v?: number) => (v === null || v === undefined ? '—' : String(v))
const barWidth = (n: number) => `${Math.round((n / maxCount.value) * 100)}%`

async function refresh() {
  try {
    plans.value = await listPlanStats()
    const q = Number(route.query.planId)
    const hit = plans.value.find((p) => p.planId === q)
    if (hit) planId.value = hit.planId
    else if (!planId.value && plans.value.length) planId.value = plans.value[0].planId
    await loadDetail()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function loadDetail() {
  if (!planId.value) return
  loading.value = true
  try {
    detail.value = await getPlanStats(planId.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.stat { background: #fff; border: 1px solid var(--border); border-radius: var(--radius); padding: 16px 18px; }
.s-label { font-size: 12px; color: var(--text-3); margin-bottom: 8px; }
.s-num { font-size: 26px; font-weight: 600; color: #1f2b4d; letter-spacing: .3px; }
.s-num.brand { color: var(--brand); }
.s-num .slash { color: var(--text-3); font-weight: 400; font-size: 18px; }
.s-sub { margin-top: 6px; font-size: 12px; color: var(--text-3); }

.bars { display: flex; flex-direction: column; gap: 12px; padding: 4px 0; min-height: 120px; }
.bar-row { display: flex; align-items: center; gap: 12px; font-size: 12.5px; }
.bar-label { width: 72px; color: var(--text-2); }
.bar-track { flex: 1; height: 16px; background: #f2f5fc; border-radius: 8px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 8px; background: linear-gradient(90deg, #6d8ff8, #3d6ef5); transition: width .3s; }
.bar-count { width: 56px; text-align: right; color: var(--text-3); }

.ok { color: #17a673; font-weight: 600; }
.bad { color: #e5484d; font-weight: 600; }
.muted { color: var(--text-3); }
</style>
