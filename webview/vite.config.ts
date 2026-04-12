import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { viteSingleFile } from 'vite-plugin-singlefile';
import path from 'path';

export default defineConfig({
  plugins: [
    react(),
    viteSingleFile(), // 将所有资源内联到单个 HTML 文件
  ],
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
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: false, // 保留 console 用于 JCEF 环境调试
        drop_debugger: true,
        pure_funcs: ['console.log'] // 仅移除开发时的 console.log，保留 error/warn/info
      },
    },
    // 内联所有资源到 HTML
    assetsInlineLimit: 1024 * 1024, // 1MB 以下的资源全部内联
    cssCodeSplit: false, // 不分割 CSS
    sourcemap: false,
    rollupOptions: {
      output: {
        // 使用 IIFE 格式，兼容 JCEF（不支持 ES 模块）
        format: 'iife',
        // 禁用代码分割，生成单个文件
        manualChunks: undefined,
      },
    },
    // Chunk 大小警告阈值 (KB)
    chunkSizeWarningLimit: 1000
  },
  // 优化依赖预构建
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', 'zustand', 'react-markdown']
  }
});
