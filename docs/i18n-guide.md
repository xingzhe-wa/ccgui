# 国际化（i18n）使用指南

## 概述

CC Assistant 插件使用统一的国际化驱动 `I18nManager` 来管理多语言支持。

## 功能特性

- ✅ 统一的国际化文本获取接口
- ✅ 支持多资源束（Bundle）管理
- ✅ 自动缓存优化，减少资源加载开销
- ✅ 支持运行时语言切换
- ✅ 提供默认值回退机制
- ✅ 支持 8 种语言（中文、英文、日语、韩语、西班牙语、法语、德语、葡萄牙语）

## 快速开始

### 1. 基本用法

```kotlin
// 获取本地化文本
val pluginName = I18nManager.message("plugin.name")

// 获取命令本地化文本
val newSession = I18nManager.command("command.session.new")
```

### 2. 带参数的文本

```kotlin
// 在资源文件中定义：service.project=Project service: {0}
val message = I18nManager.message("service.project", "MyProject")
// 输出：Project service: MyProject
```

### 3. 切换语言

```kotlin
// 通过 LocaleConfigManager 切换
val localeConfig = LocaleConfigManager.getInstance(project)
localeConfig.setLocale(Locale.ENGLISH)

// 通过语言代码切换
localeConfig.setLocaleByCode("zh")
```

## 资源文件结构

```
src/main/resources/messages/
├── MyBundle.properties       # 中文（默认）
├── MyBundle_en.properties    # 英文
├── MyBundle_ja.properties    # 日语
├── Commands.properties       # 命令中文
└── Commands_en.properties    # 命令英文
```

## API 参考

### I18nManager

全局单例，提供国际化文本获取服务。

| 方法 | 说明 |
|------|------|
| `message(key, vararg params)` | 获取 MyBundle 的本地化文本 |
| `command(key)` | 获取 Commands 的本地化文本 |
| `getText(bundleName, key, defaultValue)` | 获取指定 Bundle 的文本 |
| `setLocale(locale)` | 设置当前语言 |
| `getLocale()` | 获取当前语言 |
| `isChinese()` | 是否为中文环境 |
| `isEnglish()` | 是否为英文环境 |
| `clearCache()` | 清空缓存 |
| `preloadBundles()` | 预加载资源 |

### LocaleConfigManager

项目级服务，管理语言配置。

| 方法 | 说明 |
|------|------|
| `getCurrentLocale()` | 获取当前语言 |
| `setLocale(locale)` | 设置当前语言 |
| `setLocaleByCode(code)` | 通过语言代码设置语言 |
| `getSupportedLocales()` | 获取支持的语言列表 |
| `resetToSystemLocale()` | 重置为系统默认 |

## 支持的语言

| 代码 | 语言 | Locale |
|------|------|--------|
| zh | 简体中文 | `Locale.SIMPLIFIED_CHINESE` |
| en | English | `Locale.ENGLISH` |
| ja | 日本語 | `Locale.JAPANESE` |
| ko | 한국어 | `Locale.KOREAN` |
| es | Español | `Locale("es", "ES")` |
| fr | Français | `Locale.FRANCE` |
| de | Deutsch | `Locale.GERMANY` |
| pt | Português | `Locale("pt", "BR")` |

## 事件

语言变更时会发布 `LocaleChangedEvent`：

```kotlin
EventBus.subscribeType(LocaleChangedEvent::class.java) { event ->
    println("Locale changed to: ${event.locale.displayName}")
    // 更新 UI 或重新加载资源
}
```

## UI 组件

### LocaleSelectorComponent

语言选择下拉框：

```kotlin
val selector = LocaleSelectorComponent(project)
panel.add(selector.getPanel())
```

### LocaleStatusComponent

语言状态显示：

```kotlin
val status = LocaleStatusComponent(project)
panel.add(status.getPanel())
```

## 最佳实践

1. **使用 I18nManager 而非 ResourceBundle**
   ```kotlin
   // ✅ 推荐
   val text = I18nManager.message("key")

   // ❌ 不推荐
   val bundle = ResourceBundle.getBundle("messages.MyBundle")
   val text = bundle.getString("key")
   ```

2. **提供默认值**
   ```kotlin
   val text = I18nManager.message("key", "Default Value")
   ```

3. **监听语言变更事件**
   ```kotlin
   EventBus.subscribeType(LocaleChangedEvent::class.java) { event ->
       // 重新加载 UI 文本
   }
   ```

4. **预加载资源**
   ```kotlin
   // 在应用启动时调用
   I18nManager.preloadBundles()
   ```

## 添加新语言

1. 创建新的资源文件：`MyBundle_xx.properties`
2. 复制现有内容并翻译
3. 在 `I18nManager.SupportedLocale` 中添加新语言

## 性能优化

- I18nManager 自动缓存资源束和文本
- 使用 `clearCache()` 可以强制重新加载
- 使用 `preloadBundles()` 预加载资源以提升首次访问速度
