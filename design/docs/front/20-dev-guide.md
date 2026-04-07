# ClaudeCodeJet 前端开发指引

**文档版本**: 1.0
**创建日期**: 2026-04-08
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
- Store 架构设计（7个独立Store的职责划分）
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
- [ ] 7个独立 Store 的职责：appStore / sessionStore / themeStore / streamingStore / skillsStore / agentsStore / mcpStore
- [ ] sessionStore 在 Phase 4 会从基础版升级为增强版（支持多会话隔离）
- [ ] streamingStore 独立于 sessionStore（不在 sessionStore 中管理流式状态）
- [ ] `createSession` 是异步方法（返回 `Promise<ChatSession>`），不是同步的

### 3.3 通信层（10-architecture.md + 01-phase1-foundation.md）
- [ ] JavaBridge 封装了 `window.ccBackend.send()` 底层方法，业务代码通过 JavaBridge 调用
- [ ] EventBus 是前端内部事件通道，使用命名空间常量（`Events.STREAMING_CHUNK` 等）
- [ ] useStreaming Hook 通过 EventBus 监听流式事件，不直接调用 `window.ccEvents`
- [ ] 所有事件监听器必须在 `useEffect` cleanup 中取消订阅

---

## 4. 建议开发路径

### Phase 1: 基础架构（3周）

**核心产出**: React 项目骨架 + JCEF 双向通信 + Zustand Store 框架

```
Week 1: 项目初始化
  └─ Vite + React + TypeScript + TailwindCSS + ESLint 配置完成
Week 2: JCEF 通信桥接
  └─ JavaBridge 类 + EventBus + 类型定义（对齐 11-types.md）
Week 3: 状态管理
  └─ appStore + sessionStore(基础版) + themeStore + React Router
```

**验收标准**: 可通过 React UI 发送消息并收到后端回复

### Phase 2: 核心 UI（4.5周）

**核心产出**: 主题系统 + 消息列表 + Markdown 渲染 + 输入组件

```
Week 4: 主题系统
  └─ 6套预设CSS变量 + ThemeSwitcher + ThemeEditor
Week 5: 消息组件
  └─ MessageItem + MarkdownRenderer + CodeBlock + 虚拟滚动
Week 6: 输入组件
  └─ ChatInput + AutoResize + AttachmentDropZone + 快捷键
```

**验收标准**: 完整聊天 UI 可显示消息、发送消息、切换主题

### Phase 3: 交互增强（4周）

**核心产出**: 流式输出 + 交互式请求 + 多模态输入

```
Week 7: 流式输出
  └─ SSEParser + useStreaming + StreamingMessage + streamingStore
Week 8: 交互式请求
  └─ InteractiveQuestionPanel + 4种问题类型 + questionStore
Week 9: 多模态输入
  └─ useFileDrop + useImagePaste + useHotkeys + QuickActionsPanel
```

**验收标准**: AI 回复打字机效果正常，可上传图片/附件，交互式请求正常应答

### Phase 4: 会话管理（3周）

**核心产出**: 多会话 Tab + 会话搜索 + 导入/导出

```
Week 10: 多会话管理
  └─ SessionTabs + @dnd-kit 拖拽 + sessionStore 增强版
Week 11: 搜索与导出
  └─ SessionSearch + exportToMarkdown + importSession
```

**验收标准**: 支持 10+ 并发会话，切换延迟 < 100ms

### Phase 5: 生态集成（3.5周）

**核心产出**: Skills / Agents / MCP 管理器

```
Week 12: Skills/Agents
  └─ SkillsManager + AgentsManager + 对应 Store
Week 13: MCP 服务器
  └─ McpServerManager + ScopeManager + 连接测试
```

**验收标准**: 可视化管理所有 Claude Code 生态组件

### Phase 6: 优化测试（3.5周）

**核心产出**: 性能达标 + 测试覆盖 > 80%

```
Week 14: 性能优化
  └─ 虚拟滚动调优 + Markdown LRU 缓存 + 组件懒加载
Week 15: 集成测试
  └─ 单元测试 + E2E 测试 + 兼容性验证
```

**验收标准**: 所有性能指标通过，可发布

---

## 5. 常见陷阱与避坑指南

### 陷阱 1: 重新定义 TypeScript 内置类型
```typescript
// ❌ 错误：与 TypeScript 内置 Partial 冲突
export type Partial<T> = { [P in keyof T]?: T[P] };

// ✅ 正确：直接使用内置类型
import type { Partial } from 'typescript'; // 不需要，全局可用
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
  setContent(prev => prev + chunk); // 每次更新都触发 startStreaming 重建
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

## 6. 文档权威层级

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

## 7. 开发环境配置

```bash
# 1. Node.js 版本要求
node >= 18.0.0
npm >= 9.0.0

# 2. 初始化项目（Phase 1 Week 1）
npm create vite@latest ccgui-frontend -- --template react-ts
cd ccgui-frontend
npm install

# 3. TypeScript 严格模式检查
npx tsc --noEmit

# 4. ESLint 检查
npm run lint

# 5. 开发服务器（浏览器调试）
npm run dev  # http://localhost:3000

# 6. JCEF 环境模拟
# 在浏览器中开发时，window.ccBackend 不可用
# 使用 mock: src/shared/utils/java-bridge-mock.ts
```

---

## 8. 总工期汇总

| 阶段 | 工期 | 累计 |
|------|------|------|
| Phase 1: 基础架构 | 3周 | 3周 |
| Phase 2: 核心 UI | 4.5周 | 7.5周 |
| Phase 3: 交互增强 | 4周 | 11.5周 |
| Phase 4: 会话管理 | 3周 | 14.5周 |
| Phase 5: 生态集成 | 3.5周 | 18周 |
| Phase 6: 优化测试 | 3.5周 | 21.5周 |

**总计**: 约21.5周（5.4个月）

---

## 9. 相关文档

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
