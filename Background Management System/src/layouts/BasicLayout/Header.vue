<template>
  <a-layout-header class="header">
    <div class="header-left">
      <span class="material-icons-round trigger" @click="emit('toggle')">
        {{ collapsed ? 'menu' : 'close' }}
      </span>
      <a-breadcrumb>
        <a-breadcrumb-item>首页</a-breadcrumb-item>
        <a-breadcrumb-item>{{ currentTitle }}</a-breadcrumb-item>
      </a-breadcrumb>
    </div>
    
    <div class="header-right">
      <a-badge :count="5" size="small">
        <a-button type="text" shape="circle">
          <span class="material-icons-round">notifications</span>
        </a-button>
      </a-badge>
      
      <a-dropdown>
        <div class="user-info">
          <a-avatar size="default" style="background-color: #FF6B6B">
            <template #icon>👤</template>
          </a-avatar>
          <span class="username">管理员</span>
        </div>
        <template #overlay>
          <a-menu>
            <a-menu-item key="profile">
              <span class="material-icons-round">person</span>
              个人中心
            </a-menu-item>
            <a-menu-item key="settings">
              <span class="material-icons-round">settings</span>
              系统设置
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout" @click="handleLogout">
              <span class="material-icons-round">logout</span>
              退出登录
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const router = useRouter()
const route = useRoute()

const currentTitle = computed(() => route.meta.title as string || '未知页面')

const handleLogout = () => {
  localStorage.removeItem('admin_token')
  router.push('/login')
}
</script>

<style scoped lang="less">
.header {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 24px;

    .trigger {
      font-size: 24px;
      cursor: pointer;
      transition: all 0.3s;
      
      &:hover {
        color: #FF6B6B;
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;

    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      
      .username {
        font-size: 14px;
        color: #212529;
      }
    }
  }
}
</style>