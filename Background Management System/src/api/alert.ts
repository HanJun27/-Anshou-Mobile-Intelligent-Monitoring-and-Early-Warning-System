import request from '@/utils/request'

export interface AlertItem {
  id: number
  userId: number
  userName: string
  type: 'checkin' | 'sleep' | 'usage' | 'step'
  status: 'pending' | 'success' | 'failed' | 'cancelled'
  method: 'sms' | 'email' | 'notification' | 'both'
  content: string
  timestamp: number
}

export interface AlertStats {
  total: number
  pending: number
  processed: number
  rate: number
}

/**
 * 获取实时警报列表
 */
export function getRealtimeAlerts() {
  return request<AlertItem[]>({
    url: '/alerts/realtime',
    method: 'get'
  })
}

/**
 * 获取警报历史
 */
export function getAlertHistory(params: {
  page: number
  size: number
  type?: string
  status?: string
  startDate?: string
  endDate?: string
}) {
  return request<{ list: AlertItem[]; total: number }>({
    url: '/alerts/history',
    method: 'get',
    params
  })
}

/**
 * 处理警报
 */
export function handleAlert(id: number, action: string) {
  return request({
    url: `/alerts/${id}/handle`,
    method: 'post',
    data: { action }
  })
}

/**
 * 获取警报统计
 */
export function getAlertStats() {
  return request<AlertStats>({
    url: '/alerts/stats',
    method: 'get'
  })
}