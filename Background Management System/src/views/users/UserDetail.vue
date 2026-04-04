<template>
  <div class="user-detail">
    <a-card title="用户详情" :bordered="false">
      <template #extra>
        <a-space>
          <a-button @click="goBack">返回</a-button>
          <a-button type="primary">编辑</a-button>
        </a-space>
      </template>

      <a-descriptions bordered :column="2">
        <a-descriptions-item label="用户 ID">1</a-descriptions-item>
        <a-descriptions-item label="姓名">张三</a-descriptions-item>
        <a-descriptions-item label="年龄">72</a-descriptions-item>
        <a-descriptions-item label="性别">男</a-descriptions-item>
        <a-descriptions-item label="手机号" :span="2">138****1234</a-descriptions-item>
        <a-descriptions-item label="设备编号" :span="2">DEV001</a-descriptions-item>
        <a-descriptions-item label="安装地址" :span="2">北京市朝阳区 XX 小区 1-101</a-descriptions-item>
        <a-descriptions-item label="紧急联系人">张小二 (儿子)</a-descriptions-item>
        <a-descriptions-item label="联系电话">137****5678</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-badge status="success" text="正常" />
        </a-descriptions-item>
        <a-descriptions-item label="注册时间">2024-01-15</a-descriptions-item>
        <a-descriptions-item label="最后活跃时间" :span="2">2024-03-19 10:23:45</a-descriptions-item>
        <a-descriptions-item label="备注" :span="2">
          老人独居，患有高血压，需要定期监测
        </a-descriptions-item>
      </a-descriptions>

      <a-divider />

      <h3>设备信息</h3>
      <a-table 
        :columns="deviceColumns" 
        :data-source="deviceData"
        :pagination="false"
        size="small"
      />

      <a-divider />

      <h3>最近警报记录</h3>
      <a-table 
        :columns="alertColumns" 
        :data-source="alertData"
        :pagination="false"
        size="small"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.back()
}

const deviceColumns = [
  { title: '设备类型', dataIndex: 'type', key: 'type' },
  { title: '设备编号', dataIndex: 'no', key: 'no' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '电量', dataIndex: 'battery', key: 'battery' },
  { title: '最后上报时间', dataIndex: 'lastReport', key: 'lastReport' }
]

const deviceData = ref([
  { type: '网关', no: 'GW001', status: '在线', battery: '-', lastReport: '2024-03-19 10:23:45' },
  { type: '跌倒检测雷达', no: 'RD001', status: '在线', battery: '85%', lastReport: '2024-03-19 10:23:40' },
  { type: '烟雾报警器', no: 'SK001', status: '在线', battery: '92%', lastReport: '2024-03-19 10:20:00' },
  { type: '紧急按钮', no: 'EB001', status: '在线', battery: '78%', lastReport: '2024-03-19 09:00:00' }
])

const alertColumns = [
  { title: '警报类型', dataIndex: 'type', key: 'type' },
  { title: '警报级别', dataIndex: 'level', key: 'level' },
  { title: '发生时间', dataIndex: 'time', key: 'time' },
  { title: '处理状态', dataIndex: 'status', key: 'status' }
]

const alertData = ref([
  { type: '跌倒警报', level: '紧急', time: '2024-03-18 15:30:00', status: '已处理' },
  { type: '长时间未活动', level: '警告', time: '2024-03-17 20:00:00', status: '已处理' },
  { type: '设备离线', level: '提示', time: '2024-03-16 10:00:00', status: '已恢复' }
])
</script>

<style scoped lang="less">
.user-detail {
  h3 {
    margin: 16px 0;
    color: rgba(0, 0, 0, 0.85);
  }
}
</style>
