<template>
  <a-layout-sider 
    :collapsed="localCollapsed"
    @collapse="handleCollapse"
    :trigger="null" 
    collapsible
    class="sidebar"
    :width="256"
  >
    <div class="logo">
      <span class="material-icons-round">favorite</span>
      <h1 v-show="!localCollapsed">活着呢</h1>
    </div>
    
    <a-menu
      v-model:selectedKeys="selectedKeys"
      v-model:openKeys="openKeys"
      theme="dark"
      mode="inline"
    >
      <a-menu-item 
        v-for="route in menuRoutes" 
        :key="route.path"
        @click="navigateTo(route)"
      >
        <span class="material-icons-round">{{ route.meta?.icon }}</span>
        <span>{{ route.meta?.title }}</span>
      </a-menu-item>
    </a-menu>
  </a-layout-sider>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const router = useRouter()
const route = useRoute()

const props = defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: [value: boolean]
}>()

// 使用本地变量管理折叠状态
const localCollapsed = ref(props.collapsed)

// 监听父组件传来的变化
watch(() => props.collapsed, (newVal) => {
  localCollapsed.value = newVal
})

// 处理折叠事件
const handleCollapse = (collapsed: boolean) => {
  localCollapsed.value = collapsed
  emit('toggle', collapsed)
}

// 过滤菜单路由（排除隐藏的和子路由）
const menuRoutes = computed(() => {
  return router.options.routes[0].children?.filter(
    r => !r.meta?.hidden && !r.path.includes(':')
  ) as RouteRecordRaw[]
})

const selectedKeys = computed(() => [route.path])
const openKeys = ref<string[]>([])

const navigateTo = (route: RouteRecordRaw) => {
  router.push(route.path)
}
</script>

<style scoped lang="less">
.sidebar {
  overflow: hidden;
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 999;
  
  :deep(.ant-menu) {
    border-right: none;
    overflow-y: auto;
    max-height: calc(100vh - 64px);
  }
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.05);
  
  .material-icons-round {
    font-size: 32px;
    color: #FF6B6B;
  }
  
  h1 {
    color: white;
    font-size: 20px;
    font-weight: 700;
    margin: 0;
    white-space: nowrap;
  }
}
</style>