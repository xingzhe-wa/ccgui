/**
 * 会话相关类型定义
 */

import type { ID, Timestamp } from './index';
import type { ChatMessage } from './chat';

// ============== 枚举 ==============

/**
 * 会话类型枚举
 */
export enum SessionType {
  PROJECT = 'project',    // 项目会话
  GLOBAL = 'global',      // 全局会话
  TEMPORARY = 'temporary' // 临时会话
}

/**
 * 会话状态枚举
 */
export enum SessionStatus {
  IDLE = 'idle',               // 空闲
  THINKING = 'thinking',       // 思考中
  STREAMING = 'streaming',     // 流式输出中
  WAITING_FOR_USER = 'waiting', // 等待用户输入
  ERROR = 'error'              // 错误
}

// ============== 配置类型 ==============

/**
 * 会话上下文
 */
export interface SessionContext {
  modelConfig: ModelConfig;
  enabledSkills: string[];
  enabledMcpServers: string[];
  metadata: Record<string, any>;
}

/**
 * 模型配置
 */
export interface ModelConfig {
  provider: string;
  model: string;
  maxTokens: number;
  temperature: number;
  topP: number;
}

// ============== 会话类型 ==============

/**
 * 聊天会话
 * 注意：使用 ChatSession 而不是 Session
 */
export interface ChatSession {
  id: ID;
  name: string;
  type: SessionType;
  projectId?: string;
  messages: ChatMessage[];
  context: SessionContext;
  createdAt: Timestamp;
  updatedAt: Timestamp;
  isActive: boolean;
  status: SessionStatus;
}

/**
 * 会话搜索过滤器
 */
export interface SessionSearchFilters {
  query?: string;
  type?: SessionType;
  dateRange?: {
    start: Timestamp;
    end: Timestamp;
  };
  tags?: string[];
}

/**
 * 会话搜索结果
 */
export interface SessionSearchResult {
  sessionId: ID;
  sessionName: string;
  excerpt: string;
  score: number;
  timestamp: Timestamp;
}

/**
 * 会话检查点
 */
export interface SessionCheckpoint {
  sessionId: ID;
  messages: ChatMessage[];
  context: SessionContext;
  timestamp: Timestamp;
}

/**
 * 会话恢复结果
 */
export enum RecoveryResult {
  SUCCESS = 'success',
  NO_CHECKPOINT = 'no_checkpoint',
  CORRUPTED = 'corrupted'
}
