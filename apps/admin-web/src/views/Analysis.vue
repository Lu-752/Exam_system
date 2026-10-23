<template>
  <div class="page-head">
    <div class="page-title">成绩分析</div>
    <div class="head-actions">
      <el-select v-model="planId" placeholder="选择考试计划" style="width: 280px" @change="load">
        <el-option v-for="p in plans" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>
  </div>

  <div class="card">
    <div class="card-head">
      <span class="card-title">题型正确率</span>
      <span class="card-date">基于 {{ view?.attemptCount ?? 0 }} 份已交卷答卷</span>
    </div>
    <div v-loading="loading" class="type-grid">
      <div v-for="t in view?.types ?? []" :key="t.type" class="type-card">
        <div class="t-head">
          <span class="t-name">{{ TYPE_LABEL[t.type] }}</span>
          <span class="t-count">{{ t.questionCount }} 题</span>
        </div>
        <div class="t-rate" :class="rateClass(t.correctRate)">{{ t.correctRate }}%</div>
        <el-progress :percentage="Number(t.correctRate)" :stroke-width="6" :show-text="false"
                     :color="rateColor(t.correctRate)" />
        <div class="t-sub">作答 {{ t.answeredCount }} 次 · 正确 {{ t.correctCount }} 次</div>
      </div>
      <el-empty v-if="!loading && !(view?.types ?? []).length" description="暂无作答数据" :image-size="60" />
    </div>
  </div>

  <div class="card">
    <div class="card-head">
      <span class="card-title">题目难度明细</span>
      <span class="card-date">正确率越低越难，建议关注 &lt; 60% 的题目</span>
    </div>
    <el-table v-loading="loading" :data="view?.questions ?? []" border stripe empty-text="暂无数据">
      <el-table-column prop="sortNo" label="序号" width="70" align="center" />
      <el-table-column label="题型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ TYPE_LABEL[row.type as QuestionType] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stem" label="题干" min-width="300" show-overflow-tooltip />
      <el-table-column label="正确率" width="200">
        <template #default="{ row }">
          <div class="rate-cell">
            <el-progress :percentage="Number(row.correctRate)" :stroke-width="8" :show-text="false"
                         :color="rateColor(row.correctRate)" style="flex: 1" />
            <span class="rate-txt" :class="rateClass(row.correctRate)">{{ row.correctRate }}%</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="正确/作答" width="110" align="center">
        <template #default="{ row }">{{ row.correctCount }} / {{ row.answeredCount }}</template>
      </el-table-column>
      <el-table-column label="平均得分" width="120" align="center">
        <template #default="{ row }">{{ row.avgScore }} / {{ row.fullScore }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getPlanAnalysis, listPlans, TYPE_LABEL,
  type AnalysisView, type Plan, type QuestionType
} from '@/api/exam'

const route = useRoute()
const loading = ref(false)
const plans = ref<Plan[]>([])
const planId = ref<number>()
const view = ref<AnalysisView | null>(null)

const rateColor = (r: number) => (r >= 80 ? '#17a673' : r >= 60 ? '#3d6ef5' : r >= 40 ? '#f5a524' : '#e5484d')
const rateClass = (r: number) => (r >= 80 ? 'ok' : r >= 60 ? 'mid' : 'bad')

async function load() {
  if (!planId.value) return
  loading.value = true
  try {
    view.value = await getPlanAnalysis(planId.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    plans.value = await listPlans()
    const q = Number(route.query.planId)
    const hit = plans.value.find((p) => p.id === q)
    planId.value = hit ? hit.id : plans.value[0]?.id
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
})
</script>

<style scoped>
.type-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px; min-height: 80px; }
.type-card { border: 1px solid var(--border); border-radius: 8px; padding: 12px 14px; background: #fbfcff; }
.t-head { display: flex; align-items: baseline; gap: 8px; }
.t-name { font-size: 13px; font-weight: 600; color: var(--text); }
.t-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.t-rate { font-size: 24px; font-weight: 600; margin: 6px 0 8px; }
.t-rate.ok { color: #17a673; }
.t-rate.mid { color: var(--brand); }
.t-rate.bad { color: #e5484d; }
.t-sub { margin-top: 8px; font-size: 12px; color: var(--text-3); }

.rate-cell { display: flex; align-items: center; gap: 8px; }
.rate-txt { width: 46px; text-align: right; font-size: 12.5px; }
.rate-txt.ok { color: #17a673; }
.rate-txt.mid { color: var(--brand); }
.rate-txt.bad { color: #e5484d; }
</style>
