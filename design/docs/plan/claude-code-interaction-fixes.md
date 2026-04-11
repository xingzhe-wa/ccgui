# Claude Code 交互机制修复文档

**日期**: 2026-04-11
**版本**: CC Assistant v0.0.1
**状态**: ✅ 已完成

---

## 一、问题概述

经过系统性检查，发现以下 Claude Code 交互机制存在实现缺陷：

| 模块 | 原状态 | 问题 |
|------|--------|------|
| 供应商配置传递 | ❌ 缺失 | API Key/Base URL 未传递给 CLI |
| Agent 指定 | ❌ 缺失 | Agent 选择只存 store，未传递给 SDK |
| MCP 配置 | ❌ 缺失 | MCP 服务器配置未连接到消息发送流程 |
| LOCAL_SETTINGS | ❌ 缺失 | 未实现从 `~/.claude/settings.json` 读取配置 |
| 流式输出 | ✅ 完整 | - |
| Skills | ✅ 完整 | - |

---

## 二、修复清单

### 2.1 供应商配置传递 → CLI

**问题原因**:
- `ConfigManager.getActiveModelConfig()` 返回的配置从未被使用
- `ClaudeCodeClient.sendMessage()` 只读取 `options.env`，未填充 API Key/Base URL

**修复方案**:
```kotlin
// ClaudeCodeClient.sendMessage() 第111-152行
val modelConfig = ConfigManager.getInstance(project).getActiveModelConfig()

// 填充环境变量
modelConfig.apiKey?.let { configEnv["ANTHROPIC_AUTH_TOKEN"] = it }
modelConfig.baseUrl?.let { configEnv["ANTHROPIC_BASE_URL"] = it }

// 填充模型参数
val effectiveOptions = if (options.model.isNullOrBlank()) {
    options.copy(model = modelConfig.model)
} else options
```

**涉及文件**:
- `ClaudeCodeClient.kt` - 添加配置读取和传递逻辑
- `ConfigManager.kt` - 添加 `LOCAL_SETTINGS` 特殊处理

---

### 2.2 LOCAL_SETTINGS 功能实现

**问题原因**:
- `SpecialProviderIds.LOCAL_SETTINGS` 已定义但从未被使用
- 未实现从 `~/.claude/settings.json` 读取配置的逻辑

**修复方案**:

1. **新增 `LocalSettingsReader.kt`**:
```kotlin
object LocalSettingsReader {
    fun readSettingsJson(): JsonObject?  // 读取 ~/.claude/settings.json
    fun getAuthToken(): String?           // 提取 ANTHROPIC_AUTH_TOKEN
    fun getBaseUrl(): String?             // 提取 ANTHROPIC_BASE_URL
    fun getSonnetModel(): String?          // 提取 ANTHROPIC_DEFAULT_SONNET_MODEL
    fun getAllProviders(): List<LocalProvider>  // 获取所有 Provider
}
```

2. **更新 `ConfigManager.getActiveModelConfig()`**:
```kotlin
when (activeProfileId) {
    SpecialProviderIds.LOCAL_SETTINGS -> {
        val localProvider = LocalSettingsReader.getAllProviders().firstOrNull()
        return ModelConfig(
            provider = "anthropic",
            apiKey = localProvider.authToken,
            baseUrl = localProvider.baseUrl,
            sonnetModel = localProvider.sonnetModel,
            ...
        )
    }
}
```

**涉及文件**:
- `util/LocalSettingsReader.kt` - **新增**，读取本地 Claude CLI 设置

---

### 2.3 Agent → SDK 连接

**问题原因**:
- `CefBrowserPanel` 的 `chatConfig` 是局部变量，其他组件无法访问
- `ChatOrchestrator.sendMessage()` 从不读取当前选中的 Agent

**修复方案**:

1. **新增 `ChatConfigManager.kt`** - 聊天配置管理器（单例）:
```kotlin
class ChatConfigManager {
    private val _currentAgentId = MutableStateFlow<String?>(null)
    private val _conversationMode = MutableStateFlow(ConversationMode.AUTO)
    private val _streamingEnabled = MutableStateFlow(true)

    fun setCurrentAgent(agentId: String?)
    fun getCurrentAgentId(): String?
}
```

2. **更新 `ChatOrchestrator.buildSdkOptions()`**:
```kotlin
private fun buildSdkOptions(sessionId: String, context: SessionContext): SdkOptions {
    val currentAgentId = chatConfigManager.getCurrentAgentId()
    val agent = currentAgentId?.let { agentsManager.getAgent(it) }

    // 合并 Agent 的系统提示
    val systemPrompt = if (agent != null && agent.systemPrompt.isNotBlank()) {
        buildString {
            append(baseSystemPrompt)
            append("\n\n--- Agent Configuration ---\n")
            append("Agent: ${agent.name}\n")
            append(agent.systemPrompt)
        }
    } else {
        baseSystemPrompt
    }

    // 传递 Agent 的工具列表
    val allowedTools = agent?.tools?.takeIf { it.isNotEmpty() }

    return sdkSessionManager.buildResumeOptions(sessionId, SdkOptions(
        systemPrompt = systemPrompt,
        allowedTools = allowedTools
    ))
}
```

**涉及文件**:
- `application/chat/ChatConfigManager.kt` - **新增**，聊天配置管理器
- `CefBrowserPanel.kt` - 使用 `ChatConfigManager` 替代局部变量
- `ChatOrchestrator.kt` - 添加 Agent 配置读取和传递

---

### 2.4 MCP → SDK 连接

**问题原因**:
- `McpServerManager` 管理 MCP 服务器状态
- 但 `ChatOrchestrator.sendMessage()` 从不构建 `SdkOptions.mcpConfig`

**修复方案**:

1. **在 `ChatOrchestrator` 中添加 `buildMcpConfig()`**:
```kotlin
private fun buildMcpConfig(): McpServersConfig? {
    val enabledServers = mcpServerManager.getAllServers()
        .filter { it.enabled && it.isConnected }
    if (enabledServers.isEmpty()) return null

    val servers = enabledServers.associate { server ->
        server.name to McpServersConfig.McpServerEntry(
            command = server.command,
            args = server.args,
            env = server.env
        )
    }
    return McpServersConfig(servers)
}
```

2. **在 `buildSdkOptions()` 中调用**:
```kotlin
return sdkSessionManager.buildResumeOptions(sessionId, SdkOptions(
    systemPrompt = systemPrompt,
    allowedTools = allowedTools,
    mcpConfig = buildMcpConfig()  // 添加 MCP 配置
))
```

**涉及文件**:
- `ChatOrchestrator.kt` - 添加 `buildMcpConfig()` 和传递 MCP 配置

---

## 三、修复后的数据流

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (JS/React)                          │
│  userAgent.currentAgentId → ChatConfigManager.setCurrentAgent() │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│              CefBrowserPanel.handleSendMessage()                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│         ChatOrchestrator.sendMessage()                          │
│                                                               │
│  1. chatConfigManager.getCurrentAgentId()                      │
│  2. agentsManager.getAgent(agentId)                            │
│     → agent.systemPrompt → 合并到 SdkOptions.systemPrompt      │
│     → agent.tools → SdkOptions.allowedTools                    │
│                                                               │
│  3. mcpServerManager.getAllServers()                           │
│     → filter { it.enabled && it.isConnected }                  │
│     → buildMcpConfig() → SdkOptions.mcpConfig                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│        ClaudeCodeClient.sendMessage()                           │
│                                                               │
│  1. ConfigManager.getActiveModelConfig()                       │
│     → ANTHROPIC_AUTH_TOKEN → process.env                      │
│     → ANTHROPIC_BASE_URL → process.env                        │
│     → model → --model 参数                                     │
│                                                               │
│  2. SdkOptions.mcpConfig → --mcp-config <tempfile>           │
│                                                               │
│  3. ProcessBuilder.start("claude -p <prompt> ...")            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                     Claude Code CLI                              │
│  claude -p <prompt> --output-format stream-json ...          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、配置优先级

| 来源 | 优先级 | 说明 |
|------|--------|------|
| `SdkOptions` (代码显式传入) | 最高 | 直接传递给 CLI |
| `ChatConfigManager` (Agent 配置) | 中 | 合并到 systemPrompt |
| `ConfigManager` (ProviderProfile) | 中 | API Key/Base URL/Model |
| `LocalSettingsReader` (~/.claude/settings.json) | 中低 | 本地 CLI 配置 |
| 环境变量 (`ANTHROPIC_API_KEY` 等) | 最低 | 系统环境变量 |

---

## 五、涉及文件变更汇总

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `util/LocalSettingsReader.kt` | **新增** | 读取本地 Claude CLI 设置 |
| `application/chat/ChatConfigManager.kt` | **新增** | 聊天配置管理器 |
| `ClaudeCodeClient.kt` | 修改 | 添加配置读取和传递 |
| `ConfigManager.kt` | 修改 | 添加 LOCAL_SETTINGS 处理 |
| `CefBrowserPanel.kt` | 修改 | 使用 ChatConfigManager |
| `ChatOrchestrator.kt` | 修改 | 添加 Agent/MCP 配置传递 |

---

## 六、验证方法

### 6.1 验证供应商配置传递
1. 在插件设置中配置 API Key 和 Base URL
2. 发送消息，观察日志:
   ```
   Using API config: provider=anthropic, model=claude-sonnet-4-20250514,
   hasApiKey=true, hasBaseUrl=true
   ```

### 6.2 验证 Agent 配置传递
1. 在聊天界面选择 Agent
2. 发送消息，观察 CLI 命令是否包含 Agent 的 systemPrompt

### 6.3 验证 MCP 配置传递
1. 启动 MCP 服务器（连接状态变为 connected）
2. 发送消息，观察日志确认 MCP 配置被写入临时文件

### 6.4 验证 LOCAL_SETTINGS
1. 确保 `~/.claude/settings.json` 存在且包含有效的 provider 配置
2. 在插件中选择 "Local Settings" profile
3. 发送消息，确认使用本地配置的 API Key

---

## 七、待优化项

以下功能尚未完全实现，仅做了基础架构预留：

1. **对话模式 (THINKING/PLANNING)**: `ChatConfigManager` 已存储 `conversationMode`，但未影响 CLI 调用参数
2. **Agent 模式 (CAUTIOUS/BALANCED/AGGRESSIVE)**: 可根据 mode 调整 CLI 的 `--allowedTools` 或 `--max-turns`
3. **流式输出开关**: `ChatConfigManager.streamingEnabled` 已存储，但流式输出硬编码启用

---

**文档版本**: 1.0
**编写者**: Claude Code
**审核者**: -
