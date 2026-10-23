import { http } from './http'

export interface RoleOption {
  id: number
  code: string
  name: string
  remark?: string
  userCount: number
  menuCount: number
}

export interface UserRow {
  id: number
  username: string
  name: string
  phone?: string
  email?: string
  status: 'ACTIVE' | 'DISABLED'
  roleIds: number[]
  roleNames: string[]
  lastLoginAt?: string
  createdAt?: string
}

export interface MenuTreeNode {
  id: number
  parentId?: number
  name: string
  path?: string
  perm?: string
  type: 'DIR' | 'MENU' | 'BUTTON'
  children: MenuTreeNode[]
}

// ---------- 用户 ----------
export const listUsers = (keyword?: string) => http.get('/admin/users', { params: { keyword } }) as Promise<UserRow[]>
export const createUser = (body: Record<string, unknown>) => http.post('/admin/users', body) as Promise<UserRow>
export const setUserStatus = (id: number, status: string) =>
  http.put(`/admin/users/${id}/status`, { status }) as Promise<void>
export const resetUserPassword = (id: number) => http.post(`/admin/users/${id}/reset-password`, {}) as Promise<void>
export const setUserRoles = (id: number, roleIds: number[]) =>
  http.put(`/admin/users/${id}/roles`, { roleIds }) as Promise<void>

// ---------- 角色 ----------
export const listRoles = () => http.get('/admin/roles') as Promise<RoleOption[]>
export const getMenuTree = () => http.get('/admin/roles/menu-tree') as Promise<MenuTreeNode[]>
export const getRoleMenus = (roleId: number) =>
  http.get(`/admin/roles/${roleId}/menus`) as Promise<number[]>
export const saveRoleMenus = (roleId: number, menuIds: number[]) =>
  http.put(`/admin/roles/${roleId}/menus`, { menuIds }) as Promise<void>

export interface RolePayload {
  code: string
  name: string
  remark?: string
  menuIds?: number[]
}

export const createRole = (body: RolePayload) => http.post('/admin/roles', body) as Promise<RoleOption>
export const updateRole = (id: number, body: RolePayload) =>
  http.put(`/admin/roles/${id}`, body) as Promise<RoleOption>
export const deleteRole = (id: number) => http.delete(`/admin/roles/${id}`) as Promise<void>
