# 需求驱动审计报告与修复计划

> **审计日期**: 2026-04-12
> **基于文档**: Claude_Code_IDEA_Plugin_Technical_Architecture-init.md (v3.0)
> **审计范围**: 第2章(需求分析)、第5章(核心模块)、第6章(新增功能)

---

## 1. 需求映射总表

### 1.1 UI交互需求 (2.2.1)

| 子需求 | 描述 | 实现文件 | 状态 |
|--------|------|----------|------|
| 交互流畅 | 响应迅速，无卡顿 | StreamingOutputEngine (自适应节流) | ✅ 已实现 |
| 主题配置 | 精美/简约主题切换 | ThemeConfig, ThemeSwitcher | ✅ 已实现 |
| 配置驱动 | 所有功能可配置 | ConfigManager + PersistentStateComponent | ✅ 已实现 |
| 热更新 | 配置即时生效 | HotReloadManager + EventBus | ✅ 已实现 |

### 1.2 对话发起需求 (2.2.2)

| 子需求 | 描述 | 实现文件 | 状态 |
|--------|------|----------|------|
| 提示词优化 | 自动优化用户输入 | QuickActionsPanel | ⚠️ 部分实现 |
| 代码块选择 | 选中代码发起对话 | ChatView.handleQuickAction | ✅ 已实现 |
| 多模态输入 | 图片、附件支持 | AttachmentManager | ⚠️ 部分实现 |
| 引用对话 | 引用历史消息 | ChatMessage.references | ✅ 已实现 |
| 交互式请求 | 主动提问、动态抉择 | InteractiveQuestionPanel | ✅ 已实现 |

### 1.3 会话管理需求 (2.2.3)

| 子需求 | 描述 | 实现文件 | 状态 |
|--------|------|----------|------|
| 多会话 | 支持多个并行会话 | SessionManager + TabSwitcher | ✅ 已实现 |
| 流式输出 | 实时显示响应 | StreamingOutputEngine | ✅ 已实现 |
| 会话打断 | 随时中断响应 | StreamingOutputEngine.cancelStreaming | ✅ 已实现 |
| 会话回滚 | 回到历史状态 | SessionStorage.snapshots | ✅ 已实现 |
| 会话重命名 | 自定义会话名称 | ChatSession.withName | ✅ 已实现 |
| 数据隔离 | 会话间数据独立 | sessionId隔离 | ✅ 已实现 |
| 生命周期管理 | 跟踪进度、状态 | SessionStatus状态机 | ✅ 已实现 |

### 1.4 模型配置需求 (2.2.4)

| 子需求 | 描述 | 实现文件 | 状态 |
|--------|------|----------|------|
| 供应商切换 | 多API供应商支持 | ProviderProfile | ✅ 已实现 |
| 模型切换 | 快速切换模型 | ModelConfigPanel | ✅ 已实现 |
| 对话模式 | thinking/plan/auto | ConversationMode | ✅ 已实现 |

### 1.5 生态集成需求 (2.2.5)

| 子需求 | 描述 | 实现文件 | 状态 |
|--------|------|----------|------|
| Skills管理 | 创建/导入/导出 | SkillsManager | ✅ 已实现 |
| SubAgents管理 | 配置/监控/权限 | AgentsManager | ✅ 已实现 |
| MCP管理 | 生命周期管理 | McpServerManager | ✅ 已实现 |
| 作用域配置 | 全局/项目级别 | *Scope枚举 | ✅ 已实现 |

### 1.6 辅助功能需求 (2.2.6)

| 子需求 | 描述 | 实现文件 | 状态 |
|--------|------|----------|------|
| Commit生成 | 智能生成提交信息 | CommitService + DiffAnalyzer | ✅ 已实现 |
| Token统计 | 用量统计与预算 | UsageService | ⚠️ 部分实现 |
| 原生指令集成 | `/`唤起指令列表 | SlashCommandRegistry | ⚠️ 部分实现 |

---

## 2. 差异分析

### 2.1 P0 - 阻断性问题

#### P0-1: 大消息内容存储方案缺失
- **文档规定** (5.1): 大型消息内容应存储为独立JSON文件，通过MessageFileManager管理
- **当前实现**: 所有消息内容直接存储在ChatSession.messages中
- **影响范围**: 会话存储性能、大型会话内存占用
- **修复建议**: 实现MessageFileManager，将大消息内容分离存储

#### P0-2: 上下文压缩模块缺失
- **文档规定** (5.3): ContextCompressor类实现轻量化上下文压缩
- **当前实现**: ContextManager存在但无压缩功能
- **影响范围**: 长会话上下文膨胀、Token浪费
- **修复建议**: 实现ContextCompressor类

#### P0-3: 指令补全UI缺失
- **文档规定** (6.3): CommandCompletionPopup实现指令补全UI
- **当前实现**: SlashCommandPalette在webview中存在，但Kotlin侧无对应实现
- **影响范围**: 用户无法通过`/`触发指令补全
- **修复建议**: 实现CommandCompletionPopup

### 2.2 P1 - 严重问题

#### P1-1: 国际化资源文件缺失
- **文档规定** (6.3, 8.3): 需要Commands_zh.properties和Commands_en.properties
- **当前实现**: I18nManager存在但commands相关资源文件不存在
- **影响范围**: 指令描述无法本地化
- **修复建议**: 创建messages/Commands_*.properties文件

#### P1-2: 预算管理器未实现
- **文档规定** (6.2): BudgetManager类实现预算检查和告警
- **当前实现**: UsageService有calculateCost但无BudgetManager
- **影响范围**: 无法进行用量预算控制
- **修复建议**: 实现BudgetManager类

#### P1-3: SSE解析器不完整
- **文档规定** (5.2): StreamParser应解析完整SSE事件
- **当前实现**: StreamJsonParser仅处理部分事件类型
- **影响范围**: 部分流式响应无法正确解析
- **修复建议**: 完善StreamJsonParser

#### P1-4: Commit模板引擎部分缺失
- **文档规定** (6.1): 4种模板(Conventional/Gitmoji/Angular/Emoji)
- **当前实现**: CommitService只有前3种，EmojiTemplate缺失
- **影响范围**: 无法使用Emoji格式生成commit
- **修复建议**: 添加EmojiTemplate

### 2.3 P2 - 优化建议

#### P2-1: 多模态输入集成不完整
- **文档规定** (2.2.2): Base64编码 + 文件引用
- **当前实现**: AttachmentManager存在但未与发送流程集成
- **修复建议**: 完善javaBridge.sendMultimodalMessage

#### P2-2: 提示词自动优化缺失
- **文档规定** (2.2.2): 内置模板 + 上下文增强
- **当前实现**: QuickActionsPanel有预设prompt但无动态优化
- **修复建议**: 实现PromptOptimizer类

#### P2-3: Skills导入导出功能不完整
- **文档规定** (5.5): 完整的导入/导出机制
- **当前实现**: SkillsManager有importSkill但UI可能不完整
- **修复建议**: 完善SkillsManager UI

---

## 3. 修复执行计划

### P0阶段：阻断性问题修复

| 任务ID | 任务描述 | 实现文件 | 优先级 |
|--------|----------|----------|--------|
| P0-1 | 实现MessageFileManager大消息分离存储 | infrastructure/storage/MessageFileManager.kt | P0 |
| P0-2 | 实现ContextCompressor上下文压缩 | application/context/ContextCompressor.kt | P0 |
| P0-3 | 实现CommandCompletionPopup指令补全 | ui/popup/CommandCompletionPopup.kt | P0 |

### P1阶段：严重问题修复

| 任务ID | 任务描述 | 实现文件 | 优先级 |
|--------|----------|----------|--------|
| P1-1 | 创建国际化资源文件 | resources/messages/Commands_*.properties | P1 |
| P1-2 | 实现BudgetManager预算管理 | application/usage/BudgetManager.kt | P1 |
| P1-3 | 完善StreamJsonParser SSE解析 | adaptation/sdk/StreamJsonParser.kt | P1 |
| P1-4 | 添加EmojiTemplate | application/commit/CommitService.kt | P1 |

### P2阶段：优化建议

| 任务ID | 任务描述 | 实现文件 | 优先级 |
|--------|----------|----------|--------|
| P2-1 | 完善多模态输入集成 | bridge/ + webview | P2 |
| P2-2 | 实现PromptOptimizer | application/prompt/PromptOptimizer.kt | P2 |
| P2-3 | 完善Skills导入导出UI | webview/features/skills | P2 |

---

## 4. 架构偏离清单

以下实现与文档设计存在偏离，需评估是否更新文档或修复代码：

1. **SessionStorage.snapshots**: 文档(5.1)描述为messageSnapshots存储在独立文件，实际实现在XML中
2. **StreamingOutputEngine**: 文档(5.2)描述使用Channel<String>，实际使用StateFlow + StringBuilder
3. **Agent监控**: 文档(5.6)描述SubAgentMonitor类，实际通过EventBus事件替代

---

## 5. 待确认事项

1. 是否需要保留内置Skills(代码生成器、代码审查员等)？文档未明确定义但代码中有实现
2. Commit模板引擎的Emoji格式具体规范是什么？文档中未详细定义

---

**报告生成时间**: 2026-04-12
## 6. 执行状态

### P0阶段：已完成 ✅

| 任务ID | 任务描述 | 状态 | 完成文件 |
|--------|----------|------|----------|
| P0-1 | MessageFileManager大消息分离存储 | ✅ 已完成 | infrastructure/storage/MessageFileManager.kt |
| P0-1a | MessageFileManager添加@Service注解 | ✅ 已完成 | 同上 |
| P0-1b | SessionStorage集成MessageFileManager | ✅ 已完成 | SessionStorage.kt |
| P0-2 | ContextCompressor上下文压缩 | ✅ 已完成 | application/context/ContextCompressor.kt |
| P0-3 | CommandCompletionPopup指令补全 | ✅ 已完成 | ui/popup/CommandCompletionPopup.kt |

### P1阶段：已完成 ✅

| 任务ID | 任务描述 | 状态 | 完成文件 |
|--------|----------|------|----------|
| P1-1 | 国际化资源文件 | ✅ 已完成 | resources/messages/Commands_zh.properties, Commands_en.properties |
| P1-2 | BudgetManager预算管理 | ✅ 已完成 | application/usage/BudgetManager.kt |
| P1-3 | StreamJsonParser SSE解析 | ✅ 已完成 | adaptation/sdk/StreamJsonParser.kt |
| P1-4 | EmojiTemplate | ✅ 已存在无需修改 | - |
| P1-5 | SlashCommandRegistry注册17条指令 | ✅ 已完成 | SlashCommandRegistry.kt |

### P2阶段：已完成 ✅

| 任务ID | 任务描述 | 状态 | 完成文件 |
|--------|----------|------|----------|
| P2-1 | 多模态输入集成 | ✅ 已完成 | MultimodalMessageHandler.kt, useImageCompression.ts |
| P2-2 | PromptOptimizer | ✅ 已完成 | application/prompt/PromptOptimizer.kt |
| P2-3 | Skills导入导出UI | ✅ 已完成 | SkillImportDialog.tsx, SkillCard.tsx, SkillsList.tsx |

### 辅助功能完善 ✅

| 任务 | 状态 | 完成文件 |
|------|------|----------|
| Token统计部分实现完善 | ✅ 已完成 | UsageService.kt |
| 指令集成部分实现完善 | ✅ 已完成 | SlashCommandRegistry.kt (17条指令注册) |

---

**执行完成时间**: 2026-04-12
