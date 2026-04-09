# Sprint 8 断点 Prompt：交互增强

> **角色**：你是一个同时精通 Kotlin（IntelliJ Platform SDK）和 TypeScript（React）的全栈开发者，擅长 IDE 交互设计和跨语言通信调试。
> **目标**：完成交互式请求 UI、会话导入导出、代码快捷操作、PreviewPanel 基础布局，达成 v0.0.2 可发布状态。
> **前置条件**：Sprint 7 完成，所有高频功能已就绪，仅缺交互增强层。

---

## 项目背景

**当前状态（Sprint 7 交接）**：
- ✅ Skills/Agents 页面列表展示，启用/禁用切换正常
- ✅ 历史会话搜索按关键词过滤，点击跳转
- ✅ ChatInput 支持拖拽/粘贴图片和文件，附件预览
- ✅ 流式输出有闪烁光标，完成后消失
- ✅ ChatInput 有"✨优化"按钮
- ❌ AI 提问时无交互式确认面板（streaming:question 未实现）
- ❌ 会话无法导入/导出
- ❌ 编辑器中无"解释代码"等快捷操作
- ❌ 无 PreviewPanel 详情面板

**v0.0.2 目标**：完成交互增强模块，具备发布质量。

---

## Task 8.1：交互式请求 UI（1.5 天）

### 任务目标
AI 请求用户确认或选择时（如"Yes/No"确认、选项列表），显示 InteractiveQuestionPanel，用户回答后 AI 继续。

### 实现步骤

**Step 8.1.1：理解后端事件推送机制**
读取以下文件，理解 `streaming:question` 事件如何从后端推送：
- `src/main/kotlin/.../browser/CefBrowserPanel.kt` — `streaming:question` 推送位置
- `src/main/kotlin/.../infrastructure/eventbus/Events.kt` — `InteractiveQuestionEvent` 结构
- `src/main/kotlin/.../application/interaction/InteractiveRequestEngine.kt` — 问问题的 coroutine 流程

**关键发现**：Sprint 5 发现 `InteractiveQuestionEvent` 已定义，但无前端订阅者。

**Step 8.1.2：确认 questionStore 存在**
读取 `webview/src/shared/stores/questionStore.ts`。

如果不存在，创建：
```ts
import { create } from 'zustand';

interface Question {
  id: string;
  type: 'confirmation' | 'selection' | 'input';
  message: string;
  options?: string[];
  requiredCapability?: string;
}

interface QuestionStore {
  currentQuestion: Question | null;
  setCurrentQuestion: (q: Question | null) => void;
  submitAnswer: (questionId: string, answer: string) => Promise<void>;
}

export const useQuestionStore = create<QuestionStore>((set, get) => ({
  currentQuestion: null,

  setCurrentQuestion: (q) => set({ currentQuestion: q }),

  submitAnswer: async (questionId, answer) => {
    await window.ccBackend?.submitAnswer(questionId, answer);
    set({ currentQuestion: null });
  }
}));
```

**Step 8.1.3：前端订阅 streaming:question 事件**
读取 `webview/src/main/App.tsx`，在 `useEffect` 中添加事件监听：
```tsx
useEffect(() => {
  // streaming:question 事件监听
  const handleQuestion = (event: CustomEvent) => {
    const data = event.detail;
    useQuestionStore.getState().setCurrentQuestion({
      id: data.questionId,
      type: data.questionType, // 'confirmation' | 'selection' | 'input'
      message: data.message,
      options: data.options,
      requiredCapability: data.requiredCapability
    });
  };

  window.addEventListener('streaming:question', handleQuestion as EventListener);
  return () => {
    window.removeEventListener('streaming:question', handleQuestion as EventListener);
  };
}, []);
```

**Step 8.1.4：创建 InteractiveQuestionPanel 组件**
创建 `webview/src/features/interaction/components/InteractiveQuestionPanel.tsx`：
```tsx
import { useQuestionStore } from '@/shared/stores';

export function InteractiveQuestionPanel() {
  const { currentQuestion, submitAnswer } = useQuestionStore();
  const [inputValue, setInputValue] = useState('');

  if (!currentQuestion) return null;

  const handleConfirm = (answer: string) => {
    submitAnswer(currentQuestion.id, answer);
    setInputValue('');
  };

  return (
    <div className="border-t p-4 bg-accent/50">
      <div className="text-sm font-medium mb-3">AI 提问</div>
      <div className="text-sm mb-4">{currentQuestion.message}</div>

      {currentQuestion.type === 'confirmation' && (
        <div className="flex gap-2">
          <button
            onClick={() => handleConfirm('yes')}
            className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:opacity-90"
          >
            是
          </button>
          <button
            onClick={() => handleConfirm('no')}
            className="px-4 py-2 border rounded-md hover:bg-accent"
          >
            否
          </button>
        </div>
      )}

      {currentQuestion.type === 'selection' && currentQuestion.options && (
        <div className="flex flex-wrap gap-2">
          {currentQuestion.options.map((option, idx) => (
            <button
              key={idx}
              onClick={() => handleConfirm(option)}
              className="px-4 py-2 border rounded-md hover:bg-accent"
            >
              {option}
            </button>
          ))}
        </div>
      )}

      {currentQuestion.type === 'input' && (
        <div className="flex gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleConfirm(inputValue)}
            className="flex-1 border rounded-md px-3 py-2 bg-background"
            placeholder="输入你的回答..."
          />
          <button
            onClick={() => handleConfirm(inputValue)}
            className="px-4 py-2 bg-primary text-primary-foreground rounded-md hover:opacity-90"
          >
            发送
          </button>
        </div>
      )}
    </div>
  );
}
```

**Step 8.1.5：在 ChatView 中集成 InteractiveQuestionPanel**
读取 `webview/src/main/pages/ChatView.tsx`，在合适位置添加：
```tsx
import { InteractiveQuestionPanel } from '@/features/interaction/components/InteractiveQuestionPanel';

// 在 MessageList 或 ChatInput 下方添加
<InteractiveQuestionPanel />
```

**Step 8.1.6：后端推送 streaming:question 事件**
读取 `CefBrowserPanel.sendToJavaScript()`，确认支持推送 `streaming:question` 事件类型。

如果 `streaming:question` 事件格式未定义，使用：
```kotlin
// CefBrowserPanel 中
private fun handleAskQuestion(params: JsonElement?): Any? {
    val questionId = params?.asJsonObject?.get("questionId")?.asString ?: return null
    val questionType = params.asJsonObject.get("type")?.asString ?: "confirmation"
    val message = params.asJsonObject.get("message")?.asString ?: ""
    val options = params.asJsonObject.get("options")?.asJsonArray?.map { it.asString }

    sendToJavaScript(mapOf(
        "type" to "streaming:question",
        "questionId" to questionId,
        "questionType" to questionType,
        "message" to message,
        "options" to options
    ))
    return null
}
```

**Step 8.1.7：handleSubmitAnswer 确认**
读取 `CefBrowserPanel.handleSubmitAnswer()`，确认提交答案后继续流式输出。

### 验收标准（完成的定义）
- [ ] AI 请求确认时（如"是否继续？"），显示确认面板
- [ ] AI 请求选择时，显示选项列表
- [ ] 用户回答后，AI 继续流式输出
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 7.4（打字机光标）完成后可并行
- 后端 `InteractiveRequestEngine` 已在 Sprint 5 就绪

---

## Task 8.2：会话导入/导出（1 天）

### 任务目标
导出会话为 JSON/Markdown 文件，导入 JSON/Markdown 文件创建新会话。

### 实现步骤

**Step 8.2.1：在 SessionTabs 添加导出按钮**
读取 `webview/src/features/session/components/SessionTabs.tsx`，在标签栏添加导出菜单：
```tsx
<button
  onClick={handleExport}
  className="flex items-center justify-center w-8 h-8 rounded hover:bg-accent"
  title="导出会话"
>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
    <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
  </svg>
</button>
```

**Step 8.2.2：实现导出会话逻辑**
```tsx
const handleExport = async () => {
  const session = useAppStore.getState().sessions.find(
    s => s.id === useAppStore.getState().currentSessionId
  );
  if (!session) return;

  const exportData = {
    version: '1.0',
    session: {
      id: session.id,
      name: session.name,
      type: session.type,
      messages: session.messages,
      createdAt: session.createdAt,
      updatedAt: session.updatedAt
    }
  };

  const jsonStr = JSON.stringify(exportData, null, 2);
  const blob = new Blob([jsonStr], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  const a = document.createElement('a');
  a.href = url;
  a.download = `${session.name}-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
};
```

**Step 8.2.3：创建导入按钮和文件选择器**
在 SessionTabs 或设置页面添加导入按钮：
```tsx
const handleImport = () => {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.json,.md';
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;

    const text = await file.text();
    try {
      const data = JSON.parse(text);
      if (data.session) {
        const session = await useAppStore.getState().createSession(
          data.session.name,
          data.session.type
        );
        // 导入消息...
        // 调用 javaBridge.importSession(data.session)
      }
    } catch {
      // 尝试 Markdown 格式
    }
  };
  input.click();
};
```

**Step 8.2.4：后端 exportSession / importSession 确认**
读取 `CefBrowserPanel.handleExportSession()` 和 `handleImportSession()`。

### 验收标准（完成的定义）
- [ ] 点击"导出"按钮，下载当前会话的 JSON 文件
- [ ] 导入 JSON 文件后，创建新会话并加载消息
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 8.1（交互式请求 UI）完成后可并行

---

## Task 8.3：代码快捷操作（1 天）

### 任务目标
在 IntelliJ IDEA 编辑器中选中代码 → 右键菜单 → "解释代码" → ToolWindow 打开并发送解释请求。

### 实现步骤

**Step 8.3.1：理解 IntelliJ AnAction 创建方式**
参考现有 `src/main/kotlin/.../actions/` 目录下的 Action 实现。

**Step 8.3.2：创建 CodeQuickAction AnAction**
创建 `src/main/kotlin/.../actions/CodeQuickAction.kt`：
```kotlin
package com.github.xingzhewa.ccgui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

class CodeQuickAction : AnAction("解释代码") {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText

        if (selectedText.isNullOrBlank()) {
            return
        }

        // 获取 ToolWindow
        val toolWindowManager = WindowManager.getInstance()
        val toolWindow = toolWindowManager.getToolWindow("ClaudeCodeJet", project)

        // 激活 ToolWindow
        toolWindow?.activate {
            // 通过 EventBus 或直接调用发送消息
            // 这里需要找到 BridgeManager 或 ChatOrchestrator 的引用
            // 发送解释请求
        }
    }
}
```

**Step 8.3.3：注册 Action 到 plugin.xml**
在 `src/main/resources/META-INF/plugin.xml` 中添加：
```xml
<actions>
    <action
        id="com.github.xingzhewa.ccgui.actions.CodeQuickAction"
        class="com.github.xingzhewa.ccgui.actions.CodeQuickAction"
        text="解释代码"
        description="将选中代码发送到 ClaudeCodeJet 解释">
        <add-to-group group-id="EditorPopupMenu" anchor="last" />
    </action>
</actions>
```

**Step 8.3.4：简化版 — 通过 Bridge 发送**
由于 `BridgeManager` 是单例，可通过静态方法调用：
```kotlin
class CodeQuickAction : AnAction("解释代码") {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val selectedText = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText

        if (selectedText.isNullOrBlank()) return

        // 激活 ToolWindow 并发送消息
        val toolWindowManager = WindowManager.getInstance()
        val toolWindow = toolWindowManager.getToolWindow("ClaudeCodeJet", project)

        toolWindow?.activate {
            // 使用 invokeLater 确保在主线程
            SwingUtilities.invokeLater {
                // 找到 CefBrowserPanel 实例并调用 JS
                // 或者通过 project service 路由
            }
        }
    }
}
```

**Step 8.3.5：前端处理"解释代码"消息**
前端 ChatInput 接收特殊格式消息，渲染为"代码解释请求"：
```tsx
// 在 ChatInput 或 appStore 中
if (message.startsWith('[CODE_EXPLAIN]')) {
  const code = message.replace('[CODE_EXPLAIN]', '');
  // 显示代码解释请求气泡
}
```

### 验收标准（完成的定义）
- [ ] 在编辑器中选中代码 → 右键菜单 → "解释代码" 可见
- [ ] 点击"解释代码" → ToolWindow 打开并发送解释请求
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 8.1（交互式请求 UI）完成后可并行

---

## Task 8.4：PreviewPanel 基础布局（0.5 天）

### 任务目标
AppLayout 支持左右分栏布局，选中消息时右侧显示详情/元数据面板。

### 实现步骤

**Step 8.4.1：在 AppLayout 添加可选的 PreviewPanel**
读取 `webview/src/main/components/AppLayout.tsx`：
```tsx
import { useState } from 'react';

// 新增状态
const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null);
const [previewWidth, setPreviewWidth] = useState(320); // px

// 修改主内容区
<div className="flex-1 flex overflow-hidden">
  {/* 左侧：消息列表 */}
  <div className="flex-1 overflow-hidden">
    <Outlet />
  </div>

  {/* 右侧：PreviewPanel（仅选中消息时显示） */}
  {selectedMessageId && (
    <div
      className="border-l resize-x overflow-auto"
      style={{ width: previewWidth, minWidth: 200, maxWidth: 600 }}
    >
      <MessagePreview messageId={selectedMessageId} />
    </div>
  )}
</div>
```

**Step 8.4.2：创建 MessagePreview 组件**
创建 `webview/src/shared/components/MessagePreview.tsx`：
```tsx
import { useAppStore } from '@/shared/stores';

interface MessagePreviewProps {
  messageId: string;
}

export function MessagePreview({ messageId }: MessagePreviewProps) {
  const messages = useAppStore((s) => {
    const session = s.sessions.find(sess => sess.id === s.currentSessionId);
    return session?.messages ?? [];
  });

  const message = messages.find(m => m.id === messageId);

  if (!message) return <div className="p-4 text-muted-foreground">消息不存在</div>;

  return (
    <div className="p-4 space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="font-semibold">消息详情</h3>
        <button
          onClick={() => {/* 关闭 preview */}}
          className="text-muted-foreground hover:text-foreground"
        >
          ×
        </button>
      </div>

      <div className="space-y-3 text-sm">
        <div>
          <div className="text-muted-foreground">角色</div>
          <div>{message.role}</div>
        </div>
        <div>
          <div className="text-muted-foreground">时间</div>
          <div>{new Date(message.timestamp).toLocaleString('zh-CN')}</div>
        </div>
        <div>
          <div className="text-muted-foreground">内容长度</div>
          <div>{message.content.length} 字符</div>
        </div>
        {message.attachments && message.attachments.length > 0 && (
          <div>
            <div className="text-muted-foreground">附件</div>
            <div>{message.attachments.length} 个</div>
          </div>
        )}
      </div>
    </div>
  );
}
```

**Step 8.4.3：在 MessageList 点击消息时触发选中**
读取 `webview/src/shared/components/MessageList.tsx`，在消息项的 `onClick` 中：
```tsx
<div
  className="p-3 hover:bg-accent cursor-pointer"
  onClick={() => setSelectedMessageId(message.id)}
>
```

### 验收标准（完成的定义）
- [ ] AppLayout 支持左右分栏（消息列表 + 详情面板）
- [ ] 点击消息，右侧显示消息详情（元数据）
- [ ] 可以拖拽调整左右宽度比例
- [ ] `./gradlew build` 通过

### 依赖关系
- Task 8.2（导入导出）完成后可并行

---

## Task 8.5：Sprint 8 收尾（0.5 天）

### 任务目标
最终构建、测试、文档更新、打 tag。

### 实现步骤

**Step 8.5.1：最终构建**
```bash
./gradlew clean buildPlugin
```

**Step 8.5.2：全流程手动测试**
- [ ] 发送包含代码的消息，代码有高亮和复制按钮
- [ ] 创建两个会话，切换后消息正确显示
- [ ] 发送长消息，点击停止，流式立即中断
- [ ] 点击"+"创建新会话，自动切换并聚焦输入框
- [ ] Skills/Agents 页面正常显示
- [ ] 搜索历史会话，点击跳转
- [ ] 拖拽图片到输入框，显示预览
- [ ] 流式输出有闪烁光标
- [ ] 点击"✨优化"，输入框内容被优化
- [ ] AI 提问时显示交互式面板
- [ ] 导出会话为 JSON，导入后消息正确加载

**Step 8.5.3：更新 methodology.md**
读取 `design/methodology.md`，添加 Sprint 8 经验总结：
```markdown
## Sprint 8 经验总结

### 断点协同场景
- streaming:question 事件是前端与后端解耦的关键，前端通过 questionStore 统一管理
- InteractiveQuestionPanel 必须挂载在 ChatView 中，而非 MessageList（因为消息列表滚动时不应遮挡面板）

### JCEF 与 IDE 交互
- AnAction 中获取 ToolWindow 使用 `WindowManager.getToolWindow()`
- 激活 ToolWindow 使用 `toolWindow.activate {}`
- 选中编辑器文本使用 `CommonDataKeys.EDITOR`
```

**Step 8.5.4：打 tag 并 commit**
```bash
git add -A
git commit -m "feat: 完成 v0.0.2 开发 (Sprint 6-8)"
git tag v0.0.2
```

---

## 需要读取的文件（按优先级排序）

### 前端（TypeScript/React）

| 文件 | 关注点 |
|------|--------|
| `webview/src/main/pages/ChatView.tsx` | InteractiveQuestionPanel 挂载位置 |
| `webview/src/shared/stores/questionStore.ts` | 问题状态管理 |
| `webview/src/features/interaction/components/InteractiveQuestionPanel.tsx` | 交互面板（新建） |
| `webview/src/features/session/components/SessionTabs.tsx` | 导出按钮 |
| `webview/src/shared/components/MessageList.tsx` | 消息预览选中 |
| `webview/src/shared/components/MessagePreview.tsx` | 详情面板（新建） |

### 后端（Kotlin）

| 文件 | 关注点 |
|------|--------|
| `src/main/kotlin/.../actions/` | 现有 Action 参考 |
| `src/main/kotlin/.../actions/CodeQuickAction.kt` | 解释代码 Action（新建） |
| `src/main/kotlin/.../application/interaction/InteractiveRequestEngine.kt` | 提问流程 |
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | streaming:question 推送 |
| `src/main/resources/META-INF/plugin.xml` | Action 注册 |

---

## Sprint 8 验收标准

完成 Task 8.1-8.5 后，确认以下全部通过：

- [ ] AI 提问时显示 InteractiveQuestionPanel（确认/选择/输入）
- [ ] 用户回答后 AI 继续流式输出
- [ ] 会话可导出为 JSON 文件
- [ ] JSON 文件可导入创建新会话
- [ ] 编辑器右键菜单有"解释代码"选项
- [ ] 点击"解释代码"后 ToolWindow 打开并发送解释请求
- [ ] AppLayout 支持左右分栏，点击消息显示详情面板
- [ ] `./gradlew buildPlugin` 构建成功
- [ ] `git tag v0.0.2` 打标签成功

---

## 交接给下一位开发者

> "Sprint 8 完成。v0.0.2 所有功能已实现，具备发布质量。以下是各任务的实际执行情况：
>
> - Task 8.1（交互式请求）：InteractiveQuestionPanel 已集成，支持确认/选择/输入三种模式
> - Task 8.2（导入导出）：支持 JSON 格式的会话导入导出
> - Task 8.3（代码快捷操作）：编辑器右键菜单添加了"解释代码"Action
> - Task 8.4（PreviewPanel）：AppLayout 支持左右分栏，点击消息显示详情
> - Task 8.5（收尾）：最终构建通过，git tag v0.0.2 已创建
>
> v0.0.2 发布就绪。"
>
> **必须记录**：
> 1. InteractiveQuestionPanel 的三种模式（confirmation/selection/input）的具体 UI 布局
> 2. CodeQuickAction 的具体实现方式（通过什么机制发送到 ToolWindow）
> 3. PreviewPanel 的默认宽度和可调范围
