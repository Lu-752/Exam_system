<template>
  <div class="page-head">
    <div class="page-title">用户管理</div>
    <div class="head-actions">
      <el-input v-model="keyword" placeholder="搜索用户名 / 姓名" clearable style="width: 220px"
                @keyup.enter="load" @clear="load" />
      <el-button @click="load">查询</el-button>
      <el-button type="primary" @click="openCreate">新建用户</el-button>
    </div>
  </div>

  <div class="card">
    <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无用户">
      <el-table-column prop="username" label="用户名" width="130" />
      <el-table-column prop="name" label="姓名" width="110" />
      <el-table-column prop="phone" label="手机号" width="140">
        <template #default="{ row }">{{ row.phone || '—' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="r in row.roleNames" :key="r" size="small" effect="light" class="role-tag">{{ r }}</el-tag>
          <span v-if="!row.roleNames.length" class="muted">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light">
            {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最近登录" width="170">
        <template #default="{ row }">{{ row.lastLoginAt || '从未登录' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openRoles(row)">角色</el-button>
          <el-button link type="warning" size="small" @click="resetPwd(row)">重置密码</el-button>
          <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" size="small"
                     :disabled="row.id === auth.user?.id" @click="toggle(row)">
            {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="createDialog" title="新建用户" width="520px" :close-on-click-modal="false">
    <el-form label-width="90px">
      <el-form-item label="用户名" required>
        <el-input v-model="form.username" placeholder="登录名，创建后不可改" />
      </el-form-item>
      <el-form-item label="姓名" required>
        <el-input v-model="form.name" placeholder="真实姓名" />
      </el-form-item>
      <el-form-item label="初始密码">
        <el-input v-model="form.password" placeholder="留空则默认 123456" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="form.phone" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
          <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">创建</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="roleDialog" :title="`分配角色 · ${current?.name ?? ''}`" width="420px">
    <el-select v-model="roleIds" multiple placeholder="选择角色" style="width: 100%">
      <el-option v-for="r in roles" :key="r.id" :label="`${r.name}（${r.code}）`" :value="r.id" />
    </el-select>
    <p class="tip">角色变更后，该用户需重新登录才会生效。</p>
    <template #footer>
      <el-button @click="roleDialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createUser, listRoles, listUsers, resetUserPassword, setUserRoles, setUserStatus,
  type RoleOption, type UserRow
} from '@/api/system'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const rows = ref<UserRow[]>([])
const roles = ref<RoleOption[]>([])

const createDialog = ref(false)
const form = reactive({ username: '', name: '', password: '', phone: '', roleIds: [] as number[] })

const roleDialog = ref(false)
const current = ref<UserRow | null>(null)
const roleIds = ref<number[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await listUsers(keyword.value || undefined)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.username = ''
  form.name = ''
  form.password = ''
  form.phone = ''
  form.roleIds = []
  createDialog.value = true
}

async function save() {
  if (!form.username.trim()) return ElMessage.warning('请输入用户名')
  if (!form.name.trim()) return ElMessage.warning('请输入姓名')
  saving.value = true
  try {
    await createUser({ ...form })
    ElMessage.success('用户已创建')
    createDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

function openRoles(row: UserRow) {
  current.value = row
  roleIds.value = [...row.roleIds]
  roleDialog.value = true
}

async function saveRoles() {
  if (!current.value) return
  saving.value = true
  try {
    await setUserRoles(current.value.id, roleIds.value)
    ElMessage.success('角色已更新')
    roleDialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function resetPwd(row: UserRow) {
  try {
    await ElMessageBox.confirm(`将「${row.name}」的密码重置为 123456？`, '重置密码', { type: 'warning' })
  } catch {
    return
  }
  try {
    await resetUserPassword(row.id)
    ElMessage.success('已重置为 123456')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function toggle(row: UserRow) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      next === 'DISABLED' ? `停用后「${row.name}」将立即被踢下线且无法登录，确认？` : `恢复「${row.name}」的登录权限？`,
      next === 'DISABLED' ? '停用账号' : '启用账号',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await setUserStatus(row.id, next)
    ElMessage.success(next === 'DISABLED' ? '已停用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await load()
  try {
    roles.value = await listRoles()
  } catch {
    roles.value = []
  }
})
</script>

<style scoped>
.role-tag { margin-right: 4px; }
.muted { color: var(--text-3); }
.tip { margin: 12px 0 0; font-size: 12px; color: var(--text-3); }
</style>
