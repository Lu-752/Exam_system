export type StudentStatus = 'ACTIVE' | 'DISABLED'

export interface Student {
  id: number
  no: string
  name: string
  gender: '男' | '女'
  orgId?: number
  orgName: string
  phone: string
  level: string
  examCount: number
  status: StudentStatus
  createdAt: string
}

export type PlanStatus = 'ongoing' | 'upcoming' | 'finished'

export interface ExamPlan {
  id: number
  name: string
  paperName: string
  startAt: string
  endAt: string
  duration: number
  signupCount: number
  attempts: number
  status: PlanStatus
}

export interface Overview {
  items: { label: string; value: string }[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface LoginResult {
  token: string
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  name: string
  avatar: string
  roles: string[]
  perms: string[]
}

export interface MenuNode {
  id: number
  parentId: number | null
  name: string
  path: string | null
  icon: string | null
  perm: string | null
  type: 'DIR' | 'MENU' | 'BUTTON'
  sortNo: number
  children: MenuNode[]
}
