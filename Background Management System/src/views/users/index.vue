<template>
  <div class="user-management-container">
    <a-card :bordered="false">
      <!-- 统计卡片 -->
      <a-row :gutter="16" class="stats-row" style="margin-bottom: 24px;">
        <a-col :span="6">
          <a-card class="stat-card" size="small">
            <a-statistic 
              title="总用户数" 
              :value="userStats.total"
              :value-style="{ color: '#1890ff' }"
            >
              <template #prefix>👥</template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card" size="small">
            <a-statistic 
              title="在线用户" 
              :value="userStats.online"
              :value-style="{ color: '#52c41a' }"
            >
              <template #prefix>📱</template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card" size="small">
            <a-statistic 
              title="今日签到" 
              :value="userStats.todayCheckin"
              :value-style="{ color: '#722ed1' }"
            >
              <template #prefix>✅</template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card" size="small">
            <a-statistic 
              title="重点关注" 
              :value="userStats.focus"
              :value-style="{ color: '#faad14' }"
            >
              <template #prefix>⚠️</template>
            </a-statistic>
          </a-card>
        </a-col>
      </a-row>

      <!-- 搜索栏 -->
      <div class="filter-section">
        <a-form layout="inline" :model="searchForm">
          <a-form-item label="姓名">
            <a-input 
              v-model:value="searchForm.name" 
              placeholder="请输入姓名" 
              allow-clear
              style="width: 200px"
            />
          </a-form-item>
          <a-form-item label="手机号">
            <a-input 
              v-model:value="searchForm.phone" 
              placeholder="请输入手机号" 
              allow-clear
              style="width: 200px"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select 
              v-model:value="searchForm.status" 
              placeholder="请选择状态"
              allow-clear
              style="width: 150px"
            >
              <a-select-option value="all">全部</a-select-option>
              <a-select-option value="safe">安全</a-select-option>
              <a-select-option value="warning">注意</a-select-option>
              <a-select-option value="danger">危险</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="handleSearch">
                🔍 查询
              </a-button>
              <a-button @click="handleReset">
                🔄 重置
              </a-button>
              <a-button type="primary" @click="handleAdd">
                ➕ 新增用户
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </div>

      <!-- 表格 -->
      <a-table 
        :columns="columns" 
        :data-source="dataSource"
        :pagination="pagination"
        :loading="tableLoading"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <a-space>
              <a-avatar :style="{ backgroundColor: record.avatarColor }">
                {{ record.name.charAt(0) }}
              </a-avatar>
              <span>{{ record.name }}</span>
            </a-space>
          </template>
          
          <template v-if="column.key === 'status'">
            <a-badge :status="getStatusBadge(record.status as string)" />
            <span>{{ getStatusText(record.status as string) }}</span>
          </template>
          
          <template v-if="column.key === 'todayCheckin'">
            <a-tag :color="record.todayCheckin ? 'green' : 'red'">
              {{ record.todayCheckin ? '已签到' : '未签到' }}
            </a-tag>
          </template>
          
          <template v-if="column.key === 'healthScore'">
            <a-progress 
              :percent="record.healthScore" 
              :stroke-color="getHealthColor(record.healthScore)"
              size="small"
              style="width: 100px"
            />
          </template>
          
          <template v-if="column.key === 'lastActiveTime'">
            <span>{{ formatTime(record.lastActiveTime) }}</span>
          </template>
          
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">
                👁️ 详情
              </a-button>
              <a-button type="link" size="small" @click="handleEdit(record)">
                ✏️ 编辑
              </a-button>
              <a-popconfirm 
                title="确定删除该用户吗？" 
                ok-text="确定" 
                cancel-text="取消" 
                @confirm="handleDelete(record)"
              >
                <a-button type="link" size="small" danger>
                  🗑️ 删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新增/编辑用户对话框 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      width="800px"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
      >
        <a-form-item label="姓名" name="name" :rules="[{ required: true, message: '请输入姓名' }]">
          <a-input v-model:value="formData.name" placeholder="请输入姓名" />
        </a-form-item>
        
        <a-form-item label="年龄" name="age" :rules="[{ required: true, message: '请输入年龄' }]">
          <a-input-number v-model:value="formData.age" :min="1" :max="150" style="width: 100%" />
        </a-form-item>
        
        <a-form-item label="手机号" name="phone" :rules="[{ required: true, message: '请输入手机号' }]">
          <a-input v-model:value="formData.phone" placeholder="请输入手机号" />
        </a-form-item>
        
        <a-form-item label="设备编号" name="deviceNo">
          <a-input v-model:value="formData.deviceNo" placeholder="请输入设备编号" />
        </a-form-item>
        
        <a-form-item label="地址" name="address">
          <a-input v-model:value="formData.address" placeholder="请输入地址" />
        </a-form-item>
        
        <a-form-item label="紧急联系人" name="emergencyContact">
          <a-input v-model:value="formData.emergencyContact" placeholder="请输入紧急联系人姓名" />
        </a-form-item>
        
        <a-form-item label="紧急联系人电话" name="emergencyPhone">
          <a-input v-model:value="formData.emergencyPhone" placeholder="请输入紧急联系人电话" />
        </a-form-item>
        
        <a-form-item label="备注" name="remark">
          <a-textarea v-model:value="formData.remark" :rows="3" placeholder="请输入备注信息" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { TablePaginationConfig } from 'ant-design-vue'

const router = useRouter()

// 用户统计
const userStats = ref({
  total: 128,
  online: 86,
  todayCheckin: 92,
  focus: 12
})

// 搜索表单
const searchForm = ref({
  name: '',
  phone: '',
  status: ''
})

// 表格列配置
const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 80,
    fixed: 'left' as const
  },
  {
    title: '姓名',
    dataIndex: 'name',
    key: 'name',
    width: 150
  },
  {
    title: '年龄',
    dataIndex: 'age',
    key: 'age',
    width: 80,
    sorter: (a: any, b: any) => a.age - b.age
  },
  {
    title: '手机号',
    dataIndex: 'phone',
    key: 'phone',
    width: 150
  },
  {
    title: '设备编号',
    dataIndex: 'deviceNo',
    key: 'deviceNo',
    width: 120
  },
  {
    title: '地址',
    dataIndex: 'address',
    key: 'address',
    ellipsis: true,
    width: 250
  },
  {
    title: '今日签到',
    dataIndex: 'todayCheckin',
    key: 'todayCheckin',
    width: 100,
    filters: [
      { text: '已签到', value: true },
      { text: '未签到', value: false }
    ],
    onFilter: (value: any, record: any) => record.todayCheckin === value
  },
  {
    title: '健康评分',
    dataIndex: 'healthScore',
    key: 'healthScore',
    width: 150,
    sorter: (a: any, b: any) => a.healthScore - b.healthScore
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
    filters: [
      { text: '安全', value: 'safe' },
      { text: '注意', value: 'warning' },
      { text: '危险', value: 'danger' }
    ],
    onFilter: (value: any, record: any) => record.status === value
  },
  {
    title: '最后活跃',
    dataIndex: 'lastActiveTime',
    key: 'lastActiveTime',
    width: 180,
    sorter: (a: any, b: any) => a.lastActiveTime - b.lastActiveTime
  },
  {
    title: '操作',
    key: 'action',
    width: 220,
    fixed: 'right' as const
  }
]

// 表格数据
const tableLoading = ref(false)
const dataSource = ref([
  {
    id: 1,
    name: '张三',
    age: 72,
    phone: '138****1234',
    deviceNo: 'DEV001',
    address: '北京市朝阳区 XX 小区 1-101',
    status: 'safe',
    todayCheckin: true,
    healthScore: 95,
    lastActiveTime: Date.now() - 3600000,
    avatarColor: '#1890ff'
  },
  {
    id: 2,
    name: '李四',
    age: 68,
    phone: '139****5678',
    deviceNo: 'DEV002',
    address: '北京市海淀区 XX 小区 2-202',
    status: 'safe',
    todayCheckin: true,
    healthScore: 88,
    lastActiveTime: Date.now() - 7200000,
    avatarColor: '#52c41a'
  },
  {
    id: 3,
    name: '王五',
    age: 75,
    phone: '136****9012',
    deviceNo: 'DEV003',
    address: '上海市浦东新区 XX 小区 3-303',
    status: 'warning',
    todayCheckin: false,
    healthScore: 72,
    lastActiveTime: Date.now() - 86400000,
    avatarColor: '#faad14'
  },
  {
    id: 4,
    name: '赵六',
    age: 80,
    phone: '135****3456',
    deviceNo: 'DEV004',
    address: '广州市天河区 XX 小区 4-404',
    status: 'danger',
    todayCheckin: false,
    healthScore: 45,
    lastActiveTime: Date.now() - 172800000,
    avatarColor: '#f5222d'
  }
])

// 分页配置
const pagination = ref<TablePaginationConfig>({
  current: 1,
  pageSize: 10,
  total: dataSource.value.length,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total) => `共 ${total} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
})

// 对话框相关
const modalVisible = ref(false)
const modalTitle = ref('新增用户')
const formRef = ref()

const formData = ref({
  id: null,
  name: '',
  age: 0,
  phone: '',
  deviceNo: '',
  address: '',
  emergencyContact: '',
  emergencyPhone: '',
  remark: ''
})

// 获取状态徽章颜色
const getStatusBadge = (status: string) => {
  const statusMap: Record<string, any> = {
    safe: 'success',
    warning: 'warning',
    danger: 'error'
  }
  return statusMap[status] || 'default'
}

// 获取状态文本
const getStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    safe: '安全',
    warning: '注意',
    danger: '危险'
  }
  return statusMap[status] || '未知'
}

// 获取健康分数颜色
const getHealthColor = (score: number) => {
  if (score >= 90) return '#52c41a'
  if (score >= 70) return '#faad14'
  return '#ff4d4f'
}

// 格式化时间
const formatTime = (timestamp: number) => {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

// 搜索
const handleSearch = () => {
  console.log('搜索条件:', searchForm.value)
  // TODO: 调用 API 进行搜索
}

// 重置
const handleReset = () => {
  searchForm.value = {
    name: '',
    phone: '',
    status: ''
  }
}

// 新增用户
const handleAdd = () => {
  modalTitle.value = '新增用户'
  formData.value = {
    id: null,
    name: '',
    age: 0,
    phone: '',
    deviceNo: '',
    address: '',
    emergencyContact: '',
    emergencyPhone: '',
    remark: ''
  }
  modalVisible.value = true
}

// 查看详情
const handleView = (record: any) => {
  router.push(`/users/${record.id}`)
}

// 编辑用户
const handleEdit = (record: any) => {
  modalTitle.value = '编辑用户'
  formData.value = {
    id: record.id,
    name: record.name,
    age: record.age,
    phone: record.phone,
    deviceNo: record.deviceNo,
    address: record.address,
    emergencyContact: record.emergencyContact || '',
    emergencyPhone: record.emergencyPhone || '',
    remark: record.remark || ''
  }
  modalVisible.value = true
}

// 删除用户
const handleDelete = (record: any) => {
  console.log('删除用户:', record)
  // TODO: 调用 API 删除
}

// 表格变化处理
const handleTableChange = (pag: TablePaginationConfig) => {
  pagination.value = pag
}

// 对话框确认
const handleModalOk = () => {
  formRef.value?.validate().then(() => {
    console.log('提交数据:', formData.value)
    // TODO: 调用 API 保存
    modalVisible.value = false
  })
}

// 对话框取消
const handleModalCancel = () => {
  modalVisible.value = false
  formRef.value?.resetFields()
}

onMounted(() => {
  // 加载用户列表数据
})
</script>

<style scoped lang="less">
.user-management-container {
  padding: 24px;
}

.stats-row {
  .stat-card {
    border-radius: 8px;
  }
}

.filter-section {
  margin-bottom: 24px;
}
</style>
