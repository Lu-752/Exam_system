import type { ExamPlan, Overview } from './types'

/**
 * 尚未接入后端的模块暂用内存 mock（考试计划 / 首页概览）。
 * 接口签名与 /api/v1 契约一致，后端就绪后仅替换函数体。
 */

const delay = (ms = 200) => new Promise((r) => setTimeout(r, ms))

const plans: ExamPlan[] = [
  { id: 1, name: '2026 年度消防安全知识竞赛', paperName: '消防基础综合卷 A', startAt: '2026-10-22 09:00:00', endAt: '2026-10-22 18:00:00', duration: 60, signupCount: 320, attempts: 287, status: 'ongoing' },
  { id: 2, name: '电工安全操作规范考试', paperName: '电工操作规范卷（B）', startAt: '2026-10-22 20:00:00', endAt: '2026-10-22 21:00:00', duration: 45, signupCount: 156, attempts: 0, status: 'upcoming' },
  { id: 3, name: '危化品管理月度考核', paperName: '危化品管理综合卷', startAt: '2026-10-20 14:00:00', endAt: '2026-10-20 16:00:00', duration: 90, signupCount: 88, attempts: 85, status: 'finished' },
  { id: 4, name: '高处作业复审考试（第三批）', paperName: '高处作业复审卷', startAt: '2026-10-25 09:30:00', endAt: '2026-10-25 11:30:00', duration: 60, signupCount: 64, attempts: 0, status: 'upcoming' },
  { id: 5, name: '新员工安全准入考试', paperName: '安全准入基础卷', startAt: '2026-10-18 10:00:00', endAt: '2026-10-18 12:00:00', duration: 60, signupCount: 210, attempts: 208, status: 'finished' },
  { id: 6, name: '消防设施操作员（中级）模拟考', paperName: '设施操作员中级模拟卷', startAt: '2026-10-22 15:00:00', endAt: '2026-10-22 17:00:00', duration: 120, signupCount: 42, attempts: 18, status: 'ongoing' }
]

export async function listExamPlans(q: { keyword?: string; status?: string }): Promise<ExamPlan[]> {
  await delay()
  const kw = (q.keyword || '').trim()
  return plans.filter((p) => {
    const hitKw = !kw || p.name.includes(kw) || p.paperName.includes(kw)
    const hitStatus = !q.status || p.status === q.status
    return hitKw && hitStatus
  })
}

export async function getOverview(): Promise<Overview> {
  await delay(180)
  return {
    items: [
      { label: '学员总数', value: '5,111' },
      { label: '题库数量', value: '452' },
      { label: '试题数量', value: '49,306' },
      { label: '试卷数量', value: '821' },
      { label: '考试计划', value: '186' },
      { label: '考试场次', value: '1,116' },
      { label: '待批阅试卷', value: '7' },
      { label: '今日考试', value: '3' },
      { label: '参考人次', value: '12,847' },
      { label: '平均分', value: '86.5' },
      { label: '及格率', value: '92.3%' },
      { label: '练习次数', value: '45,209' }
    ]
  }
}
