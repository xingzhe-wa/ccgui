package com.github.claudecode.ccgui.infrastructure.process

import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 进程管理器
 *
 * 封装进程启动、监控、终止等操作，支持超时控制和输出流处理
 */
class ProcessManager(private val project: Project) {

    private val log = logger<ProcessManager>()

    /** 活跃进程映射 */
    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    /** 进程状态 */
    private val _processStates = MutableStateFlow<Map<String, ProcessState>>(emptyMap())
    val processStates: StateFlow<Map<String, ProcessState>> = _processStates.asStateFlow()

    init {
        log.info("ProcessManager initialized")
    }

    /**
     * 托管进程
     */
    data class ManagedProcess(
        val id: String,
        val process: Process,
        val command: List<String>,
        val workingDirectory: File?,
        val startTime: Long,
        val scope: CoroutineScope,
        val isAlive: AtomicBoolean = AtomicBoolean(true),
        var exitCode: Int? = null
    )

    /**
     * 进程状态
     */
    enum class ProcessState {
        STARTING, RUNNING, COMPLETED, FAILED, CANCELLED, TIMEOUT
    }

    /**
     * 进程输出行处理器
     */
    interface OutputHandler {
        fun onLine(line: String)
        fun onError(line: String)
    }

    /**
     * 进程选项
     */
    data class ProcessOptions(
        val timeout: Long? = null,  // 超时时间（毫秒），null 表示无超时
        val inheritEnvironment: Boolean = true,
        val environment: Map<String, String> = emptyMap(),
        val redirectErrorStream: Boolean = false
    )

    /**
     * 启动进程
     */
    fun start(
        id: String,
        command: List<String>,
        workingDirectory: File? = null,
        options: ProcessOptions = ProcessOptions(),
        outputHandler: OutputHandler? = null
    ): ManagedProcess? {
        if (processes.containsKey(id)) {
            log.warn("Process with id $id already exists")
            return null
        }

        try {
            val pb = ProcessBuilder(command)
                .apply {
                    directory(workingDirectory ?: File(project.basePath ?: "."))
                    redirectErrorStream(options.redirectErrorStream)
                }

            // 设置环境变量
            if (!options.inheritEnvironment) {
                pb.environment().clear()
            }
            options.environment.forEach { (k, v) ->
                pb.environment()[k] = v
            }

            log.debug("Starting process: ${command.joinToString(" ")}")

            val process = pb.start()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val managedProcess = ManagedProcess(
                id = id,
                process = process,
                command = command,
                workingDirectory = workingDirectory,
                startTime = System.currentTimeMillis(),
                scope = scope
            )

            processes[id] = managedProcess
            updateProcessState(id, ProcessState.RUNNING)

            // 启动输出读取协程
            if (outputHandler != null) {
                scope.launch {
                    readOutput(process, outputHandler)
                }
            }

            // 启动进程监控协程
            scope.launch {
                monitorProcess(id, managedProcess, options.timeout)
            }

            log.info("Process started: $id")
            return managedProcess

        } catch (e: Exception) {
            log.error("Failed to start process: $id", e)
            updateProcessState(id, ProcessState.FAILED)
            return null
        }
    }

    /**
     * 启动进程并等待完成
     */
    suspend fun startAndWait(
        id: String,
        command: List<String>,
        workingDirectory: File? = null,
        options: ProcessOptions = ProcessOptions(),
        outputHandler: OutputHandler? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        val managedProcess = start(id, command, workingDirectory, options, outputHandler)
            ?: return@withContext ProcessResult(
                success = false,
                exitCode = -1,
                output = null,
                error = "Failed to start process"
            )

        try {
            managedProcess.process.waitFor()
            val exitCode = managedProcess.process.exitValue()

            managedProcess.exitCode = exitCode
            managedProcess.isAlive.set(false)
            updateProcessState(id, if (exitCode == 0) ProcessState.COMPLETED else ProcessState.FAILED)

            ProcessResult(
                success = exitCode == 0,
                exitCode = exitCode,
                output = null  // 输出由 OutputHandler 处理
            )
        } catch (e: Exception) {
            log.error("Process execution failed: $id", e)
            updateProcessState(id, ProcessState.FAILED)
            ProcessResult(
                success = false,
                exitCode = -1,
                output = null,
                error = e.message
            )
        }
    }

    /**
     * 读取进程输出
     */
    private suspend fun readOutput(process: Process, handler: OutputHandler) {
        withContext(Dispatchers.IO) {
            try {
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                // 同时读取 stdout 和 stderr
                val stdoutJob = CoroutineScope(SupervisorJob()).launch {
                    try {
                        var line: String?
                        while (stdoutReader.readLine().also { line = it } != null) {
                            handler.onLine(line!!)
                        }
                    } catch (e: Exception) {
                        // 读取结束
                    }
                }

                val stderrJob = CoroutineScope(SupervisorJob()).launch {
                    try {
                        var line: String?
                        while (stderrReader.readLine().also { line = it } != null) {
                            handler.onError(line!!)
                        }
                    } catch (e: Exception) {
                        // 读取结束
                    }
                }

                // 等待两个读取任务完成
                stdoutJob.join()
                stderrJob.join()

            } catch (e: Exception) {
                log.error("Error reading process output", e)
            }
        }
    }

    /**
     * 监控进程
     */
    private suspend fun monitorProcess(id: String, managedProcess: ManagedProcess, timeout: Long?) {
        withContext(Dispatchers.IO) {
            try {
                if (timeout != null) {
                    val completed = managedProcess.process.waitFor(timeout, TimeUnit.MILLISECONDS)
                    if (!completed) {
                        log.warn("Process timed out: $id")
                        managedProcess.process.destroyForcibly()
                        managedProcess.isAlive.set(false)
                        updateProcessState(id, ProcessState.TIMEOUT)
                    } else {
                        managedProcess.exitCode = managedProcess.process.exitValue()
                        managedProcess.isAlive.set(false)
                        updateProcessState(
                            id,
                            if (managedProcess.exitCode == 0) ProcessState.COMPLETED else ProcessState.FAILED
                        )
                    }
                } else {
                    // 无超时限制，等待进程自然结束
                    managedProcess.process.waitFor()
                    managedProcess.exitCode = managedProcess.process.exitValue()
                    managedProcess.isAlive.set(false)
                    updateProcessState(
                        id,
                        if (managedProcess.exitCode == 0) ProcessState.COMPLETED else ProcessState.FAILED
                    )
                }

                // 清理进程
                cleanupProcess(id)

            } catch (e: Exception) {
                log.error("Process monitoring failed: $id", e)
                updateProcessState(id, ProcessState.FAILED)
                cleanupProcess(id)
            }
        }
    }

    /**
     * 终止进程
     */
    fun terminate(id: String): Boolean {
        val managedProcess = processes[id] ?: return false

        return try {
            if (managedProcess.isAlive.get()) {
                managedProcess.process.destroy()
                Thread.sleep(100)
                if (managedProcess.process.isAlive) {
                    managedProcess.process.destroyForcibly()
                }
                managedProcess.isAlive.set(false)
            }
            updateProcessState(id, ProcessState.CANCELLED)
            cleanupProcess(id)
            log.info("Process terminated: $id")
            true
        } catch (e: Exception) {
            log.error("Failed to terminate process: $id", e)
            false
        }
    }

    /**
     * 强制终止进程
     */
    fun kill(id: String): Boolean {
        val managedProcess = processes[id] ?: return false

        return try {
            managedProcess.process.destroyForcibly()
            managedProcess.isAlive.set(false)
            updateProcessState(id, ProcessState.CANCELLED)
            cleanupProcess(id)
            log.info("Process killed: $id")
            true
        } catch (e: Exception) {
            log.error("Failed to kill process: $id", e)
            false
        }
    }

    /**
     * 终止所有进程
     */
    fun terminateAll() {
        processes.keys.toList().forEach { id ->
            terminate(id)
        }
    }

    /**
     * 获取进程状态
     */
    fun getState(id: String): ProcessState? {
        return _processStates.value[id]
    }

    /**
     * 检查进程是否存活
     */
    fun isAlive(id: String): Boolean {
        val managedProcess = processes[id] ?: return false
        return managedProcess.isAlive.get() && managedProcess.process.isAlive
    }

    /**
     * 获取进程信息
     */
    fun getProcessInfo(id: String): ProcessInfo? {
        val managedProcess = processes[id] ?: return null
        val state = _processStates.value[id] ?: return null

        return ProcessInfo(
            id = id,
            command = managedProcess.command,
            workingDirectory = managedProcess.workingDirectory?.absolutePath,
            state = state,
            startTime = managedProcess.startTime,
            uptime = System.currentTimeMillis() - managedProcess.startTime,
            exitCode = managedProcess.exitCode
        )
    }

    /**
     * 获取所有进程信息
     */
    fun getAllProcesses(): List<ProcessInfo> {
        return processes.keys.mapNotNull { getProcessInfo(it) }
    }

    private fun updateProcessState(id: String, state: ProcessState) {
        _processStates.value = _processStates.value.toMutableMap().apply {
            put(id, state)
        }
    }

    private fun cleanupProcess(id: String) {
        processes.remove(id)
        val stateMap = _processStates.value.toMutableMap()
        stateMap.remove(id)
        _processStates.value = stateMap
    }

    /**
     * 进程结果
     */
    data class ProcessResult(
        val success: Boolean,
        val exitCode: Int,
        val output: String?,
        val error: String? = null
    )

    /**
     * 进程信息
     */
    data class ProcessInfo(
        val id: String,
        val command: List<String>,
        val workingDirectory: String?,
        val state: ProcessState,
        val startTime: Long,
        val uptime: Long,
        val exitCode: Int?
    )

    companion object {
        fun getInstance(project: Project): ProcessManager =
            project.getService(ProcessManager::class.java)
    }
}
