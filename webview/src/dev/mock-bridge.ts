/**
 * Mock Bridge - 独立浏览器开发时的模拟后端
 *
 * 仅在 Vite dev server 模式下加载（import.meta.env.DEV === true）
 * 生产构建时会被 dead-code elimination 完全移除
 *
 * 注入 window.ccBackend 和 window.ccEvents，
 * 提供模拟数据让 UI 组件可以正常渲染
 */

import type { JavaBackendAPI, JavaEventsAPI } from '@/shared/types';
import { eventBus, Events } from '@/shared/utils/event-bus';
import {
  SessionType, SessionStatus,
  MessageRole, MessageStatus,
  SkillCategory, VariableType, SkillScope,
  AgentMode, AgentScope,
  McpServerStatus, McpScope,
} from '@/shared/types';
import type {
  ChatResponse, ChatSession, ChatMessage,
  ThemeConfig, Skill, Agent, McpServer, TestResult,
} from '@/shared/types';

// ============ Mock 数据 ============

const defaultModelConfig = {
  provider: 'anthropic',
  model: 'claude-sonnet-4-6',
  maxTokens: 4096,
  temperature: 0.7,
  topP: 1.0,
};

const defaultSessionContext = {
  modelConfig: defaultModelConfig,
  enabledSkills: [] as string[],
  enabledMcpServers: [] as string[],
  metadata: {} as Record<string, any>,
};

const mockMessages: ChatMessage[] = [
  {
    id: 'msg-1',
    role: MessageRole.USER,
    content: 'Hello, 帮我看下这个项目结构',
    timestamp: Date.now() - 60000,
    status: MessageStatus.COMPLETED,
  },
  {
    id: 'msg-2',
    role: MessageRole.ASSISTANT,
    content:
      '## 项目结构分析\n\n这是一个 IntelliJ IDEA 插件项目 **CC Assistant**，采用前后端分离架构：\n\n### 后端 (Kotlin)\n- `adaptation/sdk/` — Claude CLI 集成\n- `application/` — 业务逻辑层\n- `infrastructure/` — 基础设施（存储、事件总线、缓存）\n- `browser/` — JCEF 浏览器面板\n\n### 前端 (React + TypeScript)\n- `features/chat/` — 聊天组件\n- `features/session/` — 会话管理\n- `features/agents/` — Agent 管理\n- `features/skills/` — 技能模板\n\n```kotlin\nfun main() {\n    println("Hello from Kotlin!")\n}\n```\n\n> 该项目使用了 **JetBrains Platform SDK** 和 **JCEF** 技术栈。',
    timestamp: Date.now() - 30000,
    status: MessageStatus.COMPLETED,
  },
];

const mockSessions: ChatSession[] = [
  {
    id: 'session-1',
    name: 'Default Session',
    type: SessionType.PROJECT,
    messages: mockMessages,
    context: defaultSessionContext,
    createdAt: Date.now() - 3600000,
    updatedAt: Date.now(),
    isActive: true,
    status: SessionStatus.IDLE,
    isInitialized: true,
  },
  {
    id: 'session-2',
    name: 'Code Review',
    type: SessionType.PROJECT,
    messages: mockMessages.length > 0 ? [mockMessages[0]!] : [],
    context: defaultSessionContext,
    createdAt: Date.now() - 7200000,
    updatedAt: Date.now() - 120000,
    isActive: false,
    status: SessionStatus.IDLE,
    isInitialized: true,
  },
];

const mockAgents: Agent[] = [
  {
    id: 'agent-code-reviewer',
    name: 'Code Reviewer',
    description: '专注于代码质量审查，发现潜在问题和改进建议',
    avatar: '',
    systemPrompt: 'You are a code reviewer...',
    capabilities: [],
    constraints: [],
    tools: [],
    mode: AgentMode.CAUTIOUS,
    scope: AgentScope.PROJECT,
    enabled: true,
  },
  {
    id: 'agent-code-gen',
    name: 'Code Generator',
    description: '根据需求生成高质量代码',
    avatar: '',
    systemPrompt: 'You are a code generator...',
    capabilities: [],
    constraints: [],
    tools: [],
    mode: AgentMode.BALANCED,
    scope: AgentScope.PROJECT,
    enabled: true,
  },
  {
    id: 'agent-test',
    name: 'Test Engineer',
    description: '自动生成单元测试和集成测试',
    avatar: '',
    systemPrompt: 'You are a test engineer...',
    capabilities: [],
    constraints: [],
    tools: [],
    mode: AgentMode.BALANCED,
    scope: AgentScope.PROJECT,
    enabled: true,
  },
  {
    id: 'agent-debug',
    name: 'Debugging Expert',
    description: '帮助分析和修复 Bug',
    avatar: '',
    systemPrompt: 'You are a debugging expert...',
    capabilities: [],
    constraints: [],
    tools: [],
    mode: AgentMode.AGGRESSIVE,
    scope: AgentScope.SESSION,
    enabled: false,
  },
];

const mockSkills: Skill[] = [
  {
    id: 'skill-code-gen',
    name: 'Code Generation',
    description: '根据描述生成代码片段',
    icon: 'code',
    category: SkillCategory.CODE_GENERATION,
    prompt: '请根据以下需求生成 {language} 代码：\n\n{description}',
    variables: [
      { name: 'language', type: VariableType.TEXT, required: true, description: '目标编程语言' },
      { name: 'description', type: VariableType.TEXT, required: true, description: '代码需求描述' },
    ],
    enabled: true,
    scope: SkillScope.PROJECT,
  },
  {
    id: 'skill-code-review',
    name: 'Code Review',
    description: '审查代码质量和潜在问题',
    icon: 'search',
    category: SkillCategory.CODE_REVIEW,
    prompt: '请审查以下代码：\n\n```{language}\n{code}\n```',
    variables: [
      { name: 'language', type: VariableType.TEXT, required: true, description: '代码语言' },
      { name: 'code', type: VariableType.CODE, required: true, description: '待审查的代码' },
    ],
    enabled: true,
    scope: SkillScope.PROJECT,
  },
  {
    id: 'skill-test-gen',
    name: 'Unit Test Generation',
    description: '为指定代码生成单元测试',
    icon: 'test-tube',
    category: SkillCategory.TESTING,
    prompt: '请为以下代码生成单元测试：\n\n```{language}\n{code}\n```',
    variables: [
      { name: 'language', type: VariableType.TEXT, required: true, description: '代码语言' },
      { name: 'code', type: VariableType.CODE, required: true, description: '源代码' },
    ],
    enabled: true,
    scope: SkillScope.PROJECT,
  },
  {
    id: 'skill-refactor',
    name: 'Code Refactoring',
    description: '重构代码以提升可读性和性能',
    icon: 'refresh',
    category: SkillCategory.REFACTORING,
    prompt: '请重构以下代码：\n\n```{language}\n{code}\n```',
    variables: [
      { name: 'language', type: VariableType.TEXT, required: true, description: '代码语言' },
      { name: 'code', type: VariableType.CODE, required: true, description: '待重构代码' },
    ],
    enabled: false,
    scope: SkillScope.PROJECT,
  },
];

const mockMcpServers: McpServer[] = [
  {
    id: 'mcp-filesystem',
    name: 'File System',
    description: '文件系统操作 MCP 服务器',
    command: 'npx',
    args: ['-y', '@modelcontextprotocol/server-filesystem', '/tmp'],
    env: {},
    scope: McpScope.GLOBAL,
    status: McpServerStatus.CONNECTED,
    enabled: true,
    capabilities: ['read_file', 'write_file', 'list_directory'],
  },
  {
    id: 'mcp-github',
    name: 'GitHub',
    description: 'GitHub API 集成',
    command: 'npx',
    args: ['-y', '@modelcontextprotocol/server-github'],
    env: {},
    scope: McpScope.PROJECT,
    status: McpServerStatus.DISCONNECTED,
    enabled: false,
    capabilities: ['create_issue', 'list_prs', 'search_code'],
  },
];

const mockThemes: ThemeConfig[] = [
  {
    id: 'jetbrains-dark', name: 'JetBrains Dark', isDark: true,
    colors: {
      primary: '#0d47a1', background: '#1e1e1e', foreground: '#d4d4d4',
      muted: '#2d2d2d', mutedForeground: '#858585', accent: '#0d47a1',
      accentForeground: '#ffffff', destructive: '#f44336', border: '#3c3c3c',
      userMessage: '#0d47a1', aiMessage: '#2d2d2d', systemMessage: '#ff9800',
      codeBackground: '#1e1e1e', codeForeground: '#d4d4d4',
    },
    typography: { messageFont: 'Inter, sans-serif', codeFont: 'JetBrains Mono, monospace', fontSize: 14, fontSizeSmall: 12, fontSizeLarge: 16, lineHeight: 1.5 },
    spacing: { messageSpacing: 16, codeBlockPadding: 12, headerHeight: 48, sidebarWidth: 280 },
    borderRadius: { messageBubble: 12, codeBlock: 8, button: 6, input: 6, modal: 12 },
    shadow: { sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)', md: '0 4px 6px -1px rgb(0 0 0 / 0.1)', lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)', xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)' },
  },
  {
    id: 'jetbrains-light', name: 'JetBrains Light', isDark: false,
    colors: {
      primary: '#0d47a1', background: '#ffffff', foreground: '#333333',
      muted: '#f5f5f5', mutedForeground: '#666666', accent: '#0d47a1',
      accentForeground: '#ffffff', destructive: '#f44336', border: '#e0e0e0',
      userMessage: '#0d47a1', aiMessage: '#f5f5f5', systemMessage: '#ff9800',
      codeBackground: '#f5f5f5', codeForeground: '#333333',
    },
    typography: { messageFont: 'Inter, sans-serif', codeFont: 'JetBrains Mono, monospace', fontSize: 14, fontSizeSmall: 12, fontSizeLarge: 16, lineHeight: 1.5 },
    spacing: { messageSpacing: 16, codeBlockPadding: 12, headerHeight: 48, sidebarWidth: 280 },
    borderRadius: { messageBubble: 12, codeBlock: 8, button: 6, input: 6, modal: 12 },
    shadow: { sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)', md: '0 4px 6px -1px rgb(0 0 0 / 0.1)', lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)', xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)' },
  },
  {
    id: 'github-dark', name: 'GitHub Dark', isDark: true,
    colors: {
      primary: '#58a6ff', background: '#0d1117', foreground: '#c9d1d9',
      muted: '#161b22', mutedForeground: '#8b949e', accent: '#58a6ff',
      accentForeground: '#ffffff', destructive: '#f85149', border: '#30363d',
      userMessage: '#58a6ff', aiMessage: '#161b22', systemMessage: '#f0883e',
      codeBackground: '#0d1117', codeForeground: '#c9d1d9',
    },
    typography: { messageFont: '-apple-system, sans-serif', codeFont: 'SFMono-Regular, Consolas, monospace', fontSize: 14, fontSizeSmall: 12, fontSizeLarge: 16, lineHeight: 1.5 },
    spacing: { messageSpacing: 16, codeBlockPadding: 12, headerHeight: 48, sidebarWidth: 280 },
    borderRadius: { messageBubble: 8, codeBlock: 6, button: 6, input: 6, modal: 8 },
    shadow: { sm: '0 1px 2px rgba(0, 0, 0, 0.3)', md: '0 4px 6px rgba(0, 0, 0, 0.4)', lg: '0 10px 15px rgba(0, 0, 0, 0.5)', xl: '0 20px 25px -5px rgba(0, 0, 0, 0.6)' },
  },
  {
    id: 'vscode-dark', name: 'VS Code Dark', isDark: true,
    colors: {
      primary: '#007acc', background: '#1e1e1e', foreground: '#d4d4d4',
      muted: '#252526', mutedForeground: '#858585', accent: '#007acc',
      accentForeground: '#ffffff', destructive: '#f14c4c', border: '#3c3c3c',
      userMessage: '#007acc', aiMessage: '#252526', systemMessage: '#dcdcaa',
      codeBackground: '#1e1e1e', codeForeground: '#d4d4d4',
    },
    typography: { messageFont: '-apple-system, sans-serif', codeFont: 'Consolas, monospace', fontSize: 14, fontSizeSmall: 12, fontSizeLarge: 16, lineHeight: 1.5 },
    spacing: { messageSpacing: 16, codeBlockPadding: 12, headerHeight: 48, sidebarWidth: 280 },
    borderRadius: { messageBubble: 4, codeBlock: 0, button: 4, input: 4, modal: 8 },
    shadow: { sm: '0 2px 4px rgba(0, 0, 0, 0.4)', md: '0 4px 8px rgba(0, 0, 0, 0.5)', lg: '0 8px 16px rgba(0, 0, 0, 0.6)', xl: '0 12px 24px rgba(0, 0, 0, 0.7)' },
  },
];

// ============ Mock JavaEventsAPI ============

const eventListeners = new Map<string, Set<(data: any) => void>>();

const mockEvents: JavaEventsAPI = {
  on(event: string, handler: (data: any) => void): () => void {
    if (!eventListeners.has(event)) {
      eventListeners.set(event, new Set());
    }
    eventListeners.get(event)!.add(handler);
    return () => eventListeners.get(event)?.delete(handler);
  },
  off(event: string, handler: (data: any) => void): void {
    eventListeners.get(event)?.delete(handler);
  },
  emit(event: string, data: any): void {
    console.log(`[MockEvent] ${event}`, data);
    eventListeners.get(event)?.forEach((h) => {
      try { h(data); } catch (e) { console.error(e); }
    });
  },
};

// ============ Mock JavaBackendAPI ============

function delay(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

const chatResponse: ChatResponse = {
  content: '这是 mock 回复，实际运行时会由 Claude AI 生成回复。',
  tokensUsed: 42,
  executionTime: 500,
  model: 'claude-sonnet-4-6',
};

const mockBackend: JavaBackendAPI = {
  // 消息
  sendMessage: async (message: string): Promise<ChatResponse> => {
    console.log('[MockBackend] sendMessage:', message);
    await delay(500);
    return { ...chatResponse, content: `收到消息："${message}"\n\n${chatResponse.content}` };
  },

  sendMultimodalMessage: async (): Promise<ChatResponse> => {
    await delay(500);
    return { ...chatResponse, content: '收到多模态消息（mock 回复）' };
  },

  streamMessage: (message: string) => {
    console.log('[MockBackend] streamMessage:', message);
    const chunks = ['你好', '！这是', ' mock ', '流式输出', '的演示', '文本。'];
    const messageId = 'stream-' + Date.now();
    let i = 0;

    // 返回 Promise，模拟异步完成
    return new Promise<void>((resolve) => {
      const interval = setInterval(() => {
        if (i < chunks.length) {
          // Dev 模式：直接 emit 到 React eventBus（production 走 window.ccEvents → javaBridge）
          eventBus.emit(Events.STREAMING_CHUNK, { messageId, chunk: chunks[i] });
          i++;
        } else {
          eventBus.emit(Events.STREAMING_COMPLETE, { messageId });
          clearInterval(interval);
          resolve();
        }
      }, 200);
    });
  },

  cancelStreaming: () => console.log('[MockBackend] cancelStreaming'),

  // 底层通信：拦截 javaBridge.invoke() 的调用
  // javaBridge 监听 window.ccEvents.on('response', ...) 获取响应
  send: (request: any) => {
    const { queryId, action, params } = request || {};
    console.log('[MockBackend] send:', action, params);

    // 模拟异步响应（通过 window.ccEvents.emit 模拟 Java sendResponseToJs）
    setTimeout(() => {
      switch (action) {
        case 'sendMessage': {
          const msg = typeof params === 'object' ? params?.message : params;
          mockEvents.emit('response', {
            queryId,
            result: { content: `收到："${msg}"`, tokensUsed: 42, executionTime: 500, model: 'claude-sonnet-4-6' }
          });
          break;
        }
        case 'streamMessage': {
          const chunks = ['你', '好', '！这', '是', ' m', 'oc', 'k ', '流', '式', '。'];
          const messageId = 'stream-' + Date.now();
          let i = 0;
          const interval = setInterval(() => {
            if (i < chunks.length) {
              eventBus.emit(Events.STREAMING_CHUNK, { messageId, chunk: chunks[i] });
              i++;
            } else {
              eventBus.emit(Events.STREAMING_COMPLETE, { messageId });
              clearInterval(interval);
              // 模拟流结束后发送 response 事件
              mockEvents.emit('response', {
                queryId,
                result: { content: '[stream complete]', tokensUsed: chunks.join('').length, model: 'mock' }
              });
            }
          }, 150);
          break;
        }
        case 'getConfig':
          mockEvents.emit('response', { queryId, result: { currentThemeId: 'jetbrains-dark' } });
          break;
        case 'getThemes':
          mockEvents.emit('response', { queryId, result: mockThemes });
          break;
        case 'getModelConfig':
          mockEvents.emit('response', {
            queryId,
            result: {
              provider: 'anthropic',
              model: 'claude-sonnet-4-20250514',
              apiKey: '',
              baseUrl: '',
              maxTokens: 8192,
              temperature: 1.0,
              topP: 0.999,
              maxRetries: 3,
            }
          });
          break;
        case 'updateModelConfig':
          mockEvents.emit('response', { queryId, result: { success: true } });
          break;
        case 'createSession': {
          const name = typeof params === 'object' ? params?.name : 'New Session';
          const type = typeof params === 'object' ? params?.type : 'PROJECT';
          mockEvents.emit('response', {
            queryId,
            result: {
              id: 'session-' + Date.now(),
              name,
              type,
              messages: [],
              context: defaultSessionContext,
              createdAt: Date.now(),
              updatedAt: Date.now(),
              isActive: true,
              status: SessionStatus.IDLE,
              isInitialized: true,
            }
          });
          break;
        }
        case 'switchSession':
        case 'deleteSession':
        case 'cancelStreaming':
        case 'submitAnswer':
          mockEvents.emit('response', { queryId, result: { success: true } });
          break;
        default:
          mockEvents.emit('response', { queryId, result: null });
      }
    }, 100);
  },

  // 配置
  getConfig: async (key: string) => ({ key, value: 'mock-value' }),
  setConfig: async () => {},
  updateConfig: async () => {},

  // 模型配置
  getModelConfig: async () => ({
    provider: 'anthropic',
    model: 'claude-sonnet-4-20250514',
    apiKey: '',
    baseUrl: '',
    maxTokens: 8192,
    temperature: 1.0,
    topP: 0.999,
    maxRetries: 3,
  }),
  updateModelConfig: async () => {},

  // 主题
  getThemes: async (): Promise<ThemeConfig[]> => mockThemes,
  updateTheme: async () => {},
  saveCustomTheme: async () => {},
  deleteCustomTheme: async () => {},

  // 会话
  createSession: async (name: string, type: any): Promise<ChatSession> => ({
    id: 'session-' + Date.now(),
    name,
    type,
    messages: [],
    context: defaultSessionContext,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    isActive: true,
    status: SessionStatus.IDLE,
    isInitialized: true,
  }),
  switchSession: async () => {},
  deleteSession: async () => {},
  searchSessions: async (query: string): Promise<ChatSession[]> =>
    mockSessions.filter((s) => s.name.includes(query)),
  exportSession: async () => new Blob(['mock export'], { type: 'text/plain' }),
  importSession: async () => mockSessions[0]!,

  // Skills
  executeSkill: async (skillId: string) => ({
    skillId,
    response: 'Skill 执行结果（mock）',
    executionTime: 1000,
    tokensUsed: 150,
    success: true,
  }),
  getSkills: async (): Promise<Skill[]> => mockSkills,
  saveSkill: async () => {},
  deleteSkill: async () => {},

  // Agents
  startAgent: async () => {},
  stopAgent: async () => {},
  getAgents: async (): Promise<Agent[]> => mockAgents,
  saveAgent: async () => {},
  deleteAgent: async () => {},

  // MCP
  startMcpServer: async () => {},
  stopMcpServer: async () => {},
  testMcpServer: async (): Promise<TestResult> => ({ success: true, capabilities: ['read', 'write'] }),
  getMcpServers: async (): Promise<McpServer[]> => mockMcpServers,
  saveMcpServer: async () => {},
  deleteMcpServer: async () => {},

  // 交互问答
  submitAnswer: async () => {},

  // Chat Config
  getChatConfig: async () => ({
    conversationMode: 'AUTO',
    currentAgentId: null,
    streamingEnabled: true,
  }),

  updateChatConfig: async () => {},

  // Task Status
  getTaskStatus: async () => ({
    tasks: [],
    activeSubagents: [],
    diffRecords: [],
  }),

  // Slash Commands
  executeSlashCommand: async (command: string) => ({
    success: true,
    message: `Executed: ${command}`,
  }),

  // Provider Profiles
  getProviderProfiles: async () => ({
    profiles: [],
    activeProfileId: null,
  }),

  createProviderProfile: async (_profile: any) => ({
    success: true,
    id: `profile-${Date.now()}`,
  }),

  updateProviderProfile: async (_profile: any) => ({
    success: true,
  }),

  deleteProviderProfile: async (_profileId: string) => ({
    success: true,
  }),

  setActiveProviderProfile: async (_profileId: string | null) => ({
    success: true,
  }),

  reorderProviderProfiles: async (_orderedIds: string[]) => ({
    success: true,
  }),

  convertCcSwitchProfile: async (_profileId: string) => ({
    success: true,
    profile: {
      id: 'converted-local',
      name: 'Converted Profile (Local)',
      provider: 'anthropic',
      source: 'local',
      model: 'claude-sonnet-4-20250514',
      apiKey: '',
      baseUrl: '',
      sonnetModel: 'claude-sonnet-4-20250514',
      opusModel: 'claude-opus-4-20250514',
      maxModel: 'claude-3-5-haiku-20250514',
      maxRetries: 3,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    },
  }),
};

// ============ 注入 ============

export function injectMockBridge(): void {
  if (window.ccBackend && window.ccEvents) {
    console.log('[MockBridge] Java bridge already exists, skipping mock');
    return;
  }

  console.log('[MockBridge] Injecting mock bridge for standalone development');
  (window as any).ccBackend = mockBackend;
  (window as any).ccEvents = mockEvents;

  (window as any).__MOCK_DATA__ = {
    sessions: mockSessions,
    agents: mockAgents,
    skills: mockSkills,
    mcpServers: mockMcpServers,
  };
}
