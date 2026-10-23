import { http } from './http'
import type { PageResult, Student, StudentStatus } from './types'

export interface StudentQuery {
  keyword?: string
  /** 组织 id，按该组织 + 全部下级筛选 */
  orgId?: number
  status?: StudentStatus | ''
  page: number
  size: number
}

export interface StudentPayload {
  no: string
  name: string
  gender: '男' | '女'
  orgId?: number | null
  orgName?: string
  phone: string
  level: string
  status: StudentStatus
}

export function listStudents(q: StudentQuery): Promise<PageResult<Student>> {
  return http.get('/admin/students', {
    params: {
      keyword: q.keyword || undefined,
      orgId: q.orgId || undefined,
      status: q.status || undefined,
      page: q.page,
      size: q.size
    }
  })
}

export function createStudent(payload: StudentPayload): Promise<Student> {
  return http.post('/admin/students', payload)
}

export function updateStudent(id: number, payload: Partial<StudentPayload>): Promise<Student> {
  return http.put(`/admin/students/${id}`, payload)
}

export function deleteStudent(id: number): Promise<void> {
  return http.delete(`/admin/students/${id}`)
}

export interface ImportRowError {
  line: number
  message: string
}

export interface ImportResult {
  total: number
  success: number
  failed: number
  errors: ImportRowError[]
}

export const importStudents = (content: string, autoCreateOrg = false) =>
  http.post('/admin/students/import', { content, autoCreateOrg }) as Promise<ImportResult>

export const studentImportTemplate = () =>
  http.get('/admin/students/import-template') as Promise<string>
