<template>
  <a-layout class="layout">
    <!-- 侧边栏 -->
    <Sidebar :collapsed="collapsed" @toggle="toggleCollapsed" />
    
    <a-layout :class="['main-layout', { collapsed }]">
      <!-- 顶部导航 -->
      <Header :collapsed="collapsed" @toggle="toggleCollapsed" />
      
      <!-- 主内容区 -->
      <a-layout-content class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <keep-alive>
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </a-layout-content>
      
      <!-- 底部 -->
      <a-layout-footer class="footer">
        © 2026 活着呢 - 独居老人安全监测平台
      </a-layout-footer>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'

const collapsed = ref<boolean>(false)

const toggleCollapsed = (value?: boolean) => {
  if (value !== undefined) {
    collapsed.value = value
  } else {
    collapsed.value = !collapsed.value
  }
}
</script>

<style scoped lang="less">
.layout {
  min-height: 100vh;
}

.main-layout {
  margin-left: 256px;
  transition: margin-left 0.3s ease;
  
  &.collapsed {
    margin-left: 80px;
  }
  
  :deep(.ant-layout-header) {
    position: sticky;
    top: 0;
    z-index: 998;
  }
}

.content {
  margin: 24px 16px;
  padding: 24px;
  background: #f8f9fa;
  min-height: calc(100vh - 112px);
  overflow: auto;
}

.footer {
  text-align: center;
  color: #6c757d;
  background: transparent;
  padding: 24px 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>