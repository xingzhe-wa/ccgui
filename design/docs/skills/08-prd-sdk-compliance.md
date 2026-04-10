# Skill 08: PRD-SDK 合规开发指南

**适用版本**: PRD-v3.1.md + Claude SDK Integration Guide + 00-development-pitfalls-and-lessons.md

**触发条件**: 任何代码修改前、bug 修复后新盲点记录

---

## 1. 参考文档索引

| 文档 | 路径 | 用途 |
|------|------|------|
| PRD-v3.1.md | `design/docs/PRD-v3.1.md` | 产品需求规格、架构决策、技术选型 |
| Claude SDK Integration Guide | `design/docs/sdk/claude-sdk-integration-guide.md` | NDJSON 协议、Daemon 模式、标签体系、进程通信 |
| 踩坑记录 | `design/docs/00-development-pitfalls-and-lessons.md` | PIT-001~PIT-008 及经验总结 |

### 1.1 PRD-v3.1 核心要点速查

**MVP 范围（Phase 1）**：
- ✅ 单会话聊天
- ✅ Claude Agent SDK 直连（daemon.js stdin/stdout NDJSON）
- ✅ 基础 Markdown 渲染
- ✅ 主题切换（预设主题）

**废弃方案（注意规避）**：
- ~~HTTP + SSE~~ → 已废弃，改为 stdin/stdout + NDJSON
- ~~多供应商适配器~~ → v1.0+ Phase 3

**NDJSON 协议标签体系**（必须严格遵循）：

| 标签 | 含义 |
|------|------|
| `[MESSAGE_START]` | SDK 开始处理消息 |
| `[STREAM_START]` | 流式输出开始 |
| `[CONTENT_DELTA]` | 文本增量（已转义 JSON 字符串） |
| `[THINKING_DELTA]` | 思考过程增量 |
| `[TOOL_USE]` | 工具调用请求 |
| `[TOOL_RESULT]` | 工具执行结果 |
| `[SESSION_ID]` | 会话 ID |
| `[USAGE]` | Token 使用量 |
| `[MESSAGE_END]` | 单轮消息结束 |
| `{"done":true,"success":true}` | 请求完成成功 |

### 1.2 踩坑记录速查表

| 编号 | 问题 | 根因 | 关键位置 |
|------|------|------|----------|
| PIT-001 | JCEF 白屏 | loadURL 异步但 JS 注入在 DOM 完成前执行 | `CefBrowserPanel.kt:loadHtmlPage()` |
| PIT-002 | 生产构建白屏 | Vite base:'/' 生成绝对路径，file:// 无法加载 | `webview/vite.config.ts:8` |
| PIT-003 | 流式输出不显示 | Java ccEvents 与前端 eventBus 无桥接 | `CefBrowserPanel.kt:injectBackendJavaScript()` |
| PIT-004 | 插件启动崩溃 | Dispatchers.Main 在 postStartupActivity 未就绪 | `EventBus.kt:34` |
| PIT-005 | 流式消息不更新 UI | streaming 事件缺少 messageId 字段 | `CefBrowserPanel.kt:handleSendMessage()` |
| PIT-006 | 发送消息无响应 | handleSendMessage 少解析一层 JSON 嵌套 | `CefBrowserPanel.kt:handleSendMessage()` |
| PIT-007 | 事件推送失败 | ToolWindow ID 与 plugin.xml 不一致 | `MyProjectActivity.kt:87` |
| PIT-008 | 侧边栏显示错误名称 | 插件 ID 与显示名未分离 | `plugin.xml:4`, `MyToolWindowFactory.kt:39` |

---

## 2. 开发前检查清单

任何代码修改前，必须确认以下事项：

### 2.1 技术规范遵循

- [ ] **NDJSON 协议**：新增的 Java → JS 事件是否遵循标签体系？是否有 `messageId` 用于关联？
- [ ] **JCEF 时序**：`loadHtmlPage()` / `loadURL()` 后是否等待 `onLoadEnd` 再注入 JS？
- [ ] **协程调度**：所有 `CoroutineScope` 是否使用 `Dispatchers.Default`？仅在需要 UI 操作时用 `withContext(Dispatchers.EDT)`
- [ ] **前后端通信**：JSON 序列化/反序列化是否正确？嵌套 JSON 解析是否完整？
- [ ] **Vite 构建路径**：`vite.config.ts` 的 `base` 是否为 `'./'`？

### 2.2 PRD 功能边界

- [ ] **在 MVP 范围内**：当前修改是否属于 Phase 1 MVP 范围？
  - ✅ 聊天界面、主题切换、Claude SDK 集成
  - ❌ 多会话、Skills/Agents/MCP（Phase 2/3）
- [ ] **不在废弃方案内**：是否使用了已废弃的 HTTP + SSE 方案？
  - ❌ `streamMessage` 不应走 HTTP
  - ✅ 走 stdin/stdout + NDJSON

### 2.3 踩坑规避

- [ ] **PIT-001 规避**：JavaScript 注入是否在页面 `onLoadEnd` 之后？
- [ ] **PIT-003 规避**：Java → JS 事件是否通过 `window.dispatchEvent(CustomEvent)` + `window.addEventListener` 桥接？
- [ ] **PIT-005 规避**：所有 streaming 事件是否携带 `messageId`？

---

## 3. Bug 修复工作流

当发现并修复一个 bug 时，按以下步骤操作：

### Step 1：确认是否是新盲点

修复完成后，对照 `00-development-pitfalls-and-lessons.md` 中的 PIT-001~PIT-008，检查：

- 这个 bug 是否已有对应的 PIT 记录？
- 如果**是** → 检查现有 PIT 描述是否准确，无需新建
- 如果**否** → 这是**新盲点**，进入 Step 2

### Step 2：记录新盲点到踩坑文档

将新发现的盲点追加到 `00-development-pitfalls-and-lessons.md`，格式如下：

```markdown
## PIT-00X: [问题标题]

**现象**：[具体表现]

**根因**：[技术原因]

**解决方案**：
1. ...
2. ...

**关键位置**：`[文件名]:[行号范围]`

**规避检查清单**：
- [ ] ...
- [ ] ...

**状态**：resolved
```

### Step 3：更新相关 skill 文档

如果新盲点涉及特定模块（如 JCEF 生命周期、Java-JS 桥接），同步更新对应 skill 文档（`skills/01-` ~ `skills/07-`）。

### Step 4：验证修复

- [ ] `./gradlew build` 通过
- [ ] `./gradlew runIde` 沙箱测试通过（无启动崩溃）
- [ ] 相关功能的端到端测试通过

---

## 4. 代码修改规范

### 4.1 Java 后端（Kotlin）

**协程使用**：
```kotlin
// ✅ 正确：启动阶段使用 Dispatchers.Default
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// ✅ 正确：需要 UI 操作时切换
withContext(Dispatchers.EDT) { /* Swing 操作 */ }

// ❌ 错误：启动阶段直接使用 Dispatchers.Main
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
```

**NDJSON 消息格式**：
```kotlin
// ✅ 正确：构造 NDJSON 请求
val request = JsonObject().apply {
    addProperty("id", requestId)
    addProperty("method", "claude.send")
    add("params", params)
}
synchronized(daemonStdin) {
    daemonStdin.write(request.toString())
    daemonStdin.newLine()
    daemonStdin.flush()
}
```

**streaming 事件必须携带 messageId**：
```kotlin
// ✅ 正确：streaming 事件携带 messageId
sendToJavaScript("streaming:chunk",
    mapOf("messageId" to contextMessageId, "chunk" to line))

// ❌ 错误：缺少 messageId
sendToJavaScript("streaming:chunk",
    mapOf("chunk" to line))
```

### 4.2 前端（TypeScript/React）

**Vite 配置**：
```typescript
// webview/vite.config.ts
export default defineConfig({
  base: './',  // ✅ 必须是相对路径
  // ...
})
```

**Java → JS 事件桥接**（必须在 index.tsx 中建立）：
```typescript
// webview/src/main/index.tsx
const javaEvents = [
  'streaming:chunk',
  'streaming:complete',
  'streaming:error',
  'streaming:question',
  'response'
];

javaEvents.forEach((eventName) => {
  window.addEventListener(eventName, (e: Event) => {
    const customEvent = e as CustomEvent;
    eventBus.emit(eventName, customEvent.detail);
  });
});
```

**流式响应处理**：
```typescript
// ✅ 正确：使用 messageId 过滤
window.ccEvents.on('streaming:chunk', (data: any) => {
  if (data.messageId !== currentMessageId) return;
  appendChunk(data.chunk);
});
```

---

## 5. 新盲点追加模板

当发现并修复一个新 bug 后，将以下格式的记录追加到 `00-development-pitfalls-and-lessons.md` 末尾（PIT-009 起）：

```markdown
---

## PIT-00X: [简短标题]

**现象**：[页面白屏 / 消息丢失 / 崩溃等]

**根因**：[最核心的技术原因，一句话]

**解决方案**：

1. [具体步骤1]
2. [具体步骤2]

**关键位置**：`[文件路径]:[行号范围]`

**PRD/SDK 关联**：
- PRD-v3.1.md: [相关章节]
- Claude SDK Integration Guide: [相关章节]

**状态**：resolved
```

---

## 6. 快速参考

### 6.1 项目关键路径

| 文件 | 职责 |
|------|------|
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | JCEF 桥接、JS 注入、handle* 方法 |
| `src/main/kotlin/.../bridge/BridgeManager.kt` | Java 后端消息路由 |
| `src/main/kotlin/.../application/ContextManager.kt` | 上下文长度追踪 + /compact |
| `webview/src/lib/java-bridge.ts` | 前端 → Java 通信封装 |
| `webview/src/main/index.tsx` | Java → 前端事件桥接（CustomEvent） |
| `webview/src/shared/stores/streamingStore.ts` | 流式输出状态 |
| `design/docs/skills/01-jcef-page-lifecycle.md` | JCEF 生命周期管理 |

### 6.2 SDK 通信必须遵循的规则

1. **stdin 写入**：每行一个完整 JSON 对象（NDJSON），必须 `flush()`
2. **stdout 读取**：逐行解析，标签前不含空格
3. **heartbeat**：15 秒一次，45 秒无响应判定死亡
4. **请求 ID**：所有 SDK 输出都打上 `{"id":"X","line":"..."}` 标签
5. **stdout 拦截**：daemon.js 必须拦截 `process.stdout.write` 才能打标签
