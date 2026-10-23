import { http } from './http'
import type { MenuNode, UserInfo } from './types'

export interface BackendLoginResult {
  token: string
  user: UserInfo
}

/** 真实登录：POST /api/v1/auth/login */
export function login(username: string, password: string): Promise<BackendLoginResult> {
  return http.post('/auth/login', { username, password })
}

/** 当前登录用户 */
export function me(): Promise<UserInfo> {
  return http.get('/auth/me')
}

/** 当前用户可见菜单树（后端按角色权限下发） */
export function menus(): Promise<MenuNode[]> {
  return http.get('/auth/menus')
}

export function logout(): Promise<void> {
  return http.post('/auth/logout')
}
