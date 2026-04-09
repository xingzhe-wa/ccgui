# v0.0.1 发版执行方案

**版本**: 1.0
**创建日期**: 2026-04-09
**目标**: 产出可在 IntelliJ IDEA 中安装运行的初版插件
**预计工期**: 5 个 Sprint，约 5-7 个工作日

---

## 1. v0.0.1 MVP 范围定义

### 1.1 必须有 (Must Have)

| 功能 | 验收标准 |
|------|---------|
| 插件安装 & Tool Window 打开 | IDEA 安装 .zip → 侧边栏出现 "CCGUI" → 点击打开不白屏 |
| 消息发送 + 流式接收 | 输入文字 → 点击发送 → AI 回复逐字流式显示 → 显示完毕 |
| 会话管理 | 创建新会话 / 切换会话 / 删除会话 / 会话独立消息隔离 |
| 主题切换 | 至少 JetBrains Dark / Light 切换生效，UI 颜色实际变化 |
| 前端资源打包 | `./gradlew buildPlugin` 产出的 .zip 包含完整前端资源 |

### 1.2 延后到 v0.1.0

- Agent Balanced/Aggressive 执行模式
- MCP 持久化加载 + 能力解析
- PreviewPanel / DiffViewer 分栏
- 响应式布局 / Tab 切换动画
- 测试覆盖 >80%
- Notification UI 组件
- ModelSelector 模型选择器
- CI/CD 完整流水线

---

## 2. 当前阻断性问题清单

| # | 阻断点 | 严重度 | 所在 Sprint |
|---|--------|--------|------------|
| B1 | 前端未打包进插件构建 (build.gradle.kts 无 copy task) | P0 | Sprint 1 |
| B2 | Kotlin 2.1.20 编译未验证 (刚从 2.3.20 降级) | P0 | Sprint 1 |
| B3 | CSS 变量体系断裂 (`--color-*` HEX vs `--*` HSL) | P0 | Sprint 2 |
| B4 | 从未端到端联调过 (Bridge 通信未验证) | P0 | Sprint 3 |

---

## 3. Sprint 执行计划

### Sprint 1：打通构建流水线 (Day 1)

**目标**：`./gradlew buildPlugin` 产出一个包含前端资源的可用 .zip

| Task | 内容 | 产出物 |
|------|------|--------|
| 1.1 | `build.gradle.kts` 添加 `buildWebview` Exec task | Gradle task |
| 1.2 | `build.gradle.kts` 添加 `copyWebview` Copy task | Gradle task |
| 1.3 | `buildPlugin` dependsOn `copyWebview` | 构建依赖 |
| 1.4 | 验证 Kotlin 2.1.20 编译通过，修复语法不兼容 | 编译通过 |
| 1.5 | 验证 .zip 解压后包含 `webview/dist/index.html` + assets | 完整包 |

**验收命令**：
```bash
cd webview && npm run build
cd .. && ./gradlew buildPlugin
unzip -l build/distributions/*.zip | grep webview
```

---

### Sprint 2：修复 CSS/主题系统 (Day 2)

**目标**：主题切换生效，JetBrains Dark 作为默认主题正常显示

| Task | 内容 | 产出物 |
|------|------|--------|
| 2.1 | 统一 CSS 变量：themeStore.applyTheme() 改为设置 `--primary` 等 HSL 变量 | themeStore.ts |
| 2.2 | globals.css 补全缺失变量 (transition/scrollbar/selection/focus) | globals.css |
| 2.3 | 默认 JetBrains Dark 主题生效 | 初始加载即暗色 |
| 2.4 | 浏览器 `npm run dev` 验证主题切换颜色变化 | 手动验证 |

**验收标准**：
- `npm run dev` 打开浏览器，切换主题后页面颜色实际变化
- 代码块背景色随主题切换
- 侧边栏/消息气泡颜色随主题切换

---

### Sprint 3：端到端联调 (Day 3-4)

**目标**：`./gradlew runIde` → Tool Window → 发消息 → 收到流式回复

| Task | 内容 | 产出物 |
|------|------|--------|
| 3.1 | `./gradlew runIde` 启动，Tool Window 加载前端页面不白屏 | 运行验证 |
| 3.2 | Bridge 通信链路打通：JBCefJSQuery 注入 → JS 调 Java → Java 回调 JS | 日志验证 |
| 3.3 | 消息发送链路：ChatInput → javaBridge → CefBrowserPanel → BridgeManager → ChatOrchestrator → ClaudeCodeClient → CLI | 发送成功 |
| 3.4 | 流式显示链路：CLI 输出 → StreamJsonParser → StreamingOutputEngine → CefBrowserPanel → window.ccEvents → useStreaming → StreamingMessage | 流式渲染 |
| 3.5 | 会话管理链路：createSession → SessionManager → SessionStorage → 前端 store 更新 | CRUD 正常 |

**验收标准**：
- 发送 "Hello" → 收到 Claude AI 的回复（流式逐字显示）
- 创建新会话 → 切换到新会话 → 发消息 → 切回旧会话 → 旧消息还在
- 停止按钮可中断流式输出

---

### Sprint 4：修 Bug + 基础稳定 (Day 5)

**目标**：核心流程可稳定运行，不崩溃

| Task | 内容 |
|------|------|
| 4.1 | 修复联调发现的 Bridge/JCEF 问题 |
| 4.2 | 修复前端数据流问题（Store 初始化时机、状态清理） |
| 4.3 | 清理死代码（删除 MyProjectService.kt） |
| 4.4 | 错误处理兜底（CLI 不可用提示、网络异常重试） |

**验收标准**：
- 连续发送 10 条消息不崩溃
- 切换会话 10 次不崩溃
- 无 Claude CLI 时给出友好提示而非白屏

---

### Sprint 5：打包发版 (Day 6)

**目标**：产出可分发的 v0.0.1 插件包

| Task | 内容 |
|------|------|
| 5.1 | 更新 plugin.xml 元数据 (description / changeNotes / vendor) |
| 5.2 | 最终 `./gradlew buildPlugin` 构建 |
| 5.3 | 手动安装测试：Install from disk → 完整走一遍核心流程 |
| 5.4 | `git tag v0.0.1` 并推送 |

---

## 4. 风险矩阵

| 风险 | 概率 | 影响 | 应对策略 |
|------|------|------|---------|
| Kotlin 2.1.20 语法不兼容 | 中 | 阻断编译 | Sprint 1 首先验证 |
| JCEF JBCefJSQuery 注入失败 | 高 | 无法通信 | 降级为 executeJavaScript 直接调用 |
| CLI 进程在 Windows 上异常 | 中 | 无法对话 | ProcessBuilder 路径转义 + 日志排查 |
| 流式 JSON 解析错位 | 中 | 乱码/丢数据 | 备选：整条消息非流式返回 |
| JCEF 内存占用过高 | 低 | IDE 卡顿 | v0.0.1 可接受，后续优化 |

---

## 5. 关键文件索引

### 后端 (Kotlin)
| 文件 | 职责 | 所属 Sprint |
|------|------|------------|
| `build.gradle.kts` | 构建配置，需要添加前端打包 | Sprint 1 |
| `gradle/libs.versions.toml` | Kotlin 版本已改为 2.1.20 | Sprint 1 |
| `CefBrowserPanel.kt` | JCEF 双向通信核心 | Sprint 3 |
| `BridgeManager.kt` | 消息转发 | Sprint 3 |
| `ChatOrchestrator.kt` | 聊天编排 | Sprint 3 |
| `ClaudeCodeClient.kt` | CLI 进程管理 | Sprint 3 |
| `StreamingOutputEngine.kt` | 流式输出推送 | Sprint 3 |
| `SessionManager.kt` | 会话管理 | Sprint 3 |
| `MyToolWindowFactory.kt` | Tool Window 创建 + 前端加载 | Sprint 3 |

### 前端 (React)
| 文件 | 职责 | 所属 Sprint |
|------|------|------------|
| `webview/src/shared/stores/themeStore.ts` | 主题管理 + CSS 变量注入 | Sprint 2 |
| `webview/src/styles/globals.css` | Tailwind 基础 + CSS 变量定义 | Sprint 2 |
| `webview/src/dev/mock-bridge.ts` | 独立开发 Mock (不进生产构建) | Sprint 2 |
| `webview/src/lib/java-bridge.ts` | Java 通信封装 | Sprint 3 |
| `webview/src/shared/stores/appStore.ts` | 全局状态 | Sprint 3 |
| `webview/src/shared/stores/sessionStore.ts` | 会话状态 | Sprint 3 |
| `webview/src/shared/stores/streamingStore.ts` | 流式状态 | Sprint 3 |
| `webview/src/main/pages/ChatView.tsx` | 聊天主页面 | Sprint 3 |

---

## 6. 不做的事 (v0.0.1 明确排除)

1. **不重构布局** — Sidebar 导航虽与设计文档不同但功能可用
2. **不补全 Agent 高级模式** — Cautious 即发消息，够用
3. **不做响应式** — Tool Window 固定宽度
4. **不补自动化测试** — 手动验证即可
5. **不上 JetBrains Marketplace** — 先 GitHub Release 内测
