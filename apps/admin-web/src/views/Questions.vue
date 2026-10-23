<template>
  <div class="page-head">
    <div class="page-title">题库与试题</div>
    <div class="head-actions">
      <el-button @click="openBankDialog">新建题库</el-button>
      <el-button :disabled="!currentBank" @click="openImport">批量导入</el-button>
      <el-button type="primary" :disabled="!currentBank" @click="openQuestionDialog">
        新建试题
      </el-button>
    </div>
  </div>

  <div class="layout">
    <div class="card bank-pane">
      <div class="card-head">
        <span class="card-title">题库</span>
        <span class="card-date">{{ filteredBanks.length }} / {{ banks.length }}</span>
      </div>
      <el-input v-model="bankKeyword" size="small" placeholder="搜索题库名称 / 等级" clearable class="bank-search">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <div v-if="outlineFilter" class="ol-filter">
        <span>按大纲「{{ outlineFilterName }}」筛选</span>
        <a @click="outlineFilter = null">清除</a>
      </div>
      <div v-loading="bankLoading" class="bank-list">
        <div v-for="b in filteredBanks" :key="b.id" class="bank-item" :class="{ active: currentBank?.id === b.id }"
             @click="selectBank(b)">
          <div class="bank-name">
            {{ b.name }}
            <a class="bank-edit" title="编辑题库" @click.stop="openBankDialog(b)">编辑</a>
          </div>
          <div class="bank-meta">
            <span>{{ b.level || '未分级' }}</span>
            <span v-if="b.outlineName" class="ol-tag" :title="`关联大纲：${b.outlineName}`">{{ b.outlineName }}</span>
            <span v-if="b.recommended" class="rec">推荐</span>
          </div>
        </div>
        <el-empty v-if="!banks.length" description="暂无题库" :image-size="60" />
        <el-empty v-else-if="!filteredBanks.length" description="没有匹配的题库" :image-size="60" />
      </div>
    </div>

    <div class="card q-pane">
      <div class="q-head">
        <span class="card-title">{{ currentBank ? currentBank.name : '请选择题库' }}</span>
        <div class="q-filters">
          <el-select v-model="filterType" size="small" placeholder="全部题型" clearable style="width: 120px"
                     @change="loadQuestions">
            <el-option v-for="(label, key) in TYPE_LABEL" :key="key" :label="label" :value="key" />
          </el-select>
          <el-input v-model="keyword" size="small" placeholder="搜索题干" clearable style="width: 180px"
                    @keyup.enter="loadQuestions" />
          <el-button size="small" @click="loadQuestions">查询</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="questions" border size="small" empty-text="该题库下暂无试题">
        <el-table-column type="expand" width="26">
          <template #default="{ row }">
            <div v-if="row.type === 'COMPOSITE'" class="expand">
              <div v-for="(c, i) in row.children ?? []" :key="c.id" class="expand-row">
                <span class="expand-no">{{ i + 1 }}</span>
                <el-tag size="small" effect="plain" type="info">{{ TYPE_LABEL[c.type as QuestionType] }}</el-tag>
                <span class="expand-stem">{{ c.stem }}</span>
                <span class="expand-ans">答案：{{ c.answer || '—' }}</span>
                <span class="expand-score">{{ c.score }} 分</span>
              </div>
              <div v-if="!(row.children ?? []).length" class="expand-muted">暂无子题</div>
            </div>
            <div v-else class="expand-muted">该题无子题</div>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ TYPE_LABEL[row.type as QuestionType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stem" label="题干" min-width="300" show-overflow-tooltip />
        <el-table-column label="答案" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.type === 'COMPOSITE'" class="muted">含 {{ (row.children ?? []).length }} 个子题</span>
            <span v-else>{{ row.answer }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="70" align="center" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeQuestion(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

  <el-dialog v-model="bankDialog" :title="bankDialogTitle" width="420px">
    <el-form label-width="80px">
      <el-form-item label="名称" required>
        <el-input v-model="bankForm.name" placeholder="如 消防安全基础题库" />
      </el-form-item>
      <el-form-item label="职业等级">
        <el-select v-model="bankForm.level" style="width: 100%">
          <el-option v-for="l in ['初级', '中级', '高级']" :key="l" :label="l" :value="l" />
        </el-select>
      </el-form-item>
      <el-form-item label="关联大纲">
        <el-select v-model="bankForm.outlineId" style="width: 100%" clearable
                   placeholder="选择培训大纲（可空）">
          <el-option v-for="o in outlines" :key="o.id" :label="`${o.name}（${o.version || '—'}）`" :value="o.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="bankDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveBank">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="qDialog" title="新建试题" width="760px" :close-on-click-modal="false">
    <el-form label-width="80px">
      <el-form-item label="题型" required>
        <el-radio-group v-model="form.type" @change="onTypeChange">
          <el-radio-button v-for="k in TYPE_KEYS" :key="k" :value="k">
            {{ TYPE_LABEL[k] }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item :label="form.type === 'COMPOSITE' ? '材料' : '题干'" required>
        <el-input v-model="form.stem" type="textarea" :rows="form.type === 'COMPOSITE' ? 4 : 2"
                  :placeholder="form.type === 'COMPOSITE' ? '材料/背景描述，例如案例材料' : '请输入题干'" />
      </el-form-item>

      <el-form-item v-if="hasOptions" label="选项" required>
        <div class="opt-editor">
          <div v-for="(o, i) in form.options" :key="i" class="opt-row">
            <span class="opt-key">{{ o.key }}</span>
            <el-input v-model="o.text" size="small" placeholder="选项内容" />
            <el-button link type="danger" size="small" :disabled="form.options.length <= 2"
                       @click="removeOption(i)">删除</el-button>
          </div>
          <el-button size="small" plain @click="addOption">+ 添加选项</el-button>
        </div>
      </el-form-item>

      <el-form-item v-if="form.type === 'SC'" label="正确答案" required>
        <el-select v-model="scAnswer" style="width: 140px" @change="(v: string) => (form.answer = v)">
          <el-option v-for="o in form.options" :key="o.key" :label="o.key" :value="o.key" />
        </el-select>
      </el-form-item>

      <el-form-item v-else-if="form.type === 'MC'" label="正确答案" required>
        <el-checkbox-group v-model="mcAnswer">
          <el-checkbox v-for="o in form.options" :key="o.key" :value="o.key">{{ o.key }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item v-else-if="form.type === 'TF'" label="正确答案" required>
        <el-radio-group v-model="form.answer">
          <el-radio value="T">正确</el-radio>
          <el-radio value="F">错误</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-else-if="form.type === 'BLANK'" label="正确答案" required>
        <el-input v-model="form.answer" placeholder="多空用 | 分隔，同一空的多个答案用 / 分隔，如：保险销/保险销钉" />
      </el-form-item>

      <!-- 题冒题：材料 + 子题 -->
      <el-form-item v-if="form.type === 'COMPOSITE'" label="子题" required>
        <div class="sub-editor">
          <div v-for="(s, i) in form.children" :key="i" class="sub-card">
            <div class="sub-top">
              <span class="sub-idx">{{ i + 1 }}</span>
              <el-select v-model="s.type" size="small" style="width: 118px"
                         @change="onSubTypeChange(s)">
                <el-option v-for="k in SUB_TYPES" :key="k" :label="TYPE_LABEL[k]" :value="k" />
              </el-select>
              <span class="muted">分值</span>
              <el-input-number v-model="s.score" size="small" :min="1" :max="100"
                               controls-position="right" style="width: 100px" />
              <el-button link type="danger" size="small" :disabled="form.children.length <= 1"
                         class="sub-del" @click="removeSub(i)">删除子题</el-button>
            </div>

            <el-input v-model="s.stem" type="textarea" :rows="2" :placeholder="`子题 ${i + 1} 题干`" />

            <div v-if="['SC', 'MC'].includes(s.type)" class="opt-editor sub-opts">
              <div v-for="(o, j) in s.options" :key="j" class="opt-row">
                <span class="opt-key">{{ o.key }}</span>
                <el-input v-model="o.text" size="small" placeholder="选项内容" />
                <el-button link type="danger" size="small" :disabled="s.options.length <= 2"
                           @click="removeSubOption(s, j)">删除</el-button>
              </div>
              <el-button size="small" plain @click="addSubOption(s)">+ 添加选项</el-button>
            </div>

            <div class="sub-ans">
              <span class="sub-ans-label">答案</span>
              <el-select v-if="s.type === 'SC'" v-model="s.answer" size="small" style="width: 120px">
                <el-option v-for="o in s.options" :key="o.key" :label="o.key" :value="o.key" />
              </el-select>
              <el-checkbox-group v-else-if="s.type === 'MC'" v-model="s.mc">
                <el-checkbox v-for="o in s.options" :key="o.key" :value="o.key">{{ o.key }}</el-checkbox>
              </el-checkbox-group>
              <el-radio-group v-else-if="s.type === 'TF'" v-model="s.answer">
                <el-radio value="T">正确</el-radio>
                <el-radio value="F">错误</el-radio>
              </el-radio-group>
              <el-input v-else-if="s.type === 'BLANK'" v-model="s.answer" size="small"
                        placeholder="多空用 | 分隔" style="max-width: 320px" />
              <span v-else class="muted">主观题，无需预设答案（考后人工评分）</span>
            </div>
          </div>
          <el-button size="small" type="primary" plain @click="addSub">+ 添加子题</el-button>
        </div>
      </el-form-item>

      <el-form-item v-if="form.type === 'COMPOSITE'" label="总分">
        <span class="total-score">{{ subTotal }} 分</span>
        <span class="muted" style="margin-left: 8px">由子题分值自动累加</span>
      </el-form-item>

      <el-form-item v-else label="分值">
        <el-input-number v-model="form.score" :min="1" :max="100" />
        <span class="muted" style="margin-left: 10px">分</span>
      </el-form-item>

      <el-form-item label="解析">
        <el-input v-model="form.analysis" type="textarea" :rows="2" placeholder="选填，交卷后展示" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="qDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveQuestion">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="imp.visible" title="批量导入试题" width="660px" :close-on-click-modal="false">
    <div class="imp-tip">
      导入到题库「{{ currentBank?.name }}」。列顺序：题型 / 题干 / 选项 / 答案 / 分值 / 解析 / 难度。<br />
      选择题选项用 <b>|</b> 分隔（如 <code>A.选项一|B.选项二</code>）；多选答案写 <code>A,B,C</code>；
      判断题答案写 <code>对</code> 或 <code>错</code>；填空多个可接受答案用 <code>/</code> 分隔。
    </div>

    <div class="imp-actions">
      <el-button size="small" @click="downloadTpl">下载 CSV 模板</el-button>
      <span class="imp-hint">支持 单选 / 多选 / 判断 / 填空 / 简答</span>
    </div>

    <el-upload drag :auto-upload="false" :show-file-list="false" accept=".csv,text/csv"
               :on-change="onFile" class="imp-upload">
      <div class="up-main">把 CSV 文件拖到这里，或点击选择</div>
      <div class="up-sub">{{ imp.fileName || '支持 UTF-8 编码的 .csv 文件' }}</div>
    </el-upload>

    <div v-if="imp.text" class="imp-preview">
      <span>已读取 {{ imp.rowCount }} 行有效数据</span>
      <el-button link type="primary" size="small" @click="clearImport">重新选择</el-button>
    </div>

    <div v-if="imp.result" class="imp-result">
      <el-alert :type="imp.result.failed ? 'warning' : 'success'" :closable="false" show-icon
                :title="`共 ${imp.result.total} 行：成功 ${imp.result.success} 条，失败 ${imp.result.failed} 条`" />
      <el-table v-if="imp.result.errors.length" :data="imp.result.errors" size="small" border
                max-height="200" class="err-table">
        <el-table-column prop="line" label="行号" width="70" align="center" />
        <el-table-column prop="message" label="失败原因" />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="imp.visible = false">关闭</el-button>
      <el-button type="primary" :loading="imp.loading" :disabled="!imp.text" @click="doImport">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  createBank, createQuestion, deleteQuestion, importQuestions, listBanks, listQuestions,
  questionImportTemplate, updateBank, TYPE_LABEL, type Bank, type QuestionItem, type QuestionType
} from '@/api/exam'
import { listOutlines, type Outline } from '@/api/misc'
import { countRows, downloadCsv, readTextFile } from '@/utils/csv'

const banks = ref<Bank[]>([])
const currentBank = ref<Bank | null>(null)
const questions = ref<QuestionItem[]>([])
const bankLoading = ref(false)
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const filterType = ref('')
const bankKeyword = ref('')

/** 题库搜索：名称 / 等级；支持按大纲筛选（来自大纲管理页深链） */
const outlineFilter = ref<number | null>(null)
const outlines = ref<Outline[]>([])

const filteredBanks = computed(() => {
  let list = banks.value
  if (outlineFilter.value) list = list.filter((b) => b.outlineId === outlineFilter.value)
  const kw = bankKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((b) =>
    b.name.toLowerCase().includes(kw) || (b.level || '').toLowerCase().includes(kw))
})

const outlineFilterName = computed(() =>
  outlines.value.find((o) => o.id === outlineFilter.value)?.name ?? '')

const bankDialog = ref(false)
const bankForm = reactive<{ id?: number; name: string; level: string; outlineId?: number | null }>({
  name: '', level: '初级', outlineId: null
})
const bankDialogTitle = computed(() => (bankForm.id ? '编辑题库' : '新建题库'))

interface SubForm {
  type: QuestionType
  stem: string
  options: { key: string; text: string }[]
  answer: string
  mc: string[]
  score: number
}

const TYPE_KEYS: QuestionType[] = ['SC', 'MC', 'TF', 'BLANK', 'ESSAY', 'COMPOSITE']
const SUB_TYPES: QuestionType[] = ['SC', 'MC', 'TF', 'BLANK', 'ESSAY']

const qDialog = ref(false)
const form = reactive<{
  type: QuestionType
  stem: string
  options: { key: string; text: string }[]
  answer: string
  analysis: string
  score: number
  children: SubForm[]
}>({ type: 'SC', stem: '', options: [], answer: '', analysis: '', score: 10, children: [] })

const scAnswer = ref('')
const mcAnswer = ref<string[]>([])

const hasOptions = computed(() => ['SC', 'MC'].includes(form.type))

const subTotal = computed(() => form.children.reduce((n, c) => n + (Number(c.score) || 0), 0))

const LETTERS = 'ABCDEFGH'

function newSub(): SubForm {
  return {
    type: 'SC',
    stem: '',
    options: [
      { key: 'A', text: '' },
      { key: 'B', text: '' },
      { key: 'C', text: '' },
      { key: 'D', text: '' }
    ],
    answer: '',
    mc: [],
    score: 5
  }
}

function resetForm() {
  form.type = 'SC'
  form.stem = ''
  form.options = [
    { key: 'A', text: '' },
    { key: 'B', text: '' },
    { key: 'C', text: '' },
    { key: 'D', text: '' }
  ]
  form.answer = ''
  form.analysis = ''
  form.score = 10
  form.children = []
  scAnswer.value = ''
  mcAnswer.value = []
}

async function loadBanks() {
  bankLoading.value = true
  try {
    banks.value = await listBanks()
    if (!currentBank.value) {
      const first = filteredBanks.value[0] ?? banks.value[0]
      if (first) selectBank(first)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    bankLoading.value = false
  }
}

function selectBank(b: Bank) {
  currentBank.value = b
  loadQuestions()
}

async function loadQuestions() {
  if (!currentBank.value) return
  loading.value = true
  try {
    questions.value = await listQuestions({
      bankId: currentBank.value.id,
      keyword: keyword.value || undefined,
      type: filterType.value || undefined
    })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openBankDialog(b?: Bank) {
  bankForm.id = b?.id
  bankForm.name = b?.name ?? ''
  bankForm.level = b?.level ?? '初级'
  bankForm.outlineId = b?.outlineId ?? null
  bankDialog.value = true
}

async function saveBank() {
  if (!bankForm.name.trim()) return ElMessage.warning('请输入题库名称')
  saving.value = true
  try {
    let b: Bank
    if (bankForm.id) {
      b = await updateBank(bankForm.id, { ...bankForm })
      ElMessage.success('题库已保存')
    } else {
      b = await createBank({ ...bankForm })
      ElMessage.success('题库已创建')
    }
    bankDialog.value = false
    await loadBanks()
    selectBank(b)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function loadOutlines() {
  try {
    outlines.value = (await listOutlines({ status: 'ACTIVE' })).filter((o) => o.status === 'ACTIVE')
  } catch { /* 大纲不可用时不阻塞题库页 */ }
}

function openQuestionDialog() {
  resetForm()
  qDialog.value = true
}

function onTypeChange() {
  form.answer = ''
  scAnswer.value = ''
  mcAnswer.value = []
  if (['SC', 'MC'].includes(form.type) && form.options.length < 2) {
    form.options = [{ key: 'A', text: '' }, { key: 'B', text: '' }]
  }
  if (form.type === 'COMPOSITE' && !form.children.length) {
    form.children.push(newSub())
  }
}

function addSub() {
  if (form.children.length >= 10) return ElMessage.warning('一道题冒题最多 10 个子题')
  form.children.push(newSub())
}

function removeSub(i: number) {
  if (form.children.length <= 1) return
  form.children.splice(i, 1)
}

function onSubTypeChange(s: SubForm) {
  s.answer = ''
  s.mc = []
  if (['SC', 'MC'].includes(s.type) && s.options.length < 2) {
    s.options = [{ key: 'A', text: '' }, { key: 'B', text: '' }]
  }
}

function addSubOption(s: SubForm) {
  if (s.options.length >= 8) return
  s.options.push({ key: LETTERS[s.options.length], text: '' })
}

function removeSubOption(s: SubForm, j: number) {
  if (s.options.length <= 2) return
  s.options.splice(j, 1)
  s.options.forEach((o, idx) => (o.key = LETTERS[idx]))
  s.answer = ''
  s.mc = []
}

function addOption() {
  if (form.options.length >= 8) return
  form.options.push({ key: LETTERS[form.options.length], text: '' })
}
function removeOption(i: number) {
  form.options.splice(i, 1)
  form.options.forEach((o, idx) => (o.key = LETTERS[idx]))
  form.answer = ''
  mcAnswer.value = []
}

async function saveQuestion() {
  if (!form.stem.trim()) return ElMessage.warning(form.type === 'COMPOSITE' ? '请输入材料内容' : '请输入题干')

  let answer = form.answer
  if (form.type === 'MC') {
    if (!mcAnswer.value.length) return ElMessage.warning('请选择正确答案')
    answer = mcAnswer.value.join(',')
  }
  if (['SC', 'TF', 'BLANK'].includes(form.type) && !answer.trim()) {
    return ElMessage.warning('请填写正确答案')
  }
  if (['SC', 'MC'].includes(form.type) && form.options.some((o) => !o.text.trim())) {
    return ElMessage.warning('选项内容不能为空')
  }

  if (form.type === 'COMPOSITE') {
    if (!form.children.length) return ElMessage.warning('请至少添加 1 个子题')
    for (let i = 0; i < form.children.length; i++) {
      const s = form.children[i]
      if (!s.stem.trim()) return ElMessage.warning(`请填写子题 ${i + 1} 的题干`)
      if (['SC', 'MC'].includes(s.type) && s.options.some((o) => !o.text.trim())) {
        return ElMessage.warning(`子题 ${i + 1} 的选项内容不能为空`)
      }
      if (s.type === 'MC' && !s.mc.length) return ElMessage.warning(`请选择子题 ${i + 1} 的正确答案`)
      if (['SC', 'TF', 'BLANK'].includes(s.type) && !s.answer.trim()) {
        return ElMessage.warning(`请填写子题 ${i + 1} 的正确答案`)
      }
    }
  }

  saving.value = true
  try {
    await createQuestion({
      bankId: currentBank.value?.id,
      type: form.type,
      stem: form.stem,
      options: hasOptions.value ? form.options : [],
      answer: form.type === 'COMPOSITE' ? '' : answer,
      analysis: form.analysis,
      score: form.type === 'COMPOSITE' ? subTotal.value : form.score,
      difficulty: '中等',
      children:
        form.type === 'COMPOSITE'
          ? form.children.map((s) => ({
              type: s.type,
              stem: s.stem,
              options: ['SC', 'MC'].includes(s.type) ? s.options : [],
              answer: s.type === 'MC' ? s.mc.join(',') : s.answer,
              analysis: '',
              score: s.score
            }))
          : []
    })
    const tip = form.type === 'COMPOSITE'
      ? `题冒题已保存（${form.children.length} 个子题，共 ${subTotal.value} 分）`
      : '试题已保存'
    ElMessage.success(tip)
    qDialog.value = false
    await loadQuestions()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function removeQuestion(row: QuestionItem) {
  try {
    await ElMessageBox.confirm('确定删除该试题？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteQuestion(row.id)
    ElMessage.success('已删除')
    await loadQuestions()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

// ---------------- 批量导入 ----------------

const imp = reactive<{
  visible: boolean
  loading: boolean
  text: string
  fileName: string
  rowCount: number
  result: { total: number; success: number; failed: number; errors: { line: number; message: string }[] } | null
}>({ visible: false, loading: false, text: '', fileName: '', rowCount: 0, result: null })

function openImport() {
  if (!currentBank.value) return ElMessage.warning('请先选择题库')
  Object.assign(imp, { visible: true, loading: false, text: '', fileName: '', rowCount: 0, result: null })
}

function clearImport() {
  imp.text = ''
  imp.fileName = ''
  imp.rowCount = 0
  imp.result = null
}

async function downloadTpl() {
  try {
    const tpl = await questionImportTemplate()
    downloadCsv('试题导入模板.csv', tpl)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function onFile(f: UploadFile) {
  if (!f.raw) return
  try {
    const text = await readTextFile(f.raw as File)
    imp.text = text
    imp.fileName = f.name
    imp.rowCount = Math.max(0, countRows(text))
    imp.result = null
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function doImport() {
  if (!currentBank.value) return
  imp.loading = true
  try {
    const res = await importQuestions(currentBank.value.id, imp.text)
    imp.result = res
    if (res.success > 0) {
      ElMessage.success(`已导入 ${res.success} 道试题`)
      await loadQuestions()
    } else {
      ElMessage.warning('没有试题导入成功，请按失败原因修正后重试')
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '导入失败')
  } finally {
    imp.loading = false
  }
}

const route = useRoute()

onMounted(async () => {
  await loadBanks()
  loadOutlines()
  // 深链：/questions?outlineId=x 按大纲过滤题库（来自大纲管理页「查看题库」）
  const oid = Number(route.query.outlineId)
  if (oid) outlineFilter.value = oid
  // 深链：/questions?new=1&type=COMPOSITE 直开新建弹窗（便于演示与自动化测试）
  if (currentBank.value && route.query.new === '1') {
    openQuestionDialog()
    const t = route.query.type as QuestionType | undefined
    if (t && TYPE_KEYS.includes(t)) {
      form.type = t
      onTypeChange()
    }
  }
  // 深链：/questions?import=1 直开批量导入弹窗（演示与自动化截图用）
  if (currentBank.value && route.query.import === '1') {
    openImport()
  }
})
</script>

<style scoped>
.imp-tip {
  font-size: 12.5px; line-height: 1.9; color: var(--text-2);
  background: #fafbfe; border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px;
}
.imp-tip code { background: #f0f3fa; border-radius: 3px; padding: 1px 4px; font-size: 12px; }
.imp-actions { display: flex; align-items: center; gap: 12px; margin: 12px 0 6px; }
.imp-hint { font-size: 11.5px; color: var(--text-3); }
.imp-upload :deep(.el-upload-dragger) { padding: 18px 10px; }
.up-main { font-size: 13px; color: var(--text); }
.up-sub { margin-top: 4px; font-size: 12px; color: var(--text-3); }
.imp-preview { display: flex; align-items: center; gap: 10px; margin-top: 10px; font-size: 12.5px; color: var(--text-2); }
.imp-result { margin-top: 12px; }
.err-table { margin-top: 10px; }
.layout { display: flex; gap: 12px; align-items: flex-start; }
.bank-pane { width: 240px; flex: 0 0 auto; }
.bank-search { margin-bottom: 8px; }
.bank-list { display: flex; flex-direction: column; gap: 6px; }
.bank-item {
  padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px;
  cursor: pointer; transition: all .15s;
}
.bank-item:hover { border-color: #c9d6ff; background: #f8faff; }
.bank-item.active { border-color: var(--brand); background: var(--brand-soft); }
.bank-name { font-size: 13px; font-weight: 600; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
.bank-edit {
  margin-left: auto; font-size: 11.5px; font-weight: 400; color: var(--brand);
  opacity: 0; transition: opacity .15s;
}
.bank-item:hover .bank-edit { opacity: .85; }
.bank-meta { display: flex; gap: 8px; font-size: 12px; color: var(--text-3); align-items: center; flex-wrap: wrap; }
.ol-tag {
  max-width: 110px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: var(--brand); background: var(--brand-soft); border-radius: 4px; padding: 0 5px; font-size: 11px;
}
.ol-filter {
  display: flex; align-items: center; gap: 8px; margin-top: 8px;
  font-size: 12px; color: var(--brand); background: var(--brand-soft);
  border-radius: 6px; padding: 5px 9px;
}
.ol-filter a { cursor: pointer; color: var(--text-3); margin-left: auto; }
.ol-filter a:hover { color: var(--brand); }
.rec { color: #d97706; }

.q-pane { flex: 1; min-width: 0; }
.q-head { display: flex; align-items: center; margin-bottom: 12px; gap: 10px; }
.q-filters { margin-left: auto; display: flex; gap: 8px; }

.opt-editor { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.opt-row { display: flex; align-items: center; gap: 8px; }
.opt-key {
  width: 22px; height: 22px; flex: 0 0 auto;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 50%; background: var(--brand-soft); color: var(--brand);
  font-size: 12px; font-weight: 600;
}

/* ---------- 题冒题子题编辑 ---------- */
.sub-editor { width: 100%; display: flex; flex-direction: column; gap: 10px; }
.sub-card {
  border: 1px solid var(--border); border-radius: 10px;
  padding: 12px 14px; background: #fbfcff;
  display: flex; flex-direction: column; gap: 8px;
}
.sub-top { display: flex; align-items: center; gap: 8px; }
.sub-idx {
  width: 20px; height: 20px; flex: 0 0 auto; border-radius: 6px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--brand-soft); color: var(--brand); font-size: 12px; font-weight: 700;
}
.sub-del { margin-left: auto; }
.sub-opts { padding: 4px 0 0 26px; }
.sub-ans { display: flex; align-items: center; gap: 8px; padding-left: 26px; }
.sub-ans-label { font-size: 12px; color: var(--text-3); flex: 0 0 auto; }
.total-score { font-weight: 700; color: var(--brand); font-size: 15px; }

.expand { padding: 6px 10px 8px 34px; display: flex; flex-direction: column; gap: 6px; }
.expand-row { display: flex; align-items: center; gap: 10px; font-size: 12.5px; }
.expand-no {
  width: 18px; height: 18px; flex: 0 0 auto; border-radius: 4px;
  display: inline-flex; align-items: center; justify-content: center;
  background: #eef2fb; color: var(--text-2); font-size: 11.5px;
}
.expand-stem { flex: 1; min-width: 0; color: var(--text); }
.expand-ans { color: var(--text-3); max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.expand-score { color: var(--text-3); flex: 0 0 auto; }
.expand-muted { font-size: 12px; color: var(--text-3); padding: 4px 10px 4px 34px; }
</style>
