<template>
  <div class="page-head">
    <div class="page-title">练习中心</div>
    <div class="head-actions">
      <el-radio-group v-model="tab" size="small" @change="onTab">
        <el-radio-button value="practice">刷题</el-radio-button>
        <el-radio-button value="wrong">错题本（{{ summary?.wrongCount ?? 0 }}）</el-radio-button>
      </el-radio-group>
    </div>
  </div>

  <div class="overview">
    <div class="ov">
      <div class="ov-label">累计练习</div>
      <div class="ov-num">{{ summary?.practicedCount ?? 0 }} <span>题</span></div>
    </div>
    <div class="ov">
      <div class="ov-label">正确率</div>
      <div class="ov-num ok">{{ summary?.correctRate ?? 0 }}<span>%</span></div>
    </div>
    <div class="ov">
      <div class="ov-label">答对</div>
      <div class="ov-num">{{ summary?.correctCount ?? 0 }} <span>题</span></div>
    </div>
    <div class="ov">
      <div class="ov-label">待攻克错题</div>
      <div class="ov-num bad">{{ summary?.wrongCount ?? 0 }} <span>题</span></div>
    </div>
  </div>

  <div v-show="tab === 'practice'" class="layout">
    <div class="card bank-panel">
      <div class="card-head"><span class="card-title">选择题库</span></div>
      <div class="banks">
        <div v-for="b in banks" :key="b.id" class="bank" :class="{ active: bankId === b.id }"
             @click="chooseBank(b.id)">
          <div class="b-head">
            <span class="b-name">{{ b.name }}</span>
            <el-tag size="small" effect="plain">{{ b.questionCount }} 题</el-tag>
          </div>
          <div class="b-meta">已练 {{ b.practicedCount }} 题 · 正确率 {{ b.correctRate }}%</div>
          <el-progress :percentage="Math.min(100, Number(b.correctRate))" :stroke-width="5" :show-text="false" />
        </div>
        <el-empty v-if="!banks.length" description="暂无题库" :image-size="60" />
      </div>
    </div>

    <div class="card quiz-panel">
      <div v-if="current" class="quiz">
        <div class="q-head">
          <el-tag size="small" effect="plain">{{ TYPE_LABEL[current.type] }}</el-tag>
          <span class="q-index">第 {{ index + 1 }} / {{ questions.length }} 题</span>
          <span class="q-score">{{ current.score }} 分</span>
        </div>
        <div class="q-stem">{{ current.stem }}</div>

        <div class="q-body">
          <el-radio-group v-if="current.type === 'SC' || current.type === 'TF'" v-model="answer" class="opts">
            <el-radio v-for="o in options" :key="o.key" :value="o.key" class="opt">
              <b>{{ o.key }}</b>. {{ o.text }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group v-else-if="current.type === 'MC'" v-model="multi" class="opts">
            <el-checkbox v-for="o in options" :key="o.key" :value="o.key" class="opt">
              <b>{{ o.key }}</b>. {{ o.text }}
            </el-checkbox>
          </el-checkbox-group>

          <div v-else class="blank">
            <el-input v-model="answer" placeholder="多个空用 | 分隔，如：提、拔、握、压" @keyup.enter="submit" />
          </div>
        </div>

        <div v-if="result" class="feedback" :class="result.correct ? 'right' : 'wrong'">
          <div class="fb-title">
            <span>{{ result.correct ? '回答正确' : '回答错误' }}</span>
            <span class="fb-answer">正确答案：{{ result.correctAnswer }}</span>
          </div>
          <div v-if="result.analysis" class="fb-analysis">解析：{{ result.analysis }}</div>
        </div>

        <div class="q-foot">
          <span class="muted">练习不记入成绩，可反复作答</span>
          <el-button v-if="!result" type="primary" :disabled="!answered" :loading="submitting"
                     @click="submit">提交判分</el-button>
          <el-button v-else type="primary" @click="next">下一题</el-button>
        </div>
      </div>

      <el-empty v-else :description="loading ? '加载中…' : '选择左侧题库开始练习'" />
    </div>
  </div>

  <div v-show="tab === 'wrong'" class="card">
    <el-table v-loading="wrongLoading" :data="wrong" border stripe empty-text="错题本是空的，继续保持">
      <el-table-column label="题型" width="96" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ TYPE_LABEL[row.type as QuestionType] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stem" label="题干" min-width="280" show-overflow-tooltip />
      <el-table-column prop="myAnswer" label="我的作答" width="160" show-overflow-tooltip />
      <el-table-column prop="correctAnswer" label="正确答案" width="160" show-overflow-tooltip />
      <el-table-column prop="wrongTimes" label="错次" width="70" align="center" />
      <el-table-column prop="lastAt" label="最近错误" width="170" />
      <el-table-column label="操作" width="150" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="retry(row)">重做</el-button>
          <el-button link type="danger" size="small" @click="removeWrong(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { TYPE_LABEL } from '@/api/exam'
import type { QuestionType } from '@/api/exam'
import {
  clearWrong, getPracticeQuestions, getPracticeSummary, listPracticeBanks, listWrong, submitPractice,
  type PracticeBank, type PracticeQuestion, type PracticeResult, type PracticeSummary, type WrongItem
} from '@/api/student'

const tab = ref<'practice' | 'wrong'>('practice')
const loading = ref(false)
const submitting = ref(false)
const wrongLoading = ref(false)

const summary = ref<PracticeSummary | null>(null)
const banks = ref<PracticeBank[]>([])
const bankId = ref<number>()

const questions = ref<PracticeQuestion[]>([])
const index = ref(0)
const answer = ref('')
const multi = ref<string[]>([])
const result = ref<PracticeResult | null>(null)

const wrong = ref<WrongItem[]>([])

const current = computed(() => questions.value[index.value])
const options = computed(() => current.value?.options ?? [])
const answered = computed(() =>
  current.value?.type === 'MC' ? multi.value.length > 0 : answer.value.trim().length > 0
)

async function loadSummary() {
  try {
    summary.value = await getPracticeSummary()
  } catch {
    summary.value = null
  }
}

async function loadBanks() {
  try {
    banks.value = await listPracticeBanks()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function chooseBank(id: number) {
  bankId.value = id
  loading.value = true
  try {
    questions.value = await getPracticeQuestions({ bankId: id, size: 20 })
    index.value = 0
    resetAnswer()
    if (!questions.value.length) ElMessage.info('该题库暂无题目')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function resetAnswer() {
  answer.value = ''
  multi.value = []
  result.value = null
}

async function submit() {
  if (!current.value || !answered.value) return
  submitting.value = true
  try {
    const payload = current.value.type === 'MC' ? multi.value.join(',') : answer.value
    result.value = await submitPractice(current.value.id, payload)
    await loadSummary()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}

function next() {
  if (index.value + 1 >= questions.value.length) {
    ElMessage.success('本轮练习已完成，可切换题库或去错题本复习')
    index.value = 0
  } else {
    index.value++
  }
  resetAnswer()
}

async function loadWrong() {
  wrongLoading.value = true
  try {
    wrong.value = await listWrong()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    wrongLoading.value = false
  }
}

async function onTab() {
  if (tab.value === 'wrong') await loadWrong()
  else await loadSummary()
}

async function retry(row: WrongItem) {
  tab.value = 'practice'
  questions.value = [{
    id: row.questionId, bankId: 0, type: row.type, stem: row.stem, options: row.options, score: 10
  }]
  index.value = 0
  resetAnswer()
}

async function removeWrong(row: WrongItem) {
  try {
    await clearWrong(row.questionId)
    ElMessage.success('已移出错题本')
    await loadWrong()
    await loadSummary()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await Promise.all([loadSummary(), loadBanks()])
  const first = banks.value.find((b) => b.questionCount > 0)
  if (first) await chooseBank(first.id)
})
</script>

<style scoped>
.overview { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.ov { background: #fff; border: 1px solid var(--border); border-radius: var(--radius); padding: 14px 18px; }
.ov-label { font-size: 12px; color: var(--text-3); margin-bottom: 6px; }
.ov-num { font-size: 24px; font-weight: 600; color: #1f2b4d; }
.ov-num span { font-size: 12px; font-weight: 400; color: var(--text-3); }
.ov-num.ok { color: #17a673; }
.ov-num.bad { color: #e5484d; }

.layout { display: flex; gap: 12px; align-items: flex-start; }
.bank-panel { width: 300px; flex: 0 0 auto; }
.quiz-panel { flex: 1; min-width: 0; }
.banks { display: flex; flex-direction: column; gap: 8px; }
.bank { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; }
.bank:hover { border-color: #c9d6ff; background: #f8faff; }
.bank.active { border-color: var(--brand); background: var(--brand-soft); }
.b-head { display: flex; align-items: center; gap: 8px; }
.b-name { font-size: 13px; font-weight: 600; flex: 1; }
.b-meta { margin: 6px 0; font-size: 12px; color: var(--text-3); }

.q-head { display: flex; align-items: center; gap: 10px; }
.q-index { font-size: 12px; color: var(--text-3); }
.q-score { margin-left: auto; font-size: 12px; color: var(--text-3); }
.q-stem { margin: 14px 0 16px; font-size: 15px; line-height: 1.8; color: #1f2b4d; }
.opts { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.opt {
  margin: 0; padding: 10px 14px; height: auto;
  border: 1px solid var(--border); border-radius: 8px; width: 100%;
}
.opt:hover { border-color: #c9d6ff; background: #f8faff; }
.opt :deep(.el-radio__label), .opt :deep(.el-checkbox__label) { font-size: 13px; line-height: 1.7; white-space: normal; }
.blank :deep(.el-input__wrapper) { padding: 8px 12px; }

.feedback { margin-top: 16px; padding: 12px 14px; border-radius: 8px; font-size: 13px; line-height: 1.8; }
.feedback.right { background: #eefaf3; border: 1px solid #c6efdb; }
.feedback.wrong { background: #fdf1f1; border: 1px solid #f7d4d4; }
.fb-title { display: flex; gap: 14px; align-items: baseline; font-weight: 600; }
.feedback.right .fb-title { color: #17a673; }
.feedback.wrong .fb-title { color: #d14343; }
.fb-answer { margin-left: auto; font-weight: 400; color: var(--text-2); font-size: 12.5px; }
.fb-analysis { margin-top: 6px; color: var(--text-2); }

.q-foot { display: flex; align-items: center; margin-top: 18px; }
.q-foot .muted { color: var(--text-3); font-size: 12px; }
.q-foot .el-button { margin-left: auto; }
</style>
