import { http } from './http'
import type { OptionItem, QuestionType } from './exam'

// ---------- 练习中心 ----------

export interface PracticeBank {
  id: number
  name: string
  level?: string
  questionCount: number
  practicedCount: number
  correctCount: number
  correctRate: number
}

export interface PracticeQuestion {
  id: number
  bankId: number
  type: QuestionType
  stem: string
  options: OptionItem[]
  score: number
}

export interface PracticeResult {
  correct: boolean
  remark: string
  myAnswer: string
  correctAnswer: string
  analysis?: string
  fullScore: number
}

export interface WrongItem {
  questionId: number
  type: QuestionType
  stem: string
  options: OptionItem[]
  myAnswer: string
  correctAnswer: string
  analysis?: string
  wrongTimes: number
  lastAt: string
}

export interface PracticeSummary {
  practicedCount: number
  correctCount: number
  correctRate: number
  wrongCount: number
}

export const listPracticeBanks = () => http.get('/student/practice/banks') as Promise<PracticeBank[]>
export const getPracticeSummary = () => http.get('/student/practice/summary') as Promise<PracticeSummary>
export const getPracticeQuestions = (params: { bankId?: number; size?: number; onlyWrong?: boolean }) =>
  http.get('/student/practice/questions', { params }) as Promise<PracticeQuestion[]>
export const submitPractice = (questionId: number, answer: string) =>
  http.post('/student/practice/answer', { questionId, answer }) as Promise<PracticeResult>
export const listWrong = () => http.get('/student/practice/wrong') as Promise<WrongItem[]>
export const clearWrong = (questionId: number) => http.delete(`/student/practice/wrong/${questionId}`) as Promise<void>

// ---------- 报名中心 ----------

export interface AvailablePlan {
  planId: number
  planName: string
  startAt: string
  endAt: string
  passScore: number
  totalScore: number
  durationMin: number
  planState: 'UPCOMING' | 'ONGOING'
  signupStatus: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'
  signupId?: number
}

export interface SignupRow {
  id: number
  planId: number
  planName: string
  studentName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  remark?: string
  createdAt: string
  reviewedAt?: string
}

export const listAvailablePlans = () => http.get('/student/signups/available') as Promise<AvailablePlan[]>
export const listMySignups = () => http.get('/student/signups') as Promise<SignupRow[]>
export const createSignup = (planId: number) => http.post('/student/signups', { planId }) as Promise<SignupRow>
export const cancelSignup = (id: number) => http.delete(`/student/signups/${id}`) as Promise<void>

export const listAdminSignups = (params: { planId?: number; status?: string }) =>
  http.get('/admin/signups', { params }) as Promise<SignupRow[]>
export const reviewSignup = (id: number, status: string, remark: string) =>
  http.put(`/admin/signups/${id}/review`, { status, remark }) as Promise<void>

// ---------- 个人中心 ----------

export interface ProfileStats {
  attemptCount: number
  passedCount: number
  avgScore: number
  bestScore: number
  passRate: number
}

export interface Profile {
  id: number
  username: string
  name: string
  phone: string
  email: string
  roles: string[]
  lastLoginAt: string
  studentNo: string
  orgName: string
  level: string
  stats: ProfileStats
}

export const getProfile = () => http.get('/me') as Promise<Profile>
export const changePassword = (oldPassword: string, newPassword: string) =>
  http.post('/me/password', { oldPassword, newPassword }) as Promise<void>
