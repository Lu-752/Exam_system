import { http } from './http'

// ---------- 大纲 ----------

export interface Outline {
  id: number
  name: string
  category?: string
  version?: string
  content?: string
  status: 'ACTIVE' | 'DISABLED'
  sortNo: number
  createdAt?: string
  updatedAt?: string
  /** 关联题库数量（大纲 → 题库联动） */
  bankCount?: number
}

export type OutlinePayload = {
  name: string
  category?: string
  version?: string
  content?: string
  status?: string
  sortNo?: number
}

export const listOutlines = (params?: { keyword?: string; status?: string }) =>
  http.get('/admin/outlines', { params }) as Promise<Outline[]>
export const createOutline = (body: OutlinePayload) => http.post('/admin/outlines', body) as Promise<Outline>
export const updateOutline = (id: number, body: OutlinePayload) =>
  http.put(`/admin/outlines/${id}`, body) as Promise<Outline>
export const deleteOutline = (id: number) => http.delete(`/admin/outlines/${id}`) as Promise<void>

// ---------- 字典 ----------

export interface DictItem {
  id: number
  dictType: string
  code: string
  label: string
  value?: string
  sortNo: number
  status: 'ACTIVE' | 'DISABLED'
  remark?: string
  createdAt?: string
}

export type DictPayload = {
  dictType: string
  code: string
  label: string
  value?: string
  sortNo?: number
  status?: string
  remark?: string
}

export const listDictTypes = () => http.get('/admin/dicts/types') as Promise<string[]>
export const listDictItems = (params?: { dictType?: string; keyword?: string }) =>
  http.get('/admin/dicts', { params }) as Promise<DictItem[]>
export const createDict = (body: DictPayload) => http.post('/admin/dicts', body) as Promise<DictItem>
export const updateDict = (id: number, body: DictPayload) => http.put(`/admin/dicts/${id}`, body) as Promise<DictItem>
export const deleteDict = (id: number) => http.delete(`/admin/dicts/${id}`) as Promise<void>

// ---------- 系统参数 ----------

export interface SysConfig {
  id: number
  paramKey: string
  paramValue?: string
  remark?: string
  updatedAt?: string
}

export const listConfigs = (keyword?: string) =>
  http.get('/admin/configs', { params: { keyword } }) as Promise<SysConfig[]>
export const createConfig = (body: { paramKey: string; paramValue?: string; remark?: string }) =>
  http.post('/admin/configs', body) as Promise<SysConfig>
export const updateConfig = (id: number, body: { paramKey: string; paramValue?: string; remark?: string }) =>
  http.put(`/admin/configs/${id}`, body) as Promise<SysConfig>
export const deleteConfig = (id: number) => http.delete(`/admin/configs/${id}`) as Promise<void>
