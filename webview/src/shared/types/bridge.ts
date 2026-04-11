/**
 * Java 通信桥接类型定义
 * 定义 window.ccBackend 和 window.ccEvents 的 TypeScript 类型
 */

import type { ChatResponse, MultimodalMessage } from './chat';
import type { ChatSession, SessionType, ModelConfig } from './session';
import type { ThemeConfig } from './theme';
import type { Skill, ExecutionContext, SkillResult } from './ecosystem';
import type { Agent, AgentTask } from './ecosystem';
import type { McpServer, TestResult } from './ecosystem';

/**
 * Java后端API接口
 * 前端调用后端的全部方法签名
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
  streamMessage(message: string): Promise<any>;

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

  /**
   * 获取模型配置
   */
  getModelConfig(): Promise<{
    provider: string;
    model: string;
    apiKey: string;
    baseUrl: string;
    maxTokens: number;
    temperature: number;
    topP: number;
    maxRetries: number;
  }>;

  /**
   * 更新模型配置
   */
  updateModelConfig(config: {
    provider?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    maxTokens?: number;
    temperature?: number;
    topP?: number;
    maxRetries?: number;
  }): Promise<void>;

  // ========== Provider Profiles ==========
  /**
   * 获取所有 Provider Profiles
   */
  getProviderProfiles(): Promise<{
    profiles: Array<{
      id: string;
      name: string;
      provider: string;
      source: string;
      model: string;
      apiKey: string;
      baseUrl: string;
      sonnetModel: string;
      opusModel: string;
      maxModel: string;
      maxRetries: number;
      createdAt: number;
      updatedAt: number;
    }>;
    activeProfileId: string | null;
  }>;

  /**
   * 创建 Provider Profile
   */
  createProviderProfile(profile: {
    id: string;
    name: string;
    provider?: string;
    source?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    sonnetModel?: string;
    opusModel?: string;
    maxModel?: string;
    maxRetries?: number;
    createdAt?: number;
    updatedAt?: number;
  }): Promise<{ success: boolean; id?: string }>;

  /**
   * 更新 Provider Profile
   */
  updateProviderProfile(profile: {
    id: string;
    name: string;
    provider?: string;
    source?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    sonnetModel?: string;
    opusModel?: string;
    maxModel?: string;
    maxRetries?: number;
    createdAt?: number;
    updatedAt?: number;
  }): Promise<{ success: boolean }>;

  /**
   * 删除 Provider Profile
   */
  deleteProviderProfile(profileId: string): Promise<{ success: boolean }>;

  /**
   * 设置激活的 Provider Profile
   */
  setActiveProviderProfile(profileId: string | null): Promise<{ success: boolean }>;

  /**
   * 重新排序 Provider Profiles
   */
  reorderProviderProfiles(orderedIds: string[]): Promise<{ success: boolean }>;

  /**
   * 转换 cc-switch 配置为本地配置
   */
  convertCcSwitchProfile(profileId: string): Promise<{
    success: boolean;
    profile?: {
      id: string;
      name: string;
      provider: string;
      source: string;
      model: string;
      apiKey: string;
      baseUrl: string;
      sonnetModel: string;
      opusModel: string;
      maxModel: string;
      maxRetries: number;
      createdAt: number;
      updatedAt: number;
    };
  }>;

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
   * 注意：这是异步方法，返回 Promise<ChatSession>
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
  importSession(data: string): Promise<{ id: string; name: string }>;

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

  // ========== Chat Config ==========
  /**
   * 获取 Chat 配置（模式、Agent、Streaming、Thinking）
   */
  getChatConfig(): Promise<{
    conversationMode: string;
    currentAgentId: string | null;
    streamingEnabled: boolean;
  }>;

  /**
   * 更新 Chat 配置
   */
  updateChatConfig(config: {
    conversationMode?: string;
    currentAgentId?: string | null;
    streamingEnabled?: boolean;
  }): Promise<void>;

  // ========== Task Status ==========
  /**
   * 获取任务状态
   */
  getTaskStatus(): Promise<{
    tasks: Array<{
      taskId: string;
      name: string;
      status: string;
      currentStep: number;
      totalSteps: number;
      progress: number;
    }>;
    activeSubagents: Array<{
      agentId: string;
      agentName: string;
      taskId: string;
      startTime: number;
      status: string;
    }>;
    diffRecords: Array<Record<string, never>>;
  }>;

  // ========== Slash Commands ==========
  /**
   * 执行 Slash 命令
   */
  executeSlashCommand(command: string): Promise<{
    success: boolean;
    message?: string;
    error?: string;
  }>;

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
  modelConfig: ModelConfig;
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
 * Java后端通过JCEF注入的全局对象类型声明
 */
declare global {
  interface Window {
    /**
     * Java后端API对象（由后端BridgeManager注入）
     */
    ccBackend: JavaBackendAPI;

    /**
     * Java事件总线（由后端BridgeManager注入）
     */
    ccEvents: JavaEventsAPI;

    /**
     * Java流式输出接口（可选，仅在流式输出场景中使用）
     */
    ccStreaming?: CcStreamingAPI;
  }
}

export {};
