<template>
  <div class="result-page">
    <div v-loading="loading" class="wrap">
      <template v-if="data">
        <div class="hero" :class="{ fail: !data.passed }">
          <div class="hero-label">本次成绩</div>
          <div class="hero-score">{{ data.score }}<small> / {{ data.totalScore }}</small></div>
          <div class="hero-tags">
            <span class="pill">{{ data.passed ? '及格' : '不及格' }}</span>
            <span class="pill ghost">答对 {{ data.correctCount }} / {{ data.questionCount }} 题</span>
          </div>
          <div class="hero-name">{{ data.planName }}</div>
        </div>

        <div class="card">
          <div class="card-head"><span class="card-title">答题详情</span></div>
          <div v-for="(d, i) in data.details" :key="d.questionId" class="detail">
            <div class="dhead">
              <span class="dno">{{ i + 1 }}</span>
              <el-tag size="small" effect="plain">{{ TYPE_LABEL[d.type] }}</el-tag>
              <span class="dres" :class="{ ok: d.ok }">{{ statusText(d) }}</span>
              <span class="dscore">{{ d.score }} / {{ d.fullScore }} 分</span>
            </div>
            <div class="dstem">{{ d.stem }}</div>

            <template v-if="d.type === 'COMPOSITE'">
              <div v-for="(c, j) in d.children ?? []" :key="c.questionId" class="sub">
                <div class="subhead">
                  <span class="subno">{{ i + 1 }}.{{ j + 1 }}</span>
                  <el-tag size="small" effect="plain" type="info">{{ TYPE_LABEL[c.type] }}</el-tag>
                  <span class="dres" :class="{ ok: c.ok }">{{ statusText(c) }}</span>
                  <span class="dscore">{{ c.score }} / {{ c.fullScore }} 分</span>
                </div>
                <div class="substem">{{ c.stem }}</div>
                <div class="drow"><span class="dl">我的答案</span><span :class="{ wrong: !c.ok }">{{ c.myAnswer || '（未作答）' }}</span></div>
                <div v-if="c.type !== 'ESSAY'" class="drow"><span class="dl">正确答案</span><span class="ok-text">{{ c.correct }}</span></div>
                <div v-if="c.analysis" class="danalysis">解析：{{ c.analysis }}</div>
              </div>
            </template>

            <template v-else>
              <div class="drow"><span class="dl">我的答案</span><span :class="{ wrong: !d.ok }">{{ d.myAnswer || '（未作答）' }}</span></div>
              <div v-if="d.type !== 'ESSAY'" class="drow"><span class="dl">正确答案</span><span class="ok-text">{{ d.correct }}</span></div>
              <div v-if="d.analysis" class="danalysis">解析：{{ d.analysis }}</div>
            </template>
          </div>
        </div>

        <div class="foot">
          <el-button @click="router.push('/exam-center')">返回考试中心</el-button>
          <el-button type="primary" @click="router.push('/dashboard')">回到首页</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getResult, TYPE_LABEL, type ExamResult, type ResultDetail } from '@/api/exam'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const data = ref<ExamResult | null>(null)

/** 主观题尚未评分时提示「待人工阅卷」 */
function statusText(d: ResultDetail) {
  if (d.ok) return '正确'
  if (d.type === 'ESSAY') return '待人工阅卷'
  if (d.type === 'COMPOSITE') return (d.children ?? []).some((c) => c.type === 'ESSAY' && !c.ok) ? '部分待阅卷' : '部分错误'
  return '错误'
}

onMounted(async () => {
  try {
    data.value = await getResult(Number(route.params.attemptId))
  } catch (e) {
    ElMessage.error((e as Error).message)
    router.push('/exam-center')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.result-page { min-height: 100%; background: var(--bg); padding: 20px; }
.wrap { max-width: 880px; margin: 0 auto; }

.hero {
  border-radius: 12px; padding: 26px 28px; color: #fff; margin-bottom: 14px;
  background: linear-gradient(120deg, #2f7a4f, #3fa06a);
}
.hero.fail { background: linear-gradient(120deg, #9b3b3b, #c25555); }
.hero-label { font-size: 12px; opacity: .85; }
.hero-score { font-size: 46px; font-weight: 700; line-height: 1.1; margin: 6px 0 10px; }
.hero-score small { font-size: 16px; font-weight: 400; opacity: .8; }
.hero-tags { display: flex; gap: 8px; }
.pill { background: rgba(255, 255, 255, .2); padding: 3px 10px; border-radius: 20px; font-size: 12px; }
.pill.ghost { background: rgba(255, 255, 255, .12); }
.hero-name { margin-top: 12px; font-size: 13px; opacity: .9; }

.detail { padding: 14px 0; border-bottom: 1px dashed var(--border); }
.detail:last-child { border-bottom: 0; }
.dhead { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.dno {
  width: 20px; height: 20px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: #eef2fb; color: var(--text-2); font-size: 12px;
}
.dres { color: #d14343; font-size: 12px; }
.dres.ok { color: #2f7a4f; }
.dscore { margin-left: auto; color: var(--text-3); font-size: 12px; }
.dstem { font-size: 13.5px; line-height: 1.7; margin-bottom: 8px; }
.drow { display: flex; gap: 10px; font-size: 12.5px; line-height: 1.8; }
.dl { width: 62px; color: var(--text-3); flex: 0 0 auto; }
.wrong { color: #d14343; }
.ok-text { color: #2f7a4f; }
.danalysis {
  margin-top: 8px; padding: 8px 10px; background: #f8faff;
  border-radius: 6px; font-size: 12.5px; color: var(--text-2); line-height: 1.7;
}

.sub {
  margin-top: 10px; padding: 12px 14px;
  border: 1px solid var(--border); border-radius: 8px; background: #fcfdff;
}
.subhead { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.subno {
  font-size: 11.5px; font-weight: 700; color: var(--brand);
  background: var(--brand-soft); border-radius: 5px; padding: 1px 6px;
}
.substem { font-size: 13px; line-height: 1.7; margin-bottom: 6px; color: var(--text); }
.foot { display: flex; justify-content: center; gap: 10px; padding: 18px 0 6px; }
</style>
