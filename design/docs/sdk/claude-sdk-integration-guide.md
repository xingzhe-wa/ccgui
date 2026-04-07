# Claude Agent SDK 集成指南

> 本文档详细说明如何将 Claude Agent SDK 集成到任意 IDE 插件项目中。以 JetBrains IDEA 插件 [jetbrains-cc-gui](https://github.com/zhukunpenglinyutong/jetbrains-cc-gui) 为参考实现，从零讲解架构设计、通信协议、关键代码和集成步骤。

**文档版本**：v1.0.0
**参考项目**：jetbrains-cc-gui
**适用场景**：IDE 插件 / 桌面客户端 / 任何需要从 JVM 语言集成 Claude Code 的场景

---

## 目录

1. [前置概念](#1-前置概念)
2. [整体架构](#2-整体架构)
3. [核心技术：Daemon + NDJSON 进程桥接](#3-核心技术daemon--ndjson-进程桥接)
4. [Layer 1：Node.js 端 — daemon.js](#4-layer-1nodejs-端--daemonjs)
5. [Layer 2：Node.js 端 — SDK 调用](#5-layer-2nodejs-端--sdk-调用)
6. [Layer 3：Java 端 — DaemonBridge](#6-layer-3java-端--daemonbridge)
7. [Layer 4：Java 端 — 消息处理与 UI 集成](#7-layer-4java-端--消息处理与-ui-集成)
8. [从零集成步骤](#8-从零集成步骤)
9. [性能优化策略](#9-性能优化策略)
10. [常见问题排查](#10-常见问题排查)

---

## 1. 前置概念

### 1.1 集成方式对比

在将 Claude 能力接入桌面应用时，有三种主流方案：

| 方案 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **直接 HTTP API** | 直接调用 Anthropic REST API | 简单、无进程开销 | 需要自己实现认证、流式响应、工具调用 |
| **Claude CLI** | `spawn('claude', [...])` 启动 CLI 子进程 | 功能完整、官方维护 | 每次启动有 5-10s 冷启动延迟 |
| **Agent SDK** | 在进程中加载 `@anthropic-ai/claude-agent-sdk` | 功能完整 + 可常驻进程 | 需要 Node.js 运行时、需进程间通信 |

### 1.2 为什么选择 Agent SDK + Daemon 模式？

```
┌──────────────────────────────────────────────────────────┐
│  Agent SDK 的两个事实：                                    │
│                                                          │
│  1. 它是一个纯 JavaScript/TypeScript npm 包               │
│     → 没有 HTTP 服务模式，不能被 Java/Kotlin 直接调用      │
│     → 必须通过 Node.js 进程托管                            │
│                                                          │
│  2. 它没有 Java/JVM 绑定（GraalVM、JNI 等）               │
│     → Java 无法直接 import 使用                           │
│     → 必须通过进程间通信 (IPC)                             │
│                                                          │
│  因此：Java 侧必须启动一个 Node.js 进程，                 │
│       通过 stdin/stdout 与其通信，才能调用 SDK。           │
└──────────────────────────────────────────────────────────┘
```

### 1.3 性能瓶颈：Per-Process vs Daemon

```
Per-Process 模式（每次请求启动新进程）：
┌────────┐    ┌─────────────┐    ┌──────────┐    ┌──────────┐
│  Java  │───▶│ 启动Node.js │───▶│ 加载SDK   │───▶│ 调用API  │
│        │    │  (~1-2s)   │    │ (~3-5s)  │    │ (可接受) │
└────────┘    └─────────────┘    └──────────┘    └──────────┘
  请求1         每次都要重复这套流程
  请求2         每次都要重复这套流程
  ...

总延迟：10-15秒/请求
```

```
Daemon 模式（单进程常驻）：
┌─────────────────────────────────────────────────────────┐
│  启动时（一次性）                                          │
│  ┌────────┐    ┌─────────────┐    ┌──────────┐          │
│  │  Java  │───▶│ 启动Node.js │───▶│ 预加载SDK │(3-5s,仅1次)│
│  └────────┘    └─────────────┘    └──────────┘          │
│                                                         │
│  后续请求（毫秒级）                                         │
│  ┌────────┐    ┌──────────────────────────────┐         │
│  │  Java  │◀──▶│ 已有热进程 + NDJSON通信       │         │
│  └────────┘    └──────────────────────────────┘         │
│                  < 1秒/请求                              │
└─────────────────────────────────────────────────────────┘
```

---

## 2. 整体架构

### 2.1 四层架构总览

```
┌────────────────────────────────────────────────────────────────────┐
│                        Layer 4: Java / IDE UI                       │
│                                                                     │
│   ClaudeSession          ClaudeMessageHandler        Webview        │
│   (会话管理)     ───▶    (消息解析)         ───▶    (前端展示)       │
└───────────────────────────────┬────────────────────────────────────┘
                                │ CompletableFuture<Boolean>
                                │ Tagged output lines
┌───────────────────────────────▼────────────────────────────────────┐
│                      Layer 3: Java / DaemonBridge                    │
│                                                                     │
│   DaemonBridge                                                    │
│   ├─ ProcessBuilder 启动 node daemon.js                            │
│   ├─ stdin (写入 NDJSON 请求)                                      │
│   ├─ stdout reader (读取 NDJSON 响应，分派到 pendingRequests)       │
│   ├─ heartbeat thread (每15秒健康检查)                              │
│   └─ auto-restart (崩溃后最多重试3次)                              │
└───────────────────────────────┬────────────────────────────────────┘
                                │ stdin/stdout (NDJSON)
                                │ 进程管道（Process pipes）
┌───────────────────────────────▼────────────────────────────────────┐
│                   Layer 1: Node.js / Daemon Process                  │
│                                                                     │
│   daemon.js                                                        │
│   ├─ SDK 预加载 (启动时一次性)                                      │
│   ├─ stdout/stderr 拦截 (为每行输出打上 request ID 标签)             │
│   ├─ process.exit 拦截 (防止 SDK 意外退出进程)                      │
│   ├─ 请求队列 (串行化命令请求，心跳可并发)                           │
│   └─ 环境变量隔离 (每个请求的环境变量隔离)                           │
└───────────────────────────────┬────────────────────────────────────┘
                                │ sdk.query()
                                │ AsyncStream / AsyncGenerator
┌───────────────────────────────▼────────────────────────────────────┐
│                   Layer 2: Node.js / Claude SDK                      │
│                                                                     │
│   @anthropic-ai/claude-agent-sdk                                    │
│   ├─ sdk.query({ prompt, options }) 返回 AsyncGenerator            │
│   ├─ 处理 OAuth 登录、API 认证、模型路由                             │
│   ├─ 工具调用 (Read/Write/Edit/Bash 等)                            │
│   └─ MCP 工具支持 (通过 SDK 内置能力)                               │
└────────────────────────────────────────────────────────────────────┘
```

### 2.2 关键设计决策

| 决策 | 说明 | 为什么重要 |
|------|------|-----------|
| **NDJSON over stdin/stdout** | 每行一个完整 JSON 对象 | 简单、可靠、天然支持流式 |
| **请求 ID 标签** | 所有 SDK 输出都打上 `{id: "X", line: "..."}` | Java 侧能精准路由响应到哪个请求 |
| **stdout 拦截** | 覆写 `process.stdout.write` | SDK 内部 `console.log` 无法修改，但可拦截输出 |
| **process.exit 拦截** | 将 `process.exit()` 转为 `throw` | SDK 内部错误处理可能调用 exit，必须阻止 |
| **Runtime 池** | 按 sessionId 复用 SDK query 对象 | 多轮对话复用同一 runtime，减少开销 |
| **心跳机制** | 15秒一次，45秒无响应判定死亡 | 检测 daemon 挂起/网络断开/SDK 内部错误 |
| **自动重启** | 30秒窗口内最多3次 | 瞬时故障自动恢复，不影响用户体验 |

---

## 3. 核心技术：Daemon + NDJSON 进程桥接

### 3.1 NDJSON 协议格式

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
{"id":"1","line":"[CONTENT_DELTA] \"Hello, "}
{"id":"1","line":"[CONTENT_DELTA] \"world!\""}
{"id":"1","line":"[TOOL_USE] {\"id\":\"tool_1\",\"name\":\"Read\"}"}
{"id":"1","line":"[TOOL_RESULT] {\"tool_use_id\":\"tool_1\"}"}
{"id":"1","line":"[SESSION_ID] sess_abc123"}
{"id":"1","line":"[USAGE] {\"output_tokens\":120,\"input_tokens\":80}"}
{"id":"1","line":"[MESSAGE_END]"}
{"id":"1","done":true,"success":true}

{"id":"2","type":"heartbeat","ts":1234567890}
```

### 3.2 消息标签体系

SDK 的所有输出通过标签（tag）编码，Java 侧解析标签决定如何处理：

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

### 3.3 stdout 拦截原理

SDK 内部使用 `console.log()` 输出消息标签，但 SDK 本身不能修改。因此 daemon.js 通过拦截 `process.stdout.write` 来打标签：

```
JavaScript 调用链：
  sdk.query({ prompt, options })
    └─ 内部调用 console.log('[CONTENT_DELTA] "Hello"')
         │
         ▼
    process.stdout.write('...')
         │
         ▼
    daemon.js 拦截函数：
      if (activeRequestId) {
        // 有活跃请求 → 包装为 {"id":"X","line":"原始输出"}
        writeRawLine({ id: activeRequestId, line });
      } else {
        // 无活跃请求 → 检查是否是 daemon 自身事件
        if (trimmed.startsWith('{')) {
          _originalStdoutWrite(chunk);  // 直接透传
        } else {
          // SDK debug 日志 → 包装为 daemon 日志事件
          writeRawLine({ type: 'daemon', event: 'log', message: line });
        }
      }
```

---

## 4. Layer 1：Node.js 端 — daemon.js

### 4.1 文件位置与职责

```
ai-bridge/
└── daemon.js          ← 入口文件，可直接执行
```

**daemon.js 职责矩阵：**

| 职责 | 具体实现 |
|------|---------|
| SDK 预加载 | `await preloadSdks()` 在 startup 阶段调用 |
| stdout 拦截 | 覆写 `process.stdout.write`，按 request ID 打标签 |
| stderr 拦截 | 覆写 `console.error`，按 request ID 发送到 stderr 通道 |
| process.exit 拦截 | 覆写为 `throw`，阻止进程退出 |
| 请求路由 | `processRequest()` 解析 `method` 字段分发到对应 handler |
| 请求队列 | `commandQueue` 串行化命令请求，避免 activeRequestId 冲突 |
| 心跳响应 | `heartbeat` method 直接返回 `{type:'heartbeat', ts}` |
| 优雅退出 | `shutdown` method 清理 runtime 后 `process.exit(0)` |
| 父进程监控 | 每10秒检查 ppid，父进程死亡则自动退出（防止僵尸进程）|

### 4.2 启动流程（伪代码）

```javascript
// daemon.js 启动流程
async function main() {
  // 1. 拦截 stdout/stderr（必须在任何输出之前）
  patchProcessStreams();

  // 2. 拦截 process.exit
  patchProcessExit();

  // 3. 设置网络环境变量（代理/TLS 配置）
  injectNetworkEnvVars();

  // 4. 注册未捕获异常处理器
  setupErrorHandlers();

  // 5. 发送 starting 事件
  sendDaemonEvent('starting', { pid: process.pid, version: DAEMON_VERSION });

  // 6. 预加载 SDK（阻塞，等待完成）
  await preloadSdks();

  // 7. 发送 ready 事件（Java 端等待此事件）
  sendDaemonEvent('ready', { pid: process.pid, sdkPreloaded });

  // 8. 进入请求监听循环
  startRequestLoop();  // rl.on('line', processRequest)
}

main();
```

### 4.3 请求处理流程

```
收到一行 NDJSON: {"id":"1","method":"claude.send","params":{...}}
       │
       ▼
  ┌─────────────┐
  │ method ===  │──yes──▶ 返回心跳响应，跳出
  │ "heartbeat"? │
  └─────┬───────┘
        │ no
       ▼
  ┌─────────────┐
  │ method ===  │──yes──▶ 发送 abort，立即返回
  │   "abort"   │
  └─────┬───────┘
        │ no
        ▼
  ┌───────────────────────────────────────┐
  │ method === "status" / "shutdown"?     │──status──▶ 返回状态，跳出
  │                                       │──shutdown─▶ 清理并退出
  └─────────────┬─────────────────────────┘
                │ no
                ▼
  设置 activeRequestId = id
  保存原环境变量（用于请求结束后恢复）
       │
       ▼
  ┌───────────────────────────────────────┐
  │ 解析 method: "claude.send"           │
  │   provider = "claude"                 │
  │   command   = "send"                  │
  └─────────────┬─────────────────────────┘
                │
                ▼
  ┌───────────────────────────────────────┐
  │ 调用对应服务函数：                     │
  │   claude.send  → sendMessagePersistent()
  │   claude.preconnect → preconnectPersistent()
  │   claude.resetRuntime → resetRuntimePersistent()
  └─────────────┬─────────────────────────┘
                │
                ▼
  服务函数内部通过 process.stdout.write 输出带标签的流
       │
       ▼
  函数返回 → 发送 {"done":true,"success":true}
  捕获异常 → 发送 {"done":true,"success":false,"error":"..."}
       │
       ▼
  清理 activeRequestId
  恢复环境变量
```

---

## 5. Layer 2：Node.js 端 — SDK 调用

### 5.1 SDK 动态加载

SDK 不随插件打包，而是从用户目录按需加载：

```
~/.codemoss/dependencies/
└── claude-sdk/
    └── node_modules/
        └── @anthropic-ai/
            └── claude-agent-sdk/
                └── sdk.mjs         ← SDK 入口文件
```

**sdk-loader.js 加载流程：**

```javascript
// 1. 构造 SDK 包路径
const sdkRootDir = ~/.codemoss/dependencies/claude-sdk
const packageDir = sdkRootDir/node_modules/@anthropic-ai/claude-agent-sdk

// 2. 解析 package.json 的 exports 字段，找到入口文件
const entry = resolveEntryFileFromPackageDir(packageDir)
// 例如: sdk.mjs

// 3. 转为 file:// URL（Node.js ESM 要求）
const resolvedUrl = pathToFileURL(entry).href
// 例如: file:///home/user/.codemoss/dependencies/claude-sdk/node_modules/@anthropic-ai/claude-agent-sdk/sdk.mjs

// 4. 动态 import
const sdk = await import(resolvedUrl)
// sdk = { query: [AsyncFunction] }

// 5. 缓存结果
sdkCache.set('claude', sdk)
return sdk
```

### 5.2 SDK query 调用

SDK 的核心接口是 `query()` 函数，返回一个 AsyncGenerator：

```javascript
// 获取 query 函数
const sdk = await loadClaudeSdk()
const queryFn = sdk.query  // 类型: (options) => AsyncGenerator<Message>

// 构造 options
const options = {
  cwd: '/project',
  model: 'claude-sonnet-4-7-20250611',
  permissionMode: 'default',      // default | bypass | acceptEdits | plan
  maxTurns: 100,
  enableFileCheckpointing: true,
  maxThinkingTokens: 10240,
  includePartialMessages: true,    // 开启流式
  additionalDirectories: ['/project', '/project2'],
  systemPrompt: {
    type: 'preset',
    preset: 'claude_code',
    append: 'You are in an IDE...' // 追加的系统提示
  },
  canUseTool,                      // 工具权限检查回调
  hooks: {
    PreToolUse: [{ hooks: [preToolUseHook] }]
  },
  settingSources: ['user', 'project', 'local'],
}

// 调用 query
const result = queryFn({ prompt: 'Hello', options })

// 遍历流式响应
for await (const msg of result) {
  // msg.type: 'stream_event' | 'assistant' | 'user' | 'system' | 'result'
  processMessage(msg)
}
```

### 5.3 流式消息处理

```
SDK 流式消息 -> message-sender.js 处理 -> process.stdout.write 打标签 -> Java 接收

stream_event (SDK 流式事件):
├─ message_start    → 初始化 usage 累加器
├─ message_delta    → 累加 output_tokens
├─ content_block_delta:
│   ├─ text_delta   → 输出 [CONTENT_DELTA] "文本增量"
│   └─ thinking_delta → 输出 [THINKING_DELTA] "思考增量"
└─ content_block_start (type=thinking) → 输出 [THINKING_START]

assistant (非流式完整消息):
├─ message.content[type=text] → 输出 [CONTENT]
├─ message.content[type=tool_use] → 输出 [TOOL_USE]
└─ message.usage → 输出 [USAGE]

system (元信息):
└─ session_id → 输出 [SESSION_ID]

result (结束):
├─ is_error: true → 抛出异常
└─ is_error: false → 正常结束
```

### 5.4 Runtime 池管理

为了在多轮对话中复用 SDK 连接，实现了 runtime 池：

```
┌─────────────────────────────────────────────────────────────┐
│                      Runtime 池                              │
│                                                              │
│  runtimesBySessionId (Map<sessionId, Runtime>)             │
│  ├─ "sess_abc123" → Runtime(claude-sonnet, cwd=/project)   │
│  └─ "sess_def456" → Runtime(claude-sonnet, cwd=/project2)  │
│                                                              │
│  anonymousRuntimesBySignature (Map<signature, Runtime>)    │
│  ├─ "{cwd:/project,model:...}" → Runtime                   │
│  └─ "{cwd:/project2,model:...}" → Runtime                  │
└─────────────────────────────────────────────────────────────┘
```

**Runtime 生命周期：**

```
创建 Runtime
  ├─ new AsyncStream()          // 输入流
  ├─ sdk.query({prompt: inputStream, options})
  └─ 返回 query AsyncGenerator

执行单轮 (executeTurn)
  ├─ inputStream.enqueue(userMessage)
  ├─ for await (msg of query)
  │   ├─ processStreamMessage() → 输出标签
  │   └─ 捕获 tool_use → 等待结果
  └─ inputStream.done()

保持活跃（等待下一轮）
  ├─ 相同 session → 复用同一 runtime
  ├─ 不同 signature → 销毁并创建新 runtime
  └─ 空闲超时(10分钟) → 自动回收
```

---

## 6. Layer 3：Java 端 — DaemonBridge

### 6.1 文件位置与职责

```
src/main/java/com/github/claudecodegui/provider/common/
└── DaemonBridge.java          ← 核心进程管理类
```

**DaemonBridge 职责矩阵：**

| 职责 | 实现 |
|------|------|
| 进程启动 | `ProcessBuilder(nodePath, daemon.js)` |
| stdin 写入 | `BufferedWriter(OutputStreamWriter(daemonProcess.getOutputStream()))` |
| stdout 读取 | `DaemonBridge-Reader` 线程持续读取 |
| stderr 读取 | `DaemonBridge-Stderr` 线程（仅调试用）|
| 心跳检测 | `DaemonBridge-Heartbeat` 线程，每15秒发送心跳 |
| 响应路由 | `pendingRequests.get(id).callback.onLine()` |
| 自动重启 | `handleDaemonDeath()` 检测后最多重启3次 |
| 优雅停止 | 发送 `shutdown` 命令 → 等待 → `destroyForcibly()` |

### 6.2 进程启动代码

```java
// DaemonBridge.start() 核心逻辑
public boolean start() {
    // 1. 找到 daemon.js 路径
    File bridgeDir = directoryResolver.findSdkDir();
    File daemonScript = new File(bridgeDir, "daemon.js");

    // 2. 找到 Node.js 可执行文件
    String nodePath = nodeDetector.findNodeExecutable();

    // 3. 构造进程
    ProcessBuilder pb = new ProcessBuilder(nodePath, daemonScript.getAbsolutePath());
    pb.directory(bridgeDir);

    // 4. 配置环境变量（代理、路径等）
    Map<String, String> env = pb.environment();
    envConfigurator.updateProcessEnvironment(pb, nodePath);

    // 5. 启动进程
    daemonProcess = pb.start();

    // 6. 获取 stdin writer
    daemonStdin = new BufferedWriter(
        new OutputStreamWriter(daemonProcess.getOutputStream(), StandardCharsets.UTF_8)
    );

    // 7. 启动 stdout 读取线程
    startReaderThread();

    // 8. 启动 stderr 读取线程
    startStderrReaderThread();

    // 9. 等待 "ready" 事件
    boolean ready = awaitReady(30_000);  // 超时30秒
    if (!ready) return false;

    // 10. 启动心跳线程
    startHeartbeatThread();

    return true;
}
```

### 6.3 发送请求代码

```java
public CompletableFuture<Boolean> sendCommand(
        String method,           // e.g., "claude.send"
        JsonObject params,       // e.g., {"message": "Hello", "cwd": "/project"}
        DaemonOutputCallback callback  // 回调接收输出行
) {
    // 1. 确保进程存活
    if (!ensureRunning()) {
        return CompletableFuture.failedFuture(new IOException("Daemon not running"));
    }

    // 2. 生成请求 ID
    String requestId = String.valueOf(requestIdCounter.incrementAndGet());

    // 3. 创建 Future + Handler
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    RequestHandler handler = new RequestHandler(callback, future);
    pendingRequests.put(requestId, handler);

    // 4. 构造 NDJSON 请求
    JsonObject request = new JsonObject();
    request.addProperty("id", requestId);
    request.addProperty("method", method);
    request.add("params", params);

    // 5. 写入 stdin（同步锁保证原子性）
    synchronized (daemonStdin) {
        daemonStdin.write(request.toString());
        daemonStdin.newLine();
        daemonStdin.flush();
    }

    // 6. 返回 Future，调用方 await
    return future;
}
```

### 6.4 线程模型

```
┌──────────────────────────────────────────────────────┐
│              DaemonBridge 线程模型                     │
│                                                       │
│  主线程 (sendCommand)                                  │
│    └─ synchronized(daemonStdin) 写入请求              │
│        ↑                                             │
│        │ (CompletableFuture 异步等待)                 │
│        │                                             │
│  DaemonBridge-Reader (守护线程)                        │
│    └─ BufferedReader.readLine() 循环                  │
│        ├─ parse JSON                                  │
│        ├─ daemon 事件 → handleDaemonEvent()           │
│        ├─ 心跳响应 → 更新时间戳                        │
│        └─ 请求响应 → callback.onLine() + future.complete│
│                                                       │
│  DaemonBridge-Heartbeat (守护线程)                     │
│    └─ 每15秒: 发送心跳 + 检查超时                      │
│        ├─ 正常 → 更新 lastHeartbeatResponse           │
│        └─ 超时(45s) → handleDaemonDeath()            │
│                                                       │
│  DaemonBridge-Stderr (守护线程)                       │
│    └─ 读取 stderr → LOG.debug() (仅调试)             │
└──────────────────────────────────────────────────────┘
```

### 6.5 心跳与故障自愈

```java
// 心跳发送
private void startHeartbeatThread() {
    Thread t = new Thread(() -> {
        while (isRunning.get()) {
            Thread.sleep(15_000);  // 15秒

            // 发送心跳
            daemonStdin.write("{\"id\":\"hb-\" + ts, \"method\":\"heartbeat\"}\n");
            daemonStdin.flush();

            // 检查超时
            long heartbeatAge = now - lastHeartbeatResponse.get();
            if (heartbeatAge > 45_000) {
                // 3次心跳无响应 → 判定死亡
                handleDaemonDeath();
            }
        }
    });
}

// 死亡处理
private void handleDaemonDeath() {
    // 1. 标记停止
    isRunning.set(false);

    // 2. 杀掉残留进程
    daemonProcess.destroyForcibly();

    // 3. 所有 pending 请求标记失败
    for (entry : pendingRequests) {
        entry.getValue().onError("Daemon died");
    }
    pendingRequests.clear();

    // 4. 重启计数
    int attempts = restartAttempts.incrementAndGet();
    if (attempts <= 3) {
        start();  // 自动重启
    } else {
        // 放弃重启，Java 侧将回退到 per-process 模式
    }
}
```

---

## 7. Layer 4：Java 端 — 消息处理与 UI 集成

### 7.1 ClaudeMessageHandler：标签解析

```java
public class ClaudeMessageHandler implements MessageCallback {

    @Override
    public void onMessage(String type, String content) {
        switch (type) {
            case "stream_start":
                // 流式开始：通知前端
                isStreaming = true;
                callbackHandler.notifyStreamStart();
                break;

            case "content_delta":
                // 文本增量：追加并推送前端
                assistantContent.append(content);
                currentAssistantMessage.content = assistantContent.toString();
                callbackHandler.notifyContentDelta(content);
                callbackHandler.notifyMessageUpdate(state.getMessages());
                break;

            case "thinking_delta":
                // 思考增量
                callbackHandler.notifyThinkingDelta(content);
                break;

            case "tool_use":
                // 工具调用：显示工具块
                handleToolUse(content);
                break;

            case "tool_result":
                // 工具结果：添加到消息列表
                handleToolResult(content);
                break;

            case "session_id":
                // 会话 ID：保存用于会话恢复
                state.setSessionId(content);
                callbackHandler.notifySessionIdReceived(content);
                break;

            case "usage":
                // Token 用量：更新状态栏
                handleUsage(content);
                break;

            case "stream_end":
                // 流式结束
                isStreaming = false;
                callbackHandler.notifyStreamEnd();
                break;

            case "error":
                // 错误：显示通知
                onError(content);
                break;
        }
    }

    @Override
    public void onComplete(SDKResult result) {
        // 所有消息处理完毕，清理状态
        isStreaming = false;
        state.setBusy(false);
        state.setLoading(false);
        callbackHandler.notifyStateChange(...);
    }
}
```

### 7.2 三阶段预热策略

为了保证用户首次提问也能低延迟，Java 侧在三个时机触发预热：

```
时机1: 插件启动
  WebviewInitializer.java
    └─ prewarmDaemonAsync(projectPath)
        → 启动 daemon + 加载 SDK + 创建匿名 runtime

时机2: 新建会话
  SessionLifecycleManager.java
    └─ prewarmDaemonAsync(workingDirectory)
        → 确认 daemon 存活 + 必要时补充 runtime

时机3: 首轮对话完成
  ClaudeSession.java
    └─ whenComplete { prewarmDaemonAsync(cwd) }
        → 补充被消耗的匿名 runtime
```

**预热时序：**

```
插件启动 ──▶ daemon 启动 ──▶ SDK 加载(3-5s) ──▶ 匿名runtime就绪
                                                        │
用户发消息 ──▶ 命中匿名runtime ──▶ <1s响应 ──▶ 补充匿名runtime
                                                        │
下一条消息 ──▶ 命中session runtime ──▶ <1s响应 ──▶ ...
```

### 7.3 会话恢复（Resume）

用户下次打开相同项目时，daemon 可以恢复之前的会话：

```java
// 发送消息时携带 sessionId
JsonObject params = new JsonObject();
params.addProperty("sessionId", sessionId);
params.addProperty("message", userMessage);

// daemon.js 中
if (resumeSessionId && resumeSessionId !== '') {
    options.resume = resumeSessionId;  // 传给 sdk.query()
}

// SDK 读取 ~/.claude/projects/{projectId}/sessions/{sessionId}.jsonl
// 恢复完整的对话历史，无需重新发送所有消息
```

---

## 8. 从零集成步骤

### 步骤概览

```
Step 1: 准备 Node.js 运行时环境
Step 2: 创建 Java 侧项目结构
Step 3: 实现 DaemonBridge（进程管理 + NDJSON通信）
Step 4: 实现 daemon.js（Node.js 入口 + SDK 加载）
Step 5: 实现消息标签解析与 UI 集成
Step 6: 实现 SDK 按需安装（DependencyManager）
Step 7: 集成测试与调优
```

### Step 1: 准备 Node.js 运行时

Java 侧需要能够找到 Node.js 可执行文件：

```java
// NodeDetector.java
public class NodeDetector {
    public String findNodeExecutable() {
        // 1. 优先从 PATH 查找
        String node = findInPath("node");
        if (node != null) return node;

        // 2. 常见安装路径（Windows）
        String[] windowsPaths = {
            System.getenv("ProgramFiles") + "\\nodejs\\node.exe",
            System.getenv("ProgramFiles(x86)") + "\\nodejs\\node.exe",
            System.getProperty("user.home") + "\\AppData\\Roaming\\npm\\node.exe"
        };

        // 3. Unix 常见路径
        String[] unixPaths = {
            "/usr/local/bin/node",
            "/usr/bin/node",
            "/opt/homebrew/bin/node"
        };

        for (String path : paths) {
            if (new File(path).exists()) return path;
        }

        return null;  // 未找到，提示用户安装
    }
}
```

### Step 2: 创建 Java 侧项目结构

```
src/main/java/com/yourplugin/
├── provider/
│   └── common/
│       ├── DaemonBridge.java          ← 核心：进程管理 + IPC
│       ├── MessageCallback.java        ← 回调接口
│       ├── SDKResult.java             ← 结果封装
│       └── BridgeDirectoryResolver.java
├── session/
│   ├── ClaudeSession.java             ← 会话管理
│   └── ClaudeMessageHandler.java      ← 消息标签解析
├── bridge/
│   ├── NodeDetector.java              ← 找 Node.js
│   └── EnvironmentConfigurator.java   ← 配置环境变量
└── dependency/
    └── DependencyManager.java         ← SDK 按需安装
```

### Step 3: 实现 DaemonBridge

**DaemonBridge.java 核心模板：**

```java
public class DaemonBridge {

    private Process daemonProcess;
    private BufferedWriter daemonStdin;
    private Thread readerThread;
    private volatile boolean isRunning = false;

    // === 启动 ===
    public boolean start() {
        try {
            String nodePath = nodeDetector.findNodeExecutable();
            File daemonScript = new File(bridgeDir, "daemon.js");

            ProcessBuilder pb = new ProcessBuilder(nodePath, daemonScript.getAbsolutePath());
            pb.directory(bridgeDir);
            envConfigurator.updateProcessEnvironment(pb, nodePath);

            daemonProcess = pb.start();
            isRunning = true;

            // stdin writer
            daemonStdin = new BufferedWriter(
                new OutputStreamWriter(daemonProcess.getOutputStream(), UTF_8)
            );

            // stdout reader
            readerThread = new Thread(this::readLoop, "Daemon-Reader");
            readerThread.setDaemon(true);
            readerThread.start();

            // 等待 ready
            return awaitReady(30_000);

        } catch (Exception e) {
            LOG.error("Daemon start failed", e);
            return false;
        }
    }

    // === 发送请求 ===
    public CompletableFuture<Boolean> sendCommand(
            String method, JsonObject params, Callback callback) {

        String requestId = String.valueOf(requestIdCounter.incrementAndGet());

        // 构建请求
        JsonObject request = new JsonObject();
        request.addProperty("id", requestId);
        request.addProperty("method", method);
        request.add("params", params);

        // 写入 stdin
        synchronized (daemonStdin) {
            daemonStdin.write(request.toString());
            daemonStdin.newLine();
            daemonStdin.flush();
        }

        return future;  // future 在 readLoop 中 complete
    }

    // === 读取响应 ===
    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(daemonProcess.getInputStream(), UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            if (isRunning) LOG.error("Reader error", e);
        }
        handleDaemonDeath();
    }

    // === 解析 NDJSON ===
    private void handleLine(String jsonLine) {
        JsonObject obj = JsonParser.parseString(jsonLine).getAsJsonObject();

        if (obj.has("type") && "daemon".equals(obj.get("type").getAsString())) {
            // 生命周期事件
            handleDaemonEvent(obj);
            return;
        }

        String id = obj.get("id").getAsString();

        if (obj.has("done")) {
            // 请求完成
            boolean success = obj.get("success").getAsBoolean();
            pendingRequests.get(id).complete(success);
            return;
        }

        if (obj.has("line")) {
            // 输出行：解析标签并回调
            String tagLine = obj.get("line").getAsString();
            parseAndDispatch(tagLine, pendingRequests.get(id).callback);
        }
    }
}
```

### Step 4: 实现 daemon.js

**ai-bridge/daemon.js 模板：**

```javascript
#!/usr/bin/env node
/**
 * Claude SDK Daemon
 * 长驻 Node.js 进程，通过 NDJSON 与 Java 通信
 */

// === 拦截 stdout ===
const _originalWrite = process.stdout.write.bind(process.stdout);
let activeRequestId = null;

process.stdout.write = function(chunk, encoding, callback) {
    const text = typeof chunk === 'string' ? chunk : chunk.toString('utf8');

    if (activeRequestId) {
        // 有活跃请求：打标签
        for (const line of text.split('\n')) {
            if (line.length > 0) {
                _originalWrite(JSON.stringify({ id: activeRequestId, line }) + '\n');
            }
        }
        if (typeof callback === 'function') callback();
        return true;
    }

    // 无活跃请求：透传（daemon 事件）或包装为日志
    if (text.trim().startsWith('{')) {
        return _originalWrite(chunk, encoding, callback);
    }
    if (text.trim().length > 0) {
        for (const line of text.split('\n')) {
            _originalWrite(JSON.stringify({ type: 'daemon', event: 'log', message: line }) + '\n');
        }
    }
    if (typeof callback === 'function') callback();
    return true;
};

// === 拦截 process.exit ===
process.exit = function(code) {
    if (isDaemonMode) {
        if (activeRequestId) {
            _originalWrite(JSON.stringify({
                id: activeRequestId, done: true, success: code === 0
            }) + '\n');
        }
        throw new Error(`process.exit(${code}) intercepted`);
    }
};

// === 加载 SDK ===
async function preloadSdks() {
    const { loadClaudeSdk } = await import('./utils/sdk-loader.js');
    const sdk = await loadClaudeSdk();
    sdkPreloaded = true;
    _originalWrite(JSON.stringify({
        type: 'daemon', event: 'sdk_loaded', provider: 'claude'
    }) + '\n');
    return sdk;
}

// === 请求处理 ===
async function processRequest(request) {
    const { id, method, params = {} } = request;
    activeRequestId = id;

    try {
        if (method === 'heartbeat') {
            _originalWrite(JSON.stringify({ id, type: 'heartbeat', ts: Date.now() }) + '\n');
            return;
        }

        if (method === 'claude.send') {
            const { sendMessage } = await import('./services/claude/message-sender.js');
            await sendMessage(params.message, params.sessionId, params.cwd, ...);
        }

        _originalWrite(JSON.stringify({ id, done: true, success: true }) + '\n');

    } catch (error) {
        _originalWrite(JSON.stringify({
            id, done: true, success: false, error: error.message
        }) + '\n');
    } finally {
        activeRequestId = null;
    }
}

// === 主循环 ===
(async () => {
    _originalWrite(JSON.stringify({ type: 'daemon', event: 'starting', pid: process.pid }) + '\n');

    await preloadSdks();

    _originalWrite(JSON.stringify({ type: 'daemon', event: 'ready', pid: process.pid }) + '\n');

    const rl = createInterface({ input: process.stdin, crlfDelay: Infinity });
    let commandQueue = Promise.resolve();

    rl.on('line', (line) => {
        if (!line.trim()) return;
        const req = JSON.parse(line);
        commandQueue = commandQueue.then(() => processRequest(req));
    });
})();
```

### Step 5: 实现 SDK 按需安装

SDK 不随插件打包，首次使用时自动下载安装：

```java
public class DependencyManager {

    public static final File SDK_ROOT = new File(
        System.getProperty("user.home"),
        ".codemoss/dependencies"
    );

    public void ensureClaudeSdkInstalled() {
        File sdkPath = new File(SDK_ROOT, "claude-sdk/node_modules/@anthropic-ai/claude-agent-sdk");
        if (sdkPath.exists()) return;  // 已安装

        // 提示用户安装
        notifyUserInstallRequired();

        // 或者静默安装
        installSdk("claude-sdk", "@anthropic-ai/claude-agent-sdk");
    }

    public void installSdk(String sdkId, String npmPackage) {
        // 创建目标目录
        File sdkDir = new File(SDK_ROOT, sdkId);
        sdkDir.mkdirs();

        // 运行 npm install
        ProcessBuilder pb = new ProcessBuilder(
            "npm", "install", npmPackage,
            "--prefix", sdkDir.getAbsolutePath(),
            "--save-prod"
        );
        pb.redirectErrorStream(true);

        Process p = pb.start();
        // 读取输出，实时显示安装进度
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    notifyProgress(line);  // 通知 UI 显示进度
                }
            }
        }).start();

        int exit = p.waitFor();
        if (exit != 0) throw new RuntimeException("SDK install failed");
    }
}
```

### Step 6: 标签解析模板

```java
public class MessageParser {

    public void parseTagLine(String line, MessageCallback callback) {
        // 解析标签
        if (line.startsWith("[STREAM_START]")) {
            callback.onMessage("stream_start", null);
        } else if (line.startsWith("[CONTENT_DELTA]")) {
            String content = unescapeJson(line.substring(15));
            callback.onMessage("content_delta", content);
        } else if (line.startsWith("[THINKING_DELTA]")) {
            String content = unescapeJson(line.substring(17));
            callback.onMessage("thinking_delta", content);
        } else if (line.startsWith("[TOOL_USE]")) {
            String json = line.substring(10);
            callback.onMessage("tool_use", json);
        } else if (line.startsWith("[TOOL_RESULT]")) {
            String json = line.substring(13);
            callback.onMessage("tool_result", json);
        } else if (line.startsWith("[SESSION_ID]")) {
            String sessionId = line.substring(12).trim();
            callback.onMessage("session_id", sessionId);
        } else if (line.startsWith("[USAGE]")) {
            String json = line.substring(8);
            callback.onMessage("usage", json);
        } else if (line.startsWith("[MESSAGE_END]")) {
            callback.onMessage("message_end", null);
        } else if (line.startsWith("[SEND_ERROR]")) {
            String json = line.substring(13);
            callback.onMessage("error", json);
        } else if (line.startsWith("[MESSAGE]")) {
            String json = line.substring(9);
            callback.onMessage("message", json);
        }
    }

    private String unescapeJson(String s) {
        // [CONTENT_DELTA] 后的内容是 JSON 字符串格式，需要解析
        return JsonParser.parseString(s.trim()).getAsString();
    }
}
```

---

## 9. 性能优化策略

### 9.1 三阶段预热（详见 7.2）

这是降低用户感知延迟的核心手段。Daemon 模式本身已经将后续请求降到 <1s，预热策略进一步消除了首次请求的冷启动代价。

### 9.2 Runtime 复用

```
不复用（每次请求重建 Runtime）：
  首次请求 → 创建 runtime → SDK 初始化 → 处理消息 → 销毁
  第二次请求 → 创建 runtime → SDK 初始化 → 处理消息 → 销毁
  第三次请求 → 创建 runtime → SDK 初始化 → 处理消息 → 销毁

复用（按 session 复用 Runtime）：
  首次请求 → 创建 runtime → SDK 初始化 → 处理消息 → 保持
  第二次请求 → 复用 runtime → 处理消息 → 保持
  第三次请求 → 复用 runtime → 处理消息 → 保持
```

### 9.3 Anonymous Runtime 预创建

每次对话消耗一个匿名 runtime 后，立即补充一个新的：

```java
.whenComplete((result, error) -> {
    if (sessionId != null && !sessionId.isEmpty()) {
        // 首轮结束，补充一个匿名 runtime
        claudeSDKBridge.prewarmDaemonAsync(cwd);
    }
});
```

### 9.4 环境变量缓存

Daemon 启动时从 `~/.claude/settings.json` 读取认证信息和代理配置，之后所有请求共享：

```javascript
// daemon.js 启动时一次性读取
injectNetworkEnvVars();  // 设置 HTTPS_PROXY, ANTHROPIC_API_KEY 等

// 之后所有 SDK 请求自动继承这些环境变量
```

---

## 10. 常见问题排查

### Q1: Daemon 启动后立即退出

```
排查步骤：
1. 检查 daemon.js 是否存在且有执行权限
2. 检查 Node.js 版本（需要 >= 18）
3. 查看 Java 侧 stderr 输出（daemon 的 stderr 被重定向到 LOG.debug）
4. 检查 SDK 路径是否正确
5. 检查 Node.js 是否在 PATH 中

常见原因：
- SDK 未安装 → 先调用 DependencyManager 安装
- Node.js 版本过低 → 提示用户升级
- 端口被占用 → 不涉及（无网络端口，纯 stdin/stdout）
```

### Q2: 消息标签解析错误

```
排查步骤：
1. 确认 Java 侧能收到 [MESSAGE_START] 和 [MESSAGE_END]
2. 检查 [CONTENT_DELTA] 后的 JSON 是否正确转义
3. 检查 [TOOL_USE] 和 [TOOL_RESULT] 的 JSON 格式

常见原因：
- SDK 版本升级导致输出格式变化
- 多字节字符（中文/emoji）未正确 UTF-8 编码
- JSON 字符串未正确解析（直接拼接而非 JsonParser）
```

### Q3: 首次请求超时

```
原因：SDK 冷加载耗时 3-5 秒

解决方案：
1. 确保预热策略已实现（见 7.2）
2. 首次请求设置更长的超时（60秒）
3. 在 UI 上显示 "正在初始化..." 状态

配置：
  if (!daemonBridge.isSdkPreloaded()) {
      // 显示初始化提示
      callbackHandler.notifyStateChange(busy=false, loading=true, error="正在连接 Claude...");
  }
```

### Q4: Session Resume 失败

```
排查步骤：
1. 检查 sessionId 是否正确传递到 daemon
2. 检查 ~/.claude/projects/{id}/sessions/{sessionId}.jsonl 是否存在
3. 检查会话文件权限

常见原因：
- Session 文件被清理工具删除
- 不同 cwd 的会话无法跨目录恢复
- SDK 版本导致 session 格式不兼容
```

### Q5: 工具调用（Tool Use）无响应

```
排查步骤：
1. 检查 canUseTool 回调是否正确实现
2. 检查工具执行后是否正确返回 [TOOL_RESULT]
3. 检查权限模式配置（default 模式需要用户确认）

流程：
  [TOOL_USE] {"id":"tool_1","name":"Read","input":{"file_path":"..."}}
       │
       ▼
  Java 侧: 弹窗确认 / 显示工具调用
       │
       ▼
  用户确认 / 执行工具
       │
       ▼
  Java 侧: 发送工具结果给 daemon
       │
       ▼
  [TOOL_RESULT] {"tool_use_id":"tool_1","content":"文件内容..."}
       │
       ▼
  SDK 继续处理下一条消息
```

### Q6: Daemon 进程僵尸

```
原因：Java 侧异常退出但 Node.js 进程未感知父进程死亡

解决方案：daemon.js 中实现父进程监控
```javascript
const initialPpid = process.ppid;
const monitor = setInterval(() => {
    try {
        process.kill(process.ppid, 0);  // 检查父进程是否存在
    } catch (e) {
        if (e.code === 'ESRCH') {
            // 父进程已死亡，立即退出
            process.exit(0);
        }
    }
}, 10_000);
```

---

## 附录：关键文件索引

| 文件 | 作用 |
|------|------|
| `ai-bridge/daemon.js` | Node.js 守护进程入口 |
| `ai-bridge/utils/sdk-loader.js` | SDK 动态加载器 |
| `ai-bridge/services/claude/message-sender.js` | SDK query 调用 + 标签输出 |
| `ai-bridge/services/claude/persistent-query-service.js` | Runtime 池管理 |
| `ai-bridge/services/claude/runtime-lifecycle.js` | Runtime 创建/销毁 |
| `ai-bridge/config/api-config.js` | API 配置、认证、代理 |
| `src/.../provider/common/DaemonBridge.java` | Java 侧进程管理 |
| `src/.../provider/claude/ClaudeSDKBridge.java` | Java 侧 SDK 桥接入口 |
| `src/.../session/ClaudeMessageHandler.java` | 消息标签解析 |
| `src/.../provider/common/MessageCallback.java` | 消息回调接口 |
| `src/.../dependency/DependencyManager.java` | SDK 按需安装 |
| `docs/feat/daemon-architecture-refactor.md` | 架构改造详细文档 |
