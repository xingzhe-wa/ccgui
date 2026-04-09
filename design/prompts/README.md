# ClaudeCodeJet Sprint Prompts 索引

> Sprint Prompt 文档索引。每次 Sprint 开发前，开发者应完整阅读对应的 prompt 文件。

---

## v0.0.2 开发 Sprint

| Sprint | 文件 | 核心目标 | 预计周期 |
|--------|------|---------|---------|
| Sprint 6 | `sprint-6-core-experience.md` | 核心体验修复：Markdown高亮、会话切换联动、StopButton、新建会话UI | Day 1-2 |
| Sprint 7 | `sprint-7-high-frequency-features.md` | 高频功能补全：Skill/Agent列表、历史搜索、多模态输入、打字机光标 | Day 3-4 |
| Sprint 8 | `sprint-8-interaction-enhancement.md` | 交互增强：交互式请求UI、断点协同与会话恢复、导入导出、代码快捷操作 | Day 5-6 |

---

## v0.0.1 开发 Sprint

| Sprint | 文件 | 核心目标 | 状态 |
|--------|------|---------|------|
| Sprint 1 | `sprint-1-build-pipeline.md` | 构建流水线打通，前端打包进插件 | ✅ 完成 |
| Sprint 2 | `sprint-2-css-theme.md` | CSS主题系统，HSL变量架构 | ✅ 完成 |
| Sprint 3 | `sprint-3-e2e-integration.md` | 端到端联调，Bridge通信链路打通 | ✅ 完成 |
| Sprint 4 | `sprint-4-stability.md` | Bug修复，错误处理，基础稳定性 | ✅ 完成 |
| Sprint 5 | `sprint-5-release.md` | Bridge缺口补全，权限流打通，死代码清理 | ✅ 完成 |

---

## 关联文档

| 文件 | 说明 |
|------|------|
| `design/release-v0.0.2-plan.md` | v0.0.2 完整发版执行方案 |
| `design/release-v0.0.1-plan.md` | v0.0.1 完整发版执行方案 |
| `design/PRD-v3.0.md` | 完整产品需求文档 |
| `design/methodology.md` | 开发方法论与经验沉淀 |

---

## 如何使用本文档

1. 在开始 Sprint N 之前，完整阅读 `sprint-N-*.md`
2. 按文件中的 Task 顺序执行，每个 Task 完成时确认验收标准
3. 遇到问题，参考"需要读取的文件"章节排查
4. Sprint 结束后，在文件末尾更新"交接给下一位开发者"章节
5. 将所有变更 commit，并 tag 对应版本

---

## 通用调试技巧

### 后端（Kotlin）

| 调试目标 | 命令/技巧 |
|---------|---------|
| 查看 IDEA 日志 | `{user.home}/AppData/Local/JetBrains/IntelliJIdea{版本}/log/idea.log` |
| 打印调试 | `println("=== DEBUG: $variable ===")` — 输出到 IDEA console |
| Gradle 重新构建 | `./gradlew clean build --rerun-tasks` |
| 运行插件 | `./gradlew runIde` |
| 插件打包 | `./gradlew buildPlugin` |

### 前端（React/TypeScript）

| 调试目标 | 命令/技巧 |
|---------|---------|
| 打开 CEF DevTools | IDEA → `Help` → `Find Action` → 搜索 "Open CEF DevTools" |
| 前端独立开发 | `cd webview && npm run dev`（需 mock bridge） |
| TypeScript 类型检查 | `cd webview && npx tsc --noEmit` |
| 热更新 | Vite 默认支持，保存即更新 |

### 跨语言通信调试

| 调试目标 | 命令/技巧 |
|---------|---------|
| JS→Java 通道 | 在 `CefBrowserPanel.handleJsRequest()` 第一行加 `println` |
| Java→JS 通道 | 在 `sendToJavaScript()` 第一行加 `println` |
| CLI 独立测试 | 终端运行 `claude -p "hello" --output-format stream-json` |
| 确认 window.ccBackend 存在 | CEF DevTools Console: `window.ccBackend ? "OK" : "MISSING"` |

---

*本文档随 Sprint 迭代更新。*
