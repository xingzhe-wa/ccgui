/**
 * React 应用入口
 */

import '@/styles/globals.css';
import { eventBus, Events } from '@/shared/utils/event-bus';

// Dev 模式注入 mock bridge（生产构建由 dead-code elimination 移除）
if (import.meta.env.DEV) {
  // 使用 require 风格确保同步执行（Vite 会处理）
  const { injectMockBridge } = await import('@/dev/mock-bridge');
  injectMockBridge();
}

// 桥接 Java 事件到前端 eventBus
// window.ccEvents.emit() 会 dispatch CustomEvent，我们转发到 eventBus
const javaEvents = [
  Events.STREAMING_CHUNK,
  Events.STREAMING_COMPLETE,
  Events.STREAMING_ERROR,
  Events.STREAMING_CANCEL,
  'response',
  'question',
];
javaEvents.forEach((eventName) => {
  window.addEventListener(eventName, (e: Event) => {
    const customEvent = e as CustomEvent;
    eventBus.emit(eventName, customEvent.detail);
  });
});

import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

// 全局错误处理 - 捕获所有未处理的错误
window.onerror = (message, source, lineno, colno, error) => {
  console.error('[Global Error]', { message, source, lineno, colno, error });
  return false;
};

window.onunhandledrejection = (event) => {
  console.error('[Unhandled Promise Rejection]', event.reason);
};

// 渲染应用
try {
  console.log('[CCGUI] Initializing React app...');
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
  console.log('[CCGUI] React app mounted');
} catch (error) {
  console.error('[CCGUI] Failed to mount React app:', error);
  document.getElementById('root')!.innerHTML = `
    <div style="padding: 20px; color: red;">
      <h1>Failed to initialize application</h1>
      <pre>${error instanceof Error ? error.message : String(error)}</pre>
    </div>
  `;
}
