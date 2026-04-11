# Skill 01: PIT 踩坑记录与合规检查同步

**数据源**: `../pit/00-pit-master.md` (PIT Master — 唯一写入点)

> ⚠️ **合规检查清单由本 Skill 同步生成，不得手动编辑**
> 追加新 PIT 后调用 `/sync-pit`

---

## 1. PIT Master 同步机制

### 调用方式

```
/sync-pit
```

### 执行逻辑

**Step 1**：读取 `../pit/00-pit-master.md`，提取所有 PIT 条目的：
- `id` (PIT-001 ~ PIT-0XX)
- `title`
- `keyLocation`
- `avoidanceItems`（`**规避检查清单**` 下的所有 `- [ ]` 项）

**Step 2**：找到本文件的 `<!-- AUTO-GENERATED-CONTENT-START -->` 和 `<!-- AUTO-GENERATED-CONTENT-END -->` 标记区域，用解析出的条目替换。

**Step 3**：将 `上次同步时间：` 更新为当前时间。

### 同步分组（固定映射）

| Section | PIT IDs | Label |
|---------|---------|-------|
| 2.1 | PIT-001 | JCEF 时序 |
| 2.2 | PIT-002 | 构建配置 |
| 2.3 | PIT-003, PIT-006, PIT-009, PIT-010 | 前后端通信 + Kotlin 类型 |
| 2.4 | PIT-004 | 协程调度 |
| 2.5 | PIT-005 | 流式通信 |
| 2.6 | PIT-007 | ID 一致性 |
| 2.7 | PIT-008 | 命名规范 |
| 2.8 | PIT-011 | Claude Code Daemon 参数限制 |

### 自动触发时机

1. 向 `../pit/00-pit-master.md` 追加新 PIT 条目后
2. 手动执行 `/sync-pit` 时
3. Code Review 发现新盲点并已写入 PIT Master 后

### PIT 条目追加格式

在 `../pit/00-pit-master.md` 追加新 PIT 时，使用以下格式确保可被解析：

```markdown
## PIT-00X: [标题]

**现象**：[描述]

**根因**：[一句话技术原因]

**解决方案**：
1. [步骤1]
2. [步骤2]

**关键位置**：`[文件路径]:[行号]`

**规避检查清单**：
- [ ] [规避项1]
- [ ] [规避项2]

**状态**：resolved
```

注意：
- `**规避检查清单**：` 后必须换行
- 每个 `- [ ]` 项必须独占一行
- `**状态**：` 必须在 `**规避检查清单**` 之后，且格式为 `resolved` 或 `open`

---

## 2. 合规检查清单

> **自动生成** — 上次同步时间：2026-04-11 (PIT-012, PIT-013 新增)
> 同步方式：在对话中调用 `/sync-pit`

<!-- AUTO-GENERATED-CONTENT-START -->
### 2.1 JCEF 时序（PIT-001）

- [ ] **PIT-001**: 所有 JavaScript 注入代码是否放在 `onLoadEnd` 回调或 load listener 中？
- [ ] **PIT-001**: 是否添加了后备超时（3 秒）防止 load handler 未触发？
  - 检查：`CefBrowserPanel.kt`

### 2.2 构建配置（PIT-002）

- [ ] **PIT-002**: `vite.config.ts` 的 `base` 是否为 `'./'`（相对路径）？
- [ ] **PIT-002**: 生产构建后是否通过 `file://` 协议测试过页面加载？
  - 检查：`webview/vite.config.ts`

### 2.3 前后端通信 + Kotlin 类型（PIT-003, PIT-006, PIT-009, PIT-010）

- [ ] **PIT-003**: Java → JS 事件是否通过 `window.dispatchEvent(CustomEvent)` 广播？
- [ ] **PIT-003**: 前端 `index.tsx` 是否通过 `window.addEventListener` 监听并转发到 `eventBus`？
- [ ] **PIT-003**: `javaEvents` 数组是否包含了所有需要桥接的事件名？
  - 检查：`CefBrowserPanel.kt`
- [ ] **PIT-006**: `handleSendMessage` 是否处理了 `message` 字段的 JSON 字符串嵌套解析？
- [ ] **PIT-006**: 是否同时兼容直接字段格式（`sessionId`/`content`）作为 fallback？
  - 检查：`CefBrowserPanel.kt`
- [ ] **PIT-009**: 所有 Java → JS 的 Promise 返回值是否处理 null 场景？
- [ ] **PIT-009**: Kotlin handler 是否返回非空类型的结构化数据（不返回 null）？
  - 检查：`webview/src/main/components/TaskStatusBar.tsx:58-60`
- [ ] **PIT-010**: Kotlin 中 `mutableMapOf` 异构值类型时是否显式声明 `MutableMap<K, V>`？
- [ ] **PIT-010**: 若需要存储 null 值（如 `null as String?`），泛型参数 V 是否包含 `?`（可空）？
  - 检查：`CefBrowserPanel.kt:636`

### 2.4 协程调度（PIT-004）

- [ ] **PIT-004**: 所有 `CoroutineScope` 是否使用 `Dispatchers.Default`？
- [ ] **PIT-004**: 是否有启动阶段使用 `Dispatchers.Main` 的代码（应使用 `withContext(Dispatchers.EDT)` 临时切换）？
  - 检查：`EventBus.kt`

### 2.5 流式通信（PIT-005）

- [ ] **PIT-005**: 所有 streaming 事件（chunk/complete/error）是否携带 `messageId`？
- [ ] **PIT-005**: `handleSendMessage` 解析后是否将 `messageId` 保存到上下文中供后续事件使用？
  - 检查：`CefBrowserPanel.kt`

### 2.6 ID 一致性（PIT-007）

- [ ] **PIT-007**: 所有 `getToolWindow()` 调用使用的 ID 是否与 `plugin.xml` 中注册的 ID 一致？
- [ ] **PIT-007**: 新增 ToolWindow 时是否同时更新 `plugin.xml` 和代码中的 ID？
  - 检查：`MyProjectActivity.kt`

### 2.7 命名规范（PIT-008）

- [ ] **PIT-008**: `plugin.xml` 的 `<name>` 标签是否设置为 "CC Assistant"（非内部 ID）？
- [ ] **PIT-008**: ToolWindowFactory 是否显式调用 `setTitle("CC Assistant")`？
- [ ] **PIT-008**: 代码中是否有硬编码的产品名 "CCGUI" 需要替换？
  - 检查：`plugin.xml`

### 2.8 Claude Code Daemon 参数限制（PIT-011）

- [ ] **PIT-011**: 在 Claude Code daemon 模式下，任何模型级别的参数（temperature、topP、maxTokens 等）是否需要确认 CLI 支持后才暴露给用户？
- [ ] **PIT-011**: 新增模型参数配置时，是否确认了当前通信模式（HTTP API vs daemon CLI）的支持情况？
  - 检查：`src/main/kotlin/.../adaptation/sdk/SdkConfigBuilder.kt`

### 2.9 Flex 布局方向（PIT-012）

- [ ] **PIT-012**: 修改 flex 布局（增删子元素）时，是否确认 `flex-direction` 与预期布局方向一致？
- [ ] **PIT-012**: UI 重构后是否通过实际运行验证所有页面可见且可交互（不能仅依赖编译通过）？
  - 检查：`webview/src/main/components/AppLayout.tsx`

### 2.10 后端字段移除完整性（PIT-013）

- [ ] **PIT-013**: 移除 data class 字段后，是否执行了全文搜索确认无残留引用？
- [ ] **PIT-013**: 前后端共享字段变更时，是否同时更新 Kotlin data class + TS type + Bridge handler 三处？
- [ ] **PIT-013**: 字段移除后是否运行了 `compileKotlin` + `tsc --noEmit` 双端编译验证？
  - 检查：`model/config/ModelConfig.kt`、`browser/CefBrowserPanel.kt`、`webview/src/lib/java-bridge.ts`
<!-- AUTO-GENERATED-CONTENT-END -->

---

## 3. Bug 修复工作流

### Step 1：判断是否新盲点

对照上方检查清单，确认该 bug 是否有对应 PIT 规避项：
- **有对应 PIT** → 确认现有 PIT 描述准确，检查是否再次触发
- **无对应 PIT** → 这是**新盲点**，进入 Step 2

### Step 2：写入 PIT Master

在 `../pit/00-pit-master.md` 末尾追加 PIT 条目（格式见上方"同步机制"）。

### Step 3：同步到合规清单

在对话中调用 `/sync-pit`，本 Skill 自动解析 PIT Master 并更新上方清单区域。

### Step 4：验证修复

- [ ] `./gradlew build` 通过
- [ ] `./gradlew runIde` 沙箱测试通过
- [ ] 相关功能端到端测试通过

---

## 4. 代码修改规范

### 4.1 Kotlin 后端

**协程**：
```kotlin
// ✅ 正确
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// ❌ 错误：启动阶段使用 Dispatchers.Main
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
```

**NDJSON**：
```kotlin
// ✅ 正确
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

**异构 MutableMap**：
```kotlin
// ✅ 正确：显式声明类型
private val chatConfig: MutableMap<String, Any?> = mutableMapOf(...)

// ❌ 错误：类型推断导致投影冲突
private val chatConfig = mutableMapOf(...) // MutableMap<String, out Any?>
```

### 4.2 TypeScript 前端

**Vite 配置**：
```typescript
// webview/vite.config.ts
base: './',  // ✅ 相对路径
```

**Java → JS 事件桥接**：
```typescript
// webview/src/main/index.tsx
const javaEvents = [
  'streaming:chunk', 'streaming:complete', 'streaming:error',
  'streaming:question', 'response'
];
javaEvents.forEach((eventName) => {
  window.addEventListener(eventName, (e: Event) => {
    const customEvent = e as CustomEvent;
    eventBus.emit(eventName, customEvent.detail);
  });
});
```

**Promise 返回值空值处理**：
```typescript
// ✅ 正确
const DEFAULT_DATA = { tasks: [], activeSubagents: [], diffRecords: [] };
setData(result ?? DEFAULT_DATA);

// ❌ 错误：直接使用可能为 null 的返回值
setData(result); // TS error + runtime crash
```

---

## 5. 关键文件索引

| 文件 | 职责 |
|------|------|
| `src/main/kotlin/.../browser/CefBrowserPanel.kt` | JCEF 桥接、JS 注入、handle* 方法 |
| `src/main/kotlin/.../bridge/BridgeManager.kt` | Java 后端消息路由 |
| `src/main/kotlin/.../application/ContextManager.kt` | 上下文长度追踪 + /compact |
| `webview/src/lib/java-bridge.ts` | 前端 → Java 通信封装 |
| `webview/src/main/index.tsx` | Java → 前端事件桥接（CustomEvent） |
| `webview/src/shared/stores/streamingStore.ts` | 流式输出状态 |
