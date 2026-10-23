<template>
  <div class="login">
    <section class="brand-pane">
      <div class="brand-top">
        <span class="logo">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M2.5 9.3 12 4.8l9.5 4.5L12 13.8z" />
            <path d="M6.6 11.2v4.4c0 1.7 2.4 2.9 5.4 2.9s5.4-1.2 5.4-2.9v-4.4" />
            <path d="M20.7 10.1v4.6" />
          </svg>
        </span>
        <span>学生考试系统</span>
      </div>

      <div class="brand-copy">
        <h1>从命题到成绩，<br />一条链路跑完</h1>
        <p>大纲 · 题库 · 组卷 · 考试 · 监考 · 阅卷 · 成绩分析</p>
        <ul>
          <li>服务端计时与答案自动保存，断线可续考</li>
          <li>考场实时监考：暂停、强制交卷、消息直达</li>
          <li>客观题即时判分，主观题多人阅卷</li>
        </ul>
      </div>

      <div class="brand-foot">v0.1.0 · M0 骨架</div>
    </section>

    <section class="form-pane">
      <div class="form-box">
        <h2>账号登录</h2>
        <p class="sub">请使用管理员账号登录后台</p>

        <el-form :model="form" @submit.prevent>
          <el-form-item>
            <el-input v-model="form.username" size="large" placeholder="用户名" autocomplete="username" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="form.password" type="password" size="large" placeholder="密码"
                      show-password autocomplete="current-password" @keyup.enter="submit" />
          </el-form-item>
          <el-form-item>
            <div class="captcha-row">
              <el-input v-model="form.captcha" size="large" placeholder="验证码" maxlength="4" @keyup.enter="submit" />
              <button class="captcha" type="button" title="点击刷新" @click="refreshCaptcha">{{ code }}</button>
            </div>
          </el-form-item>

          <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="err" />

          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            登 录
          </el-button>
        </el-form>

        <p class="hint">管理员 admin / 123456 · 学员 student / 123456</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const form = reactive({ username: 'admin', password: '123456', captcha: '' })
const code = ref('')
const loading = ref(false)
const error = ref('')

function makeCode() {
  const s = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  return Array.from({ length: 4 }, () => s[Math.floor(Math.random() * s.length)]).join('')
}
function refreshCaptcha() {
  code.value = makeCode()
  form.captcha = ''
}

onMounted(refreshCaptcha)

async function submit() {
  error.value = ''
  if (!form.username.trim()) { error.value = '请输入用户名'; return }
  if (!form.password) { error.value = '请输入密码'; return }
  if (form.captcha.toUpperCase() !== code.value) {
    refreshCaptcha()
    error.value = '验证码不正确'
    return
  }

  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    error.value = (e as Error).message || '登录失败'
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login { height: 100%; display: flex; }

.brand-pane {
  width: 52%;
  min-width: 520px;
  padding: 32px 48px;
  display: flex;
  flex-direction: column;
  color: #fff;
  background: linear-gradient(135deg, var(--topbar-from) 0%, var(--topbar-to) 62%, #4a63b8 100%);
  position: relative;
  overflow: hidden;
}
.brand-pane::after {
  content: '';
  position: absolute;
  right: -140px; bottom: -160px;
  width: 420px; height: 420px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, .14), transparent 68%);
}
.brand-top { display: flex; align-items: center; gap: 9px; font-size: 15px; font-weight: 600; }
.brand-top .logo { width: 22px; height: 22px; }
.brand-top svg { width: 100%; height: 100%; }

.brand-copy { margin-top: auto; margin-bottom: auto; max-width: 460px; }
.brand-copy h1 { font-size: 32px; line-height: 1.35; margin: 0 0 14px; font-weight: 700; letter-spacing: .5px; }
.brand-copy p { color: rgba(255, 255, 255, .72); font-size: 13px; margin: 0 0 22px; }
.brand-copy ul { margin: 0; padding: 0; list-style: none; }
.brand-copy li {
  position: relative; padding-left: 16px; margin-bottom: 10px;
  font-size: 13px; color: rgba(255, 255, 255, .84);
}
.brand-copy li::before {
  content: ''; position: absolute; left: 0; top: 7px;
  width: 5px; height: 5px; border-radius: 50%; background: #8fb0ff;
}
.brand-foot { font-size: 12px; color: rgba(255, 255, 255, .45); }

.form-pane { flex: 1; display: flex; align-items: center; justify-content: center; background: #fff; }
.form-box { width: 340px; }
.form-box h2 { margin: 0 0 6px; font-size: 21px; }
.form-box .sub { margin: 0 0 26px; color: var(--text-3); font-size: 12.5px; }

.captcha-row { display: flex; gap: 10px; width: 100%; }
.captcha {
  flex: 0 0 108px; height: 40px;
  border: 1px solid var(--border); border-radius: 6px;
  background: repeating-linear-gradient(45deg, #f7f9ff, #f7f9ff 6px, #eef2ff 6px, #eef2ff 12px);
  color: var(--brand); font-size: 18px; font-weight: 700;
  letter-spacing: 5px; font-family: Georgia, serif;
  cursor: pointer; user-select: none;
}
.err { margin-bottom: 14px; }
.submit { width: 100%; margin-top: 4px; }
.hint { margin-top: 18px; font-size: 12px; color: var(--text-3); text-align: center; }
</style>
