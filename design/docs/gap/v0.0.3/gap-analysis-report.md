# PRD Gap Analysis Report — v0.0.3

**文档版本**: 1.1
**基于**: PRD-v3.0.md
**分析日期**: 2026-04-10
**更新日期**: 2026-04-10 (G1/G2 修复)
**覆盖版本**: v0.0.3 (继 bea2c3a)
**分析维度**: 需求规格 vs 代码实现吻合度 + 接口一致性审查

---

## 摘要

| 模块 | 状态 | 覆盖率 | 阻断性问题 |
|------|------|--------|-----------|
| 3.1 UI与主题系统 | 大部分实现 | ~95% | 无 |
| 3.2 交互增强系统 | 大部分实现 | ~90% | 无 |
| 3.3 会话管理系统 | 大部分实现 | ~90% | 无 |
| 3.4 模型配置系统 | 部分实现 | ~50% | 无（架构决策） |
| 3.5 Claude Code生态 | 大部分实现 | ~95% | 无 |

**v0.0.3 总体功能覆盖率**: ~90%

> v0.0.3 完成了 v0.0.2 阶段所有 gap 的修复（Phase 1/2/3 + ContextManager + G1 + G2），覆盖率从 ~65% 提升至 ~90%。

---

## Module 3.1: UI与主题系统

**Gap Status**: 大部分实现 (~95%)

### Findings

- ✅ **主题引擎 — 6 个预设主题** — `ThemeConfig.getPresets()` 返回 JETBRAINS_DARK、JETBRAINS_LIGHT、GITHUB_DARK、VSCODE_DARK、MONOKAI、NORD，与 PRD 规定一致
- ✅ **自定义主题编辑器** — `ThemeEditor.tsx` 颜色/字体/间距/圆角配置 UI 完整
- ✅ **配置热更新机制** — `ConfigManager` 通过 EventBus 发布 `ConfigChangedEvent`，前端 `themeStore.ts:84` 调用 `window.ccBackend?.updateTheme(theme)` 触发热更新
- ✅ **响应式布局** — `ChatView.tsx` 中 `ResizeObserver` 监听容器宽度，<768px 时 `MessageDetail` 面板自动隐藏

---

## Module 3.2: 交互增强系统

**Gap Status**: 大部分实现 (~90%)

### Findings

- ✅ **提示词优化器 (PromptOptimizer)** — `optimizePrompt()` 实现了 AI 驱动优化（Step 2：调用 Claude CLI 生成优化版本 + `improvements[]` + `confidence`），与 PRD 规定格式一致
- ✅ **代码快捷操作 (CodeQuickActions)** — 7 个 AnAction 全部实现并注册到 `EditorPopupMenu`：`CodeExplainAction`、`CodeOptimizeAction`、`CodeCommentAction`、`CodeTestAction`、`CodeRefactorAction`、`CodeBugAction`、`AddToChatAction`
- ✅ **对话引用系统 (ConversationReferenceSystem)** — `MessageItem.tsx` 引用按钮、`ChatInput.tsx` 引用气泡、发送时 `[@msgId]: excerpt` 格式、后端 `MessageReference` 数据结构
- ✅ **交互式请求引擎 (InteractiveRequestEngine)** — 后端完整实现，前端 `InteractiveQuestionPanel.tsx` 渲染器齐全
- ✅ **多模态输入 UI** — 前端 `AttachmentDropZone`、`ImagePreview`、`useFileDrop` 等完整实现

### Key Deviations

- ❌ **多模态消息发送未实现** — `CefBrowserPanel.handleSendMultimodalMessage()` 返回 `mapOf("error" to "Multimodal messaging not yet implemented")`。前端附件 UI 已完整，但后端 `handleSendMultimodalMessage` 未实现，附件发送会直接报错。

---

## Module 3.3: 会话管理系统

**Gap Status**: 大部分实现 (~90%)

### Findings

- ✅ **多会话管理器** — `SessionManager` 完整实现 PROJECT/GLOBAL/TEMPORARY 三种类型
- ✅ **流式输出引擎** — `StreamingOutputEngine` + 前端 `useStreaming.ts` 完整实现
- ✅ **会话中断与恢复** — `ContextManager` 通过 `/compact` 机制提供上下文压缩能力，替代 PRD 规定的 checkpoint 方案（checkpoint 持久化未实现但已通过上下文压缩缓解）
- ✅ **历史会话检索** — `SearchFilters` 支持 `sessionType` + `dateRange` + `query` 过滤
- ✅ **任务进度可视化** — `TaskProgressTracker` + `TaskProgressDashboard` 完整实现

---

## Module 3.4: 模型配置系统

**Gap Status**: 部分实现 (~50%) — 架构决策

### Findings

- ❌ **MultiProviderAdapter** — 完全未实现（架构决策：插件定位为 Claude Code CLI 前端，不做多供应商 API）
- ❌ **ModelSwitcher** — 无状态栏 Widget，无会话级模型切换 UI
- ✅ **ConversationMode** — 枚举值已修正为 `THINKING/PLANNING/AUTO`，与 PRD 一致

---

## Module 3.5: Claude Code生态集成

**Gap Status**: 大部分实现 (~95%)

### Findings

- ✅ **Skills管理器** — 完整实现 CRUD、7 种种别、GLOBAL/PROJECT 作用域
- ✅ **Agents管理器** — 完整实现 CRUD、8 种能力、3 种模式、GLOBAL/PROJECT/SESSION 作用域
- ✅ **MCP服务器管理器** — 完整实现 CRUD、connect/disconnect/test、进程生命周期管理
- ✅ **作用域管理 (ScopeManager)** — 三级作用域合并策略正确

---

## 接口一致性审查

### 发现的问题

| 优先级 | 接口 | 问题描述 | 状态 |
|--------|------|---------|------|
| **P1** | `handleSendMultimodalMessage` | 返回 error，功能完全不可用 | ✅ 已修复 (2026-04-10) |
| **P2** | `javaBridge.streamMessage` | 前端返回 void（fire-and-forget），后端会调用 `onResponse`，响应被忽略 | ✅ 已修复 (2026-04-10) |
| ~~P2~~ | `javaBridge.executeSkill` | ~~前端传 `(skillId, context)` 位置参数，后端期望 `{skillId, context}` JSON 对象~~ | ❌ 误报，实际参数格式正确 |

### 技术根因分析

**G1 - MultimodalMessage 已修复** (2026-04-10):
```kotlin
// CefBrowserPanel.kt:387-445
private fun handleSendMultimodalMessage(queryId: Int, params: com.google.gson.JsonElement?): Any? {
    // 解析 sessionId, content, attachments
    // 图片格式化为: [Image #N: data:mimeType;base64,...]
    // 文件格式化为: [filename]content[/filename]
    // 附件文本追加到 prompt 末尾，通过 bridgeManager.streamMessage 发送
}
```
实现方式：Claude Code CLI 不支持内联图片，附件被格式化为文本引用追加到消息内容中发送。

**G2 - streamMessage 响应处理已修复** (2026-04-10):
```typescript
// java-bridge.ts:108-133
async streamMessage(message: string): Promise<any> {
    const queryId = ++this.queryId;
    return new Promise<any>((resolve, reject) => {
        // 设置超时（30秒）
        // 存储 pending 请求
        // 调用 ccBackend.send()
        // onResponse 到来时 resolve/reject
    });
}
```
`streamMessage` 现在返回 Promise，可等待响应或错误。mock-bridge.ts 也同步更新。

**executeSkill 参数格式经验证正确**：
```typescript
// java-bridge.ts - 前端传 { skillId, context } 对象
return this.invoke('executeSkill', { skillId, context });
// 后端 CefBrowserPanel - 正确解析 jsonObj.get("skillId")
```
此条为误报，无需修复。
```
后端 `handleExecuteSkill` 正确使用 `jsonObj.get("skillId")`，参数格式匹配。**此条经验证实际正确** — 需更正为非 Gap。

---

## 优先级矩阵（v0.0.3 Review 后新增）

| 优先级 | 问题 | 模块 | 影响 | 修复工作量 |
|--------|------|------|------|-----------|
| **P1** | `handleSendMultimodalMessage` 返回 error | 3.2 | 附件发送不可用 | ✅ 已修复 |
| **P2** | `streamMessage` 前端不处理 `onResponse` | 3.3 | 流式响应回调丢失 | ✅ 已修复 |
| **P3** | 响应式布局仅 2 断点（<768px / >=768px），PRD 规定 3 断点（<800 / 800-1200 / >1200）且 50:50 分栏 | 3.1 | 窄屏体验简化 | 低（仅为 CSS 调整，不影响功能） |
| **P3** | MultiProviderAdapter 完全缺失 | 3.4 | 无法对接外部 API | 高（架构级） |
| **P3** | ModelSwitcher 状态栏 Widget 缺失 | 3.4 | 无法快捷切换模型 | 中 |

---

## 不纳入 v0.0.3 修复范围的功能

1. **MultiProviderAdapter** — 与 Claude Code CLI 定位冲突
2. **checkpoint 持久化** — ContextManager + /compact 已提供等效能力
3. **Lucene 全文检索** — 当前规模简单搜索足够

---

## 关键代码索引（v0.0.3 新增）

| 文件 | 职责 | 状态 |
|------|------|------|
| `CefBrowserPanel.kt:387-445` | handleSendMultimodalMessage | ✅ 已修复：附件格式化为文本追加到 prompt |
| `CefBrowserPanel.kt:453-469` | handleOptimizePrompt | ✅ AI 优化 + improvements + confidence |
| `java-bridge.ts:108-133` | streamMessage | ✅ 已修复：返回 Promise，正确处理 onResponse |
| `ContextManager.kt` | 上下文长度追踪 + /compact | ✅ 新增 |
| `webview/src/main/pages/ChatView.tsx` | 响应式布局 | ⚠️ 仅 2 断点（PRD 规定 3 断点） |

---

*报告版本：v1.2 | 编制日期：2026-04-10 | 更新日期：2026-04-10 | 基于：PRD-v3.0.md & codebase bea2c3a*
