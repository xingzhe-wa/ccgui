# 前端构建与 JAR 资源加载

## 核心规则
1. Vite 生产构建必须使用相对路径 (base: './')
2. JCEF 通过 file:// URL 加载 HTML 时，资源路径基于 HTML 文件所在目录
3. JAR 内资源通过 PluginClassLoader 访问，不能用 SystemClassLoader

## 构建流程

```
webview/src/                  webview/dist/               JAR 内
├── index.tsx    ─npm build→  ├── index.html    ─copy→   webview/dist/index.html
├── App.tsx     ─────────→   ├── assets/      ─────→   webview/dist/assets/*.js
└── styles/     ─────────→   └── assets/*.css  ─────→   webview/dist/assets/*.css
```

### build.gradle.kts 自动化
```kotlin
val buildWebview by tasks.registering(Exec::class) {
    workingDir = file("webview")

    // Windows 兼容：使用 cmd /c 执行 npm
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "npm.cmd", "run", "build")
    } else {
        commandLine("sh", "-c", "npm run build")
    }

    // 增量构建：仅当源码变更时才重新构建
    inputs.dir(file("webview/src"))
    inputs.file(file("webview/package.json"))
    inputs.file(file("webview/vite.config.ts"))
    inputs.file(file("webview/tsconfig.json"))
    inputs.file(file("webview/package-lock.json"))
    outputs.dir(file("webview/dist"))
}

val copyWebview by tasks.registering(Copy::class) {
    dependsOn(buildWebview)
    from(file("webview/dist"))
    into(file("src/main/resources/webview/dist"))
    inputs.dir(file("webview/dist"))
    outputs.dir(file("src/main/resources/webview/dist"))
}

tasks.named("processResources") { dependsOn(copyWebview) }
```

## Vite 配置关键项

### 相对路径（必须）
```typescript
// vite.config.ts
export default defineConfig({
  base: './',  // ← 关键！生成 ./assets/xxx.js 而非 /assets/xxx.js
})
```

### 禁用 HMR
```typescript
server: {
    port: 3000,
    strictPort: true,
    hmr: false  // JCEF 不支持 HMR，热更新会导致白屏
}
```

### 手动分包
避免单个 chunk 过大导致 file:// 加载慢：
```typescript
rollupOptions: {
    output: {
        manualChunks: (id) => {
            if (id.includes('node_modules/react') || id.includes('node_modules/react-dom')) {
                return 'vendor-react';
            }
            if (id.includes('node_modules/react-router')) {
                return 'vendor-router';
            }
            if (id.includes('node_modules/zustand')) {
                return 'vendor-state';
            }
            if (id.includes('node_modules/react-markdown') ||
                id.includes('node_modules/remark') ||
                id.includes('node_modules/rehype') ||
                id.includes('node_modules/katex')) {
                return 'vendor-markdown';
            }
            if (id.includes('node_modules/highlight.js')) {
                return 'vendor-highlight';
            }
            if (id.includes('node_modules/@tanstack/react-virtual')) {
                return 'vendor-virtual';
            }
            if (id.includes('node_modules/@dnd-kit')) {
                return 'vendor-dnd';
            }
            if (id.includes('node_modules/lucide-react')) {
                return 'vendor-icons';
            }
            // 其他 vendor
            if (id.includes('node_modules')) {
                return 'vendor';
            }
            return undefined;
        }
    }
}
```

### 生产环境代码压缩
```typescript
build: {
    minify: 'terser',
    terserOptions: {
        compress: {
            drop_console: true,
            drop_debugger: true,
            pure_funcs: ['console.log', 'console.info', 'console.debug', 'console.warn']
        }
    },
    chunkSizeWarningLimit: 500
}
```

## JAR 资源提取与加载

### 生产环境加载流程
```kotlin
// 1. 使用插件类加载器获取资源（不是系统类加载器）
val classLoader = this::class.java.classLoader
val distUrl = classLoader.getResource("webview/dist/index.html")

// 2. 解析 jar:file:/path/to/plugin.jar!/webview/dist/index.html
val jarUrlStr = distUrl.toString()
val bangIndex = jarUrlStr.indexOf("!/")
val jarPath = URLDecoder.decode(
    jarUrlStr.substring("jar:file:".length, bangIndex), "UTF-8"
)

// 3. 使用 IntelliJ FileUtil 创建临时目录并提取
val tempDir = FileUtil.createTempDirectory("ccgui-webview", "", true)
val jarFile = java.util.jar.JarFile(jarPath)
val entries = jarFile.entries()
while (entries.hasMoreElements()) {
    val entry = entries.nextElement()
    if (entry.name.startsWith("$webappRootDir/") && !entry.isDirectory) {
        val relativePath = entry.name.substring((webappRootDir + "/").length)
        val outFile = File(tempDir, relativePath)
        outFile.parentFile?.mkdirs()
        jarFile.getInputStream(entry).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

// 4. 通过 file:// URL 加载
val indexFile = File(tempDir, "index.html")
cefPanel?.loadHtmlPage(indexFile.toURI().toURL().toString())
```

### 开发环境加载
```kotlin
private fun loadFrontendPage(project: Project, toolWindow: ToolWindow) {
    val devServerUrl = "http://localhost:3000"
    val useDevServer = isDevServerAvailable(devServerUrl)
    if (useDevServer) {
        cefPanel?.loadHtmlPage(devServerUrl)
    } else {
        loadProductionFrontend()  // 回退到 JAR 提取
    }
}
```

## 常见问题诊断

### 白屏/无 UI
1. 检查 index.html 中资源路径是否为相对路径 (./assets/)
2. 检查 JAR 中是否包含 webview/dist/ 目录
3. 检查临时目录提取是否成功（查看日志 "Extracting webview from JAR"）

### CSS/JS 加载 404
1. 检查 base 配置是否为 './'
2. 检查 file:// URL 指向的目录结构是否正确
3. 检查手动分包配置是否导致路径变化

### 修改前端后不生效
1. 确认运行了 npm run build
2. 确认 build.gradle.kts 的 copyWebview 任务执行了
3. 确认 processResources 依赖 copyWebview
4. 重新构建插件 JAR

### chunk 过大警告
- highlight.js 单独一个 chunk 约 900KB+
- 通过 manualChunks 继续拆分
- 或调高 chunkSizeWarningLimit（当前设为 500KB）

## 检查清单
- [ ] vite.config.ts 中 base 是否为 './'？
- [ ] hmr 是否为 false？
- [ ] npm run build 是否成功？
- [ ] JAR 中是否包含最新 webview/dist/ 资源？
- [ ] 开发环境 dev server 端口是否正确？（默认 3000）

## 关键代码位置
| 文件 | 关键内容 |
|------|---------|
| `webview/vite.config.ts` | base, server.hmr, manualChunks, terserOptions |
| `build.gradle.kts` | buildWebview, copyWebview tasks（第 182-221 行） |
| `MyToolWindowFactory.kt` | loadFrontendPage(), loadProductionFrontend(), isDevServerAvailable() |
| `CefBrowserPanel.kt` | loadHtmlPage() -- 底层调用 browser.loadURL(url) |
