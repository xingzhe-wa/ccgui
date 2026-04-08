/**
 * React 应用入口
 */

import '@/styles/globals.css';

// Dev 模式注入 mock bridge（生产构建由 dead-code elimination 移除）
if (import.meta.env.DEV) {
  // 使用 require 风格确保同步执行（Vite 会处理）
  const { injectMockBridge } = await import('@/dev/mock-bridge');
  injectMockBridge();
}

import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
