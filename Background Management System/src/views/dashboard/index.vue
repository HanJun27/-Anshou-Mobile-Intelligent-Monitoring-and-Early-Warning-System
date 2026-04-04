<template>
  <div class="dashboard-container">
    <!-- 统计卡片 -->
    <a-row :gutter="16" class="stats-cards">
      <a-col :span="6">
        <a-card hoverable class="stat-card">
          <a-statistic 
            title="总用户数" 
            :value="stats.totalUsers"
            :value-style="{ color: 'var(--primary-color)' }"
          >
            <template #prefix>
              <span class="stat-icon">👥</span>
            </template>
            <template #suffix>
              <span class="stat-suffix">人</span>
            </template>
          </a-statistic>
          <div class="stat-footer">
            <span>今日活跃：{{ stats.activeToday }}</span>
            <span class="stat-rate">活跃率 {{ Math.round((stats.activeToday / stats.totalUsers) * 100) }}%</span>
          </div>
        </a-card>
      </a-col>
      
      <a-col :span="6">
        <a-card hoverable class="stat-card warning-card">
          <a-statistic 
            title="待处理警报" 
            :value="stats.pendingAlerts"
            :value-style="{ color: 'var(--error-color)' }"
          >
            <template #prefix>
              <span class="stat-icon">⚠️</span>
            </template>
          </a-statistic>
          <div class="stat-footer">
            <span>需要立即关注</span>
          </div>
        </a-card>
      </a-col>
      
      <a-col :span="6">
        <a-card hoverable class="stat-card success-card">
          <a-statistic 
            title="警报处理率" 
            :value="stats.successRate"
            :precision="1"
            :value-style="{ color: 'var(--success-color)' }"
            suffix="%"
          >
            <template #prefix>
              <span class="stat-icon">✅</span>
            </template>
          </a-statistic>
          <div class="stat-footer">
            <span>已处理：{{ stats.processedAlerts }}/{{ stats.totalAlerts }}</span>
          </div>
        </a-card>
      </a-col>
      
      <a-col :span="6">
        <a-card hoverable class="stat-card">
          <a-statistic 
            title="本月警报总数" 
            :value="stats.totalAlerts"
            :value-style="{ color: 'var(--info-color)' }"
          >
            <template #prefix>
              <span class="stat-icon">📊</span>
            </template>
          </a-statistic>
          <div class="stat-footer">
            <span>平均每天 {{ Math.round(stats.totalAlerts / 30) }} 起</span>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="16" class="charts-section">
      <a-col :span="16">
        <a-card title="警报趋势分析" :bordered="false">
          <template #extra>
            <a-space>
              <a-radio-group v-model:value="trendPeriod" size="small" @change="loadTrendData">
                <a-radio-button value="7">近 7 天</a-radio-button>
                <a-radio-button value="30">近 30 天</a-radio-button>
                <a-radio-button value="90">近 90 天</a-radio-button>
              </a-radio-group>
            </a-space>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </a-card>
      </a-col>
      
      <a-col :span="8">
        <a-card title="用户状态分布" :bordered="false">
          <div ref="statusChartRef" class="chart-container small"></div>
          <div class="status-legend">
            <div class="legend-item safe">
              <span class="legend-dot"></span>
              <span>安全 ({{ statusDist.safe }}人)</span>
            </div>
            <div class="legend-item warning">
              <span class="legend-dot"></span>
              <span>注意 ({{ statusDist.warning }}人)</span>
            </div>
            <div class="legend-item danger">
              <span class="legend-dot"></span>
              <span>危险 ({{ statusDist.danger }}人)</span>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 用户卡片和警报列表 -->
    <a-row :gutter="16" class="bottom-section">
      <a-col :span="12">
        <a-card title="重点关注用户" :bordered="false">
          <template #extra>
            <a href="/users" class="view-all">查看全部</a>
          </template>
          <a-list
            item-layout="horizontal"
            :data-source="focusUsers"
          >
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta :description="item.description">
                  <template #title>
                    <a-space>
                      <a-badge :status="item.status" :text="item.name" />
                      <a-tag :color="item.typeColor">{{ item.type }}</a-tag>
                    </a-space>
                  </template>
                  <template #avatar>
                    <a-avatar :style="{ backgroundColor: item.avatarColor }">
                      {{ item.name.charAt(0) }}
                    </a-avatar>
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>
      
      <a-col :span="12">
        <a-card title="实时活动" :bordered="false">
          <template #extra>
            <a href="/alerts/realtime" class="view-all">查看全部</a>
          </template>
          <a-timeline>
            <a-timeline-item 
              v-for="activity in recentActivities" 
              :key="activity.id"
              :color="activity.status === 'alert' ? 'red' : activity.status === 'warning' ? 'orange' : 'blue'"
            >
              <template #dot>
                <span v-if="activity.status === 'alert'" style="font-size: 16px;">⚠️</span>
                <span v-else-if="activity.status === 'warning'" style="font-size: 16px;">❗</span>
                <span v-else style="font-size: 16px;">✓</span>
              </template>
              <div class="activity-item">
                <div class="activity-content">{{ activity.action }}</div>
                <div class="activity-time">{{ formatTime(activity.timestamp) }}</div>
              </div>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboardStats, getAlertTrend, getUserStatusDistribution, getFocusUsers, getRecentActivities } from '@/api/dashboard'
import type { DashboardStats, AlertTrend, UserStatusDistribution, RecentActivity } from '@/api/dashboard'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import dayjs from 'dayjs'

// 统计数据
const stats = ref<DashboardStats>({
  totalUsers: 0,
  activeToday: 0,
  pendingAlerts: 0,
  successRate: 0,
  totalAlerts: 0,
  processedAlerts: 0
})

// 趋势图表
const trendPeriod = ref('7')
const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
const trendData = ref<AlertTrend[]>([])

// 状态分布
const statusChartRef = ref<HTMLElement>()
let statusChart: echarts.ECharts | null = null
const statusDist = ref<UserStatusDistribution>({
  safe: 0,
  warning: 0,
  danger: 0
})

// 重点关注用户
const focusUsers = ref<any[]>([])

// 最近活动
const recentActivities = ref<RecentActivity[]>([])

// 加载统计数据
const loadStats = async () => {
  try {
    const res = await getDashboardStats() as any
    stats.value = res
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 加载趋势数据
const loadTrendData = async () => {
  try {
    const endDate = dayjs().format('YYYY-MM-DD')
    const startDate = dayjs().subtract(parseInt(trendPeriod.value), 'day').format('YYYY-MM-DD')
    
    const res = await getAlertTrend({ startDate, endDate }) as any
    const data = res
    trendData.value = data
    
    // 更新图表
    if (trendChart) {
      trendChart.setOption({
        xAxis: {
          data: data.map((item: AlertTrend) => dayjs(item.date).format('MM/DD'))
        },
        series: [
          { name: '签到警报', data: data.map((item: AlertTrend) => item.checkin) },
          { name: '睡眠警报', data: data.map((item: AlertTrend) => item.sleep) },
          { name: '使用时长', data: data.map((item: AlertTrend) => item.usage) },
          { name: '步数警报', data: data.map((item: AlertTrend) => item.step) }
        ]
      })
    }
  } catch (error) {
    console.error('加载趋势数据失败:', error)
  }
}

// 加载状态分布
const loadStatusDistribution = async () => {
  try {
    const res = await getUserStatusDistribution() as any
    statusDist.value = res
    
    // ... existing code ...
  } catch (error) {
    console.error('加载状态分布失败:', error)
  }
}

// 加载重点关注用户
const loadFocusUsers = async () => {
  try {
    const res = await getFocusUsers() as any
    focusUsers.value = res
  } catch (error) {
    console.error('加载重点关注用户失败:', error)
  }
}

// 加载最近活动
const loadRecentActivities = async () => {
  try {
    const res = await getRecentActivities(5) as any
    recentActivities.value = res
  } catch (error) {
    console.error('加载最近活动失败:', error)
  }
}

// 初始化趋势图表
const initTrendChart = () => {
  if (!trendChartRef.value) return
  
  trendChart = echarts.init(trendChartRef.value)
  
  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['签到警报', '睡眠警报', '使用时长', '步数警报'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: []
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '签到警报',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#1890ff' }
      },
      {
        name: '睡眠警报',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#722ed1' }
      },
      {
        name: '使用时长',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#faad14' }
      },
      {
        name: '步数警报',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#f5222d' }
      }
    ]
  }
  
  trendChart.setOption(option)
  
  window.addEventListener('resize', () => {
    trendChart?.resize()
  })
}

// 初始化状态分布图
const initStatusChart = () => {
  if (!statusChartRef.value) return
  
  statusChart = echarts.init(statusChartRef.value)
  
  const option: EChartsOption = {
    tooltip: {
      trigger: 'item'
    },
    series: [
      {
        name: '用户状态',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold'
          }
        },
        data: [
          { value: 0, name: '安全', itemStyle: { color: '#52c41a' } },
          { value: 0, name: '注意', itemStyle: { color: '#faad14' } },
          { value: 0, name: '危险', itemStyle: { color: '#ff4d4f' } }
        ]
      }
    ]
  }
  
  statusChart.setOption(option)
  
  window.addEventListener('resize', () => {
    statusChart?.resize()
  })
}

// 格式化时间
const formatTime = (timestamp: number) => {
  return dayjs(timestamp).format('MM/DD HH:mm')
}

onMounted(() => {
  initTrendChart()
  initStatusChart()
  loadStats()
  loadTrendData()
  loadStatusDistribution()
  loadFocusUsers()
  loadRecentActivities()
})
</script>

<style scoped lang="less">
.dashboard-container {
  padding: 24px;
}

.stats-cards {
  margin-bottom: 24px;
  
  .stat-card {
    border-radius: 8px;
    
    .stat-icon {
      font-size: 24px;
      margin-right: 8px;
    }
    
    .stat-suffix {
      font-size: 14px;
      margin-left: 4px;
    }
    
    .stat-footer {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--border-color);
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--text-secondary);
      
      .stat-rate {
        color: var(--success-color);
        font-weight: 500;
      }
    }
    
    &.warning-card .stat-footer {
      .stat-rate {
        color: var(--error-color);
      }
    }
  }
}

.charts-section {
  margin-bottom: 24px;
  
  .chart-container {
    height: 320px;
    
    &.small {
      height: 250px;
    }
  }
  
  .status-legend {
    display: flex;
    justify-content: center;
    gap: 24px;
    margin-top: 16px;
    
    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      
      .legend-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        
        &.safe { background: #52c41a; }
        &.warning { background: #faad14; }
        &.danger { background: #ff4d4f; }
      }
    }
  }
}

.bottom-section {
  .view-all {
    color: var(--primary-color);
    font-size: 14px;
    
    &:hover {
      text-decoration: underline;
    }
  }
  
  .activity-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .activity-content {
      flex: 1;
    }
    
    .activity-time {
      font-size: 12px;
      color: var(--text-secondary);
      margin-left: 16px;
    }
  }
}
</style>
