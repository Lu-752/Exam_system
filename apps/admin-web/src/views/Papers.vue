<template>
  <div class="page-head">
    <div class="page-title">试卷管理</div>
    <div class="head-actions">
      <el-button type="primary" @click="openCreate">新建试卷</el-button>
    </div>
  </div>

  <div class="card">
    <div class="tools">
      <el-input v-model="listKeyword" placeholder="搜索试卷名称" clearable style="width: 240px">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <span class="tools-count">共 {{ filteredPapers.length }} 份试卷</span>
    </div>

    <el-table v-loading="loading" :data="filteredPapers" border stripe
              :empty-text="listKeyword ? '没有匹配的试卷' : '暂无试卷'">
      <el-table-column prop="name" label="试卷名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="questionCount" label="题量" width="90" align="center" />
      <el-table-column prop="totalScore" label="总分" width="90" align="center" />
      <el-table-column prop="passScore" label="及格分" width="90" align="center" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="110" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="useForExam(row)">排考试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialog" title="新建试卷" width="900px" :close-on-click-modal="false">
    <el-form label-width="86px">
      <el-form-item label="组卷方式">
        <el-radio-group v-model="mode">
          <el-radio-button value="manual">手动选题</el-radio-button>
          <el-radio-button value="auto">自动抽题</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="试卷名称" required>
        <el-input v-model="form.name" placeholder="如 消防安全综合卷 B" style="width: 340px" />
      </el-form-item>
      <el-form-item label="题库">
        <el-select v-model="bankId" placeholder="全部题库" clearable style="width: 260px" @change="onBankChange">
          <el-option v-for="b in banks" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="及格分">
        <el-input-number v-model="form.passScore" :min="1" :max="1000" />
        <span class="muted" style="margin-left: 10px">
          总分 {{ mode === 'manual' ? totalScore : autoTotal }} 分
        </span>
      </el-form-item>

      <el-form-item v-if="mode === 'manual'" label="选题">
        <div class="picker">
          <div class="picker-bar">
            <el-input v-model="keyword" size="small" placeholder="搜索题干" clearable style="width: 200px"
                      @keyup.enter="loadQuestions" />
            <el-button size="small" @click="loadQuestions">查询</el-button>
            <el-button size="small" text type="primary" @click="selectAll">全选本页</el-button>
            <el-button size="small" text :disabled="!selected.length" @click="clearAll">清空已选</el-button>
            <span class="pick-sum">已选 <b>{{ selected.length }}</b> 题 · 合计 <b>{{ totalScore }}</b> 分</span>
          </div>
          <el-table ref="tableRef" :data="questions" size="small" border max-height="300"
                    :row-key="(row: QuestionItem) => row.id" @selection-change="onSelectionChange">
            <el-table-column type="selection" width="42" reserve-selection />
            <el-table-column label="题型" width="84" align="center">
              <template #default="{ row }">{{ TYPE_LABEL[row.type as QuestionType] }}</template>
            </el-table-column>
            <el-table-column prop="stem" label="题干" min-width="360" show-overflow-tooltip />
            <el-table-column prop="score" label="分值" width="70" align="center" />
          </el-table>
          <div class="muted pick-tip">提示：切换题库或搜索不会清空已选题目</div>
        </div>
      </el-form-item>

      <el-form-item v-else label="抽题规则">
        <div class="rules">
          <div v-for="r in rules" :key="r.type" class="rule-row">
            <span class="rule-type">{{ TYPE_LABEL[r.type as QuestionType] }}</span>
            <el-input-number v-model="r.count" :min="0" :max="50" size="small" />
            <span class="muted">题 ×</span>
            <el-input-number v-model="r.score" :min="1" :max="100" size="small" />
            <span class="muted">分</span>
          </div>
          <div class="muted">按题库现有题目随机抽取；题目不足会明确提示缺口，不会静默少题。</div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存试卷</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { TableInstance } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  createAutoPaper, createPaper, listBanks, listPapers, listQuestions, TYPE_LABEL,
  type Bank, type Paper, type QuestionItem, type QuestionType
} from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const papers = ref<Paper[]>([])
const banks = ref<Bank[]>([])
const questions = ref<QuestionItem[]>([])
const selected = ref<QuestionItem[]>([])
const tableRef = ref<TableInstance>()
const bankId = ref<number | undefined>()
const keyword = ref('')
const listKeyword = ref('')

const filteredPapers = computed(() => {
  const kw = listKeyword.value.trim().toLowerCase()
  if (!kw) return papers.value
  return papers.value.filter((p) => p.name.toLowerCase().includes(kw))
})

const dialog = ref(false)
const mode = ref<'manual' | 'auto'>('manual')
const form = reactive({ name: '', passScore: 60 })
const rules = ref([
  { type: 'SC', count: 4, score: 10 },
  { type: 'MC', count: 2, score: 10 },
  { type: 'TF', count: 2, score: 10 },
  { type: 'BLANK', count: 1, score: 10 },
  { type: 'ESSAY', count: 1, score: 10 }
])

const totalScore = computed(() => selected.value.reduce((s, q) => s + Number(q.score || 0), 0))
const autoTotal = computed(() => rules.value.reduce((s, r) => s + r.count * r.score, 0))

async function load() {
  loading.value = true
  try {
    papers.value = await listPapers()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadQuestions() {
  try {
    questions.value = await listQuestions({ bankId: bankId.value, keyword: keyword.value || undefined })
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function onBankChange() {
  loadQuestions()
}

function selectAll() {
  questions.value.forEach((q) => tableRef.value?.toggleRowSelection(q, true))
}

function clearAll() {
  tableRef.value?.clearSelection()
  selected.value = []
}

async function openCreate() {
  mode.value = 'manual'
  form.name = ''
  form.passScore = 60
  selected.value = []
  keyword.value = ''
  dialog.value = true
  if (!banks.value.length) banks.value = await listBanks()
  if (banks.value.length && !bankId.value) bankId.value = banks.value[0].id
  await loadQuestions()
}

function onSelectionChange(rows: QuestionItem[]) {
  selected.value = rows
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请输入试卷名称')

  saving.value = true
  try {
    if (mode.value === 'manual') {
      if (!selected.value.length) {
        saving.value = false
        return ElMessage.warning('请至少选择 1 道试题')
      }
      await createPaper({
        name: form.name,
        bankId: bankId.value,
        passScore: form.passScore,
        items: selected.value.map((q) => ({ questionId: q.id, score: q.score }))
      })
    } else {
      if (!bankId.value) {
        saving.value = false
        return ElMessage.warning('自动组卷必须指定题库')
      }
      await createAutoPaper({
        name: form.name,
        bankId: bankId.value,
        passScore: form.passScore,
        rules: rules.value.filter((r) => r.count > 0)
      })
    }
    ElMessage.success('试卷已创建')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

function useForExam(row: Paper) {
  router.push(`/exam-plans?new=1&paperId=${row.id}`)
}

onMounted(load)
</script>

<style scoped>
.tools { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tools-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.picker { width: 100%; }
.picker-bar { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.pick-sum { margin-left: auto; font-size: 12px; color: var(--text-3); }
.pick-sum b { color: var(--brand); }
.pick-tip { margin-top: 6px; font-size: 11.5px; }
.rules { display: flex; flex-direction: column; gap: 8px; }
.rule-row { display: flex; align-items: center; gap: 8px; }
.rule-type { width: 64px; font-size: 13px; }
</style>
