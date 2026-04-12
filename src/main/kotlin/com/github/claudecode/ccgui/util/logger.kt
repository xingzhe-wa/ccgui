package com.github.claudecode.ccgui.util

import com.intellij.openapi.diagnostic.Logger

/**
 * 日志工具类
 *
 * 提供统一的日志接口，基于 IntelliJ Platform 的 Logger
 *
 * 使用方式:
 *   private val logger = logger<MyClass>()
 *   logger.info("message")
 *   logger.warn("warning", e)
 *
 * 扩展埋点:
 *   - 后续可添加日志级别配置
 *   - 后续可添加日志文件输出
 */
object LoggerUtils {

    /**
     * 创建指定类的日志实例
     *
     * @param T 类类型
     * @return Logger 实例
     */
    inline fun <reified T : Any> logger(): Logger {
        return Logger.getInstance(T::class.java)
    }

    /**
     * 创建指定名称的日志实例
     *
     * @param name 日志实例名称
     * @return Logger 实例
     */
    fun logger(name: String): Logger {
        return Logger.getInstance(name)
    }
}

/**
 * 扩展属性，用于在类中快速获取日志实例
 *
 * 使用方式:
 *   class MyClass {
 *       private val logger = logger()
 *   }
 */
fun <T : Any> T.logger(): Logger = Logger.getInstance(this::class.java)

/**
 * 带标签的日志实例创建
 *
 * @param tag 标签名称
 * @return Logger 实例
 */
fun loggerForTag(tag: String): Logger = LoggerUtils.logger(tag)
