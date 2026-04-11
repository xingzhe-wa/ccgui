/**
 * test/utils/test-utils.tsx - 测试工具函数
 *
 * 提供可复用的测试辅助函数和组件。
 */

import React, { ReactElement } from 'react';
import { render, RenderOptions } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { vi } from 'vitest';
import type { MockCcBackend } from '../setup';
import type { ChatMessage, ChatSession } from '@/shared/types';
import { SessionType, SessionStatus, MessageRole, MessageStatus } from '@/shared/types';

/**
 * 自定义渲染选项
 */
interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  /** 路由初始路径 */
  route?: string;
  /** 自定义 wrapper 组件 */
  wrapper?: React.ComponentType<{ children: React.ReactNode }>;
}

/**
 * 自定义渲染函数，自动包含必要的 providers
 */
export function renderWithProviders(
  ui: ReactElement,
  {
    route = '/',
    wrapper: CustomWrapper,
    ...renderOptions
  }: CustomRenderOptions = {}
) {
  // 默认 wrapper 包含 BrowserRouter
  function AllTheProviders({ children }: { children: React.ReactNode }) {
    let wrapped = <BrowserRouter>{children}</BrowserRouter>;

    if (CustomWrapper) {
      wrapped = <CustomWrapper>{wrapped}</CustomWrapper>;
    }

    return wrapped;
  }

  // 设置当前路由
  window.history.pushState({}, 'Test page', route);

  return {
    ...render(ui, { wrapper: AllTheProviders, ...renderOptions }),
  };
}

// 重新导出 testing-library 工具
export * from '@testing-library/react';
export { default as userEvent } from '@testing-library/user-event';

/**
 * 等待指定时间（用于测试异步操作）
 */
export function waitFor(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 创建测试用的 ChatMessage 对象
 */
export function createMockMessage(overrides?: Partial<ChatMessage>): ChatMessage {
  return {
    id: `msg-${Date.now()}`,
    role: MessageRole.USER,
    content: 'Test message',
    timestamp: Date.now(),
    status: MessageStatus.COMPLETED,
    ...overrides,
  };
}

/**
 * 创建测试用的 ChatSession 对象
 */
export function createMockSession(overrides?: Partial<ChatSession>): ChatSession {
  return {
    id: `session-${Date.now()}`,
    name: 'Test Session',
    type: SessionType.PROJECT,
    messages: [],
    context: {
      modelConfig: {
        provider: 'test',
        model: 'test-model',
        maxTokens: 4000,
        temperature: 0.7,
        topP: 0.9,
      },
      enabledSkills: [],
      enabledMcpServers: [],
      metadata: {},
    },
    createdAt: Date.now(),
    updatedAt: Date.now(),
    isActive: true,
    status: SessionStatus.IDLE,
    isInitialized: true,
    ...overrides,
  };
}

/**
 * 获取 JCEF mock 对象
 */
export function getMockCcBackend(): MockCcBackend {
  return window.ccBackend as unknown as MockCcBackend;
}

/**
 * 重置所有 JCEF mocks
 */
export function resetJcefMocks(): void {
  const mockBackend = getMockCcBackend();

  vi.clearAllMocks();

  // 重新设置基本的 mock 返回值
  mockBackend.sendMessage.mockResolvedValue({ content: 'Test response' });
  mockBackend.createSession.mockResolvedValue({
    id: 'test-session-1',
    name: 'Test Session',
  });
  mockBackend.submitAnswer.mockResolvedValue({ success: true });
  mockBackend.searchSessions.mockResolvedValue([]);
}
