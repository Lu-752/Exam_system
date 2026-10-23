<template>
  <div class="page-head">
    <div class="page-title">字典管理</div>
    <div class="head-actions">
      <el-input v-model="keyword" placeholder="搜索标签 / 编码" clearable style="width: 180px"
                @keyup.enter="load" @clear="load">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-button type="primary" @click="openForm()">新增字典项</el-button>
    </div>
  </div>

  <div class="dict-layout">
    <aside class="card side">
      <div class="side-head">字典类型</div>
      <button v-for="t in types" :key="t" class="type-item" :class="{ cur: currentType === t }"
              @click="pick(t)">
        <span>{{ t }}</span>
        <span class="cnt">{{ countOf(t) }}</span>
      </button>
      <el-empty v-if="!types.length" description="暂无字典类型" :image-size="60" />
      <div class="side-tip">新增字典项时可输入新的类型名来创建分组</div>
    </aside>

    <div class="card main">
      <div class="main-head">
        <span class="cur-type">{{ currentType || '全部' }}</span>
        <span class="count">{{ filteredRows.length }} 项</span>
      </div>
      <el-table v-loading="loading" :data="filteredRows" border stripe empty-text="该类型下暂无字典项">
        <el-table-column prop="code" label="编码" width="140" />
        <el-table-column prop="label" label="标签" min-width="140" />
        <el-table-column prop="value" label="取值" width="120">
          <template #default="{ row }">{{ row.value || '—' }}</template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

  <el-dialog v-model="dialog" :title="form.id ? '编辑字典项' : '新增字典项'" width="480px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="类型" required>
        <el-input v-model="form.dictType" maxlength="50" :disabled="!!form.id"
                  placeholder="如 grade / difficulty（小写）" />
      </el-form-item>
      <el-form-item label="编码" required>
        <el-input v-model="form.code" maxlength="50" :disabled="!!form.id" placeholder="唯一编码，如 junior" />
      </el-form-item>
      <el-form-item label="标签" required>
        <el-input v-model="form.label" maxlength="100" placeholder="显示名称，如 初级" />
      </el-form-item>
      <el-form-item label="取值">
        <el-input v-model="form.value" maxlength="200" placeholder="可选" />
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
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  createDict, deleteDict, listDictItems, updateDict, type DictItem, type DictPayload
} from '@/api/misc'

const items = ref<DictItem[]>([])
const types = ref<string[]>([])
const currentType = ref('')
const keyword = ref('')
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const form = reactive<DictPayload & { id?: number }>({
  dictType: '', code: '', label: '', status: 'ACTIVE', sortNo: 0
})

const filteredRows = computed(() =>
  currentType.value ? items.value.filter((i) => i.dictType === currentType.value) : items.value
)

function countOf(t: string) {
  return items.value.filter((i) => i.dictType === t).length
}

function pick(t: string) {
  currentType.value = currentType.value === t ? '' : t
}

async function load() {
  loading.value = true
  try {
    items.value = await listDictItems({ keyword: keyword.value.trim() || undefined })
    types.value = [...new Set(items.value.map((i) => i.dictType))].sort()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openForm(row?: DictItem) {
  Object.assign(form, {
    id: row?.id, dictType: row?.dictType ?? currentType.value ?? '',
    code: row?.code ?? '', label: row?.label ?? '', value: row?.value ?? '',
    sortNo: row?.sortNo ?? 0, status: row?.status ?? 'ACTIVE', remark: row?.remark ?? ''
  })
  dialog.value = true
}

async function save() {
  if (!form.dictType.trim() || !form.code.trim() || !form.label.trim()) {
    ElMessage.warning('类型 / 编码 / 标签 为必填项')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateDict(form.id, form)
      ElMessage.success('已保存')
    } else {
      await createDict(form)
      ElMessage.success('已新增')
    }
    dialog.value = false
    currentType.value = form.dictType.trim().toLowerCase()
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: DictItem) {
  try {
    await ElMessageBox.confirm(`确认删除字典项「${row.label}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteDict(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.dict-layout { display: flex; gap: 14px; align-items: flex-start; }
.side { width: 230px; flex: 0 0 auto; padding: 14px; }
.side-head { font-size: 13px; font-weight: 600; margin-bottom: 10px; }
.type-item {
  width: 100%; display: flex; align-items: center; justify-content: space-between;
  border: 0; background: transparent; padding: 8px 10px; border-radius: 8px;
  cursor: pointer; font-size: 13px; color: var(--text-2); font-family: inherit;
}
.type-item:hover { background: var(--bg); }
.type-item.cur { background: var(--brand-soft); color: var(--brand); font-weight: 600; }
.cnt { font-size: 11.5px; color: var(--text-3); }
.side-tip { margin-top: 12px; font-size: 11.5px; color: var(--text-3); line-height: 1.6; }
.main { flex: 1; min-width: 0; padding: 14px; }
.main-head { display: flex; align-items: baseline; margin-bottom: 10px; }
.cur-type { font-size: 13.5px; font-weight: 600; }
.count { margin-left: auto; font-size: 12px; color: var(--text-3); }
</style>
