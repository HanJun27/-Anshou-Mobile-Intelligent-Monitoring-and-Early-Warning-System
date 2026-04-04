import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')
  
  return {
    plugins: [
      vue(),
      Components({
        resolvers: [
          AntDesignVueResolver({
            importStyle: false,
          }),
        ],
      }),
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    css: {
      preprocessorOptions: {
        less: {
          modifyVars: {
            '@primary-color': '#FF6B6B',
            '@success-color': '#4CAF50',
            '@warning-color': '#FFC107',
            '@error-color': '#F44336',
          },
          javascriptEnabled: true,
        },
      },
    },
    server: {
      port: Number(env.VITE_PORT) || 3000,
      host: true,
      proxy: {
        [env.VITE_API_BASE_URL || '/api']: {
          target: env.VITE_API_BASE_URL?.replace('/api', '') || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
