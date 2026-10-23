<template>
  <div class="page-head">
    <div class="page-title">报名中心</div>
    <div class="head-actions">
      <el-input v-model="keyword" placeholder="搜索考试名称" clearable size="small" style="width: 200px">
        <template #prefix><Icon name="search" /></template>
      </el-input>
      <el-radio-group v-model="tab" size="small" @change="load">
        <el-radio-button value="available">可报名考试</el-radio-button>
        <el-radio-button value="mine">我的报名</el-radio-button>
      </el-radio-group>
    </div>
  </div>

  <div v-show="tab === 'available'" class="card">
    <el-table v-loading="loading" :data="filteredPlans" border stripe
              :empty-text="keyword ? '没有匹配的考试' : '暂无可报名的考试'">
      <el-table-column prop="planName" label="考试名称" min-width="220" show-overflow-tooltip />
      <el-table-column label="考试时间" width="290">
        <template #default="{ row }">
          <div class="tline">{{ row.startAt }}</div>
          <div class="tline muted">至 {{ row.endAt }}</div>
        </template>
      </el-table-column>
      <el-table-column label="时长/满分" width="120" align="center">
        <template #default="{ row }">{{ row.durationMin }} 分钟 / {{ row.totalScore }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.planState === 'ONGOING' ? 'success' : 'warning'" size="small" effect="light">
            {{ row.planState === 'ONGOING' ? '进行中' : '未开始' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="报名状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="signupTagType(row.signupStatus)" size="small" effect="light">
            {{ signupText(row.signupStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" align="center">
        <template #default="{ row }">
          <el-button v-if="row.signupStatus === 'NONE' || row.signupStatus === 'REJECTED'" link type="primary"
                     size="small" :loading="busy === row.planId" @click="signup(row)">
            {{ row.signupStatus === 'REJECTED' ? '重新报名' : '报名' }}
          </el-button>
          <el-button v-else-if="row.signupStatus === 'APPROVED'" link type="primary" size="small"
                     @click="router.push('/exam-center')">去考试</el-button>
          <span v-else class="muted">待审核</span>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <div v-show="tab === 'mine'" class="card">
    <el-table v-loading="loading" :data="filteredMine" border stripe
              :empty-text="keyword ? '没有匹配的报名记录' : '还没有报名记录'">
      <el-table-column prop="planName" label="考试名称" min-width="240" show-overflow-tooltip />
      <el-table-column label="审核状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="signupTagType(row.status)" size="small" effect="light">{{ signupText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="审核意见" min-width="180">
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="报名时间" width="170" />
      <el-table-column prop="reviewedAt" label="审核时间" width="170">
        <template #default="{ row }">{{ row.reviewedAt || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="danger" size="small" @click="cancel(row)">取消报名</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Icon from '@/components/Icon.vue'
import {
  cancelSignup, createSignup, listAvailablePlans, listMySignups,
  type AvailablePlan, type SignupRow
} from '@/api/student'

const router = useRouter()
const tab = ref<'available' | 'mine'>('available')
const loading = ref(false)
const busy = ref<number | null>(null)
const plans = ref<AvailablePlan[]>([])
const mine = ref<SignupRow[]>([])
const keyword = ref('')

const match = (name?: string) => {
  const kw = keyword.value.trim().toLowerCase()
  return !kw || (name || '').toLowerCase().includes(kw)
}
const filteredPlans = computed(() => plans.value.filter((p) => match(p.planName)))
const filteredMine = computed(() => mine.value.filter((r) => match(r.planName)))

const signupText = (s: string) =>
  ({ NONE: '未报名', PENDING: '待审核', APPROVED: '已通过', REJECTED: '未通过' }[s] ?? s)
const signupTagType = (s: string) =>
  ({ NONE: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] as
    'info' | 'warning' | 'success' | 'danger')

async function load() {
  loading.value = true
  try {
    if (tab.value === 'available') plans.value = await listAvailablePlans()
    else mine.value = await listMySignups()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function signup(row: AvailablePlan) {
  busy.value = row.planId
  try {
    await createSignup(row.planId)
    ElMessage.success('报名已提交，等待管理员审核')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    busy.value = null
  }
}

async function cancel(row: SignupRow) {
  try {
    await ElMessageBox.confirm(`确认取消「${row.planName}」的报名？`, '取消报名', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelSignup(row.id)
    ElMessage.success('已取消报名')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<style scoped>
.tline { font-size: 12.5px; line-height: 1.5; }
.muted { color: var(--text-3); }
</style>
