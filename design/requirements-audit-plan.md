# 需求驱动审计与重构执行计划

> **审计日期**: 2026-04-12
> **架构文档**: Claude_Code_IDEA_Plugin_Technical_Architecture-init.md (v3.0)
> **审计范围**: Chapter 2, 5, 6
> **整体符合度**: ~80%

---

## 一、需求映射总览

| 需求分类 | 功能数 | 已实现 | 部分实现 | 未实现 |
|----------|--------|--------|----------|--------|
| UI交互 | 4 | 4 | 0 | 0 |
| 对话发起 | 5 | 4 | 0 | 1 |
| 会话管理 | 7 | 5 | 1 | 1 |
| 模型配置 | 3 | 3 | 0 | 0 |
| 生态集成 | 4 | 4 | 0 | 0 |
| 辅助功能 | 3 | 3 | 0 | 0 |
| **总计** | **26** | **23** | **1** | **2** |

---

## 二、问题清单（按优先级）

### P0 - 阻断性问题（2项）

#### P0-1: MCP服务器存储加载未实现
- **文档位置**: Chapter 5.7 - MCPManager.loadServersFromStorage()
- **问题描述**: `McpServerManager.loadServersFromStorage()` 只有TODO注释，服务器配置未持久化
- **影响**: 用户配置的MCP服务器重启后丢失
- **修复建议**: 实现MCP服务器的持久化存储，使用PersistentStateComponent

#### P0-2: 会话消息快照机制缺失
- **文档位置**: Chapter 5.1 - 会话回滚需求
- **问题描述**: 缺少消息版本快照机制，无法实现会话回滚功能
- **影响**: 用户无法回到历史状态
- **修复建议**: 在SessionStorage中实现消息快照存储，支持版本化消息内容

---

### P1 - 严重问题（4项）

#### P1-1: 消息引用机制缺失
- **文档位置**: Chapter 2.2.2 - 引用对话需求
- **问题描述**: 缺少消息ID引用机制，无法实现 `引用历史消息` 功能
- **影响**: 无法在对话中引用历史消息
- **修复建议**: 在ChatMessage中添加消息ID，实现引用解析和展示

#### P1-2: Skills存储位置不符合文档
- **文档位置**: Chapter 5.5 - Skills文件结构
- **问题描述**: 当前使用PropertiesComponent而非`.claude/skills/`目录
- **影响**: 与Claude CLI原生目录结构不兼容
- **修复建议**: 实现文件系统加载器，从项目`.claude/skills/`目录读取

#### P1-3: SubAgents存储位置不符合文档
- **文档位置**: Chapter 5.6 - SubAgent配置格式
- **问题描述**: 当前使用PropertiesComponent而非`.claude/agents/`目录
- **影响**: 与Claude CLI原生目录结构不兼容
- **修复建议**: 实现文件系统加载器，从项目`.claude/agents/`目录读取

#### P1-4: CLI Login Provider未实现
- **文档位置**: Chapter 2.2.4 - SpecialProviderIds
- **问题描述**: `SpecialProviderIds.CLI_LOGIN` 标记为"暂未实现"
- **影响**: 无法从Claude CLI读取认证状态
- **修复建议**: 实现CLI认证状态检测，支持CLI登录模式

---

### P2 - 优化建议（3项）

#### P2-1: 会话重命名UI功能不明确
- **文档位置**: Chapter 2.2.3 - 会话重命名需求
- **问题描述**: ChatSession有name字段但UI内联编辑功能未确认
- **修复建议**: 确认ChatView是否支持会话名称内联编辑

#### P2-2: 包名结构与文档不符
- **文档位置**: Chapter 8.3 - 项目结构
- **问题描述**: 文档要求`com.github.claudecode`，实际为`com.github.xingzhewa.ccgui`
- **影响**: 模块导入和组织方式与文档不符
- **修复建议**: 这可能是故意偏离（实际项目名不同），建议更新文档

#### P2-3: WebView前端未在文档中定义
- **文档位置**: 无（文档v3.0未提及）
- **问题描述**: 实际使用React WebView + CefBrowserPanel，文档定义Kotlin Swing UI
- **影响**: 文档与实现不一致
- **修复建议**: 更新文档或重构为Kotlin Swing UI（二选一，建议保持WebView）

---

## 三、架构符合度分析

### 整体符合度: ~80%

| 模块 | 文档章节 | 符合度 | 主要差距 |
|------|----------|--------|----------|
| 会话管理 | 5.1 | 85% | 消息快照缺失 |
| 流式输出 | 5.2 | 90% | - |
| 上下文管理 | 5.3 | 75% | 上下文压缩算法实现不完整 |
| 配置管理 | 5.4 | 80% | CLI Login Provider缺失 |
| Skills管理 | 5.5 | 70% | 存储位置不符合文档 |
| SubAgents管理 | 5.6 | 75% | 存储位置不符合文档 |
| MCP管理 | 5.7 | 70% | 存储加载未实现 |
| Commit生成 | 6.1 | 95% | - |
| Token统计 | 6.2 | 90% | - |
| 指令集成 | 6.3 | 85% | CLI Login Provider缺失 |

---

## 四、重构执行分组

### 组A: 存储与持久化（P0-P1）

| 任务ID | 任务 | 优先级 | 修改文件 |
|--------|------|--------|----------|
| A-1 | 实现MCP服务器持久化存储 | P0 | McpServerManager.kt, 新建McpServerStorage.kt |
| A-2 | 实现会话消息快照机制 | P0 | SessionStorage.kt, ChatMessage.kt |
| A-3 | 实现Skills文件系统加载器 | P1 | SkillsManager.kt |
| A-4 | 实现SubAgents文件系统加载器 | P1 | AgentsManager.kt |

### 组B: 会话与消息（P0-P1）

| 任务ID | 任务 | 优先级 | 修改文件 |
|--------|------|--------|----------|
| B-1 | 实现消息引用机制 | P1 | ChatMessage.kt, SlashCommandParser.kt |
| B-2 | 完善会话重命名UI | P2 | ChatView.tsx (webview) |

### 组C: 配置与认证（P1-P2）

| 任务ID | 任务 | 优先级 | 修改文件 |
|--------|------|--------|----------|
| C-1 | 实现CLI Login Provider | P1 | ConfigManager.kt, ProviderProfile.kt |
| C-2 | 更新架构文档包名 | P2 | 文档更新 |

---

## 五、执行计划

| 阶段 | 时间 | 任务 |
|------|------|------|
| **Phase 1** | P0任务 | MCP持久化 + 会话快照 |
| **Phase 2** | P1任务 | 消息引用 + 文件系统加载器 + CLI Login |
| **Phase 3** | P2任务 | UI完善 + 文档更新 |

---

## 六、修复任务详情

### A-1: MCP服务器持久化存储 [P0]

**问题**: `McpServerManager.loadServersFromStorage()` 只有TODO注释

**修复方案**:
1. 创建 `McpServerStorage.kt` 继承 PersistentStateComponent
2. 存储格式: JSON数组，每个服务器包含id/name/command/args/env/scope/enabled
3. 在McpServerManager初始化时从存储加载

**预估工时**: 2h

---

### A-2: 会话消息快照机制 [P0]

**问题**: 缺少消息版本快照机制，无法实现会话回滚

**修复方案**:
1. 在 `ChatMessage` 中添加 `version` 和 `parentVersion` 字段
2. 在 `SessionStorage` 中实现消息版本存储
3. 添加 `createSnapshot()` 和 `rollbackTo(version)` 方法

**预估工时**: 3h

---

### A-3: Skills文件系统加载器 [P1]

**问题**: 当前使用PropertiesComponent而非`.claude/skills/`目录

**修复方案**:
1. 修改 `SkillsManager` 添加文件系统加载模式
2. 从项目 `.claude/skills/` 目录读取 `.md` 文件
3. 解析Markdown格式的Skill定义

**预估工时**: 2h

---

### A-4: SubAgents文件系统加载器 [P1]

**问题**: 当前使用PropertiesComponent而非`.claude/agents/`目录

**修复方案**:
1. 修改 `AgentsManager` 添加文件系统加载模式
2. 从项目 `.claude/agents/` 目录读取 `.md/.json` 文件
3. 解析Agent配置

**预估工时**: 2h

---

### B-1: 消息引用机制 [P1]

**问题**: 缺少消息ID引用机制

**修复方案**:
1. 在 `ChatMessage` 中确保每个消息有唯一ID
2. 实现引用解析: 检测 `[messageId]` 格式
3. 在UI中渲染为可点击链接

**预估工时**: 2h

---

### C-1: CLI Login Provider [P1]

**问题**: `SpecialProviderIds.CLI_LOGIN` 未实现

**修复方案**:
1. 添加CLI认证状态检测方法
2. 实现从`~/.claude/settings.json`读取配置
3. 集成到ProviderProfile

**预估工时**: 2h

---

> **文档状态**: 待执行
> **创建日期**: 2026-04-12
> **维护者**: Claude Code IDEA Plugin Team
