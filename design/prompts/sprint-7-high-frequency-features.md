# Sprint 7 断点 Prompt：高频功能补全

> **角色**：你是一个同时精通 Kotlin（IntelliJ Platform SDK）和 TypeScript（React）的全栈开发者，擅长 UI 状态管理和跨语言通信调试。
> **目标**：补全 Skill/Agent 列表、历史搜索、多模态输入、打字机光标、PromptOptimizer 入口五个高频场景。
> **前置条件**：Sprint 6 完成，Markdown 高亮、会话切换联动、StopButton、新建会话 UI 已就绪。

---

## 项目背景

**当前状态（Sprint 6 交接）**：
- ✅ Markdown 代码块有语法高亮（highlight.js + marked）
- ✅ 代码块右上角有"Copy"按钮，点击复制成功
- ✅ 切换会话 Tab，消息列表正确刷新
- ✅ 新建会话"+"按钮可用，自动切换到新会话
- ✅ StopButton 点击后流式立即停止，显示"已中断"
- ❌ Skills/Agents 页面为空，未调用后端接口
- ❌ 历史会话搜索未实现
- ❌ 多模态输入（图片/文件拖拽粘贴）未实现
- ❌ 流式输出时无打字机闪烁光标
- ❌ PromptOptimizer 入口未实现

**v0.0.2 目标**：补全上述五个高频功能，提升日常使用体验。

---

## Task 7.1：Skill/Agent 列表展示（1 天）

### 任务目标
Skills 页面展示内置技能列表，支持启用/禁用切换；Agents 页面展示 Agent 列表，支持启停控制。

### 实现步骤

**Step 7.1.1：理解后端接口**
读取以下文件，确认 `getSkills()` / `getAgents()` 返回格式：
- `src/main/kotlin/.../browser/CefBrowserPanel.kt` — `handleGetSkills`、`handleGetAgents`
- `src/main/kotlin/.../application/ccbot/SkillsManager.kt` — 技能数据结构
- `src/main/kotlin/.../application/ccbot/AgentsManager.kt` — Agent 数据结构

**Step 7.1.2：创建 SkillsView 页面**
读取 `webview/src/main/pages/SkillsView.tsx`（懒加载页面）：
```tsx
import { useEffect, useState } from 'react';
import { useAppStore } from '@/shared/stores';

interface Skill {
  id: string;
  name: string;
  description: string;
  category: string;
  enabled: boolean;
}

export function SkillsView() {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    window.ccBackend?.getSkills().then((data: Skill[]) => {
      setSkills(data);
      setLoading(false);
    });
  }, []);

  const toggleSkill = async (skillId: string, enabled: boolean) => {
    await window.ccBackend?.saveSkill(skillId, enabled);
    setSkills(prev => prev.map(s => s.id === skillId ? { ...s, enabled } : s));
  };

  if (loading) return <div className="p-4">加载中...</div>;

  return (
    <div className="p-4 space-y-4">
      <h2 className="text-xl font-semibold">技能</h2>
      <div className="grid gap-4">
        {skills.map(skill => (
          <div key={skill.id} className="border rounded-lg p-4 flex items-center justify-between">
            <div>
              <div className="font-medium">{skill.name}</div>
              <div className="text-sm text-muted-foreground">{skill.description}</div>
              <div className="text-xs text-muted-foreground mt-1">分类: {skill.category}</div>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={skill.enabled}
                onChange={(e) => toggleSkill(skill.id, e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-accent peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
            </label>
          </div>
        ))}
      </div>
    </div>
  );
}
```

**Step 7.1.3：创建 AgentsView 页面**
读取 `webview/src/main/pages/AgentsView.tsx`：
```tsx
import { useEffect, useState } from 'react';

interface Agent {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  capability: string;
}

export function AgentsView() {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    window.ccBackend?.getAgents().then((data: Agent[]) => {
      setAgents(data);
      setLoading(false);
    });
  }, []);

  const toggleAgent = async (agentId: string, enabled: boolean) => {
    await window.ccBackend?.saveAgent(agentId, enabled);
    setAgents(prev => prev.map(a => a.id === agentId ? { ...a, enabled } : a));
  };

  const startAgent = async (agentId: string) => {
    await window.ccBackend?.startAgent(agentId);
  };

  const stopAgent = async (agentId: string) => {
    await window.ccBackend?.stopAgent(agentId);
  };

  if (loading) return <div className="p-4">加载中...</div>;

  return (
    <div className="p-4 space-y-4">
      <h2 className="text-xl font-semibold">Agent</h2>
      <div className="grid gap-4">
        {agents.map(agent => (
          <div key={agent.id} className="border rounded-lg p-4 flex items-center justify-between">
            <div>
              <div className="font-medium">{agent.name}</div>
              <div className="text-sm text-muted-foreground">{agent.description}</div>
              <div className="text-xs text-muted-foreground mt-1">能力: {agent.capability}</div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => startAgent(agent.id)}
                className="px-3 py-1 text-sm bg-primary text-primary-foreground rounded hover:opacity-90"
                disabled={!agent.enabled}
              >
                启动
              </button>
              <button
                onClick={() => stopAgent(agent.id)}
                className="px-3 py-1 text-sm border rounded hover:bg-accent"
              >
                停止
              </button>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={agent.enabled}
                  onChange={(e) => toggleAgent(agent.id, e.target.checked)}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-accent peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary"></div>
              </label>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

**Step 7.1.4：更新 router.tsx**
确认 SkillsView 和 AgentsView 通过懒加载引入（已确认在 router.tsx 中）。

### 验收标准（完成的定义）
- [ ] Skills 页面显示技能列表，每个技能有名称、描述、分类
- [ ] Skills 页面启用/禁用开关切换成功
- [ ] Agents 页面显示 Agent 列表，每个 Agent 有名称、描述、能力
- [ ] Agents 页面启用/禁用开关切换成功
- [ ] Agent 有"启动"/"停止"按钮
- [ ] `./gradlew build` 通过，无 ERROR 级别日志

### 依赖关系
- Task 6.2（会话切换联动）完成后可并行
- 后端 `getSkills` / `getAgents` / `saveSkill` / `saveAgent` / `startAgent` / `stopAgent` 已在 Sprint 5 实现

---

## Task 7.2：历史会话搜索（1 天）

### 任务目标
SessionHistoryView 添加搜索框，输入关键词过滤会话，点击搜索结果切换到对应会话。

### 实现步骤

**Step 7.2.1：理解后端接口**
读取 `CefBrowserPanel.handleSearchSessions()`，确认返回格式：
```kotlin
// 返回格式
sessions.map { session ->
    mapOf(
        "id" to session.id,
        "name" to session.name,
        "type" to session.type.name,
        "messageCount" to session.messages.size,
        "lastMessage" to session.messages.lastOrNull()?.content?.take(50),
        "createdAt" to session.createdAt,
        "updatedAt" to session.updatedAt
    )
}
```

**Step 7.2.2：更新 SessionHistoryView**
读取 `webview/src/main/pages/SessionHistoryView.tsx`：
```tsx
import { useState, useEffect } from 'react';
import { useAppStore } from '@/shared/stores';

interface SearchResult {
  id: string;
  name: string;
  type: string;
  messageCount: number;
  lastMessage: string;
  createdAt: number;
  updatedAt: number;
}

export function SessionHistoryView() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const switchSession = useAppStore((s) => s.switchSession);

  const handleSearch = async () => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    setSearching(true);
    try {
      const data = await window.ccBackend?.searchSessions(query);
      setResults(data || []);
    } finally {
      setSearching(false);
    }
  };

  const handleResultClick = (sessionId: string) => {
    switchSession(sessionId);
    // 跳转到聊天页面
    window.location.hash = '/';
  };

  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return d.toLocaleString('zh-CN');
  };

  return (
    <div className="p-4 space-y-4">
      <h2 className="text-xl font-semibold">历史会话</h2>

      {/* 搜索框 */}
      <div className="flex gap-2">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          placeholder="搜索会话..."
          className="flex-1 border rounded-md px-3 py-2 bg-background"
        />
        <button
          onClick={handleSearch}
          disabled={searching}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:opacity-90 disabled:opacity-50"
        >
          {searching ? '搜索中...' : '搜索'}
        </button>
      </div>

      {/* 搜索结果 */}
      {results.length > 0 && (
        <div className="space-y-2">
          <div className="text-sm text-muted-foreground">找到 {results.length} 个会话</div>
          {results.map(session => (
            <div
              key={session.id}
              onClick={() => handleResultClick(session.id)}
              className="border rounded-lg p-3 cursor-pointer hover:bg-accent transition-colors"
            >
              <div className="font-medium">{session.name}</div>
              <div className="text-sm text-muted-foreground mt-1">
                {session.lastMessage || '(空会话)'}
              </div>
              <div className="text-xs text-muted-foreground mt-1 flex justify-between">
                <span>{session.messageCount} 条消息</span>
                <span>{formatTime(session.updatedAt)}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {query && results.length === 0 && !searching && (
        <div className="text-muted-foreground text-center py-8">未找到匹配的会话</div>
      )}
    </div>
  );
}
```

**Step 7.2.3：后端搜索逻辑确认**
读取 `SessionManager.searchSessions()`，确认按会话名称和消息内容模糊搜索。

### 验收标准（完成的定义）
- [ ] 输入关键词，能找到包含该词的会话名称或消息内容
- [ ] 搜索结果显示会话名称、最后一条消息摘要、消息数量、更新时间
- [ ] 点击搜索结果，自动切换到对应会话
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 6.2（会话切换联动）完成后可并行
- 后端 `searchSessions` 已在 Sprint 5 实现

---

## Task 7.3：多模态输入（1 天）

### 任务目标
ChatInput 支持拖拽图片/文件粘贴，显示附件预览，发送时携带附件数据。

### 实现步骤

**Step 7.3.1：理解 ChatInput 当前实现**
读取 `webview/src/main/components/ChatInput.tsx`，了解当前输入组件结构。

**Step 7.3.2：安装图片压缩依赖**
```bash
cd webview
npm install browser-image-compression
npm install -D @types/browser-image-compression
```

**Step 7.3.3：更新 ChatInput 添加附件状态**
```tsx
import { useState, useCallback, useRef } from 'react';
import { useAppStore } from '@/shared/stores';
import { useStreamingStore } from '@/shared/stores';

interface Attachment {
  id: string;
  type: 'image' | 'file';
  name: string;
  size: number;
  data: string; // Base64
  preview?: string; // URL for preview
}

export function ChatInput() {
  const [message, setMessage] = useState('');
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 压缩图片并转为 Base64
  const processImage = async (file: File): Promise<string> => {
    const压缩 = await import('browser-image-compression');
    const compressed = await压缩.default(file, { maxSizeMB: 1, maxWidthOrHeight: 1920 });
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(compressed);
    });
  };

  // 读取文件内容
  const processFile = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsText(file);
    });
  };

  // 处理拖拽/粘贴的文件
  const handleFiles = async (files: FileList) => {
    const newAttachments: Attachment[] = [];

    for (const file of Array.from(files)) {
      const isImage = file.type.startsWith('image/');
      const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`;

      if (isImage) {
        const data = await processImage(file);
        newAttachments.push({
          id,
          type: 'image',
          name: file.name,
          size: file.size,
          data,
          preview: data
        });
      } else {
        const data = await processFile(file);
        newAttachments.push({
          id,
          type: 'file',
          name: file.name,
          size: file.size,
          data
        });
      }
    }

    setAttachments(prev => [...prev, ...newAttachments]);
  };

  // 拖拽事件
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files.length > 0) {
      handleFiles(e.dataTransfer.files);
    }
  }, []);

  // 粘贴事件
  const handlePaste = useCallback((e: React.ClipboardEvent) => {
    if (e.clipboardData.files.length > 0) {
      handleFiles(e.clipboardData.files);
    }
  }, []);

  // 删除附件
  const removeAttachment = (id: string) => {
    setAttachments(prev => prev.filter(a => a.id !== id));
  };

  // 发送消息
  const sendMessage = async () => {
    if (!message.trim() && attachments.length === 0) return;
    const submitMessage = useAppStore.getState().submitMessage;
    await submitMessage(message, attachments);
    setMessage('');
    setAttachments([]);
  };

  return (
    <div
      className={`border-t p-4 transition-colors ${isDragging ? 'bg-accent' : ''}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* 附件预览区 */}
      {attachments.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-3">
          {attachments.map(att => (
            <div key={att.id} className="relative group">
              {att.type === 'image' ? (
                <img
                  src={att.preview}
                  alt={att.name}
                  className="w-16 h-16 object-cover rounded border"
                />
              ) : (
                <div className="w-16 h-16 flex items-center justify-center border rounded bg-accent">
                  <span className="text-xs text-center px-1 truncate">{att.name}</span>
                </div>
              )}
              <button
                onClick={() => removeAttachment(att.id)}
                className="absolute -top-2 -right-2 w-5 h-5 bg-destructive text-destructive-foreground rounded-full text-xs opacity-0 group-hover:opacity-100 transition-opacity"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {/* 拖拽提示 */}
      {isDragging && (
        <div className="text-center py-4 border-2 border-dashed border-primary rounded mb-3 text-muted-foreground">
          拖拽图片或文件到这里
        </div>
      )}

      {/* 输入区 */}
      <div className="flex gap-2 items-end">
        <textarea
          ref={textareaRef}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              sendMessage();
            }
          }}
          onPaste={handlePaste}
          placeholder="输入消息... (Ctrl+V 粘贴图片/文件)"
          className="flex-1 border rounded-md px-3 py-2 resize-none bg-background"
          rows={1}
          data-chat-input
        />
        <button
          onClick={sendMessage}
          disabled={!message.trim() && attachments.length === 0}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:opacity-90 disabled:opacity-50"
        >
          发送
        </button>
      </div>

      <div className="text-xs text-muted-foreground mt-2">
        支持拖拽或 Ctrl+V 粘贴图片/文件
      </div>
    </div>
  );
}
```

**Step 7.3.4：更新 appStore.submitMessage 支持附件**
读取 `appStore.ts`，确保 `submitMessage` 签名支持附件：
```ts
submitMessage: async (content: string, attachments?: Attachment[]) => {
  // ...
  // 传递 attachments 到 javaBridge.sendMessage 或 sendMultimodalMessage
}
```

**Step 7.3.5：后端 sendMultimodalMessage 确认**
读取 `CefBrowserPanel.handleSendMultimodalMessage()`，确认后端正确处理 Base64 数据。

### 验收标准（完成的定义）
- [ ] 拖拽图片到输入框，显示预览缩略图
- [ ] Ctrl+V 粘贴文件，内容被读取
- [ ] 附件可点击"×"删除
- [ ] 发送消息后附件和内容一起发送
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 6.4（新建会话 UI）完成后可并行
- Task 7.1（Skill/Agent）完成后可并行

---

## Task 7.4：流式打字机光标（0.5 天）

### 任务目标
StreamingMessage 流式输出时显示闪烁光标 `|`，流式结束时光标消失，中断时变红。

### 实现步骤

**Step 7.4.1：读取 MarkdownRenderer 和 StreamingMessage**
读取：
- `webview/src/shared/components/MarkdownRenderer.tsx`
- `webview/src/shared/components/StreamingMessage.tsx`

**Step 7.4.2：在 StreamingMessage 添加光标状态**
确认 `streamingStore` 有 `isStreaming` 状态。

在 `StreamingMessage.tsx` 中，当 `isStreaming=true` 时，在内容末尾显示闪烁光标：
```tsx
import { useStreamingStore } from '@/shared/stores';

// StreamingMessage 组件内
const isStreaming = useStreamingStore((s) => s.isStreaming);

// 在 MarkdownRenderer 或内容末尾添加
{
  isStreaming && (
    <span className="streaming-cursor">|</span>
  )
}
```

**Step 7.4.3：添加 CSS 动画**
在 `globals.css` 添加：
```css
.streaming-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: primary;
  font-weight: bold;
}

@keyframes blink {
  50% { opacity: 0; }
}
```

**Step 7.4.4：中断状态光标变红**
当 `status === 'interrupted'` 时：
```tsx
<span
  className="streaming-cursor"
  style={{ color: 'var(--destructive)' }}
>
  |
</span>
```

**Step 7.4.5：流式结束时隐藏光标**
确认 `streaming:complete` 事件触发时 `isStreaming=false`。

### 验收标准（完成的定义）
- [ ] AI 回复逐字输出时，光标跟随在文字末尾
- [ ] 光标有 1s 闪烁动画（0.5s 显示，0.5s 隐藏）
- [ ] 回复完成后光标立即消失
- [ ] 中断时光标变红色
- [ ] `npx tsc --noEmit` 无类型错误

### 依赖关系
- Task 6.1（MarkdownRenderer）完成后可并行
- Task 7.3（多模态输入）完成后可并行

---

## Task 7.5：PromptOptimizer 入口（0.5 天）

### 任务目标
ChatInput 工具栏添加"✨优化"按钮，点击后调用后端 PromptOptimizer 优化输入框内容。

### 实现步骤

**Step 7.5.1：理解后端 PromptOptimizer**
读取 `src/main/kotlin/.../application/ccbot/PromptOptimizer.kt`（如果存在）或确认后端 `optimizePrompt` action。

**Step 7.5.2：在 ChatInput 添加优化按钮**
读取当前 `ChatInput.tsx`，在发送按钮旁添加优化按钮：
```tsx
const [optimizing, setOptimizing] = useState(false);

const handleOptimize = async () => {
  if (!message.trim() || optimizing) return;
  setOptimizing(true);
  try {
    const result = await window.ccBackend?.optimizePrompt?.(message);
    if (result) {
      setMessage(result);
    }
  } finally {
    setOptimizing(false);
  }
};

// 在工具栏添加按钮
<button
  onClick={handleOptimize}
  disabled={!message.trim() || optimizing}
  className="px-3 py-2 text-sm border rounded-md hover:bg-accent disabled:opacity-50"
  title="优化提示词"
>
  ✨ 优化
</button>
```

**Step 7.5.3：确认前端 javaBridge 有 optimizePrompt**
读取 `webview/src/shared/utils/javaBridge.ts`，确认 `optimizePrompt` 方法存在：
```ts
optimizePrompt?: (content: string) => Promise<string>;
```

### 验收标准（完成的定义）
- [ ] ChatInput 工具栏有"✨优化"按钮
- [ ] 输入提示词后点击"优化"，内容变为优化后的版本
- [ ] 优化中按钮显示 loading 状态
- [ ] 优化失败时显示错误提示（Toast）
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 7.3（多模态输入）完成后可并行

---

## 需要读取的文件（按优先级排序）

### 前端（TypeScript/React）

| 文件 | 关注点 |
|------|--------|
| `webview/src/main/pages/SkillsView.tsx` | 技能列表页面（当前为懒加载桩） |
| `webview/src/main/pages/AgentsView.tsx` | Agent 列表页面（当前为懒加载桩） |
| `webview/src/main/pages/SessionHistoryView.tsx` | 历史会话搜索 |
| `webview/src/main/components/ChatInput.tsx` | 多模态输入、PromptOptimizer 按钮 |
| `webview/src/shared/components/StreamingMessage.tsx` | 打字机光标 |
| `webview/src/shared/utils/javaBridge.ts` | optimizePrompt 方法定义 |
| `webview/src/styles/globals.css` | 光标闪烁动画样式 |

### 后端（Kotlin）

| 文件 | 关注点 |
|------|--------|
| `src/main/kotlin/.../application/ccbot/SkillsManager.kt` | 技能数据结构 |
| `src/main/kotlin/.../application/ccbot/AgentsManager.kt` | Agent 数据结构 |
| `src/main/kotlin/.../application/ccbot/PromptOptimizer.kt` | Prompt 优化逻辑（如果存在） |
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | handleGetSkills、handleGetAgents、handleSearchSessions |

---

## Sprint 7 验收标准

完成 Task 7.1-7.5 后，确认以下全部通过：

- [ ] Skills 页面显示技能列表，启用/禁用切换成功
- [ ] Agents 页面显示 Agent 列表，启停控制正常
- [ ] 历史会话搜索能按关键词过滤，点击跳转到对应会话
- [ ] ChatInput 支持拖拽/粘贴图片和文件，显示预览
- [ ] 流式输出时有闪烁光标，完成后光标消失
- [ ] ChatInput 有"✨优化"按钮，点击后内容被优化
- [ ] `./gradlew build` 通过
- [ ] `cd webview && npx tsc --noEmit` 无类型错误

---

## 交接给下一位开发者

> "Sprint 7 完成。高频功能已补全。以下是各任务的实际执行情况：
>
> - Task 7.1（Skill/Agent）：SkillsView 和 AgentsView 已实现，启用/禁用、启停控制已连接后端
> - Task 7.2（历史搜索）：SessionHistoryView 添加搜索框，支持按会话名和消息内容搜索
> - Task 7.3（多模态）：ChatInput 支持拖拽/粘贴图片和文件，附件预览已实现
> - Task 7.4（打字机光标）：StreamingMessage 添加闪烁光标，中断时变红
> - Task 7.5（PromptOptimizer）：ChatInput 添加"✨优化"按钮，调用后端优化输入内容
>
> 请进入 Sprint 8：交互增强。"
>
> **必须记录**：
> 1. highlight.js 的具体 theme 名称（如 `github-dark`）
> 2. 图片压缩的具体配置（maxSizeMB, maxWidthOrHeight）
> 3. 光标闪烁动画的具体 CSS 实现
