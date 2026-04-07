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

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
