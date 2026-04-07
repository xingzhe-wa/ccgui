# ClaudeCodeJet v3.0 前端开发总览

**文档版本**: 1.0
**创建日期**: 2026-04-08
**前端技术栈**: React 18 + TypeScript 5 + Zustand 4 + TailwindCSS 3
**运行环境**: JCEF (JetBrains Chromium Embedded Framework)
**目标IDE平台**: IntelliJ IDEA 2025.2+ (sinceBuild=252)
**当前项目版本**: 0.0.1

---

## 1. 项目现状分析

### 1.1 前端项目状态

| 项目 | 状态 | 说明 |
|------|------|------|
| React项目骨架 | 🟡 待创建 | 需使用Vite初始化React + TypeScript项目 |
| TypeScript类型定义 | 🟡 待创建 | 完整类型系统已设计（见 11-types.md） |
| UI组件库 | 🟡 待创建 | 组件设计规范已完成（见 12-components.md） |
| JCEF集成 | 🟡 待创建 | 通信协议已设计（见 10-architecture.md 通信层） |
| 构建配置 | 🟡 待创建 | Vite + TailwindCSS + ESLint配置已设计 |

### 1.2 后端依赖的前端接口

后端（Kotlin侧）通过 `JBCefJSQuery` 和 `CefJavaScriptExecutor` 与前端交互，以下接口需要在**前端Phase 1完成后**即可供后端调用：

```
window.ccBackend            ← Java注入的全局API对象（后端Phase 2 BridgeManager注入）
window.ccEvents             ← Java注入的事件总线（后端Phase 2 BridgeManager注入）
window.ccBackend.send()     ← JS调用Java的统一入口
window.ccEvents.emit()      ← Java向前端推送事件
```

### 1.3 前端技术选型

| 技术 | 版本 | 用途 | 选型理由 |
|------|------|------|----------|
| React | 18.3+ | UI框架 | 成熟生态，JCEF兼容性好 |
| TypeScript | 5.3+ | 类型系统 | 类型安全，IDE支持好 |
| Zustand | 4.5+ | 状态管理 | 轻量、无boilerplate |
| TailwindCSS | 3.4+ | 样式系统 | CSS变量主题方案 |
| Radix UI | latest | 无障碍组件库 | WAI-ARIA合规 |
| react-markdown | 9.0+ | Markdown渲染 | 支持GFM/数学公式 |
| highlight.js | 11.9+ | 代码高亮 | 190+语言支持 |
| @tanstack/react-virtual | 3.0+ | 虚拟滚动 | 支持1000+消息列表 |
| @dnd-kit | 6.1+ | 拖拽排序 | 轻量可访问 |

---

## 2. 前端目标架构

### 2.1 分层架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                     Presentation Layer                          │
│  React 18 App (JCEF内运行)                                      │
│  ├── features/chat/        聊天功能组件                          │
│  ├── features/session/     会话管理组件                          │
│  ├── features/theme/       主题系统组件                          │
│  ├── features/streaming/   流式输出组件                          │
│  ├── features/interaction/ 交互增强组件                          │
│  ├── features/skills/      Skills管理组件                        │
│  ├── features/agents/      Agents管理组件                        │
│  └── features/mcp/         MCP服务器组件                         │
├─────────────────────────────────────────────────────────────────┤
│                     State Management Layer                      │
│  Zustand Stores                                                 │
│  ├── appStore         全局应用状态                               │
│  ├── sessionStore     会话/消息状态                              │
│  ├── themeStore       主题配置状态                               │
│  ├── streamingStore   流式输出状态                               │
│  └── configStore      配置热更新状态                             │
├─────────────────────────────────────────────────────────────────┤
│                     Business Logic Layer                        │
│  Custom Hooks                                                   │
│  ├── useJavaBridge    Java通信封装                               │
│  ├── useStreaming     SSE流式处理                                │
│  ├── useVirtualList   虚拟滚动                                  │
│  ├── useTheme         主题切换                                  │
│  ├── useFileDrop      文件拖拽                                  │
│  └── useHotkeys       快捷键绑定                                │
├─────────────────────────────────────────────────────────────────┤
│                     Communication Layer                         │
│  Java ↔ JavaScript Bridge                                      │
│  ├── JavaBridge       通信封装（JBCefJSQuery）                   │
│  ├── EventBus         事件总线（Pub/Sub）                        │
│  └── SSEParser        SSE流式解析                               │
├─────────────────────────────────────────────────────────────────┤
│                     Utility Layer                               │
│  MarkdownParser / StorageManager / CacheManager                 │
└─────────────────────────────────────────────────────────────────┘
           ↕                                ↕
    Java后端(BridgeManager)         JCEF Browser Engine
```

### 2.2 前端目录结构规划

```
src/
├── main/                           # 入口
│   ├── index.tsx
│   ├── App.tsx
│   └── router.tsx
│
├── shared/                         # 共享模块
│   ├── components/ui/              # 基础UI组件 (Button/Input/Dialog/Tabs)
│   ├── components/layout/          # 布局组件 (ChatLayout/ResponsiveLayout)
│   ├── components/markdown/        # Markdown组件 (Renderer/CodeBlock/Latex)
│   ├── hooks/                      # 共享Hooks
│   ├── stores/                     # Zustand Stores
│   ├── types/                      # TypeScript类型定义
│   ├── utils/                      # 工具函数
│   └── constants/                  # 常量定义
│
├── features/                       # 功能模块
│   ├── chat/                       # Phase 2: 聊天功能
│   ├── theme/                      # Phase 2: 主题系统
│   ├── streaming/                  # Phase 3: 流式输出
│   ├── interaction/                # Phase 3: 交互增强
│   ├── session/                    # Phase 4: 会话管理
│   ├── skills/                     # Phase 5: Skills管理
│   ├── agents/                     # Phase 5: Agents管理
│   ├── mcp/                        # Phase 5: MCP服务器管理
│   └── tasks/                      # Phase 3: 任务进度
│
├── lib/                            # 核心库 (JavaBridge/EventBus)
└── styles/                         # 样式 (globals.css/themes/)
```

---

## 3. 开发阶段划分

### Phase 1: 项目基础与通信桥接 (Foundation)

**优先级**: P0
**预估工期**: 3周
**前置依赖**: 后端Phase 1（数据模型 + infrastructure）完成，或前后端并行开发时类型定义对齐
**阶段目标**: React项目可运行，JCEF通信链路打通，状态管理框架就绪
**完成标志**: 可通过React UI发送消息并收到后端回复
**详见**: [01-phase1-foundation.md](01-phase1-foundation.md)

### Phase 2: 核心UI组件 (Core UI)

**优先级**: P0
**预估工期**: 22.5人天 (4.5周)
**前置依赖**: Phase 1 全部完成
**阶段目标**: 主题系统可用，消息列表可渲染，输入组件可交互
**完成标志**: 完整的聊天UI可正常显示和交互
**详见**: [02-phase2-core-ui.md](02-phase2-core-ui.md)

### Phase 3: 交互增强系统 (Interaction)

**优先级**: P0
**预估工期**: 4周
**前置依赖**: Phase 1 + Phase 2
**阶段目标**: 流式输出可用，交互式请求可用，多模态输入可用
**完成标志**: AI回复打字机效果正常，可上传图片/附件
**详见**: [03-phase3-interaction.md](03-phase3-interaction.md)

### Phase 4: 会话管理系统 (Session Management)

**优先级**: P1
**预估工期**: 3周
**前置依赖**: Phase 1 + Phase 2 + Phase 3
**阶段目标**: 多会话Tab管理，会话搜索/导出，虚拟滚动优化
**完成标志**: 支持10+并发会话，切换延迟<100ms
**详见**: [04-phase4-session.md](04-phase4-session.md)

### Phase 5: 生态集成 (Ecosystem Integration)

**优先级**: P1
**预估工期**: 3.5周
**前置依赖**: Phase 4（会话管理可用，后端Core Services已提供Skills/Agents/MCP管理API）
**阶段目标**: Skills/Agents/MCP管理器UI完整可用
**完成标志**: 可视化管理所有Claude Code生态组件
**详见**: [05-phase5-ecosystem.md](05-phase5-ecosystem.md)

### Phase 6: 性能优化与测试 (Optimization & Testing)

**优先级**: P1-P2
**预估工期**: 3.5周
**前置依赖**: Phase 1-5 全部完成
**阶段目标**: 性能指标达标，测试覆盖率>80%，无内存泄漏
**完成标志**: 所有性能指标通过，可发布
**详见**: [06-phase6-optimization.md](06-phase6-optimization.md)

---

## 4. 阶段依赖关系

```
Phase 1 (Foundation)
    ↓
Phase 2 (Core UI)           ← 依赖Phase 1的JCEF通信和Store框架
    ↓
Phase 3 (Interaction)       ← 依赖Phase 2的UI组件库
    ↓
Phase 4 (Session)           ← 依赖Phase 2的消息组件 + Phase 3的流式输出
    ↓
Phase 5 (Ecosystem)         ← 依赖Phase 4的会话管理（顺序执行）
    ↓
Phase 6 (Optimization)      ← 依赖Phase 1-5全部完成
```

**与后端的协作关系**:

```
后端Phase 1 (数据模型)    ←→  前端Phase 1 (类型定义对齐)
后端Phase 2 (通信桥接)    ←→  前端Phase 1 (JCEF通信)
后端Phase 3 (核心服务)    ←→  前端Phase 3 (流式/交互)
后端Phase 4 (功能模块)    ←→  前端Phase 3-4 (交互/会话)
后端Phase 5 (生态集成)    ←→  前端Phase 5 (生态UI)
```

每个Phase完成后必须：
1. 所有TypeScript编译无错误
2. ESLint检查通过
3. 不破坏已有功能
4. 核心组件有基本单元测试

---

## 5. 编码规范约定

### 5.1 命名规范

| 类别 | 规范 | 示例 |
|------|------|------|
| 组件文件 | PascalCase.tsx | `MessageItem.tsx` |
| Hook文件 | camelCase.ts | `useStreaming.ts` |
| Store文件 | camelCase.ts | `sessionStore.ts` |
| 类型文件 | camelCase.ts | `chat.ts` |
| 工具函数 | camelCase.ts | `cn.ts` |
| CSS类名 | kebab-case / BEM | `message-item__avatar` |
| CSS变量 | kebab-case | `--color-primary` |
| 常量 | UPPER_SNAKE_CASE | `MAX_CACHE_SIZE` |
| 枚举 | PascalCase | `MessageRole.USER` |

### 5.2 组件规范

- 所有组件使用 `React.FC` + 泛型Props接口
- 所有组件使用 `memo()` 优化（除特殊情况外）
- 事件处理使用 `useCallback`
- 复杂计算使用 `useMemo`
- 副作用必须在 `useEffect` 返回清理函数
- 可选Props提供合理默认值
- 所有公开组件有 `displayName`

### 5.3 扩展埋点规范

- Store使用slice模式，新功能添加新slice
- 组件通过Context或Props注入依赖，不硬编码
- 事件总线使用命名空间（`module:action`）
- Java Bridge API通过接口定义，不直接调用window对象
- 主题使用CSS变量，方便动态切换
- 新功能模块放在 `features/` 目录下

---

## 6. 关键技术决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| UI框架 | React 18 | 生态丰富，JCEF兼容性验证通过 |
| 状态管理 | Zustand | 比Redux轻量，TypeScript友好 |
| 样式方案 | TailwindCSS + CSS变量 | 主题切换只需更新CSS变量 |
| 组件库 | Radix UI (无样式原语) | 无障碍合规，可完全自定义样式 |
| 构建工具 | Vite | 快速HMR，JCEF不需要HMR时关闭 |
| 虚拟滚动 | @tanstack/react-virtual | 支持动态高度，API简洁 |
| 拖拽 | @dnd-kit | 轻量，可访问，React 18兼容 |
| Markdown | react-markdown + remark | 插件体系完善，支持GFM/数学 |
| 代码高亮 | highlight.js | 190+语言，支持懒加载 |
| 包管理 | npm | 与后端Gradle构建解耦 |

---

## 7. 参考文档索引

### 架构与规范

| 文档 | 描述 |
|------|------|
| [10-architecture.md](10-architecture.md) | 前端技术架构设计（分层架构/通信层/状态管理/样式系统） |
| [11-types.md](11-types.md) | TypeScript类型定义规范（完整类型系统） |
| [12-components.md](12-components.md) | React组件设计规范（基础UI/复合/业务组件） |

### 后端文档（协作参考）

| 文档 | 描述 |
|------|------|
| [../backend/00-overview.md](../backend/00-overview.md) | 后端开发总览 |
| [../backend/01-phase1-foundation.md](../backend/01-phase1-foundation.md) | 后端Phase 1（数据模型/基础设施） |
| [../backend/02-phase2-adaptation.md](../backend/02-phase2-adaptation.md) | 后端Phase 2（通信桥接/JCEF集成） |

---

**文档维护**：
- 本文档为前端开发总览，作为所有前端阶段文档的导航入口
- 重大变更需经过团队评审，并更新此文档
- 所有技术决策必须有追溯记录
