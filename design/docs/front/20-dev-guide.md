# ClaudeCodeJet 前端开发指引

**文档版本**: 2.0
**创建日期**: 2026-04-08
**更新日期**: 2026-04-08
**面向读者**: 前端开发人员（新加入或首次参与本项目）
**目的**: 帮助开发人员快速理解前端架构、掌握开发规范、有序推进实现

---

## 1. 推荐文档阅读顺序

```
第1步: 00-overview.md          了解项目全貌、技术选型、阶段划分
  ↓
第2步: 11-types.md             熟悉完整类型系统（所有代码的基石）
  ↓
第3步: 10-architecture.md      理解分层架构、Store设计、通信层
  ↓
第4步: 12-components.md        掌握组件设计规范和UI组件接口
  ↓
第5步: 按Phase顺序阅读开发文档
  01-phase1-foundation.md → 02-phase2-core-ui.md → 03-phase3-interaction.md
  → 04-phase4-session.md → 05-phase5-ecosystem.md → 06-phase6-optimization.md
```

**阅读时间预估**: 约4-6小时可通读全部文档

---

## 2. 各文档核心内容概要

### 00-overview.md — 项目总览
- 前端技术栈确认（React 18 + TypeScript 5 + Zustand 4 + TailwindCSS 3）
- 6个开发阶段的划分和依赖关系
- 编码规范约定（命名、组件规范、扩展埋点）
- 关键技术决策记录

### 11-types.md — 类型定义规范（最权威）
- 完整的 TypeScript 类型系统：ChatMessage、ChatSession、ThemeConfig、Skill、Agent、McpServer
- ContentPart 联合类型（TextContentPart | ImageContentPart | FileContentPart）
- JavaBackendAPI 接口定义（前端调用后端的全部方法签名）
- Window 全局类型扩展（ccBackend / ccEvents / ccStreaming）
- **重要**: 本文档定义的类型是代码实现的标准，任何 Phase 文档中的类型引用必须与此一致

### 10-architecture.md — 技术架构设计
- 5层架构：Presentation → State Management → Business Logic → Communication → Utility
- Store 架构设计（8个独立Store的职责划分）
- JCEF 双向通信机制（JavaBridge + EventBus）
- CSS 变量主题方案（切换延迟 < 100ms）
- 性能优化策略总览

### 12-components.md — React 组件设计规范
- 基础 UI 组件（Button / Input / Dialog / Tabs / Dropdown）的实现接口
- 业务组件（MessageItem / InteractiveQuestionPanel）的 Props 定义
- 组件开发规范（memo / useCallback / useMemo 的使用时机）
- Icon 系统设计说明

### 01-06 Phase 文档 — 各阶段详细开发计划
每个 Phase 文档包含：标准 header（优先级/工期/前置依赖/目标）→ 任务清单 → 完整实现代码 → 任务依赖树 → 验收标准 → 文件清单 → 相关文档引用

---

## 3. 开发前必读清单

开始编码前，必须掌握以下核心概念：

### 3.1 类型系统（11-types.md）
- [ ] `ChatMessage` 的完整字段（id / role / content / timestamp / attachments / references / metadata / status）
- [ ] `ContentPart` 是联合类型，必须用分支处理，不能直接访问 `.name` / `.type`
- [ ] `ChatSession`（不是 `Session`）是会话类型的标准名称
- [ ] `ThemePresets` 是 enum（如 `ThemePresets.JETBRAINS_DARK`），不是普通对象
- [ ] `AgentMode` 只有3个合法值：`CAUTIOUS` / `BALANCED` / `AGGRESSIVE`
- [ ] **禁止重新定义** TypeScript 内置类型（Partial / Required / Readonly / Parameters / ReturnType）

### 3.2 Store 架构（10-architecture.md）
- [ ] 8个独立 Store 的职责：appStore / sessionStore / themeStore / streamingStore / skillsStore / agentsStore / mcpStore / questionStore
- [ ] sessionStore 支持多会话隔离（含 persist）
- [ ] streamingStore 独立于 sessionStore（不在 sessionStore 中管理流式状态）
- [ ] `createSession` 是异步方法（返回 `Promise<ChatSession>`），不是同步的

### 3.3 通信层（10-architecture.md + 01-phase1-foundation.md）
- [ ] JavaBridge 封装了 `window.ccBackend.send()` 底层方法，业务代码通过 JavaBridge 调用
- [ ] EventBus 是前端内部事件通道，使用命名空间常量（`Events.STREAMING_CHUNK` 等）
- [ ] useStreaming Hook 通过 EventBus 监听流式事件，不直接调用 `window.ccEvents`
- [ ] 所有事件监听器必须在 `useEffect` cleanup 中取消订阅

---

## 4. 当前进度评估（2026-04-08 基线）

### 4.1 总体状态

| 维度 | 完成度 | 说明 |
|------|--------|------|
| 组件代码 | **~75%** | 50+ 组件已编写，覆盖全部6个Phase |
| Store状态管理 | **~90%** | 8个Store全部实现，含persist和异步操作 |
| Hook工具 | **~90%** | 9个自定义Hook覆盖流式/拖拽/快捷键/虚拟滚动 |
| 类型定义 | **~95%** | 9个类型文件，与11-types.md对齐 |
| **路由集成** | **~5%** | ❌ 仅2个占位div路由，无功能组件接入 |
| **页面组合** | **~0%** | ❌ 无ChatView/SettingsView等页面级组件 |
| 测试覆盖 | **~5%** | 仅4个测试文件 |
| 后端集成 | **~20%** | BridgeManager框架存在但Action全是TODO |

**核心判断**：组件"骨架"已搭好，但"神经系统"未接通。优先级最高的是集成工作。

### 4.2 各Phase组件完成状态

#### Phase 1: 基础架构 — 85%
| 模块 | 状态 | 备注 |
|------|------|------|
| Vite + React + TS + TailwindCSS | ✅ | 配置完整 |
| JavaBridge + EventBus | ✅ | 存在BUG：开发端口不匹配 |
| 8个 Zustand Store | ✅ | 全部实现 |
| React Router | ⚠️ | 占位符，需重建 |
| JcefBrowser 环境检测 | ✅ | polling机制正常 |

#### Phase 2: 核心UI — 90%（组件层）
| 模块 | 状态 | 备注 |
|------|------|------|
| 9套主题预设 + CSS变量 | ✅ | 7个CSS文件 |
| ThemeSwitcher + ThemeEditor | ✅ | |
| MessageItem + MarkdownRenderer + CodeBlock | ✅ | |
| MessageList (虚拟滚动) | ✅ | |
| ChatInput + AutoResizeTextarea | ✅ | |
| AttachmentDropZone + ImagePreview | ✅ | |

#### Phase 3: 交互增强 — 85%（组件层）
| 模块 | 状态 | 备注 |
|------|------|------|
| SSEParser + useStreaming | ✅ | EventBus模式 |
| StreamingMessage + StopButton + TypingCursor | ✅ | |
| InteractiveQuestionPanel (3种类型) | ✅ | |
| questionStore | ✅ | |
| useFileDrop + useImagePaste + useHotkeys | ✅ | |
| QuickActionsPanel | ✅ | |

#### Phase 4: 会话管理 — 85%（组件层）
| 模块 | 状态 | 备注 |
|------|------|------|
| SessionTabs + TabItem (含拖拽) | ✅ | @dnd-kit |
| TabSwitchAnimation | ✅ | |
| sessionStore 增强版 | ✅ | persist + 多会话 |
| SessionSearch + SearchFilters | ✅ | |
| SessionHistory | ✅ | |
| exportToMarkdown/PDF + importSession | ✅ | |

#### Phase 5: 生态集成 — 80%（组件层）
| 模块 | 状态 | 备注 |
|------|------|------|
| SkillsManager/Card/Editor/List + skillsStore | ✅ | |
| AgentsManager/Card/Editor/List + agentsStore | ✅ | |
| McpServerManager/Card/Config/List | ✅ | |
| ConnectionStatus + ScopeManager + mcpStore | ✅ | |
| lazy-components.ts 预配置 | ✅ | 8个组件 |

#### Phase 6: 优化测试 — 40%
| 模块 | 状态 | 备注 |
|------|------|------|
| LRU缓存 / Markdown缓存 | ✅ | |
| useOptimizedVirtualList | ✅ | |
| performance-monitor / memory-leak-detector | ✅ | |
| Vite手动chunk分包 | ✅ | 9个vendor chunk |
| 单元测试 | ❌ | 仅4个文件 |
| E2E/兼容性测试 | ❌ | 不存在 |

---

## 5. 后续开发计划（集成优先）

### 5.1 开发策略

```
原计划: Phase 1 → 2 → 3 → 4 → 5 → 6（线性推进）
当前策略: 集成优先，从"有组件"到"能运行"
```

原则：
1. **先集成，后优化** — 让现有组件跑起来比写新组件更重要
2. **先主流程，后分支流程** — 聊天主流程优先于生态管理
3. **前后端联调并行** — 不等后端完全就绪，先用Mock验证UI

### 5.2 Sprint划分（6个Sprint，约7.5周）

---

### Sprint 1: 集成基础（1周）— P0 阻塞性

**目标**: 建立完整的页面路由和布局框架，组件可通过路由访问

| # | 任务 | 预估 | 优先级 | 依赖 |
|---|------|------|--------|------|
| S1-1 | 创建 ChatView 页面组件 | 1d | P0 | 无 |
| S1-2 | 创建 SettingsView 页面组件 | 0.5d | P0 | 无 |
| S1-3 | 重构 AppLayout 接入 SessionTabs | 1d | P0 | S1-1 |
| S1-4 | 实现路由系统（替换占位路由） | 1d | P0 | S1-1, S1-2, S1-3 |
| S1-5 | 修复 stores barrel 导出 | 0.5h | P0 | 无 |
| S1-6 | 创建功能页面壳组件（SessionView, SkillsView, AgentsView, McpView） | 1d | P1 | 无 |

**验收标准**: 所有页面可通过路由访问，AppLayout展示侧边栏导航+内容区

---

### Sprint 2: 聊天主流程（1.5周）— P1 核心集成

**目标**: 完整聊天UI可用，含消息收发、流式输出、交互式请求

| # | 任务 | 预估 | 优先级 | 依赖 |
|---|------|------|--------|------|
| S2-1 | ChatView 组合: MessageList + ChatInput + SendButton | 1.5d | P0 | S1-1 |
| S2-2 | 接入 streamingStore + StreamingMessage + StopButton | 1d | P0 | S2-1 |
| S2-3 | 接入 InteractiveQuestionPanel（交互式请求） | 0.5d | P1 | S2-1 |
| S2-4 | 接入 QuickActionsPanel（快捷操作） | 0.5d | P1 | S2-1 |
| S2-5 | 接入 AttachmentDropZone + ImagePreview（多模态输入） | 1d | P1 | S2-1 |
| S2-6 | 联调后端 sendMessage → 流式回复端到端 | 1.5d | P0 | S2-2 |

**验收标准**: 可在IDE中发送消息，看到AI流式回复，交互式请求可应答

---

### Sprint 3: 会话管理集成（1周）— P1

**目标**: 多会话管理可用，含Tab切换、搜索、导入/导出

| # | 任务 | 预估 | 优先级 | 依赖 |
|---|------|------|--------|------|
| S3-1 | SessionTabs 接入 appStore + sessionStore | 1d | P0 | S1-3 |
| S3-2 | 实现会话新建/切换/关闭/拖拽排序 | 1d | P0 | S3-1 |
| S3-3 | SessionSearch 接入后端搜索接口 | 0.5d | P1 | S3-1 |
| S3-4 | SessionHistory 页面组装 | 0.5d | P1 | S3-1 |
| S3-5 | 导入/导出功能联调 | 0.5d | P2 | S3-1 |

**验收标准**: 支持多会话切换，搜索可用，可导出为Markdown/PDF

---

### Sprint 4: 主题与设置（0.5周）— P2

**目标**: 主题切换、偏好设置完整可用

| # | 任务 | 预估 | 优先级 | 依赖 |
|---|------|------|--------|------|
| S4-1 | SettingsView 组合: ThemeSwitcher + ThemeEditor | 0.5d | P1 | S1-2 |
| S4-2 | 主题实时预览 + IDE主题同步 | 0.5d | P2 | S4-1 |
| S4-3 | 偏好设置面板（模型选择、快捷键配置） | 0.5d | P2 | S4-1 |

**验收标准**: 可切换9套主题，自定义主题可保存

---

### Sprint 5: 生态集成（1.5周）— P2

**目标**: Skills / Agents / MCP 管理器功能可用

| # | 任务 | 预估 | 优先级 | 依赖 |
|---|------|------|--------|------|
| S5-1 | SkillsView 页面: SkillsManager + 对话编辑/新建 | 1d | P1 | S1-6 |
| S5-2 | AgentsView 页面: AgentsManager + Agent配置 | 1d | P1 | S1-6 |
| S5-3 | McpView 页面: McpServerManager + 连接测试 | 1d | P1 | S1-6 |
| S5-4 | 三者联调后端CRUD接口 | 1d | P2 | S5-1, S5-2, S5-3 |

**验收标准**: 可视化管理Skills/Agents/MCP服务器

---

### Sprint 6: 优化与测试（2周）— P3

**目标**: 性能达标，测试覆盖 > 60%

| # | 任务 | 预估 | 优先级 | 依赖 |
|---|------|------|--------|------|
| S6-1 | 虚拟滚动调优（10000条消息） | 1d | P2 | S2-1 |
| S6-2 | Markdown LRU缓存接入 | 0.5d | P2 | S2-1 |
| S6-3 | 懒加载验证（首屏 < 500ms） | 0.5d | P2 | S1-4 |
| S6-4 | 补充核心组件单元测试 | 3d | P1 | Sprint 2-5 |
| S6-5 | 端到端冒烟测试 | 2d | P2 | S6-4 |
| S6-6 | 性能基准测试 | 1d | P2 | S6-1 |

**验收标准**: 所有性能指标通过，核心流程有测试保障

---

### 5.3 已知问题清单（需后端配合）

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| B1 | 开发端口不匹配（Factory查5173，Vite配3000） | 开发模式无法加载 | 改Factory为3000 |
| B2 | JS注入方法未调用 | window.ccBackend不可用 | 在createToolWindowContent中调用inject |
| B3 | 后端7个Action为TODO stub | 无实际功能 | 逐个实现 |
| B4 | CCGuiConfig类缺失 | plugin.xml报错 | 补全类文件 |
| B5 | 无Gradle前端构建任务 | 生产环境无法加载 | 实现buildWebview任务 |

---

## 6. 常见陷阱与避坑指南

### 陷阱 1: 重新定义 TypeScript 内置类型
```typescript
// ❌ 错误：与 TypeScript 内置 Partial 冲突
export type Partial<T> = { [P in keyof T]?: T[P] };

// ✅ 正确：直接使用内置类型（全局可用，无需导入）
```

### 陷阱 2: ContentPart 直接访问属性
```typescript
// ❌ 错误：ContentPart 没有 name/type 属性（联合类型）
const name = attachment.name;

// ✅ 正确：先判断分支类型
if (attachment.type === 'file') {
  console.log(attachment.name);  // FileContentPart 有 name
} else if (attachment.type === 'image') {
  console.log(attachment.mimeType);  // ImageContentPart 有 mimeType
}
```

### 陷阱 3: useStreaming 闭包陷阱
```typescript
// ❌ 错误：content 在 useCallback 依赖中导致无限更新
const startStreaming = useCallback((msgId: string) => {
  setContent(prev => prev + chunk);
}, [content]); // content 变化 → useCallback 重建 → 新的监听器

// ✅ 正确：使用 ref 累积内容
const contentRef = useRef('');
const startStreaming = useCallback((msgId: string) => {
  contentRef.current += chunk;
  setContent(contentRef.current);
}, []); // 无依赖，稳定引用
```

### 陷阱 4: AgentMode 的合法值
```typescript
// ❌ 错误：'single' 不是 AgentMode 的合法值
mode: 'single' as AgentMode

// ✅ 正确：使用枚举值
mode: AgentMode.BALANCED  // 'balanced'
```

### 陷阱 5: Session 类型名
```typescript
// ❌ 错误：类型系统中没有 Session 类型
import type { Session } from '@/shared/types';

// ✅ 正确：使用 ChatSession
import type { ChatSession } from '@/shared/types';
```

### 陷阱 6: ThemePresets 使用方式
```typescript
// ❌ 错误：ThemePresets 不是普通对象
const config = ThemePresets['jetbrains-dark'];

// ✅ 正确：ThemePresets 是枚举，配置存储在 ThemePresetConfigs 中
const presetId = ThemePresets.JETBRAINS_DARK; // 'jetbrains-dark'
const config = ThemePresetConfigs[presetId];  // ThemeConfig 对象
```

### 陷阱 7: SingleChoiceOptions 缺少 questionId
```typescript
// ❌ 错误：内联子组件无法访问外部的 questionId
<SingleChoiceOptions options={options} selected={selected} onChange={onChange} />

// ✅ 正确：显式传入 questionId
<SingleChoiceOptions questionId={questionId} options={options} selected={selected} onChange={onChange} />
```

### 陷阱 8: createSession 的同步/异步
```typescript
// ❌ 错误：createSession 是异步方法
const session = appStore.getState().createSession(name, type);
console.log(session.id); // undefined

// ✅ 正确：使用 await
const session = await appStore.getState().createSession(name, type);
console.log(session.id); // 'session-xxx'
```

### 陷阱 9: 事件监听器未清理
```typescript
// ❌ 错误：组件卸载后监听器仍在执行
useEffect(() => {
  eventBus.on(Events.STREAMING_CHUNK, handler);
  // 没有 cleanup
}, []);

// ✅ 正确：返回 cleanup 函数
useEffect(() => {
  const unsubscribe = eventBus.on(Events.STREAMING_CHUNK, handler);
  return unsubscribe; // 组件卸载时自动取消
}, []);
```

---

## 7. 文档权威层级

当不同文档之间存在矛盾时，按以下优先级判断：

```
11-types.md (最高)   ← 类型定义的最终裁决者
    ↓
10-architecture.md   ← 架构设计的最终裁决者
    ↓
12-components.md     ← 组件接口的参考规范
    ↓
01-06 Phase 文档     ← 具体实现指导（可能与上述文档有细微差异，以上述为准）
```

**冲突解决原则**: 如果 Phase 文档中的代码与 11-types.md 的类型定义不一致，以 11-types.md 为准并修正 Phase 文档中的代码。

---

## 8. 开发环境配置

```bash
# 1. Node.js 版本要求
node >= 18.0.0
npm >= 9.0.0

# 2. 安装依赖
cd webview
npm install

# 3. TypeScript 严格模式检查
npx tsc --noEmit

# 4. ESLint 检查
npm run lint

# 5. 开发服务器（浏览器调试）
npm run dev  # http://localhost:3000

# 6. 单元测试
npm run test

# 7. JCEF 环境模拟
# 在浏览器中开发时，window.ccBackend 不可用
# test/setup.ts 已配置全局 mock
```

---

## 9. 总工期汇总

### 原始规划（从零开始）

| 阶段 | 工期 | 累计 |
|------|------|------|
| Phase 1: 基础架构 | 3周 | 3周 |
| Phase 2: 核心 UI | 4.5周 | 7.5周 |
| Phase 3: 交互增强 | 4周 | 11.5周 |
| Phase 4: 会话管理 | 3周 | 14.5周 |
| Phase 5: 生态集成 | 3.5周 | 18周 |
| Phase 6: 优化测试 | 3.5周 | 21.5周 |

**总计**: 约21.5周（5.4个月）

### 当前进度后的剩余工作

| Sprint | 工期 | 累计 | 状态 |
|--------|------|------|------|
| Sprint 1: 集成基础 | 1周 | 1周 | 待开始 |
| Sprint 2: 聊天主流程 | 1.5周 | 2.5周 | 待开始 |
| Sprint 3: 会话管理集成 | 1周 | 3.5周 | 待开始 |
| Sprint 4: 主题与设置 | 0.5周 | 4周 | 待开始 |
| Sprint 5: 生态集成 | 1.5周 | 5.5周 | 待开始 |
| Sprint 6: 优化与测试 | 2周 | 7.5周 | 待开始 |

**剩余总计**: 约7.5周（1.9个月）

---

## 10. 相关文档

- [项目总览](./00-overview.md)
- [类型定义规范](./11-types.md) — 最权威的类型来源
- [技术架构设计](./10-architecture.md) — 架构设计权威来源
- [组件设计规范](./12-components.md) — UI 组件接口参考
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 2: 核心 UI](./02-phase2-core-ui.md)
- [Phase 3: 交互增强](./03-phase3-interaction.md)
- [Phase 4: 会话管理](./04-phase4-session.md)
- [Phase 5: 生态集成](./05-phase5-ecosystem.md)
- [Phase 6: 优化测试](./06-phase6-optimization.md)
