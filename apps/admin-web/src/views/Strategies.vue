<template>
  <div class="page-head">
    <div class="page-title">组卷策略</div>
    <div class="head-actions">
      <el-button @click="load">刷新</el-button>
      <el-button type="primary" @click="openCreate">新建策略</el-button>
    </div>
  </div>

  <div class="card tools">
    <el-input v-model="keyword" placeholder="搜索策略名称 / 说明" clearable style="width: 240px">
      <template #prefix><Icon name="search" /></template>
    </el-input>
    <el-select v-model="bankFilter" placeholder="全部题库" clearable style="width: 190px">
      <el-option v-for="b in banks" :key="b.id" :label="b.name" :value="b.id" />
    </el-select>
    <span class="tools-count">共 {{ filteredRows.length }} 条策略</span>
  </div>

  <div v-loading="loading" class="grid">
    <div v-for="s in filteredRows" :key="s.id" class="scard">
      <div class="s-head">
        <span class="s-name">{{ s.name }}</span>
        <el-tag size="small" effect="plain">{{ s.bankName }}</el-tag>
      </div>
      <div class="s-meta">
        <span>{{ s.questionCount }} 题</span>
        <span>满分 {{ s.totalScore }}</span>
        <span>及格 {{ s.passScore }}</span>
        <span>{{ Math.round(s.durationSec / 60) }} 分钟</span>
      </div>

      <div class="rules">
        <div v-for="r in s.rules" :key="r.type" class="rule">
          <span class="r-type">{{ TYPE_LABEL[r.type] }}</span>
          <span class="r-calc">{{ r.count }} 题 × {{ r.score }} 分</span>
        </div>
      </div>

      <div v-if="s.remark" class="s-remark">{{ s.remark }}</div>

      <div class="s-foot">
        <el-button type="primary" size="small" :loading="genId === s.id" @click="generate(s)">生成试卷</el-button>
        <el-button size="small" @click="openEdit(s)">编辑</el-button>
        <el-button size="small" type="danger" plain @click="remove(s)">删除</el-button>
      </div>
    </div>
    <el-empty v-if="!loading && !rows.length" description="还没有组卷策略，先新建一条" />
    <el-empty v-else-if="!loading && !filteredRows.length" description="没有匹配的策略，换个关键字试试" />
  </div>

  <el-dialog v-model="dialog" :title="editing ? '编辑策略' : '新建策略'" width="680px" :close-on-click-modal="false">
    <el-form label-width="96px">
      <el-form-item label="策略名称" required>
        <el-input v-model="form.name" placeholder="如 消防安全标准卷（100 分）" />
      </el-form-item>
      <el-form-item label="题库范围">
        <el-select v-model="form.bankId" placeholder="全部题库" clearable style="width: 100%">
          <el-option v-for="b in banks" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="题型配比" required>
        <div class="rule-editor">
          <div v-for="(r, i) in form.rules" :key="i" class="rule-row">
            <el-select v-model="r.type" style="width: 120px">
              <el-option v-for="(label, t) in TYPE_LABEL" :key="t" :label="label" :value="t" />
            </el-select>
            <el-input-number v-model="r.count" :min="1" :max="100" controls-position="right" style="width: 110px" />
            <span class="x">题 ×</span>
            <el-input-number v-model="r.score" :min="0.5" :max="100" :step="0.5" controls-position="right"
                             style="width: 110px" />
            <span class="x">分</span>
            <el-button link type="danger" :disabled="form.rules.length <= 1" @click="form.rules.splice(i, 1)">移除</el-button>
          </div>
          <el-button link type="primary" @click="addRule">+ 添加题型</el-button>
        </div>
      </el-form-item>

      <el-form-item label="合计">
        <span class="sum">{{ sumCount }} 题 · 总分 {{ sumScore }} 分</span>
      </el-form-item>

      <el-form-item label="及格分">
        <el-input-number v-model="form.passScore" :min="1" :max="1000" controls-position="right" />
        <span class="tip">建议为总分的 60%</span>
      </el-form-item>
      <el-form-item label="考试时长">
        <el-input-number v-model="form.minutes" :min="5" :max="600" controls-position="right" />
        <span class="tip">分钟</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" placeholder="选填，便于团队识别" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  createStrategy, deleteStrategy, generateFromStrategy, listBanks, listStrategies,
  TYPE_LABEL, updateStrategy,
  type Bank, type QuestionType, type Strategy, type StrategyRule
} from '@/api/exam'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const genId = ref<number | null>(null)
const rows = ref<Strategy[]>([])
const banks = ref<Bank[]>([])
const keyword = ref('')
const bankFilter = ref<number>()

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((s) => {
    if (bankFilter.value && s.bankId !== bankFilter.value) return false
    if (!kw) return true
    return s.name.toLowerCase().includes(kw) || (s.remark || '').toLowerCase().includes(kw) ||
      (s.bankName || '').toLowerCase().includes(kw)
  })
})

const dialog = ref(false)
const editing = ref<Strategy | null>(null)
const form = reactive({
  name: '',
  bankId: undefined as number | undefined,
  passScore: 60,
  minutes: 30,
  remark: '',
  rules: [] as StrategyRule[]
})

const sumCount = computed(() => form.rules.reduce((s, r) => s + (r.count || 0), 0))
const sumScore = computed(() => form.rules.reduce((s, r) => s + (r.count || 0) * (r.score || 0), 0))

async function load() {
  loading.value = true
  try {
    rows.value = await listStrategies()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function addRule() {
  form.rules.push({ type: 'SC' as QuestionType, count: 5, score: 5 })
}

function openCreate() {
  editing.value = null
  form.name = ''
  form.bankId = banks.value[0]?.id
  form.passScore = 60
  form.minutes = 30
  form.remark = ''
  form.rules = [
    { type: 'SC', count: 5, score: 5 },
    { type: 'TF', count: 5, score: 5 }
  ]
  dialog.value = true
}

function openEdit(s: Strategy) {
  editing.value = s
  form.name = s.name
  form.bankId = s.bankId
  form.passScore = Number(s.passScore)
  form.minutes = Math.round(s.durationSec / 60)
  form.remark = s.remark ?? ''
  form.rules = s.rules.map((r) => ({ ...r, score: Number(r.score) }))
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写策略名称')
  if (!form.rules.length) return ElMessage.warning('请至少配置一条题型规则')

  saving.value = true
  const body = {
    name: form.name,
    bankId: form.bankId,
    rules: form.rules,
    passScore: form.passScore,
    durationSec: form.minutes * 60,
    remark: form.remark
  }
  try {
    if (editing.value) await updateStrategy(editing.value.id, body)
    else await createStrategy(body)
    ElMessage.success('策略已保存')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function generate(s: Strategy) {
  let name = ''
  try {
    const r = await ElMessageBox.prompt('生成的试卷名称（留空自动命名）', `按「${s.name}」组卷`, {
      inputPlaceholder: `${s.name} · 自动命名`,
      confirmButtonText: '开始组卷'
    })
    name = r.value ?? ''
  } catch {
    return
  }

  genId.value = s.id
  try {
    const res = await generateFromStrategy(s.id, name || undefined)
    if (res.gaps.length) {
      ElMessage.warning(`已生成 ${res.questionCount} 题，但存在缺口：${res.gaps.join('；')}`)
    } else {
      ElMessage.success(`已生成试卷「${res.paperName}」共 ${res.questionCount} 题 / ${res.totalScore} 分`)
    }
    router.push('/papers')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    genId.value = null
  }
}

async function remove(s: Strategy) {
  try {
    await ElMessageBox.confirm(`删除策略「${s.name}」？已生成的试卷不受影响。`, '删除策略', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteStrategy(s.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await load()
  try {
    banks.value = await listBanks()
  } catch {
    banks.value = []
  }
})
</script>

<style scoped>
.tools { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tools-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 12px; }
.scard {
  background: #fff; border: 1px solid var(--border); border-radius: var(--radius);
  padding: 16px 18px; box-shadow: var(--shadow-sm);
  display: flex; flex-direction: column; gap: 10px;
}
.s-head { display: flex; align-items: center; gap: 8px; }
.s-name { font-size: 14px; font-weight: 600; flex: 1; }
.s-meta { display: flex; gap: 14px; font-size: 12px; color: var(--text-3); }
.rules { display: flex; flex-wrap: wrap; gap: 6px; }
.rule {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 4px 9px; border-radius: 6px; background: #f5f8ff; font-size: 12px;
}
.r-type { color: var(--brand); font-weight: 600; }
.r-calc { color: var(--text-2); }
.s-remark { font-size: 12px; color: var(--text-3); line-height: 1.6; }
.s-foot { display: flex; gap: 8px; margin-top: auto; padding-top: 10px; border-top: 1px dashed var(--border); }

.rule-editor { display: flex; flex-direction: column; gap: 8px; }
.rule-row { display: flex; align-items: center; gap: 8px; }
.x { font-size: 12px; color: var(--text-3); }
.sum { font-size: 13px; font-weight: 600; color: var(--brand); }
.tip { margin-left: 10px; font-size: 12px; color: var(--text-3); }
</style>
