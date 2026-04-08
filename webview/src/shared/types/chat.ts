/**
 * 聊天相关类型定义
 */

import type { ID, Timestamp } from './index';

// ============== 枚举 ==============

/**
 * 消息角色枚举
 */
export enum MessageRole {
  USER = 'user',
  ASSISTANT = 'assistant',
  SYSTEM = 'system'
}

/**
 * 消息状态枚举
 */
export enum MessageStatus {
  PENDING = 'pending',      // 发送中
  SENT = 'sent',            // 已发送
  STREAMING = 'streaming',  // 流式输出中
  COMPLETED = 'completed',  // 已完成
  FAILED = 'failed'         // 失败
}

// ============== 内容部分类型 ==============

/**
 * 内容部分类型（联合类型）
 * 必须使用 discriminated union 处理，不能直接访问 .name / .type
 */
export type ContentPart =
  | TextContentPart
  | ImageContentPart
  | FileContentPart;

/**
 * 文本内容
 */
export interface TextContentPart {
  type: 'text';
  text: string;
  language?: string; // 代码语言
}

/**
 * 图片内容
 */
export interface ImageContentPart {
  type: 'image';
  mimeType: string;
  data: string; // Base64编码
  width?: number;
  height?: number;
}

/**
 * 文件内容
 */
export interface FileContentPart {
  type: 'file';
  name: string;
  content: string;
  mimeType: string;
  size?: number;
}

// ============== 消息类型 ==============

/**
 * 消息引用
 */
export interface MessageReference {
  messageId: ID;
  excerpt: string;
  timestamp: Timestamp;
  sender: MessageRole;
}

/**
 * 消息元数据
 */
export interface MessageMetadata {
  tokensUsed?: number;
  model?: string;
  provider?: string;
  executionTime?: number;
  cost?: number;
  [key: string]: any;
}

/**
 * 聊天消息
 */
export interface ChatMessage {
  id: ID;
  role: MessageRole;
  content: string;
  timestamp: Timestamp;
  attachments?: ContentPart[];
  references?: MessageReference[];
  metadata?: MessageMetadata;
  status?: MessageStatus;
}

/**
 * 聊天响应
 */
export interface ChatResponse {
  content: string;
  tokensUsed: number;
  executionTime: number;
  model: string;
  finishReason?: 'stop' | 'length' | 'error';
}

/**
 * 多模态消息
 */
export interface MultimodalMessage {
  text: string;
  images: ImageContentPart[];
  attachments: FileContentPart[];
}

/**
 * 流式输出块
 */
export interface StreamingChunk {
  chunk: string;
  index: number;
  isComplete: boolean;
}

/**
 * 优化后的提示词
 */
export interface OptimizedPrompt {
  originalPrompt: string;
  optimizedPrompt: string;
  improvements: string[];
  confidence: number;
}
