# Phase 1: 基础架构搭建

**阶段周期**: Week 1-3 (3周)
**阶段目标**: 搭建React + TypeScript项目脚手架，实现JCEF环境搭建与Java-JS通信桥接，建立状态管理框架

---

## 📋 本阶段任务清单

### Week 1: 项目初始化与脚手架

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T1-W1-01** | React项目初始化 | 0.5人天 | 项目骨架 | 可运行`npm run dev` |
| **T1-W1-02** | TypeScript配置 | 0.5人天 | tsconfig.json | 严格模式通过 |
| **T1-W1-03** | TailwindCSS配置 | 0.5人天 | tailwind.config.js | CSS类正常工作 |
| **T1-W1-04** | 项目目录结构创建 | 0.5人天 | 完整目录结构 | 符合架构设计 |
| **T1-W1-05** | ESLint + Prettier配置 | 0.5人天 | .eslintrc, .prettierrc | 代码规范检查通过 |
| **T1-W1-06** | 路径别名配置 | 0.5人天 | vite.config.ts | 可使用@/导入 |

### Week 2: JCEF环境搭建与通信桥接

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T1-W2-01** | JCEF浏览器初始化 | 1人天 | JcefBrowser.tsx | 可加载空白页面 |
| **T1-W2-02** | Java全局类型定义 | 1人天 | bridge.ts类型 | TypeScript无报错 |
| **T1-W2-03** | JBCefJSQuery端点注册 | 1.5人天 | java-bridge.ts | 可调用Java方法 |
| **T1-W2-04** | 事件总线实现 | 1人天 | event-bus.ts | 事件订阅/发布正常 |
| **T1-W2-05** | 通信封装层 | 1.5人天 | JavaBridge类 | 双向通信正常 |
| **T1-W2-06** | 错误处理与重试机制 | 1人天 | error-handler.ts | 超时重试正常 |

### Week 3: 状态管理与路由系统

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T1-W3-01** | Zustand Store架构设计 | 1人天 | stores/目录结构 | Store设计文档 |
| **T1-W3-02** | appStore实现 | 1人天 | appStore.ts | 状态读写正常 |
| **T1-W3-03** | sessionStore实现 | 1人天 | sessionStore.ts | 会话状态管理正常 |
| **T1-W3-04** | themeStore实现 | 1人天 | themeStore.ts | 主题状态管理正常 |
| **T1-W3-05** | React Router配置 | 1人天 | router.tsx | 路由跳转正常 |
| **T1-W3-06** | 基础布局组件 | 1人天 | AppLayout.tsx | 布局显示正常 |

---

## 🎯 Week 1: 项目初始化与脚手架

### T1-W1-01: React项目初始化

**任务描述**: 使用Vite创建React + TypeScript项目

**实现步骤**:

```bash
# 1. 创建项目
npm create vite@latest ccgui-frontend -- --template react-ts
cd ccgui-frontend

# 2. 安装依赖
npm install

# 3. 安装核心依赖
npm install \
  react@18.3.1 \
  react-dom@18.3.1 \
  zustand@4.5.0 \
  react-router-dom@6.21.1

# 4. 安装开发依赖
npm install -D \
  @types/react@18.3.0 \
  @types/react-dom@18.3.0 \
  @vitejs/plugin-react@4.2.1 \
  typescript@5.3.3 \
  vite@5.0.11
```

**配置文件**:

```typescript
// vite.config.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@/shared': path.resolve(__dirname, './src/shared'),
      '@/features': path.resolve(__dirname, './src/features'),
      '@/lib': path.resolve(__dirname, './src/lib'),
      '@/styles': path.resolve(__dirname, './src/styles')
    }
  },
  server: {
    port: 3000,
    // JCEF环境下的特殊配置
    strictPort: true,
    hmr: false // JCEF不支持HMR
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor': ['react', 'react-dom'],
          'router': ['react-router-dom'],
          'state': ['zustand']
        }
      }
    }
  }
});
```

**验收标准**:
- ✅ `npm run dev` 可启动开发服务器
- ✅ 访问 http://localhost:3000 显示React欢迎页面
- ✅ 控制台无错误

---

### T1-W1-02: TypeScript配置

**任务描述**: 配置TypeScript严格模式和路径别名

**配置文件**:

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,

    /* 严格模式 */
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "noImplicitReturns": true,
    "noUncheckedIndexedAccess": true,

    /* 路径别名 */
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"],
      "@/shared/*": ["./src/shared/*"],
      "@/features/*": ["./src/features/*"],
      "@/lib/*": ["./src/lib/*"],
      "@/styles/*": ["./src/styles/*"]
    },

    /* 模块解析 */
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",

    /* 类型声明 */
    "declaration": true,
    "declarationDir": "./dist-types"
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

**验收标准**:
- ✅ TypeScript编译无错误
- ✅ 路径别名 `@/` 可以正常使用
- ✅ 严格模式检查开启

---

### T1-W1-03: TailwindCSS配置

**任务描述**: 配置TailwindCSS和CSS变量系统

**安装依赖**:

```bash
npm install -D \
  tailwindcss@3.4.1 \
  postcss@8.4.33 \
  autoprefixer@10.4.16 \
  class-variance-authority@0.7.0

npm install \
  clsx@2.1.0 \
  tailwind-merge@2.2.0
```

**配置文件**:

```javascript
// tailwind.config.js
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))'
        },
        // ... 更多颜色
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)'
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' }
        }
      }
    }
  },
  plugins: []
};
```

**工具函数**:

```typescript
// src/shared/utils/cn.ts
import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
```

**验收标准**:
- ✅ TailwindCSS类名正常工作
- ✅ `cn()` 工具函数可以合并类名
- ✅ CSS变量系统正常

---

### T1-W1-04: 项目目录结构创建

**任务描述**: 创建符合架构设计的完整目录结构

**目录结构**:

```bash
src/
├── main/                           # 主入口
│   ├── index.tsx                   # React入口
│   ├── App.tsx                     # 根组件
│   └── main.css                    # 全局样式
│
├── shared/                         # 共享模块
│   ├── components/                 # 共享组件
│   │   ├── ui/                     # 基础UI组件
│   │   │   ├── button/
│   │   │   ├── input/
│   │   │   └── dialog/
│   │   ├── layout/
│   │   └── markdown/
│   │
│   ├── hooks/                      # 共享Hooks
│   │   ├── useJavaBridge.ts
│   │   ├── useTheme.ts
│   │   └── useDebounce.ts
│   │
│   ├── stores/                     # 全局Store
│   │   ├── appStore.ts
│   │   ├── sessionStore.ts
│   │   └── themeStore.ts
│   │
│   ├── types/                      # 类型定义
│   │   ├── index.ts
│   │   ├── chat.ts
│   │   ├── session.ts
│   │   └── bridge.ts
│   │
│   ├── utils/                      # 工具函数
│   │   ├── java-bridge.ts
│   │   ├── event-bus.ts
│   │   ├── storage.ts
│   │   └── cn.ts
│   │
│   └── constants/                  # 常量定义
│       ├── themes.ts
│       └── config.ts
│
├── features/                       # 功能模块
│   ├── chat/
│   ├── session/
│   └── theme/
│
├── lib/                            # 核心库
│   └── java-bridge.ts
│
└── styles/                         # 样式文件
    ├── globals.css
    └── themes/
```

**创建脚本**:

```bash
# 创建目录结构的脚本
mkdir -p src/{main,shared/{components/{ui,layout,markdown},hooks,stores,types,utils,constants},features/{chat,session,theme},lib,styles/themes}
touch src/main/{index.tsx,App.tsx,main.css}
```

**验收标准**:
- ✅ 所有目录创建完成
- ✅ 符合架构设计规范
- ✅ 每个目录有对应的index.ts或占位文件

---

### T1-W1-05: ESLint + Prettier配置

**配置文件**:

```javascript
// .eslintrc.cjs
module.exports = {
  root: true,
  env: {
    browser: true,
    es2021: true
  },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
    'plugin:react/recommended',
    'plugin:react/jsx-runtime'
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: {
      jsx: true
    }
  },
  plugins: [
    '@typescript-eslint',
    'react-hooks',
    'react'
  ],
  rules: {
    'react/react-in-jsx-scope': 'off',
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    '@typescript-eslint/no-explicit-any': 'warn',
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn'
  },
  settings: {
    react: {
      version: 'detect'
    }
  }
};
```

```json
// .prettierrc
{
  "semi": true,
  "singleQuote": true,
  "tabWidth": 2,
  "trailingComma": "es5",
  "printWidth": 100,
  "arrowParens": "always"
}
```

**验收标准**:
- ✅ `npm run lint` 无错误
- ✅ 代码格式化正常
- ✅ 编辑器集成正常

---

## 🎯 Week 2: JCEF环境搭建与通信桥接

### T1-W2-01: JCEF浏览器初始化

**任务描述**: 创建JCEF浏览器初始化组件

**实现代码**:

```typescript
// src/main/components/JcefBrowser.tsx
import { useEffect, useRef, useState } from 'react';
import { cn } from '@/shared/utils/cn';

interface JcefBrowserProps {
  className?: string;
  onReady?: () => void;
  onError?: (error: Error) => void;
}

export const JcefBrowser = ({ className, onReady, onError }: JcefBrowserProps) => {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // 检测JCEF环境
    const checkJcefReady = () => {
      // 在JCEF环境中，window.ccBackend会被Java注入
      if (window.ccBackend && window.ccEvents) {
        setIsReady(true);
        onReady?.();
      } else {
        // 非JCEF环境，显示警告
        const err = new Error('Not running in JCEF environment');
        setError(err);
        onError?.(err);
      }
    };

    // 立即检查一次
    checkJcefReady();

    // 每隔100ms检查一次，最多检查50次（5秒）
    let attempts = 0;
    const maxAttempts = 50;
    const interval = setInterval(() => {
      attempts++;
      if (attempts >= maxAttempts) {
        clearInterval(interval);
        if (!isReady) {
          const err = new Error('JCEF environment initialization timeout');
          setError(err);
          onError?.(err);
        }
      } else {
        checkJcefReady();
      }
    }, 100);

    return () => clearInterval(interval);
  }, [onReady, onError, isReady]);

  if (error) {
    return (
      <div className={cn('flex h-full items-center justify-center', className)}>
        <div className="text-center">
          <div className="text-destructive text-lg font-semibold">JCEF环境初始化失败</div>
          <div className="text-muted-foreground mt-2">{error.message}</div>
          <div className="text-muted-foreground mt-4 text-sm">
            请确保在IntelliJ IDEA插件中运行此应用
          </div>
        </div>
      </div>
    );
  }

  if (!isReady) {
    return (
      <div className={cn('flex h-full items-center justify-center', className)}>
        <div className="text-center">
          <div className="animate-pulse text-primary text-lg">正在初始化JCEF环境...</div>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className={cn('h-full w-full', className)}>
      {/* JCEF环境已就绪，渲染应用内容 */}
    </div>
  );
};
```

**验收标准**:
- ✅ 在JCEF环境中正常初始化
- ✅ 在非JCEF环境中显示友好提示
- ✅ 超时处理正常（5秒）

---

### T1-W2-02: Java全局类型定义

**任务描述**: 定义window.ccBackend和window.ccEvents的TypeScript类型

**实现代码**:

```typescript
// src/shared/types/bridge.ts
import type { ID, Timestamp } from './index';
import type { ChatMessage, ChatResponse, MultimodalMessage } from './chat';
import type { ChatSession, SessionType } from './session';
import type { ThemeConfig } from './theme';
import type { Skill, ExecutionContext, SkillResult } from './ecosystem';
import type { Agent, AgentTask } from './ecosystem';
import type { McpServer, TestResult } from './ecosystem';

/**
 * Java后端API接口
 */
export interface JavaBackendAPI {
  // ========== 消息相关 ==========
  sendMessage(message: string): Promise<ChatResponse>;
  sendMultimodalMessage(message: MultimodalMessage): Promise<ChatResponse>;
  streamMessage(message: string): void;
  cancelStreaming(sessionId: string): void;

  // ========== 配置相关 ==========
  getConfig(key: string): Promise<any>;
  setConfig(key: string, value: any): Promise<void>;
  updateConfig(config: Partial<ConfigState>): Promise<void>;

  // ========== 主题相关 ==========
  getThemes(): Promise<ThemeConfig[]>;
  updateTheme(theme: ThemeConfig): Promise<void>;
  saveCustomTheme(theme: ThemeConfig): Promise<void>;
  deleteCustomTheme(themeId: string): Promise<void>;

  // ========== 会话相关 ==========
  createSession(name: string, type: SessionType): Promise<ChatSession>;
  switchSession(sessionId: string): Promise<void>;
  deleteSession(sessionId: string): Promise<void>;
  searchSessions(query: string): Promise<ChatSession[]>;
  exportSession(sessionId: string, format: 'markdown' | 'pdf'): Promise<Blob>;
  importSession(data: ArrayBuffer): Promise<ChatSession>;

  // ========== Skills相关 ==========
  executeSkill(skillId: string, context: ExecutionContext): Promise<SkillResult>;
  getSkills(): Promise<Skill[]>;
  saveSkill(skill: Skill): Promise<void>;
  deleteSkill(skillId: string): Promise<void>;

  // ========== Agents相关 ==========
  startAgent(agentId: string, task: AgentTask): Promise<void>;
  stopAgent(agentId: string): Promise<void>;
  getAgents(): Promise<Agent[]>;
  saveAgent(agent: Agent): Promise<void>;
  deleteAgent(agentId: string): Promise<void>;

  // ========== MCP相关 ==========
  startMcpServer(serverId: string): Promise<void>;
  stopMcpServer(serverId: string): Promise<void>;
  testMcpServer(serverId: string): Promise<TestResult>;
  getMcpServers(): Promise<McpServer[]>;
  saveMcpServer(server: McpServer): Promise<void>;
  deleteMcpServer(serverId: string): Promise<void>;

  // ========== 交互式请求相关 ==========
  submitAnswer(questionId: string, answer: any): Promise<void>;
}

/**
 * Java事件接口
 */
export interface JavaEventsAPI {
  on(event: string, handler: (data: any) => void): () => void;
  off(event: string, handler: (data: any) => void): void;
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
 * 全局类型扩展
 */
declare global {
  interface Window {
    ccBackend: JavaBackendAPI;
    ccEvents: JavaEventsAPI;
  }
}

export {};
```

**验收标准**:
- ✅ TypeScript类型定义完整
- ✅ window.ccBackend和window.ccEvents有类型提示
- ✅ 所有API方法有完整类型

---

### T1-W2-03: JBCefJSQuery端点注册

**任务描述**: 实现Java通信封装层

**实现代码**:

```typescript
// src/lib/java-bridge.ts
import type { JavaBackendAPI, JavaEventsAPI } from '@/shared/types';

class JavaBridge implements JavaBackendAPI {
  private queryId = 0;
  private pendingRequests = new Map<number, {
    resolve: (value: any) => void;
    reject: (error: Error) => void;
  }>();

  /**
   * 通用调用方法
   */
  private async invoke<T>(action: string, params?: any): Promise<T> {
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

      // 调用Java（通过JBCefJSQuery注入的send方法）
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

  /**
   * 初始化：监听Java响应
   */
  async init(): Promise<void> {
    return new Promise((resolve, reject) => {
      // 等待Java注入完成
      const checkReady = () => {
        if (window.ccBackend && window.ccEvents) {
          // 监听响应事件
          window.ccEvents.on('response', (data: any) => {
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
          resolve();
        } else {
          requestAnimationFrame(checkReady);
        }
      };
      checkReady();
    });
  }

  // ========== 实现JavaBackendAPI接口 ==========

  async sendMessage(message: string): Promise<any> {
    return this.invoke('sendMessage', { message });
  }

  async sendMultimodalMessage(message: any): Promise<any> {
    return this.invoke('sendMultimodalMessage', { message });
  }

  streamMessage(message: string): void {
    window.ccBackend.send({ action: 'streamMessage', params: { message } });
  }

  cancelStreaming(sessionId: string): void {
    window.ccBackend.send({ action: 'cancelStreaming', params: { sessionId } });
  }

  async getConfig(key: string): Promise<any> {
    return this.invoke('getConfig', { key });
  }

  async setConfig(key: string, value: any): Promise<void> {
    return this.invoke('setConfig', { key, value });
  }

  async updateConfig(config: any): Promise<void> {
    return this.invoke('updateConfig', { config });
  }

  async getThemes(): Promise<any[]> {
    return this.invoke('getThemes');
  }

  async updateTheme(theme: any): Promise<void> {
    return this.invoke('updateTheme', { theme });
  }

  async saveCustomTheme(theme: any): Promise<void> {
    return this.invoke('saveCustomTheme', { theme });
  }

  async deleteCustomTheme(themeId: string): Promise<void> {
    return this.invoke('deleteCustomTheme', { themeId });
  }

  async createSession(name: string, type: any): Promise<any> {
    return this.invoke('createSession', { name, type });
  }

  async switchSession(sessionId: string): Promise<void> {
    return this.invoke('switchSession', { sessionId });
  }

  async deleteSession(sessionId: string): Promise<void> {
    return this.invoke('deleteSession', { sessionId });
  }

  async searchSessions(query: string): Promise<any[]> {
    return this.invoke('searchSessions', { query });
  }

  async exportSession(sessionId: string, format: any): Promise<Blob> {
    return this.invoke('exportSession', { sessionId, format });
  }

  async importSession(data: ArrayBuffer): Promise<any> {
    return this.invoke('importSession', { data });
  }

  async executeSkill(skillId: string, context: any): Promise<any> {
    return this.invoke('executeSkill', { skillId, context });
  }

  async getSkills(): Promise<any[]> {
    return this.invoke('getSkills');
  }

  async saveSkill(skill: any): Promise<void> {
    return this.invoke('saveSkill', { skill });
  }

  async deleteSkill(skillId: string): Promise<void> {
    return this.invoke('deleteSkill', { skillId });
  }

  async startAgent(agentId: string, task: any): Promise<void> {
    return this.invoke('startAgent', { agentId, task });
  }

  async stopAgent(agentId: string): Promise<void> {
    return this.invoke('stopAgent', { agentId });
  }

  async getAgents(): Promise<any[]> {
    return this.invoke('getAgents');
  }

  async saveAgent(agent: any): Promise<void> {
    return this.invoke('saveAgent', { agent });
  }

  async deleteAgent(agentId: string): Promise<void> {
    return this.invoke('deleteAgent', { agentId });
  }

  async startMcpServer(serverId: string): Promise<void> {
    return this.invoke('startMcpServer', { serverId });
  }

  async stopMcpServer(serverId: string): Promise<void> {
    return this.invoke('stopMcpServer', { serverId });
  }

  async testMcpServer(serverId: string): Promise<any> {
    return this.invoke('testMcpServer', { serverId });
  }

  async getMcpServers(): Promise<any[]> {
    return this.invoke('getMcpServers');
  }

  async saveMcpServer(server: any): Promise<void> {
    return this.invoke('saveMcpServer', { server });
  }

  async deleteMcpServer(serverId: string): Promise<void> {
    return this.invoke('deleteMcpServer', { serverId });
  }

  async submitAnswer(questionId: string, answer: any): Promise<void> {
    return this.invoke('submitAnswer', { questionId, answer });
  }
}

// 导出单例
export const javaBridge = new JavaBridge();

// 自动初始化
javaBridge.init().catch((error) => {
  console.error('Failed to initialize Java bridge:', error);
});
```

**验收标准**:
- ✅ 可成功调用Java方法
- ✅ Promise异步调用正常
- ✅ 超时处理正常（30秒）
- ✅ 错误处理正常

---

### T1-W2-04: 事件总线实现

**实现代码**:

```typescript
// src/shared/utils/event-bus.ts
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

export const eventBus = new EventBus();

// 定义事件名称常量
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

  // UI事件
  SIDEBAR_TOGGLE: 'ui:sidebar:toggle',
  PREVIEW_TOGGLE: 'ui:preview:toggle'
} as const;
```

**验收标准**:
- ✅ 事件订阅/发布正常
- ✅ 取消订阅正常
- ✅ 错误处理正常
- ✅ 一次性订阅正常

---

## 🎯 Week 3: 状态管理与路由系统

### T1-W3-01: Zustand Store架构设计

**设计文档**:

```
stores/
├── appStore.ts          # 应用全局状态
├── sessionStore.ts      # 会话状态
├── themeStore.ts        # 主题状态
├── configStore.ts       # 配置状态
└── index.ts             # 统一导出
```

**Store设计原则**:
1. 单一数据源：每个Store只管理一个领域的状态
2. 不可变更新：使用immer或展开运算符
3. 异步操作：使用async/await
4. 订阅机制：支持部分状态订阅

---

### T1-W3-02: appStore实现

```typescript
// src/shared/stores/appStore.ts
import { create } from 'zustand';
import type { Session, UIState, TaskProgress } from '@/shared/types';

interface AppState {
  // 会话相关
  sessions: Session[];
  currentSessionId: string;

  // UI状态
  sidebarOpen: boolean;
  previewPanelOpen: boolean;
  activeModal: string | null;

  // 任务进度
  activeTaskProgress: TaskProgress | null;

  // 操作
  switchSession: (sessionId: string) => void;
  createSession: (name: string, type: any) => Promise<Session>;
  deleteSession: (sessionId: string) => void;
  updateSession: (sessionId: string, updates: Partial<Session>) => void;

  // UI操作
  toggleSidebar: () => void;
  togglePreviewPanel: () => void;
  openModal: (modalId: string, data?: any) => void;
  closeModal: () => void;

  // 任务进度操作
  setTaskProgress: (progress: TaskProgress | null) => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  // 初始状态
  sessions: [],
  currentSessionId: '',
  sidebarOpen: true,
  previewPanelOpen: false,
  activeModal: null,
  activeTaskProgress: null,

  // 会话操作
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

  // UI操作
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  togglePreviewPanel: () => set((state) => ({ previewPanelOpen: !state.previewPanelOpen })),

  openModal: (modalId, data) => set({ activeModal: modalId }),
  closeModal: () => set({ activeModal: null }),

  // 任务进度操作
  setTaskProgress: (progress) => set({ activeTaskProgress: progress })
}));
```

---

### T1-W3-05: React Router配置

```typescript
// src/main/router.tsx
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { ChatWindow } from '@/features/chat/components/ChatWindow';
import { SettingsPage } from '@/features/settings/components/SettingsPage';
import { ErrorPage } from './components/ErrorPage';

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <ErrorPage />,
    children: [
      {
        index: true,
        element: <ChatWindow />
      },
      {
        path: 'settings',
        element: <SettingsPage />
      },
      {
        path: 'settings/:tab',
        element: <SettingsPage />
      }
    ]
  }
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}
```

---

## ✅ 阶段验收标准

### 功能验收
- [ ] React项目可正常启动和构建
- [ ] TypeScript编译无错误
- [ ] TailwindCSS样式正常工作
- [ ] JCEF环境检测正常
- [ ] Java-JS双向通信正常
- [ ] 事件总线功能正常
- [ ] Zustand状态管理正常
- [ ] React Router路由正常

### 性能验收
- [ ] 首次渲染 < 500ms
- [ ] HMR正常（非JCEF环境）
- [ ] 无内存泄漏

### 代码质量验收
- [ ] ESLint检查通过
- [ ] TypeScript strict mode通过
- [ ] 组件有完整的Props类型定义
- [ ] 代码符合规范

---

## 📚 相关文档

- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 2: 核心UI组件](./02-phase2-core-ui.md)
