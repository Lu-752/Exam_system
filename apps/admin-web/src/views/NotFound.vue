<template>
  <div class="nf">
    <div class="nf-code">404</div>
    <div class="nf-title">页面走丢了</div>
    <p class="nf-desc">你访问的地址不存在，可能是链接过期或该功能尚未上线。</p>
    <div class="nf-actions">
      <el-button @click="router.back()">返回上一页</el-button>
      <el-button type="primary" @click="router.push(auth.isStudent ? '/exam-center' : '/dashboard')">
        回到{{ auth.isStudent ? '考试中心' : '首页' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
</script>

<style scoped>
.nf {
  min-height: 60vh; display: flex; flex-direction: column;
  align-items: center; justify-content: center; text-align: center;
  padding: 48px 20px;
}
.nf-code {
  font-size: 76px; font-weight: 800; line-height: 1;
  background: linear-gradient(135deg, #5b8cff, #2c3f74);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  letter-spacing: 2px;
}
.nf-title { font-size: 18px; font-weight: 600; margin: 14px 0 6px; }
.nf-desc { color: var(--text-3); font-size: 13px; margin: 0 0 20px; }
.nf-actions { display: flex; gap: 10px; }
</style>
