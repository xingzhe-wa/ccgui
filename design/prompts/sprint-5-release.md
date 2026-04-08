# Sprint 5 断点 Prompt：打包发版

> **角色**：你是一个负责发布管理的 IntelliJ 插件开发者。
> **目标**：产出可分发的 v0.0.1 插件包，手动安装验证通过。
> **前置条件**：Sprint 1-4 全部完成，核心功能稳定可用。

---

## 项目背景

经过 Sprint 1-4，插件的核心功能已验证可用。现在需要最终打包并验证安装流程。

## 你的任务

### Task 5.1：更新 plugin.xml 元数据

读取 `src/main/resources/META-INF/plugin.xml`，更新以下字段：

```xml
<idea-plugin>
    <id>com.github.xingzhewa.ccgui</id>
    <name>CCGUI</name>
    <vendor url="https://github.com/xingzhewa/ccgui">xingzhewa</vendor>

    <description><![CDATA[
    ClaudeCodeJet - AI-Powered Coding Assistant for IntelliJ IDEA

    Features:
    • Chat with Claude AI directly in your IDE
    • Streaming response with real-time display
    • Multi-session management
    • Theme customization (JetBrains Dark/Light, GitHub Dark, VSCode Dark, etc.)
    • Agent & Skill ecosystem

    Requirements:
    • Claude CLI installed and available in PATH
    • IntelliJ IDEA 2025.2+
    ]]></description>

    <change-notes><![CDATA[
    v0.0.1 - Initial Release
    • Core chat functionality with streaming support
    • Session management (create/switch/delete)
    • Theme system with 9 preset themes
    • Agent & Skill management panels
    • MCP server configuration
    ]]></change-notes>
</idea-plugin>
```

### Task 5.2：最终构建

1. 确保前端构建产物是最新的：
   ```bash
   cd webview && npm run build
   ```

2. 执行完整构建：
   ```bash
   cd .. && ./gradlew clean buildPlugin
   ```

3. 找到产物：
   ```bash
   ls -la build/distributions/
   # 应该看到 CCGUI-0.0.1.zip
   ```

4. 解压检查内容完整性：
   ```bash
   unzip -l build/distributions/CCGUI-0.0.1.zip
   ```
   确认包含：
   - `lib/ccgui-0.0.1.jar`（后端代码）
   - `webview/dist/index.html`（前端入口）
   - `webview/dist/assets/*.js`（前端 JS chunks）
   - `webview/dist/assets/*.css`（前端样式）

### Task 5.3：手动安装测试

在一个**全新的** IDEA 实例中测试（不能用 runIde 的开发实例）：

1. 打开 IDEA → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
2. 选择 `build/distributions/CCGUI-0.0.1.zip`
3. 重启 IDEA
4. 验证步骤：
   - [ ] 右侧边栏出现 "CCGUI" 图标
   - [ ] 点击打开，页面加载不白屏（JetBrains Dark 主题）
   - [ ] 输入 "Hello, test" → 点击发送 → 收到流式回复
   - [ ] 点击 "+" 创建新会话 → 切换到新会话 → 发消息 → 切回旧会话 → 消息还在
   - [ ] 左侧导航切换到 Settings → ThemeSwitcher 切换主题 → 颜色变化
   - [ ] 切换到 Skills/Agents/MCP 页面 → 列表显示 mock 数据（这些页面不需要后端）
   - [ ] 关闭 Tool Window → 重新打开 → 状态恢复

### Task 5.4：Git 打 Tag

```bash
git add -A
git commit -m "release: v0.0.1 - initial working version"
git tag -a v0.0.1 -m "v0.0.1 - Initial Release

Features:
- Core chat with Claude AI (streaming)
- Multi-session management
- Theme system (9 presets)
- Agent & Skill management
- MCP server configuration"
git push origin main --tags
```

### Task 5.5：(可选) GitHub Release

如果需要发布到 GitHub：
```bash
gh release create v0.0.1 \
  build/distributions/CCGUI-0.0.1.zip \
  --title "v0.0.1 - Initial Release" \
  --notes "First working version of ClaudeCodeJet"
```

## 验收标准

- [ ] `./gradlew clean buildPlugin` 构建成功
- [ ] .zip 文件包含完整前端资源
- [ ] 全新 IDEA 实例安装后可正常使用
- [ ] 发消息 → 收流式回复 完整流程通过
- [ ] 会话 CRUD 通过
- [ ] 主题切换通过
- [ ] Git tag v0.0.1 已创建
- [ ] 无 ERROR 级别日志

## 发版完成

至此 v0.0.1 发版完成。后续版本规划：

### v0.1.0 规划（预计 v0.0.1 后 2 周）
- Agent Balanced/Aggressive 执行模式
- MCP 持久化 + 能力解析
- 完善错误处理和日志
- 补充自动化测试
- 布局重构（按设计文档 Header 方案）

### v1.0.0 规划
- JetBrains Marketplace 发布
- PreviewPanel / DiffViewer
- 响应式布局
- 国际化 (i18n)
- 完整测试覆盖 >80%
