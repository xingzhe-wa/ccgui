# ClaudeCodeJet 前端类型定义规范

**文档版本**: 1.0
**创建日期**: 2026-04-08
**维护者**: Frontend Team

---

## 目录

- [1. 核心类型定义](#1-核心类型定义)
- [2. 聊天相关类型](#2-聊天相关类型)
- [3. 会话相关类型](#3-会话相关类型)
- [4. 主题相关类型](#4-主题相关类型)
- [5. 生态相关类型](#5-生态相关类型)
- [6. 交互式请求类型](#6-交互式请求类型)
- [7. 任务进度类型](#7-任务进度类型)
- [8. 通信桥接类型](#8-通信桥接类型)
- [9. UI状态类型](#9-ui状态类型)
- [10. 工具类型](#10-工具类型)
- [11. 类型导出索引](#11-类型导出索引)

---

## 1. 核心类型定义

### 基础类型

```typescript
// ============ shared/types/index.ts ============

/**
 * 通用ID类型
 */
export type ID = string;

/**
 * 时间戳类型（毫秒）
 */
export type Timestamp = number;

/**
 * 提取类型（美化类型显示）
 */
export type Prettify<T> = {
  [K in keyof T]: T[K];
} & {};

/**
 * 深度可选类型
 */
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

/**
 * 深度只读类型
 */
export type DeepReadonly<T> = {
  readonly [P in keyof T]: T[P] extends object ? DeepReadonly<T[P]> : T[P];
};
```

---

## 2. 聊天相关类型

### 消息类型

```typescript
// ============ shared/types/chat.ts ============

import type { ID, Timestamp } from './index';

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
  PENDING = 'pending',        // 发送中
  SENT = 'sent',              // 已发送
  STREAMING = 'streaming',    // 流式输出中
  COMPLETED = 'completed',    // 已完成
  FAILED = 'failed'           // 失败
}

/**
 * 内容部分类型（联合类型）
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
```

---

## 3. 会话相关类型

```typescript
// ============ shared/types/session.ts ============

import type { ID, Timestamp } from './index';
import type { ChatMessage } from './chat';

/**
 * 会话类型枚举
 */
export enum SessionType {
  PROJECT = 'project',     // 项目会话
  GLOBAL = 'global',       // 全局会话
  TEMPORARY = 'temporary'  // 临时会话
}

/**
 * 会话状态枚举
 */
export enum SessionStatus {
  IDLE = 'idle',                    // 空闲
  THINKING = 'thinking',            // 思考中
  STREAMING = 'streaming',          // 流式输出中
  WAITING_FOR_USER = 'waiting',     // 等待用户输入
  ERROR = 'error'                   // 错误
}

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

/**
 * 聊天会话
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
```

---

## 4. 主题相关类型

```typescript
// ============ shared/types/theme.ts ============

import type { ID } from './index';

/**
 * 颜色方案
 */
export interface ColorScheme {
  // 主色调
  primary: string;
  // 背景色
  background: string;
  // 前景色
  foreground: string;
  // 静音色
  muted: string;
  mutedForeground: string;
  // 强调色
  accent: string;
  accentForeground: string;
  // 危险色
  destructive: string;
  // 边框色
  border: string;
  // 消息颜色
  userMessage: string;
  aiMessage: string;
  systemMessage: string;
  // 代码块颜色
  codeBackground: string;
  codeForeground: string;
}

/**
 * 字体配置
 */
export interface Typography {
  messageFont: string;
  codeFont: string;
  fontSize: number;
  fontSizeSmall: number;
  fontSizeLarge: number;
  lineHeight: number;
}

/**
 * 间距配置
 */
export interface Spacing {
  messageSpacing: number;
  codeBlockPadding: number;
  headerHeight: number;
  sidebarWidth: number;
}

/**
 * 圆角配置
 */
export interface BorderRadius {
  messageBubble: number;
  codeBlock: number;
  button: number;
  input: number;
  modal: number;
}

/**
 * 阴影配置
 */
export interface Shadow {
  sm: string;
  md: string;
  lg: string;
  xl: string;
}

/**
 * 主题配置
 */
export interface ThemeConfig {
  id: ID;
  name: string;
  isDark: boolean;
  colors: ColorScheme;
  typography: Typography;
  spacing: Spacing;
  borderRadius: BorderRadius;
  shadow: Shadow;
}

/**
 * 主题预设
 */
export enum ThemePresets {
  JETBRAINS_DARK = 'jetbrains-dark',
  JETBRAINS_LIGHT = 'jetbrains-light',
  GITHUB_DARK = 'github-dark',
  GITHUB_LIGHT = 'github-light',
  VS_CODE_DARK = 'vscode-dark',
  MONOKAI = 'monokai',
  SOLARIZED_LIGHT = 'solarized-light',
  SOLARIZED_DARK = 'solarized-dark',
  NORD = 'nord',
  CUSTOM = 'custom'
}

/**
 * 主题编辑器状态
 */
export interface ThemeEditorState {
  currentTab: 'colors' | 'typography' | 'spacing' | 'radius' | 'shadow';
  selectedCategory: string;
  isPreviewMode: boolean;
  hasUnsavedChanges: boolean;
}
```

---

## 5. 生态相关类型

### Skills类型

```typescript
// ============ shared/types/ecosystem.ts ============

import type { ID } from './index';

/**
 * Skill分类枚举
 */
export enum SkillCategory {
  CODE_GENERATION = 'code_generation',
  CODE_REVIEW = 'code_review',
  REFACTORING = 'refactoring',
  TESTING = 'testing',
  DOCUMENTATION = 'documentation',
  DEBUGGING = 'debugging',
  PERFORMANCE = 'performance'
}

/**
 * 变量类型枚举
 */
export enum VariableType {
  TEXT = 'text',
  NUMBER = 'number',
  ENUM = 'enum',
  BOOLEAN = 'boolean',
  CODE = 'code'
}

/**
 * Skill作用域
 */
export enum SkillScope {
  GLOBAL = 'global',
  PROJECT = 'project'
}

/**
 * Skill变量
 */
export interface SkillVariable {
  name: string;
  type: VariableType;
  defaultValue?: any;
  required: boolean;
  options?: any[];
  placeholder?: string;
  description?: string;
}

/**
 * Skill
 */
export interface Skill {
  id: ID;
  name: string;
  description: string;
  icon: string;
  category: SkillCategory;
  prompt: string;
  variables: SkillVariable[];
  shortcut?: string;
  enabled: boolean;
  scope: SkillScope;
  createdAt?: number;
  updatedAt?: number;
}

/**
 * Skill执行上下文
 */
export interface ExecutionContext {
  variables: Record<string, any>;
  attachments: any[];
  metadata: Record<string, any>;
}

/**
 * Skill执行结果
 */
export interface SkillResult {
  skillId: ID;
  response: any;
  executionTime: number;
  tokensUsed: number;
  success: boolean;
  error?: string;
}
```

### Agents类型

```typescript
/**
 * Agent能力枚举
 */
export enum AgentCapability {
  CODE_GENERATION = 'code_generation',
  CODE_REVIEW = 'code_review',
  REFACTORING = 'refactoring',
  TESTING = 'testing',
  DOCUMENTATION = 'documentation',
  DEBUGGING = 'debugging',
  FILE_OPERATION = 'file_operation',
  TERMINAL_OPERATION = 'terminal_operation'
}

/**
 * 约束类型枚举
 */
export enum ConstraintType {
  MAX_TOKENS = 'max_tokens',
  ALLOWED_FILE_TYPES = 'allowed_file_types',
  FORBIDDEN_PATTERNS = 'forbidden_patterns',
  RESOURCE_LIMITS = 'resource_limits'
}

/**
 * Agent模式枚举
 */
export enum AgentMode {
  CAUTIOUS = 'cautious',       // 仅提供建议
  BALANCED = 'balanced',       // 建议为主，低风险自动执行
  AGGRESSIVE = 'aggressive'    // 自动执行高风险操作
}

/**
 * Agent作用域
 */
export enum AgentScope {
  GLOBAL = 'global',
  PROJECT = 'project',
  SESSION = 'session'
}

/**
 * Agent约束
 */
export interface AgentConstraint {
  type: ConstraintType;
  description: string;
  parameters: Record<string, any>;
}

/**
 * Agent任务
 */
export interface AgentTask {
  id: ID;
  description: string;
  requiredCapability: AgentCapability;
  context: Record<string, any>;
}

/**
 * Agent
 */
export interface Agent {
  id: ID;
  name: string;
  description: string;
  avatar: string;
  systemPrompt: string;
  capabilities: AgentCapability[];
  constraints: AgentConstraint[];
  tools: string[];
  mode: AgentMode;
  scope: AgentScope;
  enabled: boolean;
}

/**
 * Agent结果
 */
export interface AgentResult {
  agentId: ID;
  suggestion: any;
  executedActions: any[];
  pendingActions: any[];
  success: boolean;
  error?: string;
}
```

### MCP类型

```typescript
/**
 * MCP服务器状态枚举
 */
export enum McpServerStatus {
  CONNECTED = 'connected',
  DISCONNECTED = 'disconnected',
  ERROR = 'error',
  CONNECTING = 'connecting'
}

/**
 * MCP作用域
 */
export enum McpScope {
  GLOBAL = 'global',
  PROJECT = 'project'
}

/**
 * MCP服务器
 */
export interface McpServer {
  id: ID;
  name: string;
  description: string;
  command: string;
  args: string[];
  env: Record<string, string>;
  enabled: boolean;
  status: McpServerStatus;
  capabilities: string[];
  scope: McpScope;
  lastConnected?: number;
  error?: string;
}

/**
 * 测试结果
 */
export type TestResult =
  | { success: true; capabilities: string[] }
  | { success: false; error: string };

/**
 * MCP服务器配置
 */
export interface McpServerConfig {
  id?: ID;
  name: string;
  description: string;
  command: string;
  args: string[];
  env: Record<string, string>;
  enabled: boolean;
  scope: McpScope;
}
```

---

## 6. 交互式请求类型

```typescript
// ============ shared/types/interaction.ts ============

import type { ID } from './index';

/**
 * 问题类型枚举
 */
export enum QuestionType {
  SINGLE_CHOICE = 'single_choice',    // 单选题
  MULTIPLE_CHOICE = 'multiple_choice', // 多选题
  TEXT_INPUT = 'text_input',          // 文本输入
  CONFIRMATION = 'confirmation',       // 确认（是/否）
  CODE_REVIEW = 'code_review'         // 代码审查选择
}

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

/**
 * 问题答案
 */
export type QuestionAnswer =
  | string               // TEXT_INPUT
  | string               // SINGLE_CHOICE (option id)
  | string[]             // MULTIPLE_CHOICE (option ids)
  | boolean              // CONFIRMATION
  | Record<string, boolean>; // CODE_REVIEW

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
```

---

## 7. 任务进度类型

```typescript
// ============ shared/types/task.ts ============

import type { ID, Timestamp } from './index';

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
```

---

## 8. 通信桥接类型

```typescript
// ============ shared/types/bridge.ts ============

import type { ID } from './index';
import type { ChatMessage, ChatResponse, MultimodalMessage } from './chat';
import type { ChatSession, SessionType } from './session';
import type { ThemeConfig } from './theme';
import type { Skill, ExecutionContext, SkillResult } from './ecosystem';
import type { Agent, AgentTask } from './ecosystem';
import type { McpServer, TestResult } from './ecosystem';
import type { InteractiveQuestion } from './interaction';
import type { TaskProgress } from './task';

/**
 * Java后端API接口
 */
export interface JavaBackendAPI {
  // ========== 消息相关 ==========
  /**
   * 发送消息
   */
  sendMessage(message: string): Promise<ChatResponse>;

  /**
   * 发送多模态消息
   */
  sendMultimodalMessage(message: MultimodalMessage): Promise<ChatResponse>;

  /**
   * 流式发送消息
   */
  streamMessage(message: string): void;

  /**
   * 取消流式输出
   */
  cancelStreaming(sessionId: string): void;

  // ========== 配置相关 ==========
  /**
   * 获取配置
   */
  getConfig(key: string): Promise<any>;

  /**
   * 设置配置
   */
  setConfig(key: string, value: any): Promise<void>;

  /**
   * 更新配置
   */
  updateConfig(config: Partial<ConfigState>): Promise<void>;

  // ========== 主题相关 ==========
  /**
   * 获取所有主题
   */
  getThemes(): Promise<ThemeConfig[]>;

  /**
   * 更新主题
   */
  updateTheme(theme: ThemeConfig): Promise<void>;

  /**
   * 保存自定义主题
   */
  saveCustomTheme(theme: ThemeConfig): Promise<void>;

  /**
   * 删除自定义主题
   */
  deleteCustomTheme(themeId: string): Promise<void>;

  // ========== 会话相关 ==========
  /**
   * 创建会话
   */
  createSession(name: string, type: SessionType): Promise<ChatSession>;

  /**
   * 切换会话
   */
  switchSession(sessionId: string): Promise<void>;

  /**
   * 删除会话
   */
  deleteSession(sessionId: string): Promise<void>;

  /**
   * 搜索会话
   */
  searchSessions(query: string): Promise<ChatSession[]>;

  /**
   * 导出会话
   */
  exportSession(sessionId: string, format: 'markdown' | 'pdf'): Promise<Blob>;

  /**
   * 导入会话
   */
  importSession(data: ArrayBuffer): Promise<ChatSession>;

  // ========== Skills相关 ==========
  /**
   * 执行Skill
   */
  executeSkill(skillId: string, context: ExecutionContext): Promise<SkillResult>;

  /**
   * 获取所有Skills
   */
  getSkills(): Promise<Skill[]>;

  /**
   * 保存Skill
   */
  saveSkill(skill: Skill): Promise<void>;

  /**
   * 删除Skill
   */
  deleteSkill(skillId: string): Promise<void>;

  // ========== Agents相关 ==========
  /**
   * 启动Agent
   */
  startAgent(agentId: string, task: AgentTask): Promise<void>;

  /**
   * 停止Agent
   */
  stopAgent(agentId: string): Promise<void>;

  /**
   * 获取所有Agents
   */
  getAgents(): Promise<Agent[]>;

  /**
   * 保存Agent
   */
  saveAgent(agent: Agent): Promise<void>;

  /**
   * 删除Agent
   */
  deleteAgent(agentId: string): Promise<void>;

  // ========== MCP相关 ==========
  /**
   * 启动MCP服务器
   */
  startMcpServer(serverId: string): Promise<void>;

  /**
   * 停止MCP服务器
   */
  stopMcpServer(serverId: string): Promise<void>;

  /**
   * 测试MCP服务器连接
   */
  testMcpServer(serverId: string): Promise<TestResult>;

  /**
   * 获取所有MCP服务器
   */
  getMcpServers(): Promise<McpServer[]>;

  /**
   * 保存MCP服务器
   */
  saveMcpServer(server: McpServer): Promise<void>;

  /**
   * 删除MCP服务器
   */
  deleteMcpServer(serverId: string): Promise<void>;

  // ========== 交互式请求相关 ==========
  /**
   * 提交问题答案
   */
  submitAnswer(questionId: string, answer: any): Promise<void>;

  // ========== 底层通信 ==========
  /**
   * 底层请求-响应通信（由JavaBridge封装层内部使用，不直接在业务代码中调用）
   */
  send(request: { queryId: number; action: string; params?: any }): void;
}

/**
 * Java事件接口
 */
export interface JavaEventsAPI {
  /**
   * 监听事件
   */
  on(event: string, handler: (data: any) => void): () => void;

  /**
   * 取消监听事件
   */
  off(event: string, handler: (data: any) => void): void;

  /**
   * 触发事件
   */
  emit(event: string, data: any): void;
}

/**
 * 配置状态
 */
export interface ConfigState {
  theme: ThemeConfig;
  modelConfig: ModelConfig; // 定义见 session.ts (3. 会话相关类型)
  skills: Skill[];
  mcpServers: McpServer[];
  version: number;
}

/**
 * Java流式输出接口（可选，由Java注入）
 */
export interface CcStreamingAPI {
  onChunk(chunk: string): void;
  onDone(): void;
  onError(error: string): void;
}

/**
 * 全局类型扩展
 *
 * Java后端通过JCEF注入的全局对象类型声明。
 * ccBackend/ccEvents 由后端BridgeManager注入。
 * ccStreaming 为可选接口，仅在流式输出场景中使用。
 */
declare global {
  interface Window {
    ccBackend: JavaBackendAPI;
    ccEvents: JavaEventsAPI;
    ccStreaming?: CcStreamingAPI;
  }
}

export {};
```

---

## 9. UI状态类型

```typescript
// ============ shared/types/ui.ts ============

import type { ThemeConfig } from './theme';
import type { TaskProgress } from './task';

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
 * 布局模式
 */
export enum LayoutMode {
  SINGLE = 'single',    // 单列
  SPLIT = 'split'       // 分栏
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
```

---

## 10. 工具类型

```typescript
// ============ shared/types/util.ts ============

/**
 * 函数类型
 */
export type Fn<Args extends any[] = any[], Result = any> = (...args: Args) => Result;

/**
 * 异步函数类型
 */
export type AsyncFn<Args extends any[] = any[], Result = any> = (...args: Args) => Promise<Result>;

/**
 * 事件处理器类型
 */
export type EventHandler<T = any> = (event: T) => void;

/**
 * 类名类型
 */
export type ClassValue = ClassArray | ClassDictionary | string | number | null | boolean | undefined;

/**
 * 类名数组
 */
interface ClassArray extends Array<ClassValue> {}

/**
 * 类名字典
 */
interface ClassDictionary {
  [id: string]: any;
}

/**
 * 可空类型
 */
export type Nullable<T> = T | null;

/**
 * 不可空类型
 */
export type NonNullable<T> = T extends null | undefined ? never : T;

/**
 * 深度必填类型
 */
export type DeepRequired<T> = {
  [P in keyof T]-?: DeepRequired<T[P]>;
};

/**
 * 提取属性类型
 */
export type ValueOf<T> = T[keyof T];

/**
 * 元组转联合
 */
export type TupleToUnion<T extends any[]> = T[number];

/**
 * 联合转交叉
 */
export type UnionToIntersection<U> = (U extends any ? (k: U) => void : never) extends (k: infer I) => void ? I : never;

/**
 * 条件类型
 */
export type If<C extends boolean, T, F> = C extends true ? T : F;

/**
 * 是否字面量类型
 */
export type IsLiteral<T> = string extends T
  ? never
  : [T] extends [boolean]
  ? never
  : true;

/**
 * 提取Promise返回值
 */
export type Awaited<T> = T extends Promise<infer U> ? U : T;

/**
 * Omit掉多个key
 */
export type OmitMultiple<T, K extends keyof T> = Omit<T, K>;

/**
 * Partial + Required 混合
 */
export type PartialRequired<T, K extends keyof T> = Partial<Omit<T, K>> & Required<Pick<T, K>>;
```

---

## 11. 类型导出

```typescript
// ============ shared/types/index.ts ============

// 基础类型
export * from './util';

// 聊天相关
export * from './chat';

// 会话相关
export * from './session';

// 主题相关
export * from './theme';

// 生态相关
export * from './ecosystem';

// 交互相关
export * from './interaction';

// 任务相关
export * from './task';

// 通信相关
export * from './bridge';

// UI相关
export * from './ui';
```

---

## 📋 使用示例

```typescript
// 导入类型
import type {
  ChatMessage,
  ChatSession,
  ThemeConfig,
  Skill,
  InteractiveQuestion,
  JavaBackendAPI
} from '@/shared/types';

// 使用类型
const message: ChatMessage = {
  id: 'msg-123',
  role: MessageRole.USER,
  content: 'Hello, AI!',
  timestamp: Date.now()
};

const session: ChatSession = {
  id: 'session-456',
  name: 'My Chat',
  type: SessionType.PROJECT,
  messages: [message],
  context: {
    modelConfig: {
      provider: 'anthropic',
      model: 'claude-3-5-sonnet-20241022',
      maxTokens: 4096,
      temperature: 0.7,
      topP: 0.9
    },
    enabledSkills: [],
    enabledMcpServers: [],
    metadata: {}
  },
  createdAt: Date.now(),
  updatedAt: Date.now(),
  isActive: true,
  status: SessionStatus.IDLE
};

// 使用Java后端API
const backend: JavaBackendAPI = window.ccBackend;
backend.sendMessage('Hello!').then((response) => {
  console.log(response.content);
});
```

---

**相关文档**：
- [技术架构设计](./10-architecture.md)
- [组件设计规范](./12-components.md)
