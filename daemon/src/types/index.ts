/**
 * Type definitions for Claude Agent Daemon
 */

// Attachment types
export interface Attachment {
  type: 'text' | 'image' | 'file';
  name?: string;
  content: string;
  mimeType?: string;
}

// Session types
export interface SessionStartRequest {
  model?: string;
  mcpServers?: string[];
  systemPrompt?: string;
}

export interface SessionStartResponse {
  sessionId: string;
  status: 'ready' | 'error';
  supportedTools: string[];
}

export interface SessionMessageRequest {
  content: string;
  attachments?: Attachment[];
}

export interface SessionStatusResponse {
  sessionId: string;
  status: 'ready' | 'streaming' | 'waiting';
}

// SSE Event types
export interface SseMessageStart {
  type: 'message_start';
  messageId: string;
}

export interface SseContentBlockStart {
  type: 'content_block_start';
  index: number;
  blockType: 'text' | 'tool_use' | 'thinking';
}

export interface SseContentBlockDelta {
  type: 'content_block_delta';
  index: number;
  delta: {
    type: 'text_delta' | 'thinking_delta' | 'input_json_delta';
    text?: string;
    thinking?: string;
  };
}

export interface SseContentBlockStop {
  type: 'content_block_stop';
  index: number;
}

export interface SseMessageStop {
  type: 'message_stop';
}

export interface SseUsage {
  type: 'usage';
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
}

export interface SseError {
  type: 'error';
  message: string;
}

export type SseEvent =
  | SseMessageStart
  | SseContentBlockStart
  | SseContentBlockDelta
  | SseContentBlockStop
  | SseMessageStop
  | SseUsage
  | SseError;

// MCP types
export interface McpServerConfig {
  command: string;
  args?: string[];
  env?: Record<string, string>;
}

export interface McpServerStatus {
  id: string;
  name: string;
  status: 'stopped' | 'starting' | 'running' | 'error';
  error?: string;
}

// OAuth types
export interface OAuthTokenRequest {
  action: 'set' | 'get' | 'clear';
  token?: string;
}

export interface OAuthTokenResponse {
  success: boolean;
  hasToken?: boolean;
  error?: string;
}

// API Error
export interface ApiError {
  error: string;
  code?: string;
}
