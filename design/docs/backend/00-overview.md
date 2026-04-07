# ClaudeCodeJet v3.0 后端开发总览

**文档版本**: 1.0
**创建日期**: 2026-04-08
**基包路径**: `com.github.xingzhewa.ccgui`
**目标平台**: IntelliJ IDEA 2025.2+ (sinceBuild=252)
**JVM版本**: 21
**当前项目版本**: 0.0.1

---

## 1. 项目现状分析

### 1.1 已有代码

| 文件 | 状态 | 说明 |
|------|------|------|
| `MyBundle.kt` | 模板代码 | i18n DynamicBundle，可保留 |
| `MyProjectService.kt` | 占位代码 | 需替换为真实服务 |
| `MyProjectActivity.kt` | 占位代码 | 需替换为真实启动逻辑 |
| `MyToolWindowFactory.kt` | **核心代码** | 已实现JCEF加载和消息分发，但依赖多个未实现的类 |
| `MyPluginTest.kt` | 模板测试 | 需替换 |

### 1.2 已注册但未实现的关键类

`MyToolWindowFactory.kt` 和 `plugin.xml` 引用了以下**尚未创建**的类：

```
com.github.xingzhewa.ccgui.bridge.BridgeManager       ← plugin.xml 注册为 ProjectService
com.github.xingzhewa.ccgui.bridge.StreamCallback       ← 流式回调接口
com.github.xingzhewa.ccgui.bridge.SimpleStreamCallback ← 流式回调基类
com.github.xingzhewa.ccgui.browser.CefBrowserPanel     ← JCEF浏览器面板封装
com.github.xingzhewa.ccgui.util.logger                 ← 日志工具函数
com.github.xingzhewa.ccgui.config.CCGuiConfig          ← plugin.xml 注册为 ProjectService
```

### 1.3 已创建但为空的包目录

```
src/main/kotlin/com/github/xingzhewa/ccgui/
├── bridge/model/          ← 空
├── config/ui/             ← 空
├── model/                 ← 空
├── agent/
│   ├── capabilities/      ← 空
│   ├── core/              ← 空
│   ├── formatter/         ← 空
│   ├── models/            ← 空
│   └── workflow/          ← 空
├── ide/
│   ├── context/           ← 空
│   ├── editor/            ← 空
│   ├── popup/             ← 空
│   └── vcs/               ← 空
└── ui/
    ├── toolwindow/        ← 空
    └── webview/           ← 空
```

### 1.4 现有依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kotlin | (DSL managed) | 核心语言 |
| IntelliJ Platform | 2025.2.6.1 | 插件SDK |
| Gson | 2.10.1 | JSON序列化 |
| JCEF | (平台内置) | Web UI渲染 |
| JUnit 5 | (test) | 单元测试 |
| Coroutines | (平台内置) | 异步编程 |

---

## 2. 后端目标架构

### 2.1 分层架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                     Presentation Layer                          │
│  (已有: MyToolWindowFactory, CefBrowserPanel)                   │
│  (不在后端范围，但需提供接口)                                     │
├─────────────────────────────────────────────────────────────────┤
│                     Application Layer                           │
│  ChatOrchestrator  │  SessionManager  │  ConfigHotReload        │
│  StreamingEngine   │  InteractiveEngine│  PromptOptimizer       │
│  TaskProgressTracker│ ContextProvider │  ConversationModeMgr   │
├─────────────────────────────────────────────────────────────────┤
│                     Adaptation Layer                            │
│  BridgeManager     │  StdioBridge     │  MessageParser          │
│  StreamingParser   │  ProcessPool     │  MultiProviderAdapter   │
│  ModelSwitcher     │  VersionDetector │                         │
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure Layer                        │
│  EventBus          │  SecureStorage   │  StateManager           │
│  CacheManager      │  ErrorRecovery   │  MetricsCollector       │
│  Logger            │  Persistence     │  PluginDisposable       │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 后端包结构规划

```
com.github.xingzhewa.ccgui/
│
├── infrastructure/                    # 基础设施层
│   ├── eventbus/
│   │   ├── EventBus.kt               # 事件总线
│   │   ├── Event.kt                  # 事件基类
│   │   └── Events.kt                 # 所有事件定义
│   ├── storage/
│   │   ├── SecureStorage.kt          # 加密存储(API Key等)
│   │   ├── ConfigStorage.kt          # 配置持久化
│   │   └── SessionStorage.kt         # 会话持久化
│   ├── state/
│   │   ├── StateManager.kt           # 集中式状态管理
│   │   └── DisposableHelper.kt       # 生命周期管理
│   ├── cache/
│   │   └── CacheManager.kt           # LRU缓存
│   ├── error/
│   │   ├── ErrorRecoveryManager.kt   # 错误恢复
│   │   └── PluginExceptions.kt       # 异常体系
│   └── metrics/
│       └── MetricsCollector.kt       # 性能指标收集
│
├── model/                             # 数据模型(共享)
│   ├── message/
│   │   ├── ChatMessage.kt            # 聊天消息
│   │   ├── ContentPart.kt            # 多模态内容
│   │   └── MessageRole.kt            # 消息角色
│   ├── session/
│   │   ├── ChatSession.kt            # 会话
│   │   ├── SessionType.kt            # 会话类型
│   │   └── SessionContext.kt         # 会话上下文
│   ├── config/
│   │   ├── ThemeConfig.kt            # 主题配置
│   │   ├── ModelConfig.kt            # 模型配置
│   │   ├── ConversationMode.kt       # 对话模式
│   │   └── AppConfig.kt             # 应用总配置
│   ├── interaction/
│   │   ├── InteractiveQuestion.kt    # 交互式问题
│   │   └── QuestionType.kt           # 问题类型
│   ├── task/
│   │   ├── TaskProgress.kt           # 任务进度
│   │   └── TaskStep.kt              # 任务步骤
│   ├── provider/
│   │   ├── AIProvider.kt             # 供应商接口
│   │   ├── ChatRequest.kt            # 请求
│   │   └── ChatResponse.kt           # 响应
│   ├── skill/
│   │   ├── Skill.kt                 # 技能
│   │   └── SkillVariable.kt          # 技能变量
│   ├── agent/
│   │   ├── Agent.kt                 # 代理
│   │   └── AgentConstraint.kt        # 代理约束
│   └── mcp/
│       └── McpServer.kt             # MCP服务器
│
├── adaptation/                        # 适配层
│   ├── bridge/
│   │   ├── BridgeManager.kt          # CLI桥接管理器
│   │   ├── StdioBridge.kt           # stdio通信
│   │   ├── StreamCallback.kt        # 流式回调接口
│   │   ├── SimpleStreamCallback.kt   # 流式回调基类
│   │   └── ProcessPool.kt           # 进程池
│   ├── parser/
│   │   ├── MessageParser.kt          # 消息解析器
│   │   └── StreamingResponseParser.kt # 流式响应解析
│   ├── provider/
│   │   ├── MultiProviderAdapter.kt   # 多供应商适配
│   │   ├── AnthropicProvider.kt      # Anthropic实现
│   │   ├── OpenAIProvider.kt         # OpenAI实现
│   │   └── DeepSeekProvider.kt       # DeepSeek实现
│   └── version/
│       └── VersionDetector.kt        # 版本探测
│
├── application/                       # 应用层
│   ├── orchestrator/
│   │   └── ChatOrchestrator.kt       # 聊天编排器
│   ├── session/
│   │   ├── SessionManager.kt         # 会话管理器
│   │   └── SessionInterruptRecovery.kt # 中断恢复
│   ├── streaming/
│   │   └── StreamingOutputEngine.kt  # 流式输出引擎
│   ├── interaction/
│   │   └── InteractiveRequestEngine.kt # 交互式请求引擎
│   ├── config/
│   │   ├── ConfigManager.kt          # 配置管理器
│   │   ├── ThemeManager.kt           # 主题管理器
│   │   ├── ModelSwitcher.kt          # 模型切换器
│   │   └── ConfigHotReloadManager.kt # 热更新管理器
│   ├── context/
│   │   └── ContextProvider.kt        # 上下文提供者
│   ├── prompt/
│   │   └── PromptOptimizer.kt        # 提示词优化器
│   ├── multimodal/
│   │   └── MultimodalInputHandler.kt # 多模态输入处理
│   ├── reference/
│   │   └── ConversationReferenceSystem.kt # 对话引用
│   ├── mode/
│   │   └── ConversationModeManager.kt # 对话模式管理
│   ├── task/
│   │   ├── TaskProgressTracker.kt    # 任务进度追踪
│   │   └── TaskParser.kt            # 任务解析器
│   ├── skill/
│   │   ├── SkillsManager.kt          # 技能管理
│   │   └── SkillExecutor.kt          # 技能执行
│   ├── agent/
│   │   ├── AgentsManager.kt          # 代理管理
│   │   └── AgentExecutor.kt          # 代理执行
│   └── mcp/
│       ├── McpServerManager.kt       # MCP服务器管理
│       └── ScopeManager.kt           # 作用域管理
│
├── browser/                           # JCEF浏览器封装
│   └── CefBrowserPanel.kt            # JCEF面板(已有引用)
│
├── config/                            # 插件配置页
│   └── CCGuiConfig.kt                # 插件配置(已有引用)
│
├── util/                              # 工具类
│   └── logger.kt                      # 日志工具(已有引用)
│
├── toolWindow/                        # ToolWindow(已有)
│   └── MyToolWindowFactory.kt
│
└── services/                          # IDE服务(已有)
    └── MyProjectService.kt            # 待重构
```

---

## 3. 开发阶段划分

### Phase 1: 基础设施与数据模型 (Foundation)
**目标**: 建立所有共享数据模型、工具类和基础设施组件
**产出**: 可编译的基础库，所有model/infrastructure包可用
**详见**: [01-phase1-foundation.md](01-phase1-foundation.md)

### Phase 2: 通信适配层 (Adaptation)
**目标**: 实现CLI通信桥接、JCEF通信、消息解析
**产出**: BridgeManager可用，ToolWindow可编译运行
**详见**: [02-phase2-adaptation.md](02-phase2-adaptation.md)

### Phase 3: 核心应用服务 (Core Services)
**目标**: 实现ChatOrchestrator、SessionManager、StreamingEngine等核心服务
**产出**: 完整的聊天流程可工作
**详见**: [03-phase3-core-services.md](03-phase3-core-services.md)

### Phase 4: 功能模块 (Feature Modules)
**目标**: 实现交互增强、多模型适配、任务进度等功能模块
**产出**: 所有PRD定义的功能模块后端逻辑
**详见**: [04-phase4-features.md](04-phase4-features.md)

### Phase 5: 生态集成 (Ecosystem Integration)
**目标**: 实现Skills/Agents/MCP生态管理和作用域系统
**产出**: 完整的Claude Code生态集成
**详见**: [05-phase5-ecosystem.md](05-phase5-ecosystem.md)

### Phase 6: 性能优化与质量保障 (Optimization)
**目标**: 性能调优、内存管理、错误恢复、测试覆盖
**产出**: 满足所有性能指标的稳定版本
**详见**: [06-phase6-optimization.md](06-phase6-optimization.md)

---

## 4. 阶段依赖关系

```
Phase 1 (Foundation)
    ↓
Phase 2 (Adaptation) ← 依赖Phase 1的model和infrastructure
    ↓
Phase 3 (Core Services) ← 依赖Phase 2的Bridge和Parser
    ↓
Phase 4 (Features) ← 依赖Phase 3的Core Services
    ↓
Phase 5 (Ecosystem) ← 依赖Phase 3/4的Services
    ↓
Phase 6 (Optimization) ← 依赖所有功能完成
```

每个Phase完成后必须:
1. 代码可编译通过
2. 核心单元测试通过
3. 不破坏已有功能
4. 接口稳定，后续Phase可依赖

---

## 5. 编码规范约定

### 5.1 命名规范
- 类名: PascalCase (`ChatOrchestrator`, `BridgeManager`)
- 函数/变量: camelCase (`sendMessage`, `currentSession`)
- 常量: `UPPER_SNAKE_CASE` (`MAX_RETRIES`, `DEFAULT_TIMEOUT`)
- 私有成员: `_` 前缀 (`_state`, `_sessions`)
- 接口: 无I前缀 (`AIProvider` 而非 `IAIProvider`)

### 5.2 架构规范
- 所有服务通过 IntelliJ Service 系统注册 (`@Service` 或 plugin.xml)
- 所有异步操作使用 Kotlin Coroutines，禁止阻塞EDT
- 状态管理使用 `StateFlow` / `MutableStateFlow`
- 资源释放实现 `Disposable` 接口
- 错误处理使用 `Result<T>` 包装返回值
- 日志使用 `com.intellij.openapi.diagnostic.Logger`

### 5.3 扩展埋点规范
- 所有Manager类使用接口+实现分离模式
- 使用 `Companion object` + `getInstance(project)` 提供服务访问
- 预留事件通知点（通过EventBus）
- 预留配置项（通过ConfigManager）
- 预留Hook点（通过回调接口）

---

## 6. 关键技术决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| JSON库 | Gson | 项目已引入，与平台一致 |
| HTTP客户端 | Ktor HttpClient | 轻量、协程原生支持 |
| 状态管理 | StateFlow | 响应式，线程安全 |
| 进程通信 | stdin/stdout | Claude Code CLI标准协议 |
| 持久化 | PersistentStateComponent | IntelliJ原生机制 |
| 加密存储 | PasswordSafe | IntelliJ原生API |
| 事件系统 | 自研EventBus | 轻量级，解耦模块 |
| 测试框架 | JUnit 5 + MockK | Kotlin友好 |
