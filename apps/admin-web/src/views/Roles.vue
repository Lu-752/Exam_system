<template>
  <div class="page-head">
    <div class="page-title">角色权限</div>
    <div class="head-actions">
      <el-button @click="loadRoles">刷新</el-button>
      <el-button @click="openCreate">新增角色</el-button>
      <el-button type="primary" :loading="saving" :disabled="!current" @click="save">保存权限</el-button>
    </div>
  </div>

  <div class="role-layout">
    <div class="card role-list">
      <div class="card-head">
        <span class="card-title">角色</span>
        <span class="card-date">{{ filteredRoles.length }} / {{ roles.length }}</span>
      </div>

      <el-input v-model="keyword" placeholder="搜索角色名称 / 编码" clearable size="small" class="rsearch">
        <template #prefix><Icon name="search" /></template>
      </el-input>

      <div v-loading="loading" class="rlist">
        <div v-for="r in filteredRoles" :key="r.id" class="ritem" :class="{ active: current?.id === r.id }"
             @click="select(r)">
          <div class="rname">
            <span class="rlabel">{{ r.name }}</span>
            <span class="rcode">{{ r.code }}</span>
            <span class="rops">
              <el-button link size="small" title="编辑" @click.stop="openEdit(r)">✎</el-button>
              <el-button v-if="r.code !== 'SUPER_ADMIN'" link size="small" type="danger" title="删除"
                         @click.stop="remove(r)">✕</el-button>
            </span>
          </div>
          <div class="rmeta">{{ r.userCount }} 个用户 · {{ r.menuCount }} 项权限</div>
          <div v-if="r.remark" class="rremark">{{ r.remark }}</div>
        </div>
        <el-empty v-if="!loading && !filteredRoles.length" description="没有匹配的角色" :image-size="64" />
      </div>
    </div>

    <div class="card perm-card">
      <div class="card-head">
        <span class="card-title">菜单权限{{ current ? ` · ${current.name}` : '' }}</span>
        <span v-if="current?.code === 'SUPER_ADMIN'" class="card-date">超级管理员默认拥有全部权限，不可收窄</span>
        <span v-else-if="current" class="card-date">勾选子项会自动补齐上级目录</span>
      </div>
      <el-tree
        ref="treeRef"
        v-loading="treeLoading"
        :data="tree"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
        class="tree"
      >
        <template #default="{ data }">
          <span class="tnode">
            <el-tag v-if="data.type === 'DIR'" size="small" type="info" effect="plain">目录</el-tag>
            <el-tag v-else-if="data.type === 'BUTTON'" size="small" type="warning" effect="plain">按钮</el-tag>
            <span class="tname">{{ data.name }}</span>
            <span v-if="data.perm" class="tperm">{{ data.perm }}</span>
            <span v-if="data.path" class="tpath">{{ data.path }}</span>
          </span>
        </template>
      </el-tree>
    </div>
  </div>

  <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑角色' : '新增角色'" width="500px"
             :close-on-click-modal="false" @closed="resetForm">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="form.name" placeholder="如 考务专员" maxlength="32" show-word-limit />
      </el-form-item>
      <el-form-item label="角色编码" prop="code">
        <el-input v-model="form.code" placeholder="如 EXAM_STAFF（大写字母 / 下划线）" maxlength="48"
                  :disabled="builtinEditing" @input="onCodeInput" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="角色职责（选填）" />
      </el-form-item>
      <el-form-item v-if="!dialog.id" label="复制权限">
        <el-select v-model="copyFrom" placeholder="不复制（权限留空，稍后勾选）" clearable style="width: 100%">
          <el-option v-for="r in roles" :key="r.id" :label="`${r.name}（${r.menuCount} 项）`" :value="r.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  createRole, deleteRole, getMenuTree, getRoleMenus, listRoles, saveRoleMenus, updateRole,
  type MenuTreeNode, type RoleOption
} from '@/api/system'

const loading = ref(false)
const treeLoading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const roles = ref<RoleOption[]>([])
const current = ref<RoleOption | null>(null)
const tree = ref<MenuTreeNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const keyword = ref('')

const filteredRoles = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return roles.value
  return roles.value.filter((r) =>
    r.name.toLowerCase().includes(kw) || r.code.toLowerCase().includes(kw) ||
    (r.remark || '').toLowerCase().includes(kw))
})

/** id -> 父 id，用于保存时补齐上级目录 */
const parentMap = new Map<number, number | undefined>()

function buildParentMap(nodes: MenuTreeNode[], parent?: number) {
  for (const n of nodes) {
    parentMap.set(n.id, parent)
    if (n.children?.length) buildParentMap(n.children, n.id)
  }
}

async function loadRoles(keepId?: number) {
  loading.value = true
  try {
    roles.value = await listRoles()
    const id = keepId ?? current.value?.id
    const keep = roles.value.find((r) => r.id === id)
    await select(keep ?? roles.value[0])
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function ensureTree() {
  if (tree.value.length) return
  tree.value = await getMenuTree()
  buildParentMap(tree.value)
}

async function select(role?: RoleOption) {
  if (!role) {
    current.value = null
    return
  }
  current.value = role
  treeLoading.value = true
  try {
    await ensureTree()
    const ids = await getRoleMenus(role.id)
    await nextTick()
    treeRef.value?.setCheckedKeys(ids, false)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    treeLoading.value = false
  }
}

async function save() {
  if (!current.value) return
  const checked = (treeRef.value?.getCheckedKeys(false) ?? []) as number[]
  const all = new Set<number>(checked)
  // 勾选子项时自动补齐上级目录，避免菜单树断裂
  for (const id of checked) {
    let p = parentMap.get(id)
    while (p) {
      all.add(p)
      p = parentMap.get(p)
    }
  }
  saving.value = true
  try {
    await saveRoleMenus(current.value.id, [...all])
    ElMessage.success('权限已保存，相关用户重新登录后生效')
    await loadRoles()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

// ---------------- 新增 / 编辑 ----------------

const dialog = reactive<{ visible: boolean; id: number | null }>({ visible: false, id: null })
const formRef = ref<FormInstance>()
const form = reactive({ name: '', code: '', remark: '' })
const copyFrom = ref<number>()
const builtinEditing = ref(false)

const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]{1,47}$/, message: '以大写字母开头，仅含大写字母、数字与下划线', trigger: 'blur' }
  ]
}

function onCodeInput(v: string) {
  form.code = v.toUpperCase().replace(/[^A-Z0-9_]/g, '')
}

function openCreate() {
  dialog.id = null
  builtinEditing.value = false
  Object.assign(form, { name: '', code: '', remark: '' })
  copyFrom.value = undefined
  dialog.visible = true
}

function openEdit(r: RoleOption) {
  dialog.id = r.id
  builtinEditing.value = r.code === 'SUPER_ADMIN'
  Object.assign(form, { name: r.name, code: r.code, remark: r.remark ?? '' })
  copyFrom.value = undefined
  dialog.visible = true
}

function resetForm() {
  formRef.value?.clearValidate()
}

async function submit() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  submitting.value = true
  try {
    if (dialog.id) {
      await updateRole(dialog.id, { ...form })
      ElMessage.success('已保存')
      dialog.visible = false
      await loadRoles(dialog.id)
    } else {
      const menuIds = copyFrom.value ? await getRoleMenus(copyFrom.value) : undefined
      const created = await createRole({ ...form, menuIds })
      ElMessage.success('角色已创建，可继续勾选权限')
      dialog.visible = false
      await loadRoles(created.id)
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function remove(r: RoleOption) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${r.name}」吗？该角色下若还有用户将无法删除。`, '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteRole(r.id)
    ElMessage.success('已删除')
    if (current.value?.id === r.id) current.value = null
    await loadRoles()
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(loadRoles)
</script>

<style scoped>
.role-layout { display: flex; gap: 12px; align-items: flex-start; }
.role-list { width: 320px; flex: 0 0 auto; }
.perm-card { flex: 1; min-width: 0; }

.rsearch { margin-bottom: 8px; }
.rlist { display: flex; flex-direction: column; gap: 8px; }
.ritem { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; cursor: pointer; }
.ritem:hover { border-color: #c9d6ff; background: #f8faff; }
.ritem.active { border-color: var(--brand); background: var(--brand-soft); }
.rname { font-size: 13px; font-weight: 600; color: var(--text); display: flex; align-items: baseline; gap: 8px; }
.rlabel { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rcode { font-size: 11px; color: var(--text-3); font-weight: 400; }
.rops { margin-left: auto; display: none; }
.ritem:hover .rops { display: inline-flex; }
.rops :deep(.el-button) { padding: 0 3px; }
.rmeta { margin-top: 4px; font-size: 12px; color: var(--text-3); }
.rremark { margin-top: 4px; font-size: 12px; color: var(--text-2); }

.tree { --el-tree-node-content-height: 30px; }
.tnode { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }
.tname { color: var(--text); }
.tperm, .tpath { color: var(--text-3); font-size: 11.5px; }
</style>
