package com.github.claudecode.ccgui.model.config

import com.github.claudecode.ccgui.util.IdGenerator
import com.google.gson.JsonObject

/**
 * 颜色方案
 */
data class ColorScheme(
    val primary: String = "#0d47a1",
    val background: String = "#1e1e1e",
    val foreground: String = "#d4d4d4",
    val muted: String = "#2d2d2d",
    val mutedForeground: String = "#858585",
    val accent: String = "#0d47a1",
    val accentForeground: String = "#ffffff",
    val destructive: String = "#f44336",
    val border: String = "#3c3c3c",
    val userMessage: String = "#0d47a1",
    val aiMessage: String = "#2d2d2d",
    val systemMessage: String = "#ff9800",
    val codeBackground: String = "#1e1e1e",
    val codeForeground: String = "#d4d4d4"
)

/**
 * 字体配置
 */
data class Typography(
    val messageFont: String = "Inter, -apple-system, BlinkMacSystemFont, sans-serif",
    val codeFont: String = "JetBrains Mono, Fira Code, monospace",
    val fontSize: Int = 14,
    val fontSizeSmall: Int = 12,
    val fontSizeLarge: Int = 16,
    val lineHeight: Double = 1.5
)

/**
 * 间距配置
 */
data class Spacing(
    val messageSpacing: Int = 16,
    val codeBlockPadding: Int = 12,
    val headerHeight: Int = 48,
    val sidebarWidth: Int = 280
)

/**
 * 圆角配置
 */
data class BorderRadius(
    val messageBubble: Int = 12,
    val codeBlock: Int = 8,
    val button: Int = 6,
    val input: Int = 6,
    val modal: Int = 12
)

/**
 * 阴影配置
 */
data class Shadow(
    val sm: String = "0 1px 2px 0 rgb(0 0 0 / 0.05)",
    val md: String = "0 4px 6px -1px rgb(0 0 0 / 0.1)",
    val lg: String = "0 10px 15px -3px rgb(0 0 0 / 0.1)",
    val xl: String = "0 20px 25px -5px rgb(0 0 0 / 0.1)"
)

/**
 * 主题配置
 */
data class ThemeConfig(
    val id: String = IdGenerator.prefixedShortId("theme_"),
    val name: String,
    val isDark: Boolean = true,
    val colors: ColorScheme = ColorScheme(),
    val typography: Typography = Typography(),
    val spacing: Spacing = Spacing(),
    val borderRadius: BorderRadius = BorderRadius(),
    val shadow: Shadow = Shadow()
) {

    companion object {
        /**
         * JetBrains Dark 主题预设
         */
        val JETBRAINS_DARK = ThemeConfig(
            id = "jetbrains-dark",
            name = "JetBrains Dark",
            isDark = true,
            colors = ColorScheme(
                primary = "#0d47a1",
                background = "#1e1e1e",
                foreground = "#d4d4d4",
                muted = "#2d2d2d",
                mutedForeground = "#858585",
                accent = "#0d47a1",
                accentForeground = "#ffffff",
                destructive = "#f44336",
                border = "#3c3c3c",
                userMessage = "#0d47a1",
                aiMessage = "#2d2d2d",
                systemMessage = "#ff9800",
                codeBackground = "#1e1e1e",
                codeForeground = "#d4d4d4"
            )
        )

        /**
         * JetBrains Light 主题预设
         */
        val JETBRAINS_LIGHT = ThemeConfig(
            id = "jetbrains-light",
            name = "JetBrains Light",
            isDark = false,
            colors = ColorScheme(
                primary = "#0d47a1",
                background = "#ffffff",
                foreground = "#333333",
                muted = "#f5f5f5",
                mutedForeground = "#666666",
                accent = "#0d47a1",
                accentForeground = "#ffffff",
                destructive = "#f44336",
                border = "#e0e0e0",
                userMessage = "#0d47a1",
                aiMessage = "#f5f5f5",
                systemMessage = "#ff9800",
                codeBackground = "#f5f5f5",
                codeForeground = "#333333"
            )
        )

        /**
         * GitHub Dark 主题预设
         */
        val GITHUB_DARK = ThemeConfig(
            id = "github-dark",
            name = "GitHub Dark",
            isDark = true,
            colors = ColorScheme(
                primary = "#58a6ff",
                background = "#0d1117",
                foreground = "#c9d1d9",
                muted = "#161b22",
                mutedForeground = "#8b949e",
                accent = "#58a6ff",
                accentForeground = "#ffffff",
                destructive = "#f85149",
                border = "#30363d",
                userMessage = "#58a6ff",
                aiMessage = "#161b22",
                systemMessage = "#f0883e",
                codeBackground = "#0d1117",
                codeForeground = "#c9d1d9"
            )
        )

        /**
         * VSCode Dark 主题预设
         */
        val VSCODE_DARK = ThemeConfig(
            id = "vscode-dark",
            name = "VSCode Dark",
            isDark = true,
            colors = ColorScheme(
                primary = "#007acc",
                background = "#1e1e1e",
                foreground = "#d4d4d4",
                muted = "#252526",
                mutedForeground = "#858585",
                accent = "#007acc",
                accentForeground = "#ffffff",
                destructive = "#f14c4c",
                border = "#3c3c3c",
                userMessage = "#007acc",
                aiMessage = "#252526",
                systemMessage = "#fcc419",
                codeBackground = "#1e1e1e",
                codeForeground = "#d4d4d4"
            )
        )

        /**
         * Monokai 主题预设
         */
        val MONOKAI = ThemeConfig(
            id = "monokai",
            name = "Monokai",
            isDark = true,
            colors = ColorScheme(
                primary = "#f92672",
                background = "#272822",
                foreground = "#f8f8f2",
                muted = "#3e3d32",
                mutedForeground = "#75715e",
                accent = "#f92672",
                accentForeground = "#ffffff",
                destructive = "#f92672",
                border = "#3e3d32",
                userMessage = "#f92672",
                aiMessage = "#3e3d32",
                systemMessage = "#e6db74",
                codeBackground = "#272822",
                codeForeground = "#f8f8f2"
            )
        )

        /**
         * Nord 主题预设
         */
        val NORD = ThemeConfig(
            id = "nord",
            name = "Nord",
            isDark = true,
            colors = ColorScheme(
                primary = "#88c0d0",
                background = "#2e3440",
                foreground = "#eceff4",
                muted = "#3b4252",
                mutedForeground = "#8e99a4",
                accent = "#88c0d0",
                accentForeground = "#2e3440",
                destructive = "#bf616a",
                border = "#4c566a",
                userMessage = "#88c0d0",
                aiMessage = "#3b4252",
                systemMessage = "#ebcb8b",
                codeBackground = "#2e3440",
                codeForeground = "#eceff4"
            )
        )

        /**
         * 获取所有预设主题
         */
        fun getPresets(): List<ThemeConfig> = listOf(
            JETBRAINS_DARK,
            JETBRAINS_LIGHT,
            GITHUB_DARK,
            VSCODE_DARK,
            MONOKAI,
            NORD
        )

        /**
         * 从 JSON 反序列化
         */
        fun fromJson(json: JsonObject): ThemeConfig {
            return ThemeConfig(
                id = json.get("id")?.asString ?: IdGenerator.prefixedShortId("theme_"),
                name = json.get("name")?.asString ?: "Custom Theme",
                isDark = json.get("isDark")?.asBoolean ?: true,
                colors = json.getAsJsonObject("colors")?.let { ColorScheme(
                    primary = it.get("primary")?.asString ?: "#0d47a1",
                    background = it.get("background")?.asString ?: "#1e1e1e",
                    foreground = it.get("foreground")?.asString ?: "#d4d4d4",
                    muted = it.get("muted")?.asString ?: "#2d2d2d",
                    mutedForeground = it.get("mutedForeground")?.asString ?: "#858585",
                    accent = it.get("accent")?.asString ?: "#0d47a1",
                    accentForeground = it.get("accentForeground")?.asString ?: "#ffffff",
                    destructive = it.get("destructive")?.asString ?: "#f44336",
                    border = it.get("border")?.asString ?: "#3c3c3c",
                    userMessage = it.get("userMessage")?.asString ?: "#0d47a1",
                    aiMessage = it.get("aiMessage")?.asString ?: "#2d2d2d",
                    systemMessage = it.get("systemMessage")?.asString ?: "#ff9800",
                    codeBackground = it.get("codeBackground")?.asString ?: "#1e1e1e",
                    codeForeground = it.get("codeForeground")?.asString ?: "#d4d4d4"
                ) } ?: ColorScheme(),
                typography = json.getAsJsonObject("typography")?.let {
                    Typography(
                        messageFont = it.get("messageFont")?.asString ?: "Inter, sans-serif",
                        codeFont = it.get("codeFont")?.asString ?: "JetBrains Mono, monospace",
                        fontSize = it.get("fontSize")?.asInt ?: 14,
                        fontSizeSmall = it.get("fontSizeSmall")?.asInt ?: 12,
                        fontSizeLarge = it.get("fontSizeLarge")?.asInt ?: 16,
                        lineHeight = it.get("lineHeight")?.asDouble ?: 1.5
                    )
                } ?: Typography(),
                spacing = json.getAsJsonObject("spacing")?.let {
                    Spacing(
                        messageSpacing = it.get("messageSpacing")?.asInt ?: 16,
                        codeBlockPadding = it.get("codeBlockPadding")?.asInt ?: 12,
                        headerHeight = it.get("headerHeight")?.asInt ?: 48,
                        sidebarWidth = it.get("sidebarWidth")?.asInt ?: 280
                    )
                } ?: Spacing(),
                borderRadius = json.getAsJsonObject("borderRadius")?.let {
                    BorderRadius(
                        messageBubble = it.get("messageBubble")?.asInt ?: 12,
                        codeBlock = it.get("codeBlock")?.asInt ?: 8,
                        button = it.get("button")?.asInt ?: 6,
                        input = it.get("input")?.asInt ?: 6,
                        modal = it.get("modal")?.asInt ?: 12
                    )
                } ?: BorderRadius(),
                shadow = json.getAsJsonObject("shadow")?.let {
                    Shadow(
                        sm = it.get("sm")?.asString ?: "",
                        md = it.get("md")?.asString ?: "",
                        lg = it.get("lg")?.asString ?: "",
                        xl = it.get("xl")?.asString ?: ""
                    )
                } ?: Shadow()
            )
        }
    }

    /**
     * 序列化为 JSON
     */
    fun toJson(): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("name", name)
            addProperty("isDark", isDark)
            add("colors", JsonObject().apply {
                addProperty("primary", colors.primary)
                addProperty("background", colors.background)
                addProperty("foreground", colors.foreground)
                addProperty("muted", colors.muted)
                addProperty("mutedForeground", colors.mutedForeground)
                addProperty("accent", colors.accent)
                addProperty("accentForeground", colors.accentForeground)
                addProperty("destructive", colors.destructive)
                addProperty("border", colors.border)
                addProperty("userMessage", colors.userMessage)
                addProperty("aiMessage", colors.aiMessage)
                addProperty("systemMessage", colors.systemMessage)
                addProperty("codeBackground", colors.codeBackground)
                addProperty("codeForeground", colors.codeForeground)
            })
            add("typography", JsonObject().apply {
                addProperty("messageFont", typography.messageFont)
                addProperty("codeFont", typography.codeFont)
                addProperty("fontSize", typography.fontSize)
                addProperty("fontSizeSmall", typography.fontSizeSmall)
                addProperty("fontSizeLarge", typography.fontSizeLarge)
                addProperty("lineHeight", typography.lineHeight)
            })
            add("spacing", JsonObject().apply {
                addProperty("messageSpacing", spacing.messageSpacing)
                addProperty("codeBlockPadding", spacing.codeBlockPadding)
                addProperty("headerHeight", spacing.headerHeight)
                addProperty("sidebarWidth", spacing.sidebarWidth)
            })
            add("borderRadius", JsonObject().apply {
                addProperty("messageBubble", borderRadius.messageBubble)
                addProperty("codeBlock", borderRadius.codeBlock)
                addProperty("button", borderRadius.button)
                addProperty("input", borderRadius.input)
                addProperty("modal", borderRadius.modal)
            })
            add("shadow", JsonObject().apply {
                addProperty("sm", shadow.sm)
                addProperty("md", shadow.md)
                addProperty("lg", shadow.lg)
                addProperty("xl", shadow.xl)
            })
        }
    }
}
