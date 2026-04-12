package com.github.claudecode.ccgui.util

import com.intellij.openapi.diagnostic.Logger
import java.nio.charset.StandardCharsets

/**
 * HTML 加载器
 *
 * 从 classpath 加载前端 HTML 文件，用于 JCEF 渲染
 */
class HtmlLoader(private val resourceClass: Class<*>) {

    companion object {
        private val log = Logger.getInstance(HtmlLoader::class.java)
        private const val HTML_RESOURCE_PATH = "/html/claude-chat.html"
    }

    /**
     * 加载聊天界面 HTML
     *
     * @return HTML 内容字符串，如果加载失败则返回 fallback HTML
     */
    fun loadChatHtml(): String {
        return try {
            val inputStream = resourceClass.getResourceAsStream(HTML_RESOURCE_PATH)
                ?: throw IllegalStateException("HTML resource not found: $HTML_RESOURCE_PATH")

            val html = String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            inputStream.close()

            log.info("[HtmlLoader] Successfully loaded chat HTML from classpath: $HTML_RESOURCE_PATH")
            log.info("[HtmlLoader] HTML size: ${html.length} bytes")

            // 注入 IDE 主题，防止白屏闪烁
            injectIdeTheme(html)
        } catch (e: Exception) {
            log.error("[HtmlLoader] Failed to load chat HTML", e)
            generateFallbackHtml()
        }
    }

    /**
     * 注入 IDE 主题到 HTML
     *
     * 策略：在 HTML 标签上添加内联样式，确保首帧渲染即应用正确背景色
     */
    private fun injectIdeTheme(html: String): String {
        return try {
            // TODO: 从 IDE 配置获取当前主题
            val isDark = true
            val theme = if (isDark) "dark" else "light"
            val bgColor = if (isDark) "#1e1e1e" else "#ffffff"

            // 1. 修改 <html> 标签添加内联样式
            var modifiedHtml = html.replaceFirst(
                "<html([^>]*)>".toRegex(),
                "<html$1 style=\"background-color:$bgColor;\">"
            )

            // 2. 修改 <body> 标签添加内联样式
            modifiedHtml = modifiedHtml.replaceFirst(
                "<body([^>]*)>".toRegex(),
                "<body$1 style=\"background-color:$bgColor;\">"
            )

            // 3. 在 <head> 后注入主题变量脚本
            val scriptInjection = "\n    <script>window.__INITIAL_IDE_THEME__ = '$theme';</script>"
            val headIndex = modifiedHtml.indexOf("<head>")
            if (headIndex != -1) {
                val insertPos = headIndex + "<head>".length
                modifiedHtml = modifiedHtml.substring(0, insertPos) +
                        scriptInjection +
                        modifiedHtml.substring(insertPos)
            }

            log.info("[HtmlLoader] Injected IDE theme: $theme, background: $bgColor")
            modifiedHtml
        } catch (e: Exception) {
            log.error("[HtmlLoader] Failed to inject IDE theme", e)
            html // 返回原始 HTML
        }
    }

    /**
     * 生成 fallback HTML
     */
    private fun generateFallbackHtml(): String {
        return """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>CC Assistant</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: #1e1e1e;
            color: #fff;
            display: flex;
            align-items: center;
            justify-content: center;
            height: 100vh;
            margin: 0;
        }
        .error {
            text-align: center;
            padding: 40px;
        }
        h1 {
            color: #f85149;
            margin-bottom: 16px;
        }
        p {
            color: #8b949e;
        }
    </style>
</head>
<body>
    <div class="error">
        <h1>Failed to load chat interface</h1>
        <p>Please verify that the HTML resource file exists at: $HTML_RESOURCE_PATH</p>
    </div>
</body>
</html>""".trimIndent()
    }
}
