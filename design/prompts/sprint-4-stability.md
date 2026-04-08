# Sprint 4 断点 Prompt：修 Bug + 基础稳定

> **角色**：你是一个注重稳定性和错误处理的 IntelliJ 插件开发者。
> **目标**：核心流程可稳定运行不崩溃，边界情况有兜底。
> **前置条件**：Sprint 3 完成，核心聊天链路已跑通。

---

## 项目背景

Sprint 3 联调后，核心链路已通但有若干 bug。本 Sprint 的目标是修掉这些 bug，让插件能稳定运行。

## 你的任务

### Task 4.1：修复联调中发现的问题

读取 Sprint 3 交接文档末尾的 **实际执行记录**，逐一修复记录的 bug。

常见的预期问题（Sprint 3 一定会遇到的）：

#### 问题 A：JCEF JS 注入时机
- **现象**：`window.ccBackend` 时有时无，刷新后丢失
- **原因**：`CefBrowserPanel` 的 JS 注入和 HTML 加载存在竞态
- **修复**：确保在 `CefBrowser.loadEnd` 或 `LoadListener` 回调后再注入 bridge 对象

#### 问题 B：线程不安全
- **现象**：偶尔崩溃或 UI 不更新
- **原因**：JCEF 回调在非 EDT 线程，但 UI 更新需要 EDT
- **修复**：所有 `sendToJavaScript` / `executeJavaScript` 确保在正确线程

#### 问题 C：JSON 序列化不匹配
- **现象**：前端收到数据但解析失败
- **原因**：后端 Gson 序列化的字段名和前端 TypeScript 类型不一致
- **修复**：在 `CefBrowserPanel` 的 handler 中打印实际 JSON，对比前端类型定义

#### 问题 D：Store 初始化顺序
- **现象**：首次打开 Tool Window 时 sessions 列表为空
- **原因**：`appStore.initializeSessions()` 在 `window.ccBackend` 就绪前执行
- **修复**：在 `JcefBrowser.tsx` 的 `onReady` 回调中触发初始化

### Task 4.2：错误处理兜底

#### 4.2.1 CLI 不可用时的降级
读取 `ClaudeCodeClient.kt`，确认 `isCliAvailable()` 逻辑：
- 如果 CLI 不在 PATH，前端应该显示友好提示
- 在 `ChatOrchestrator.sendMessage()` 开头检查 CLI 可用性
- 不可用时通过 Bridge 推送错误消息到前端

#### 4.2.2 网络异常处理
读取 `ClaudeCodeClient.kt` 的异常处理：
- CLI 进程异常退出（exit code != 0）
- CLI 输出流意外关闭
- 超时处理

确认 `ErrorRecoveryManager.kt` 对这些异常有正确的恢复策略。

#### 4.2.3 前端错误边界
虽然设计文档规划了全局 `ErrorBoundary`，v0.0.1 可用简化版：
- 在 `App.tsx` 外层添加基本的 try-catch 或 React ErrorBoundary
- 渲染失败时显示 "插件加载失败，请重启 IDEA" 而非白屏

### Task 4.3：清理死代码

1. 删除 `src/main/kotlin/.../services/MyProjectService.kt`（空壳，未在 plugin.xml 注册）
2. 检查 `plugin.xml` 中是否有引用已删除类的注册项，一并清理
3. 检查 `webview/src/dev/` 目录确保仅用于开发（不进生产构建）

### Task 4.4：压力测试

手动执行以下操作，确认不崩溃：
1. 连续发送 10 条消息
2. 快速切换会话 10 次
3. 流式输出过程中切换会话
4. 流式输出过程中关闭 Tool Window 再打开
5. 无 Claude CLI 时尝试发消息

## 需要读取的文件

| 文件 | 关注点 |
|------|--------|
| Sprint 3 交接文档 | 联调发现的问题列表 |
| `CefBrowserPanel.kt` | JS 注入时机、线程安全 |
| `ClaudeCodeClient.kt` | CLI 进程异常处理 |
| `ChatOrchestrator.kt` | CLI 可用性检查 |
| `ErrorRecoveryManager.kt` | 错误恢复策略 |
| `BridgeManager.kt` | 连接状态管理 |
| `JcefBrowser.tsx` | 就绪检测、onReady 回调 |
| `App.tsx` | ErrorBoundary 添加位置 |
| `appStore.ts` | 初始化时机 |
| `plugin.xml` | 死注册清理 |

## 验收标准

- [ ] 连续发 10 条消息不崩溃
- [ ] 切换会话 10 次不崩溃
- [ ] 流式输出中切换会话不崩溃
- [ ] 流式输出中关闭/重开 Tool Window 恢复正常
- [ ] 无 Claude CLI 时显示友好提示（不白屏/不崩溃）
- [ ] 死代码已清理
- [ ] 无 ERROR 级别日志（IDEA 日志中）

## 交接给下一位开发者

> "Sprint 4 完成。核心流程稳定，边界情况有兜底。以下是修复的具体 bug：[列表]。请进入 Sprint 5：最终打包发版。"
