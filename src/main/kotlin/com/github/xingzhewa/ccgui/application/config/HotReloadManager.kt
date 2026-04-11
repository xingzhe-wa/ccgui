package com.github.xingzhewa.ccgui.application.config

import com.github.xingzhewa.ccgui.util.logger
import com.github.xingzhewa.ccgui.util.LocalSettingsReader
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

/**
 * 配置热更新管理器
 *
 * 监听外部配置文件变化（主要是 ~/.claude/settings.json），
 * 在文件变更时自动通知注册的配置变更监听器。
 *
 * 使用 WatchService 实现文件系统级别的变更监听，
 * 支持在 IDE 运行时检测到 Claude CLI 配置的外部修改。
 *
 * @param project IntelliJ 项目实例
 */
@Service(Service.Level.PROJECT)
class HotReloadManager(private val project: Project) : Disposable {

    private val log = logger<HotReloadManager>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** WatchService 任务 */
    private var watchJob: Job? = null

    /** 监听的文件路径集合 */
    private val watchedFiles = mutableSetOf<Path>()

    /** 配置变更监听器 */
    private val changeListeners = mutableListOf<ConfigChangeListener>()

    /** 防抖延迟（毫秒），避免文件变更时的频繁触发 */
    private val debounceDelayMs = 500L

    /** 上次触发时间戳（用于防抖） */
    private var lastTriggerTime = 0L

    /** 触发锁定（防止并发触发） */
    private val triggerLock = Any()

    init {
        log.info("HotReloadManager initialized")
    }

    /**
     * 启动文件监听
     *
     * 异步启动 WatchService 协程，监听所有已注册的文件。
     */
    fun start() {
        if (watchJob?.isActive == true) {
            log.warn("HotReloadManager: already running")
            return
        }

        watchJob = scope.launch {
            runWatchLoop()
        }
        log.info("HotReloadManager: started watching ${watchedFiles.size} file(s)")
    }

    /**
     * 停止文件监听
     */
    fun stop() {
        watchJob?.cancel()
        watchJob = null
        log.info("HotReloadManager: stopped")
    }

    /**
     * 注册要监听的文件
     *
     * @param filePath 文件路径字符串
     * @return true 如果文件存在且已注册
     */
    fun registerFile(filePath: String): Boolean {
        val path = java.nio.file.Paths.get(filePath)
        return registerFilePath(path)
    }

    /**
     * 注册要监听的文件路径
     *
     * @param path 文件路径
     * @return true 如果文件存在且已注册
     */
    fun registerFilePath(path: Path): Boolean {
        if (!Files.exists(path)) {
            log.warn("HotReloadManager: file does not exist: $path")
            return false
        }

        synchronized(watchedFiles) {
            // 获取父目录进行监听（WatchService 监听目录而非文件）
            val watchDir = path.parent ?: path
            if (watchedFiles.add(path)) {
                log.info("HotReloadManager: registered file $path (watching dir: $watchDir)")
            }
        }
        return true
    }

    /**
     * 注册监听本地设置文件
     *
     * 便捷方法，自动获取并注册 ~/.claude/settings.json
     *
     * @return true 如果文件存在且已注册
     */
    fun registerLocalSettingsFile(): Boolean {
        return if (LocalSettingsReader.isSettingsFileExists()) {
            registerFile(LocalSettingsReader.getSettingsFilePath())
        } else {
            log.info("HotReloadManager: local settings file does not exist yet")
            false
        }
    }

    /**
     * 注销监听的路径
     *
     * @param filePath 文件路径字符串
     */
    fun unregisterFile(filePath: String) {
        val path = java.nio.file.Paths.get(filePath)
        synchronized(watchedFiles) {
            watchedFiles.remove(path)
        }
        log.info("HotReloadManager: unregistered file $filePath")
    }

    /**
     * 添加配置变更监听器
     *
     * @param listener 监听器实例
     */
    fun addChangeListener(listener: ConfigChangeListener) {
        synchronized(changeListeners) {
            changeListeners.add(listener)
        }
    }

    /**
     * 移除配置变更监听器
     *
     * @param listener 监听器实例
     */
    fun removeChangeListener(listener: ConfigChangeListener) {
        synchronized(changeListeners) {
            changeListeners.remove(listener)
        }
    }

    /**
     * 主监听循环
     */
    private suspend fun runWatchLoop() {
        val watchService: WatchService = FileSystems.getDefault().newWatchService()

        try {
            // 注册所有需要监听的目录
            val watchKeys = mutableMapOf<WatchKey, Path>()
            synchronized(watchedFiles) {
                watchedFiles.forEach { filePath ->
                    val watchDir = filePath.parent ?: filePath
                    try {
                        val key = watchDir.register(
                            watchService,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE
                        )
                        watchKeys[key] = watchDir
                    } catch (e: Exception) {
                        log.warn("HotReloadManager: failed to register watch on $watchDir: ${e.message}")
                    }
                }
            }

            if (watchKeys.isEmpty()) {
                log.info("HotReloadManager: no files to watch, exiting watch loop")
                return
            }

            log.info("HotReloadManager: watching ${watchKeys.size} directories")

            // 监听循环
            while (scope.isActive) {
                val key: WatchKey = try {
                    watchService.take()
                } catch (e: InterruptedException) {
                    log.info("HotReloadManager: watch loop interrupted")
                    break
                }

                val watchDir = watchKeys[key]
                if (watchDir == null) {
                    key.cancel()
                    continue
                }

                // 处理事件
                for (event in key.pollEvents()) {
                    handleWatchEvent(event, watchDir)
                }

                // 重置 key 并检查是否继续
                val valid = key.reset()
                if (!valid) {
                    watchKeys.remove(key)
                    if (watchKeys.isEmpty()) {
                        log.info("HotReloadManager: all watch keys invalidated, exiting")
                        break
                    }
                }
            }
        } finally {
            watchService.close()
        }
    }

    /**
     * 处理 WatchEvent
     *
     * @param event 来自 WatchService 的事件
     * @param watchDir 监听目录
     */
    private fun handleWatchEvent(event: WatchEvent<*>, watchDir: Path) {
        @Suppress("UNCHECKED_CAST")
        val kind = event.kind()
        val context = event.context() as? Path ?: return

        // 忽略目录事件
        if (Files.isDirectory(watchDir.resolve(context))) return

        synchronized(watchedFiles) {
            val fullPath = watchDir.resolve(context)
            if (fullPath !in watchedFiles) return
        }

        // 防抖处理
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY || kind == StandardWatchEventKinds.ENTRY_CREATE) {
            val now = System.currentTimeMillis()
            synchronized(triggerLock) {
                if (now - lastTriggerTime < debounceDelayMs) {
                    log.debug("HotReloadManager: debounce skipped for $context")
                    return
                }
                lastTriggerTime = now
            }

            log.info("HotReloadManager: detected change in $context, notifying listeners")
            notifyListeners(context.toString(), kind == StandardWatchEventKinds.ENTRY_CREATE)
        }
    }

    /**
     * 通知所有监听器
     *
     * @param filePath 变更的文件路径
     * @param isNewFile 是否为新建文件
     */
    private fun notifyListeners(filePath: String, isNewFile: Boolean) {
        val listeners: List<ConfigChangeListener> = synchronized(changeListeners) {
            changeListeners.toList()
        }

        listeners.forEach { listener ->
            try {
                listener.onConfigFileChanged(filePath, isNewFile)
            } catch (e: Exception) {
                log.error("HotReloadManager: error notifying listener: ${e.message}", e)
            }
        }
    }

    /**
     * 获取当前监听的文件数量
     */
    val watchedFileCount: Int
        get() = synchronized(watchedFiles) { watchedFiles.size }

    /**
     * 获取监听器数量
     */
    val listenerCount: Int
        get() = synchronized(changeListeners) { changeListeners.size }

    override fun dispose() {
        stop()
        synchronized(changeListeners) {
            changeListeners.clear()
        }
        synchronized(watchedFiles) {
            watchedFiles.clear()
        }
        log.info("HotReloadManager disposed")
    }

    companion object {
        fun getInstance(project: Project): HotReloadManager {
            return project.getService(HotReloadManager::class.java)
        }
    }
}

/**
 * 配置变更监听器接口
 *
 * 用于接收配置文件变更通知。
 */
interface ConfigChangeListener {
    /**
     * 当配置文件发生变更时调用
     *
     * @param filePath 变更的文件完整路径
     * @param isNewFile 是否为新建文件（false 表示修改）
     */
    fun onConfigFileChanged(filePath: String, isNewFile: Boolean)
}
