/**
 * 交互式请求类型定义
 */

import type { ID } from './index';

// ============== 枚举 ==============

/**
 * 问题类型枚举
 */
export enum QuestionType {
  SINGLE_CHOICE = 'single_choice',   // 单选题
  MULTIPLE_CHOICE = 'multiple_choice', // 多选题
  TEXT_INPUT = 'text_input',         // 文本输入
  CONFIRMATION = 'confirmation',     // 确认（是/否）
  CODE_REVIEW = 'code_review'        // 代码审查选择
}

// ============== 问题类型 ==============

/**
 * 问题选项
 */
export interface QuestionOption {
  id: string;
  label: string;
  description?: string;
  icon?: string;
  metadata: Record<string, any>;
}

/**
 * 交互式问题
 */
export interface InteractiveQuestion {
  questionId: ID;
  question: string;
  questionType: QuestionType;
  options?: QuestionOption[];
  allowMultiple: boolean;
  required: boolean;
  context: Record<string, any>;
  timeout?: number; // 超时时间（毫秒）
  createdAt: number;
}

// ============== 答案类型 ==============

/**
 * 问题答案
 */
export type QuestionAnswer =
  | string               // TEXT_INPUT
  | string               // SINGLE_CHOICE (option id)
  | string[]             // MULTIPLE_CHOICE (option ids)
  | boolean              // CONFIRMATION
  | Record<string, boolean>; // CODE_REVIEW

// ============== 处理结果 ==============

/**
 * 处理结果
 */
export type ProcessResult =
  | ProcessResultCompleted
  | ProcessResultWaiting
  | ProcessResultError;

/**
 * 已完成结果
 */
export interface ProcessResultCompleted {
  status: 'completed';
  content: string;
}

/**
 * 等待用户输入结果
 */
export interface ProcessResultWaiting {
  status: 'waiting';
  questionIds: ID[];
}

/**
 * 错误结果
 */
export interface ProcessResultError {
  status: 'error';
  error: string;
}
