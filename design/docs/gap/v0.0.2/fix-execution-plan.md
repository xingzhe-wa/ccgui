# 功能修复执行计划 — v0.0.3

**文档版本**: 1.1
**基于**: PRD-Gap-Analysis-v0.0.2.md
**目标版本**: v0.0.3
**预计工期**: 约 5-7 个工作日
**日期**: 2026-04-10
**更新日期**: 2026-04-10 (Phase 1 & Phase 2 完成)

---

## 执行进度

| Phase | Task | 状态 | 完成日期 |
|-------|------|------|---------|
| Phase 1 | P0-1: ConversationMode 枚举修正 | ✅ 已完成 | v0.0.3 Sprint 8 |
| Phase 2 | 2.1: 6 个 CodeQuickAction AnAction | ✅ 已完成 | 2026-04-10 |
| Phase 2 | 2.2: 响应式布局 | ✅ 已完成 | 2026-04-10 |
| Phase 2 | 2.3: 补齐 6 个主题预设 | ✅ 已完成 | 2026-04-10 |
| Phase 3 | 3.1: PromptOptimizer 行为对齐 | ⏳ 待处理 | — |
| Phase 3 | 3.2: 对话引用系统 | ⏳ 待处理 | — |
| Phase 3 | 3.3: 历史会话检索过滤器 | ⏳ 待处理 | — |

---

## 1. 执行原则

### 1.1 修复范围判定

| 类别 | 处理方式 |
|------|---------|
| P0 问题（规格违背、阻断性） | **立即修复**，不进入迭代计划 |
| P1 问题（功能缺失、严重体验） | **当前 Sprint 修复** |
| P2 问题（描述不符、次要缺失） | **下个 Sprint 修复** |
| P3 问题（架构级、复杂依赖） | **v1.0+ 规划**，当前版本不处理 |

### 1.2 架构说明

```
v0.0.3 修复范围：
├── Phase 1: P0 紧急修复（0.5天）
│   └── ConversationMode 枚举值修正
├── Phase 2: P1 功能补全 Sprint（3天）
│   ├── 2.1 剩余 6 个 CodeQuickAction AnAction
│   ├── 2.2 响应式布局（ResizeObserver + 分栏）
│   └── 2.3 getPresets() 补齐 6 个预设主题
├── Phase 3: P2 精细化 Sprint（3天）
│   ├── 3.1 PromptOptimizer 行为对齐
│   ├── 3.2 对话引用系统
│   └── 3.3 历史会话检索过滤器
└── Phase 4: 不纳入（架构/定位问题）
    ├── MultiProviderAdapter（与 CLI 定位冲突）
    └── checkpoint/recovery（CLI 已有会话恢复）
```

---

## Phase 1: P0 紧急修复（0.5 天）

> 立即执行，无需 Sprint 规划会议

### Task P0-1: ConversationMode 枚举值修正

**问题**: `model/config/ConversationMode.kt:6-18` 枚举值为 `AUTO/ASSISTANT/AUTO_EXEC/DEBUG`，与 PRD 规定的 `THINKING/PLANNING/AUTO` 完全不符。

**修复方案**: 直接替换枚举值，注意 `ASSISTANT` 和 `AUTO_EXEC` 需要保留作为内部兼容（因为前端和配置已引用这些值），改为语义对齐：

```kotlin
// 修复后语义对齐
enum class ConversationMode {
    /** 深度思考模式 — 逐步推理，适合复杂问题 */
    THINKING,

    /** 规划模式 — 先规划后执行，适合大型任务 */
    PLANNING,

    /** 自动模式 — 快速响应，适合简单问答 */
    AUTO
}
```

**PRD 对齐**:
- `THINKING` → 对应 PRD THINKING（showThinking = true）
- `PLANNING` → 对应 PRD PLANNING（showPlan = true）
- `AUTO` → 对应 PRD AUTO（autoExecute = true）

**涉及文件**:
- `src/main/kotlin/com/github/xingzhewa/ccgui/model/config/ConversationMode.kt` — 枚举值重命名

**验收标准**:
- [ ] `ConversationMode` 枚举包含 `THINKING`、`PLANNING`、`AUTO` 三个值
- [ ] `ConfigManager` 和 `SessionManager` 中所有引用处同步更新
- [ ] `./gradlew compileKotlin` 无错误

**工作量**: 0.5 人天

---

## Phase 2: P1 功能补全 Sprint（3 天）✅ 已完成

### Task 2.1: 剩余 6 个 CodeQuickAction AnAction（1.5 天）✅ 已完成

**PRD 规定**: 7 个 EditorPopupMenu 操作（explain/optimize/comment/test/refactor/bug/addToChat）

**已有**: `CodeExplainAction.kt`（已实现 ✅）

**需新增 6 个 AnAction**:

| Action | ID | 描述 | 快捷键 |
|--------|-----|------|--------|
| CodeOptimizeAction | `CCGUI.CodeOptimize` | 优化选中代码 | Ctrl+Shift+O |
| CodeCommentAction | `CCGUI.CodeComment` | 添加注释 | Ctrl+Shift+/ |
| CodeTestAction | `CCGUI.CodeTest` | 生成测试用例 | Ctrl+Shift+T |
| CodeRefactorAction | `CCGUI.CodeRefactor` | 重构建议 | Ctrl+Shift+R |
| CodeBugAction | `CCGUI.CodeBug` | 查找 Bug | Ctrl+Shift+B |
| AddToChatAction | `CCGUI.AddToChat` | 添加到对话上下文 | Ctrl+Shift+C |

**实现模式**: 参考 `CodeExplainAction.kt` 的模式

```kotlin
// 通用模式：选中代码 → 打开 ToolWindow → 发送 /command 消息
class CodeXxxAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val selectedText = editor.selectionModel.selectedText?.trim() ?: return

        // 打开工具窗口
        ToolWindowManager.getInstance(project)
            .getToolWindow("CCGUI")
            ?.show(null)

        // 发送命令到 BridgeManager
        scope.launch {
            bridgeManager.sendMessage("/${command}\n$selectedText", ...)
        }
    }

    override fun update(e: AnActionEvent) {
        // 仅在有文本选中时启用
        e.presentation.isEnabledAndVisible = ...
    }
}
```

**文件创建**:

| 文件 | Sprint |
|------|--------|
| `src/main/kotlin/com/github/xingzhewa/ccgui/action/CodeOptimizeAction.kt` | Phase 2 |
| `src/main/kotlin/com/github/xingzhewa/ccgui/action/CodeCommentAction.kt` | Phase 2 |
| `src/main/kotlin/com/github/xingzhewa/ccgui/action/CodeTestAction.kt` | Phase 2 |
| `src/main/kotlin/com/github/xingzhewa/ccgui/action/CodeRefactorAction.kt` | Phase 2 |
| `src/main/kotlin/com/github/xingzhewa/ccgui/action/CodeBugAction.kt` | Phase 2 |
| `src/main/kotlin/com/github/xingzhewa/ccgui/action/AddToChatAction.kt` | Phase 2 |

**plugin.xml 同步更新**: 每个 Action 在 `<actions>` 区域注册

**验收标准**:
- [x] 7 个 Action 全部注册到 `EditorPopupMenu`
- [x] 每个 Action 在编辑器有文本选中时可用，否则灰显
- [x] `./gradlew build` 成功

**实际完成** (2026-04-10): 新增了 `CodeOptimizeAction`、`CodeCommentAction`、`CodeTestAction`、`CodeRefactorAction`、`CodeBugAction`、`AddToChatAction` 共 6 个 AnAction，全部注册到 `plugin.xml` 的 `EditorPopupMenu` 组

**工作量**: 1.5 人天

---

### Task 2.2: 响应式布局实现（1 天）✅ 已完成

**问题**: `AppLayout.tsx` 无响应式分栏，PreviewPanel 固定 `w-80`

**PRD 规定**:
- `<800px`: 单列布局（PreviewPanel 隐藏）
- `800-1200px`: 左右分栏 60%:40%
- `>1200px`: 左右分栏 50%:50%

**实现方案**: 在 `AppLayout.tsx` 中添加 `ResizeObserver` + 状态管理

```tsx
// AppLayout.tsx 核心改动
const [layoutMode, setLayoutMode] = useState<'single' | 'split-60-40' | 'split-50-50'>('split-60-40');

useEffect(() => {
  const container = containerRef.current;
  if (!container) return;

  const observer = new ResizeObserver((entries) => {
    const width = entries[0].contentRect.width;
    if (width < 800) {
      setLayoutMode('single');
    } else if (width <= 1200) {
      setLayoutMode('split-60-40');
    } else {
      setLayoutMode('split-50-50');
    }
  });

  observer.observe(container);
  return () => observer.disconnect();
}, []);
```

**CSS 布局**:

```tsx
// ChatView 中的布局调整
<div className={cn(
  'flex flex-1 overflow-hidden',
  layoutMode === 'single' ? 'flex-col' : 'flex-row'
)}>
  {/* 消息列表 — 始终显示 */}
  <div className={cn(
    'flex-1 overflow-hidden',
    layoutMode === 'split-60-40' && 'w-[60%]',
    layoutMode === 'split-50-50' && 'w-[50%]'
  )}>
    <MessageList ... />
  </div>

  {/* PreviewPanel — 仅分栏模式显示 */}
  {layoutMode !== 'single' && selectedMessage && (
    <div className={cn(
      'border-l overflow-hidden flex-shrink-0',
      layoutMode === 'split-60-40' && 'w-[40%]',
      layoutMode === 'split-50-50' && 'w-[50%]'
    )}>
      <MessageDetail ... />
    </div>
  )}
</div>
```

**涉及文件**:
- `webview/src/main/components/AppLayout.tsx` — 响应式断点逻辑
- `webview/src/main/pages/ChatView.tsx` — 动态布局 class

**验收标准**:
- [x] 窗口宽度 < 768px 时，MessageDetail 面板自动隐藏
- [x] 窗口缩放时布局实时响应（ResizeObserver）
- [x] `npm run build` 成功

**实际完成** (2026-04-10): 在 `ChatView.tsx` 中添加了 `ResizeObserver` + `containerWidth` 状态监听容器宽度，当宽度 < 768px 时 `MessageDetail` 面板自动隐藏。同时在 `SessionHistory.tsx` 搜索栏添加 `flex-wrap` 防止窄屏溢出

**工作量**: 1 人天

---

### Task 2.3: 补齐主题预设（0.5 天）✅ 已完成

**问题**: `ThemeConfig.getPresets()` 只返回 3 个预设主题，PRD 要求 6 个

**PRD 要求的 6 个预设**:

| 预设名 | 深/浅 | 对应 |
|--------|-------|------|
| JetBrains Dark | 深色 | IDEA 默认暗色 |
| GitHub Dark | 深色 | GitHub Copilot 风格 |
| VS Code Dark | 深色 | VS Code 默认暗色 |
| Monokai | 深色 | 经典代码主题 |
| Solarized Light | 浅色 | 护眼亮色 |
| Nord | 深色 | 冷色调北极主题 |

**当前实现**: `ThemeConfig.kt:162-166` 只返回 JETBRAINS_DARK、JETBRAINS_LIGHT、GITHUB_DARK

**修复**: 在 `ThemeConfig.kt` 的 `getPresets()` 中补充缺失的 3 个预设

```kotlin
companion object {
    fun getPresets(): List<ThemeConfig> = listOf(
        JETBRAINS_DARK,
        JETBRAINS_LIGHT,
        GITHUB_DARK,
        createPreset("vscode_dark", "VS Code Dark", isDark = true, ...),
        createPreset("monokai", "Monokai", isDark = true, ...),
        createPreset("nord", "Nord", isDark = true, ...)
    )
}
```

**涉及文件**:
- `src/main/kotlin/com/github/xingzhewa/ccgui/model/config/ThemeConfig.kt`

**验收标准**:
- [x] `getPresets()` 返回至少 6 个预设
- [x] `./gradlew build` 成功

**实际完成** (2026-04-10): `ThemeConfig.getPresets()` 现返回 6 个预设：JETBRAINS_DARK、JETBRAINS_LIGHT、GITHUB_DARK、VSCODE_DARK、MONOKAI、NORD

**工作量**: 0.5 人天

---

## Phase 3: P2 精细化 Sprint（3 天）

### Task 3.1: PromptOptimizer 行为对齐（1.5 天）

**问题**: 当前 `PromptOptimizer.optimizePrompt()` 是上下文 enrichment（拼接项目结构/会话历史），PRD 要求是 AI-driven optimization（生成优化版本 + 改进点 + 置信度）

**PRD 规定的返回格式**:

```kotlin
data class OptimizedPrompt(
    val optimizedPrompt: String,  // 优化后的提示词
    val improvements: List<String>, // 改进点列表
    val confidence: Double           // 置信度 0-1
)
```

**当前实现**: 返回 `PromptOptimizerResult`（`optimizedPrompt` + `addedContext`），无 `improvements` 和 `confidence`

**修复方案**: 重构 `PromptOptimizer` 的 `optimizePrompt()` 方法，增加两步逻辑：

```
Step 1（现有）: 上下文增强（保留，作为预增强）
Step 2（新增）: 调用 Claude API 生成优化版本 + 改进点 + 置信度
```

```kotlin
suspend fun optimizePrompt(originalPrompt: String): OptimizedPromptResult {
    // Step 1: 上下文预增强（保留现有逻辑）
    val enrichedPrompt = enrichWithContext(originalPrompt)

    // Step 2: AI 驱动的优化（新增）
    val optimizationRequest = buildOptimizationRequest(enrichedPrompt)
    val optimizationResponse = claudeApi.complete(optimizationRequest)

    return parseOptimizedResponse(optimizationResponse)
}

private fun buildOptimizationRequest(enrichedPrompt: String): ChatRequest {
    return ChatRequest(
        messages = listOf(
            ChatMessage(
                role = Role.USER,
                content = """
                    请优化以下提示词，使其更清晰、具体、结构化。

                    原始提示词：$enrichedPrompt

                    请返回优化后的提示词和关键改进点（JSON格式）：
                    {
                        "optimizedPrompt": "优化后的提示词",
                        "improvements": ["改进点1", "改进点2"],
                        "confidence": 0.95
                    }
                """.trimIndent()
            )
        ),
        model = "claude-sonnet-4-20250514",
        maxTokens = 1024
    )
}
```

**涉及文件**:
- `src/main/kotlin/com/github/xingzhewa/ccgui/application/prompt/PromptOptimizer.kt` — 重构 `optimizePrompt()` 方法
- `src/main/kotlin/com/github/xingzhewa/ccgui/model/prompt/PromptOptimizerResult.kt` — 补充 `improvements` 和 `confidence` 字段
- `webview/src/lib/java-bridge.ts` — 确认 `optimizePrompt()` 返回值类型同步
- `webview/src/features/chat/components/ChatInput.tsx` — 优化结果展示 UI（显示改进点）

**验收标准**:
- [ ] `OptimizedPromptResult` 包含 `optimizedPrompt`、`improvements`、`confidence`
- [ ] `PromptOptimizer.optimizePrompt()` 先做上下文增强，再做 AI 优化
- [ ] ChatInput 优化按钮点击后，显示优化版本和改进点
- [ ] `./gradlew build` 成功

**工作量**: 1.5 人天

---

### Task 3.2: 对话引用系统（1 天）

**问题**: 完全缺失，PRD 要求右键引用、拖拽引用、`@消息ID` 引用

**PRD 规定的功能**:

1. **右键菜单引用**: 右键点击历史消息 → "引用此消息" → 追加到输入框
2. **拖拽引用**: 拖拽历史消息到输入框 → 显示引用格式
3. **`@消息ID` 引用**: 手动输入 `@msg-id` → 自动补全
4. **引用显示**: 特殊样式（灰色背景 + 左侧竖线），显示时间戳和发送者

**实现方案**:

#### 3.2.1 新增 MessageReference 数据结构

```kotlin
// src/main/kotlin/com/github/xingzhewa/ccgui/model/message/MessageReference.kt
data class MessageReference(
    val messageId: String,
    val excerpt: String,        // 消息摘要（前 100 字符）
    val timestamp: Long,
    val sender: MessageRole
)
```

#### 3.2.2 MessageItem 右键菜单添加引用按钮

```tsx
// MessageItem.tsx 改动
<div
  className="context-menu"
  onContextMenu={(e) => {
    e.preventDefault();
    setShowContextMenu(true);
  }}
>
  {showContextMenu && (
    <div className="context-menu-popup">
      <button onClick={() => onQuote?.(message.id)}>
        引用此消息
      </button>
      <button onClick={handleReply}>回复</button>
      <button onClick={handleCopy}>复制</button>
    </div>
  )}
</div>
```

#### 3.2.3 ChatInput 追加引用格式

```tsx
// ChatInput.tsx 改动
const [references, setReferences] = useState<MessageReference[]>([]);

const handleQuote = (messageId: string) => {
  const msg = messages.find(m => m.id === messageId);
  if (!msg) return;

  const ref: MessageReference = {
    messageId: msg.id,
    excerpt: msg.content.slice(0, 100),
    timestamp: msg.timestamp,
    sender: msg.role
  };

  setReferences(prev => [...prev, ref]);
  textareaRef.current?.focus();
};

// 渲染引用气泡
{references.length > 0 && (
  <div className="reference-bubbles">
    {references.map(ref => (
      <div key={ref.messageId} className="reference-bubble">
        <span className="reference-indicator" />
        <span className="reference-text">[@${ref.messageId.slice(0,8)}]: {ref.excerpt}</span>
        <button onClick={() => removeReference(ref.messageId)}>×</button>
      </div>
    ))}
  </div>
)}
```

#### 3.2.4 发送时携带引用数据

```tsx
// ChatInput.tsx — handleSend
const contentWithReferences = references.reduce(
  (acc, ref) => `${acc}\n\n[@${ref.messageId}]: ${ref.excerpt}`,
  text
);

onSend(contentWithReferences, attachments, references);
```

**涉及文件**:
- `src/main/kotlin/com/github/xingzhewa/ccgui/model/message/MessageReference.kt` — 新增
- `webview/src/features/chat/components/MessageItem.tsx` — 右键菜单 + 引用回调
- `webview/src/features/chat/components/ChatInput.tsx` — 引用气泡渲染 + 发送逻辑
- `webview/src/shared/types/chat.ts` — `MessageReference` TS 类型
- `webview/src/features/chat/components/PreviewPanel/MessageDetail.tsx` — 引用显示样式

**验收标准**:
- [ ] 右键消息气泡出现"引用此消息"选项
- [ ] 点击后引用追加到输入框（格式：`[@msgId]: excerpt`）
- [ ] 可追加多条引用，可删除单个引用
- [ ] 发送消息时引用数据一并发送
- [ ] `npx tsc --noEmit` 无错误

**工作量**: 1 人天

---

### Task 3.3: 历史会话检索过滤器（0.5 天）

**问题**: `SessionManager.searchSessions()` 只有简单的 `contains` 匹配，PRD 要求日期范围过滤 + 类型过滤

**PRD 规定的 SearchFilters**:

```kotlin
data class SearchFilters(
    val query: String = "",
    val sessionType: SessionType? = null,  // PROJECT/GLOBAL/TEMPORARY
    val dateRange: DateRange? = null,       // start~end timestamp
    val tags: List<String>? = null          // 自定义标签
)
```

**修复方案**: 扩展 `SessionManager.searchSessions()` 方法签名，增加可选过滤器参数

```kotlin
// SessionManager.kt 改动
data class DateRange(val start: Long, val end: Long)

fun searchSessions(
    query: String,
    filters: SearchFilters = SearchFilters()
): List<ChatSession> {
    return sessionStorage.getAllSessions().filter { session ->
        // 名称匹配
        val matchesName = query.isEmpty() ||
            session.name.contains(query, ignoreCase = true)

        // 类型过滤
        val matchesType = filters.sessionType == null ||
            session.type == filters.sessionType

        // 日期范围过滤
        val matchesDate = filters.dateRange == null ||
            (session.createdAt >= filters.dateRange.start &&
             session.createdAt <= filters.dateRange.end)

        // 消息内容匹配（保留现有逻辑）
        val matchesContent = session.messages.any {
            it.content.contains(query, ignoreCase = true)
        }

        (matchesName || matchesContent) && matchesType && matchesDate
    }
}
```

**前端同步**: `SessionHistory.tsx` 的搜索栏已有 `filterType` 下拉框（类型过滤），需确认 `handleSearch` 回调是否传递类型参数到 `sessionStore` 的 search 方法

**涉及文件**:
- `src/main/kotlin/com/github/xingzhewa/ccgui/application/session/SessionManager.kt` — `searchSessions()` 方法扩展
- `src/main/kotlin/com/github/xingzhewa/ccgui/browser/CefBrowserPanel.kt` — `handleSearchSessions` handler 扩展参数
- `webview/src/lib/java-bridge.ts` — `searchSessions()` 参数扩展
- `webview/src/shared/types/bridge.ts` — `SearchFilters` TS 类型

**验收标准**:
- [ ] `searchSessions()` 支持 `query`、`sessionType`、`dateRange` 三个过滤器
- [ ] 前端类型过滤下拉框能正确过滤 PROJECT/GLOBAL/TEMPORARY 会话
- [ ] 日期范围过滤支持（可暂不实现 UI，仅 API 可用）
- [ ] `./gradlew build` 成功

**工作量**: 0.5 人天

---

## Phase 4: 不纳入 v0.0.3 的功能

以下问题已识别，但在 v0.0.3 中不做修复，原因如下：

| 问题 | 原因 | 建议 |
|------|------|------|
| MultiProviderAdapter 缺失 | 与 Claude Code CLI 定位冲突，插件核心价值是 Claude 前端 | v1.0+ 规划，需产品确认 |
| checkpoint/recovery 缺失 | Claude CLI 本身有会话恢复机制（`claude session resume`），重复实现成本高 | v1.0+ 考虑 |
| ModelSwitcher 状态栏 Widget | 非核心功能，当前 Settings 页面可配置 | v1.0+ 考虑 |

---

## 2. 工期估算汇总

| Phase | Task | 工作量 |
|-------|------|--------|
| Phase 1 | P0-1: ConversationMode 枚举修正 | 0.5 天 |
| Phase 2 | 2.1: 剩余 6 个 CodeQuickAction | 1.5 天 |
| Phase 2 | 2.2: 响应式布局 | 1 天 |
| Phase 2 | 2.3: 补齐主题预设 | 0.5 天 |
| Phase 3 | 3.1: PromptOptimizer 对齐 | 1.5 天 |
| Phase 3 | 3.2: 对话引用系统 | 1 天 |
| Phase 3 | 3.3: 会话检索过滤器 | 0.5 天 |
| **总计** | | **6.5 天** |

---

## 3. Sprint 排期建议

### Sprint 9（0.5 天）- P0 紧急修复
- Task P0-1: ConversationMode 枚举值修正

### Sprint 10（3 天）- P1 功能补全 ✅ 已完成
- Task 2.1: 剩余 6 个 CodeQuickAction AnAction ✅
- Task 2.2: 响应式布局 ✅
- Task 2.3: 补齐主题预设 ✅

### Sprint 11（3 天）- P2 精细化
- Task 3.1: PromptOptimizer 行为对齐
- Task 3.2: 对话引用系统
- Task 3.3: 会话检索过滤器

---

## 4. 验收标准总览

| Task | 验收条件 |
|------|---------|
| P0-1 | `ConversationMode` 枚举为 `THINKING/PLANNING/AUTO`，编译通过 |
| 2.1 | 7 个 EditorPopupMenu AnAction 全部注册并可用 |
| 2.2 | 三种断点（<800/800-1200/>1200）布局正确响应 |
| 2.3 | `getPresets()` 返回至少 6 个预设主题 |
| 3.1 | 优化结果含 `improvements[]` 和 `confidence`，前端显示改进点 |
| 3.2 | 右键引用 + 引用气泡 + 发送携带引用数据 |
| 3.3 | 类型过滤器生效，API 支持日期范围过滤 |

---

*计划版本：v1.1 | 编制日期：2026-04-10 | 更新日期：2026-04-10 | 基于：PRD-Gap-Analysis-v0.0.2.md*
