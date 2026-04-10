# ClaudeCodeJet PRD v3.1 - 开发驱动版

**文档版本**: 3.2
**创建日期**: 2026-04-10
**基于版本**: v3.1
**文档状态**: 开发驱动版（可执行）

---

## 变更日志

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v3.1 | 2026-04-10 | 优化为开发驱动版：明确MVP边界、修正技术路径、增加可测试验收标准 |
| v3.2 | 2026-04-10 | 更新技术架构：采用 Claude Agent SDK + daemon.js stdin/stdout NDJSON 方案，替换原有 HTTP + SSE 方案 |

---

## 1. 项目概述（精简版）

### 1.1 核心价值主张

ClaudeCodeJet 是一款 IntelliJ IDEA 插件，通过可视化界面调用 Claude API，为开发者提供：
1. **无缝集成**：IDE内直接对话，不打断心流
2. **多模型支持**：Anthropic/OpenAI/DeepSeek 一键切换
3. **代码深度交互**：选中文本 → 快捷键 → AI分析

### 1.2 用户痛点（精简）

| 痛点 | 解决方案 |
|------|----------|
| CLI交互破坏心流 | ToolWindow内聊天 |
| Copilot无法自定义 | Skills/Agents可视化配置 |
| 多会话管理混乱 | 多Tab会话隔离 |
| 配置修改需重启 | 配置热更新 |

### 1.3 MVP定义（v1.0 必须交付）

**MVP范围**（Phase 1）：
- ✅ 单会话聊天（非多会话）
- ✅ Claude Agent SDK 直连（自维护 daemon.js）
- ✅ 基础Markdown渲染（非完整解析）
- ✅ 主题切换（预设主题，非自定义编辑器）
- ❌ ~~交互式请求引擎~~ → 降级为"选择式快捷操作"
- ❌ ~~多会话+搜索~~ → Phase 2
- ❌ ~~Skills/Agents/MCP~~ → Phase 3

**MVP成功标准**：
- 用户输入问题 → 收到AI回复（端到端）
- 响应时间 < 3秒（含网络延迟）
- 无崩溃、无内存泄漏

---

## 2. 技术架构（精简可执行版）

### 2.1 整体架构图（SDK方案 - 修订版）

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    ClaudeCodeJet 架构 (Agent SDK 方案)                  │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌───────────────┐     ┌─────────────────────────────────────────────┐ │
│  │   Swing层      │     │              JCEF层 (React 18)              │ │
│  │               │     │                                             │ │
│  │  • Settings   │     │  • ChatWindow (聊天界面)                     │ │
│  │  • StatusBar  │     │  • MessageList (虚拟滚动)                    │ │
│  │  • Popups     │     │  • ChatInput (输入+发送)                     │ │
│  └───────┬───────┘     └──────────────────┬──────────────────────────┘ │
│          │                                  │                            │
│          │         ┌────────────────────────┴───────────────────────┐  │
│          │         │              Java ↔ JS Bridge                   │  │
│          │         │  • JBCefJSQuery (JS→Java)                       │  │
│          │         │  • CefJavaScriptExecutor (Java→JS)            │  │
│          │         └─────────────────────────────────────────────────┘  │
│          │                                  │                            │
├──────────┼──────────────────────────────────┼────────────────────────────┤
│          │     Application Layer            │                            │
│          │                                  │                            │
│  ┌───────▼──────────────────────────────────────────────────────────┐  │
│  │                   DaemonBridge (Kotlin)                            │  │
│  │  • Process stdin/stdout 管理                                       │  │
│  │  • NDJSON 编码/解码                                                │  │
│  │  • 心跳线程 (15s 间隔, 45s 超时)                                   │  │
│  │  • 自动重启 (最多 3 次)                                            │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│                        │ Process IO (stdin/stdout)                      │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│                      Daemon Process (Node.js)                           │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                        daemon.js (管道模式)                        │  │
│  │  • 预加载 Claude Agent SDK (启动时一次性)                          │  │
│  │  • stdout 拦截打标签 (为每行输出加 request ID)                      │  │
│  │  • process.exit 拦截 (防止意外退出)                                │  │
│  │  • 请求队列 (串行化命令请求)                                       │  │
│  │  • 父进程监控 (父进程死亡则自动退出)                                │  │
│  └───────────────────────────────┬──────────────────────────────────┘  │
│                                  │                                       │
│                                  ▼                                       │
│                    ┌─────────────────────────┐                           │
│                    │  Claude Agent SDK       │                           │
│                    │  (@anthropic-ai/sdk)    │                           │
│                    │                          │                           │
│                    │  • sdk.query()          │                           │
│                    │  • OAuth 处理            │                           │
│                    │  • MCP 工具调用          │                           │
│                    └───────────┬─────────────┘                           │
│                                │                                          │
│                                ▼                                          │
│                      ┌─────────────────┐                                 │
│                      │  Claude API     │                                 │
│                      │  + OAuth        │                                 │
│                      │  + MCP Servers  │                                 │
│                      └─────────────────┘                                 │
│                                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                      Infrastructure Layer                               │
│  • ConfigManager (配置存储)                                             │
│  • SecureStringStorage (API Key加密)                                    │
│  • EventBus (事件总线)                                                 │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 架构说明

| 层级 | 组件 | 技术选型 | 说明 |
|------|------|----------|------|
| 前端 | ChatWindow | React 18 + TypeScript | JCEF内嵌 |
| 前端桥接 | CefBrowserPanel | JBCefJSQuery | Java ↔ JS 通信 |
| 应用层 | DaemonBridge | Kotlin + Process IO | stdin/stdout NDJSON 通信 |
| 应用层 | ChatOrchestrator | Kotlin | 聊天编排 |
| Daemon层 | daemon.js | Node.js (无HTTP) | 管道处理器 |
| SDK层 | @anthropic-ai/sdk | TypeScript | Claude SDK |
| 协议 | NDJSON | 每行一个完整 JSON 对象 | 进程间通信 |

### 2.3 NDJSON 协议规范（修订版）

> **重要变更**：原 HTTP + SSE 方案已废弃，改为 stdin/stdout + NDJSON 方案。
> daemon.js 不再是 HTTP 服务器，而是纯管道处理器。

#### 协议格式

NDJSON = Newline Delimited JSON，每行是一个完整 JSON 对象。

**请求（Java → Node.js，写入 daemon stdin）：**
```json
{"id":"1","method":"claude.send","params":{"message":"Hello","cwd":"/project"}}
{"id":"2","method":"heartbeat"}
{"id":"3","method":"shutdown"}
{"id":"4","method":"abort"}
```

**响应（Node.js → Java，从 daemon stdout 读取）：**
```json
{"type":"daemon","event":"starting","pid":12345}
{"type":"daemon","event":"sdk_loaded","provider":"claude"}
{"type":"daemon","event":"ready","pid":12345,"sdkPreloaded":true}

{"id":"1","line":"[MESSAGE_START]"}
{"id":"1","line":"[STREAM_START]"}
{"id":"1","line":"[CONTENT_DELTA] \"Hello, \""}
{"id":"1","line":"[CONTENT_DELTA] \"world!\""}
{"id":"1","line":"[TOOL_USE] {\"id\":\"tool_1\",\"name\":\"Read\"}"}
{"id":"1","line":"[TOOL_RESULT] {\"tool_use_id\":\"tool_1\"}"}
{"id":"1","line":"[SESSION_ID] sess_abc123"}
{"id":"1","line":"[USAGE] {\"output_tokens\":120,\"input_tokens\":80}"}
{"id":"1","line":"[MESSAGE_END]"}
{"id":"1","done":true,"success":true}

{"id":"2","type":"heartbeat","ts":1234567890}
```

#### 消息标签体系

| 标签 | 含义 | Java 处理方式 |
|------|------|-------------|
| `[MESSAGE_START]` | SDK 开始处理消息 | 重置消息累加器 |
| `[STREAM_START]` | 流式输出开始 | 通知前端进入流式渲染模式 |
| `[CONTENT_DELTA] "xxx"` | 文本增量（已转义JSON字符串） | 追加到当前消息，实时推送前端 |
| `[THINKING_DELTA] "xxx"` | 思考过程增量 | 追加到 thinking 区域 |
| `[TOOL_USE] {json}` | 工具调用请求 | 显示工具块，等待用户确认 |
| `[TOOL_RESULT] {json}` | 工具执行结果 | 追加到消息列表 |
| `[SESSION_ID] xxx` | 会话 ID（用于恢复会话） | 缓存到 SessionState |
| `[USAGE] {json}` | Token 使用量 | 更新状态栏显示 |
| `[MESSAGE_END]` | 单轮消息结束 | 标记轮次完成 |
| `[SEND_ERROR] {json}` | 错误信息 | 通知前端显示错误 |
| `{"done":true,"success":true}` | 请求完成成功 | resolve CompletableFuture |
| `{"done":true,"success":false,"error":"..."}` | 请求失败 | reject CompletableFuture |

#### 请求方法

| 方法 | 描述 | 参数 |
|------|------|------|
| `claude.send` | 发送消息 | `{message, sessionId?, cwd, model?}` |
| `claude.preconnect` | 预热连接 | `{cwd, model?}` |
| `claude.resetRuntime` | 重置 Runtime | `{sessionId}` |
| `heartbeat` | 心跳检测 | 无 |
| `shutdown` | 优雅关闭 | 无 |
| `abort` | 中止当前请求 | 无 |

#### Daemon 生命周期事件

| 事件 | 说明 |
|------|------|
| `starting` | daemon 启动中 |
| `sdk_loaded` | SDK 加载完成 |
| `ready` | 就绪，可接受请求 |
| `log` | 日志消息（仅调试） |
| `error` | 错误事件 |

### 2.2 技术选型决策（Sprint可执行版）

| 模块 | 技术方案 | 决策理由 | 状态 |
|------|----------|----------|------|
| 聊天界面 | JCEF + React 18 | Markdown渲染 + 流式输出 | ✅ MVP |
| 设置页面 | Kotlin UI DSL | 与IDEA原生一致 | ✅ MVP |
| 状态栏 | Swing Label | 轻量、启动快 | ✅ MVP |
| 快捷菜单 | AnAction | IDE原生 | ✅ MVP |
| 主题切换 | JCEF内React组件 | 实时预览 | Phase 2 |
| 多会话管理 | JCEF Tab组件 | UI一致 | Phase 2 |
| 生态集成 | Swing+JCEF混合 | 配置复杂 | Phase 3 |
| **进程通信** | **stdin/stdout + NDJSON** | **SDK 指南要求** | **✅ MVP** |

### 2.5 数据流图（MVP必须跑通 - 修订版）

```
用户输入"解释这段代码"
        │
        ▼
┌───────────────────┐
│  ChatInput (JCEF) │
│  用户点击发送      │
└─────────┬─────────┘
          │ window.ccBackend.streamMessage("解释这段代码")
          ▼
┌───────────────────────────────────────────────────┐
│              CefBrowserPanel (Java)                │
│  1. 验证输入非空                                   │
│  2. 调用 DaemonBridge.sendCommand()              │
└────────────────────────┬──────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────┐
│              DaemonBridge (Kotlin)                │
│  1. 构造 NDJSON 请求                               │
│  2. 写入 stdin                                     │
│  3. 从 stdout 读取响应行                           │
│  4. 解析标签，路由到 pendingRequests               │
│  5. 回调 onLine / onComplete                      │
└────────────────────────┬──────────────────────────┘
                         │
                         │ stdin/stdout (NDJSON)
                         ▼
┌───────────────────────────────────────────────────┐
│           daemon.js (Node.js)                      │
│  1. 从 stdin 读取 NDJSON 请求                      │
│  2. 解析 method 分发到对应 handler                 │
│  3. 调用 Agent SDK sdk.query()                    │
│  4. stdout 拦截打标签输出                          │
└────────────────────────┬──────────────────────────┘
                         │
                         ▼
┌───────────────────────────────────────────────────┐
│           Claude Agent SDK                         │
│           (@anthropic-ai/sdk)                      │
└────────────────────────┬──────────────────────────┘
                         │
                         │ window.ccEvents.onStreamingChunk()
                         ▼
┌───────────────────────────────────────────────────┐
│           ChatWindow (React)                       │
│  1. 收到 streamingChunk 事件                       │
│  2. 追加到 messages state                         │
│  3. React 重新渲染 MessageList                    │
└───────────────────────────────────────────────────┘
```

---

## 3. 核心模块详细设计（MVP可执行版）

### 3.1 模块1: 聊天界面 (ChatWindow)

#### 3.1.1 组件层级

```
ChatWindow (JCEF React)
├── ChatHeader
│   ├── modelSelector (下拉选择)
│   └── settingsButton (齿轮图标)
├── MessageList (虚拟滚动)
│   ├── MessageItem (role=user)
│   ├── MessageItem (role=assistant)
│   └── ErrorItem (错误提示)
├── ChatInput
│   ├── textarea (自动增高)
│   ├── sendButton
│   └── stopButton (流式中断)
└── StatusBar
    ├── tokenCount
    └── streamingIndicator
```

#### 3.1.2 状态定义

```typescript
// ChatWindow State (Zustand)
interface ChatState {
  // 消息列表
  messages: ChatMessage[];

  // 输入状态
  inputValue: string;
  isStreaming: boolean;

  // 模型配置
  currentModel: string;
  provider: 'anthropic' | 'openai' | 'deepseek';

  // 错误状态
  error: string | null;
}

interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}
```

#### 3.1.3 接口定义（Java ↔ JS）

**JS 调用 Java**：

```typescript
// window.ccBackend API
interface CcBackend {
  // 发送消息（单次，非流式）
  sendMessage(text: string): Promise<SendMessageResult>;

  // 流式发送
  streamMessage(text: string): void;

  // 中断流式
  cancelStreaming(): void;

  // 获取配置
  getConfig(): Promise<AppConfig>;

  // 更新配置
  updateConfig(config: Partial<AppConfig>): Promise<void>;

  // 获取可用模型
  getAvailableModels(provider: string): Promise<string[]>;
}

interface SendMessageResult {
  success: boolean;
  messageId?: string;
  error?: string;
}
```

**Java 触发 JS 事件**：

```typescript
// window.ccEvents API
interface CcEvents {
  // 消息事件
  onMessageReceived(callback: (msg: ChatMessage) => void): void;
  onStreamingChunk(callback: (chunk: string) => void): void;
  onStreamingComplete(callback: () => void): void;
  onStreamingError(callback: (error: string) => void): void;

  // 配置事件
  onConfigChanged(callback: (config: AppConfig) => void): void;

  // 模型事件
  onModelChanged(callback: (model: string) => void): void;
}
```

#### 3.1.4 MVP验收标准

| 功能 | 验收条件 | 测试方法 |
|------|----------|----------|
| 发送消息 | 输入"你好"点击发送，2秒内收到AI回复 | 手动测试，计时 |
| Markdown渲染 | AI返回的代码块有语法高亮 | 检查渲染结果 |
| 流式输出 | AI回复逐字显示，有打字机效果 | 观察动画 |
| 中断功能 | 点击停止按钮，流式输出立即停止 | 手动测试 |
| 滚动优化 | 100条消息时滚动流畅不卡顿 | 性能测试 |

---

### 3.2 模块2: 配置管理 (ConfigManager)

#### 3.2.1 配置数据结构

```kotlin
// MVP版本配置
data class AppConfig(
    val version: String = "1.0.0",

    // 模型配置（MVP只支持Anthropic）
    val model: ModelConfig = ModelConfig(),

    // 主题配置
    val theme: ThemeConfig = ThemeConfig.JETBRAINS_DARK,

    // UI配置
    val ui: UiConfig = UiConfig()
)

data class ModelConfig(
    val provider: String = "anthropic",
    val apiKey: String = "",  // 加密存储
    val model: String = "claude-3-5-sonnet-20241022",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7
)

data class UiConfig(
    val streamingSpeed: Int = 50,  // ms/字符
    val showTokenCount: Boolean = true,
    val enableHotReload: Boolean = true
)
```

#### 3.2.2 配置热更新流程

```
用户修改API Key
        │
        ▼
Swing Settings 保存配置
        │
        ▼
ConfigManager.saveConfig()
        │
        ├─► 加密存储到 SecureStringStorage
        │
        ├─► EventBus.publish(ConfigChangedEvent)
        │
        └─► JcefBridge.notifyJs("configChanged", config)
                │
                ▼
        React useEffect 监听器触发
                │
                ▼
        更新 local state，重新渲染
```

#### 3.2.3 验收标准

| 功能 | 验收条件 | 测试方法 |
|------|----------|----------|
| API Key保存 | 关闭设置页面重开，API Key仍存在 | 重启IDE测试 |
| 模型切换 | 切换模型后，新消息使用新模型 | 发送消息验证 |
| 主题切换 | 切换主题，聊天界面立即更新 | 手动切换 |
| 配置校验 | 无效API Key时提示错误 | 输入错误Key测试 |

---

### 3.3 模块3: DaemonBridge (SDK通信桥接 - 修订版)

> **架构说明**：原 AgentSdkBridge + HTTP + SSE 方案已废弃。
> 当前采用 DaemonBridge + stdin/stdout + NDJSON 方案（基于 SDK 指南）。

#### 3.3.1 接口定义

```kotlin
/**
 * DaemonBridge - 进程桥接器
 *
 * 负责：
 * 1. 管理 daemon.js 进程生命周期
 * 2. stdin/stdout NDJSON 通信
 * 3. 心跳检测与自动重启
 * 4. 请求/响应路由
 */
class DaemonBridge(private val project: Project) : Disposable {

    /** Node.js 进程 */
    private var daemonProcess: Process? = null

    /** stdin writer */
    private var daemonStdin: BufferedWriter? = null

    /** stdout reader 线程 */
    private var readerThread: Thread? = null

    /** 心跳线程 */
    private var heartbeatThread: Thread? = null

    /** 运行状态标志 */
    private val isRunning = AtomicBoolean(false)

    /** 请求 ID 计数器 */
    private val requestIdCounter = AtomicInteger(1)

    /** 待处理请求映射 */
    private val pendingRequests = ConcurrentHashMap<String, RequestHandler>()

    /** 上次心跳响应时间 */
    private val lastHeartbeatResponse = AtomicLong(0)

    /** 重启次数 */
    private val restartAttempts = AtomicInteger(0)

    /**
     * 启动 daemon 进程
     */
    fun start(): Boolean

    /**
     * 发送命令（NDJSON）
     */
    fun sendCommand(
        method: String,
        params: JsonObject,
        callback: DaemonOutputCallback
    ): CompletableFuture<Boolean>

    /**
     * 发送消息（流式）
     */
    fun sendMessage(
        content: String,
        sessionId: String? = null,
        cwd: String? = null
    ): CompletableFuture<Boolean>

    /**
     * 停止 daemon
     */
    fun stop()

    override fun dispose() { ... }
}

interface DaemonOutputCallback {
    fun onLine(tag: String, content: String?)
    fun onComplete(success: Boolean, error: String?)
}

class RequestHandler(
    val callback: DaemonOutputCallback,
    val future: CompletableFuture<Boolean>
)

data class SseEvent(
    val type: SseEventType,
    val data: Map<String, Any?>
)

enum class SseEventType {
    MESSAGE_START,
    CONTENT_BLOCK_START,
    CONTENT_BLOCK_DELTA,
    CONTENT_BLOCK_STOP,
    MESSAGE_STOP,
    USAGE,
    ERROR
}
```

#### 3.3.2 进程启动

```kotlin
fun start(): Boolean {
    try {
        // 1. 找到 daemon.js 路径
        val bridgeDir = findBridgeDir()
        val daemonScript = File(bridgeDir, "daemon.js")

        // 2. 找到 Node.js 可执行文件
        val nodePath = nodeDetector.findNodeExecutable()
            ?: throw IllegalStateException("Node.js not found")

        // 3. 构造进程
        val pb = ProcessBuilder(nodePath, daemonScript.absolutePath)
        pb.directory(bridgeDir)
        configureEnvironment(pb, nodePath)

        // 4. 启动进程
        daemonProcess = pb.start()
        isRunning.set(true)

        // 5. 获取 stdin writer
        daemonStdin = BufferedWriter(
            OutputStreamWriter(daemonProcess!!.outputStream, StandardCharsets.UTF_8)
        )

        // 6. 启动 stdout 读取线程
        startReaderThread()

        // 7. 启动 stderr 读取线程
        startStderrReaderThread()

        // 8. 等待 "ready" 事件
        val ready = awaitReady(30_000)
        if (!ready) return false

        // 9. 启动心跳线程
        startHeartbeatThread()

        return true
    } catch (e: Exception) {
        log.error("Daemon start failed", e)
        return false
    }
}

private fun awaitReady(timeoutMs: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (daemonReady.get()) return true
        Thread.sleep(100)
    }
    return false
}
```


#### 3.3.3 NDJSON 通信

```kotlin
/**
 * 发送命令（NDJSON 格式）
 */
fun sendCommand(
    method: String,
    params: JsonObject,
    callback: DaemonOutputCallback
): CompletableFuture<Boolean> {
    if (!isRunning.get()) {
        return CompletableFuture.failedFuture(IOException("Daemon not running"))
    }

    val requestId = requestIdCounter.incrementAndGet().toString()
    val future = CompletableFuture<Boolean>()
    val handler = RequestHandler(callback, future)
    pendingRequests[requestId] = handler

    val request = JsonObject().apply {
        addProperty("id", requestId)
        addProperty("method", method)
        add("params", params)
    }

    synchronized(daemonStdin!!) {
        daemonStdin!!.write(request.toString())
        daemonStdin!!.newLine()
        daemonStdin!!.flush()
    }

    return future
}

/**
 * 读取响应循环
 */
private fun readLoop() {
    try {
        BufferedReader(InputStreamReader(daemonProcess!!.inputStream, UTF_8)).use { reader ->
            var line: String?
            while (isRunning.get() && reader.readLine().also { line = it } != null) {
                handleLine(line!!)
            }
        }
    } catch (e: IOException) {
        if (isRunning.get()) log.error("Reader error", e)
    }
    handleDaemonDeath()
}

/**
 * 解析 NDJSON 行
 */
private fun handleLine(jsonLine: String) {
    val obj = JsonParser.parseString(jsonLine).asJsonObject

    if (obj.has("type") && "daemon" == obj.get("type").asString) {
        handleDaemonEvent(obj)
        return
    }

    val id = obj.get("id").asString

    if (obj.has("done")) {
        val success = obj.get("success").asBoolean
        pendingRequests.remove(id)?.future?.complete(success)
        return
    }

    if (obj.has("line")) {
        val tagLine = obj.get("line").asString
        parseAndDispatch(tagLine, pendingRequests[id]?.callback)
    }
}
```

#### 3.3.4 心跳与故障自愈

```kotlin
private fun startHeartbeatThread() {
    heartbeatThread = Thread({
        while (isRunning.get()) {
            Thread.sleep(15_000)  // 15秒

            val heartbeatRequest = JsonObject().apply {
                addProperty("id", "hb-${System.currentTimeMillis()}")
                addProperty("method", "heartbeat")
            }
            synchronized(daemonStdin!!) {
                daemonStdin!!.write(heartbeatRequest.toString())
                daemonStdin!!.newLine()
                daemonStdin!!.flush()
            }

            val heartbeatAge = System.currentTimeMillis() - lastHeartbeatResponse.get()
            if (heartbeatAge > 45_000) {
                log.warn("Heartbeat timeout, restarting daemon...")
                handleDaemonDeath()
            }
        }
    }, "DaemonBridge-Heartbeat")
    heartbeatThread!!.isDaemon = true
    heartbeatThread!!.start()
}

private fun handleDaemonDeath() {
    isRunning.set(false)
    daemonProcess?.destroyForcibly()

    pendingRequests.forEach { (_, handler) ->
        handler.callback.onComplete(false, "Daemon died")
        handler.future.complete(false)
    }
    pendingRequests.clear()

    val attempts = restartAttempts.incrementAndGet()
    if (attempts <= 3) {
        log.info("Auto-restarting daemon (attempt $attempts/3)")
        start()
    } else {
        log.error("Max restart attempts reached, giving up")
    }
}
```

#### 3.3.5 标签解析

```kotlin
private fun parseAndDispatch(line: String, callback: DaemonOutputCallback?) {
    callback ?: return

    when {
        line.startsWith("[STREAM_START]") -> callback.onLine("stream_start", null)
        line.startsWith("[CONTENT_DELTA]") -> {
            val content = unescapeJsonString(line.substring(15))
            callback.onLine("content_delta", content)
        }
        line.startsWith("[THINKING_DELTA]") -> {
            val content = unescapeJsonString(line.substring(17))
            callback.onLine("thinking_delta", content)
        }
        line.startsWith("[TOOL_USE]") -> callback.onLine("tool_use", line.substring(10))
        line.startsWith("[TOOL_RESULT]") -> callback.onLine("tool_result", line.substring(13))
        line.startsWith("[SESSION_ID]") -> callback.onLine("session_id", line.substring(12).trim())
        line.startsWith("[USAGE]") -> callback.onLine("usage", line.substring(8))
        line.startsWith("[MESSAGE_END]") -> callback.onLine("message_end", null)
        line.startsWith("[SEND_ERROR]") -> callback.onLine("error", line.substring(13))
        line.startsWith("[MESSAGE]") -> callback.onLine("message", line.substring(9))
    }
}

private fun unescapeJsonString(s: String): String {
    return JsonParser.parseString(s.trim()).asString
}
```

#### 3.3.6 验收标准

| 功能 | 验收条件 | 测试方法 |
|------|----------|----------|
| 进程启动 | daemon 启动成功并发送 ready 事件 | 单元测试 |
| NDJSON 通信 | 发送请求，收到带标签的响应 | 集成测试 |
| 心跳检测 | 45秒无响应自动重启 | Mock 测试 |
| 错误处理 | daemon 崩溃后自动重启 3 次 | 单元测试 |

---

### 3.4 模块4: JCEF集成 (CefBrowserPanel)

#### 3.4.1 初始化流程

```kotlin
class CefBrowserPanel(project: Project) : JPanel() {

    private val browser: JBCefBrowser
    private val jsQuery: JBCefJSQuery

    init {
        // 1. 创建Browser
        browser = JBCefBrowserBuilder()
            .setInitialPage(PluginResourceHandler.getChatPageUrl())
            .build()

        // 2. 创建JSQuery桥接
        jsQuery = JBCefJSQuery.create(browser)

        // 3. 注册JS→Java处理器
        jsQuery.addHandler { request ->
            handleJsRequest(request)
        }

        // 4. 添加到Panel
        layout = BorderLayout()
        add(browser.component, BorderLayout.CENTER)

        // 5. 注入JavaScript端点
        injectJsEndpoints()
    }

    private fun injectJsEndpoints() {
        browser.cefClient.executeJavaScript("""
            window.ccBackend = {
                sendMessage: function(text) {
                    return $jsQuery.invoke(JSON.stringify({
                        action: 'sendMessage',
                        text: text
                    }));
                },
                streamMessage: function(text) {
                    return $jsQuery.invoke(JSON.stringify({
                        action: 'streamMessage',
                        text: text
                    }));
                },
                cancelStreaming: function() {
                    return $jsQuery.invoke(JSON.stringify({
                        action: 'cancelStreaming'
                    }));
                }
            };
        """.trimIndent(), null, 0)
    }

    private fun handleJsRequest(request: String): String? {
        val data = Json.parseToJsonElement(request).jsonObject
        val action = data["action"]?.jsonPrimitive?.content

        when (action) {
            "sendMessage" -> {
                val text = data["text"]?.jsonPrimitive?.content ?: return null
                handleSendMessage(text)
            }
            "streamMessage" -> {
                val text = data["text"]?.jsonPrimitive?.content ?: return null
                handleStreamMessage(text)
            }
            "cancelStreaming" -> {
                handleCancelStreaming()
            }
        }
        return null
    }

    private fun handleSendMessage(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = chatOrchestrator.sendMessage(
                    ChatMessage(role = "user", content = text)
                )
                notifyJs("messageReceived", mapOf(
                    "id" to response.messageId,
                    "content" to response.content,
                    "role" to "assistant"
                ))
            } catch (e: Exception) {
                notifyJs("error", mapOf("message" to e.message))
            }
        }
    }

    private fun handleStreamMessage(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            chatOrchestrator.streamMessage(
                ChatMessage(role = "user", content = text)
            ).collect { chunk ->
                notifyJs("streamingChunk", mapOf("chunk" to chunk))
            }
        }
    }

    private fun handleCancelStreaming() {
        chatOrchestrator.cancelStreaming()
        notifyJs("streamingCancelled", emptyMap())
    }

    private fun notifyJs(event: String, data: Map<String, Any?>) {
        browser.cefClient.executeJavaScript("""
            window.ccEvents?.$event(${Json.encodeToString(data)});
        """.trimIndent(), null, 0)
    }

    fun dispose() {
        browser.dispose()
        super.dispose()
    }
}
```

#### 3.4.2 React组件实现

```typescript
// ChatWindow.tsx
import React, { useEffect, useRef, useState } from 'react';
import { FixedSizeList } from 'react-window';
import ReactMarkdown from 'react-markdown';
import hljs from 'highlight.js';

export const ChatWindow: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const listRef = useRef<FixedSizeList>(null);

  useEffect(() => {
    // 注册事件监听
    window.ccEvents.onMessageReceived((msg) => {
      setMessages(prev => [...prev, msg]);
    });

    window.ccEvents.onStreamingChunk((chunk) => {
      setMessages(prev => {
        const last = prev[prev.length - 1];
        if (last?.role === 'assistant') {
          return [...prev.slice(0, -1), { ...last, content: last.content + chunk }];
        }
        return [...prev, { id: Date.now().toString(), role: 'assistant', content: chunk }];
      });
    });

    window.ccEvents.onStreamingComplete(() => {
      setIsStreaming(false);
    });

    window.ccEvents.onStreamingError((error) => {
      setIsStreaming(false);
      alert(error.message);
    });
  }, []);

  const handleSend = async () => {
    if (!inputValue.trim() || isStreaming) return;

    const userMessage = { id: Date.now().toString(), role: 'user', content: inputValue };
    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setIsStreaming(true);

    await window.ccBackend.streamMessage(inputValue);
  };

  const handleStop = () => {
    window.ccBackend.cancelStreaming();
    setIsStreaming(false);
  };

  return (
    <div className="chat-window">
      <div className="message-list">
        <FixedSizeList
          height={600}
          itemCount={messages.length}
          itemSize={80}
          width="100%"
          ref={listRef}
        >
          {({ index, style }) => (
            <MessageItem message={messages[index]} style={style} />
          )}
        </FixedSizeList>
      </div>

      <div className="chat-input">
        <textarea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && handleSend()}
          placeholder="输入消息..."
        />
        {isStreaming ? (
          <button onClick={handleStop}>停止</button>
        ) : (
          <button onClick={handleSend}>发送</button>
        )}
      </div>
    </div>
  );
};

const MessageItem: React.FC<{ message: ChatMessage; style: any }> = ({ message, style }) => (
  <div style={style} className={`message message-${message.role}`}>
    <ReactMarkdown
      components={{
        code: ({ className, children }) => {
          const match = /language-(\w+)/.exec(className || '');
          const code = String(children).replace(/\n$/, '');
          return match ? (
            <pre><code className={match[1]}>{hljs.highlight(code, { language: match[1] }).value}</code></pre>
          ) : (
            <code>{children}</code>
          );
        }
      }}
    >
      {message.content}
    </ReactMarkdown>
  </div>
);
```

#### 3.4.3 验收标准

| 功能 | 验收条件 | 测试方法 |
|------|----------|----------|
| 首次加载 | ToolWindow打开 < 1.5s | 性能测试 |
| JS桥接 | 消息往返 < 100ms | 单元测试 |
| 内存管理 | 关闭ToolWindow无内存泄漏 | 内存profiler |
| 异常恢复 | JS崩溃后可重连 | 手动测试 |

---

### 3.5 模块5: 设置页面 (SettingsConfigurable)

#### 3.5.1 Kotlin UI DSL实现

```kotlin
class MainSettingsConfigurable : Configurable {
    private lateinit var panel: JPanel
    private val configManager = ConfigManager.getInstance()

    override fun createComponent(): JPanel {
        panel = panel {
            row("Provider:") {
                comboBox(
                    items = arrayOf("Anthropic", "OpenAI", "DeepSeek"),
                    selectedItem = configManager.config.model.provider,
                    onAction = { /* 更新配置 */ }
                )
            }
            row("Model:") {
                comboBox(
                    items = configManager.getAvailableModels().toTypedArray(),
                    selectedItem = configManager.config.model.model
                )
            }
            row("API Key:") {
                passwordField(
                    value = configManager.config.model.apiKey,
                    columns = 40,
                    onApply = { value ->
                        configManager.updateModel(apiKey = value)
                    }
                )
                button("Test") {
                    onClick = { testApiKey() }
                }
            }
            row {
                checkBox(
                    text = "Enable Hot Reload",
                    selected = configManager.config.ui.enableHotReload,
                    onChange = { configManager.updateUi(enableHotReload = it) }
                )
            }
        }
        return panel
    }

    private fun testApiKey() {
        val apiKey = configManager.config.model.apiKey
        val result = configManager.testApiKey(apiKey)
        if (result) {
            Notifications.Bus.notify(Notification("Success", "API Key有效"))
        } else {
            Notifications.Bus.notify(Notification("Error", "API Key无效"))
        }
    }

    override fun apply() {
        // 保存配置
    }

    override fun reset() {
        // 重置为初始值
    }
}
```

#### 3.5.2 验收标准

| 功能 | 验收条件 | 测试方法 |
|------|----------|----------|
| 配置保存 | 修改设置后重启，设置仍生效 | 重启IDE |
| API测试 | 有效Key提示成功，无效Key提示失败 | 手动测试 |
| 验证拦截 | 空白API Key阻止保存 | 手动测试 |

---

### 3.6 模块6: Daemon.js (Agent SDK 服务进程)

#### 3.6.1 功能概述

daemon.js 是运行在 Node.js 环境下的 HTTP 服务进程，负责：
1. 启动和管理 Claude Agent SDK
2. 暴露 REST API 供 Kotlin 端调用
3. 处理 OAuth Token 管理
4. 管理 MCP 服务器生命周期
5. 将 SDK 响应转换为 SSE 格式

#### 3.6.2 项目结构

```
ccgui/
├── daemon/
│   ├── package.json          # Node.js 依赖
│   ├── tsconfig.json         # TypeScript 配置
│   ├── src/
│   │   ├── index.ts          # 入口文件
│   │   ├── server.ts         # HTTP 服务器
│   │   ├── routes/
│   │   │   ├── session.ts    # 会话路由
│   │   │   ├── oauth.ts      # OAuth 路由
│   │   │   └── mcp.ts        # MCP 路由
│   │   ├── services/
│   │   │   ├── agent-sdk.ts  # Agent SDK 封装
│   │   │   ├── sse.ts        # SSE 工具
│   │   │   └── mcp.ts        # MCP 管理
│   │   └── types/
│   │       └── index.ts      # 类型定义
│   └── dist/                 # 编译输出
```

#### 3.6.3 核心实现

**index.ts 入口**：
```typescript
import { createServer } from './server';
import { AgentSdkService } from './services/agent-sdk';
import { McpService } from './services/mcp';

async function main() {
    const port = parseInt(process.env.PORT || '9229');

    // 初始化服务
    const agentSdk = new AgentSdkService();
    const mcpService = new McpService();

    // 创建服务器
    const app = createServer({
        agentSdk,
        mcpService,
        apiKey: process.env.API_KEY
    });

    // 启动监听
    app.listen(port, () => {
        console.log(`Claude Agent daemon running on port ${port}`);
    });
}

main().catch(console.error);
```

**server.ts HTTP 服务**：
```typescript
import express, { Express, Request, Response } from 'express';
import { AgentSdkService } from './services/agent-sdk';
import { McpService } from './services/mcp';
import sessionRoutes from './routes/session';
import oauthRoutes from './routes/oauth';
import mcpRoutes from './routes/mcp';

export interface ServerDeps {
    agentSdk: AgentSdkService;
    mcpService: McpService;
    apiKey?: string;
}

export function createServer(deps: ServerDeps): Express {
    const app = express();
    app.use(express.json());

    // 健康检查
    app.get('/api/v1/health', (req, res) => {
        res.json({ status: 'ok', uptime: process.uptime() });
    });

    // 路由
    app.use('/api/v1/session', sessionRoutes(deps));
    app.use('/api/v1/oauth', oauthRoutes(deps));
    app.use('/api/v1/mcp', mcpRoutes(deps));

    return app;
}
```

**services/agent-sdk.ts SDK 封装**：
```typescript
import Anthropic from '@anthropic-ai/claude-agent-sdk';

export class AgentSdkService {
    private client: Anthropic | null = null;
    private session: any = null;

    async initialize(apiKey: string): Promise<void> {
        this.client = new Anthropic({ apiKey });
    }

    async startSession(config: {
        model?: string;
        systemPrompt?: string;
        mcpServers?: string[];
    }): Promise<{ sessionId: string; supportedTools: string[] }> {
        if (!this.client) {
            throw new Error('SDK not initialized');
        }

        this.session = await this.client.sessions.create({
            model: config.model || 'claude-sonnet-4-20250514',
            systemPrompt: config.systemPrompt,
            tools: config.mcpServers || []
        });

        return {
            sessionId: this.session.id,
            supportedTools: this.session.tools?.map(t => t.name) || []
        };
    }

    async sendMessage(
        content: string,
        attachments: Attachment[] = [],
        onChunk: (delta: string) => void
    ): Promise<{ usage: Usage }> {
        if (!this.session) {
            throw new Error('No active session');
        }

        const message = await this.session.send({
            content,
            attachments
        }, {
            onChunk: (chunk) => {
                if (chunk.type === 'content_block_delta') {
                    onChunk(chunk.delta.text);
                }
            }
        });

        return {
            usage: {
                inputTokens: message.usage.input_tokens,
                outputTokens: message.usage.output_tokens
            }
        };
    }

    async cancelCurrentRequest(): Promise<void> {
        // SDK 取消支持
    }

    async endSession(): Promise<void> {
        if (this.session) {
            await this.session.end();
            this.session = null;
        }
    }
}
```

**routes/session.ts 会话路由**：
```typescript
import { Router, Request, Response } from 'express';
import { ServerDeps } from '../server';

export default function sessionRoutes(deps: ServerDeps): Router {
    const router = Router();
    const sessions = new Map<string, { status: string }>();

    // POST /session/start - 启动新会话
    router.post('/start', async (req: Request, res: Response) => {
        try {
            const { sessionId, supportedTools } = await deps.agentSdk.startSession({
                model: req.body.model,
                systemPrompt: req.body.systemPrompt,
                mcpServers: req.body.mcpServers
            });

            sessions.set(sessionId, { status: 'ready' });

            res.json({ sessionId, status: 'ready', supportedTools });
        } catch (error: any) {
            res.status(500).json({ error: error.message });
        }
    });

    // POST /session/:id/message - 发送消息（SSE）
    router.post('/:id/message', async (req: Request, res: Response) => {
        const sessionId = req.params.id;

        // 设置 SSE headers
        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');

        const sendEvent = (event: string, data: object) => {
            res.write(`event: ${event}\n`);
            res.write(`data: ${JSON.stringify(data)}\n\n`);
        };

        try {
            sendEvent('message_start', { messageId: `msg_${Date.now()}` });

            const result = await deps.agentSdk.sendMessage(
                req.body.content,
                req.body.attachments || [],
                (delta) => {
                    sendEvent('content_block_delta', { delta: { text: delta } });
                }
            );

            sendEvent('message_stop', {});
            sendEvent('usage', result.usage);

            res.end();
        } catch (error: any) {
            sendEvent('error', { message: error.message });
            res.end();
        }
    });

    // POST /session/:id/cancel - 取消请求
    router.post('/:id/cancel', async (req: Request, res: Response) => {
        try {
            await deps.agentSdk.cancelCurrentRequest();
            res.json({ success: true });
        } catch (error: any) {
            res.status(500).json({ error: error.message });
        }
    });

    // POST /session/:id/end - 结束会话
    router.post('/:id/end', async (req: Request, res: Response) => {
        try {
            await deps.agentSdk.endSession();
            sessions.delete(req.params.id);
            res.json({ success: true });
        } catch (error: any) {
            res.status(500).json({ error: error.message });
        }
    });

    return router;
}
```

#### 3.6.4 验收标准

| 功能 | 验收条件 | 测试方法 |
|------|----------|----------|
| 健康检查 | `curl http://localhost:9229/api/v1/health` 返回 200 | 命令行测试 |
| 启动会话 | POST /session/start 返回 sessionId | `curl -X POST ...` |
| 发送消息 | POST /session/{id}/message 返回 SSE 流 | 观察输出 |
| 取消请求 | POST /session/{id}/cancel 停止流式 | 手动测试 |
| 结束会话 | POST /session/{id}/end 清理资源 | 进程监控 |

---

## 4. 开发计划（MVP可执行版）

### Phase 1: MVP核心（8周）

**目标**：跑通端到端聊天链路（Agent SDK 方案）

#### Sprint 1: Daemon.js 搭建（2周）

| Task | 描述 | 验收标准 | 状态 |
|------|------|----------|------|
| T1.1 | Node.js 项目初始化 | package.json, tsconfig.json 配置完成 | ⬜ |
| T1.2 | Express HTTP 服务器 | `/health` 接口返回 200 | ⬜ |
| T1.3 | Agent SDK 集成 | SDK 可正常初始化 | ⬜ |
| T1.4 | 会话管理 API | `/session/start`, `/session/:id/message` 可用 | ⬜ |
| T1.5 | SSE 流式输出 | 消息响应以 SSE 格式返回 | ⬜ |

**Milestone**: `curl -X POST http://localhost:9229/api/v1/session/start` 成功返回 sessionId

#### Sprint 2: AgentSdkBridge 实现（2周）

| Task | 描述 | 验收标准 | 状态 |
|------|------|----------|------|
| T2.1 | 进程启动管理 | Kotlin 可启动 daemon.js 进程 | ⬜ |
| T2.2 | HTTP 客户端 | Ktor/OkHttp 调用 daemon API | ⬜ |
| T2.3 | SSE 解析 | Kotlin 解析 SSE 事件 | ⬜ |
| T2.4 | 流式回调 | `onChunk`, `onComplete` 回调正确 | ⬜ |

**Milestone**: Kotlin 端可调用 daemon API 并接收 SSE 流

#### Sprint 3: 聊天功能实现（2周）

| Task | 描述 | 验收标准 | 状态 |
|------|------|----------|------|
| T3.1 | ChatOrchestrator 改造 | 使用 AgentSdkBridge | ⬜ |
| T3.2 | 流式输出 | 打字机效果 | ⬜ |
| T3.3 | cancelStreaming 中断 | 点击停止后立即停止 | ⬜ |
| T3.4 | Markdown渲染 | 代码块高亮 | ⬜ |

**Milestone**: 用户输入"你好" → 收到AI回复

#### Sprint 4: 配置与设置（2周）

| Task | 描述 | 验收标准 | 状态 |
|------|------|----------|------|
| T4.1 | Settings页面 | 配置可修改 | ⬜ |
| T4.2 | 模型切换 | 切换后用新模型 | ⬜ |
| T4.3 | 主题切换 | 预设主题可用 | ⬜ |
| T4.4 | 快捷键注册 | Ctrl+Shift+C 打开窗口 | ⬜ |

**Milestone**: MVP完成，可发布内部测试版

### Phase 2: 多会话增强（6周）

**目标**：多会话管理和高级交互

| 功能 | 描述 | Sprint |
|------|------|--------|
| 多会话 | 10并发会话，Tab切换 | Sprint 5 |
| 会话持久化 | 保存/恢复历史 | Sprint 5 |
| 提示词优化 | 用户触发的模板优化 | Sprint 6 |
| 代码快捷操作 | 7种AnAction | Sprint 6 |
| 历史检索 | 简单关键词搜索 | Sprint 7 |
| 任务进度条 | 简单进度显示 | Sprint 7 |

### Phase 3: 生态集成（6周）

**目标**：Skills/Agents/MCP

| 功能 | 描述 | Sprint |
|------|------|--------|
| Skills管理器 | CRUD + 执行 | Sprint 8 |
| Agents管理器 | 3种模式 | Sprint 8-9 |
| MCP服务器 | 连接管理 | Sprint 9-10 |
| 自定义主题 | 颜色编辑器 | Sprint 10 |
| 导出/导入 | 配置迁移 | Sprint 10 |

---

## 5. 接口契约（MVP必须遵循）

### 5.1 Java → JS 事件清单

| 事件名 | 数据结构 | 触发时机 |
|--------|----------|----------|
| `messageReceived` | `{id, content, role}` | 单次消息返回 |
| `streamingChunk` | `{chunk}` | 流式片段 |
| `streamingComplete` | `{}` | 流式结束 |
| `streamingError` | `{message}` | 流式异常 |
| `configChanged` | `AppConfig` | 配置更新 |
| `error` | `{message}` | 通用错误 |

### 5.2 JS → Java 调用清单

| 方法名 | 参数 | 返回值 | 说明 |
|--------|------|--------|------|
| `sendMessage` | `text: string` | `Promise<Result>` | 单次发送 |
| `streamMessage` | `text: string` | `void` | 流式发送 |
| `cancelStreaming` | `void` | `void` | 中断流式 |
| `getConfig` | `void` | `Promise<Config>` | 获取配置 |
| `updateConfig` | `Partial<Config>` | `Promise<void>` | 更新配置 |

### 5.3 错误码定义

| 错误码 | 说明 | 用户提示 |
|--------|------|----------|
| `AUTH_FAILED` | API Key无效 | "API Key验证失败，请检查" |
| `NETWORK_ERROR` | 网络异常 | "网络连接失败，请重试" |
| `RATE_LIMIT` | 限流 | "请求过于频繁，请稍后" |
| `MODEL_UNAVAILABLE` | 模型不可用 | "当前模型不可用，请切换" |
| `CONTENT_BLOCKED` | 内容被阻止 | "回复内容被过滤" |
| `UNKNOWN` | 未知错误 | "发生未知错误: {detail}" |

---

## 6. 验收标准（可测试版）

### 6.1 MVP功能验收（Phase 1完成标准）

#### 必须通过（M0）

| # | 功能 | 测试步骤 | 预期结果 | 通过标准 |
|---|------|----------|----------|----------|
| F01 | 发送消息 | 1.输入"你好" 2.点击发送 | 2秒内收到AI回复 | ✅ |
| F02 | 流式输出 | 发送长问题 | 文字逐字显示 | ✅ |
| F03 | 中断流式 | 流式输出时点击停止 | 立即停止 | ✅ |
| F04 | API Key保存 | 设置页面输入Key，关闭IDE | 重开后Key仍存在 | ✅ |
| F05 | 模型切换 | Settings切换模型 | 新消息用新模型 | ✅ |
| F06 | 主题切换 | Settings切换主题 | UI立即更新 | ✅ |
| F07 | 快捷键 | Ctrl+Shift+C | 打开ToolWindow | ✅ |
| F08 | 代码高亮 | 让AI写Python代码 | 有语法高亮 | ✅ |

#### 应该通过（M1）

| # | 功能 | 测试步骤 | 预期结果 | 通过标准 |
|---|------|----------|----------|----------|
| F09 | 错误提示 | 输入无效Key | 显示明确错误 | ✅ |
| F10 | 消息历史 | 发送多条消息 | 历史可滚动 | ✅ |
| F11 | Token计数 | 发送消息后 | 显示消耗数量 | ✅ |
| F12 | 空输入拦截 | 点击发送（空白） | 无响应 | ✅ |

### 6.2 性能验收

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| ToolWindow首次打开 | < 1.5s | 计时器 |
| 消息响应（首次） | < 3s | 计时器 |
| 流式首字延迟 | < 800ms | 计时器 |
| 配置保存 | < 100ms | 计时器 |
| 内存占用（空闲） | < 300MB | 内存profiler |

### 6.3 兼容性验收

| 环境 | 最低版本 | 测试项 |
|------|----------|--------|
| IntelliJ IDEA | 2022.3 | 全功能 |
| PyCharm | 2023.1 | 全功能 |
| WebStorm | 2023.1 | 全功能 |
| Windows | 10 | 全功能 |
| macOS | 12 | 全功能 |
| Linux | Ubuntu 20.04 | 全功能 |

---

## 7. 已知限制与降级策略

### 7.1 MVP范围外的功能（暂不实现）

| 功能 | 原计划 | 降级方案 |
|------|--------|----------|
| 交互式请求引擎 | AI主动提问 | 用户主动触发的选择式操作 |
| 多会话搜索 | Lucene全文检索 | 简单内存过滤 |
| 任务进度解析 | AI自动分解 | 简单进度条 |
| MCP服务器 | 完整管理 | Phase 3 |
| Skills/Agents | 可视化配置 | Phase 3 |

### 7.2 JCEF降级策略

```kotlin
val isJcefAvailable: Boolean = try {
    JBCefApp.getInstance()
    true
} catch (e: Throwable) {
    false
}

if (isJcefAvailable) {
    // 使用JCEF聊天界面
    ChatToolWindow.showJcefChat()
} else {
    // 降级到Swing提示
    ChatToolWindow.showSwingFallback()
    Notification(
        "JCEF不可用",
        "建议升级IDEA到2022.3+以获得完整功能",
        NotificationType.WARNING
    ).notify()
}
```

### 7.3 流式降级策略

```kotlin
try {
    // 先尝试流式
    streamChat(request).collect { chunk ->
        emit(chunk)
    }
} catch (e: StreamException) {
    // 流式失败，降级到普通调用
    val response = chat(request)
    emitAll(flowOf(response.content))
}
```

---

## 8. 团队职责与协作

### 8.1 开发分工建议

| 角色 | 职责 | 人员 |
|------|------|------|
| Tech Lead | 架构设计、JCEF集成 | 1人 |
| Backend Dev | Provider实现、Config管理 | 1人 |
| Frontend Dev | React组件、状态管理 | 1人 |
| QA | 测试用例、自动化 | 1人（可兼职） |

### 8.2 代码所有权

| 模块 | 负责人 | Reviewer |
|------|--------|----------|
| ChatOrchestrator | Backend | Tech Lead |
| AgentSdkBridge | Backend | Tech Lead |
| CefBrowserPanel | Tech Lead | Frontend |
| React ChatWindow | Frontend | Tech Lead |
| ConfigManager | Backend | Tech Lead |
| daemon.js | Frontend/Node.js | Tech Lead |

### 8.3 协作规则

1. **每日站会**：15分钟，同步进度和阻塞
2. **PR Required**：所有代码必须经过Review才能合并
3. **测试要求**：核心模块单元测试覆盖率 > 80%
4. **文档更新**：接口变更必须同步更新本文档

---

## 9. 文档维护

### 9.1 变更流程

1. 提出变更 → 在GitHub Issue描述
2. 评审 → Team Review（影响范围评估）
3. 批准 → Tech Lead批准
4. 更新 → 更新本PRD
5. 通知 → 团队通知

### 9.2 版本历史

| 版本 | 日期 | 变更说明 | 作者 |
|------|------|----------|------|
| v3.0 | 2026-04-08 | 企业级完整版 | 产品团队 |
| v3.1 | 2026-04-10 | 开发驱动版，优化MVP边界 | 架构评审 |

---

**End of PRD v3.1**
