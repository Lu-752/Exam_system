<template>
  <div class="page-head">
    <div class="page-title">学员管理</div>
    <div class="head-actions">
      <el-button type="primary" @click="openCreate()">添加学员</el-button>
      <el-button @click="openImport">批量导入</el-button>
      <el-button @click="exportCsv">导出</el-button>
    </div>
  </div>

  <div class="card">
    <el-form :inline="true" class="filter" @submit.prevent>
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="姓名 / 学号 / 手机号" clearable style="width: 220px"
                  @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="所属组织">
        <el-tree-select v-model="query.orgId" :data="orgTree" node-key="id" check-strictly clearable
                        :render-after-expand="false" :props="{ label: 'name', children: 'children' }"
                        placeholder="全部（含下级）" style="width: 200px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>

  <div class="card">
    <el-table v-loading="loading" :data="rows" border stripe size="default" row-key="id"
              @selection-change="onSelectionChange">
      <el-table-column type="selection" width="44" />
      <el-table-column prop="no" label="学号" width="120" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="gender" label="性别" width="70" align="center" />
      <el-table-column label="所属组织" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span :title="orgPath(row)">{{ row.orgName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="level" label="职业等级" width="100" align="center" />
      <el-table-column prop="examCount" label="累计考试" width="100" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light">
            {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="130" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="empty">没有符合条件的学员</div>
      </template>
    </el-table>

    <div class="pager">
      <span class="pager-info">已选 {{ selected.length }} 项 · 共 {{ total }} 条</span>
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size"
                     :total="total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next, total"
                     background @current-change="load" @size-change="onSizeChange" />
    </div>
  </div>

  <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑学员' : '添加学员'" width="520px"
             :close-on-click-modal="false" @closed="resetForm">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
      <el-form-item label="学号" prop="no">
        <el-input v-model="form.no" placeholder="如 S20261027" />
      </el-form-item>
      <el-form-item label="姓名" prop="name">
        <el-input v-model="form.name" placeholder="请输入姓名" />
      </el-form-item>
      <el-form-item label="性别">
        <el-radio-group v-model="form.gender">
          <el-radio value="男">男</el-radio>
          <el-radio value="女">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="所属组织" prop="orgId">
        <el-tree-select v-model="form.orgId" :data="orgTree" node-key="id" check-strictly
                        :render-after-expand="false" :props="{ label: 'name', children: 'children' }"
                        placeholder="请选择组织（在组织机构中维护）" style="width: 100%" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" />
      </el-form-item>
      <el-form-item label="职业等级">
        <el-select v-model="form.level" style="width: 100%">
          <el-option v-for="l in ['初级', '中级', '高级']" :key="l" :label="l" :value="l" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" active-value="ACTIVE" inactive-value="DISABLED"
                   active-text="启用" inactive-text="停用" inline-prompt />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="imp.visible" title="批量导入学员" width="640px" :close-on-click-modal="false">
    <div class="imp-tip">
      按模板列顺序填写：学号 / 姓名 / 性别 / 所属组织 / 手机号 / 职业等级 / 状态。
      所属组织需与「组织机构」中的名称或编码一致；学号重复或必填为空的行会被跳过并列出原因。
    </div>

    <div class="imp-actions">
      <el-button size="small" @click="downloadTemplate">下载 CSV 模板</el-button>
      <el-checkbox v-model="imp.autoCreateOrg">组织不存在时自动创建</el-checkbox>
      <span class="imp-hint">CSV 可用 Excel 另存为「CSV UTF-8」</span>
    </div>

    <el-upload drag :auto-upload="false" :show-file-list="false" accept=".csv,text/csv"
               :on-change="onFile" class="imp-upload">
      <div class="up-main">把 CSV 文件拖到这里，或点击选择</div>
      <div class="up-sub">{{ imp.fileName || '支持 UTF-8 编码的 .csv 文件' }}</div>
    </el-upload>

    <div v-if="imp.text" class="imp-preview">
      <span>已读取 {{ imp.rowCount }} 行有效数据</span>
      <el-button link type="primary" size="small" @click="clearImport">重新选择</el-button>
      <el-button link type="primary" size="small" @click="imp.result = null">清空结果</el-button>
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  createStudent, deleteStudent, importStudents, listStudents, studentImportTemplate, updateStudent,
  type ImportResult, type StudentPayload
} from '@/api/students'
import { flattenOrgs, listOrgs, type OrgNode } from '@/api/org'
import { countRows, downloadCsv, readTextFile } from '@/utils/csv'
import type { Student, StudentStatus } from '@/api/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const rows = ref<Student[]>([])
const total = ref(0)
const selected = ref<Student[]>([])
const orgTree = ref<OrgNode[]>([])
const orgFlat = ref<OrgNode[]>([])

const query = reactive<{ keyword: string; orgId?: number; status: StudentStatus | ''; page: number; size: number }>({
  keyword: '', orgId: undefined, status: '', page: 1, size: 10
})

const dialog = reactive<{ visible: boolean; id: number | null }>({ visible: false, id: null })
const formRef = ref<FormInstance>()
const form = reactive<StudentPayload>({
  no: '', name: '', gender: '男', orgId: undefined, orgName: '', phone: '', level: '初级', status: 'ACTIVE'
})

const rules: FormRules = {
  no: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

async function load() {
  loading.value = true
  try {
    const res = await listStudents(query)
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}
function reset() {
  query.keyword = ''
  query.orgId = undefined
  query.status = ''
  query.page = 1
  load()
}
function onSizeChange() {
  query.page = 1
  load()
}
function onSelectionChange(v: Student[]) {
  selected.value = v
}

function openCreate(orgId?: number) {
  dialog.id = null
  form.orgId = orgId
  dialog.visible = true
}

function openEdit(row: Student) {
  dialog.id = row.id
  Object.assign(form, {
    no: row.no, name: row.name, gender: row.gender, orgId: row.orgId,
    orgName: row.orgName, phone: row.phone, level: row.level, status: row.status
  })
  dialog.visible = true
}

function resetForm() {
  formRef.value?.clearValidate()
  Object.assign(form, {
    no: '', name: '', gender: '男', orgId: undefined, orgName: '', phone: '', level: '初级', status: 'ACTIVE'
  })
}

async function save() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return

  saving.value = true
  try {
    if (dialog.id) {
      await updateStudent(dialog.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await createStudent({ ...form })
      ElMessage.success('已添加')
      query.page = 1
    }
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: Student) {
  try {
    await ElMessageBox.confirm(`确定删除学员「${row.name}」吗？该操作不可恢复。`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteStudent(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && query.page > 1) query.page -= 1
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

/** 导出当前筛选结果（前端生成 UTF-8 BOM CSV，Excel 双击不乱码） */
function exportCsv() {
  const header = ['学号', '姓名', '性别', '所属组织', '手机号', '职业等级', '累计考试', '状态', '创建时间']
  const lines = rows.value.map((r) => [
    r.no, r.name, r.gender, r.orgName, r.phone, r.level, String(r.examCount),
    r.status === 'ACTIVE' ? '启用' : '停用', r.createdAt
  ].map((v) => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))
  const csv = '\uFEFF' + [header.join(','), ...lines].join('\r\n')
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = `学员名单-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出当前页 ${rows.value.length} 条`)
}

// ---------------- 批量导入 ----------------

const imp = reactive<{
  visible: boolean
  loading: boolean
  text: string
  fileName: string
  rowCount: number
  autoCreateOrg: boolean
  result: ImportResult | null
}>({ visible: false, loading: false, text: '', fileName: '', rowCount: 0, autoCreateOrg: false, result: null })

function openImport() {
  Object.assign(imp, { visible: true, loading: false, text: '', fileName: '', rowCount: 0, result: null })
}

function clearImport() {
  imp.text = ''
  imp.fileName = ''
  imp.rowCount = 0
  imp.result = null
}

async function downloadTemplate() {
  try {
    const tpl = await studentImportTemplate()
    downloadCsv('学员导入模板.csv', tpl)
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
    if (!imp.rowCount) ElMessage.warning('文件里没有数据行，请检查是否只填了表头')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function doImport() {
  imp.loading = true
  try {
    const res = await importStudents(imp.text, imp.autoCreateOrg)
    imp.result = res
    if (res.success > 0) {
      ElMessage.success(`已导入 ${res.success} 名学员`)
      query.page = 1
      await load()
    } else {
      ElMessage.warning('没有任何数据导入成功，请按失败原因修正后重试')
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '导入失败')
  } finally {
    imp.loading = false
  }
}

/** 组织全路径，便于确认同名下级 */
function orgPath(row: Student) {
  if (!row.orgId) return '未归属组织'
  const names: string[] = []
  let hit = orgFlat.value.find((o) => o.id === row.orgId)
  let guard = 0
  while (hit && guard++ < 32) {
    names.unshift(hit.name)
    const parentId = hit.parentId
    hit = parentId ? orgFlat.value.find((o) => o.id === parentId) : undefined
  }
  return names.join(' / ') || row.orgName
}

async function loadOrgs() {
  try {
    orgTree.value = await listOrgs()
    orgFlat.value = flattenOrgs(orgTree.value)
  } catch {
    orgTree.value = []
  }
}

onMounted(async () => {
  await loadOrgs()
  const q = route.query
  if (typeof q.keyword === 'string' && q.keyword) query.keyword = q.keyword
  if (q.orgId) query.orgId = Number(q.orgId)
  await load()
  // 从组织机构页「添加成员」跳转过来：直接打开新增弹窗并预选组织
  if (q.new === '1') {
    openCreate(query.orgId)
    router.replace({ path: '/students', query: query.orgId ? { orgId: String(query.orgId) } : {} })
  }
  if (q.import === '1') {
    openImport()
    router.replace({ path: '/students' })
  }
})
</script>

<style scoped>
.filter { margin-bottom: -18px; }
.filter :deep(.el-form-item) { margin-bottom: 18px; }
.pager { display: flex; align-items: center; margin-top: 14px; }
.pager-info { color: var(--text-3); font-size: 12px; }
.pager :deep(.el-pagination) { margin-left: auto; }
.empty { padding: 26px 0; color: var(--text-3); }

.imp-tip {
  font-size: 12.5px; line-height: 1.8; color: var(--text-2);
  background: #fafbfe; border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px;
}
.imp-actions { display: flex; align-items: center; gap: 12px; margin: 12px 0 6px; }
.imp-hint { font-size: 11.5px; color: var(--text-3); }
.imp-upload :deep(.el-upload-dragger) { padding: 18px 10px; }
.up-main { font-size: 13px; color: var(--text); }
.up-sub { margin-top: 4px; font-size: 12px; color: var(--text-3); }
.imp-preview {
  display: flex; align-items: center; gap: 10px;
  margin-top: 10px; font-size: 12.5px; color: var(--text-2);
}
.imp-result { margin-top: 12px; }
.err-table { margin-top: 10px; }
</style>
