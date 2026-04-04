import request from '@/utils/request'

export interface DashboardStats {
  totalUsers: number
  activeToday: number
  pendingAlerts: number
  successRate: number
  totalAlerts: number
  processedAlerts: number
}

export interface AlertTrend {
  date: string
  checkin: number
  sleep: number
  usage: number
  step: number
}

export interface UserStatusDistribution {
  safe: number
  warning: number
  danger: number
}

export interface RecentActivity {
  id: number
  userId: number
  userName: string
  type: 'checkin' | 'sleep' | 'usage' | 'step'
  action: string
  timestamp: number
  status: 'normal' | 'warning' | 'alert'
}

/**
 * 获取仪表板统计
 */
export function getDashboardStats() {
  return request<DashboardStats>({
    url: '/dashboard/stats',
    method: 'get'
  })
}

/**
 * 获取警报趋势数据
 */
export function getAlertTrend(params: {
  startDate?: string
  endDate?: string
}) {
  return request<AlertTrend[]>({
    url: '/dashboard/alert-trend',
    method: 'get',
    params
  })
}

/**
 * 获取用户状态分布
 */
export function getUserStatusDistribution() {
  return request<UserStatusDistribution>({
    url: '/dashboard/user-status',
    method: 'get'
  })
}

/**
 * 获取实时活动
 */
export function getRecentActivities(limit?: number) {
  return request<RecentActivity[]>({
    url: '/dashboard/activities',
    method: 'get',
    params: { limit }
  })
}

/**
 * 获取重点关注用户
 */
export function getFocusUsers() {
  return request<any[]>({
    url: '/dashboard/focus-users',
    method: 'get'
  })
}