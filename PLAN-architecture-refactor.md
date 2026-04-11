# 项目重构执行计划

## 一、现状分析

### 1.1 架构文档核心要求（v3.0 轻量化版）

| 约束指标 | 目标值 | 状态 |
|----------|--------|------|
| 插件包大小 | < 5MB | 待验证 |
| 启动增量 | < 500ms | ⚠️ 当前全部服务预加载 |
| 内存占用 | < 100MB | 待验证 |
| 外部依赖数 | < 5个 | ❌ 6个 Ktor 依赖 |
| 冷启动响应 | < 2s | 待验证 |

### 1.2 三层架构模块划分

```
架构文档定义：
├── 表现层 (Presentation): ToolWindowFactory, Dialog, EditorProvider, ActionGroup, ConfigUIBuilder, PopupFactory
├── 服务层 (Service): SessionService, ConfigService, ClaudeClient, ContextManager, UsageTracker, CommandExecutor
└── 基础层 (Infrastructure): StorageService, ProcessManager, EventBus, HttpClient, GitAdapter, I18NManager

当前项目实际：
├── adaptation/sdk/: ClaudeCodeClient, SdkSessionManager (属于服务层)
├── application/: 核心服务 (session, streaming, config, mcp, agent, skill, commit, usage 等)
├── bridge/: DaemonBridge, AgentSdkBridge, BridgeManager (混合层 - 需重构)
├── infrastructure/: storage, process, eventbus, git (基础层)
└── ui/: toolWindow, action (表现层)
```

---

## 二、差距分析报告

### P0 - 阻断性问题

#### 1. 外部依赖超标 (6个 Ktor 依赖)
```
当前依赖：
❌ io.ktor:ktor-client-core:2.3.7 (~1.2MB)
❌ io.ktor:ktor-client-okhttp:2.3.7 (~0.5MB)  ← 使用 OkHttp 引擎
❌ io.ktor:ktor-client-content-negotiation:2.3.7
❌ io.ktor:ktor-serialization-gson:2.3.7
✅ com.google.code.gson:gson:2.10.1 (保留)

架构文档要求：
✅ 使用 Platform HttpRequests 替代 OkHttp/Retrofit
```

**影响**：
- 包大小增加 ~2MB+
- 违反轻量化原则
- AgentSdkBridge 依赖 Ktor，需要重构

#### 2. 服务全部预加载 (无延迟加载)
```xml
<!-- plugin.xml 当前状态：全部 projectService，无 preload="false" -->
<projectService serviceImplementation="com.github.xingzhewa.ccgui.bridge.BridgeManager"/>
<projectService serviceImplementation="com.github.xingzhewa.ccgui.config.CCGuiConfig"/>
... 共 28 个 projectService，全部预加载
```

**影响**：
- 启动增量 > 500ms
- 内存占用 > 100MB
- 违反"按需加载"原则

---

### P1 - 严重问题

#### 3. AgentSdkBridge 废弃但未清理
- 使用 Ktor + OkHttp (已废弃)
- 与 DaemonBridge 功能重叠
- 应统一使用 DaemonBridge (stdin/stdout/NDJSON)

#### 4. 废弃文件未清理
- `bridge/AgentSdkBridge.kt` - 仍使用 Ktor
- `bridge/DaemonBridge.kt` - 新的 NDJSON 方案
- 两套 bridge 并存，需确定主方案

---

### P2 - 优化建议

#### 5. 缺少 I18nService 服务注册
- `I18nManager.kt` 存在但未在 plugin.xml 中注册为服务

#### 6. 部分 @State 类使用 @Service 但未配置延迟加载
- 需要统一检查所有 PersistentStateComponent 实现

---

## 三、重构执行计划

### P0 阶段：依赖清理与服务延迟加载

| 任务ID | 任务 | 修改文件 | 预估工时 |
|--------|------|----------|----------|
| P0-1 | 移除 Ktor 依赖，改用 Platform HttpRequests | build.gradle.kts | 1h |
| P0-2 | 删除 AgentSdkBridge (已废弃) | bridge/AgentSdkBridge.kt | 0.5h |
| P0-3 | 为所有 projectService 添加 preload="false" | plugin.xml | 0.5h |
| P0-4 | 将不需要立即加载的服务改为 applicationService + preload="false" | plugin.xml | 1h |
| P0-5 | 验证构建成功 | - | 0.5h |

### P1 阶段：架构模块重组

| 任务ID | 任务 | 修改文件 | 预估工时 |
|--------|------|----------|----------|
| P1-1 | 重组 bridge 包职责，统一使用 DaemonBridge | bridge/*.kt | 2h |
| P1-2 | 清理 adaptation/sdk 包，确认 ClaudeCodeClient 实现正确 | adaptation/sdk/*.kt | 1h |
| P1-3 | 检查 application/ 服务层是否遵循三层架构 | application/*.kt | 1h |
| P1-4 | 检查 infrastructure/ 基础层是否完整 | infrastructure/*.kt | 1h |

### P2 阶段：Bug 修复与优化

| 任务ID | 任务 | 修改文件 | 预估工时 |
|--------|------|----------|----------|
| P2-1 | 验证会话管理流式输出修复 | StreamingOutputEngine.kt | 1h |
| P2-2 | 验证上下文管理配置热更新 | HotReloadManager.kt | 1h |
| P2-3 | 验证 MCP 管理生命周期 | McpServerManager.kt | 1h |
| P2-4 | 检查 I18nService 服务注册 | plugin.xml | 0.5h |

### P3 阶段：量化指标验证

| 任务ID | 任务 | 验收标准 | 预估工时 |
|--------|------|----------|----------|
| P3-1 | 验证插件包大小 | < 5MB | 0.5h |
| P3-2 | 验证服务延迟加载生效 | 使用 preload="false" | 0.5h |
| P3-3 | 验证外部依赖数 | < 5个 | 0.5h |

---

## 四、预估工时

| 阶段 | 工时 | 风险点 |
|------|------|--------|
| P0 | 3.5h | 低 - 纯配置修改 |
| P1 | 5h | 中 - 需理解 bridge 职责 |
| P2 | 3.5h | 低 - 已知 bug 修复 |
| P3 | 1.5h | 低 - 验证工作 |
| **总计** | **13.5h** | - |

---

## 五、执行确认

**下一步操作**：
1. 用户确认执行后，进入 **P0-1** 阶段
2. 首先修改 `build.gradle.kts`，移除 Ktor 依赖
3. 然后修改 `plugin.xml`，添加延迟加载配置
4. 每完成一个子任务，输出状态恢复指令

---

> **文档版本**: v1.0
> **创建日期**: 2026-04-12
> **维护者**: Claude Code IDEA Plugin Team
