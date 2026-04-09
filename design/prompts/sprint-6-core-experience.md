# Sprint 6 断点 Prompt：核心体验修复

> **角色**：你是一个同时精通 Kotlin（IntelliJ Platform SDK）和 TypeScript（React）的全栈开发者，擅长 UI 状态管理和跨语言通信调试。
> **目标**：修复四个 P0 核心体验问题，让插件达到"可日常使用"的质量标准。
> **前置条件**：Sprint 5 完成，消息发送和流式接收链路已跑通，但 Markdown 无高亮、会话切换失效、停止按钮无法工作。

---

## 项目背景

**当前状态（Sprint 5 交接）**：
- ✅ `javaBridge.sendMessage()` → Claude CLI → 流式输出
- ✅ `streaming:chunk` / `streaming:complete` / `streaming:error` 三事件就绪
- ❌ 代码块无语法高亮，显示为纯文本
- ❌ 点击 SessionTab 后消息列表不刷新
- ❌ StopButton 点击后流式继续，无法中断
- ❌ 无新建会话 UI 入口

**v0.0.2 目标**：修掉上述四个 P0 问题，达成可日常使用。

---

## Task 6.1：Markdown 代码高亮（0.5 天）

### 任务目标
发送包含代码的消息时，代码块有语法高亮和复制按钮，而非纯文本。

### 实现步骤

**Step 6.1.1：安装依赖**
```bash
cd webview
npm install marked highlight.js
npm install -D @types/marked @types/highlight.js
```

**Step 6.1.2：创建 MarkdownRenderer 组件**
创建 `webview/src/shared/components/MarkdownRenderer.tsx`：
```tsx
import { marked } from 'marked';
import hljs from 'highlight.js';
import 'highlight.js/styles/github-dark.css';

// 配置 marked
marked.setOptions({
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value;
    }
    return hljs.highlightAuto(code).value;
  },
  breaks: true,
});

export function MarkdownRenderer({ content }: { content: string }) {
  const html = marked.parse(content) as string;
  return (
    <div
      className="markdown-content"
      dangerouslySetInnerHTML={{ __html: html }}
      onClick={(e) => {
        // 代码块复制按钮
        const target = e.target as HTMLElement;
        if (target.classList.contains('copy-btn')) {
          const code = target.parentElement?.querySelector('code')?.textContent;
          navigator.clipboard.writeText(code || '');
          target.textContent = '✓';
          setTimeout(() => { target.textContent = 'Copy'; }, 1500);
        }
      }}
    />
  );
}
```

**Step 6.1.3：注入代码块复制按钮样式**
在 `globals.css` 末尾添加：
```css
.markdown-content pre {
  position: relative;
  padding: 1rem;
  border-radius: 8px;
  overflow: hidden;
}
.markdown-content pre .copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 4px 8px;
  font-size: 12px;
  background: rgba(255,255,255,0.1);
  border: none;
  border-radius: 4px;
  color: #ccc;
  cursor: pointer;
}
.markdown-content pre .copy-btn:hover {
  background: rgba(255,255,255,0.2);
}
.markdown-content code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
```

**Step 6.1.4：StreamingMessage 替换为 MarkdownRenderer**
读取 `StreamingMessage.tsx`，将 `content` 的纯文本 div 替换为 `<MarkdownRenderer content={content} />`。

### 验收标准（完成的定义）
- [ ] 发送"请用 Python 写一个快排算法"，AI 回复中的代码块有语法高亮
- [ ] 代码块右上角有"Copy"按钮，点击后内容复制到剪贴板
- [ ] `npm run dev` 独立运行前端也能看到高亮效果
- [ ] `npx tsc --noEmit` 无类型错误

### 依赖关系
- 无前置依赖
- Task 7.4（打字机光标）依赖 MarkdownRenderer 已就绪

---

## Task 6.2：会话切换数据联动（0.5 天）

### 任务目标
点击 SessionTab → 对应会话的消息正确显示；切换标签页后 MessageList 刷新。

### 实现步骤

**Step 6.2.1：理解当前数据流**
读取以下文件，理解当前会话数据的存储和读取方式：
- `webview/src/shared/stores/appStore.ts` — sessions 列表和 currentSessionId
- `webview/src/shared/stores/sessionStore.ts` — sessionStates（当前是否还有？）
- `webview/src/main/components/SessionTabs.tsx` — 标签栏组件
- `webview/src/main/pages/ChatView.tsx` — 消息列表读取位置

**关键问题**：当前 `sessionStore` 有独立的 `messages`，而 `appStore.sessions` 也有 messages。两份数据不同步。

**修复方案**：统一以 `appStore.sessions` 为单一数据源。删除 `sessionStore.ts` 中的重复 messages 逻辑。

**Step 6.2.2：统一数据源**
在 `appStore.ts` 中：
```ts
// 确保 ChatSession 类型包含 messages
interface ChatSession {
  id: string;
  name: string;
  type: SessionType;
  messages: ChatMessage[];  // 消息直接挂在 session 上
  createdAt: number;
  updatedAt: number;
}

// switchSession: 直接更新 currentSessionId
switchSession: (sessionId: string) => {
  set({ currentSessionId: sessionId });
  // 不再调用 window.ccBackend（后端已通过 submitAnswer 处理）
},

// getCurrentMessages: 便捷方法
getCurrentMessages: () => {
  const { sessions, currentSessionId } = get();
  return sessions.find(s => s.id === currentSessionId)?.messages ?? [];
}
```

**Step 6.2.3：MessageList 读取 appStore**
读取 `MessageList.tsx`，确保它读取的是：
```ts
const messages = useAppStore((s) => {
  const session = s.sessions.find(sess => sess.id === s.currentSessionId);
  return session?.messages ?? [];
});
```

**Step 6.2.4：SessionTabs 点击切换**
读取 `SessionTabs.tsx`，确保点击标签时：
```ts
const handleTabClick = (sessionId: string) => {
  useAppStore.getState().switchSession(sessionId);
};
```

**Step 6.2.5：后端 searchSessions 补充 messageCount**
读取 `CefBrowserPanel.handleSearchSessions()`，确认返回数据包含 messageCount：
```kotlin
return sessions.map { session ->
    mapOf(
        "id" to session.id,
        "name" to session.name,
        "type" to session.type.name,
        "messageCount" to session.messages.size,  // 前端标签栏显示消息数
        "lastMessage" to session.messages.lastOrNull()?.content?.take(50),
        "createdAt" to session.createdAt,
        "updatedAt" to session.updatedAt
    )
}
```

### 验收标准（完成的定义）
- [ ] 创建两个会话，会话 A 发"你好"，会话 B 发"Hi"
- [ ] 点击会话 A 标签，MessageList 显示"你好"，点击会话 B 标签显示"Hi"
- [ ] SessionTabs 上显示各会话名称和最后一条消息摘要
- [ ] `./gradlew build` 通过，无 ERROR 级别日志

### 依赖关系
- Task 6.1（MarkdownRenderer）完成后可并行
- 无其他 Sprint 6 任务依赖此任务

---

## Task 6.3：StopButton 连接 streamingStore（0.5 天）

### 任务目标
点击停止按钮 → 前端停止接收流式数据 + 后端 cancelStreaming 被调用。

### 实现步骤

**Step 6.3.1：理解当前 StopButton 状态**
读取：
- `webview/src/features/streaming/components/StopButton.tsx`
- `webview/src/shared/stores/streamingStore.ts`
- `webview/src/main/pages/ChatView.tsx` — StopButton 使用位置

**Step 6.3.2：在 streamingStore 添加 cancelStreaming 方法**
读取 `streamingStore.ts`，确保有：
```ts
cancelStreaming: () => {
  const { streamingMessageId } = get();
  if (streamingMessageId) {
    window.ccBackend?.cancelStreaming(streamingMessageId);
  }
  set({ isStreaming: false, streamingMessageId: null });
},
```

**Step 6.3.3：StopButton 点击处理**
读取并修改 `StopButton.tsx`：
```tsx
const handleStop = () => {
  useStreamingStore.getState().cancelStreaming();
};
// 或者直接：
<button onClick={() => useStreamingStore.getState().cancelStreaming()}>
  停止
</button>
```

**Step 6.3.4：验证后端 cancelStreaming 被调用**
读取 `CefBrowserPanel.handleCancelStreaming()`：
```kotlin
private fun handleCancelStreaming(queryId: Int, params: com.google.gson.JsonElement?): Any? {
    val sessionId = params?.asJsonObject?.get("sessionId")?.asString ?: ""
    bridgeManager.cancelStreaming(sessionId)
    return null
}
```

确认 `BridgeManager.cancelStreaming()` 最终调用了 `claudeClient.cancelCurrentRequest()` 并设置 `_state.value = ClientState.IDLE`。

**Step 6.3.5：StreamingMessage 中断状态**
修改 `StreamingMessage.tsx`，在 `isStreaming=false` 时显示"已中断"：
```tsx
{status === 'interrupted' && (
  <span className="text-muted-foreground text-sm">[已中断]</span>
)}
```

### 验收标准（完成的定义）
- [ ] 发送一条长消息让 AI 生成中，点击"停止"按钮
- [ ] AI 生成立即停止，Message 显示"[已中断]"状态
- [ ] StopButton 在非流式状态时隐藏或禁用
- [ ] 后端 cancelStreaming 被调用（IDEA console 有日志）
- [ ] 再次发送消息正常，不受影响

### 依赖关系
- Task 6.1（MarkdownRenderer）完成后可并行
- 此任务修复的是 streamingStore 与 StopButton 之间的连接，不依赖其他任务

---

## Task 6.4：新建会话 UI（0.5 天）

### 任务目标
点击"+"按钮 → 创建新会话 → 自动切换到新会话 → 输入框聚焦。

### 实现步骤

**Step 6.4.1：在 SessionTabs 添加新建按钮**
读取 `SessionTabs.tsx`，在标签栏最右侧添加"+"按钮：
```tsx
<button
  className="flex items-center justify-center w-8 h-8 rounded hover:bg-accent"
  onClick={handleNewSession}
  title="新建会话"
>
  +
</button>
```

**Step 6.4.2：实现新建会话逻辑**
```tsx
const handleNewSession = async () => {
  const session = await javaBridge.createSession(
    `会话 ${sessions.length + 1}`,
    'PROJECT'
  );
  if (session?.id) {
    useAppStore.getState().switchSession(session.id);
    // 聚焦到输入框
    document.querySelector<HTMLTextAreaElement>('[data-chat-input]')?.focus();
  }
};
```

**Step 6.4.3：后端 createSession 返回完整数据**
读取 `CefBrowserPanel.handleCreateSession()`，确认返回格式与前端期望一致：
```kotlin
return mapOf(
    "id" to session.id,
    "name" to session.name,
    "type" to session.type.name,
    "messageCount" to 0
)
```

**Step 6.4.4：确保前端 appStore.createSession 也同步更新**
读取并修改 `appStore.ts` 的 `createSession` 方法：
```ts
createSession: async (name: string, type: SessionType) => {
  const session = await javaBridge.createSession(name, type);
  if (session) {
    set(state => ({
      sessions: [...state.sessions, session],
      currentSessionId: session.id  // 自动切换
    }));
  }
  return session;
}
```

### 验收标准（完成的定义）
- [ ] 点击"+"按钮后，SessionTabs 出现新标签，自动切换到新会话
- [ ] 输入框自动获得焦点（可以立即打字）
- [ ] 新会话的 messageCount 为 0
- [ ] 创建 3 个会话后，切换各会话，消息互相隔离
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 6.2（会话切换联动）完成后可并行
- 此任务的 UI 在 SessionTabs 中，与其他任务无强依赖

---

## 需要读取的文件（按优先级排序）

### 前端（TypeScript/React）

| 文件 | 关注点 |
|------|--------|
| `webview/src/shared/components/StreamingMessage.tsx` | 替换为 MarkdownRenderer |
| `webview/src/shared/stores/appStore.ts` | switchSession、createSession、sessions 数据 |
| `webview/src/shared/stores/streamingStore.ts` | cancelStreaming 方法 |
| `webview/src/main/components/SessionTabs.tsx` | 标签栏 UI、点击处理 |
| `webview/src/main/pages/ChatView.tsx` | StopButton 使用位置 |
| `webview/src/features/streaming/components/StopButton.tsx` | 停止按钮当前实现 |
| `webview/src/main/components/MessageList.tsx` | 消息列表数据读取 |
| `webview/src/shared/stores/sessionStore.ts` | 检查是否有重复 messages（待删除） |
| `webview/src/styles/globals.css` | 添加复制按钮样式 |

### 后端（Kotlin）

| 文件 | 关注点 |
|------|--------|
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | handleSearchSessions、handleCreateSession、handleCancelStreaming |
| `src/main/kotlin/.../bridge/BridgeManager.kt` | cancelStreaming 实现 |
| `src/main/kotlin/.../adaptation/sdk/ClaudeCodeClient.kt` | cancelCurrentRequest |

---

## Sprint 6 验收标准

完成 Task 6.1-6.4 后，确认以下全部通过：

- [ ] Markdown 代码块有语法高亮（highlight.js）
- [ ] 代码块有"Copy"按钮，点击复制成功
- [ ] 切换会话 Tab，消息列表正确刷新
- [ ] 新建会话"+"按钮可用，自动切换到新会话
- [ ] StopButton 点击后流式立即停止，显示"已中断"
- [ ] 流式停止后再次发送消息正常
- [ ] `./gradlew build` 通过
- [ ] `cd webview && npx tsc --noEmit` 无类型错误

---

## 交接给下一位开发者

> "Sprint 6 完成。核心体验问题已修复。以下是各任务的实际执行情况：
>
> - Task 6.1（Markdown）：安装了 marked + highlight.js，创建了 MarkdownRenderer 组件，代码块有高亮和复制按钮
> - Task 6.2（会话切换）：统一了 appStore.sessions 为单一数据源，SessionTabs 正确联动
> - Task 6.3（StopButton）：streamingStore.cancelStreaming() 已连接 StopButton，后端 cancelStreaming 被调用
> - Task 6.4（新建会话）：SessionTabs 添加了 '+' 按钮，创建会话后自动切换并聚焦输入框
>
> 请进入 Sprint 7：高频功能补全。"
>
> **必须记录**：
> 1. MarkdownRenderer 中 highlight.js 的具体配置（如 theme 名称）
> 2. 数据源统一方案的具体变更（哪些字段从 sessionStore 移除了）
> 3. StopButton 的最终连接方式
> 4. 新建会话的快捷键（如果有）
