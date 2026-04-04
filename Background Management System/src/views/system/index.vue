<template>
  <div class="system-settings-container">
    <a-card :bordered="false">
      <a-tabs v-model:activeKey="activeKey">
        <!-- 系统配置 -->
        <a-tab-pane key="basic" tab="🏠 基本设置">
          <a-form layout="vertical" :model="basicConfig" style="max-width: 600px">
            <a-form-item label="系统名称">
              <a-input v-model:value="basicConfig.systemName" />
            </a-form-item>
            <a-form-item label="系统 Logo">
              <a-upload-dragger name="file" multiple>
                <p class="ant-upload-drag-icon">
                  <span style="font-size: 48px;">📁</span>
                </p>
                <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
                <p class="ant-upload-hint">支持 PNG、JPG 格式，建议尺寸 200x200</p>
              </a-upload-dragger>
            </a-form-item>
            <a-form-item label="系统公告">
              <a-textarea v-model:value="basicConfig.announcement" :rows="4" placeholder="请输入系统公告内容" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary">保存配置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- 安全设置 -->
        <a-tab-pane key="security" tab="🔒 安全设置">
          <a-form layout="vertical" style="max-width: 600px">
            <a-form-item label="密码策略">
              <a-select v-model:value="securityConfig.passwordPolicy" style="width: 100%">
                <a-select-option value="low">简单（6 位以上）</a-select-option>
                <a-select-option value="medium">中等（8 位以上，含字母数字）</a-select-option>
                <a-select-option value="high">严格（10 位以上，含字母数字特殊字符）</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="登录失败锁定">
              <a-input-number v-model:value="securityConfig.loginAttempts" :min="3" :max="10" />
              <span style="margin-left: 8px">次失败后锁定账户</span>
            </a-form-item>
            <a-form-item label="锁定时长">
              <a-input-number v-model:value="securityConfig.lockDuration" :min="5" :max="60" />
              <span style="margin-left: 8px">分钟</span>
            </a-form-item>
            <a-form-item label="Session 超时">
              <a-input-number v-model:value="securityConfig.sessionTimeout" :min="10" :max="1440" />
              <span style="margin-left: 8px">分钟</span>
            </a-form-item>
            <a-form-item>
              <a-button type="primary">保存配置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- 数据管理 -->
        <a-tab-pane key="data" tab="💾 数据管理">
          <a-row :gutter="16">
            <a-col :span="12">
              <a-card title="数据备份" size="small">
                <a-statistic title="上次备份时间" value="2024-03-19 02:00:00" />
                <a-statistic title="备份文件大小" :value="125.6" suffix="MB" style="margin-top: 16px" />
                <a-space style="margin-top: 24px">
                  <a-button type="primary">立即备份</a-button>
                  <a-button>下载备份</a-button>
                  <a-button>恢复数据</a-button>
                </a-space>
              </a-card>
            </a-col>
            
            <a-col :span="12">
              <a-card title="数据清理" size="small">
                <a-statistic title="日志数据保留期" :value="180" suffix="天" />
                <a-statistic title="警报记录保留期" :value="365" suffix="天" style="margin-top: 16px" />
                <a-space style="margin-top: 24px">
                  <a-button danger>清理过期数据</a-button>
                  <a-button>导出全部数据</a-button>
                </a-space>
              </a-card>
            </a-col>
          </a-row>
        </a-tab-pane>

        <!-- 系统日志 -->
        <a-tab-pane key="logs" tab="📋 系统日志">
          <a-table 
            :columns="logColumns" 
            :data-source="logData"
            :pagination="{ pageSize: 10 }"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'type'">
                <a-tag :color="getLogTypeColor(record.type)">{{ record.type }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeKey = ref('basic')

const basicConfig = ref({
  systemName: '活着呢 - 独居老人安全监测平台',
  announcement: '系统将于今晚 22:00 进行例行维护，请知悉。'
})

const securityConfig = ref({
  passwordPolicy: 'medium',
  loginAttempts: 5,
  lockDuration: 30,
  sessionTimeout: 120
})

const logColumns = [
  { title: '操作时间', dataIndex: 'time', key: 'time', width: 180 },
  { title: '操作人', dataIndex: 'user', key: 'user' },
  { title: '操作类型', dataIndex: 'type', key: 'type' },
  { title: '操作内容', dataIndex: 'content', key: 'content' },
  { title: 'IP 地址', dataIndex: 'ip', key: 'ip', width: 150 }
]

const logData = ref([
  { time: '2024-03-19 10:23:45', user: '管理员 A', type: '用户管理', content: '新增用户 张三', ip: '192.168.1.100' },
  { time: '2024-03-19 09:45:12', user: '管理员 B', type: '系统配置', content: '修改通知设置', ip: '192.168.1.101' },
  { time: '2024-03-19 08:30:00', user: '管理员 A', type: '警报处理', content: '处理跌倒警报 A001', ip: '192.168.1.100' },
  { time: '2024-03-18 16:20:00', user: '管理员 C', type: '数据导出', content: '导出月度统计报表', ip: '192.168.1.102' },
  { time: '2024-03-18 14:15:00', user: '管理员 B', type: '用户管理', content: '编辑用户 李四 信息', ip: '192.168.1.101' }
])

// 获取日志类型颜色
const getLogTypeColor = (type: string) => {
  const colorMap: Record<string, string> = {
    '用户管理': 'blue',
    '系统配置': 'green',
    '警报处理': 'red',
    '数据导出': 'purple'
  }
  return colorMap[type] || 'default'
}
</script>

<style scoped lang="less">
.system-settings-container {
  padding: 24px;
  
  .form-hint {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 4px;
  }
}
</style>