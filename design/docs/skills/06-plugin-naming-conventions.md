# 插件命名规范与 ID 管理

## 三层命名体系

| 层级 | 用途 | 示例 | 改动频率 |
|------|------|------|----------|
| 插件内部 ID | 代码中 getToolWindow() 引用 | "CCGUI" | 几乎不改 |
| Action ID | plugin.xml 中 action 标识 | "CCGUI.CodeExplain" | 几乎不改 |
| 显示名 | 用户可见的 UI 文本 | "CC Assistant" | 可能改 |

## 核心规则
1. **内部 ID 使用大驼峰无空格** (如 "CCGUI")
2. **显示名使用人类可读格式** (如 "CC Assistant")
3. **内部 ID 和显示名分离** — 不要用显示名做 getToolWindow() 的参数

## 实践

### plugin.xml
```xml
<idea-plugin>
  <name>CC Assistant</name>          <!-- 显示名 -->
  <toolWindow id="CCGUI" .../>       <!-- 内部 ID，不改 -->
  <action id="CCGUI.CodeExplain" ...> <!-- Action ID，不改 -->
</idea-plugin>
```

### Kotlin 代码
```kotlin
// use internal ID
val toolWindow = toolWindowManager.getToolWindow("CCGUI")

// display name via setTitle
toolWindow.setTitle("CC Assistant")
```

### 修改显示名的检查清单
- [ ] plugin.xml <name> 是否更新？
- [ ] MyToolWindowFactory 中 toolWindow.setTitle() 是否更新？
- [ ] 所有 action description 是否更新？
- [ ] system prompt 中的插件名称是否更新？
- [ ] 前端 AppLayout/SettingsView 是否更新？
- [ ] export 导出模板是否更新？
- [ ] **内部 ID 和 Action ID 不需要改**

## 踩坑记录
- 修改 toolWindow ID 但忘记更新 getToolWindow() 调用 → 找不到 tool window
- 显示名散落在 10+ 文件中 → 需要全局搜索替换
- system prompt 中写死插件名 → 用户看到旧名称

## 关键代码位置
- plugin.xml: 所有 name/id/description
- MyToolWindowFactory.kt: setTitle()
- ChatOrchestrator.kt: buildSystemPrompt()
- SdkConfigBuilder.kt: buildDefaultSystemPrompt()
- AppLayout.tsx: 侧边栏标题
- SettingsView.tsx: 版本信息
- exportToPDF.ts, exportToMarkdown.ts: 导出模板
