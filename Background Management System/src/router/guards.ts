import type { Router } from 'vue-router'

export function setupRouterGuards(router: Router) {
  router.beforeEach((to, _from, next) => {
    // 设置页面标题
    const title = to.meta.title as string
    if (title) {
      document.title = `${title} - 活着呢管理后台`
    }

    // 检查登录状态
    const token = localStorage.getItem('admin_token')
    
    if (to.path === '/login') {
      if (token) {
        next('/dashboard')
      } else {
        next()
      }
      return
    }

    if (!token) {
      next('/login')
      return
    }

    // TODO: 这里可以添加权限验证逻辑
    
    next()
  })

  router.afterEach((to) => {
    console.log('路由跳转:', to.path)
  })
}