<template>
  <div class="realtime-alerts">
    <a-card :bordered="false">
      <a-alert 
        message="实时警报监控中" 
        description="当前有 3 条未处理的警报，请及时处理" 
        type="warning" 
        show-icon
        style="margin-bottom: 16px"
      >
        <template #icon>🔔</template>
      </a-alert>

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
          <template v-if="column.key === 'type'">
            <span :style="{ color: record.type === '跌倒警报' ? '#ff4d4f' : '' }">
              {{ record.type }}
            </span>
          </template>
          <template v-if="column.key === 'status'">
            <a-badge :status="record.status === 0 ? 'error' : 'success'" :text="record.status === 0 ? '未处理' : '已处理'" />
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleProcess(record)" :disabled="record.status !== 0">
                处理
              </a-button>
              <a-button type="link" size="small">详情</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

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
    title: '设备位置',
    dataIndex: 'location',
    key: 'location'
  },
  {
    title: '发生时间',
    dataIndex: 'time',
    key: 'time',
    width: 180
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
    width: 150,
    fixed: 'right' as const
  }
]

const dataSource = ref([
  {
    id: 'A001',
    type: '跌倒警报',
    level: 3,
    userName: '张三',
    location: '北京市朝阳区 XX 小区 1-101 客厅',
    time: '2024-03-19 10:23:45',
    status: 0
  },
  {
    id: 'A002',
    type: '烟雾报警',
    level: 3,
    userName: '李四',
    location: '北京市海淀区 XX 小区 2-202 厨房',
    time: '2024-03-19 09:45:12',
    status: 0
  },
  {
    id: 'A003',
    type: '长时间未活动',
    level: 2,
    userName: '王五',
    location: '上海市浦东新区 XX 小区 3-303 卧室',
    time: '2024-03-19 08:30:00',
    status: 0
  },
  {
    id: 'A004',
    type: 'SOS 紧急呼叫',
    level: 3,
    userName: '赵六',
    location: '广州市天河区 XX 小区 4-404 卫生间',
    time: '2024-03-19 07:15:30',
    status: 1
  },
  {
    id: 'A005',
    type: '设备离线',
    level: 1,
    userName: '孙七',
    location: '深圳市南山区 XX 小区 5-505',
    time: '2024-03-19 06:00:00',
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

const handleProcess = (record: any) => {
  console.log('处理警报:', record)
}
</script>

<style scoped lang="less">
.realtime-alerts {
  .ant-card {
    margin-bottom: 16px;
  }
}
</style>
