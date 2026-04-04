<template>
  <div class="login">
    <div class="login-container">
      <div class="login-header">
        <h1>🏠 安守</h1>
        <p>安全监测平台</p>
      </div>
      
      <a-card class="login-form" :bordered="false">
        <h2 style="margin-bottom: 24px; text-align: center">管理员登录</h2>
        
        <a-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          layout="vertical"
          @finish="handleLogin"
        >
          <a-form-item name="username" label="用户名">
            <a-input 
              v-model:value="loginForm.username" 
              placeholder="请输入用户名"
              size="large"
            >
              <template #prefix>
                <span style="font-size: 16px;">👤</span>
              </template>
            </a-input>
          </a-form-item>
          
          <a-form-item name="password" label="密码">
            <a-input-password 
              v-model:value="loginForm.password" 
              placeholder="请输入密码"
              size="large"
              @press-enter="handleLogin"
            >
              <template #prefix>
                <span style="font-size: 16px;">🔒</span>
              </template>
            </a-input-password>
          </a-form-item>
          
          <a-form-item>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <a-checkbox v-model:checked="loginForm.remember">记住密码</a-checkbox>
              <a href="#">忘记密码？</a>
            </div>
          </a-form-item>
          
          <a-form-item>
            <a-button 
              type="primary" 
              html-type="submit" 
              size="large" 
              block
              :loading="loading"
            >
              登录
            </a-button>
          </a-form-item>
        </a-form>
        
        <a-divider>其他登录方式</a-divider>
        
        <div class="other-login">
          <a-space size="large">
            <a href="#" class="login-icon" title="微信登录">
              <span style="font-size: 32px;">💬</span>
            </a>
            <a href="#" class="login-icon" title="手机号登录">
              <span style="font-size: 32px;">📱</span>
            </a>
            <a href="#" class="login-icon" title="邮箱登录">
              <span style="font-size: 32px;">📧</span>
            </a>
          </a-space>
        </div>
      </a-card>
      
      <div class="login-footer">
        <p>© 2026 活着呢 - 用心守护每一位独居老人</p>
        <p>技术支持：活着呢科技团队</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { Rule } from 'ant-design-vue/es/form'

const router = useRouter()
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  loading.value = true
  
  // 模拟登录请求
  setTimeout(() => {
    loading.value = false
    
    // 演示账号：admin / 123456
    if (loginForm.username === 'admin' && loginForm.password === '123456') {
      localStorage.setItem('admin_token', 'demo-token-' + Date.now())
      router.push('/')
    } else {
      alert('用户名或密码错误（演示账号：admin / 123456）')
    }
  }, 1000)
}
</script>

<style scoped lang="less">
.login {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.login-container {
  width: 100%;
  max-width: 420px;
}

.login-header {
  text-align: center;
  margin-bottom: 24px;
  color: white;
  
  h1 {
    margin: 0 0 8px 0;
    font-size: 32px;
    font-weight: 600;
  }
  
  p {
    margin: 0;
    opacity: 0.9;
    font-size: 14px;
  }
}

.login-form {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  border-radius: 8px;
}

.other-login {
  text-align: center;
  
  .login-icon {
    color: rgba(0, 0, 0, 0.45);
    transition: color 0.3s;
    
    &:hover {
      color: #1890ff;
    }
  }
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  
  p {
    margin: 4px 0;
  }
}
</style>
