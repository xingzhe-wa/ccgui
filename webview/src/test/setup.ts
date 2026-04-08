/**
 * test/setup.ts - Vitest 测试环境设置
 *
 * 在每个测试文件运行前执行的全局设置。
 */

import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// 每个测试后自动清理 React 组件
afterEach(() => {
  cleanup();
});

// Mock JCEF 全局对象
const mockCcBackend = {
  sendMessage: vi.fn(() => Promise.resolve({ content: 'Test response' })),
  createSession: vi.fn(() => Promise.resolve({ id: 'test-session-1', name: 'Test Session' })),
  submitAnswer: vi.fn(() => Promise.resolve({ success: true })),
  searchSessions: vi.fn((): Promise<unknown[]> => Promise.resolve([])),
  exportSession: vi.fn(() => Promise.resolve({ success: true })),
  importSession: vi.fn(() => Promise.resolve({ success: true })),
  switchSession: vi.fn(() => Promise.resolve()),
  deleteSession: vi.fn(() => Promise.resolve()),
};

const mockCcQuery = {
  invoke: vi.fn(() => Promise.resolve('{"result":"ok"}')),
};

const mockCcEvents = {
  on: vi.fn(() => vi.fn()),
  emit: vi.fn(),
  off: vi.fn(),
};

// 模拟 window.ccBackend
Object.defineProperty(window, 'ccBackend', {
  value: mockCcBackend,
  writable: true,
});

// 模拟 window.ccQuery
Object.defineProperty(window, 'ccQuery', {
  value: mockCcQuery,
  writable: true,
});

// 模拟 window.ccEvents
Object.defineProperty(window, 'ccEvents', {
  value: mockCcEvents,
  writable: true,
});

// 模拟 requestIdleCallback（浏览器 API）
globalThis.requestIdleCallback = vi.fn((cb: IdleRequestCallback) => {
  return setTimeout(() => {
    cb({
      didTimeout: false,
      timeRemaining: () => 50,
    });
  }, 0);
}) as unknown as typeof requestIdleCallback;

globalThis.cancelIdleCallback = vi.fn((id: number) => {
  clearTimeout(id);
});

// 模拟 IntersectionObserver
globalThis.IntersectionObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
})) as unknown as typeof IntersectionObserver;

// 模拟 ResizeObserver
globalThis.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
})) as unknown as typeof ResizeObserver;

// 模拟 Performance API
globalThis.performance.now = vi.fn(() => Date.now());

// 模拟 console 方法以减少测试输出噪音
globalThis.console = {
  ...console,
  log: vi.fn(),
  debug: vi.fn(),
  info: vi.fn(),
  warn: vi.fn(),
  // 保留 error 以便调试失败的测试
};

// 导出 mock 类型供测试使用
export type MockCcBackend = typeof mockCcBackend;
export type MockCcQuery = typeof mockCcQuery;
export type MockCcEvents = typeof mockCcEvents;
