import { http } from './http'

export type QuestionType = 'SC' | 'MC' | 'TF' | 'BLANK' | 'ESSAY' | 'COMPOSITE'

export interface OptionItem {
  key: string
  text: string
}

export interface Bank {
  id: number
  name: string
  level?: string
  /** 关联培训大纲 */
  outlineId?: number
  outlineName?: string
  sortNo?: number
  recommended?: boolean
  createdAt?: string
}

export interface QuestionItem {
  id: number
  bankId: number
  type: QuestionType
  stem: string
  options: OptionItem[]
  answer: string
  analysis?: string
  score: number
}

export interface Paper {
  id: number
  name: string
  totalScore: number
  passScore: number
  questionCount: number
  createdAt: string
}

export interface Plan {
  id: number
  name: string
  paperId: number
  paperName: string
  startAt: string
  endAt: string
  durationSec: number
  passScore: number
  totalScore: number
  questionCount: number
  status: 'UPCOMING' | 'ONGOING' | 'FINISHED'
  attemptId?: number
  attemptStatus?: 'IN_PROGRESS' | 'SUBMITTED'
  attemptScore?: number
  passed?: boolean
  /** 成绩是否已发布；未发布时 attemptScore 为空 */
  scorePublished?: boolean
}

export interface ExamQuestion {
  id: number
  type: QuestionType
  stem: string
  options: OptionItem[]
  score: number
  sortNo: number
}

export interface AttemptStart {
  attemptId: number
  planId: number
  planName: string
  passScore: number
  totalScore: number
  remainingSec: number
  questions: ExamQuestion[]
}

export interface ResultDetail {
  questionId: number
  type: QuestionType
  stem: string
  myAnswer: string
  correct: string
  ok: boolean
  score: number
  fullScore: number
  analysis?: string
  children?: ResultDetail[]
}

export interface ExamResult {
  attemptId: number
  planName: string
  score: number
  totalScore: number
  passed: boolean
  correctCount: number
  questionCount: number
  details: ResultDetail[]
  /** 成绩是否已发布；未发布时 score 为空、details 为空 */
  published?: boolean
}

// ---------- 题库 / 试题 ----------

export const listBanks = (keyword?: string) => http.get('/admin/question-banks', { params: { keyword } }) as Promise<Bank[]>
export const createBank = (body: { name: string; level?: string; outlineId?: number }) =>
  http.post('/admin/question-banks', body) as Promise<Bank>
export const updateBank = (id: number, body: { name: string; level?: string; outlineId?: number | null }) =>
  http.put(`/admin/question-banks/${id}`, body) as Promise<Bank>
export const deleteBank = (id: number) => http.delete(`/admin/question-banks/${id}`) as Promise<void>

export const listQuestions = (params: { bankId?: number; keyword?: string; type?: string }) =>
  http.get('/admin/questions', { params }) as Promise<QuestionItem[]>
export const createQuestion = (body: Record<string, unknown>) => http.post('/admin/questions', body) as Promise<QuestionItem>
export const deleteQuestion = (id: number) => http.delete(`/admin/questions/${id}`) as Promise<void>
export const importQuestions = (bankId: number, content: string) =>
  http.post('/admin/questions/import', { bankId, content }) as Promise<{
    total: number; success: number; failed: number; errors: { line: number; message: string }[]
  }>
export const questionImportTemplate = () =>
  http.get('/admin/questions/import-template') as Promise<string>

// ---------- 试卷 / 计划 ----------

export const listPapers = () => http.get('/admin/papers') as Promise<Paper[]>
export const createPaper = (body: Record<string, unknown>) => http.post('/admin/papers', body) as Promise<Paper>

export const listPlans = () => http.get('/admin/exam-plans') as Promise<Plan[]>
export const createPlan = (body: Record<string, unknown>) => http.post('/admin/exam-plans', body) as Promise<Plan>
export const publishScore = (id: number) => http.post(`/admin/exam-plans/${id}/publish-score`, {}) as Promise<void>
export const unpublishScore = (id: number) => http.post(`/admin/exam-plans/${id}/unpublish-score`, {}) as Promise<void>

// ---------- 学员端 ----------

export const listMyExams = () => http.get('/student/exams') as Promise<Plan[]>
export const startExam = (planId: number) => http.post(`/student/exams/${planId}/start`, {}) as Promise<AttemptStart>
export const submitExam = (attemptId: number, answers: { questionId: number; answer: string }[]) =>
  http.post(`/student/attempts/${attemptId}/submit`, { answers }) as Promise<ExamResult>
export const getResult = (attemptId: number) => http.get(`/student/attempts/${attemptId}/result`) as Promise<ExamResult>

/** 防作弊：上报切屏 / 失焦事件 */
export const reportExamEvent = (attemptId: number, type: 'VISIBILITY' | 'BLUR', count = 1) =>
  http.post(`/student/attempts/${attemptId}/events`, { type, count }) as Promise<{
    switchCount: number
    warned: boolean
    lastSwitchAt?: string | null
  }>

export const TYPE_LABEL: Record<QuestionType, string> = {
  SC: '单选题',
  MC: '多选题',
  TF: '判断题',
  BLANK: '填空题',
  ESSAY: '问答题',
  COMPOSITE: '题冒题'
}

// ---------- 阅卷 / 监考 ----------

export interface PendingReview {
  attemptId: number
  planId: number
  planName: string
  studentName: string
  submittedAt: string
  score: number
  totalScore: number
  passed: boolean
  essayTotal: number
  essayMarked: number
}

export interface ReviewItem {
  answerId: number
  questionId: number
  type: QuestionType
  stem: string
  myAnswer: string
  correctAnswer: string
  analysis?: string
  fullScore: number
  score: number
  comment?: string
  manual?: boolean
}

export interface AttemptReview {
  attemptId: number
  planName: string
  studentName: string
  score: number
  totalScore: number
  passed: boolean
  submittedAt: string
  items: ReviewItem[]
}

export interface ProctorRow {
  attemptId: number
  studentName: string
  status: string
  startedAt: string
  remainingSec: number
  answeredCount: number
  score?: number
  /** 切屏 / 失焦次数 */
  switchCount: number
  lastSwitchAt?: string | null
  /** 是否达到预警阈值 */
  suspicious: boolean
}

export const listPendingMarking = () => http.get('/admin/marking/pending') as Promise<PendingReview[]>
export const getReview = (attemptId: number) =>
  http.get(`/admin/marking/attempts/${attemptId}`) as Promise<AttemptReview>
export const saveScore = (answerId: number, score: number, comment: string) =>
  http.post(`/admin/marking/answers/${answerId}`, { score, comment }) as Promise<AttemptReview>

export const listProctorRows = (planId: number) => http.get(`/admin/proctor/${planId}`) as Promise<ProctorRow[]>
export const forceSubmit = (attemptId: number) =>
  http.post(`/admin/proctor/attempts/${attemptId}/force`, {}) as Promise<ProctorRow>

export const createAutoPaper = (body: Record<string, unknown>) =>
  http.post('/admin/papers/auto', body) as Promise<Paper>

export interface Metric { label: string; value: string }
export const getOverview = () => http.get('/dashboard/overview') as Promise<{ items: Metric[] }>

// ---------- 组卷策略 ----------

export interface StrategyRule {
  type: QuestionType
  count: number
  score: number
}

export interface Strategy {
  id: number
  name: string
  bankId?: number
  bankName: string
  rules: StrategyRule[]
  totalScore: number
  passScore: number
  durationSec: number
  remark?: string
  questionCount: number
  createdAt: string
}

export interface GenerateResult {
  paperId: number
  paperName: string
  totalScore: number
  questionCount: number
  gaps: string[]
}

export const listStrategies = () => http.get('/admin/strategies') as Promise<Strategy[]>
export const createStrategy = (body: Record<string, unknown>) =>
  http.post('/admin/strategies', body) as Promise<Strategy>
export const updateStrategy = (id: number, body: Record<string, unknown>) =>
  http.put(`/admin/strategies/${id}`, body) as Promise<Strategy>
export const deleteStrategy = (id: number) => http.delete(`/admin/strategies/${id}`) as Promise<void>
export const generateFromStrategy = (id: number, paperName?: string) =>
  http.post(`/admin/strategies/${id}/generate`, { paperName }) as Promise<GenerateResult>

// ---------- 交卷记录 ----------

export interface AttemptRow {
  attemptId: number
  planId: number
  planName: string
  studentName: string
  status: 'IN_PROGRESS' | 'SUBMITTED'
  score: number | null
  totalScore: number
  passed: boolean
  startedAt: string
  submittedAt: string | null
  answerCount: number
  essayCount: number
  essayMarked: number
  switchCount: number
}

export const listAttempts = (params: { planId?: number; status?: string; keyword?: string }) =>
  http.get('/admin/attempts', { params }) as Promise<AttemptRow[]>
