/**
 * 生态相关类型定义
 * 包含 Skills、Agents、MCP 相关类型
 */

import type { ID } from './index';

// ============== Skills 类型 ==============

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

// ============== Agents 类型 ==============

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
 * 注意：只有三个合法值 CAUTIOUS / BALANCED / AGGRESSIVE
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

// ============== MCP 类型 ==============

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
