package com.github.claudecode.ccgui.bridge

/**
 * 流式回调接口
 *
 * 用于 BridgeManager 和前端之间的流式数据传递
 */
interface StreamCallback {

    /**
     * 接收到一行数据
     */
    fun onLineReceived(line: String)

    /**
     * 流式输出完成
     *
     * @param messages 完整的消息列表
     */
    fun onStreamComplete(messages: List<String>)

    /**
     * 流式输出错误
     *
     * @param error 错误信息
     */
    fun onStreamError(error: String)

    /**
     * 流式输出开始
     */
    fun onStreamStart() {}

    /**
     * 流式输出取消
     */
    fun onStreamCancelled() {}
}

/**
 * 简单的流式回调基类
 *
 * 提供默认的空实现，子类只需重写需要的方法
 */
abstract class SimpleStreamCallback : StreamCallback {

    override fun onLineReceived(line: String) {}

    override fun onStreamComplete(messages: List<String>) {}

    override fun onStreamError(error: String) {}

    override fun onStreamStart() {}

    override fun onStreamCancelled() {}
}
