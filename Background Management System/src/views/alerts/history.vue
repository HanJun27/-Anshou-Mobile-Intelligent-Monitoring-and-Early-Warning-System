<template>
  <div class="alert-history">
    <a-card :bordered="false">
      <!-- 筛选条件 -->
      <a-form layout="inline" :model="filterForm" style="margin-bottom: 16px">
        <a-form-item label="警报类型">
          <a-select v-model:value="filterForm.type" placeholder="全部" style="width: 150px" allow-clear>
            <a-select-option value="跌倒警报">跌倒警报</a-select-option>
            <a-select-option value="烟雾报警">烟雾报警</a-select-option>
            <a-select-option value="SOS 紧急呼叫">SOS 紧急呼叫</a-select-option>
            <a-select-option value="长时间未活动">长时间未活动</a-select-option>
            <a-select-option value="设备离线">设备离线</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="警报级别">
          <a-select v-model:value="filterForm.level" placeholder="全部" style="width: 120px" allow-clear>
            <a-select-option value="3">紧急</a-select-option>
            <a-select-option value="2">警告</a-select-option>
            <a-select-option value="1">提示</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="处理状态">
          <a-select v-model:value="filterForm.status" placeholder="全部" style="width: 120px" allow-clear>
            <a-select-option value="0">未处理</a-select-option>
            <a-select-option value="1">已处理</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="时间范围">
          <a-range-picker v-model:value="filterForm.dateRange" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary">查询</a-button>
          <a-button style="margin-left: 8px">重置</a-button>
          <a-button style="margin-left: 8px">导出</a-button>
        </a-form-item>
      </a-form>

      <!-- 统计卡片 -->
      <a-row :gutter="16" style="margin-bottom: 16px">
        <a-col :span="6">
          <a-card size="small">
            <a-statistic title="今日警报总数" :value="28" />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small">
            <a-statistic title="紧急警报" :value="3" value-style="color: #ff4d4f" />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small">
            <a-statistic title="待处理" :value="5" value-style="color: #fa8c16" />
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small">
            <a-statistic title="已处理" :value="23" value-style="color: #52c41a" />
          </a-card>
        </a-col>
      </a-row>

      <!-- 表格 -->
      <a-table 
        :columns="columns" 
        :data-source="dataSource"
        :pagination="{ pageSize: 10, showTotal: (total: number) => `共 ${total} 条` }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'level'">
            <a-tag :color="getLevelColor(record.level)">
              {{ getLevelText(record.level) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-badge :status="record.status === 0 ? 'error' : 'success'" :text="record.status === 0 ? '未处理' : '已处理'" />
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small">详情</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const filterForm = ref({
  type: '',
  level: '',
  status: '',
  dateRange: []
})

const columns = [
  {
    title: '警报 ID',
    dataIndex: 'id',
    key: 'id',
    width: 100
  },
  {
    title: '警报类型',
    dataIndex: 'type',
    key: 'type',
    width: 150
  },
  {
    title: '警报级别',
    dataIndex: 'level',
    key: 'level',
    width: 100
  },
  {
    title: '用户',
    dataIndex: 'userName',
    key: 'userName'
  },
  {
    title: '发生时间',
    dataIndex: 'time',
    key: 'time',
    width: 180
  },
  {
    title: '处理时间',
    dataIndex: 'processTime',
    key: 'processTime',
    width: 180
  },
  {
    title: '处理人',
    dataIndex: 'processor',
    key: 'processor'
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100
  },
  {
    title: '操作',
    key: 'action',
    width: 80,
    fixed: 'right' as const
  }
]

const dataSource = ref([
  {
    id: 'A001',
    type: '跌倒警报',
    level: 3,
    userName: '张三',
    time: '2024-03-19 10:23:45',
    processTime: '2024-03-19 10:35:00',
    processor: '管理员 A',
    status: 1
  },
  {
    id: 'A002',
    type: '烟雾报警',
    level: 3,
    userName: '李四',
    time: '2024-03-19 09:45:12',
    processTime: '2024-03-19 09:50:00',
    processor: '管理员 B',
    status: 1
  },
  {
    id: 'A003',
    type: '长时间未活动',
    level: 2,
    userName: '王五',
    time: '2024-03-18 20:00:00',
    processTime: '2024-03-18 20:30:00',
    processor: '管理员 A',
    status: 1
  },
  {
    id: 'A004',
    type: 'SOS 紧急呼叫',
    level: 3,
    userName: '赵六',
    time: '2024-03-18 15:30:00',
    processTime: '2024-03-18 15:32:00',
    processor: '管理员 C',
    status: 1
  },
  {
    id: 'A005',
    type: '设备离线',
    level: 1,
    userName: '孙七',
    time: '2024-03-18 10:00:00',
    processTime: '2024-03-18 14:00:00',
    processor: '系统自动恢复',
    status: 1
  }
])

const getLevelColor = (level: number) => {
  const colors: Record<number, string> = {
    1: 'blue',
    2: 'orange',
    3: 'red'
  }
  return colors[level] || 'default'
}

const getLevelText = (level: number) => {
  const texts: Record<number, string> = {
    1: '提示',
    2: '警告',
    3: '紧急'
  }
  return texts[level] || '未知'
}
</script>

<style scoped lang="less">
.alert-history {
  .ant-card {
    margin-bottom: 16px;
  }
}
</style>
