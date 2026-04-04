import request from '@/utils/request'

export interface UserInfo {
  id: number
  name: string
  age: number
  phone: string
  address: string
  emergencyContact: {
    name: string
    phone: string
    relation: string
  }
  status: 'safe' | 'warning' | 'danger'
  todayCheckin: boolean
  todaySteps: number
  sleepHours: number
}

/**
 * 获取用户列表
 */
export function getUserList(params: {
  page: number
  size: number
  keyword?: string
  status?: string
}) {
  return request<{ list: UserInfo[]; total: number }>({
    url: '/users',
    method: 'get',
    params
  })
}

/**
 * 获取用户详情
 */
export function getUserDetail(id: number) {
  return request<UserInfo>({
    url: `/users/${id}`,
    method: 'get'
  })
}

/**
 * 添加用户
 */
export function addUser(data: Partial<UserInfo>) {
  return request({
    url: '/users',
    method: 'post',
    data
  })
}

/**
 * 更新用户
 */
export function updateUser(id: number, data: Partial<UserInfo>) {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除用户
 */
export function deleteUser(id: number) {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  })
}
