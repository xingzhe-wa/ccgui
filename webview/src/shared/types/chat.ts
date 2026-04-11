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
 * 扩展以支持架构文档中的所有内容块类型
 */
export type ContentPart =
  | TextContentPart
  | ImageContentPart
  | FileContentPart
  | ThinkingContentPart
  | ToolUseContentPart
  | ToolResultContentPart;

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

/**
 * 思考过程内容块
 * 对应架构文档中的 thinking 类型
 */
export interface ThinkingContentPart {
  type: 'thinking';
  thinking: string;
  text?: string; // 可选的简短文本描述
}

/**
 * 工具调用内容块
 * 对应架构文档中的 tool_use 类型
 */
export interface ToolUseContentPart {
  type: 'tool_use';
  id?: string;
  name: string;
  input?: ToolInput;
}

/**
 * 工具输入类型
 */
export type ToolInput =
  | string
  | number
  | boolean
  | ToolInput[]
  | { [key: string]: ToolInput }
  | null;

/**
 * 工具结果内容块
 * 对应架构文档中的 tool_result 类型
 */
export interface ToolResultContentPart {
  type: 'tool_result';
  tool_use_id?: string;
  content?: string | ToolResultContentPart[];
  is_error?: boolean;
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
 * 扩展以支持架构文档中的流式消息和原始消息格式
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
  /** 流式输出标识 */
  isStreaming?: boolean;
  /** 流式 turn 标识 */
  __turnId?: number;
  /** 原始消息内容（用于解析复杂内容块） */
  raw?: ClaudeRawMessage | string;
}

/**
 * Claude 原始消息格式
 * 对应架构文档中的 ClaudeRawMessage
 */
export interface ClaudeRawMessage {
  id?: string;
  type?: string;
  role?: string;
  content?: ClaudeRawContent[];
  model?: string;
  stop_reason?: string;
  usage?: MessageUsage;
}

/**
 * Claude 原始内容块
 */
export type ClaudeRawContent =
  | { type: 'text'; text?: string }
  | { type: 'thinking'; thinking?: string; text?: string }
  | { type: 'tool_use'; id?: string; name?: string; input?: ToolInput }
  | { type: 'tool_result'; tool_use_id?: string; content?: string | ClaudeRawContent[]; is_error?: boolean }
  | { type: 'image'; source?: { type: string; media_type: string; data: string } }
  | { type: 'document'; document?: { type: string; source?: { type: string; media_type: string; data: string } } };

/**
 * 消息使用量统计
 */
export interface MessageUsage {
  input_tokens: number;
  output_tokens: number;
  cache_creation_tokens?: number;
  cache_read_tokens?: number;
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
