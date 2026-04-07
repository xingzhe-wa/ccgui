# ClaudeCodeJet 前端技术架构设计

**文档版本**: 1.0
**创建日期**: 2026-04-08
**架构师**: Frontend Team

---

## 📐 架构全景图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer (UI)                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        React 18 Application                          │   │
│  │                                                                     │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │   │
│  │  │ ChatWindow  │  │SessionTabs  │  │ThemeEditor  │  │SkillsMgr    │ │   │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘ │   │
│  │                                                                     │   │
│  │  ┌───────────────────────────────────────────────────────────────┐ │   │
│  │  │                   Shared UI Components                        │ │   │
│  │  │  Button | Input | Dialog | Dropdown | Tabs | Tooltip | ...   │ │   │
│  │  └───────────────────────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                           State Management Layer                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Zustand Stores                               │   │
│  │                                                                     │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │   │
│  │  │  appStore    │  │sessionStore  │  │  themeStore  │  ...         │   │
│  │  │              │  │              │  │              │              │   │
│  │  │ - sessions   │  │ - messages   │  │ - theme      │              │   │
│  │  │ - sessionId  │  │ - streaming  │  │ - colors     │              │   │
│  │  │ - uiState    │  │ - progress   │  │ - fonts      │              │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                          Business Logic Layer                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Custom Hooks                                  │   │
│  │                                                                     │   │
│  │  useJavaBridge  │  useStreaming  │  useTheme  │  useVirtualList   │   │
│  │  useDebounce    │  useSession    │  usePromptOptimizer            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                          Communication Layer                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Java ↔ JavaScript Bridge                         │   │
│  │                                                                     │   │
│  │  window.ccBackend  ────────────────→  Java (JBCefJSQuery)          │   │
│  │  window.ccEvents  ←────────────────  Java (executeJavaScript)      │   │
│  │                                                                     │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │                   Event Bus (Pub/Sub)                       │   │   │
│  │  │                                                             │   │   │
│  │  │  - message:received   - streaming:chunk   - config:changed │   │   │
│  │  │  - session:changed    - task:progress     - theme:changed  │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                            Utility Layer                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  MarkdownParser  │  StorageManager  │  LogManager  │  CacheManager │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ 分层架构详解

### 1. 表现层 (Presentation Layer)

#### 1.1 组件层次结构

```
App (根组件)
├── ErrorBoundary (错误边界)
├── BrowserRouter (路由)
│   ├── ChatLayout (聊天布局)
│   │   ├── Header (顶部栏)
│   │   │   ├── ModelSelector
│   │   │   ├── ThemeSwitcher
│   │   │   └── SettingsButton
│   │   │
│   │   ├── SessionTabs (会话标签)
│   │   │   ├── TabItem
│   │   │   ├── NewTabButton
│   │   │   └── TabContextMenu
│   │   │
│   │   ├── MainContent (主内容区)
│   │   │   ├── MessageList (消息列表)
│   │   │   │   ├── VirtualList (虚拟滚动)
│   │   │   │   │   ├── MessageItem (用户消息)
│   │   │   │   │   ├── MessageItem (AI消息)
│   │   │   │   │   └── InteractiveQuestionPanel (交互式问题)
│   │   │   │   └── ScrollToBottomButton
│   │   │   │
│   │   │   └── ChatInput (输入区)
│   │   │       ├── TextInput
│   │   │       ├── AttachmentPreview
│   │   │       ├── ActionButtons
│   │   │       └── SendButton
│   │   │
│   │   └── PreviewPanel (预览面板 - 可选)
│   │       ├── CodePreview
│   │       └── DiffViewer
│   │
│   ├── TaskProgressDashboard (任务进度面板)
│   │   ├── ProgressBar
│   │   ├── StepList
│   │   └── TimeEstimation
│   │
│   └── SettingsPages (设置页面)
│       ├── ThemeEditor
│       ├── SkillsManager
│       ├── AgentsManager
│       └── McpServerManager
```

#### 1.2 组件设计原则

```typescript
// ✅ 正确的组件设计模式
interface ComponentProps {
  // 1. Props接口明确
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  className?: string;
}

// 2. 使用memo优化性能
const MyComponent = memo(({ value, onChange, disabled, className }: ComponentProps) => {
  // 3. 状态管理
  const [localState, setLocalState] = useState('');

  // 4. 副作用处理
  useEffect(() => {
    // 副作用逻辑
    return () => {
      // 清理函数
    };
  }, [value]);

  // 5. 事件处理使用useCallback
  const handleChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
  }, [onChange]);

  // 6. 渲染
  return (
    <input
      value={value}
      onChange={handleChange}
      disabled={disabled}
      className={cn('base-class', className)}
    />
  );
});

// 7. 显示名称用于调试
MyComponent.displayName = 'MyComponent';
```

---

### 2. 状态管理层 (State Management Layer)

#### 2.1 Zustand Store架构

```typescript
// ============ appStore.ts - 应用全局状态 ============
interface AppState {
  // 会话相关
  sessions: ChatSession[];
  currentSessionId: string;

  // UI状态
  sidebarOpen: boolean;
  previewPanelOpen: boolean;

  // 任务进度
  activeTaskProgress: TaskProgress | null;

  // 操作
  switchSession: (sessionId: string) => void;
  createSession: (name: string, type: SessionType) => Promise<ChatSession>;
  deleteSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<ChatSession>) => void;
  reorderSessions: (sessions: ChatSession[]) => void;

  // UI操作
  toggleSidebar: () => void;
  togglePreviewPanel: () => void;

  // 任务进度操作
  setTaskProgress: (progress: TaskProgress | null) => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  // 初始状态
  sessions: [],
  currentSessionId: '',
  sidebarOpen: true,
  previewPanelOpen: false,
  activeTaskProgress: null,

  // 操作实现
  switchSession: (sessionId) => {
    set({ currentSessionId: sessionId });
    window.ccBackend?.switchSession(sessionId);
  },

  createSession: async (name, type) => {
    const session = await window.ccBackend?.createSession(name, type);
    if (session) {
      set((state) => ({ sessions: [...state.sessions, session] }));
      get().switchSession(session.id);
    }
    return session;
  },

  deleteSession: (sessionId) => {
    set((state) => ({
      sessions: state.sessions.filter((s) => s.id !== sessionId)
    }));
    window.ccBackend?.deleteSession(sessionId);
  },

  updateSession: (sessionId, updates) => {
    set((state) => ({
      sessions: state.sessions.map((s) =>
        s.id === sessionId ? { ...s, ...updates } : s
      )
    }));
  },

  reorderSessions: (sessions) => set({ sessions }),

  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  togglePreviewPanel: () => set((state) => ({ previewPanelOpen: !state.previewPanelOpen })),

  setTaskProgress: (progress) => set({ activeTaskProgress: progress })
}));

// ============ sessionStore.ts - 会话状态 ============
interface SessionState {
  // 当前会话消息
  messages: Message[];

  // 注意：流式输出状态由独立的 streamingStore 管理（见 Phase 3）

  // 交互式请求状态
  pendingQuestions: InteractiveQuestion[];

  // 操作
  addMessage: (message: Message) => void;
  updateMessage: (messageId: string, updates: Partial<Message>) => void;
  removeMessage: (messageId: string) => void;

  // 交互式请求操作
  addQuestion: (question: InteractiveQuestion) => void;
  removeQuestion: (questionId: string) => void;
  submitAnswer: (questionId: string, answer: any) => void;
}

export const useSessionStore = create<SessionState>((set, get) => ({
  messages: [],
  // 注意：流式输出状态由独立的 streamingStore 管理（见 Phase 3）
  pendingQuestions: [],

  addMessage: (message) => {
    set((state) => ({ messages: [...state.messages, message] }));
  },

  updateMessage: (messageId, updates) => {
    set((state) => ({
      messages: state.messages.map((msg) =>
        msg.id === messageId ? { ...msg, ...updates } : msg
      )
    }));
  },

  removeMessage: (messageId) => {
    set((state) => ({
      messages: state.messages.filter((msg) => msg.id !== messageId)
    }));
  },

  addQuestion: (question) => {
    set((state) => ({
      pendingQuestions: [...state.pendingQuestions, question]
    }));
  },

  removeQuestion: (questionId) => {
    set((state) => ({
      pendingQuestions: state.pendingQuestions.filter((q) => q.questionId !== questionId)
    }));
  },

  submitAnswer: (questionId, answer) => {
    window.ccBackend?.submitAnswer(questionId, answer);
    get().removeQuestion(questionId);
  }
}));

// ============ themeStore.ts - 主题状态 ============
interface ThemeState {
  currentTheme: ThemeConfig;
  customThemes: ThemeConfig[];
  isEditing: boolean;

  setTheme: (theme: ThemeConfig) => void;
  saveCustomTheme: (theme: ThemeConfig) => void;
  deleteCustomTheme: (themeId: string) => void;
  startEditing: () => void;
  finishEditing: () => void;
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  currentTheme: ThemePresets.JetBrainsDark,
  customThemes: [],
  isEditing: false,

  setTheme: (theme) => {
    set({ currentTheme: theme });
    applyTheme(theme);
    window.ccBackend?.updateTheme(theme);
  },

  saveCustomTheme: (theme) => {
    set((state) => ({
      customThemes: [...state.customThemes, theme]
    }));
    window.ccBackend?.saveCustomTheme(theme);
  },

  deleteCustomTheme: (themeId) => {
    set((state) => ({
      customThemes: state.customThemes.filter((t) => t.id !== themeId)
    }));
  },

  startEditing: () => set({ isEditing: true }),
  finishEditing: () => set({ isEditing: false })
}));
```

#### 2.2 Store持久化策略

```typescript
// 使用Zustand持久化中间件
import { persist } from 'zustand/middleware';

export const useThemeStore = create(
  persist<ThemeState>(
    (set, get) => ({
      // ... 状态定义
    }),
    {
      name: 'ccgui-theme-storage',
      // 使用自定义存储（通过Java后端）
      storage: {
        getItem: (name) => {
          return window.ccBackend?.getConfig(name) ?? null;
        },
        setItem: (name, value) => {
          window.ccBackend?.setConfig(name, value);
        }
      },
      // 部分持久化
      partialize: (state) => ({
        currentTheme: state.currentTheme,
        customThemes: state.customThemes
      })
    }
  )
);
```

---

### 3. 业务逻辑层 (Business Logic Layer)

#### 3.1 自定义Hooks架构

```typescript
// ============ useJavaBridge.ts - Java通信Hook ============
export function useJavaBridge() {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // 等待Java后端注入
    const checkReady = () => {
      if (window.ccBackend && window.ccEvents) {
        setIsReady(true);
        return true;
      }
      return false;
    };

    if (!checkReady()) {
      const interval = setInterval(checkReady, 100);
      return () => clearInterval(interval);
    }
  }, []);

  const sendMessage = useCallback(async (message: string): Promise<ChatResponse> => {
    if (!isReady) {
      throw new Error('Java bridge not ready');
    }

    try {
      return await window.ccBackend.sendMessage(message);
    } catch (err) {
      setError(err as Error);
      throw err;
    }
  }, [isReady]);

  const getConfig = useCallback(async (key: string): Promise<any> => {
    if (!isReady) {
      throw new Error('Java bridge not ready');
    }
    return window.ccBackend.getConfig(key);
  }, [isReady]);

  return {
    isReady,
    error,
    sendMessage,
    getConfig,
    backend: window.ccBackend
  };
}

// ============ useStreaming.ts - 流式输出Hook ============
export function useStreaming(messageId: string) {
  const [content, setContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const contentRef = useRef('');

  useEffect(() => {
    setIsStreaming(true);
    setError(null);
    contentRef.current = '';

    // 通过 EventBus 监听流式输出事件
    const unsubChunk = eventBus.on(Events.STREAMING_CHUNK, (data: { chunk: string }) => {
      contentRef.current += data.chunk;
      setContent(contentRef.current);
    });

    const unsubComplete = eventBus.on(Events.STREAMING_COMPLETE, () => {
      setIsStreaming(false);
    });

    const unsubError = eventBus.on(Events.STREAMING_ERROR, (data: { error: string }) => {
      setError(data.error);
      setIsStreaming(false);
    });

    // 组件卸载时自动取消所有订阅
    return () => {
      unsubChunk();
      unsubComplete();
      unsubError();
    };
  }, [messageId]);

  const cancel = useCallback(() => {
    eventBus.emit(Events.STREAMING_CANCEL, { messageId });
    setIsStreaming(false);
  }, [messageId]);

  return {
    content,
    isStreaming,
    error,
    cancel
  };
}

// ============ useVirtualList.ts - 虚拟滚动Hook ============
export function useVirtualList<T>({
  items,
  estimateSize,
  overscan = 5
}: {
  items: T[];
  estimateSize: (index: number) => number;
  overscan?: number;
}) {
  const parentRef = useRef<HTMLDivElement>(null);
  const [containerSize, setContainerSize] = useState({ width: 0, height: 0 });

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => parentRef.current,
    estimateSize,
    overscan
  });

  useEffect(() => {
    const resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect;
        setContainerSize({ width, height });
      }
    });

    if (parentRef.current) {
      resizeObserver.observe(parentRef.current);
    }

    return () => resizeObserver.disconnect();
  }, []);

  return {
    parentRef,
    virtualizer,
    virtualItems: virtualizer.getVirtualItems(),
    totalSize: virtualizer.getTotalSize()
  };
}

// ============ useDebounce.ts - 防抖Hook ============
export function useDebounce<T>(value: T, delay: number = 300): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => clearTimeout(handler);
  }, [value, delay]);

  return debouncedValue;
}

// ============ useTheme.ts - 主题Hook ============
export function useTheme() {
  const { currentTheme, setTheme, customThemes } = useThemeStore();

  useEffect(() => {
    const root = document.documentElement;

    // 应用CSS变量
    Object.entries(currentTheme.colors).forEach(([key, value]) => {
      root.style.setProperty(`--${kebabCase(key)}`, value);
    });

    // 应用data-theme
    root.setAttribute('data-theme', currentTheme.isDark ? 'dark' : 'light');

    // 应用字体
    root.style.setProperty('--font-message', currentTheme.typography.messageFont);
    root.style.setProperty('--font-code', currentTheme.typography.codeFont);
    root.style.setProperty('--font-size', `${currentTheme.typography.fontSize}px`);

    // 应用间距
    Object.entries(currentTheme.spacing).forEach(([key, value]) => {
      root.style.setProperty(`--spacing-${kebabCase(key)}`, `${value}px`);
    });

    // 应用圆角
    Object.entries(currentTheme.borderRadius).forEach(([key, value]) => {
      root.style.setProperty(`--radius-${kebabCase(key)}`, `${value}px`);
    });
  }, [currentTheme]);

  return {
    theme: currentTheme,
    setTheme,
    customThemes,
    isDark: currentTheme.isDark
  };
}
```

---

### 4. 通信层 (Communication Layer)

#### 4.1 Java ↔ JavaScript 桥接架构

```typescript
// ============ Java全局类型定义 ============

// Java注入的全局对象
declare global {
  interface Window {
    // Java后端API
    ccBackend: {
      // 消息相关
      sendMessage(message: string): Promise<ChatResponse>;
      streamMessage(message: string): void;
      cancelStreaming(sessionId: string): void;

      // 配置相关
      getConfig(key: string): any;
      setConfig(key: string, value: any): Promise<void>;
      updateConfig(config: Partial<ConfigState>): Promise<void>;

      // 主题相关
      getThemes(): Promise<ThemeConfig[]>;
      updateTheme(theme: ThemeConfig): Promise<void>;
      saveCustomTheme(theme: ThemeConfig): Promise<void>;

      // 会话相关
      createSession(name: string, type: SessionType): Promise<ChatSession>;
      switchSession(sessionId: string): Promise<void>;
      deleteSession(sessionId: string): Promise<void>;
      searchSessions(query: string): Promise<ChatSession[]>;
      exportSession(sessionId: string, format: 'markdown' | 'pdf'): Promise<Blob>;

      // 生态相关
      executeSkill(skillId: string, context: ExecutionContext): Promise<SkillResult>;
      startAgent(agentId: string, task: AgentTask): Promise<void>;
      startMcpServer(serverId: string): Promise<void>;
      stopMcpServer(serverId: string): Promise<void>;
      testMcpServer(serverId: string): Promise<TestResult>;
    };

    // 事件总线
    ccEvents: {
      on(event: string, handler: (data: any) => void): () => void;
      off(event: string, handler: (data: any) => void): void;
      emit(event: string, data: any): void;
    };

    // 流式输出专用
    ccStreaming?: {
      onChunk(chunk: string): void;
      onDone(): void;
      onError(error: string): void;
    };
  }
}

// ============ 通信封装层 ============

class JavaBridge {
  private queryId = 0;
  private pendingRequests = new Map<number, {
    resolve: (value: any) => void;
    reject: (error: Error) => void;
  }>();

  private eventListeners = new Map<string, Set<(data: any) => void>>();

  async init() {
    // 等待Java注入完成
    await this.waitForReady();

    // 监听Java响应
    window.ccEvents?.on('response', (data: any) => {
      const { queryId, result, error } = data;
      const pending = this.pendingRequests.get(queryId);

      if (pending) {
        if (error) {
          pending.reject(new Error(error));
        } else {
          pending.resolve(result);
        }
        this.pendingRequests.delete(queryId);
      }
    });

    // 监听事件并转发
    this.setupEventForwarding();
  }

  private async waitForReady(): Promise<void> {
    return new Promise((resolve) => {
      const checkReady = () => {
        if (window.ccBackend && window.ccEvents) {
          resolve();
        } else {
          requestAnimationFrame(checkReady);
        }
      };
      checkReady();
    });
  }

  private setupEventForwarding() {
    // 监听所有Java事件并转发给本地监听器
    const events = [
      'message:received',
      'streaming:chunk',
      'streaming:complete',
      'streaming:error',
      'config:changed',
      'theme:changed',
      'session:changed',
      'session:created',
      'session:deleted',
      'task:progress',
      'task:step:complete',
      'question:asked'
    ];

    events.forEach((event) => {
      window.ccEvents?.on(event, (data) => {
        this.forwardEvent(event, data);
      });
    });
  }

  private forwardEvent(event: string, data: any) {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach((handler) => {
        try {
          handler(data);
        } catch (error) {
          console.error(`Error in event handler for ${event}:`, error);
        }
      });
    }
  }

  // 通用调用方法
  async invoke<T>(action: string, params?: any): Promise<T> {
    const queryId = ++this.queryId;

    return new Promise((resolve, reject) => {
      // 设置超时（30秒）
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(queryId);
        reject(new Error(`Java call timeout: ${action}`));
      }, 30000);

      // 存储pending请求
      this.pendingRequests.set(queryId, {
        resolve: (value) => {
          clearTimeout(timeout);
          resolve(value);
        },
        reject: (error) => {
          clearTimeout(timeout);
          reject(error);
        }
      });

      // 调用Java
      try {
        window.ccBackend.send({
          queryId,
          action,
          params
        });
      } catch (error) {
        clearTimeout(timeout);
        this.pendingRequests.delete(queryId);
        reject(error);
      }
    });
  }

  // 事件监听
  on(event: string, handler: (data: any) => void): () => void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, new Set());
    }
    this.eventListeners.get(event)!.add(handler);

    // 返回取消订阅函数
    return () => {
      this.off(event, handler);
    };
  }

  off(event: string, handler: (data: any) => void): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.delete(handler);
      if (listeners.size === 0) {
        this.eventListeners.delete(event);
      }
    }
  }

  emit(event: string, data: any): void {
    window.ccEvents?.emit(event, data);
  }

  // 具体API方法封装
  async sendMessage(message: string): Promise<ChatResponse> {
    return this.invoke<ChatResponse>('sendMessage', { message });
  }

  async streamMessage(message: string): Promise<void> {
    return this.invoke<void>('streamMessage', { message });
  }

  async cancelStreaming(sessionId: string): Promise<void> {
    return this.invoke<void>('cancelStreaming', { sessionId });
  }

  async getConfig(key: string): Promise<any> {
    return this.invoke<any>('getConfig', { key });
  }

  async updateTheme(theme: ThemeConfig): Promise<void> {
    return this.invoke<void>('updateTheme', { theme });
  }

  async createSession(name: string, type: SessionType): Promise<ChatSession> {
    return this.invoke<ChatSession>('createSession', { name, type });
  }

  async switchSession(sessionId: string): Promise<void> {
    return this.invoke<void>('switchSession', { sessionId });
  }

  async deleteSession(sessionId: string): Promise<void> {
    return this.invoke<void>('deleteSession', { sessionId });
  }
}

// 导出单例
export const javaBridge = new JavaBridge();

// 自动初始化
javaBridge.init().catch((error) => {
  console.error('Failed to initialize Java bridge:', error);
});
```

#### 4.2 事件总线架构

```typescript
// ============ event-bus.ts - 事件总线 ============
// 完整实现见 01-phase1-foundation.md 事件总线章节

type EventHandler<T = any> = (data: T) => void;

class EventBus {
  private listeners = new Map<string, Set<EventHandler>>();

  on<T = any>(event: string, handler: EventHandler<T>): () => void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)!.add(handler);

    // 返回取消订阅函数
    return () => {
      this.off(event, handler);
    };
  }

  off<T = any>(event: string, handler: EventHandler<T>): void {
    const handlers = this.listeners.get(event);
    if (handlers) {
      handlers.delete(handler);
      if (handlers.size === 0) {
        this.listeners.delete(event);
      }
    }
  }

  emit<T = any>(event: string, data: T): void {
    const handlers = this.listeners.get(event);
    if (handlers) {
      // 创建副本避免在遍历时修改
      Array.from(handlers).forEach((handler) => {
        try {
          handler(data);
        } catch (error) {
          console.error(`Error in event handler for ${event}:`, error);
        }
      });
    }
  }

  once<T = any>(event: string, handler: EventHandler<T>): () => void {
    const wrappedHandler: EventHandler<T> = (data) => {
      handler(data);
      this.off(event, wrappedHandler);
    };
    return this.on(event, wrappedHandler);
  }

  clear(): void {
    this.listeners.clear();
  }

  clearEvent(event: string): void {
    this.listeners.delete(event);
  }

  getListenerCount(event: string): number {
    return this.listeners.get(event)?.size ?? 0;
  }
}

// 导出单例
export const eventBus = new EventBus();

// 定义事件类型
export const Events = {
  // 消息事件
  MESSAGE_RECEIVED: 'message:received',
  MESSAGE_SEND: 'message:send',

  // 流式输出事件
  STREAMING_CHUNK: 'streaming:chunk',
  STREAMING_COMPLETE: 'streaming:complete',
  STREAMING_ERROR: 'streaming:error',
  STREAMING_CANCEL: 'streaming:cancel',

  // 配置事件
  CONFIG_CHANGED: 'config:changed',
  THEME_CHANGED: 'theme:changed',

  // 会话事件
  SESSION_CHANGED: 'session:changed',
  SESSION_CREATED: 'session:created',
  SESSION_DELETED: 'session:deleted',
  SESSION_UPDATED: 'session:updated',

  // 任务事件
  TASK_PROGRESS: 'task:progress',
  TASK_STEP_COMPLETE: 'task:step:complete',
  TASK_COMPLETE: 'task:complete',
  TASK_ERROR: 'task:error',

  // 交互式请求事件
  QUESTION_ASKED: 'question:asked',
  QUESTION_ANSWERED: 'question:answered',

  // UI事件
  SIDEBAR_TOGGLE: 'ui:sidebar:toggle',
  PREVIEW_TOGGLE: 'ui:preview:toggle',
  MODAL_OPEN: 'ui:modal:open',
  MODAL_CLOSE: 'ui:modal:close'
} as const;

export type EventName = typeof Events[keyof typeof Events];
```

---

### 5. 工具层 (Utility Layer)

#### 5.1 Markdown解析器

```typescript
// ============ markdown-parser.ts ============

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import { CodeBlock } from '../components/markdown/CodeBlock';
import { LatexRenderer } from '../components/markdown/LatexRenderer';

// Markdown解析缓存
const markdownCache = new Map<string, React.ReactNode>();
const MAX_CACHE_SIZE = 100;

export function parseMarkdown(content: string): React.ReactNode {
  // 检查缓存
  const cached = markdownCache.get(content);
  if (cached) {
    return cached;
  }

  // 解析Markdown
  const rendered = (
    <ReactMarkdown
      remarkPlugins={[remarkGfm, remarkMath]}
      rehypePlugins={[rehypeKatex]}
      components={{
        // 代码块渲染
        code: CodeBlock,

        // LaTeX公式渲染
        span: ({ node, inline, ...props }) => {
          if (inline && props.className?.includes('math-inline')) {
            return <LatexRenderer inline {...props} />;
          }
          return <span {...props} />;
        },

        // 图片渲染
        img: ({ src, alt, ...props }) => (
          <img
            src={src}
            alt={alt}
            loading="lazy"
            className="max-w-full h-auto rounded"
            {...props}
          />
        ),

        // 链接渲染
        a: ({ href, children, ...props }) => (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary hover:underline"
            {...props}
          >
            {children}
          </a>
        ),

        // 表格渲染
        table: ({ children }) => (
          <div className="overflow-x-auto">
            <table className="min-w-full border-collapse">
              {children}
            </table>
          </div>
        )
      }}
    >
      {content}
    </ReactMarkdown>
  );

  // 缓存结果
  if (markdownCache.size >= MAX_CACHE_SIZE) {
    // 删除最早的缓存
    const firstKey = markdownCache.keys().next().value;
    markdownCache.delete(firstKey);
  }
  markdownCache.set(content, rendered);

  return rendered;
}

// 清除缓存
export function clearMarkdownCache(): void {
  markdownCache.clear();
}

// 预处理Markdown（处理特殊语法）
export function preprocessMarkdown(content: string): string {
  return content
    // 处理任务分解标记
    .replace(/\[Task (\d+)[:\s]+([^\]]+)\]/g, '**任务 $1**: $2')
    // 处理代码块语言标识
    .replace(/```(\w+)?\n/g, (match, lang) => {
      return lang ? `\`\`\`${lang}\n` : '\`\`\`text\n';
    })
    // 处理引用标记
    .replace(/@(\w+)/g, '`@$1`');
}
```

#### 5.2 存储管理器

```typescript
// ============ storage.ts ============

class StorageManager {
  private prefix = 'ccgui_';

  // 设置项
  setItem<T>(key: string, value: T): void {
    try {
      const serialized = JSON.stringify(value);
      sessionStorage.setItem(this.prefix + key, serialized);
    } catch (error) {
      console.error('Failed to set item:', error);
    }
  }

  // 获取项
  getItem<T>(key: string): T | null {
    try {
      const item = sessionStorage.getItem(this.prefix + key);
      if (item === null) return null;
      return JSON.parse(item) as T;
    } catch (error) {
      console.error('Failed to get item:', error);
      return null;
    }
  }

  // 删除项
  removeItem(key: string): void {
    sessionStorage.removeItem(this.prefix + key);
  }

  // 清空所有项
  clear(): void {
    const keys = Object.keys(sessionStorage);
    keys.forEach((key) => {
      if (key.startsWith(this.prefix)) {
        sessionStorage.removeItem(key);
      }
    });
  }

  // 获取所有键
  keys(): string[] {
    const allKeys = Object.keys(sessionStorage);
    return allKeys
      .filter((key) => key.startsWith(this.prefix))
      .map((key) => key.slice(this.prefix.length));
  }

  // 批量设置
  setItems<T extends Record<string, any>>(items: T): void {
    Object.entries(items).forEach(([key, value]) => {
      this.setItem(key, value);
    });
  }

  // 批量获取
  getItems<T extends string[]>(keys: T): Record<T[number], any> {
    return keys.reduce((result, key) => {
      result[key] = this.getItem(key);
      return result;
    }, {} as Record<T[number], any>);
  }
}

export const storageManager = new StorageManager();
```

---

## 🎨 样式架构

### CSS变量系统

```css
/* ============ globals.css ============ */

@tailwind base;
@tailwind components;
@tailwind utilities;

/* CSS变量定义 */
:root {
  /* 颜色系统 */
  --color-primary: hsl(var(--primary));
  --color-background: hsl(var(--background));
  --color-foreground: hsl(var(--foreground));
  --color-muted: hsl(var(--muted));
  --color-muted-foreground: hsl(var(--muted-foreground));
  --color-accent: hsl(var(--accent));
  --color-accent-foreground: hsl(var(--accent-foreground));
  --color-destructive: hsl(var(--destructive));
  --color-border: hsl(var(--border));

  /* 消息颜色 */
  --color-user-message: hsl(var(--user-message));
  --color-ai-message: hsl(var(--ai-message));
  --color-system-message: hsl(var(--system-message));

  /* 代码块颜色 */
  --color-code-background: hsl(var(--code-background));
  --color-code-foreground: hsl(var(--code-foreground));

  /* 字体系统 */
  --font-message: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  --font-code: 'JetBrains Mono', 'Fira Code', monospace;
  --font-size: 14px;
  --font-size-small: 12px;
  --font-size-large: 16px;

  /* 间距系统 */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;

  /* 圆角系统 */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;

  /* 阴影系统 */
  --shadow-sm: 0 1px 2px 0 rgb(0 0 0 / 0.05);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1);
  --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1);

  /* 过渡系统 */
  --transition-fast: 150ms cubic-bezier(0.4, 0, 0.2, 1);
  --transition-base: 200ms cubic-bezier(0.4, 0, 0.2, 1);
  --transition-slow: 300ms cubic-bezier(0.4, 0, 0.2, 1);
}

/* 亮色主题 */
[data-theme="light"] {
  --primary: 221 83% 53%;
  --background: 0 0% 100%;
  --foreground: 222 47% 11%;
  --muted: 210 40% 96%;
  --muted-foreground: 215 16% 47%;
  --accent: 210 40% 96%;
  --accent-foreground: 222 47% 11%;
  --destructive: 0 84% 60%;
  --border: 214 32% 91%;
  --user-message: 221 83% 53%;
  --ai-message: 210 40% 96%;
  --system-message: 38 92% 50%;
  --code-background: 210 40% 96%;
  --code-foreground: 222 47% 11%;
}

/* 暗色主题 */
[data-theme="dark"] {
  --primary: 217 91% 60%;
  --background: 222 47% 11%;
  --foreground: 210 40% 98%;
  --muted: 217 33% 17%;
  --muted-foreground: 215 20% 65%;
  --accent: 217 33% 17%;
  --accent-foreground: 210 40% 98%;
  --destructive: 0 62% 30%;
  --border: 217 33% 17%;
  --user-message: 217 91% 60%;
  --ai-message: 217 33% 17%;
  --system-message: 38 92% 50%;
  --code-background: 0 0% 15%;
  --code-foreground: 210 40% 98%;
}

/* 基础样式重置 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: var(--font-message);
  font-size: var(--font-size);
  line-height: 1.5;
  color: hsl(var(--foreground));
  background-color: hsl(var(--background));
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: hsl(var(--muted-foreground) / 0.3);
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: hsl(var(--muted-foreground) / 0.5);
}

/* 选择文本样式 */
::selection {
  background-color: hsl(var(--primary) / 0.3);
  color: hsl(var(--foreground));
}

/* 焦点样式 */
:focus-visible {
  outline: 2px solid hsl(var(--primary));
  outline-offset: 2px;
}
```

---

## 🚀 性能优化策略

### 虚拟滚动实现

```typescript
// MessageList组件使用虚拟滚动
import { useVirtualizer } from '@tanstack/react-virtual';

export function MessageList({ messages }: { messages: Message[] }) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 100, // 预估消息高度
    overscan: 5 // 预渲染数量
  });

  return (
    <div ref={parentRef} className="h-full overflow-auto">
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          position: 'relative'
        }}
      >
        {virtualizer.getVirtualItems().map((virtualItem) => (
          <div
            key={virtualItem.key}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${virtualItem.start}px)`
            }}
          >
            <MessageItem message={messages[virtualItem.index]} />
          </div>
        ))}
      </div>
    </div>
  );
}
```

### 代码分割与懒加载

```typescript
// 使用React.lazy进行代码分割
import { lazy, Suspense } from 'react';

// 懒加载设置页面
const ThemeEditor = lazy(() => import('../features/theme/components/ThemeEditor'));
const SkillsManager = lazy(() => import('../features/skills/components/SkillsManager'));
const AgentsManager = lazy(() => import('../features/agents/components/AgentsManager'));
const McpServerManager = lazy(() => import('../features/mcp/components/McpServerManager'));

// 路由配置
const routes = [
  {
    path: '/settings/theme',
    element: (
      <Suspense fallback={<LoadingSpinner />}>
        <ThemeEditor />
      </Suspense>
    )
  },
  // ...
];
```

---

## 📋 总结

本架构设计遵循以下核心原则：

1. **分层清晰**：表现层、状态层、业务层、通信层、工具层职责明确
2. **可扩展性**：模块化设计，易于添加新功能
3. **类型安全**：完整的TypeScript类型定义
4. **性能优化**：虚拟滚动、代码分割、缓存策略
5. **可维护性**：统一的代码规范和组件设计模式

---

**相关文档**：
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
