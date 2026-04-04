import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import { setupRouterGuards } from './router/guards'

// 导入样式
import 'ant-design-vue/dist/reset.css'
import './assets/styles/global.less'

// 导入 Material Icons
import 'material-icons/iconfont/material-icons.css'

const app = createApp(App)
const pinia = createPinia()

// 使用插件
app.use(pinia)
app.use(router)
app.use(Antd)

// 设置路由守卫
setupRouterGuards(router)

// 挂载应用
app.mount('#app')

console.log('🎉 活着呢管理后台启动成功!')