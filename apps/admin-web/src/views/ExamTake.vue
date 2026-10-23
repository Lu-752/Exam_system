<template>
  <div class="take" :style="{ '--qfs': fontSize + 'px' }">
    <header class="take-bar">
      <div class="left">
        <button class="back" @click="leave">退出</button>
        <div class="title-box">
          <span class="name">{{ data?.planName || '在线考试' }}</span>
          <span v-if="data" class="meta">
            满分 {{ data.totalScore }} · 及格 {{ data.passScore }} · 共 {{ questions.length }} 题
          </span>
        </div>
      </div>

      <div class="right">
        <div v-if="switchCount > 0" class="cheat" :class="{ hot: switchCount >= 3 }"
             :title="`考试期间离开页面的次数，已实时上报监考`">
          <svg class="cico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" />
            <path d="M12 9v4M12 17h.01" />
          </svg>
          <span>切屏 {{ switchCount }}</span>
        </div>

        <div class="font-ctl" title="调整试题字号">
          <button class="fbtn" :disabled="fontSize <= 13" @click="setFont(fontSize - 1)">A−</button>
          <span class="fval">{{ fontSize }}</span>
          <button class="fbtn" :disabled="fontSize >= 22" @click="setFont(fontSize + 1)">A+</button>
        </div>

        <div class="timer" :class="urgency">
          <svg class="tico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="13" r="8" />
            <path d="M12 9v4l2.5 2M9 2h6" />
          </svg>
          <span class="tnum">{{ clock }}</span>
        </div>

        <el-button type="primary" size="large" :loading="submitting" @click="confirmSubmit">交 卷</el-button>
      </div>
    </header>

    <div class="progress"><div class="pbar" :style="{ width: progress + '%' }" /></div>

    <div v-loading="loading" class="body">
      <main v-if="current" class="qpane">
        <section class="qcard">
          <div class="qhead">
            <span class="qno">{{ index + 1 }}</span>
            <el-tag size="small" effect="plain">{{ TYPE_LABEL[current.type] }}</el-tag>
            <span class="qscore">{{ current.score }} 分</span>
            <span class="qpos">第 {{ index + 1 }} / {{ questions.length }} 题</span>
          </div>

          <div class="stem">{{ current.stem }}</div>

          <AnswerInput v-if="current.type !== 'COMPOSITE'" :q="current"
                       :model-value="answers[current.id] ?? ''"
                       @update:model-value="(v: string) => setAnswer(current.id, v)" />

          <div v-else class="subwrap">
            <div v-for="(s, si) in current.children ?? []" :key="s.id" class="subcard">
              <div class="subhead">
                <span class="subno">{{ index + 1 }}.{{ si + 1 }}</span>
                <el-tag size="small" effect="plain" type="info">{{ TYPE_LABEL[s.type] }}</el-tag>
                <span class="subscore">{{ s.score }} 分</span>
              </div>
              <div class="substem">{{ s.stem }}</div>
              <AnswerInput :q="s" :model-value="answers[s.id] ?? ''" :rows="5"
                           @update:model-value="(v: string) => setAnswer(s.id, v)" />
            </div>
            <el-empty v-if="!(current.children ?? []).length" description="该材料题暂无子题" :image-size="60" />
          </div>
        </section>

        <footer class="qnav">
          <el-button :disabled="index === 0" @click="go(index - 1)">上一题</el-button>
          <span class="hint">← → 可切换题目 · 答案自动保留</span>
          <el-button v-if="index < questions.length - 1" type="primary" plain @click="go(index + 1)">下一题</el-button>
          <el-button v-else type="primary" @click="confirmSubmit">交卷并结束</el-button>
        </footer>
      </main>

      <el-empty v-else-if="!loading" description="本场考试暂无试题" />

      <aside class="sheet">
        <div class="sheet-head">
          <span class="sheet-title">答题卡</span>
          <span class="sheet-count">{{ answeredCount }} / {{ questions.length }}</span>
        </div>
        <div class="grid">
          <button v-for="(q, idx) in questions" :key="q.id" class="cell"
                  :class="{ done: isAnswered(q), cur: index === idx }" @click="go(idx)">
            {{ idx + 1 }}
          </button>
        </div>
        <div class="legend">
          <span><i class="d" />已答 {{ answeredCount }}</span>
          <span><i />未答 {{ questions.length - answeredCount }}</span>
        </div>
        <div class="sheet-tip">
          交卷后不可修改，请确认所有题目后再提交。<br />
          考试期间切屏、离开页面会被记录并同步给监考。
        </div>
        <el-button type="primary" size="large" class="sheet-submit" :loading="submitting" @click="confirmSubmit">
          交 卷
        </el-button>
      </aside>
    </div>

    <div v-if="finished" class="mask">
      <div class="done-box">
        <div class="done-title">已交卷</div>
        <p>正在跳转到成绩页…</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AnswerInput from '@/components/AnswerInput.vue'
import { startExam, submitExam, reportExamEvent, TYPE_LABEL, type AttemptStart, type ExamQuestion } from '@/api/exam'

const FONT_KEY = 'exam_font_px'
const route = useRoute()
const router = useRouter()

const loading = ref(true)
const submitting = ref(false)
const finished = ref(false)
const data = ref<AttemptStart | null>(null)
const questions = computed<ExamQuestion[]>(() => data.value?.questions ?? [])

const answers = reactive<Record<number, string>>({})
const remaining = ref(0)
const index = ref(0)

const fontSize = ref(Number(localStorage.getItem(FONT_KEY)) || 15)

let timer: ReturnType<typeof setInterval> | null = null

const current = computed<ExamQuestion | undefined>(() => questions.value[index.value])

const clock = computed(() => {
  const s = Math.max(0, remaining.value)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(sec)}`
})

/** 最后 5 分钟警示、最后 1 分钟强警示 */
const urgency = computed(() => {
  if (remaining.value <= 60) return 'critical'
  if (remaining.value <= 300) return 'warn'
  return 'normal'
})

const answeredCount = computed(() => questions.value.filter((q) => isAnswered(q)).length)
const progress = computed(() =>
  questions.value.length ? Math.round((answeredCount.value / questions.value.length) * 100) : 0
)

function isAnswered(q: ExamQuestion) {
  if (q.type === 'COMPOSITE') {
    const subs = q.children ?? []
    return subs.length > 0 && subs.every((c) => !!answers[c.id]?.toString().trim())
  }
  return !!answers[q.id]?.toString().trim()
}

function setAnswer(id: number, v: string) {
  answers[id] = v
}

/* ---------- 本地草稿：误刷新/误关页不丢答案 ---------- */
let draftKey = ''

function saveDraft() {
  if (!draftKey) return
  try {
    sessionStorage.setItem(draftKey, JSON.stringify(answers))
  } catch { /* 存储满则忽略 */ }
}

function restoreDraft() {
  if (!draftKey) return
  try {
    const raw = sessionStorage.getItem(draftKey)
    if (raw) Object.assign(answers, JSON.parse(raw) as Record<number, string>)
  } catch { /* 脏数据直接丢弃 */ }
}

function clearDraft() {
  if (draftKey) sessionStorage.removeItem(draftKey)
}

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (finished.value || submitting.value) return
  e.preventDefault()
  e.returnValue = ''
}

function go(i: number) {
  if (i < 0 || i >= questions.value.length) return
  index.value = i
}

function setFont(v: number) {
  fontSize.value = Math.min(22, Math.max(13, v))
  localStorage.setItem(FONT_KEY, String(fontSize.value))
}

watch(fontSize, (v) => localStorage.setItem(FONT_KEY, String(v)))

/* ---------- 防作弊：切屏 / 失焦上报 ---------- */
const switchCount = ref(0)
const switchWarned = ref(false)
let lastReportAt = 0

function reportSwitch(type: 'VISIBILITY' | 'BLUR') {
  if (!data.value || finished.value || submitting.value) return
  const now = Date.now()
  // visibilitychange 与 blur 常成对触发，800ms 内合并为一次
  if (now - lastReportAt < 800) return
  lastReportAt = now
  switchCount.value += 1
  reportExamEvent(data.value.attemptId, type)
    .then((r) => {
      switchCount.value = r.switchCount
      if (r.warned && !switchWarned.value) {
        switchWarned.value = true
        ElMessage.warning(`已记录 ${r.switchCount} 次切屏，本场考试将被标记为异常答卷`)
      }
    })
    .catch(() => { /* 上报失败不打扰考生，本地仍保留计数 */ })
}

function onVisibility() {
  if (document.visibilityState === 'hidden') reportSwitch('VISIBILITY')
}

function onBlur() {
  reportSwitch('BLUR')
}

function onKey(e: KeyboardEvent) {
  if (e.altKey || e.ctrlKey || e.metaKey) return
  const tag = (e.target as HTMLElement)?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  if (e.key === 'ArrowLeft') go(index.value - 1)
  if (e.key === 'ArrowRight') go(index.value + 1)
}

function collect() {
  const out: { questionId: number; answer: string }[] = []
  for (const q of questions.value) {
    if (q.type === 'COMPOSITE') {
      for (const c of q.children ?? []) {
        const v = answers[c.id] ?? ''
        if (v !== '') out.push({ questionId: c.id, answer: v })
      }
      continue
    }
    const v = answers[q.id] ?? ''
    if (v !== '') out.push({ questionId: q.id, answer: v })
  }
  return out
}

async function doSubmit(auto = false) {
  if (!data.value || submitting.value) return
  submitting.value = true
  try {
    const res = await submitExam(data.value.attemptId, collect())
    finished.value = true
    clearDraft()
    clearTimer()
    // 成绩未发布：不跳成绩页，回考试中心显示「待发布」
    if (res.published === false) {
      ElMessage.success('已交卷，成绩待管理员发布后查看')
      router.replace('/exam-center')
      return
    }
    router.replace(`/exam-result/${data.value.attemptId}`)
  } catch (e) {
    ElMessage.error((e as Error).message)
    submitting.value = false
    if (auto) finished.value = false
  }
}

async function confirmSubmit() {
  const unanswered = questions.value.length - answeredCount.value
  const msg = unanswered > 0
    ? `还有 ${unanswered} 道题未作答，确认交卷吗？`
    : '所有题目均已作答，确认交卷吗？'
  try {
    await ElMessageBox.confirm(msg, '交卷确认', { type: 'warning', confirmButtonText: '确认交卷' })
  } catch {
    return
  }
  await doSubmit()
}

function leave() {
  ElMessageBox.confirm('退出后本次作答不会提交，确定退出吗？', '退出考试', { type: 'warning' })
    .then(() => {
      clearTimer()
      finished.value = true
      router.push('/exam-center')
    })
    .catch(() => undefined)
}

onBeforeRouteLeave(async () => {
  if (finished.value || submitting.value || !data.value) return true
  try {
    await ElMessageBox.confirm('离开页面将放弃本次作答，确定离开吗？', '确认离开', { type: 'warning' })
    clearTimer()
    return true
  } catch {
    return false
  }
})

onMounted(async () => {
  try {
    const planId = Number(route.params.planId)
    data.value = await startExam(planId)
    remaining.value = data.value.remainingSec
    index.value = 0

    draftKey = `exam_draft_${data.value.attemptId}`
    restoreDraft()

    timer = setInterval(() => {
      remaining.value -= 1
      if (remaining.value <= 0) {
        clearTimer()
        ElMessage.warning('考试时间已到，系统自动交卷')
        doSubmit(true)
      }
    }, 1000)
    window.addEventListener('keydown', onKey)
    window.addEventListener('beforeunload', onBeforeUnload)
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onBlur)
  } catch (e) {
    ElMessage.error((e as Error).message)
    setTimeout(() => router.push('/exam-center'), 1200)
  } finally {
    loading.value = false
  }
})

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onBeforeUnmount(() => {
  clearTimer()
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('blur', onBlur)
})

// 作答变化即写本地草稿（防误刷新丢答案）
watch(answers, saveDraft, { deep: true })
</script>

<style scoped>
.take {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  overflow: hidden;
}

/* ---------- 顶部条 ---------- */
.take-bar {
  height: 64px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  color: #fff;
  background: linear-gradient(90deg, #212f52 0%, #2c3f74 60%, #35508f 100%);
  box-shadow: 0 2px 10px rgba(20, 28, 48, .18);
  z-index: 5;
}
.take-bar .left { display: flex; align-items: center; gap: 14px; min-width: 0; }
.back {
  background: rgba(255, 255, 255, .14);
  border: 1px solid rgba(255, 255, 255, .18);
  color: #fff;
  padding: 6px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 12.5px;
  flex: 0 0 auto;
}
.back:hover { background: rgba(255, 255, 255, .26); }
.title-box { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.name { font-size: 15px; font-weight: 600; letter-spacing: .2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.meta { font-size: 11.5px; color: rgba(255, 255, 255, .62); }

.take-bar .right { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }

/* 切屏计数徽标 */
.cheat {
  display: inline-flex; align-items: center; gap: 6px;
  height: 30px; padding: 0 12px; border-radius: 999px;
  background: rgba(255, 196, 0, .18);
  border: 1px solid rgba(255, 200, 60, .55);
  color: #ffcf5c;
  font-size: 12px; font-variant-numeric: tabular-nums;
}
.cheat.hot { background: rgba(229, 72, 77, .22); border-color: rgba(255, 120, 124, .7); color: #ff9a9d; }
.cico { width: 14px; height: 14px; }

.font-ctl {
  display: flex; align-items: center; gap: 2px;
  background: rgba(255, 255, 255, .1);
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 8px; padding: 2px;
}
.fbtn {
  border: 0; background: transparent; color: rgba(255, 255, 255, .86);
  height: 26px; padding: 0 9px; border-radius: 6px; cursor: pointer;
  font-size: 12.5px; font-family: inherit;
}
.fbtn:hover:not(:disabled) { background: rgba(255, 255, 255, .18); color: #fff; }
.fbtn:disabled { opacity: .35; cursor: not-allowed; }
.fval { font-size: 11.5px; color: rgba(255, 255, 255, .7); min-width: 18px; text-align: center; }

/* ---------- 倒计时 ---------- */
.timer {
  display: inline-flex; align-items: center; gap: 7px;
  height: 36px; padding: 0 14px;
  border-radius: 999px;
  background: rgba(255, 90, 95, .14);
  border: 1px solid rgba(255, 107, 107, .45);
  color: #ff8489;
  font-variant-numeric: tabular-nums;
  transition: all .2s;
}
.tico { width: 16px; height: 16px; opacity: .9; }
.tnum { font-family: Consolas, "SF Mono", monospace; font-size: 18px; font-weight: 700; letter-spacing: 1px; }

.timer.warn {
  background: #e5484d;
  border-color: #ff8b8f;
  color: #fff;
  box-shadow: 0 0 0 4px rgba(229, 72, 77, .22);
  animation: pulse 1.6s ease-in-out infinite;
}
.timer.critical {
  background: #d31f25;
  border-color: #fff;
  color: #fff;
  box-shadow: 0 0 0 6px rgba(229, 72, 77, .3);
  animation: pulse .9s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.045); }
}

/* ---------- 进度 ---------- */
.progress { height: 3px; background: #e7ebf5; flex: 0 0 auto; }
.pbar { height: 100%; background: linear-gradient(90deg, #5b8cff, #3d6ef5); transition: width .25s ease; }

/* ---------- 主体 ---------- */
.body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 14px;
  padding: 14px 18px 16px;
}

.qpane { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; }

.qcard {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--border);
  box-shadow: 0 2px 8px rgba(31, 36, 48, .05);
  padding: 30px 44px 36px;
}

.qhead { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; }
.qno {
  width: 26px; height: 26px; border-radius: 8px;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--brand); color: #fff; font-size: 13px; font-weight: 600;
}
.qscore { color: var(--text-3); font-size: 12px; }
.qpos { margin-left: auto; color: var(--text-3); font-size: 12px; }

.stem {
  font-size: var(--qfs);
  line-height: 1.9;
  color: #1a2130;
  margin-bottom: 22px;
  font-weight: 500;
}

/* ---------- 题冒题（材料 + 子题） ---------- */
.subwrap { display: flex; flex-direction: column; gap: 16px; }
.subcard {
  border: 1px solid var(--border); border-radius: 10px;
  padding: 14px 16px 16px; background: #fcfdff;
}
.subhead { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.subno {
  font-size: calc(var(--qfs) - 2px); font-weight: 700; color: var(--brand);
  background: var(--brand-soft); border-radius: 6px; padding: 1px 7px;
}
.subscore { font-size: 12px; color: var(--text-3); }
.substem {
  font-size: calc(var(--qfs) - 1px); line-height: 1.8; color: #1a2130;
  margin-bottom: 12px;
}

.qnav {
  flex: 0 0 auto; display: flex; align-items: center; gap: 12px;
  background: #fff; border: 1px solid var(--border); border-radius: 12px;
  padding: 10px 14px;
}
.qnav .hint { margin: 0 auto; font-size: 12px; color: var(--text-3); }

/* ---------- 答题卡 ---------- */
.sheet {
  width: 268px;
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 36, 48, .05);
  padding: 16px;
}
.sheet-head { display: flex; align-items: baseline; margin-bottom: 12px; }
.sheet-title { font-size: 13.5px; font-weight: 600; }
.sheet-count { margin-left: auto; font-size: 12px; color: var(--text-3); }
.grid {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px;
  overflow-y: auto; align-content: start;
  flex: 1; min-height: 0; padding-right: 2px;
}
.cell {
  height: 34px; border: 1px solid var(--border); border-radius: 8px;
  background: #fff; color: var(--text-2); font-size: 12.5px; cursor: pointer;
  transition: all .12s;
}
.cell:hover { border-color: #b9cbff; color: var(--brand); }
.cell.done { background: var(--brand); border-color: var(--brand); color: #fff; }
.cell.cur { box-shadow: 0 0 0 2px rgba(61, 110, 245, .4); font-weight: 600; }
.legend { display: flex; gap: 14px; margin: 14px 0 0; font-size: 12px; color: var(--text-3); }
.legend span { display: inline-flex; align-items: center; gap: 5px; }
.legend i { width: 10px; height: 10px; border: 1px solid var(--border); border-radius: 3px; display: inline-block; }
.legend i.d { background: var(--brand); border-color: var(--brand); }
.sheet-tip {
  margin-top: 12px; padding: 8px 10px; border-radius: 8px;
  background: #fff8ec; color: #a8741a; font-size: 11.5px; line-height: 1.6;
}
.sheet-submit { margin-top: 12px; width: 100%; }

.mask {
  position: fixed; inset: 0; background: rgba(31, 36, 48, .45);
  display: flex; align-items: center; justify-content: center; z-index: 20;
}
.done-box { background: #fff; border-radius: 12px; padding: 26px 34px; text-align: center; }
.done-title { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.done-box p { margin: 0; color: var(--text-3); font-size: 12.5px; }

@media (max-width: 1100px) {
  .sheet { width: 220px; }
  .qcard { padding: 22px 24px 28px; }
}
</style>
