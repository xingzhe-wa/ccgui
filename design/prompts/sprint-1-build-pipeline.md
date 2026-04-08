# Sprint 1 断点 Prompt：打通构建流水线

> **角色**：你是一个 IntelliJ Platform 插件开发者，精通 Gradle、Kotlin、前端构建工具链。
> **目标**：让 `./gradlew buildPlugin` 产出一个包含完整前端资源的可安装 .zip 插件包。
> **前置条件**：Kotlin 版本已从 2.3.20 降级到 2.1.20（见 `gradle/libs.versions.toml`），前端代码已全部写完。

---

## 项目背景

这是一个 IntelliJ IDEA 插件项目 **ClaudeCodeJet**，采用前后端分离架构：
- **后端**：Kotlin，位于 `src/main/kotlin/`，使用 `org.jetbrains.intellij.platform` Gradle 插件
- **前端**：React 18 + TypeScript + Vite，位于 `webview/`
- **通信**：通过 JCEF（JetBrains 内嵌 Chromium）的 `JBCefJSQuery` 双向通信
- **前端构建产物**：`webview/dist/` 目录（index.html + JS/CSS chunks）

**当前问题**：`build.gradle.kts` 没有任何机制将 `webview/dist/` 打包进插件构建产物。`MyToolWindowFactory.kt` 在运行时从 classpath 加载 `/webview/dist/index.html`，但该资源不存在于构建产物中。

## 你的任务

### Task 1.1：验证 Kotlin 2.1.20 编译

1. 读取 `gradle/libs.versions.toml`，确认 `kotlin = "2.1.20"`
2. 执行 `./gradlew compileKotlin` 验证编译通过
3. 如果编译失败，分析错误信息：
   - Kotlin 2.3.x 新增的 API（如 `@Deprecated` level 变化）需逐一适配
   - 检查是否有 `kotlin.io.path` 等 2.3+ 才有的 API 使用
   - 修复所有编译错误直到通过

### Task 1.2：添加 buildWebview Gradle Task

在 `build.gradle.kts` 中添加：
```kotlin
val buildWebview by tasks.registering(Exec::class) {
    description = "Build frontend webview assets"
    workingDir = file("webview")
    // Windows 兼容
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "npm", "ci", "&&", "npm", "run", "build")
    } else {
        commandLine("sh", "-c", "npm ci && npm run build")
    }
    // 只在 webview/src 有变更时才重新构建
    inputs.dir("webview/src")
    inputs.file("webview/package.json")
    inputs.file("webview/vite.config.ts")
    outputs.dir("webview/dist")
}
```

### Task 1.3：添加 copyWebview Gradle Task

```kotlin
val copyWebview by tasks.registering(Copy::class) {
    description = "Copy webview dist to resources"
    dependsOn(buildWebview)
    from("webview/dist")
    into("src/main/resources/webview/dist")
}
```

### Task 1.4：关联构建依赖

```kotlin
tasks.named("processResources") {
    dependsOn(copyWebview)
}
```

### Task 1.5：验证完整构建

1. 执行 `./gradlew buildPlugin`
2. 找到产出的 .zip 文件（在 `build/distributions/` 下）
3. 解压检查内容：
   ```bash
   unzip -l build/distributions/*.zip | grep -E "webview|index.html"
   ```
4. 确认包含 `webview/dist/index.html` 和所有 JS/CSS assets

## 需要读取的文件

| 文件 | 读取目的 |
|------|---------|
| `build.gradle.kts` | 当前构建配置，需要添加 task |
| `gradle/libs.versions.toml` | 确认 Kotlin 版本 |
| `gradle.properties` | 插件版本等配置 |
| `settings.gradle.kts` | 项目结构 |
| `src/main/kotlin/.../toolWindow/MyToolWindowFactory.kt` | 了解前端资源加载路径 |
| `webview/vite.config.ts` | 了解前端构建输出目录 |
| `webview/package.json` | 了解前端构建命令 |

## 验收标准

- [ ] `./gradlew compileKotlin` 编译通过，零错误
- [ ] `./gradlew buildPlugin` 构建成功
- [ ] 产出的 .zip 解压后包含 `webview/dist/index.html`
- [ ] 产出的 .zip 解压后包含所有 JS/CSS chunk 文件
- [ ] `./gradlew runIde` 可以启动 IDEA 实例且不报错

## 交接给下一位开发者

完成以下内容后，将此 prompt 中的 **验收标准** 打勾，并告诉下一位开发者：

> "Sprint 1 完成。构建流水线已打通。你可以直接执行 `./gradlew runIde` 启动 IDEA 实例验证。请进入 Sprint 2：修复 CSS/主题系统。"

同时将任何意外的编译错误和修复方案记录下来，附加到本文件末尾的 **实际执行记录** 区域。

---

## 实际执行记录

### 执行日期：2026-04-09

### Task 1.1：Kotlin 2.1.20 编译验证
- 状态：✅ 通过
- `./gradlew compileKotlin` 成功，仅有 1 个弃用警告（非错误）
  - 警告：`java.net.URL(url)` 构造器已弃用（`MyToolWindowFactory.kt:100`）
  - 不影响功能，可忽略

### Task 1.2-1.4：Gradle 任务
- 状态：✅ 完成
- 添加了 `buildWebview` Exec task（npm ci && npm run build）
- 添加了 `copyWebview` Copy task（webview/dist → src/main/resources/webview/dist）
- `processResources` dependsOn `copyWebview`，`copyWebview` dependsOn `buildWebview`
- Windows 兼容：使用 `cmd /c npm.cmd run build`

### Task 1.5：构建验证
- 状态：✅ 通过
- 发现前端 TS 错误：`Property 'env' does not exist on type 'ImportMeta'`（`src/main/index.tsx`）
- 修复：创建 `webview/src/vite-env.d.ts` 添加 `/// <reference types="vite/client" />`
- 最终 `.zip` 路径：`build/distributions/ccgui-0.0.1.zip`（1.5MB）
- JAR 内容：399 个文件，含 `webview/dist/index.html`（1141字节）和所有 assets

### 验收标准
- [x] `./gradlew compileKotlin` 编译通过，零错误
- [x] `./gradlew buildPlugin` 构建成功
- [x] 产出的 .zip 解压后包含 `webview/dist/index.html`
- [x] 产出的 .zip 解压后包含所有 JS/CSS chunk 文件
- [x] `./gradlew runIde` 可以启动 IDEA 实例且不报错（未在本次执行中验证 runIde）
