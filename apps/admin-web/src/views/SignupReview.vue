<template>
  <div class="page-head">
    <div class="page-title">考试报名审核</div>
    <div class="head-actions">
      <el-select v-model="planId" placeholder="全部考试" clearable style="width: 240px" @change="load">
        <el-option v-for="p in plans" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px" @change="load">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="未通过" value="REJECTED" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>
  </div>

  <div class="card">
    <div class="tools">
      <el-input v-model="keyword" placeholder="搜索考生姓名" clearable size="small" style="width: 200px">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <span class="tools-count">待审核 {{ pendingCount }} 条 · 列表共 {{ filteredRows.length }} 条</span>
    </div>

    <el-table v-loading="loading" :data="filteredRows" border stripe empty-text="暂无报名记录">
      <el-table-column prop="studentName" label="考生" width="120" />
      <el-table-column prop="planName" label="考试" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" size="small" effect="light">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="报名时间" width="170" />
      <el-table-column prop="remark" label="审核意见" min-width="160">
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" align="center">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="primary" size="small" @click="review(row, 'APPROVED')">通过</el-button>
            <el-button link type="danger" size="small" @click="review(row, 'REJECTED')">拒绝</el-button>
          </template>
          <span v-else class="muted">{{ row.reviewedAt }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="dialog" :title="dialogTitle" width="440px">
    <p class="dlg-text">{{ pending?.studentName }} · {{ pending?.planName }}</p>
    <el-input v-model="remark" type="textarea" :rows="3" :placeholder="action === 'REJECTED' ? '请填写拒绝原因（考生可见）' : '备注（选填）'" />
    <template #footer>
      <el-button @click="dialog = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import Icon from '@/components/Icon.vue'
import { listPlans } from '@/api/exam'
import type { Plan } from '@/api/exam'
import { listAdminSignups, reviewSignup, type SignupRow } from '@/api/student'

const loading = ref(false)
const saving = ref(false)
const rows = ref<SignupRow[]>([])
const plans = ref<Plan[]>([])
const planId = ref<number>()
const status = ref<string>()
const keyword = ref('')

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter((r) =>
    (r.studentName || '').toLowerCase().includes(kw) || (r.planName || '').toLowerCase().includes(kw))
})
const pendingCount = computed(() => filteredRows.value.filter((r) => r.status === 'PENDING').length)

const dialog = ref(false)
const pending = ref<SignupRow | null>(null)
const action = ref<'APPROVED' | 'REJECTED'>('APPROVED')
const remark = ref('')

const dialogTitle = computed(() => (action.value === 'APPROVED' ? '通过报名' : '拒绝报名'))
const statusText = (s: string) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '未通过' }[s] ?? s)
const tagType = (s: string) =>
  ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] as 'warning' | 'success' | 'danger')

async function load() {
  loading.value = true
  try {
    rows.value = await listAdminSignups({ planId: planId.value, status: status.value || undefined })
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function review(row: SignupRow, act: 'APPROVED' | 'REJECTED') {
  pending.value = row
  action.value = act
  remark.value = ''
  dialog.value = true
}

async function confirm() {
  if (!pending.value) return
  if (action.value === 'REJECTED' && !remark.value.trim()) {
    return ElMessage.warning('拒绝时请填写原因')
  }
  saving.value = true
  try {
    await reviewSignup(pending.value.id, action.value, remark.value)
    ElMessage.success(action.value === 'APPROVED' ? '已通过' : '已拒绝')
    dialog.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    plans.value = await listPlans()
  } catch {
    plans.value = []
  }
})
</script>

<style scoped>
.muted { color: var(--text-3); font-size: 12px; }
.dlg-text { margin: 0 0 12px; font-size: 13px; color: var(--text-2); }
</style>
