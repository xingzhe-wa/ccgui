/**
 * 任务进度类型定义
 */

import type { ID, Timestamp } from './index';

// ============== 枚举 ==============

/**
 * 步骤状态枚举
 */
export enum StepStatus {
  PENDING = 'pending',
  IN_PROGRESS = 'in_progress',
  COMPLETED = 'completed',
  FAILED = 'failed',
  SKIPPED = 'skipped'
}

/**
 * 任务状态枚举
 */
export enum TaskStatus {
  PENDING = 'pending',
  IN_PROGRESS = 'in_progress',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled'
}

// ============== 任务类型 ==============

/**
 * 任务步骤
 */
export interface TaskStep {
  id: ID;
  name: string;
  description: string;
  status: StepStatus;
  output?: string;
  error?: string;
  startTime?: Timestamp;
  endTime?: Timestamp;
  duration?: number;
}

/**
 * 任务进度
 */
export interface TaskProgress {
  taskId: ID;
  totalSteps: number;
  currentStep: number;
  steps: TaskStep[];
  status: TaskStatus;
  estimatedTimeRemaining?: number;
  startTime?: Timestamp;
  endTime?: Timestamp;
}

/**
 * 任务信息
 */
export interface TaskInfo {
  id: ID;
  name: string;
  description: string;
  steps: Omit<TaskStep, 'status' | 'startTime' | 'endTime' | 'duration'>[];
}
