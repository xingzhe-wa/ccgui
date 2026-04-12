import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  // 重要：使用相对路径，使 file:// URL 加载时能正确解析资源
  base: './',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@/shared': path.resolve(__dirname, './src/shared'),
      '@/features': path.resolve(__dirname, './src/features'),
      '@/lib': path.resolve(__dirname, './src/lib'),
      '@/styles': path.resolve(__dirname, './src/styles')
    }
  },
  server: {
    port: 3000,
    strictPort: true,
    hmr: false // JCEF不支持HMR
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          // React 相关
          if (id.includes('node_modules/react') || id.includes('node_modules/react-dom')) {
            return 'vendor-react';
          }
          // React Router
          if (id.includes('node_modules/react-router')) {
            return 'vendor-router';
          }
          // Zustand 状态管理
          if (id.includes('node_modules/zustand')) {
            return 'vendor-state';
          }
          // Markdown 相关
          if (id.includes('node_modules/react-markdown') ||
              id.includes('node_modules/remark') ||
              id.includes('node_modules/rehype') ||
              id.includes('node_modules/katex')) {
            return 'vendor-markdown';
          }
          // 代码高亮
          if (id.includes('node_modules/highlight.js')) {
            return 'vendor-highlight';
          }
          // 虚拟滚动
          if (id.includes('node_modules/@tanstack/react-virtual')) {
            return 'vendor-virtual';
          }
          // DnD Kit
          if (id.includes('node_modules/@dnd-kit')) {
            return 'vendor-dnd';
          }
          // Lucide 图标
          if (id.includes('node_modules/lucide-react')) {
            return 'vendor-icons';
          }
          // 其他 vendor (排除已处理的)
          if (id.includes('node_modules') &&
              !id.includes('node_modules/react') &&
              !id.includes('node_modules/react-dom') &&
              !id.includes('node_modules/react-router') &&
              !id.includes('node_modules/zustand') &&
              !id.includes('node_modules/react-markdown') &&
              !id.includes('node_modules/remark') &&
              !id.includes('node_modules/rehype') &&
              !id.includes('node_modules/katex') &&
              !id.includes('node_modules/highlight.js') &&
              !id.includes('node_modules/@tanstack/react-virtual') &&
              !id.includes('node_modules/@dnd-kit') &&
              !id.includes('node_modules/lucide-react')) {
            return 'vendor';
          }
          // 应用代码
          return undefined;
        }
      }
    },
    // 启用压缩
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: false, // 保留 console 用于 JCEF 环境调试
        drop_debugger: true,
        pure_funcs: ['console.log'] // 仅移除开发时的 console.log，保留 error/warn/info
      }
    },
    // Chunk 大小警告阈值 (KB)
    chunkSizeWarningLimit: 500
  },
  // 优化依赖预构建
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', 'zustand', 'react-markdown']
  }
});
