# Phase 6: 性能优化与质量保障 (Optimization & Quality)

**优先级**: P1-P2
**预估工期**: 8人天
**前置依赖**: Phase 1-5 全部完成
**阶段目标**: 达标所有PRD性能指标、内存泄漏防护、完善错误恢复、提升测试覆盖率

---

> **⚠️ SDK集成架构修订 (2026-04-08)**
>
> ### 需要移除的组件
>
> | 组件 | 原因 |
> |------|------|
> | `HttpClientPool` (T6.3.1) | SDK通过CLI子进程通信，无需HTTP连接池。**移除T6.3任务** |
>
> ### 需要新增的测试文件（SDK组件）
>
> | 测试文件 | 对应源文件 | 优先级 |
> |---------|-----------|--------|
> | `StreamJsonParserTest.kt` | `adaptation/sdk/StreamJsonParser.kt` | P0 |
> | `SdkConfigBuilderTest.kt` | `adaptation/sdk/SdkConfigBuilder.kt` | P0 |
> | `SdkSessionManagerTest.kt` | `adaptation/sdk/SdkSessionManager.kt` | P1 |
> | `SdkPermissionHandlerTest.kt` | `adaptation/sdk/SdkPermissionHandler.kt` | P1 |
> | `ModelInfoRegistryTest.kt` | `adaptation/sdk/ModelInfoRegistry.kt` | P1 |
>
> ### 需要新增的测试文件（Phase 5组件）
>
> | 测试文件 | 对应源文件 |
> |---------|-----------|
> | `SkillsManagerTest.kt` | `application/skill/SkillsManager.kt` |
> | `SkillExecutorTest.kt` | `application/skill/SkillExecutor.kt` |
> | `AgentsManagerTest.kt` | `application/agent/AgentsManager.kt` |
> | `AgentExecutorTest.kt` | `application/agent/AgentExecutor.kt` |
> | `ScopeManagerTest.kt` | `application/mcp/ScopeManager.kt` |
>
> ### 文件统计修正
>
> | Phase | 新增文件数 | 累计文件数 |
> |-------|-----------|-----------|
> | Phase 1 | 38 | 38 |
> | Phase 2 | 9 (废弃3个Provider后) | 47 |
> | Phase 2.5 | 6 | 53 |
> | Phase 3 | 10 | 63 |
> | Phase 4 | 6 (移除2个废弃Provider后) | 69 |
> | Phase 5 | 6 | 75 |
> | Phase 6 | 7 + ~25测试 | ~107 |
>
> 注：移除 HttpClientPool 后T6.3为0文件，Phase 6 新增文件从8减为7

---

## 1. 阶段概览

本阶段是**质量保障和性能达标**阶段，确保后端满足PRD定义的所有性能指标：

| 指标 | 目标值 | 当前预估 | 优化策略 |
|------|--------|----------|----------|
| ToolWindow首次打开 | < 1.2s | ~2s | JCEF延迟加载 |
| 消息响应延迟 | < 300ms P95 | ~500ms | 连接池+缓存 |
| 流式输出首字延迟 | < 500ms | ~800ms | 流式优先 |
| 配置热更新延迟 | < 100ms | ~50ms | 已达标 |
| 会话切换延迟 | < 100ms | ~50ms | 已达标 |
| 内存占用 | < 500MB | ~600MB | JCEF生命周期管理 |

---

## 2. 任务清单

### T6.1 启动性能优化 (2人天)

#### T6.1.1 延迟加载策略

**优化目标**: ToolWindow首次打开 < 1.2s

```kotlin
// 文件: browser/LazyCefBrowserPanel.kt
package com.github.xingzhewa.ccgui.browser

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import java.awt.BorderLayout
import javax.JPanel

/**
 * 延迟加载的JCEF面板
 * ToolWindow创建时只创建JPanel外壳
 * 首次激活时才初始化JCEF浏览器
 */
class LazyCefBrowserPanel : JPanel(BorderLayout()) {

    private var browser: JBCefBrowser? = null
    private var isInitialized = false

    /**
     * 确保浏览器已初始化
     * 只在首次调用时创建JCEF实例
     */
    fun ensureInitialized(): JBCefBrowser {
        if (!isInitialized) {
            browser = JBCefBrowserBuilder().build()
            add(browser!!.component, BorderLayout.CENTER)
            isInitialized = true
        }
        return browser!!
    }

    fun isReady(): Boolean = isInitialized

    fun dispose() {
        browser?.dispose()
        browser = null
        isInitialized = false
    }
}
```

**关键策略**:
1. ToolWindow `createToolWindowContent` 时只创建空JPanel
2. 监听 `ToolWindowListener.activate` 首次激活时初始化JCEF
3. 全局共享 `JBCefClient` 单例
4. React bundle预编译到resources，避免运行时构建

---

#### T6.1.2 服务预热策略

```kotlin
// 文件: startup/PluginWarmupActivity.kt
package com.github.xingzhewa.ccgui.startup

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 插件预热
 * 在项目加载后异步预热关键服务
 */
class PluginWarmupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // 异步预热，不阻塞项目加载
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 预加载配置
            project.service<com.github.xingzhewa.ccgui.config.CCGuiConfig>()
            // 预加载Skills
            project.service<com.github.xingzhewa.ccgui.application.skill.SkillsManager>()
        }
    }
}
```

---

### T6.2 内存优化 (2人天)

#### T6.2.1 JCEF生命周期管理

**优化目标**: 内存占用 < 500MB

```kotlin
// 文件: infrastructure/memory/JcefMemoryManager.kt
package com.github.xingzhewa.ccgui.infrastructure.memory

import com.github.xingzhewa.ccgui.util.logger
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * JCEF内存管理器
 * 跟踪所有JCEF浏览器实例，定期回收未使用的实例
 *
 * 策略:
 *   - ToolWindow关闭时主动dispose浏览器
 *   - 10分钟未使用的浏览器自动回收
 *   - 超过内存阈值时警告用户
 */
class JcefMemoryManager : Disposable {

    private val log = logger<JcefMemoryManager>()
    private val browserRefs = ConcurrentHashMap<String, BrowserTracker>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cleanupInterval = 300_000L // 5分钟
    private val maxIdleTime = 600_000L     // 10分钟

    data class BrowserTracker(
        val id: String,
        val browserRef: WeakReference<com.intellij.ui.jcef.JBCefBrowser>,
        val lastAccessTime: Long,
        val parent: Disposable
    )

    init {
        scope.launch {
            while (isActive) {
                delay(cleanupInterval)
                cleanupIdleBrowsers()
            }
        }
    }

    /**
     * 注册浏览器实例
     */
    fun register(id: String, browser: com.intellij.ui.jcef.JBCefBrowser, parent: Disposable) {
        browserRefs[id] = BrowserTracker(
            id = id,
            browserRef = WeakReference(browser),
            lastAccessTime = System.currentTimeMillis(),
            parent = parent
        )
    }

    /**
     * 更新访问时间
     */
    fun touch(id: String) {
        browserRefs[id]?.let {
            browserRefs[id] = it.copy(lastAccessTime = System.currentTimeMillis())
        }
    }

    /**
     * 注销浏览器实例
     */
    fun unregister(id: String) {
        browserRefs.remove(id)?.browserRef?.get()?.dispose()
    }

    private fun cleanupIdleBrowsers() {
        val now = System.currentTimeMillis()
        browserRefs.entries.removeIf { (_, tracker) ->
            val idle = now - tracker.lastAccessTime
            if (idle > maxIdleTime) {
                tracker.browserRef.get()?.let { browser ->
                    browser.dispose()
                    log.info("Disposed idle JCEF browser: ${tracker.id} (idle ${idle / 1000}s)")
                }
                true
            } else false
        }
    }

    override fun dispose() {
        scope.cancel()
        browserRefs.keys.toList().forEach { unregister(it) }
    }
}
```

---

#### T6.2.2 消息分页加载

```kotlin
// 文件: infrastructure/memory/MessagePaginator.kt
package com.github.xingzhewa.ccgui.infrastructure.memory

import com.github.xingzhewa.ccgui.model.message.ChatMessage

/**
 * 消息分页加载器
 * 避免一次加载所有消息到内存
 *
 * 性能指标: 每页50条，切换延迟 < 100ms
 */
class MessagePaginator(
    private val pageSize: Int = 50
) {
    data class Page(
        val messages: List<ChatMessage>,
        val pageIndex: Int,
        val totalPages: Int,
        val hasNext: Boolean,
        val hasPrevious: Boolean
    )

    fun paginate(messages: List<ChatMessage>, pageIndex: Int = 0): Page {
        val total = messages.size
        val totalPages = (total + pageSize - 1) / pageSize
        val fromIndex = pageIndex * pageSize
        val toIndex = minOf(fromIndex + pageSize, total)

        return Page(
            messages = if (fromIndex < total) messages.subList(fromIndex, toIndex) else emptyList(),
            pageIndex = pageIndex,
            totalPages = totalPages,
            hasNext = pageIndex < totalPages - 1,
            hasPrevious = pageIndex > 0
        )
    }
}
```

---

### T6.3 网络性能优化 (1.5人天)

#### T6.3.1 HTTP连接池与重试

```kotlin
// 文件: infrastructure/network/HttpClientPool.kt
package com.github.xingzhewa.ccgui.infrastructure.network

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * HTTP连接池
 * 复用HTTP连接，减少TCP握手开销
 *
 * 性能要求: 消息响应延迟 < 300ms P95
 *
 * 扩展埋点: 后续可切换为Ktor HttpClient
 */
class HttpClientPool {

    private val log = logger<HttpClientPool>()

    data class RetryConfig(
        val maxRetries: Int = 3,
        val initialDelayMs: Long = 1000,
        val backoffFactor: Double = 2.0,
        val maxDelayMs: Long = 10000
    )

    /**
     * 带重试的HTTP请求
     */
    suspend fun <T> executeWithRetry(
        url: String,
        config: RetryConfig = RetryConfig(),
        block: (HttpURLConnection) -> T
    ): Result<T> {
        var currentDelay = config.initialDelayMs
        var lastException: Exception? = null

        repeat(config.maxRetries) { attempt ->
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                val result = block(connection)
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                log.warn("HTTP attempt ${attempt + 1}/${config.maxRetries} failed: ${e.message}")
                if (attempt < config.maxRetries - 1) {
                    delay(minOf(currentDelay, config.maxDelayMs))
                    currentDelay = (currentDelay * config.backoffFactor).toLong()
                }
            }
        }
        return Result.failure(lastException ?: RuntimeException("Unknown error"))
    }
}
```

---

### T6.4 缓存优化 (1人天)

#### T6.4.1 Markdown缓存策略

```kotlin
// 文件: infrastructure/cache/MarkdownCache.kt
package com.github.xingzhewa.ccgui.infrastructure.cache

/**
 * Markdown渲染结果缓存
 * 避免重复解析同一内容的Markdown
 *
 * 性能要求: 长消息渲染 < 200ms
 */
class MarkdownCache(maxSize: Int = 200) : CacheManager<String, String>(maxSize) {

    /**
     * 获取或计算Markdown渲染结果
     */
    fun getOrRender(content: String, renderer: (String) -> String): String {
        val cached = get(content.hashCode().toString())
        if (cached != null) return cached

        val rendered = renderer(content)
        put(content.hashCode().toString(), rendered)
        return rendered
    }

    /**
     * 使缓存失效
     */
    fun invalidate(content: String) {
        remove(content.hashCode().toString())
    }
}
```

---

### T6.5 错误恢复完善 (1人天)

#### T6.5.1 统一错误处理中间件

```kotlin
// 文件: infrastructure/error/ErrorHandler.kt
package com.github.xingzhewa.ccgui.infrastructure.error

import com.github.xingzhewa.ccgui.util.logger
import kotlinx.coroutines.CancellationException

/**
 * 统一错误处理器
 * 提供统一的异常捕获、日志记录、用户通知机制
 */
object ErrorHandler {

    private val log = logger<ErrorHandler>()

    /**
     * 安全执行异步操作
     */
    suspend fun <T> safeExecute(
        operation: String,
        block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e // 不捕获取消异常
        } catch (e: PluginException) {
            log.error("[$operation] Plugin error: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            log.error("[$operation] Unexpected error", e)
            Result.failure(PluginException.BridgeError("$operation failed: ${e.message}", e))
        }
    }

    /**
     * 带降级的执行
     */
    suspend fun <T> executeWithFallback(
        operation: String,
        primary: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        return try {
            primary()
        } catch (e: Exception) {
            log.warn("[$operation] Primary failed, using fallback: ${e.message}")
            fallback()
        }
    }
}
```

---

### T6.6 测试覆盖 (2.5人天)

#### T6.6.1 测试策略

| 测试层级 | 覆盖目标 | 框架 | 优先级 |
|----------|----------|------|--------|
| 单元测试 | 核心Manager > 80% | JUnit 5 + MockK | P0 |
| 集成测试 | 模块间交互 | JUnit 5 | P1 |
| 性能测试 | 性能指标达标 | 手动 + MetricsCollector | P1 |

#### T6.6.2 关键测试文件列表

```
src/test/kotlin/com/github/xingzhewa/ccgui/
├── infrastructure/
│   ├── EventBusTest.kt               # 事件发布订阅测试
│   ├── StateManagerTest.kt           # 状态管理测试
│   ├── CacheManagerTest.kt           # 缓存测试
│   └── ErrorRecoveryManagerTest.kt   # 错误恢复测试
├── adaptation/
│   ├── parser/
│   │   ├── MessageParserTest.kt      # 消息解析测试
│   │   └── StreamingResponseParserTest.kt
│   ├── bridge/
│   │   └── BridgeManagerTest.kt      # 桥接管理测试
│   └── version/
│       └── VersionDetectorTest.kt
├── application/
│   ├── session/
│   │   ├── SessionManagerTest.kt     # 会话管理测试
│   │   └── SessionInterruptRecoveryTest.kt
│   ├── orchestrator/
│   │   └── ChatOrchestratorTest.kt   # 聊天编排测试
│   ├── interaction/
│   │   └── InteractiveRequestEngineTest.kt
│   ├── task/
│   │   ├── TaskParserTest.kt
│   │   └── TaskProgressTrackerTest.kt
│   ├── prompt/
│   │   └── PromptOptimizerTest.kt
│   ├── multimodal/
│   │   └── MultimodalInputHandlerTest.kt
│   └── reference/
│       └── ConversationReferenceSystemTest.kt
└── model/
    └── SerializationTest.kt          # 模型序列化测试
```

#### T6.6.3 示例测试代码

**文件**: `src/test/kotlin/.../adaptation/parser/MessageParserTest.kt`

```kotlin
package com.github.xingzhewa.ccgui.adaptation.parser

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MessageParserTest {

    private val parser = MessageParser()

    @Test
    fun `parseLine should parse content_delta`() {
        val line = """{"type":"content_delta","content":"Hello","session_id":"sess_1"}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is MessageParser.ParsedLine.ContentDelta)
        assertEquals("Hello", (result as MessageParser.ParsedLine.ContentDelta).content)
    }

    @Test
    fun `parseLine should parse error`() {
        val line = """{"type":"error","message":"API error","code":"rate_limit"}"""
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is MessageParser.ParsedLine.Error)
        assertEquals("API error", (result as MessageParser.ParsedLine.Error).message)
    }

    @Test
    fun `parseLine should return RawText for non-JSON`() {
        val line = "This is plain text output"
        val result = parser.parseLine(line)

        assertNotNull(result)
        assertTrue(result is MessageParser.ParsedLine.RawText)
        assertEquals(line, (result as MessageParser.ParsedLine.RawText).text)
    }

    @Test
    fun `parseLine should return null for blank line`() {
        assertNull(parser.parseLine(""))
        assertNull(parser.parseLine("   "))
    }

    @Test
    fun `buildCliMessage should produce valid JSON`() {
        val message = parser.buildCliMessage("Hello", "sess_123")
        assertTrue(message.contains("\"type\":\"user_message\""))
        assertTrue(message.contains("\"content\":\"Hello\""))
        assertTrue(message.contains("\"session_id\":\"sess_123\""))
    }
}
```

---

### T6.7 指标收集 (0.5人天)

#### T6.7.1 `infrastructure/metrics/MetricsCollector.kt`

```kotlin
package com.github.xingzhewa.ccgui.infrastructure.metrics

import com.github.xingzhewa.ccgui.util.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能指标收集器
 * 收集关键操作的时间指标，用于性能分析和优化
 */
class MetricsCollector {

    private val log = logger<MetricsCollector>()
    private val metrics = ConcurrentHashMap<String, Metric>()

    data class Metric(
        val name: String,
        val count: AtomicLong = AtomicLong(0),
        val totalTimeMs: AtomicLong = AtomicLong(0),
        val minMs: AtomicLong = AtomicLong(Long.MAX_VALUE),
        val maxMs: AtomicLong = AtomicLong(0)
    ) {
        fun record(timeMs: Long) {
            count.incrementAndGet()
            totalTimeMs.addAndGet(timeMs)
            minMs.updateAndGet { minOf(it, timeMs) }
            maxMs.updateAndGet { maxOf(it, timeMs) }
        }

        fun avgMs(): Long = if (count.get() > 0) totalTimeMs.get() / count.get() else 0
    }

    /**
     * 记录操作耗时
     */
    fun record(operation: String, timeMs: Long) {
        metrics.getOrPut(operation) { Metric(operation) }.record(timeMs)
    }

    /**
     * 测量操作耗时
     */
    inline fun <T> measure(operation: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        return block().also {
            val elapsed = System.currentTimeMillis() - start
            record(operation, elapsed)
        }
    }

    /**
     * 获取指标报告
     */
    fun report(): Map<String, Map<String, Long>> {
        return metrics.mapValues { (_, metric) ->
            mapOf(
                "count" to metric.count.get(),
                "avgMs" to metric.avgMs(),
                "minMs" to metric.minMs.get(),
                "maxMs" to metric.maxMs.get()
            )
        }
    }

    fun reset() { metrics.clear() }
}
```

---

## 3. 任务依赖

```
T6.1 启动优化 ← 依赖 browser/包(Phase1) + startup/(已有)
T6.2 内存优化 ← 依赖 全部功能完成
T6.3 网络优化 ← 依赖 adaptation/(Phase2)
T6.4 缓存优化 ← 依赖 infrastructure/cache(Phase1)
T6.5 错误恢复 ← 依赖 infrastructure/error(Phase1)
T6.6 测试覆盖 ← 依赖 全部功能完成
T6.7 指标收集 ← 独立
```

---

## 4. 验收标准

| 验收项 | 标准 |
|--------|------|
| ToolWindow首次打开 | < 1.2s |
| 消息响应延迟 | < 300ms P95 |
| 流式输出首字延迟 | < 500ms |
| 配置热更新延迟 | < 100ms |
| 会话切换延迟 | < 100ms |
| 内存占用 | < 500MB |
| 无内存泄漏 | Profiler验证 |
| 单元测试覆盖率 | > 80% |
| 主题切换延迟 | < 100ms |
| 模型切换 | < 1s |

---

## 5. 文件清单汇总

| 序号 | 文件路径 | 类型 |
|------|----------|------|
| 1 | `browser/LazyCefBrowserPanel.kt` | 优化组件 |
| 2 | `startup/PluginWarmupActivity.kt` | 启动优化 |
| 3 | `infrastructure/memory/JcefMemoryManager.kt` | 内存管理 |
| 4 | `infrastructure/memory/MessagePaginator.kt` | 分页 |
| 5 | `infrastructure/network/HttpClientPool.kt` | 网络优化 |
| 6 | `infrastructure/cache/MarkdownCache.kt` | 缓存优化 |
| 7 | `infrastructure/error/ErrorHandler.kt` | 错误处理 |
| 8 | `infrastructure/metrics/MetricsCollector.kt` | 指标收集 |
| 9-22 | `src/test/...` (14个测试文件) | 测试 |

**共计**: 8个新文件 + 14个测试文件 = 22个文件

---

## 6. 全量文件统计

| Phase | 新增文件数 | 累计文件数 |
|-------|-----------|-----------|
| Phase 1 | 38 | 38 |
| Phase 2 | 12 | 50 |
| Phase 3 | 10 | 60 |
| Phase 4 | 6 + 2完善 | 68 |
| Phase 5 | 6 | 74 |
| Phase 6 | 8 + 14测试 | 96 |

**后端总计**: ~96个Kotlin文件（含测试）
