<template>
  <div class="page-head">
    <div class="page-title">系统参数</div>
    <div class="head-actions">
      <el-input v-model="keyword" placeholder="搜索参数键 / 备注" clearable style="width: 200px"
                @keyup.enter="load" @clear="load">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="openForm()">新增参数</el-button>
    </div>
  </div>

  <div class="card">
    <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无系统参数">
      <el-table-column prop="paramKey" label="参数键" min-width="200">
        <template #default="{ row }"><code class="key">{{ row.paramKey }}</code></template>
      </el-table-column>
      <el-table-column prop="paramValue" label="参数值" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.paramValue || '—' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="说明" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">{{ row.updatedAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialog" :title="form.id ? '编辑参数' : '新增参数'" width="480px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="参数键" required>
        <el-input v-model="form.paramKey" maxlength="100" placeholder="如 site.name（点分小写）" />
      </el-form-item>
      <el-form-item label="参数值">
        <el-input v-model="form.paramValue" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>
      <el-form-item label="说明">
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import { createConfig, deleteConfig, listConfigs, updateConfig, type SysConfig } from '@/api/misc'

const rows = ref<SysConfig[]>([])
const loading = ref(false)
const keyword = ref('')
const dialog = ref(false)
const saving = ref(false)
const form = reactive<{ id?: number; paramKey: string; paramValue: string; remark: string }>({
  paramKey: '', paramValue: '', remark: ''
})

async function load() {
  loading.value = true
  try {
    rows.value = await listConfigs(keyword.value.trim() || undefined)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openForm(row?: SysConfig) {
  Object.assign(form, {
    id: row?.id, paramKey: row?.paramKey ?? '',
    paramValue: row?.paramValue ?? '', remark: row?.remark ?? ''
  })
  dialog.value = true
}

async function save() {
  if (!form.paramKey.trim()) {
    ElMessage.warning('请填写参数键')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateConfig(form.id, form)
      ElMessage.success('已保存')
    } else {
      await createConfig(form)
      ElMessage.success('已新增')
    }
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: SysConfig) {
  try {
    await ElMessageBox.confirm(`确认删除参数「${row.paramKey}」？删除后相关功能将回退到默认值。`,
      '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteConfig(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.key {
  font-family: Consolas, monospace;
  font-size: 12.5px;
  background: var(--bg);
  padding: 2px 8px;
  border-radius: 6px;
}
</style>
