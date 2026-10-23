import { http } from './http'
import type { PageResult } from './types'

export interface OrgNode {
  id: number
  parentId?: number
  name: string
  code?: string
  leader?: string
  phone?: string
  status: 'ACTIVE' | 'DISABLED'
  remark?: string
  sortNo?: number
  /** 直属人数 */
  memberCount: number
  /** 含下级总人数 */
  totalMemberCount: number
  children: OrgNode[]
}

export interface OrgStats {
  orgCount: number
  memberCount: number
  unassigned: number
}

export interface OrgMember {
  id: number
  no: string
  name: string
  gender: string
  phone: string
  level: string
  examCount: number
  status: string
  orgName: string
}

export interface OrgPayload {
  name: string
  code?: string
  leader?: string
  phone?: string
  status?: string
  remark?: string
  sortNo?: number
  parentId?: number | null
}

export const listOrgs = (keyword?: string) =>
  http.get('/admin/orgs', { params: { keyword: keyword || undefined } }) as Promise<OrgNode[]>

export const getOrgStats = () => http.get('/admin/orgs/stats') as Promise<OrgStats>

export const createOrg = (body: OrgPayload) => http.post('/admin/orgs', body) as Promise<OrgNode>

export const updateOrg = (id: number, body: OrgPayload) =>
  http.put(`/admin/orgs/${id}`, body) as Promise<OrgNode>

export const deleteOrg = (id: number) => http.delete(`/admin/orgs/${id}`) as Promise<void>

export const listOrgMembers = (id: number, params: { keyword?: string; page: number; size: number }) =>
  http.get(`/admin/orgs/${id}/members`, {
    params: { keyword: params.keyword || undefined, page: params.page, size: params.size }
  }) as Promise<PageResult<OrgMember>>

/** 把树拍平，便于下拉与按 id 查找 */
export function flattenOrgs(nodes: OrgNode[], out: OrgNode[] = []): OrgNode[] {
  for (const n of nodes) {
    out.push(n)
    if (n.children?.length) flattenOrgs(n.children, out)
  }
  return out
}
