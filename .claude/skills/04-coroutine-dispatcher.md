# Kotlin 协程调度器选型

## 核心规则
**在 IntelliJ 插件开发中，Dispatchers.Main 在 postStartupActivity 阶段可能不可用。**

## 崩溃现象
```
PluginException: kotlinx.coroutines.CoroutineExceptionHandler:
com.intellij.openapi.application.impl.CoroutineExceptionHandlerImpl not a subtype
```

## 根因
Dispatchers.Main 依赖 IntelliJ 平台基础设施（EDT 调度器），在以下场景可能未初始化：
- postStartupActivity 阶段
- PluginClassLoader 未完全加载时
- 无头环境 (headless)

## 解决方案

### 统一使用 Dispatchers.Default
```kotlin
// safe: 适用于所有场景
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

### 需要操作 UI 时
```kotlin
// safe: 需要更新 UI 时通过 invokeLater 切换到 EDT
scope.launch(Dispatchers.Default) {
    // 耗时操作...
    java.awt.EventQueue.invokeLater {
        // UI 更新...
    }
}
```

### 禁止模式
```kotlin
// danger: 在启动阶段使用 Main
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
```

## 检查清单
- [ ] CoroutineScope 是否使用 Dispatchers.Default？
- [ ] UI 更新是否通过 EventQueue.invokeLater / invokeLater？
- [ ] 是否有在 postStartupActivity 中使用 Dispatchers.Main？

## 关键代码位置
- EventBus.kt: scope 定义
- CefBrowserPanel.kt: scope 定义
- MyProjectActivity.kt: scope 定义
- CefBrowserPanel.kt: sendToJavaScript() 中的 UI 操作
