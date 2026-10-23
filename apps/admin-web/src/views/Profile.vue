<template>
  <div class="page-head">
    <div class="page-title">个人中心</div>
    <div class="head-actions">
      <el-button @click="router.push(auth.isStudent ? '/exam-center' : '/dashboard')">返回</el-button>
    </div>
  </div>

  <div class="layout">
    <div class="card left">
      <div class="me">
        <div class="avatar">{{ profile?.name?.slice(0, 1) || 'U' }}</div>
        <div class="me-name">{{ profile?.name || '—' }}</div>
        <div class="me-sub">{{ profile?.username }}</div>
        <el-tag v-for="r in profile?.roles ?? []" :key="r" size="small" effect="plain" class="role">{{ roleText(r) }}</el-tag>
      </div>
      <div class="kv"><span>学号</span><b>{{ profile?.studentNo || '—' }}</b></div>
      <div class="kv"><span>组织</span><b>{{ profile?.orgName || '—' }}</b></div>
      <div class="kv"><span>等级</span><b>{{ profile?.level || '—' }}</b></div>
      <div class="kv"><span>手机号</span><b>{{ profile?.phone || '—' }}</b></div>
      <div class="kv"><span>最近登录</span><b>{{ profile?.lastLoginAt || '—' }}</b></div>
    </div>

    <div class="right">
      <div class="card">
        <div class="card-head"><span class="card-title">我的考试数据</span></div>
        <div class="stats">
          <div class="st"><div class="st-label">参考次数</div><div class="st-num">{{ profile?.stats.attemptCount ?? 0 }}</div></div>
          <div class="st"><div class="st-label">及格次数</div><div class="st-num ok">{{ profile?.stats.passedCount ?? 0 }}</div></div>
          <div class="st"><div class="st-label">平均分</div><div class="st-num">{{ profile?.stats.avgScore ?? '—' }}</div></div>
          <div class="st"><div class="st-label">最高分</div><div class="st-num">{{ profile?.stats.bestScore ?? '—' }}</div></div>
          <div class="st"><div class="st-label">及格率</div><div class="st-num ok">{{ profile?.stats.passRate ?? 0 }}%</div></div>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><span class="card-title">修改密码</span></div>
        <el-form label-width="90px" style="max-width: 420px">
          <el-form-item label="原密码">
            <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwd.newPassword" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="savePassword">保存</el-button>
            <span class="tip">修改成功后需重新登录</span>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { changePassword, getProfile, type Profile } from '@/api/student'

const router = useRouter()
const auth = useAuthStore()
const profile = ref<Profile | null>(null)
const saving = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const roleText = (r: string) => ({ SUPER_ADMIN: '超级管理员', STUDENT: '学员' }[r] ?? r)

async function load() {
  try {
    profile.value = await getProfile()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function savePassword() {
  if (!pwd.oldPassword) return ElMessage.warning('请输入原密码')
  if (pwd.newPassword.length < 6) return ElMessage.warning('新密码至少 6 位')
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')

  saving.value = true
  try {
    await changePassword(pwd.oldPassword, pwd.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    await auth.logout()
    router.replace('/login')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.layout { display: flex; gap: 12px; align-items: flex-start; }
.left { width: 320px; flex: 0 0 auto; }
.right { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; }

.me { text-align: center; padding: 8px 0 16px; border-bottom: 1px dashed var(--border); margin-bottom: 12px; }
.avatar {
  width: 64px; height: 64px; border-radius: 50%; margin: 0 auto 10px;
  background: var(--brand-soft); color: var(--brand);
  display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 600;
}
.me-name { font-size: 16px; font-weight: 600; color: #1f2b4d; }
.me-sub { font-size: 12px; color: var(--text-3); margin: 4px 0 8px; }
.role { margin: 0 3px; }

.kv { display: flex; justify-content: space-between; padding: 9px 0; font-size: 12.5px; border-bottom: 1px dashed var(--border); }
.kv:last-child { border-bottom: 0; }
.kv span { color: var(--text-3); }
.kv b { font-weight: 500; color: var(--text); }

.stats { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
.st { background: #f8faff; border: 1px solid #eef2fb; border-radius: 8px; padding: 12px 14px; }
.st-label { font-size: 12px; color: var(--text-3); margin-bottom: 6px; }
.st-num { font-size: 20px; font-weight: 600; color: #1f2b4d; }
.st-num.ok { color: #17a673; }
.tip { margin-left: 12px; font-size: 12px; color: var(--text-3); }
</style>
