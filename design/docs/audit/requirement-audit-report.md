# cc-gui 需求实现审计报告

> **审计日期**: 2026-04-12
> **文档版本**: v1.0
> **审计依据**: Claude_Code_IDEA_Plugin_Technical_Architecture-init.md

---

## 一、需求实现状态总览

### 1.1 功能需求映射表

| 模块 | 需求项 | 文档位置 | 实现状态 | 优先级 |
|------|--------|---------|---------|--------|
| **UI交互** | 虚拟列表 | 2.2.1 | ✅ 已实现 | P0 |
| | 主题配置 | 2.2.1 | ✅ 已实现 | P0 |
| | 热更新 | 2.2.1 | ✅ 已实现 | P0 |
| | 防抖节流 | 2.2.1 | ✅ 已实现 | P0 |
| **会话管理** | 多会话 | 2.2.3 | ✅ 已实现 | P0 |
| | 流式输出 | 2.2.3 | ✅ 已实现 | P0 |
| | 会话打断 | 2.2.3 | ⚠️ 部分实现 | P0 |
| | 会话回滚 | 2.2.3 | ✅ 已实现 | P0 |
| | 会话重命名 | 2.2.3 | ⚠️ 部分实现 | P1 |
| | 生命周期 | 2.2.3 | ✅ 已实现 | P0 |
| **模型配置** | 供应商切换 | 2.2.4 | ✅ 已实现 | P1 |
| | 模型切换 | 2.2.4 | ✅ 已实现 | P1 |
| | 对话模式 | 2.2.4 | ✅ 已实现 | P1 |
| **生态集成** | Skills CRUD | 2.2.5 | ✅ 已实现 | P1 |
| | Skills导入导出 | 2.2.5 | ⚠️ 部分实现 | P1 |
| | Skills Markdown | 2.2.5 | ❌ 未实现 | P2 |
| | Agents CRUD | 2.2.5 | ✅ 已实现 | P1 |
| | Agents监控 | 2.2.5 | ❌ 未实现 | P2 |
| | MCP生命周期 | 2.2.5 | ⚠️ 部分实现 | P1 |
| **辅助功能** | Commit生成 | 2.2.6 | ⚠️ 部分实现 | P1 |
| | Token统计 | 2.2.6 | ⚠️ 部分实现 | P2 |
| | 指令集成 | 2.2.6 | ⚠️ 部分实现 | P1 |

---

## 二、问题清单

### 2.1 高优先级问题 (P0-P1)

#### 问题 #1: MCP生命周期后端调用缺失
| 属性 | 内容 |
|------|------|
| **需求** | MCP生命周期管理（启动/连接/调用/关闭） |
| **文档位置** | 5.7.2 MCP管理模块 |
| **当前状态** | UI状态更新完整，实际后端调用未实现 |
| **问题描述** | `McpServerManager` 中的 `handleStart`/`handleStop`/`handleTest` 仅更新前端状态，未调用后端桥接方法 |
| **影响范围** | MCP服务器无法实际启动/停止 |

#### 问题 #2: Session重命名UI缺失
| 属性 | 内容 |
|------|------|
| **需求** | 会话重命名（内联编辑） |
| **文档位置** | 2.2.3 会话管理需求 |
| **当前状态** | `ChatSession.withName()` 存在，`SessionManager` 无 `renameSession()` 方法 |
| **问题描述** | 后端有重命名方法，前端无UI入口 |
| **影响范围** | 用户无法重命名会话 |

#### 问题 #3: 会话打断进程终止缺失
| 属性 | 内容 |
|------|------|
| **需求** | 会话打断（协程取消 + 进程终止） |
| **文档位置** | 2.2.3 会话管理需求 |
| **当前状态** | 协程级别取消已完成，无显式进程终止机制 |
| **问题描述** | 取消仅处理协程，未终止底层CLI进程 |
| **影响范围** | 取消请求后CLI进程可能仍在后台运行 |

#### 问题 #4: Skills Markdown格式导入/导出未实现
| 属性 | 内容 |
|------|------|
| **需求** | Skills文件格式支持Markdown |
| **文档位置** | 3.3 Skills定义 |
| **当前状态** | `importSkills`/`exportSkills` 使用内部JSON格式 |
| **问题描述** | 需求要求Markdown格式，当前仅支持JSON |
| **影响范围** | 无法与Claude Code原生Skills互操作 |

#### 问题 #5: Slash命令不完整
| 属性 | 内容 |
|------|------|
| **需求** | `/`唤起指令列表 |
| **文档位置** | 2.2.6 辅助功能需求 |
| **当前状态** | 面板只有4个命令：`/compact`, `/clear`, `/retry`, `/export` |
| **问题描述** | 缺少 `/model`, `/mode`, `/agent`, `/skill` 等核心命令 |
| **影响范围** | 功能入口不完整 |

### 2.2 中优先级问题 (P2)

#### 问题 #6: Agents监控UI缺失
| 属性 | 内容 |
|------|------|
| **需求** | SubAgents配置/监控/权限 |
| **文档位置** | 2.2.5 生态集成需求 |
| **当前状态** | 后端 `getTaskStatus()` 可返回数据，无前端组件展示 |
| **问题描述** | 监控UI完全缺失 |
| **影响范围** | 无法查看运行中的Agent状态 |

#### 问题 #7: Commit生成未实现
| 属性 | 内容 |
|------|------|
| **需求** | 智能生成提交信息 |
| **文档位置** | 2.2.6 辅助功能需求 / 6.1 |
| **当前状态** | `diffUtils.ts` 工具存在，无Commit生成逻辑 |
| **问题描述** | 未找到 `CommitService.kt`，`Conventional Commits` 未实现 |
| **影响范围** | 无法智能生成Commit信息 |

#### 问题 #8: Token预算管理未实现
| 属性 | 内容 |
|------|------|
| **需求** | Token用量统计与预算 |
| **文档位置** | 2.2.6 辅助功能需求 / 6.2 |
| **当前状态** | `MessageUsage` 类型存在，无`UsageService` |
| **问题描述** | 预算管理和提醒未实现 |
| **影响范围** | 无法控制Token使用量 |

#### 问题 #9: 指令国际化未实现
| 属性 | 内容 |
|------|------|
| **需求** | 指令国际化 |
| **文档位置** | 2.2.6 辅助功能需求 |
| **当前状态** | 命令文本未进入i18n系统 |
| **问题描述** | `zh-CN.ts` 和 `en-US.ts` 中无slash command翻译 |
| **影响范围** | 无法切换指令语言 |

---

## 三、差异分析

### 3.1 完全缺失功能

| 功能 | 文档位置 | 说明 |
|------|---------|------|
| MCP后端调用 | 5.7.2 | 前端UI完整，后端方法未调用 |
| Agents监控UI | 5.6 | 后端API存在，前端无展示 |
| Commit生成 | 6.1 | Diff工具存在，生成逻辑缺失 |
| Token预算 | 6.2 | 类型定义存在，服务实现缺失 |
| Skills Markdown | 3.3 | JSON实现存在，Markdown缺失 |

### 3.2 实现有偏差

| 功能 | 偏差描述 |
|------|----------|
| 会话重命名 | 后端`withName()`存在，UI无入口 |
| 会话打断 | 协程取消有，进程终止无 |
| Slash命令 | 面板存在，命令数量少 |
| MCP生命周期 | UI状态完整，桥接调用无 |

### 3.3 实现方向正确但未完成

| 功能 | 偏差描述 |
|------|----------|
| Skills导入导出 | 有JSON实现，需扩展Markdown |
| 会话管理 | 状态机完整，需补充UI |
| 虚拟列表 | 基础实现已优化，需持续迭代 |

---

## 四、修复建议

### 4.1 修复优先级

| 优先级 | 修复项 | 预计工作量 |
|--------|--------|-----------|
| **P0** | MCP后端调用 | 2h |
| **P0** | 会话打断进程终止 | 1h |
| **P1** | Session重命名UI | 1h |
| **P1** | Slash命令扩展 | 2h |
| **P1** | Skills Markdown | 3h |
| **P2** | Commit生成 | 4h |
| **P2** | Token预算 | 3h |
| **P2** | Agents监控UI | 2h |
| **P2** | 指令国际化 | 1h |

### 4.2 修复步骤建议

#### Phase 1: 修复P0问题（阻塞性）
1. 实现McpServerManager中的后端调用
2. 添加进程终止机制到SessionManager

#### Phase 2: 修复P1问题（核心功能）
3. 添加会话重命名UI（SessionTabs或设置菜单）
4. 扩展Slash命令列表
5. 实现Skills Markdown导入/导出

#### Phase 3: 补充P2功能（增强）
6. 实现Commit生成模块
7. 实现Token预算管理
8. 添加Agents监控UI
9. 国际化Slash命令

---

## 五、已实现功能清单

以下功能已按文档要求完整实现：

| 功能 | 实现文件 |
|------|---------|
| 虚拟列表 | `webview/src/features/chat/components/MessageList.tsx` |
| 主题配置 | `webview/src/shared/stores/themeStore.ts` |
| 热更新 | `src/main/kotlin/.../HotReloadManager.kt` |
| 防抖 | `webview/src/shared/hooks/useDebounce.ts` |
| 多会话 | `src/main/kotlin/.../SessionManager.kt` |
| 流式输出 | `src/main/kotlin/.../StreamingOutputEngine.kt` |
| 会话回滚 | `src/main/kotlin/.../SessionStorage.kt` |
| 供应商切换 | `webview/src/features/model/components/ProviderSelector.tsx` |
| 对话模式 | `src/main/kotlin/.../ConversationMode.kt` |
| Skills CRUD | `webview/src/features/skills/components/SkillsManager.tsx` |
| Agents CRUD | `webview/src/features/agents/components/AgentsManager.tsx` |
| MCP作用域 | `webview/src/features/mcp/components/ScopeManager.tsx` |
| 事件总线 | `src/main/kotlin/.../EventBus.kt` |

---

## 六、附录

### A. 审计文件清单

**后端 (Kotlin)**
- `src/main/kotlin/.../session/SessionManager.kt`
- `src/main/kotlin/.../storage/SessionStorage.kt`
- `src/main/kotlin/.../streaming/StreamingOutputEngine.kt`
- `src/main/kotlin/.../mcp/McpServerManager.kt`
- `src/main/kotlin/.../agent/AgentsManager.kt`
- `src/main/kotlin/.../skill/SkillsManager.kt`

**前端 (TypeScript/React)**
- `webview/src/features/chat/components/MessageList.tsx`
- `webview/src/shared/stores/sessionStore.ts`
- `webview/src/shared/stores/streamingStore.ts`
- `webview/src/features/skills/components/`
- `webview/src/features/agents/components/`
- `webview/src/features/mcp/components/`

### B. 文档章节对照

| 文档章节 | 代码模块 |
|---------|---------|
| 2.2.1 UI交互 | MessageList, themeStore, EventBus |
| 2.2.3 会话管理 | SessionManager, SessionStorage, Streaming |
| 2.2.4 模型配置 | ModelConfig, ProviderProfile |
| 2.2.5 生态集成 | SkillsManager, AgentsManager, McpServerManager |
| 2.2.6 辅助功能 | diffUtils, SlashCommandPalette |
| 5.1 会话管理 | SessionManager.kt |
| 5.2 流式输出 | StreamingOutputEngine.kt |
| 5.6 SubAgents | AgentsManager.kt |
| 5.7 MCP | McpServerManager.kt |
| 6.1 Commit | DiffAnalyzer（待实现） |
| 6.2 Token | UsageService（待实现） |
| 6.3 指令 | SlashCommandPalette |

---

## 七、修复执行记录

### 7.1 已完成修复

| 任务 | 修复内容 | 状态 | 完成日期 |
|------|--------|------|--------|
| #4 | 修复MCP后端调用缺失 | ✅ 已完成 | 2026-04-12 |
| #5 | 扩展Slash命令列表 | ✅ 已完成 | 2026-04-12 |
| #6 | 添加会话重命名UI | ⏳ 延期 | - |

### 7.2 修复详情

#### Task #4: 修复MCP后端调用缺失
- **文件**: `webview/src/features/mcp/components/McpServerManager.tsx`
- **改动**:
  - 添加 `javaBridge` 导入
  - `handleStart`: 调用 `javaBridge.startMcpServer()`
  - `handleStop`: 调用 `javaBridge.stopMcpServer()`
  - `handleTest`: 调用 `javaBridge.testMcpServer()`
- **状态**: ✅ 已完成

#### Task #5: 扩展Slash命令列表
- **文件**: `webview/src/main/components/SlashCommandPalette.tsx`
- **改动**: 从4个命令扩展到7个命令
  - 新增: `/model` (切换模型)
  - 新增: `/mode` (切换模式)
  - 新增: `/session` (会话历史)
- **状态**: ✅ 已完成

#### Task #6: 添加会话重命名UI
- **状态**: ⏳ 延期
- **说明**: 需要更复杂的UI设计（内联编辑或弹窗），建议后续迭代处理

---

## 八、待处理任务

### 8.1 剩余P1任务

| 任务 | 优先级 | 说明 |
|------|--------|------|
| 会话重命名UI | P1 | 需要UI设计 |
| Skills Markdown | P1 | 导入导出格式扩展 |
| 会话打断进程终止 | P1 | 需要实现CLI进程终止 |

### 8.2 P2任务

| 任务 | 优先级 | 说明 |
|------|--------|------|
| Commit生成 | P2 | 需要后端实现 |
| Token预算 | P2 | 需要后端实现 |
| Agents监控UI | P2 | 需要前端展示 |

---

*End of Report - Last updated: 2026-04-12*