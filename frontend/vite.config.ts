import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      // 后端 API
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // MinIO 图片代理：解决本地开发跨域 + 浏览器直连 MinIO 慢的问题
      // 访问 /minio-img/image-social-dev/images/xxx.jpg
      // 实际转发到 http://localhost:9000/image-social-dev/images/xxx.jpg
      '/minio-img': {
        target: 'http://localhost:9000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/minio-img/, ''),
      },
    },
  },
})
