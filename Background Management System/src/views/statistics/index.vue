<template>
  <div class="statistics-container">
    <a-card :bordered="false">
      <template #title>
        <div class="page-header">
          <div>
            <h2 style="margin: 0;">数据统计分析</h2>
            <p style="color: var(--text-secondary); margin: 8px 0 0;">独居老人安全监测平台数据统计分析</p>
          </div>
          <a-space>
            <a-button @click="exportReport" icon="📥">导出报表</a-button>
            <a-date-picker 
              v-model:value="dateRange" 
              range 
              style="width: 240px"
              @change="loadAllData"
            />
          </a-space>
        </div>
      </template>

      <!-- 时间选择 -->
      <div class="filter-section">
        <a-radio-group v-model:value="timeRange" button-style="solid" @change="loadAllData">
          <a-radio-button value="week">最近 7 天</a-radio-button>
          <a-radio-button value="month">最近 30 天</a-radio-button>
          <a-radio-button value="quarter">最近 90 天</a-radio-button>
          <a-radio-button value="year">最近 1 年</a-radio-button>
        </a-radio-group>
      </div>

      <!-- 主要图表区域 -->
      <a-row :gutter="16" class="chart-row">
        <a-col :span="16">
          <a-card title="警报类型趋势分析" size="small">
            <div ref="alertTrendChartRef" class="chart-container"></div>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card title="警报类型分布" size="small">
            <div ref="alertTypePieChartRef" class="chart-container small"></div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="16" class="chart-row">
        <a-col :span="12">
          <a-card title="用户活跃度趋势" size="small">
            <div ref="activityChartRef" class="chart-container medium"></div>
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card title="设备在线率统计" size="small">
            <div ref="onlineRateChartRef" class="chart-container medium"></div>
          </a-card>
        </a-col>
      </a-row>

      <!-- 统计指标卡片 -->
      <a-divider orientation="left">关键指标</a-divider>
      
      <a-row :gutter="16" class="metrics-row">
        <a-col :span="6">
          <a-card class="metric-card">
            <a-statistic 
              title="平均响应时间" 
              :value="metrics.avgResponseTime"
              :precision="1"
              suffix="分钟"
              :value-style="{ color: '#1890ff' }"
            >
              <template #prefix>⏱️</template>
            </a-statistic>
            <a-progress 
              :percent="metrics.processRate" 
              :stroke-color="{ '100%': '#52c41a' }"
              status="success"
            >
              <template #format>
                <span style="color: #52c41a; font-size: 12px;">处理及时率</span>
              </template>
            </a-progress>
          </a-card>
        </a-col>
        
        <a-col :span="6">
          <a-card class="metric-card">
            <a-statistic 
              title="设备在线率" 
              :value="metrics.onlineRate"
              :precision="1"
              suffix="%"
              :value-style="{ color: '#52c41a' }"
            >
              <template #prefix>📱</template>
            </a-statistic>
            <a-descriptions size="small" :column="2" style="margin-top: 16px">
              <a-descriptions-item label="在线">{{ metrics.onlineDevices }}</a-descriptions-item>
              <a-descriptions-item label="离线">{{ metrics.offlineDevices }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        
        <a-col :span="6">
          <a-card class="metric-card">
            <a-statistic 
              title="用户活跃度" 
              :value="metrics.activityRate"
              :precision="1"
              suffix="%"
              :value-style="{ color: '#722ed1' }"
            >
              <template #prefix>👥</template>
            </a-statistic>
            <a-descriptions size="small" :column="2" style="margin-top: 16px">
              <a-descriptions-item label="活跃">{{ metrics.activeUsers }}</a-descriptions-item>
              <a-descriptions-item label="总计">{{ metrics.totalUsers }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        
        <a-col :span="6">
          <a-card class="metric-card">
            <a-statistic 
              title="警报总数" 
              :value="metrics.totalAlerts"
              :value-style="{ color: '#faad14' }"
            >
              <template #prefix>🔔</template>
            </a-statistic>
            <a-descriptions size="small" :column="2" style="margin-top: 16px">
              <a-descriptions-item label="紧急">{{ metrics.urgentAlerts }}</a-descriptions-item>
              <a-descriptions-item label="一般">{{ metrics.normalAlerts }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
      </a-row>

      <!-- 详细数据表格 -->
      <a-divider orientation="left">月度统计详情</a-divider>
      
      <a-table 
        :columns="columns" 
        :data-source="tableData"
        :pagination="{ pageSize: 10, showSizeChanger: true }"
        :loading="tableLoading"
        row-key="month"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'processRate' || column.key === 'onlineRate'">
            <a-tag :color="getRateColor(record[column.key])">
              {{ record[column.key] }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import dayjs, { Dayjs } from 'dayjs'

// 时间范围
const timeRange = ref('month')
const dateRange = ref<[Dayjs, Dayjs]>()

// 图表实例
let alertTrendChart: echarts.ECharts | null = null
let alertTypePieChart: echarts.ECharts | null = null
let activityChart: echarts.ECharts | null = null
let onlineRateChart: echarts.ECharts | null = null

const alertTrendChartRef = ref<HTMLElement>()
const alertTypePieChartRef = ref<HTMLElement>()
const activityChartRef = ref<HTMLElement>()
const onlineRateChartRef = ref<HTMLElement>()

// 指标数据
const metrics = ref({
  avgResponseTime: 3.2,
  processRate: 95.8,
  onlineRate: 96.5,
  onlineDevices: 124,
  offlineDevices: 4,
  activeUsers: 86,
  totalUsers: 98,
  activityRate: 87.8,
  totalAlerts: 1234,
  urgentAlerts: 45,
  normalAlerts: 1189
})

// 表格数据
const tableLoading = ref(false)
const columns = [
  { title: '月份', dataIndex: 'month', key: 'month', width: 120 },
  { title: '新增用户', dataIndex: 'newUsers', key: 'newUsers', width: 100 },
  { title: '活跃用户', dataIndex: 'activeUsers', key: 'activeUsers', width: 100 },
  { title: '警报总数', dataIndex: 'totalAlerts', key: 'totalAlerts', width: 100 },
  { title: '紧急警报', dataIndex: 'urgentAlerts', key: 'urgentAlerts', width: 100 },
  { title: '处理及时率', dataIndex: 'processRate', key: 'processRate', width: 120 },
  { title: '设备在线率', dataIndex: 'onlineRate', key: 'onlineRate', width: 120 },
  { 
    title: '趋势', 
    key: 'trend', 
    width: 100,
    customRender: () => '📈'
  }
]

const tableData = ref([
  { month: '2026-03', newUsers: 12, activeUsers: 86, totalAlerts: 156, urgentAlerts: 8, processRate: '96.5%', onlineRate: '98.2%' },
  { month: '2026-02', newUsers: 15, activeUsers: 82, totalAlerts: 142, urgentAlerts: 6, processRate: '95.8%', onlineRate: '97.5%' },
  { month: '2026-01', newUsers: 18, activeUsers: 78, totalAlerts: 168, urgentAlerts: 10, processRate: '94.2%', onlineRate: '96.8%' },
  { month: '2025-12', newUsers: 20, activeUsers: 75, totalAlerts: 185, urgentAlerts: 12, processRate: '93.5%', onlineRate: '95.9%' },
  { month: '2025-11', newUsers: 16, activeUsers: 70, totalAlerts: 152, urgentAlerts: 7, processRate: '95.1%', onlineRate: '97.2%' },
  { month: '2025-10', newUsers: 14, activeUsers: 68, totalAlerts: 138, urgentAlerts: 5, processRate: '96.8%', onlineRate: '98.5%' }
])

// 加载所有数据
const loadAllData = () => {
  loadMetrics()
  loadCharts()
}

// 加载指标数据
const loadMetrics = () => {
  // 模拟从 API 加载数据
  console.log('加载统计数据，时间范围:', timeRange.value)
}

// 加载所有图表
const loadCharts = () => {
  loadAlertTrendChart()
  loadAlertTypePieChart()
  loadActivityChart()
  loadOnlineRateChart()
}

// 加载警报趋势图
const loadAlertTrendChart = () => {
  if (!alertTrendChartRef.value || !alertTrendChart) return
  
  const days = timeRange.value === 'week' ? 7 : timeRange.value === 'month' ? 30 : timeRange.value === 'quarter' ? 90 : 365
  
  // 生成模拟数据
  const dates: string[] = []
  const checkinData: number[] = []
  const sleepData: number[] = []
  const usageData: number[] = []
  const stepData: number[] = []
  
  for (let i = days; i >= 0; i--) {
    const date = dayjs().subtract(i, 'day')
    dates.push(date.format('MM/DD'))
    checkinData.push(Math.floor(Math.random() * 20))
    sleepData.push(Math.floor(Math.random() * 15))
    usageData.push(Math.floor(Math.random() * 25))
    stepData.push(Math.floor(Math.random() * 30))
  }
  
  alertTrendChart.setOption<EChartsOption>({
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
      data: dates,
      axisLabel: {
        interval: days > 30 ? Math.floor(days / 15) : 0,
        rotate: days > 30 ? 45 : 0
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '签到警报',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#1890ff' },
        data: checkinData
      },
      {
        name: '睡眠警报',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#722ed1' },
        data: sleepData
      },
      {
        name: '使用时长',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#faad14' },
        data: usageData
      },
      {
        name: '步数警报',
        type: 'bar',
        stack: 'total',
        itemStyle: { color: '#f5222d' },
        data: stepData
      }
    ]
  })
}

// 加载警报类型饼图
const loadAlertTypePieChart = () => {
  if (!alertTypePieChartRef.value || !alertTypePieChart) return
  
  alertTypePieChart.setOption<EChartsOption>({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center'
    },
    series: [
      {
        name: '警报类型',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
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
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: [
          { value: 156, name: '签到警报', itemStyle: { color: '#1890ff' } },
          { value: 120, name: '睡眠警报', itemStyle: { color: '#722ed1' } },
          { value: 200, name: '使用时长', itemStyle: { color: '#faad14' } },
          { value: 240, name: '步数警报', itemStyle: { color: '#f5222d' } }
        ]
      }
    ]
  })
}

// 加载活跃度图表
const loadActivityChart = () => {
  if (!activityChartRef.value || !activityChart) return
  
  const dates: string[] = []
  const activeData: number[] = []
  const totalData: number[] = []
  
  for (let i = 6; i >= 0; i--) {
    const date = dayjs().subtract(i, 'day')
    dates.push(date.format('MM/DD'))
    const total = 90 + Math.floor(Math.random() * 20)
    totalData.push(total)
    activeData.push(Math.floor(total * (0.7 + Math.random() * 0.25)))
  }
  
  activityChart.setOption<EChartsOption>({
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['活跃用户', '总用户'],
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
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '活跃用户',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#722ed1' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(114, 46, 209, 0.3)' },
            { offset: 1, color: 'rgba(114, 46, 209, 0.05)' }
          ])
        },
        data: activeData
      },
      {
        name: '总用户',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#1890ff' },
        data: totalData
      }
    ]
  })
}

// 加载在线率图表
const loadOnlineRateChart = () => {
  if (!onlineRateChartRef.value || !onlineRateChart) return
  
  const hours: string[] = []
  const rateData: number[] = []
  
  for (let i = 23; i >= 0; i--) {
    hours.push(`${i.toString().padStart(2, '0')}:00`)
    rateData.push(92 + Math.random() * 8)
  }
  
  onlineRateChart.setOption<EChartsOption>({
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLabel: {
        interval: 3
      }
    },
    yAxis: {
      type: 'value',
      min: 90,
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    series: [
      {
        name: '在线率',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#52c41a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(82, 196, 26, 0.3)' },
            { offset: 1, color: 'rgba(82, 196, 26, 0.05)' }
          ])
        },
        data: rateData
      }
    ]
  })
}

// 获取比率颜色
const getRateColor = (rate: string) => {
  const num = parseFloat(rate)
  if (num >= 95) return 'green'
  if (num >= 90) return 'blue'
  if (num >= 85) return 'orange'
  return 'red'
}

// 导出报表
const exportReport = () => {
  console.log('导出报表')
  // TODO: 实现导出功能
}

// 初始化图表
const initCharts = () => {
  if (alertTrendChartRef.value) {
    alertTrendChart = echarts.init(alertTrendChartRef.value)
  }
  if (alertTypePieChartRef.value) {
    alertTypePieChart = echarts.init(alertTypePieChartRef.value)
  }
  if (activityChartRef.value) {
    activityChart = echarts.init(activityChartRef.value)
  }
  if (onlineRateChartRef.value) {
    onlineRateChart = echarts.init(onlineRateChartRef.value)
  }
  
  window.addEventListener('resize', () => {
    alertTrendChart?.resize()
    alertTypePieChart?.resize()
    activityChart?.resize()
    onlineRateChart?.resize()
  })
}

onMounted(() => {
  initCharts()
  loadAllData()
})
</script>

<style scoped lang="less">
.statistics-container {
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-section {
  margin-bottom: 24px;
}

.chart-row {
  margin-bottom: 16px;
  
  .chart-container {
    height: 320px;
    
    &.small {
      height: 280px;
    }
    
    &.medium {
      height: 280px;
    }
  }
}

.metrics-row {
  margin-bottom: 24px;
  
  .metric-card {
    border-radius: 8px;
    text-align: center;
  }
}
</style>
