import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layouts/BasicLayout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { 
          title: '控制台', 
          icon: 'dashboard',
          keepAlive: true 
        }
      },
      {
        path: '/users',
        name: 'UserManagement',
        component: () => import('@/views/users/index.vue'),
        meta: { 
          title: '用户管理', 
          icon: 'people',
          keepAlive: true 
        }
      },
      {
        path: '/users/:id',
        name: 'UserDetail',
        component: () => import('@/views/users/UserDetail.vue'),
        meta: { 
          title: '用户详情',
          hidden: true 
        }
      },
      {
        path: '/alerts/realtime',
        name: 'RealtimeAlerts',
        component: () => import('@/views/alerts/realtime.vue'),
        meta: { 
          title: '实时警报', 
          icon: 'warning',
          keepAlive: true 
        }
      },
      {
        path: '/alerts/history',
        name: 'AlertHistory',
        component: () => import('@/views/alerts/history.vue'),
        meta: { 
          title: '警报历史', 
          icon: 'history',
          keepAlive: true 
        }
      },
      {
        path: '/statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { 
          title: '数据统计', 
          icon: 'analytics',
          keepAlive: true 
        }
      },
      {
        path: '/system',
        name: 'SystemSettings',
        component: () => import('@/views/system/index.vue'),
        meta: { 
          title: '系统设置', 
          icon: 'settings',
          keepAlive: true 
        }
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { 
      title: '登录',
      hidden: true 
    }
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/views/exception/404.vue'),
    meta: { 
      title: '404',
      hidden: true 
    }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404'
  }
]

const router = createRouter({
  history: createWebHistory((import.meta as any).env.BASE_URL),
  routes
})

export default router