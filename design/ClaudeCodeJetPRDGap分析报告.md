# v0.0.2 开发执行方案

**版本**: 0.0.2
**创建日期**: 2026-04-09
**基于**: PRD-v3.0.md + v0.0.1 Gap 分析报告
**目标**: 修复核心体验阻断问题，补全高频功能，达到"可日常使用"的质量标准
**预计工期**: 3 个 Sprint，约 3-5 个工作日

---

## 1. v0.0.2 MVP 范围定义

### 1.1 必须有 (Must Have) — P0

| 功能 | 验收标准 |
|------|---------|
| Markdown 代码高亮 | 代码块使用 `highlight.js` 渲染，支持语法高亮和复制按钮 |
| 会话切换数据联动 | 点击 SessionTab → MessageList 刷新显示对应会话消息 |
| 流式输出打断 | StopButton 点击 → 前端停收 + 后端 cancelStreaming |
| 新建会话 UI | 侧边栏 "+" 按钮 → 创建并切换到新会话 |
| Skill/Agent 列表展示 | Skills/Agents 页面调用 `getSkills()`/`getAgents()` 渲染列表 |

### 1.2 应该有 (Should Have) — P1

| 功能 | 验收标准 |
|------|---------|
| 历史会话搜索 | SessionHistoryView 添加搜索框，输入关键词过滤会话 |
| 多模态输入 | ChatInput 支持拖拽图片/文件，显示附件预览 |
| PromptOptimizer 入口 | ChatInput 工具栏"✨优化"按钮，调用 `PromptOptimizer` |
| 流式打字机光标 | StreamingMessage 显示闪烁光标，完成后消失 |
| 交互式请求 UI | AI 提问时显示 InteractiveQuestionPanel，用户可回答 |

### 1.3 延后到 v0.1.0

- 会话导入/导出（Markdown/PDF）
- 代码快捷操作（右键菜单 AnAction）
- 对话引用系统
- 任务进度可视化
- StatusBar Widget
- 虚拟滚动（长消息列表优化）
- 多供应商适配（MultiProviderAdapter）— 与 Claude CLI 定位冲突

---

## 2. Sprint 执行计划

### Sprint 6：核心体验修复（Day 1-2）

**目标**：核心聊天体验可用、可日常使用

#### Task 6.1：Markdown 代码高亮（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 6.1.1 | 在 `ChatView.tsx` 中引入 `marked.js` + `highlight.js` |
| 6.1.2 | 创建 `MarkdownRenderer` 组件，封装代码高亮逻辑 |
| 6.1.3 | `StreamingMessage` 使用 `MarkdownRenderer` 替代纯文本显示 |
| 6.1.4 | 代码块添加"复制"按钮，点击复制到剪贴板 |

**验收**：发送包含代码的消息，代码块有语法高亮 + 复制按钮

#### Task 6.2：会话切换数据联动（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 6.2.1 | `sessionStore.ts` 的 `switchSession` 正确更新 `currentSessionId` 和 `messages` |
| 6.2.2 | `SessionTabs` 绑定 `useAppStore` 的 `sessions` 列表 |
| 6.2.3 | `MessageList` 读取 `useAppStore.getState().sessions.find(id === currentSessionId)` |
| 6.2.4 | 标签栏点击 → `useAppStore.getState().switchSession(tabId)` |

**验收**：创建两个会话，分别发送不同消息，切换标签页后消息内容正确变化

#### Task 6.3：StopButton 连接 streamingStore（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 6.3.1 | `useStreamingStore` 添加 `cancelStreaming()` 方法，调用 `window.ccBackend?.cancelStreaming()` |
| 6.3.2 | `StopButton` 点击 → `useStreamingStore.getState().cancelStreaming()` |
| 6.3.3 | 取消后 `isStreaming = false`，StreamingMessage 显示"已中断"状态 |
| 6.3.4 | 验证后端 `cancelStreaming` 被调用（ IDEA console 日志） |

**验收**：点击停止按钮，流式输出立即中断，显示"已中断"

#### Task 6.4：新建会话 UI（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 6.4.1 | 侧边栏或标签栏添加 "+" 新建会话按钮 |
| 6.4.2 | 点击按钮 → 调用 `javaBridge.createSession(name, type)` |
| 6.4.3 | `appStore.createSession()` 更新 sessions 列表并切换到新会话 |
| 6.4.4 | 自动聚焦到新会话的输入框 |

**验收**：点击"+"按钮，新会话出现在标签栏，自动切换到新会话

---

### Sprint 7：高频功能补全（Day 3-4）

**目标**：补全 Skill/Agent/搜索/多模态等高频场景

#### Task 7.1：Skill/Agent 列表展示（1 天）

| 子任务 | 内容 |
|--------|------|
| 7.1.1 | `SkillsView` 调用 `javaBridge.getSkills()`，渲染技能列表 |
| 7.1.2 | 添加启用/禁用切换开关，调用 `javaBridge.saveSkill()` |
| 7.1.3 | `AgentsView` 调用 `javaBridge.getAgents()`，渲染 Agent 列表 |
| 7.1.4 | 添加启用/禁用切换开关，调用 `javaBridge.saveAgent()` |

**验收**：Skills/Agents 页面显示内置列表，可切换启用状态

#### Task 7.2：历史会话搜索（1 天）

| 子任务 | 内容 |
|--------|------|
| 7.2.1 | `SessionHistoryView` 添加搜索输入框 |
| 7.2.2 | 输入关键词 → 调用 `javaBridge.searchSessions(query)` |
| 7.2.3 | 渲染搜索结果列表（会话名 + 摘要 + 时间） |
| 7.2.4 | 点击搜索结果 → 切换到对应会话 |

**验收**：输入关键词，能找到包含该词的会话

#### Task 7.3：多模态输入（1 天）

| 子任务 | 内容 |
|--------|------|
| 7.3.1 | `ChatInput` 添加 `onDrop` / `onPaste` 事件处理 |
| 7.3.2 | 识别图片（PNG/JPG/GIF）和文件（TXT/MD/JSON） |
| 7.3.3 | 图片转为 Base64，文件读取内容，添加到附件列表 |
| 7.3.4 | 显示附件预览（缩略图或文件图标），可删除 |
| 7.3.5 | 发送时调用 `javaBridge.sendMultimodalMessage()` |

**验收**：拖拽图片到输入框，显示预览；粘贴文件，内容被读取；发送后 AI 能响应

#### Task 7.4：流式打字机光标（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 7.4.1 | `StreamingMessage` 流式输出时显示闪烁光标 `\|` |
| 7.4.2 | 流式结束时（`streaming:complete`）光标消失 |
| 7.4.3 | 异常中断时（`streaming:error`）光标变红并显示错误状态 |

**验收**：AI 回复时输入框有闪烁光标，回答完毕后光标消失

#### Task 7.5：PromptOptimizer 入口（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 7.5.1 | `ChatInput` 工具栏添加"✨优化"按钮 |
| 7.5.2 | 点击按钮 → 调用 `javaBridge.optimizePrompt?.(content)` 或后端 PromptOptimizer |
| 7.5.3 | 优化结果替换输入框内容，用户可编辑后发送 |

**验收**：输入提示词，点击"优化"，输入框内容变为优化后的版本

---

### Sprint 8：交互增强（Day 5-6）

**目标**：完成交互式请求 UI、导入导出等增值功能

#### Task 8.1：交互式请求 UI（1.5 天）

| 子任务 | 内容 |
|--------|------|
| 8.1.1 | `questionStore` 订阅 `streaming:question` 事件（新增，前端尚未定义） |
| 8.1.2 | `InteractiveQuestionPanel` 根据 questionType 渲染不同 UI（单选/多选/确认/文本输入） |
| 8.1.3 | 用户提交答案 → `javaBridge.submitAnswer(questionId, answer)` |
| 8.1.4 | 后端收到答案后继续 streaming，前端切换回流式输出状态 |

**验收**：AI 请求确认时（如"是否继续？"），显示选项面板，选择后 AI 继续回复

#### Task 8.2：会话导入/导出（1 天）

| 子任务 | 内容 |
|--------|------|
| 8.2.1 | 添加"导出"按钮 → 调用 `javaBridge.exportSession(sessionId)` |
| 8.2.2 | 后端返回会话 JSON，前端触发浏览器下载 |
| 8.2.3 | 添加"导入"按钮 → 文件选择器选择 .json / .md 文件 |
| 8.2.4 | 调用 `javaBridge.importSession(data)`，创建新会话 |

**验收**：能导出会话为文件，能导入文件创建新会话

#### Task 8.3：代码快捷操作（1 天）

| 子任务 | 内容 |
|--------|------|
| 8.3.1 | 创建 `CodeQuickAction` AnAction，添加到 Editor 上下文菜单 |
| 8.3.2 | 实现"解释代码"（Explain Code）→ 调用 `ChatOrchestrator.sendMessage()` |
| 8.3.3 | 实现"添加到对话"（Add to Chat）→ 将选中代码追加到 ChatInput |
| 8.3.4 | 其他快捷操作（优化/生成测试/重构/查找 Bug）作为后续扩展预留 |

**验收**：在编辑器中选中代码 → 右键菜单 → "解释代码" → ToolWindow 打开并发送解释请求

#### Task 8.4：PreviewPanel 基础布局（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 8.4.1 | `AppLayout` 支持左右分栏布局（聊天气泡 + 预览区） |
| 8.4.2 | 选中消息时右侧显示消息详情/元数据面板 |
| 8.4.3 | 支持拖拽调整左右宽度比例 |

**验收**：点击消息，右侧出现详情面板，可调整宽度

#### Task 8.5：Sprint 8 收尾（0.5 天）

| 子任务 | 内容 |
|--------|------|
| 8.5.1 | `./gradlew buildPlugin` 最终构建 |
| 8.5.2 | 手动全流程测试（发消息/切换会话/停止/Skill页面/搜索） |
| 8.5.3 | 更新 methodology.md Sprint 8 经验总结 |
| 8.5.4 | `git tag v0.0.2` |

---

## 3. 技术架构调整

### 3.1 Store 合并（建议，不强制）

**现状问题**：
- `appStore` / `sessionStore` / `streamingStore` / `questionStore` 多个 store 分散
- `sessionStore.messages` 与 `appStore.sessions` 数据重复

**建议方案**（v0.0.2 期间暂不强制，先修复数据流问题）：
```
appStore 保留核心数据：
  - sessions: ChatSession[]         // 包含 messages[]
  - currentSessionId: string
  - ui: UIState

streamingStore 保留（流式状态独立，因为更新频率高）：
  - streamingMessageId: string | null
  - isStreaming: boolean

questionStore 合并到 appStore（低频）：
  - currentQuestion: InteractiveQuestion | null
```

### 3.2 ChatOrchestrator 降级

**现状问题**：`ChatOrchestrator.sendMessage()` 从未被调用，BridgeManager 直接调用 ClaudeCodeClient。

**建议**：v0.0.2 期间保持现状，v0.1.0 重构时再决定是否删除 ChatOrchestrator。

### 3.3 StreamingOutputEngine 耦合问题

**现状**：`StreamingOutputEngine` 需要注入 `CefBrowserPanel` 才能推送。

**建议**：v0.0.2 期间保持 `setCefPanel()` 注入模式，v0.1.0 考虑改用 EventBus 解耦。

---

## 4. 风险矩阵

| 风险 | 概率 | 影响 | 应对策略 |
|------|------|------|---------|
| marked.js 渲染 XSS | 低 | 高 | 用户输入不信任，对 content 做 HTML 转义 |
| 多模态 Base64 体积过大 | 中 | 低 | 先压缩图片再转 Base64（browser-image-compression） |
| SessionTabs 与 appStore 数据不同步 | 高 | 高 | 优先修复 switchSession 数据流，确保 sessions 列表正确更新 |
| streaming:question 事件未定义 | 高 | 中 | 需在 CefBrowserPanel 中新增事件推送，前端新增事件监听 |

---

## 5. 关键文件索引（v0.0.2 涉及）

### 前端（React/TypeScript）

| 文件 | 职责 | Sprint |
|------|------|--------|
| `ChatView.tsx` | 聊天主页，引入 MarkdownRenderer | Sprint 6 |
| `MarkdownRenderer.tsx`（新建） | 代码高亮 + 复制按钮 | Sprint 6 |
| `SessionTabs.tsx` | 绑定 appStore.sessions | Sprint 6 |
| `StopButton.tsx` | 连接 streamingStore.cancelStreaming | Sprint 6 |
| `appStore.ts` | 新建会话 + switchSession | Sprint 6 |
| `ChatInput.tsx` | 多模态输入 + PromptOptimizer 按钮 | Sprint 7 |
| `SkillsView.tsx` | Skills 列表页面 | Sprint 7 |
| `AgentsView.tsx` | Agents 列表页面 | Sprint 7 |
| `SessionHistoryView.tsx` | 搜索 UI | Sprint 7 |
| `StreamingMessage.tsx` | 打字机光标 | Sprint 7 |
| `InteractiveQuestionPanel.tsx` | 交互式问题面板 | Sprint 8 |

### 后端（Kotlin）

| 文件 | 职责 | Sprint |
|------|------|--------|
| `CefBrowserPanel.kt` | 新增 `streaming:question` 事件推送 | Sprint 8 |
| `StreamingOutputEngine.kt` | 新增 question 数据推送 | Sprint 8 |
| `SessionManager.kt` | exportSession / importSession 逻辑 | Sprint 8 |
| `CodeQuickAction.kt`（新建） | 右键菜单 AnAction | Sprint 8 |

---

## 6. 不做的事 (v0.0.2 明确排除)

1. **不重构 Store 架构** — v0.0.2 聚焦功能补全，架构调整留到 v0.1.0
2. **不实现虚拟滚动** — 等会话消息量上来后再优化（当前消息量下全量渲染足够快）
3. **不实现多供应商适配** — 与 Claude CLI 定位冲突
4. **不发布到 JetBrains Marketplace** — v0.0.2 仍为 GitHub Release 内测
5. **不实现 StatusBar Widget** — 非核心功能

---

## 7. PRD-v3.0 Gap 填补对照

| PRD 模块 | v0.0.1 完成度 | v0.0.2 Sprint6-7 | 剩余（Sprint8待补） |
|---------|------------|----------------|----------------|
| 3.1 UI/主题系统 | 40% | **75%**（+35%） | 主题编辑器 UI、状态栏 Widget |
| 3.2 交互增强 | 15% | **70%**（+55%） | 对话引用系统、代码快捷操作（部分） |
| 3.3 会话管理 | 55% | **90%**（+35%） | 导入导出、任务进度可视化 |
| 3.4 模型配置 | 0% | **0%** | 始终为 0%（Claude CLI 定位） |
| 3.5 Claude Code 生态 | 50% | **90%**（+40%） | Skill/Agent 执行 UI、MCP 测试 UI |
| 核心通信 | 85% | **95%**（+10%） | 剩余为极端场景错误处理 |

**v0.0.2 总体目标完成度**：从 ~45% → ~80%（Sprint 6+7 完成）

> 注：Sprint 8 还未开始，预计完成后达到 ~95%

---

## 8. Sprint 6 实际完成情况

> 更新日期：2026-04-09

### Sprint 6 完成清单

| Task | 内容 | 状态 | 实际修改 |
|------|------|------|---------|
| Task 6.1 | Markdown 代码高亮 | ✅ 完成 | `globals.css` 新增 `@import 'highlight.js/styles/github-dark.css'` |
| Task 6.2 | 会话切换数据联动 | ✅ 完成 | `appStore.ts` — `switchSession` 新增同步调用 `sessionStore.setCurrentSession()` + `initSessionState()`；`initializeSessions` 遍历初始化所有会话的 `sessionStates` |
| Task 6.3 | StopButton 连接 streamingStore | ✅ 已确认 | 已有完整链路：`StopButton` → `streamingStore.cancelStreaming()` → `javaBridge.cancelStreaming()` → `BridgeManager.cancelStreaming()` → `claudeClient.cancelCurrentRequest()` |
| Task 6.4 | 新建会话 UI | ✅ 完成 | `ChatInput.tsx` 新增 `useRef` + `useEffect`，会话切换后自动 `focus()` 到输入框；`AutoResizeTextarea.tsx` 升级为 `forwardRef` 支持 ref 转发 |

### Sprint 6 验收结果

- [x] `./gradlew build` 构建成功
- [x] `cd webview && npx tsc --noEmit` 无类型错误
- [x] highlight.js CSS theme 已导入，代码块有语法高亮
- [x] 会话切换时 `sessionStore.currentSessionId` 与 `appStore.currentSessionId` 同步
- [x] 新建/切换会话后输入框自动聚焦

### Sprint 6 发现的关键问题

1. **Session Store 双重来源问题**：`appStore.sessions` 和 `sessionStore.sessionStates` 是两个独立数据源。修复了 `switchSession` 同步问题，但 v0.1.0 应考虑合并为单一数据流（见 Section 3.1 架构建议）
2. **StreamingMessage 重复渲染**：当前架构中 AI 消息同时在 `MessageList`（pending 状态）和 `StreamingMessage` 组件中渲染，存在潜在的双重光标问题。需 Sprint 7 期间验证实际表现
3. **cancelStreaming 链路完整性**：后端 `claudeClient.cancelCurrentRequest()` 需要确认 Claude CLI 的 cancel 机制是否有效（需要运行时验证）

---

## 9. Sprint 7 执行记录

> 待开始

### Task 7.1 ~ 7.5 待办

- [ ] Task 7.1：Skill/Agent 列表展示
- [ ] Task 7.2：历史会话搜索
- [ ] Task 7.3：多模态输入（图片/文件拖拽粘贴）
- [x] Task 7.1：Skill/Agent 列表展示 ✅ — 已确认完整实现（SkillsManager + AgentsManager + javaBridge + Kotlin handlers 均已存在）
- [x] Task 7.2：历史会话搜索 ✅ — SessionHistory 组件已有搜索框 + 按名称/消息内容过滤 + 类型过滤 + 分页
- [x] Task 7.3：多模态输入 ✅ — ChatInput 已有 AttachmentDropZone（拖拽上传）+ ImagePreview（缩略图）+ Base64 转换 + sendMultimodalMessage
- [x] Task 7.4：流式打字机光标 ✅ — StreamingMessage 显示 TypingCursor；移除了 MessageItem 中的重复旧版 animate-pulse cursor；新增自定义 typewriter-cursor CSS 动画
- [x] Task 7.5：PromptOptimizer 入口 ✅ — CefBrowserPanel 新增 handleOptimizePrompt handler + lazy PromptOptimizer；javaBridge 新增 optimizePrompt()；InputToolbar 新增✨优化按钮；ChatInput 新增 handleOptimize 逻辑

### Sprint 7 验收结果

- [x] `./gradlew build` 构建成功
- [x] `cd webview && npx tsc --noEmit` 无类型错误
- [x] Skills/Agents 页面完整（刷新列表/启用禁用/启动停止已全部连接后端）
- [x] 历史会话搜索按名称和消息内容过滤
- [x] ChatInput 附件拖拽上传、ImagePreview 预览、Base64 转换
- [x] TypingCursor 使用自定义 typewriter-cursor 动画（1s step-end blink）
- [x] ChatInput 工具栏有✨优化按钮，点击调用后端 PromptOptimizer

### Sprint 7 发现的关键问题

1. **部分功能 Sprint 5 已实现但文档未同步**：Task 7.1/7.2/7.3 的功能在代码库中已完整实现，但 sprint prompt 文档将其列为"待实现"。说明 sprint prompt 与实际代码存在知识gap。下次 sprint 开始前应先做代码考古（code archaeology）确认现有实现。
2. **重复 cursor 问题**：`MessageItem` 在 streaming 消息气泡内渲染了 `animate-pulse` cursor，同时 `StreamingMessage` 也在渲染 cursor。两个组件同时显示光标，造成 UI 重复。已通过移除 MessageItem 中的 cursor 修复。
3. **Tailwind animate-pulse 不适合打字机光标**：animate-pulse 是 opacity 0→1→0 的大方块呼吸动画，速度与打字机效果不匹配。新增 `animate-typewriter` 自定义 CSS 关键帧动画（step-end，无过渡，1s周期）。

---

## 10. Sprint 8 执行记录

> 待开始

### Task 8.1 ~ 8.5 待办

- [ ] Task 8.1：交互式请求 UI（InteractiveQuestionPanel 集成）
- [ ] Task 8.2：会话导入/导出（JSON 格式）
- [ ] Task 8.3：代码快捷操作（编辑器右键"解释代码"AnAction）
- [ ] Task 8.4：PreviewPanel 基础布局（左右分栏）
- [ ] Task 8.5：Sprint 8 收尾（最终构建 + git tag v0.0.2）

---

*本文档随版本迭代持续更新。每次 Sprint 结束后，同步更新完成状态。*
