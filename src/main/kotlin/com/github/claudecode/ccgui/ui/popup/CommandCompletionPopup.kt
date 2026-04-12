package com.github.claudecode.ccgui.ui.popup

import com.github.claudecode.ccgui.application.slash.command.SlashCommandRegistry
import com.github.claudecode.ccgui.util.logger
import com.intellij.openapi.Disposable

/**
 * 斜杠指令补全弹窗
 *
 * 提供斜杠指令(/command)的自动补全功能
 *
 * 注意: 当前版本仅提供基础实现
 * Webview 端的 SlashCommandPalette 已提供完整的指令补全功能
 *
 * @param registry 斜杠命令注册表
 */
class CommandCompletionPopup(
    private val registry: SlashCommandRegistry
) : Disposable {

    private val log = logger<CommandCompletionPopup>()

    init {
        log.debug("CommandCompletionPopup initialized")
    }

    /**
     * 获取匹配的指令列表
     *
     * @param prefix 命令前缀
     * @return 匹配的命令列表
     */
    fun getMatchingCommands(prefix: String): List<String> {
        return registry.find(prefix).map { "/${it.name}" }
    }

    override fun dispose() {
        log.debug("CommandCompletionPopup disposed")
    }
}