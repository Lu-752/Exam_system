<template>
  <div class="page-head">
    <div class="page-title">大纲管理</div>
    <div class="head-actions">
      <el-input v-model="keyword" placeholder="搜索名称 / 分类" clearable style="width: 200px"
                @keyup.enter="load" @clear="load">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 120px" @change="load">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="openForm()">新增大纲</el-button>
    </div>
  </div>

  <div class="card">
    <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无大纲，点击右上角新增">
      <el-table-column prop="name" label="大纲名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="110" align="center">
        <template #default="{ row }">{{ row.category || '—' }}</template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="90" align="center">
        <template #default="{ row }">{{ row.version || '—' }}</template>
      </el-table-column>
      <el-table-column label="关联题库" width="110" align="center">
        <template #default="{ row }">
          <a v-if="row.bankCount" class="bank-link" title="查看关联题库的试题"
             @click="router.push(`/questions?outlineId=${row.id}`)">{{ row.bankCount }} 个 →</a>
          <span v-else class="muted">0</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light">
            {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" width="80" align="center" />
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">{{ row.updatedAt || row.createdAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
          <el-button link size="small" @click="toggle(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-drawer v-model="drawer" :title="form.id ? '编辑大纲' : '新增大纲'" size="520px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" maxlength="200" placeholder="如：消防安全培训大纲" />
      </el-form-item>
      <el-form-item label="分类">
        <el-input v-model="form.category" maxlength="50" placeholder="消防 / 电工 / 危化品…" />
      </el-form-item>
      <el-form-item label="版本">
        <el-input v-model="form.version" maxlength="20" placeholder="V1.0" style="width: 140px" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortNo" :min="0" :max="999" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="ACTIVE">启用</el-radio>
          <el-radio value="DISABLED">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="大纲内容">
        <el-input v-model="form.content" type="textarea" :rows="10"
                  placeholder="逐行输入章节要点，如：&#10;1. 消防基础知识&#10;2. 灭火器使用" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="drawer = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  createOutline, deleteOutline, listOutlines, updateOutline,
  type Outline, type OutlinePayload
} from '@/api/misc'

const router = useRouter()
const rows = ref<Outline[]>([])
const loading = ref(false)
const keyword = ref('')
const status = ref('')
const drawer = ref(false)
const saving = ref(false)
const form = reactive<OutlinePayload & { id?: number }>({ name: '', status: 'ACTIVE', sortNo: 0 })

async function load() {
  loading.value = true
  try {
    rows.value = await listOutlines({
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined
    })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openForm(row?: Outline) {
  Object.assign(form, {
    id: row?.id, name: row?.name ?? '', category: row?.category ?? '',
    version: row?.version ?? '', content: row?.content ?? '',
    status: row?.status ?? 'ACTIVE', sortNo: row?.sortNo ?? 0
  })
  drawer.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写大纲名称')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateOutline(form.id, form)
      ElMessage.success('已保存')
    } else {
      await createOutline(form)
      ElMessage.success('已新增')
    }
    drawer.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function toggle(row: Outline) {
  try {
    await updateOutline(row.id, { ...row, status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })
    ElMessage.success(row.status === 'ACTIVE' ? '已停用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function remove(row: Outline) {
  try {
    await ElMessageBox.confirm(`确认删除大纲「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteOutline(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.bank-link { color: var(--brand); cursor: pointer; }
.bank-link:hover { text-decoration: underline; }
.muted { color: var(--text-3); }
</style>
