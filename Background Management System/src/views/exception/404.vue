<template>
  <div class="exception-404">
    <div class="content">
      <h1 class="error-code">404</h1>
      <h2 class="error-title">抱歉，您访问的页面不存在</h2>
      <p class="error-description">
        您访问的页面可能已被删除、改名或暂时不可用。
      </p>
      <a-space :size="16" style="margin-top: 32px">
        <a-button type="primary" @click="goHome">返回首页</a-button>
        <a-button @click="goBack">返回上一页</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="less">
.exception-404 {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.content {
  text-align: center;
  
  .error-code {
    font-size: 120px;
    font-weight: 600;
    color: #40a9ff;
    margin-bottom: 16px;
  }
  
  .error-title {
    font-size: 24px;
    color: rgba(0, 0, 0, 0.85);
    margin-bottom: 12px;
  }
  
  .error-description {
    font-size: 14px;
    color: rgba(0, 0, 0, 0.45);
    max-width: 400px;
    margin: 0 auto;
  }
}
</style>
