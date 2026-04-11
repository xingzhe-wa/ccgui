# Claude Code IDEA Plugin 产品技术架构文档

> **版本**: v3.0 (轻量化优化版)  
> **更新日期**: 2026-04-11  
> **文档状态**: 最终版  
> **核心原则**: 轻量、原生、按需

---

## 目录

1. [设计原则与约束](#1-设计原则与约束)
2. [需求分析与归纳总结](#2-需求分析与归纳总结)
3. [Claude Code工作机制深度解析](#3-claude-code工作机制深度解析)
4. [轻量化架构设计](#4-轻量化架构设计)
5. [核心模块详细设计](#5-核心模块详细设计)
6. [新增功能模块设计](#6-新增功能模块设计)
7. [数据流与交互流程](#7-数据流与交互流程)
8. [轻量化技术选型](#8-轻量化技术选型)
9. [开发任务拆分与里程碑](#9-开发任务拆分与里程碑)
10. [风险评估与应对策略](#10-风险评估与应对策略)
11. [附录](#附录)

---

## 1. 设计原则与约束

### 1.1 核心设计原则

作为JetBrains IDEA插件，必须在满足功能需求的同时保持轻量化。以下是核心设计原则：

| 原则 | 描述 | 实践方法 |
|------|------|----------|
| **原生优先** | 最大化利用IntelliJ Platform原生能力 | 使用Platform API替代第三方库 |
| **按需加载** | 非核心功能延迟初始化 | Service懒加载、动态注册 |
| **最小依赖** | 减少外部依赖数量 | 依赖审查、功能合并 |
| **内存友好** | 控制内存占用，避免泄漏 | 弱引用、缓存策略、及时释放 |
| **异步优先** | 所有耗时操作异步执行 | 协程、后台任务 |

### 1.2 轻量化约束指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 插件包大小 | < 5MB | 不含Claude CLI |
| 启动时间增量 | < 500ms | 不影响IDE启动 |
| 内存占用 | < 100MB | 正常使用状态 |
| 外部依赖数 | < 5个 | 核心依赖 |
| 冷启动响应 | < 2s | 首次打开Tool Window |

### 1.3 架构优化策略

```
┌─────────────────────────────────────────────────────────────┐
│                    轻量化优化策略                            │
├─────────────────────────────────────────────────────────────┤
│  ❌ 移除                          ✅ 替代方案                │
├─────────────────────────────────────────────────────────────┤
│  SQLite数据库                     PersistentStateComponent   │
│  RxJava                          Kotlin Coroutines          │
│  Gson + Jackson                  仅保留Gson (更小)           │
│  OkHttp + Retrofit               IntelliJ HttpClient         │
│  五层架构                         三层架构                    │
│  全量模块加载                     按需动态加载                 │
│  独立配置中心                     Platform Settings           │
│  自建主题引擎                     Platform Theme API          │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 需求分析与归纳总结

### 2.1 需求分类总览

| 模块 | 核心需求 | 优先级 | 复杂度 | 轻量化策略 |
|------|----------|--------|--------|------------|
| **UI交互** | 流畅交互、主题配置、热更新 | P0 | 中 | Platform Theme API |
| **对话发起** | 提示词优化、代码块选择、多模态输入 | P0 | 高 | Editor API原生 |
| **会话管理** | 多会话、流式输出、打断、回滚 | P0 | 高 | 内存+文件混合存储 |
| **模型配置** | 供应商切换、模型切换、对话模式 | P1 | 中 | 配置持久化 |
| **生态集成** | Skills/Agents/MCP管理 | P1 | 高 | CLI代理模式 |
| **辅助功能** | Commit生成、Token统计、指令集成 | P1 | 中 | Git4Idea集成 |

### 2.2 功能需求详解

#### 2.2.1 UI交互需求

| 子需求 | 描述 | 轻量化实现 |
|--------|------|------------|
| 交互流畅 | 响应迅速，无卡顿 | 虚拟列表、防抖节流 |
| 主题配置 | 精美/简约主题切换 | 跟随IDE主题，自定义CSS变量 |
| 配置驱动 | 所有功能可配置 | PersistentStateComponent |
| 热更新 | 配置即时生效 | 配置监听 + 事件总线 |

#### 2.2.2 对话发起需求

| 子需求 | 描述 | 轻量化实现 |
|--------|------|------------|
| 提示词优化 | 自动优化用户输入 | 内置模板 + 上下文增强 |
| 代码块选择 | 选中代码发起对话 | Editor.getSelectionModel() |
| 多模态输入 | 图片、附件支持 | Base64编码 + 文件引用 |
| 引用对话 | 引用历史消息 | 消息ID引用 |
| 交互式请求 | 主动提问、动态抉择 | Agent Loop控制 |

#### 2.2.3 会话管理需求

| 子需求 | 描述 | 轻量化实现 |
|--------|------|------------|
| 多会话 | 支持多个并行会话 | Tab页签管理 |
| 流式输出 | 实时显示响应 | SSE解析 + 增量渲染 |
| 会话打断 | 随时中断响应 | 协程取消 + 进程终止 |
| 会话回滚 | 回到历史状态 | 消息快照 |
| 会话重命名 | 自定义会话名称 | 内联编辑 |
| 数据隔离 | 会话间数据独立 | 会话ID隔离 |
| 生命周期管理 | 跟踪进度、状态 | 状态机 + 事件通知 |

#### 2.2.4 模型配置需求

| 子需求 | 描述 | 轻量化实现 |
|--------|------|------------|
| 供应商切换 | 多API供应商支持 | 配置模板 |
| 模型切换 | 快速切换模型 | 下拉选择 |
| 对话模式 | thinking/plan/auto | 模式参数注入 |

#### 2.2.5 生态集成需求

| 子需求 | 描述 | 轻量化实现 |
|--------|------|------------|
| Skills管理 | 创建/导入/导出 | 文件系统 + CLI代理 |
| SubAgents管理 | 配置/监控/权限 | CLI代理模式 |
| MCP管理 | 生命周期管理 | CLI代理模式 |
| 作用域配置 | 全局/项目级别 | 分层配置合并 |

#### 2.2.6 辅助功能需求（新增）

| 子需求 | 描述 | 轻量化实现 |
|--------|------|------------|
| Commit生成 | 智能生成提交信息 | Git4Idea + Diff分析 |
| Token统计 | 用量统计与预算 | 响应解析 + 聚合计算 |
| 原生指令集成 | `/`唤起指令列表 | 输入框补全 + 国际化 |

---

## 3. Claude Code工作机制深度解析

### 3.1 Claude Code核心架构

Claude Code是一个基于Agent Loop的智能编程助手，其核心架构如下：

```
┌─────────────────────────────────────────────────────────────┐
│                    Claude Code Architecture                  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   CLI       │  │  Agent SDK  │  │   Skills    │         │
│  │  Interface  │  │   Core      │  │   System    │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
│         │                │                │                 │
│         ▼                ▼                ▼                 │
│  ┌─────────────────────────────────────────────────┐       │
│  │              Agent Loop (核心循环)               │       │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐         │       │
│  │  │ Perceive│→ │  Think  │→ │   Act   │         │       │
│  │  └─────────┘  └─────────┘  └─────────┘         │       │
│  │       ↑                          │              │       │
│  │       └──────────────────────────┘              │       │
│  └─────────────────────────────────────────────────┘       │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────┐       │
│  │              Tool System (工具系统)              │       │
│  │  File │ Bash │ Web │ Agent │ Skill │ MCP       │       │
│  └─────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 三种对话模式详解

| 模式 | 特点 | 适用场景 | API参数 |
|------|------|----------|---------|
| **Thinking** | 深度推理，展示思考过程 | 复杂问题、架构设计 | `thinking: {type: "enabled"}` |
| **Plan** | 只读模式，先规划后执行 | 大型重构、多文件修改 | `plan_mode: true` |
| **Auto** | 自动决策，减少确认 | 信任场景、快速迭代 | `auto_mode: true` |

### 3.3 Skills/SubAgents/MCP机制

#### Skills（技能）
- **定义**: 可移植的工具扩展，封装特定工作流
- **存储**: `.claude/skills/` 目录
- **格式**: Markdown文件，包含description和instructions
- **触发**: 自动匹配或显式调用

#### SubAgents（子代理）
- **定义**: 专业化子代理，独立上下文和工具集
- **配置**: `.claude/agents/` 目录
- **特点**: 独立模型选择、工具限制、专用Prompt
- **调度**: 主Agent根据任务类型委托

#### MCP（Model Context Protocol）
- **定义**: 外部系统集成协议
- **配置**: `settings.json` 中的 `mcpServers`
- **能力**: 提供外部工具、资源、Prompt模板
- **生命周期**: 启动、连接、调用、关闭

### 3.4 Hooks与配置系统

```
配置层级（优先级从高到低）:
├── 命令行参数
├── 项目本地: .claude/settings.local.json
├── 项目共享: .claude/settings.json
├── 用户全局: ~/.claude/settings.json
└── 系统默认

Hooks事件类型:
├── PreToolUse: 工具调用前
├── PostToolUse: 工具调用后
├── Notification: 通知事件
├── Stop: 停止条件
└── PreCompact: 上下文压缩前
```

---

## 4. 轻量化架构设计

### 4.1 三层架构总览

相比原五层架构，简化为三层架构，最大化利用IntelliJ Platform能力：

```
┌─────────────────────────────────────────────────────────────┐
│                     表现层 (Presentation)                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ToolWindow  │  │  Dialog     │  │  Editor     │         │
│  │  Factory   │  │  Wrapper    │  │  Provider   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │Action      │  │  ConfigUI   │  │  Popup      │         │
│  │  Group     │  │  Builder    │  │  Factory    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                     服务层 (Service)                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │Session     │  │  Config     │  │  Claude     │         │
│  │  Service   │  │  Service    │  │  Client     │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │Context     │  │  Usage      │  │  Command    │         │
│  │  Manager   │  │  Tracker    │  │  Executor   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                     基础层 (Infrastructure)                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │Storage     │  │  Process    │  │  Event      │         │
│  │  Service   │  │  Manager    │  │  Bus        │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │Http        │  │  Git        │  │  I18N       │         │
│  │  Client    │  │  Adapter    │  │  Manager    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│              IntelliJ Platform (原生能力复用)                │
│  PersistentState │ Task.Backgroundable │ NotificationGroup  │
│  VirtualFile     │ Editor API          │ Git4Idea           │
│  HttpClient      │ Action System       │ MessageBus         │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 模块职责定义

#### 表现层模块

| 模块 | 职责 | 依赖 |
|------|------|------|
| **ToolWindowFactory** | 创建主界面容器 | SessionService |
| **DialogWrapper** | 配置对话框、确认对话框 | ConfigService |
| **EditorProvider** | 代码预览、Diff展示 | - |
| **ActionGroup** | 工具栏、右键菜单 | CommandExecutor |
| **ConfigUIBuilder** | Settings页面构建 | ConfigService |
| **PopupFactory** | 下拉列表、指令补全 | CommandRegistry |

#### 服务层模块

| 模块 | 职责 | 依赖 |
|------|------|------|
| **SessionService** | 会话生命周期管理 | StorageService, ClaudeClient |
| **ConfigService** | 配置读写、热更新 | StorageService |
| **ClaudeClient** | 与Claude CLI/API通信 | HttpClient, ProcessManager |
| **ContextManager** | 上下文构建、压缩 | Editor API |
| **UsageTracker** | Token统计、预算管理 | StorageService |
| **CommandExecutor** | 指令解析、执行 | ClaudeClient |

#### 基础层模块

| 模块 | 职责 | 实现方式 |
|------|------|----------|
| **StorageService** | 数据持久化 | PersistentStateComponent |
| **ProcessManager** | CLI进程管理 | ProcessBuilder + 协程 |
| **EventBus** | 事件发布订阅 | Platform MessageBus |
| **HttpClient** | HTTP请求 | Platform HttpRequests |
| **GitAdapter** | Git操作封装 | Git4Idea API |
| **I18NManager** | 国际化管理 | ResourceBundle |

### 4.3 按需加载策略

```kotlin
// plugin.xml 配置示例
<extensions defaultExtensionNs="com.intellij">
    <!-- 延迟加载服务 -->
    <applicationService 
        serviceInterface="...SessionService"
        serviceImplementation="...SessionServiceImpl"
        preload="false"/>
    
    <!-- 项目级服务，按项目加载 -->
    <projectService 
        serviceInterface="...ContextManager"
        serviceImplementation="...ContextManagerImpl"/>
    
    <!-- Tool Window 按需创建 -->
    <toolWindow 
        id="Claude Code"
        factoryClass="...ClaudeToolWindowFactory"
        anchor="right"
        canCloseContents="true"/>
</extensions>
```

### 4.4 内存优化策略

| 策略 | 实现方式 | 效果 |
|------|----------|------|
| **会话缓存限制** | LRU缓存，最多保留10个活跃会话 | 控制内存增长 |
| **消息懒加载** | 滚动到可见区域才渲染 | 减少DOM节点 |
| **图片压缩** | 自动缩放大图，限制最大尺寸 | 减少内存占用 |
| **弱引用监听** | 使用WeakReference注册监听器 | 避免内存泄漏 |
| **定时清理** | 后台任务定期清理过期数据 | 释放无用资源 |

---

## 5. 核心模块详细设计

### 5.1 会话管理模块

#### 状态机设计

```
┌─────────┐    create    ┌─────────┐    start    ┌─────────┐
│  Idle   │─────────────→│ Created │────────────→│ Running │
└─────────┘              └─────────┘             └────┬────┘
     ↑                        ↑                       │
     │                        │                       │
     │                      resume                  pause
     │                        │                       │
     │                        ↓                       ↓
     │                   ┌─────────┐            ┌─────────┐
     └───────────────────│ Closed  │←───────────│ Paused  │
                  delete └─────────┘   stop     └─────────┘
```

#### 轻量化存储方案

```kotlin
// 使用PersistentStateComponent替代SQLite
@State(
    name = "ClaudeCodeSessions",
    storages = [
        Storage("claude-code/sessions.xml", roamingType = RoamingType.DISABLED)
    ]
)
class SessionStorage : PersistentStateComponent<SessionStorage.State> {
    data class State(
        var sessions: MutableList<SessionData> = mutableListOf(),
        var activeSessionId: String? = null
    )
    
    data class SessionData(
        var id: String = "",
        var name: String = "",
        var createdAt: Long = 0,
        var updatedAt: Long = 0,
        var messages: List<MessageData> = emptyList()  // 仅保留元数据
    )
}

// 大型消息内容存储为独立JSON文件
class MessageFileManager(private val project: Project) {
    private val sessionDir: Path
    
    fun saveMessage(sessionId: String, messageId: String, content: String) {
        val file = sessionDir.resolve("$sessionId/$messageId.json")
        file.writeText(content)
    }
    
    fun loadMessage(sessionId: String, messageId: String): String? {
        val file = sessionDir.resolve("$sessionId/$messageId.json")
        return if (file.exists()) file.readText() else null
    }
}
```

### 5.2 流式输出模块

#### SSE解析器

```kotlin
class StreamParser {
    private val buffer = StringBuilder()
    
    fun parse(chunk: String): List<StreamEvent> {
        buffer.append(chunk)
        val events = mutableListOf<StreamEvent>()
        
        while (true) {
            val eventEnd = buffer.indexOf("\n\n")
            if (eventEnd == -1) break
            
            val eventData = buffer.substring(0, eventEnd)
            buffer.delete(0, eventEnd + 2)
            
            parseEvent(eventData)?.let { events.add(it) }
        }
        
        return events
    }
    
    private fun parseEvent(data: String): StreamEvent? {
        // 解析 SSE 事件格式
        // event: message_start
        // data: {"type":"message_start",...}
    }
}
```

#### 增量渲染策略

```kotlin
class StreamingRenderer(private val textPane: JEditorPane) {
    private val updateQueue = Channel<String>(capacity = 100)
    private var lastUpdate = 0L
    private val minInterval = 50L // 最小更新间隔(ms)
    
    init {
        // 单一协程处理渲染，避免频繁更新
        CoroutineScope(Dispatchers.EDT).launch {
            for (content in updateQueue) {
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= minInterval) {
                    textPane.text = content
                    lastUpdate = now
                }
            }
        }
    }
    
    fun append(text: String) {
        updateQueue.trySend(currentContent + text)
    }
}
```

### 5.3 上下文管理模块

#### 上下文注入流程

```
用户输入 → 代码选择检测 → 文件上下文提取 → CLAUDE.md读取
                ↓
          Skills匹配 → MCP工具发现 → 上下文组装
                ↓
          Token估算 → 压缩决策 → 最终上下文
```

#### 轻量化上下文压缩

```kotlin
class ContextCompressor {
    // 压缩策略：保留关键信息，移除冗余
    fun compress(messages: List<Message>): List<Message> {
        return when {
            messages.size < 10 -> messages  // 少量消息不压缩
            else -> {
                // 保留首尾消息，中间摘要
                val head = messages.take(2)
                val tail = messages.takeLast(2)
                val summary = summarizeMiddle(messages.drop(2).dropLast(2))
                head + summary + tail
            }
        }
    }
    
    private fun summarizeMiddle(messages: List<Message>): Message {
        // 调用Claude生成摘要
    }
}
```

### 5.4 配置管理模块

#### 分层配置结构

```kotlin
@State(
    name = "ClaudeCodeSettings",
    storages = [
        Storage("claude-code/settings.xml")
    ]
)
class ClaudeSettings : PersistentStateComponent<ClaudeSettings.State> {
    data class State(
        // API配置
        var apiProvider: String = "anthropic",
        var apiKey: String = "",  // 加密存储
        var apiEndpoint: String = "",
        
        // 模型配置
        var defaultModel: String = "claude-sonnet-4-20250514",
        var defaultMode: String = "auto",
        
        // UI配置
        var theme: String = "follow-ide",
        var fontSize: Int = 14,
        
        // 功能开关
        var enableThinking: Boolean = true,
        var enableStreaming: Boolean = true,
        
        // 预算配置
        var dailyBudget: Double = 10.0,
        var monthlyBudget: Double = 200.0
    )
}
```

#### 热更新实现

```kotlin
class ConfigService(private val project: Project) {
    private val messageBus = project.messageBus
    
    fun updateConfig(newConfig: Config) {
        // 1. 验证配置
        validate(newConfig)
        
        // 2. 保存配置
        saveConfig(newConfig)
        
        // 3. 发布变更事件
        messageBus.syncPublisher(ConfigChangeListener.TOPIC)
            .onConfigChanged(newConfig)
    }
}

// 各模块订阅配置变更
class SessionService : ConfigChangeListener {
    override fun onConfigChanged(config: Config) {
        // 应用新配置，无需重启
        applyNewConfig(config)
    }
}
```

### 5.5 Skills管理模块

#### Skills文件结构

```
.claude/
├── skills/
│   ├── code-review.md
│   ├── test-generator.md
│   └── doc-writer.md
├── agents/
│   ├── architect.md
│   └── reviewer.md
└── settings.json
```

#### Skills加载器

```kotlin
class SkillsLoader(private val project: Project) {
    private val skillsCache = mutableMapOf<String, Skill>()
    
    fun loadSkills(): List<Skill> {
        val skillsDir = project.basePath?.let { 
            Paths.get(it, ".claude", "skills") 
        } ?: return emptyList()
        
        if (!Files.exists(skillsDir)) return emptyList()
        
        return Files.list(skillsDir)
            .filter { it.toString().endsWith(".md") }
            .map { parseSkill(it) }
            .toList()
    }
    
    private fun parseSkill(path: Path): Skill {
        val content = Files.readString(path)
        // 解析Markdown格式的Skill
        return Skill(
            name = path.fileName.toString().removeSuffix(".md"),
            description = extractDescription(content),
            instructions = extractInstructions(content)
        )
    }
}
```

### 5.6 SubAgents管理模块

#### SubAgent配置格式

```json
{
    "name": "architect",
    "description": "System architecture design specialist",
    "model": "claude-opus-4-20250514",
    "tools": ["file_read", "file_write"],
    "systemPrompt": "You are a system architect...",
    "maxTokens": 8192
}
```

#### SubAgent监控

```kotlin
class SubAgentMonitor {
    data class AgentStatus(
        val name: String,
        val state: State,  // IDLE, RUNNING, WAITING, COMPLETED, FAILED
        val currentTask: String?,
        val progress: Float,
        val startTime: Long,
        val tokensUsed: Int
    )
    
    private val statusMap = ConcurrentHashMap<String, AgentStatus>()
    
    fun updateStatus(agentName: String, status: AgentStatus) {
        statusMap[agentName] = status
        // 发布状态变更事件
        EventBus.publish(AgentStatusEvent(agentName, status))
    }
}
```

### 5.7 MCP管理模块

#### MCP配置示例

```json
{
    "mcpServers": {
        "filesystem": {
            "command": "mcp-filesystem",
            "args": ["--root", "/path/to/project"],
            "env": {}
        },
        "github": {
            "command": "mcp-github",
            "args": [],
            "env": {
                "GITHUB_TOKEN": "${GITHUB_TOKEN}"
            }
        }
    }
}
```

#### MCP生命周期管理

```kotlin
class MCPManager(private val processManager: ProcessManager) {
    private val servers = ConcurrentHashMap<String, MCPServer>()
    
    suspend fun startServer(name: String, config: MCPServerConfig): Result<MCPServer> {
        return try {
            val process = processManager.start(
                command = config.command,
                args = config.args,
                env = config.env
            )
            val server = MCPServer(name, process)
            server.initialize()
            servers[name] = server
            Result.success(server)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun stopServer(name: String) {
        servers[name]?.shutdown()
        servers.remove(name)
    }
}
```

---

## 6. 新增功能模块设计

### 6.1 Commit提示词生成模块

#### 模块架构

```
┌─────────────────────────────────────────────────────────────┐
│                  Commit Generator Module                     │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ GitClient   │→ │ DiffAnalyzer│→ │ PromptBuild │         │
│  │ (Git4Idea)  │  │             │  │             │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                            │                │
│                                            ▼                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ CommitDialog│←│ClaudeClient │←│TemplateEng  │         │
│  │             │  │             │  │             │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

#### 核心实现

```kotlin
class CommitGenerator(private val project: Project) {
    private val git = GitUtil.getRepositoryManager(project)
    
    suspend fun generateCommitMessage(): Result<String> {
        // 1. 获取暂存区变更
        val changes = git.getStagedChanges()
        if (changes.isEmpty()) {
            return Result.failure(NoStagedChangesException())
        }
        
        // 2. 分析Diff
        val analysis = analyzeChanges(changes)
        
        // 3. 构建Prompt
        val prompt = buildCommitPrompt(analysis)
        
        // 4. 调用Claude生成
        val message = claudeClient.generate(prompt)
        
        return Result.success(message)
    }
    
    private fun analyzeChanges(changes: List<Change>): ChangeAnalysis {
        return ChangeAnalysis(
            filesAdded = changes.count { it.type == Change.Type.ADD },
            filesModified = changes.count { it.type == Change.Type.MODIFICATION },
            filesDeleted = changes.count { it.type == Change.Type.DELETION },
            diffSummary = extractDiffSummary(changes),
            affectedModules = detectAffectedModules(changes)
        )
    }
    
    private fun buildCommitPrompt(analysis: ChangeAnalysis): String {
        return """
            Based on the following git diff, generate a concise commit message.
            Follow Conventional Commits format.
            
            Changes:
            - Added: ${analysis.filesAdded} files
            - Modified: ${analysis.filesModified} files
            - Deleted: ${analysis.filesDeleted} files
            
            Diff Summary:
            ${analysis.diffSummary}
            
            Generate a commit message (max 72 chars for title):
        """.trimIndent()
    }
}
```

#### 模板引擎

```kotlin
enum class CommitTemplate(val pattern: String) {
    CONVENTIONAL("<type>(<scope>): <description>"),
    GITMOJI(":<emoji>: <description>"),
    SIMPLE("<description>")
}

class CommitTemplateEngine {
    fun render(template: CommitTemplate, data: CommitData): String {
        return when (template) {
            CommitTemplate.CONVENTIONAL -> {
                "${data.type}(${data.scope}): ${data.description}"
            }
            CommitTemplate.GITMOJI -> {
                "${data.emoji} ${data.description}"
            }
            CommitTemplate.SIMPLE -> data.description
        }
    }
}
```

### 6.2 Token用量统计模块

#### 数据模型

```kotlin
data class UsageRecord(
    val sessionId: String,
    val timestamp: Long,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0
)

data class UsageSummary(
    val period: Period,  // DAILY, WEEKLY, MONTHLY
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val estimatedCost: Double,
    val byModel: Map<String, ModelUsage>
)
```

#### 用量收集器

```kotlin
class UsageCollector {
    // 从API响应中提取Usage信息
    fun extractUsage(response: ClaudeResponse): UsageRecord {
        return UsageRecord(
            sessionId = response.sessionId,
            timestamp = System.currentTimeMillis(),
            model = response.model,
            inputTokens = response.usage.inputTokens,
            outputTokens = response.usage.outputTokens,
            cacheCreationTokens = response.usage.cacheCreationInputTokens,
            cacheReadTokens = response.usage.cacheReadInputTokens
        )
    }
    
    // 持久化记录
    fun record(usage: UsageRecord) {
        // 追加到JSON文件，避免频繁写入XML
        usageLog.append(usage.toJson())
    }
}
```

#### 成本计算器

```kotlin
class CostCalculator {
    // 模型定价表（每百万Token）
    private val pricing = mapOf(
        "claude-opus-4-20250514" to ModelPricing(
            input = 15.0,
            output = 75.0,
            cacheWrite = 18.75,
            cacheRead = 1.5
        ),
        "claude-sonnet-4-20250514" to ModelPricing(
            input = 3.0,
            output = 15.0,
            cacheWrite = 3.75,
            cacheRead = 0.3
        ),
        "claude-3-5-haiku-20241022" to ModelPricing(
            input = 0.8,
            output = 4.0,
            cacheWrite = 1.0,
            cacheRead = 0.08
        )
    )
    
    fun calculate(usage: UsageRecord): Double {
        val price = pricing[usage.model] ?: return 0.0
        val inputCost = usage.inputTokens * price.input / 1_000_000
        val outputCost = usage.outputTokens * price.output / 1_000_000
        val cacheWriteCost = usage.cacheCreationTokens * price.cacheWrite / 1_000_000
        val cacheReadCost = usage.cacheReadTokens * price.cacheRead / 1_000_000
        
        return inputCost + outputCost + cacheWriteCost + cacheReadCost
    }
}
```

#### 预算管理器

```kotlin
class BudgetManager(private val settings: ClaudeSettings) {
    private val alerts = mutableListOf<BudgetAlert>()
    
    fun checkBudget(currentUsage: Double): BudgetStatus {
        val dailyLimit = settings.state.dailyBudget
        val monthlyLimit = settings.state.monthlyBudget
        
        val dailyUsage = getDailyUsage()
        val monthlyUsage = getMonthlyUsage()
        
        return BudgetStatus(
            dailyUsed = dailyUsage,
            dailyLimit = dailyLimit,
            dailyPercentage = dailyUsage / dailyLimit,
            monthlyUsed = monthlyUsage,
            monthlyLimit = monthlyLimit,
            monthlyPercentage = monthlyUsage / monthlyLimit,
            alerts = checkAlerts(dailyUsage, dailyLimit, monthlyUsage, monthlyLimit)
        )
    }
    
    private fun checkAlerts(
        daily: Double, dailyLimit: Double,
        monthly: Double, monthlyLimit: Double
    ): List<BudgetAlert> {
        val alerts = mutableListOf<BudgetAlert>()
        
        if (daily / dailyLimit >= 0.9) {
            alerts.add(BudgetAlert.DAILY_90_PERCENT)
        } else if (daily / dailyLimit >= 0.8) {
            alerts.add(BudgetAlert.DAILY_80_PERCENT)
        }
        
        if (monthly / monthlyLimit >= 0.9) {
            alerts.add(BudgetAlert.MONTHLY_90_PERCENT)
        }
        
        return alerts
    }
}
```

### 6.3 Claude Code原生指令集成模块

#### 指令注册表

```kotlin
class CommandRegistry(private val i18n: I18NManager) {
    private val commands = mapOf<String, SlashCommand>(
        "/init" to SlashCommand(
            id = "init",
            descriptionKey = "command.init.description",
            mode = CommandMode.SYSTEM,
            requiresProject = true
        ),
        "/compact" to SlashCommand(
            id = "compact",
            descriptionKey = "command.compact.description",
            mode = CommandMode.SESSION,
            requiresProject = false
        ),
        "/clear" to SlashCommand(
            id = "clear",
            descriptionKey = "command.clear.description",
            mode = CommandMode.SESSION,
            requiresProject = false
        ),
        "/context" to SlashCommand(
            id = "context",
            descriptionKey = "command.context.description",
            mode = CommandMode.INFO,
            requiresProject = false
        ),
        "/cost" to SlashCommand(
            id = "cost",
            descriptionKey = "command.cost.description",
            mode = CommandMode.INFO,
            requiresProject = false
        ),
        "/doctor" to SlashCommand(
            id = "doctor",
            descriptionKey = "command.doctor.description",
            mode = CommandMode.DIAGNOSTIC,
            requiresProject = false
        ),
        "/model" to SlashCommand(
            id = "model",
            descriptionKey = "command.model.description",
            mode = CommandMode.CONFIG,
            requiresProject = false,
            hasParams = true
        ),
        "/think" to SlashCommand(
            id = "think",
            descriptionKey = "command.think.description",
            mode = CommandMode.MODE_SWITCH,
            requiresProject = false
        ),
        "/plan" to SlashCommand(
            id = "plan",
            descriptionKey = "command.plan.description",
            mode = CommandMode.MODE_SWITCH,
            requiresProject = false
        ),
        "/auto" to SlashCommand(
            id = "auto",
            descriptionKey = "command.auto.description",
            mode = CommandMode.MODE_SWITCH,
            requiresProject = false
        ),
        "/resume" to SlashCommand(
            id = "resume",
            descriptionKey = "command.resume.description",
            mode = CommandMode.SESSION,
            requiresProject = false
        ),
        "/mcp" to SlashCommand(
            id = "mcp",
            descriptionKey = "command.mcp.description",
            mode = CommandMode.MANAGEMENT,
            requiresProject = false
        ),
        "/skill" to SlashCommand(
            id = "skill",
            descriptionKey = "command.skill.description",
            mode = CommandMode.MANAGEMENT,
            requiresProject = false
        ),
        "/agent" to SlashCommand(
            id = "agent",
            descriptionKey = "command.agent.description",
            mode = CommandMode.MANAGEMENT,
            requiresProject = false
        ),
        "/config" to SlashCommand(
            id = "config",
            descriptionKey = "command.config.description",
            mode = CommandMode.CONFIG,
            requiresProject = false
        ),
        "/permissions" to SlashCommand(
            id = "permissions",
            descriptionKey = "command.permissions.description",
            mode = CommandMode.CONFIG,
            requiresProject = false
        ),
        "/help" to SlashCommand(
            id = "help",
            descriptionKey = "command.help.description",
            mode = CommandMode.INFO,
            requiresProject = false
        )
    )
    
    fun getCommandDescription(commandId: String): String {
        val command = commands[commandId] ?: return ""
        return i18n.getMessage(command.descriptionKey)
    }
    
    fun searchCommands(query: String): List<SlashCommand> {
        return commands.values.filter { 
            it.id.startsWith(query.removePrefix("/")) 
        }
    }
}
```

#### 国际化资源文件

```properties
# messages/Commands_zh.properties
command.init.description=初始化项目，创建CLAUDE.md配置文件
command.compact.description=压缩上下文，保留关键信息摘要
command.clear.description=清除当前会话，开始新对话
command.context.description=查看当前上下文中的文件和资源
command.cost.description=查看当前会话的Token消耗和成本
command.doctor.description=诊断Claude Code配置和环境问题
command.model.description=切换或查看当前使用的模型
command.think.description=启用思考模式，展示推理过程
command.plan.description=启用计划模式，先规划后执行
command.auto.description=启用自动模式，减少确认步骤
command.resume.description=恢复之前的会话
command.mcp.description=管理MCP服务器配置
command.skill.description=管理Skills技能
command.agent.description=管理SubAgents子代理
command.config.description=查看或修改配置
command.permissions.description=管理权限设置
command.help.description=显示帮助信息
```

```properties
# messages/Commands_en.properties
command.init.description=Initialize project and create CLAUDE.md config file
command.compact.description=Compress context, keep key information summary
command.clear.description=Clear current session, start new conversation
command.context.description=View files and resources in current context
command.cost.description=View token usage and cost for current session
command.doctor.description=Diagnose Claude Code configuration and environment
command.model.description=Switch or view current model
command.think.description=Enable thinking mode, show reasoning process
command.plan.description=Enable plan mode, plan before execute
command.auto.description=Enable auto mode, reduce confirmation steps
command.resume.description=Resume previous session
command.mcp.description=Manage MCP server configuration
command.skill.description=Manage Skills
command.agent.description=Manage SubAgents
command.config.description=View or modify configuration
command.permissions.description=Manage permission settings
command.help.description=Show help information
```

#### 指令补全UI

```kotlin
class CommandCompletionPopup(
    private val editor: EditorTextField,
    private val registry: CommandRegistry
) {
    private val popup = JBPopupFactory.getInstance()
        .createListPopup(
            CommandListPopupStep(registry, editor)
        )
    
    fun show() {
        popup.showUnderneathOf(editor)
    }
    
    fun filter(query: String) {
        // 根据输入过滤指令列表
        (popup as ListPopup).setFilter(query)
    }
}

class CommandListPopupStep(
    private val registry: CommandRegistry,
    private val editor: EditorTextField
) : BaseListPopupStep<SlashCommand>() {
    
    override fun getSelectedValue(): SlashCommand? = selectedItem
    
    override fun onChosen(command: SlashCommand, finalChoice: Boolean): PopupStep<*>? {
        // 插入选中的指令
        editor.document.insertString(editor.caretOffset, command.id + " ")
        return PopupStep.FINAL_CHOICE
    }
    
    override fun getTextFor(command: SlashCommand): String {
        return "${command.id} - ${registry.getCommandDescription(command.id)}"
    }
}
```

---

## 7. 数据流与交互流程

### 7.1 对话发起流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  用户    │     │  UI层    │     │ 服务层   │     │ Claude   │
│  输入    │     │          │     │          │     │  CLI     │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 输入消息       │                │                │
     │ (可能含代码)   │                │                │
     ├───────────────→│                │                │
     │                │ 解析输入       │                │
     │                │ 检测代码选择   │                │
     │                ├───────────────→│                │
     │                │                │ 构建上下文     │
     │                │                │ 注入配置       │
     │                │                ├───────────────→│
     │                │                │                │
     │                │                │  流式响应      │
     │                │                │←───────────────┤
     │                │  增量更新UI    │                │
     │                │←───────────────┤                │
     │  显示响应      │                │                │
     │←───────────────┤                │                │
     │                │                │                │
```

### 7.2 会话切换流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  用户    │     │SessionSvc│     │ Storage  │     │  内存    │
│  操作    │     │          │     │          │     │  缓存    │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 切换会话       │                │                │
     ├───────────────→│                │                │
     │                │ 暂存当前会话   │                │
     │                ├───────────────────────────────→│
     │                │                │                │
     │                │ 检查目标会话   │                │
     │                ├───────────────→│                │
     │                │                │                │
     │                │ 加载会话数据   │                │
     │                │←───────────────┤                │
     │                │                │                │
     │                │ 恢复到内存     │                │
     │                ├───────────────────────────────→│
     │                │                │                │
     │  更新UI        │                │                │
     │←───────────────┤                │                │
     │                │                │                │
```

### 7.3 配置热更新流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  用户    │     │ConfigSvc │     │EventBus  │     │ 各模块   │
│  配置    │     │          │     │          │     │ 监听器   │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 修改配置       │                │                │
     ├───────────────→│                │                │
     │                │ 验证配置       │                │
     │                ├────────┐       │                │
     │                │        │       │                │
     │                │←───────┘       │                │
     │                │                │                │
     │                │ 保存配置       │                │
     │                ├────────┐       │                │
     │                │        │       │                │
     │                │←───────┘       │                │
     │                │                │                │
     │                │ 发布变更事件   │                │
     │                ├───────────────→│                │
     │                │                │ 广播事件       │
     │                │                ├───────────────→│
     │                │                │                │
     │                │                │  应用新配置    │
     │                │                │←───────────────┤
     │                │                │                │
     │  配置生效      │                │                │
     │←───────────────┤                │                │
     │                │                │                │
```

### 7.4 Commit生成流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  用户    │     │CommitGen │     │ Git4Idea │     │ Claude   │
│  操作    │     │          │     │          │     │ Client   │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 触发生成       │                │                │
     ├───────────────→│                │                │
     │                │ 获取暂存变更   │                │
     │                ├───────────────→│                │
     │                │                │                │
     │                │ 返回Diff       │                │
     │                │←───────────────┤                │
     │                │                │                │
     │                │ 分析变更       │                │
     │                ├────────┐       │                │
     │                │        │       │                │
     │                │←───────┘       │                │
     │                │                │                │
     │                │ 构建Prompt     │                │
     │                ├───────────────────────────────→│
     │                │                │                │
     │                │ 生成Commit消息 │                │
     │                │←───────────────────────────────┤
     │                │                │                │
     │  显示预览      │                │                │
     │←───────────────┤                │                │
     │                │                │                │
     │ 确认提交       │                │                │
     ├───────────────→│                │                │
     │                │ 执行Git Commit │                │
     │                ├───────────────→│                │
     │                │                │                │
     │  提交成功      │                │                │
     │←───────────────┤                │                │
     │                │                │                │
```

### 7.5 Token统计流程

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ Claude   │     │UsageTrack│     │ Storage  │     │  UI      │
│ Response │     │          │     │          │     │ Dashboard│
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ API响应        │                │                │
     │ (含Usage)      │                │                │
     ├───────────────→│                │                │
     │                │ 提取Usage      │                │
     │                ├────────┐       │                │
     │                │        │       │                │
     │                │←───────┘       │                │
     │                │                │                │
     │                │ 计算成本       │                │
     │                ├────────┐       │                │
     │                │        │       │                │
     │                │←───────┘       │                │
     │                │                │                │
     │                │ 持久化记录     │                │
     │                ├───────────────→│                │
     │                │                │                │
     │                │ 聚合统计       │                │
     │                ├────────┐       │                │
     │                │        │       │                │
     │                │←───────┘       │                │
     │                │                │                │
     │                │ 更新Dashboard  │                │
     │                ├───────────────────────────────→│
     │                │                │                │
     │                │                │  显示统计     │
     │                │                │                │
```

---

## 8. 轻量化技术选型

### 8.1 依赖对比与选择

| 功能领域 | 原方案 | 优化方案 | 节省 |
|----------|--------|----------|------|
| 数据存储 | SQLite (~2MB) | PersistentStateComponent (0) | 2MB |
| 异步框架 | RxJava + Coroutines | 仅Coroutines | ~1MB |
| JSON解析 | Gson + Jackson | 仅Gson | ~500KB |
| HTTP客户端 | OkHttp + Retrofit | Platform HttpRequests | ~800KB |
| 主题引擎 | 自建 | Platform Theme API | ~200KB |
| **总计** | - | - | **~4.5MB** |

### 8.2 最终技术栈

| 类别 | 技术 | 用途 | 来源 |
|------|------|------|------|
| **开发语言** | Kotlin | 主要开发语言 | JetBrains |
| **UI框架** | IntelliJ Platform SDK | 插件开发框架 | JetBrains |
| **异步框架** | Kotlin Coroutines | 异步任务处理 | Kotlin |
| **JSON解析** | Gson | JSON序列化/反序列化 | Google |
| **配置存储** | PersistentStateComponent | 配置持久化 | Platform |
| **HTTP请求** | HttpRequests | API调用 | Platform |
| **Git操作** | Git4Idea API | Git集成 | Platform |
| **事件总线** | MessageBus | 模块通信 | Platform |
| **国际化** | ResourceBundle | 多语言支持 | JDK |

### 8.3 项目结构

```
claude-code-plugin/
├── src/
│   └── main/
│       ├── resources/
│       │   ├── META-INF/
│       │   │   └── plugin.xml
│       │   ├── messages/
│       │   │   ├── Commands_zh.properties
│       │   │   └── Commands_en.properties
│       │   └── icons/
│       │       └── claude.svg
│       └── kotlin/
│           └── com/github/claudecode/
│               ├── ClaudeCodePlugin.kt
│               ├── ui/
│               │   ├── toolwindow/
│               │   │   ├── ClaudeToolWindowFactory.kt
│               │   │   ├── ChatPanel.kt
│               │   │   ├── InputPanel.kt
│               │   │   └── MessageList.kt
│               │   ├── dialog/
│               │   │   ├── SettingsDialog.kt
│               │   │   └── CommitDialog.kt
│               │   ├── popup/
│               │   │   └── CommandCompletionPopup.kt
│               │   └── action/
│               │       ├── GenerateCommitAction.kt
│               │       └── SendToClaudeAction.kt
│               ├── service/
│               │   ├── SessionService.kt
│               │   ├── ConfigService.kt
│               │   ├── ContextManager.kt
│               │   ├── UsageTracker.kt
│               │   └── CommandExecutor.kt
│               ├── client/
│               │   ├── ClaudeClient.kt
│               │   ├── StreamParser.kt
│               │   └── ProcessManager.kt
│               ├── model/
│               │   ├── Session.kt
│               │   ├── Message.kt
│               │   ├── Config.kt
│               │   └── Usage.kt
│               ├── storage/
│               │   ├── SessionStorage.kt
│               │   └── UsageStorage.kt
│               └── util/
│                   ├── EventBus.kt
│                   ├── I18NManager.kt
│                   └── GitAdapter.kt
├── build.gradle.kts
└── gradle.properties
```

### 8.4 build.gradle.kts 配置

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.github.claudecode"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2024.2")
        plugin("Git4Idea")
    }
    
    // 最小化外部依赖
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // 测试依赖
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.0")
}

intellijPlatform {
    pluginConfiguration {
        name = "Claude Code"
        version = "1.0.0"
        
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "243.*"
        }
    }
}

tasks {
    // 优化构建大小
    jar {
        exclude("**/*.map")
        exclude("**/*.md")
    }
}
```

### 8.5 plugin.xml 配置

```xml
<idea-plugin>
    <id>com.github.claudecode</id>
    <name>Claude Code</name>
    <vendor>Claude Code Plugin Team</vendor>
    <description>
        Claude Code integration for JetBrains IDEs.
        Provides AI-powered coding assistance with Claude.
    </description>
    
    <depends>com.intellij.modules.platform</depends>
    <depends>Git4Idea</depends>
    
    <extensions defaultExtensionNs="com.intellij">
        <!-- Tool Window -->
        <toolWindow 
            id="Claude Code"
            factoryClass="...ui.toolwindow.ClaudeToolWindowFactory"
            anchor="right"
            icon="/icons/claude.svg"/>
        
        <!-- Settings -->
        <applicationConfigurable 
            parentId="tools"
            instance="...ui.dialog.SettingsConfigurable"
            id="claude.code.settings"
            displayName="Claude Code"/>
        
        <!-- Services (延迟加载) -->
        <applicationService 
            serviceImplementation="...service.ConfigService"
            preload="false"/>
        <applicationService 
            serviceImplementation="...client.ProcessManager"
            preload="false"/>
        
        <projectService 
            serviceImplementation="...service.SessionService"/>
        <projectService 
            serviceImplementation="...service.ContextManager"/>
        <projectService 
            serviceImplementation="...service.UsageTracker"/>
        
        <!-- Storage -->
        <applicationService 
            serviceImplementation="...storage.SessionStorage"/>
        <applicationService 
            serviceImplementation="...storage.UsageStorage"/>
    </extensions>
    
    <actions>
        <!-- Commit生成Action -->
        <action 
            id="ClaudeCode.GenerateCommit"
            class="...ui.action.GenerateCommitAction"
            text="Generate Commit Message"
            description="Generate commit message with Claude">
            <add-to-group group-id="Vcs.MessageActionGroup"/>
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift G"/>
        </action>
        
        <!-- 发送到Claude Action -->
        <action 
            id="ClaudeCode.SendToClaude"
            class="...ui.action.SendToClaudeAction"
            text="Send to Claude"
            description="Send selected code to Claude">
            <add-to-group group-id="EditorPopupMenu"/>
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift C"/>
        </action>
    </actions>
</idea-plugin>
```

---

## 9. 开发任务拆分与里程碑

### 9.1 里程碑规划

| 里程碑 | 周期 | 核心交付物 | 验收标准 |
|--------|------|------------|----------|
| **M1: 基础框架** | 2周 | 项目骨架、UI框架、基础服务 | Tool Window可打开 |
| **M2: 对话核心** | 3周 | 对话功能、流式输出、会话管理 | 可正常对话 |
| **M3: 配置系统** | 2周 | 配置管理、热更新、模型切换 | 配置即时生效 |
| **M4: 生态集成** | 3周 | Skills、SubAgents、MCP管理 | 生态功能可用 |
| **M5: 辅助功能** | 2周 | Commit生成、Token统计、指令集成 | 功能完整 |
| **M6: 优化发布** | 2周 | 性能优化、Bug修复、文档完善 | 可发布状态 |

### 9.2 详细任务拆分

#### M1: 基础框架 (2周)

| 任务ID | 任务描述 | 预估工时 | 依赖 |
|--------|----------|----------|------|
| M1-1 | 项目初始化、Gradle配置 | 4h | - |
| M1-2 | plugin.xml配置 | 2h | M1-1 |
| M1-3 | Tool Window框架搭建 | 8h | M1-2 |
| M1-4 | 基础UI组件（输入框、消息列表） | 12h | M1-3 |
| M1-5 | ConfigService实现 | 6h | M1-2 |
| M1-6 | EventBus实现 | 4h | M1-2 |
| M1-7 | 国际化框架搭建 | 4h | M1-2 |

#### M2: 对话核心 (3周)

| 任务ID | 任务描述 | 预估工时 | 依赖 |
|--------|----------|----------|------|
| M2-1 | ClaudeClient实现 | 16h | M1-5 |
| M2-2 | ProcessManager实现 | 12h | M1-5 |
| M2-3 | StreamParser实现 | 8h | M2-1 |
| M2-4 | SessionService实现 | 16h | M2-1 |
| M2-5 | 会话持久化 | 8h | M2-4 |
| M2-6 | 流式渲染UI | 12h | M2-3 |
| M2-7 | 会话打断功能 | 8h | M2-4 |
| M2-8 | 代码选择发送 | 8h | M1-4 |
| M2-9 | 多模态输入支持 | 12h | M1-4 |

#### M3: 配置系统 (2周)

| 任务ID | 任务描述 | 预估工时 | 依赖 |
|--------|----------|----------|------|
| M3-1 | Settings UI实现 | 12h | M1-5 |
| M3-2 | 配置热更新机制 | 8h | M3-1 |
| M3-3 | 供应商配置模板 | 6h | M3-1 |
| M3-4 | 模型切换功能 | 4h | M3-1 |
| M3-5 | 对话模式切换 | 6h | M3-4 |
| M3-6 | 主题配置 | 8h | M1-4 |

#### M4: 生态集成 (3周)

| 任务ID | 任务描述 | 预估工时 | 依赖 |
|--------|----------|----------|------|
| M4-1 | Skills加载器 | 12h | M2-1 |
| M4-2 | Skills管理UI | 8h | M4-1 |
| M4-3 | Skills导入导出 | 6h | M4-2 |
| M4-4 | SubAgents配置解析 | 8h | M2-1 |
| M4-5 | SubAgents监控UI | 12h | M4-4 |
| M4-6 | MCP配置管理 | 8h | M2-2 |
| M4-7 | MCP生命周期管理 | 12h | M4-6 |
| M4-8 | 作用域配置合并 | 8h | M3-2 |

#### M5: 辅助功能 (2周)

| 任务ID | 任务描述 | 预估工时 | 依赖 |
|--------|----------|----------|------|
| M5-1 | GitAdapter实现 | 8h | M2-1 |
| M5-2 | Diff分析器 | 8h | M5-1 |
| M5-3 | Commit生成器 | 8h | M5-2 |
| M5-4 | Commit Dialog UI | 6h | M5-3 |
| M5-5 | UsageTracker实现 | 8h | M2-1 |
| M5-6 | Usage Dashboard UI | 8h | M5-5 |
| M5-7 | 预算管理 | 6h | M5-5 |
| M5-8 | 指令注册表 | 4h | M1-7 |
| M5-9 | 指令补全Popup | 8h | M5-8 |

#### M6: 优化发布 (2周)

| 任务ID | 任务描述 | 预估工时 | 依赖 |
|--------|----------|----------|------|
| M6-1 | 内存优化 | 8h | M1~M5 |
| M6-2 | 启动优化 | 6h | M1~M5 |
| M6-3 | 异常处理完善 | 8h | M1~M5 |
| M6-4 | 单元测试 | 16h | M1~M5 |
| M6-5 | 集成测试 | 8h | M6-4 |
| M6-6 | 文档编写 | 8h | M1~M5 |
| M6-7 | 发布准备 | 4h | M6-1~M6-6 |

### 9.3 总工时估算

| 里程碑 | 工时 | 日历周 |
|--------|------|--------|
| M1 | 40h | 2周 |
| M2 | 100h | 3周 |
| M3 | 44h | 2周 |
| M4 | 74h | 3周 |
| M5 | 56h | 2周 |
| M6 | 58h | 2周 |
| **总计** | **372h** | **14周** |

---

## 10. 风险评估与应对策略

### 10.1 技术风险

| 风险 | 影响 | 概率 | 应对策略 |
|------|------|------|----------|
| Claude CLI版本兼容性 | 高 | 中 | 版本检测、兼容层适配 |
| IntelliJ API变更 | 中 | 低 | 版本范围限制、兼容性测试 |
| 流式输出解析错误 | 中 | 中 | 健壮解析器、错误恢复机制 |
| 进程管理复杂度 | 高 | 中 | 使用Platform API、完善异常处理 |

### 10.2 产品风险

| 风险 | 影响 | 概率 | 应对策略 |
|------|------|------|----------|
| 功能与CLI差异 | 中 | 高 | 明确定位、文档说明 |
| 用户学习成本 | 中 | 中 | 引导流程、快捷操作 |
| 性能体验不佳 | 高 | 低 | 性能监控、持续优化 |

### 10.3 项目风险

| 风险 | 影响 | 概率 | 应对策略 |
|------|------|------|----------|
| 开发周期超期 | 高 | 中 | 敏捷开发、分阶段交付 |
| 依赖库问题 | 中 | 低 | 最小化依赖、备选方案 |
| 测试覆盖不足 | 中 | 中 | 自动化测试、代码审查 |

### 10.4 安全风险

| 风险 | 影响 | 概率 | 应对策略 |
|------|------|------|----------|
| API密钥泄露 | 高 | 低 | 加密存储、不记录日志 |
| 会话数据安全 | 中 | 低 | 本地加密、自动清理 |
| 网络通信安全 | 高 | 低 | HTTPS强制、证书验证 |

### 10.5 运营风险

| 风险 | 影响 | 概率 | 应对策略 |
|------|------|------|----------|
| 用户支持需求 | 中 | 高 | 完善文档、FAQ |
| 版本更新问题 | 中 | 中 | 兼容性保证、回滚机制 |
| 插件审核延迟 | 低 | 中 | 提前准备、规范开发 |

---

## 附录

### A. Claude Code指令完整列表

| 指令 | 中文描述 | 英文描述 | 类型 |
|------|----------|----------|------|
| `/init` | 初始化项目 | Initialize project | 系统 |
| `/compact` | 压缩上下文 | Compress context | 会话 |
| `/clear` | 清除会话 | Clear conversation | 会话 |
| `/context` | 查看上下文 | Show context | 信息 |
| `/cost` | 查看成本 | Show cost | 信息 |
| `/doctor` | 诊断问题 | Run diagnostics | 诊断 |
| `/model` | 切换模型 | Switch model | 配置 |
| `/think` | 思考模式 | Thinking mode | 模式 |
| `/plan` | 计划模式 | Plan mode | 模式 |
| `/auto` | 自动模式 | Auto mode | 模式 |
| `/resume` | 恢复会话 | Resume session | 会话 |
| `/mcp` | MCP管理 | Manage MCP | 管理 |
| `/skill` | Skills管理 | Manage skills | 管理 |
| `/agent` | Agent管理 | Manage agents | 管理 |
| `/config` | 配置管理 | Manage config | 配置 |
| `/permissions` | 权限管理 | Manage permissions | 配置 |
| `/help` | 帮助信息 | Show help | 信息 |

### B. 支持的模型列表

| 模型ID | 名称 | 输入价格($/M) | 输出价格($/M) | 推荐场景 |
|--------|------|---------------|---------------|----------|
| `claude-opus-4-20250514` | Claude Opus 4 | 15.00 | 75.00 | 复杂任务 |
| `claude-sonnet-4-20250514` | Claude Sonnet 4 | 3.00 | 15.00 | 日常开发 |
| `claude-3-5-haiku-20241022` | Claude 3.5 Haiku | 0.80 | 4.00 | 快速响应 |

### C. 配置文件示例

```json
{
    "api": {
        "provider": "anthropic",
        "endpoint": "https://api.anthropic.com",
        "timeout": 120000
    },
    "model": {
        "default": "claude-sonnet-4-20250514",
        "fallback": "claude-3-5-haiku-20241022"
    },
    "mode": {
        "default": "auto",
        "thinkingBudget": 10000
    },
    "ui": {
        "theme": "follow-ide",
        "fontSize": 14,
        "showTokenCount": true,
        "enableMarkdown": true
    },
    "usage": {
        "dailyLimit": 10.0,
        "monthlyLimit": 200.0,
        "alertThresholds": [0.5, 0.8, 0.9]
    }
}
```

### D. API响应格式示例

```json
{
    "id": "msg_01XYZ",
    "type": "message",
    "role": "assistant",
    "model": "claude-sonnet-4-20250514",
    "content": [
        {
            "type": "text",
            "text": "Hello! How can I help you today?"
        }
    ],
    "usage": {
        "input_tokens": 150,
        "output_tokens": 20,
        "cache_creation_input_tokens": 0,
        "cache_read_input_tokens": 0
    },
    "stop_reason": "end_turn"
}
```

### E. 轻量化检查清单

| 检查项 | 标准 | 状态 |
|--------|------|------|
| 插件包大小 | < 5MB | ⬜ |
| 启动时间增量 | < 500ms | ⬜ |
| 内存占用 | < 100MB | ⬜ |
| 外部依赖数 | < 5个 | ⬜ |
| 冷启动响应 | < 2s | ⬜ |
| 无SQLite依赖 | ✅ | ⬜ |
| 无RxJava依赖 | ✅ | ⬜ |
| 使用Platform HTTP | ✅ | ⬜ |
| 使用Platform存储 | ✅ | ⬜ |
| 服务延迟加载 | ✅ | ⬜ |

---

> **文档版本**: v3.0 (轻量化优化版)  
> **最后更新**: 2026-04-11  
> **维护者**: Claude Code IDEA Plugin Team
