/**
 * UI 状态类型定义
 */

import type { TaskProgress } from './task';

/**
 * 通知
 */
export interface Notification {
  id: string;
  type: 'info' | 'success' | 'warning' | 'error';
  title: string;
  message: string;
  duration?: number;
  actions?: NotificationAction[];
}

/**
 * 通知操作
 */
export interface NotificationAction {
  label: string;
  action: () => void;
  primary?: boolean;
}

/**
 * UI状态
 */
export interface UIState {
  // 侧边栏
  sidebarOpen: boolean;
  sidebarWidth: number;

  // 预览面板
  previewPanelOpen: boolean;
  previewPanelWidth: number;

  // 模态框
  activeModal: string | null;
  modalData: any;

  // 通知
  notifications: Notification[];

  // 加载状态
  isLoading: boolean;
  loadingMessage: string;
}

/**
 * 布局模式
 */
export enum LayoutMode {
  SINGLE = 'single',  // 单列
  SPLIT = 'split'      // 分栏
}

/**
 * 响应式断点
 */
export enum Breakpoint {
  SM = 'sm',   // < 800px
  MD = 'md',   // 800px - 1200px
  LG = 'lg'    // > 1200px
}

/**
 * 应用状态
 */
export interface AppState {
  // UI状态
  ui: UIState;

  // 当前任务进度
  activeTaskProgress: TaskProgress | null;

  // 错误状态
  error: Error | null;
}
