# ClaudeCodeJet PRD v3.0 - 企业级完整版

**文档版本**: 3.0  
**创建日期**: 2026-04-08  
**项目周期**: 18周（4个阶段）  
**目标发布**: 2026年Q4  
**文档状态**: 正式版

---

## 1. 项目概述（升级）

### 1.1 项目背景与用户痛点

**项目背景**：
当前AI编程工具市场存在明显的两极分化：Web端聊天机器人缺乏IDE深度集成，而IDE内置插件（如GitHub Copilot）功能单一、缺乏自定义能力。Claude Code CLI提供了强大的代码生成与理解能力，但其命令行交互方式与现代开发者的可视化习惯存在显著gap。

**核心痛点升级**：
1. **上下文切换成本高**：终端命令行与IDE编辑器之间频繁切换破坏心流状态，导致开发者注意力分散
2. **代码审查效率低**：CLI生成的代码修改无法直接在IDE中预览和应用，缺乏可视化的diff工具
3. **配置管理复杂**：MCP、Skills、Agent等高级功能需要手动编辑JSON配置，技术门槛高
4. **状态不可见**：缺乏实时的Token消耗、模型状态、任务进度等可视化反馈
5. **交互体验割裂**：无法支持多模态输入、提示词优化、交互式决策等高级交互场景
6. **会话管理缺失**：缺乏多会话并行、历史回溯、流式输出打断等企业级会话管理能力
7. **生态集成困难**：Skills/Agents/MCP等Claude Code生态功能缺乏可视化管理界面

**成功指标（量化升级）**：
- **用户留存率**: 首周留存 > 70%，30天留存 > 50%（提升10个百分点）
- **使用频次**: DAU/MAU > 0.5（每周至少使用3.5次）
- **性能指标**: 
  - ToolWindow首次打开 < 1.2s（提升300ms）
  - 消息响应延迟 < 300ms P95（提升200ms）
  - 配置热更新延迟 < 100ms
  - 流式输出首字延迟 < 500ms
- **兼容性**: 支持IntelliJ IDEA 2022.3+，覆盖98%+活跃用户
- **功能完成度**: 5大核心功能模块完成度100%

### 1.2 目标用户画像（精细化）

**主要用户**：
- **全栈工程师**（40%）：频繁在前端/后端间切换，需要AI辅助理解复杂业务逻辑
- **后端架构师**（30%）：处理大规模代码库，需要代码审查和重构建议
- **DevOps工程师**（15%）：需要自动化脚本生成和配置管理

**次要用户**：
- **技术团队负责人**（10%）：关注团队代码质量和AI辅助决策
- **AI工具早期采纳者**（5%）：探索AI编程边界，需要高度自定义能力

**用户规模**：
- JetBrains生态全球活跃用户约1200万
- 目标首年用户：10万活跃用户
- 目标次年用户：50万活跃用户

---

## 2. 核心功能架构（新增）

### 2.1 功能模块全景图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        ClaudeCodeJet 功能架构                            │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   3.2 交互增强系统                              │   │
│  │  ├── 提示词优化器 (PromptOptimizer)                             │   │
│  │  ├── 代码快捷操作 (CodeQuickActions)                            │   │
│  │  ├── 多模态输入处理 (MultimodalInputHandler)                    │   │
│  │  ├── 对话引用系统 (ConversationReferenceSystem)                 │   │
│  │  └── 交互式请求引擎 (InteractiveRequestEngine) ⭐核心           │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   3.3 会话管理系统                              │   │
│  │  ├── 多会话管理器 (MultiSessionManager)                         │   │
│  │  ├── 流式输出引擎 (StreamingOutputEngine)                       │   │
│  │  ├── 会话中断与恢复 (SessionInterruptRecovery)                  │   │
│  │  ├── 历史会话检索 (HistorySessionSearch)                        │   │
│  │  └── 任务进度可视化 (TaskProgressVisualization)                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   3.4 模型配置系统                              │   │
│  │  ├── 多供应商适配器 (MultiProviderAdapter)                      │   │
│  │  ├── 模型切换器 (ModelSwitcher)                                 │   │
│  │  └── 对话模式管理器 (ConversationModeManager)                   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   3.5 Claude Code生态集成                       │   │
│  │  ├── Skills管理器 (SkillsManager)                               │   │
│  │  ├── Agents管理器 (AgentsManager)                               │   │
│  │  ├── MCP服务器管理器 (McpServerManager)                         │   │
│  │  └── 作用域管理 (ScopeManager: 全局 vs 项目)                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   3.1 UI与主题系统                              │   │
│  │  ├── 主题引擎 (ThemeEngine: 预设 + 自定义)                      │   │
│  │  ├── 配置热更新机制 (ConfigHotReload)                           │   │
│  │  └── 响应式布局设计 (ResponsiveLayout)                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   4. 技术架构深度设计                            │   │
│  │  ├── 整体架构图 (系统级模块交互)                                 │   │
│  │  ├── 核心组件设计 (接口定义、通信协议、数据流)                   │   │
│  │  ├── JCEF集成架构 (Java↔JS双向通信、React组件)                  │   │
│  │  └── 状态管理设计 (会话/配置/任务进度状态)                      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈选型（Swing vs JCEF详细决策）

#### 2.2.1 技术方案对比分析（升级版）

| 技术栈 | 优势 | 劣势 | 适用场景 | 开发成本 | 性能指标 |
|--------|------|------|----------|----------|----------|
| **Swing 原生** | • 零启动开销<br>• 深度IDE集成<br>• 内存占用低<br>• 原生快捷键支持 | • 复杂布局实现困难<br>• 自定义样式受限<br>• 动画效果差 | 配置页面、简单对话框、弹出菜单、状态栏Widget | 低 | 启动<10ms |
| **JCEF** | • 完整Web技术栈<br>• 丰富的Markdown渲染库<br>• 灵活的交互设计<br>• 支持复杂动画 | • 首次启动200-500ms开销<br>• 内存占用~100MB<br>• Java-JS通信复杂<br>• 调试困难 | 复杂数据展示、交互式面板、可视化图表、聊天界面 | 中高 | 启动<500ms |
| **混合架构** | • 性能与体验平衡<br>• 技术选型灵活<br>• 开发效率高 | • 架构复杂度高<br>• 需要统一设计语言 | 企业级复杂应用 | 中 | 按模块优化 |

#### 2.2.2 分模块技术选型决策（精细化）

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ClaudeCodeJet UI技术架构                         │
├─────────────────────────────────────────────────────────────────────┤
│  Swing原生组件（轻量级、高频交互、系统级集成）                       │
│  ├── Settings Configurable (Kotlin UI DSL)                         │
│  ├── Popup Actions (右键菜单、工具栏按钮)                           │
│  ├── Inline Hints (代码建议浮层)                                    │
│  ├── Notifications (系统通知)                                      │
│  ├── StatusBar Widget (状态栏显示)                                  │
│  └── Quick Actions (快捷操作面板)                                   │
├─────────────────────────────────────────────────────────────────────┤
│  JCEF组件（复杂渲染、数据密集型、富交互场景）                        │
│  ├── Chat Tool Window (React + Markdown渲染) ⭐核心                 │
│  ├── Skills Management Panel (拖拽排序 + 实时预览)                  │
│  ├── MCP Configuration UI (表单验证 + 连接测试)                    │
│  ├── Session Management (多会话Tab + 搜索)                          │
│  ├── Task Progress Dashboard (任务分解可视化)                       │
│  └── Theme Preview (主题实时预览)                                   │
├─────────────────────────────────────────────────────────────────────┤
│  通信层架构                                                          │
│  ├── Java → JS: JBCefJSQuery (单向调用)                            │
│  ├── JS → Java: CefJavaScriptExecutor (回调机制)                   │
│  └── 状态同步: EventBus + 响应式状态流                              │
└─────────────────────────────────────────────────────────────────────┘
```

**决策依据详细说明**：

1. **配置页面使用Kotlin UI DSL**：
   - 保持与IDEA原生设置页一致的视觉风格
   - 零额外内存开销，启动速度快
   - 充分利用`Configurable`和`BoundField`组件
   - 配置热更新通过`SettingsChangeNotifier`实现

2. **聊天窗口使用JCEF（关键决策）**：
   - 需要复杂的Markdown渲染（代码块、表格、LaTeX、Mermaid图）
   - 消息历史需要虚拟滚动优化（支持1000+条消息）
   - 流式输出需要实时渲染和动画效果
   - 前端生态成熟（可使用`marked.js` + `highlight.js` + `react-markdown`）

3. **JCEF性能优化策略（升级版）**：
   - **延迟加载**：ToolWindow首次打开时才初始化`JBCefBrowser`
   - **资源复用**：全局单例`JBCefClient`，多页面共享进程
   - **生命周期管理**：监听ToolWindow关闭事件，主动调用`dispose()`
   - **内存优化**：虚拟滚动 + 消息分页加载（每页50条）
   - **渲染优化**：Markdown解析缓存 + 代码块懒加载

### 2.3 分层架构设计

```
┌─────────────────────────────────────────────────────────────────────┐
│                   Presentation Layer (UI)                           │
├─────────────────────────────────────────────────────────────────────┤
│  Swing Layer                  │  JCEF Layer                         │
│  ├── SettingsPanel            │  ├── ChatWindow (React 18)          │
│  ├── PopupActions             │  ├── SessionManager (多Tab)         │
│  ├── InlineHints              │  ├── MarkdownRenderer               │
│  └── StatusBarWidget          │  ├── TaskProgressDashboard          │
│                               │  └── ThemeCustomizer                │
├─────────────────────────────────────────────────────────────────────┤
│                   Application Layer (Core)                          │
├─────────────────────────────────────────────────────────────────────┤
│  ├── ChatOrchestrator         │  ├── ConfigManager                  │
│  ├── SessionManager           │  ├── ThemeManager                   │
│  ├── ContextProvider          │  ├── TokenTracker                   │
│  ├── TaskProgressTracker      │  └── EventDispatcher                │
│  ├── PromptOptimizer          │  ├── InteractiveRequestEngine ⭐    │
│  └── MultimodalInputHandler   │  └── HotReloadNotifier              │
├─────────────────────────────────────────────────────────────────────┤
│                   Adaptation Layer (Bridge)                         │
├─────────────────────────────────────────────────────────────────────┤
│  ├── StdioBridge              │  ├── VersionDetector                │
│  ├── ProcessPool              │  ├── TerminalBridge                 │
│  ├── MessageParser            │  └── MultiModelAdapter              │
│  └── StreamingResponseParser  │                                     │
├─────────────────────────────────────────────────────────────────────┤
│                   Infrastructure Layer                              │
├─────────────────────────────────────────────────────────────────────┤
│  ├── SecureStringStorage      │  ├── Logger (4a1e级别)              │
│  ├── EventBus                 │  ├── MetricsCollector               │
│  ├── StateManager             │  └── CacheManager                   │
│  └── ErrorRecoveryManager     │                                     │
└─────────────────────────────────────────────────────────────────────┘
           ↕                                ↕
    Claude Code CLI              JetBrains Platform API
    (Node.js Process)             (PSI/VCS/RunConfig)
```

---

## 3. 详细功能需求（5大模块）

### 3.1 UI与主题系统

#### 3.1.1 主题引擎（预设主题 + 自定义主题）

**功能描述**：
提供多套预设主题和完整的自定义主题编辑器，支持亮色/暗色模式切换，满足不同用户的审美偏好。

**预设主题列表**：
1. **JetBrains Dark**（默认）：与IDEA原生暗色主题保持一致
2. **GitHub Dark**：模仿GitHub Copilot的深色主题
3. **VS Code Dark**：模仿VS Code的默认暗色主题
4. **Monokai**：经典的代码编辑器主题
5. **Solarized Light**：护眼的亮色主题
6. **Nord**：冷色调的北极主题

**自定义主题编辑器**：
- **颜色配置**：
  - 主色调（Primary Color）：影响按钮、链接、高亮
  - 背景色（Background）：聊天区域背景
  - 消息气泡色（User/AI Message Color）：区分发送方
  - 代码块背景色（Code Block Background）
  - 边框色（Border Color）
  - 文本色（Text Color）：主文本、次要文本
  
- **字体配置**：
  - 消息字体：支持系统字体选择
  - 代码字体：Monospace字体选择
  - 字号：10px - 18px可调
  
- **间距配置**：
  - 消息间距：8px - 24px
  - 代码块内边距：8px - 16px
  - 头部栏高度：40px - 60px

- **圆角配置**：
  - 消息气泡圆角：4px - 16px
  - 代码块圆角：4px - 12px

**技术实现**：
```kotlin
// 主题配置数据结构
data class ThemeConfig(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val colors: ColorScheme,
    val typography: Typography,
    val spacing: Spacing,
    val borderRadius: BorderRadius
)

data class ColorScheme(
    val primary: String,        // "#007AFF"
    val background: String,     // "#1E1E1E"
    val userMessage: String,    // "#2563EB"
    val aiMessage: String,      // "#374151"
    val codeBlock: String,      // "#111827"
    val border: String,         // "#374151"
    val textPrimary: String,    // "#F9FAFB"
    val textSecondary: String   // "#9CA3AF"
)

// 主题管理器
class ThemeManager {
    private val currentTheme = MutableStateFlow<ThemeConfig>(ThemePresets.JetBrainsDark)
    
    fun applyTheme(theme: ThemeConfig) {
        currentTheme.value = theme
        // 通知JCEF更新主题
        notifyJcefThemeChange(theme)
        // 更新Swing组件UI
        updateSwingComponents(theme)
    }
    
    fun saveCustomTheme(theme: ThemeConfig) {
        themeStorage.save(theme)
    }
}
```

#### 3.1.2 配置热更新机制（关键需求）

**功能描述**：
所有配置变更（主题、模型、Skills、MCP等）无需重启IDE，立即生效。

**热更新范围**：
1. **主题切换**：实时更新所有UI组件颜色
2. **模型切换**：立即应用到新消息
3. **Skills变更**：即时更新可用技能列表
4. **MCP服务器**：动态加载/卸载服务器连接
5. **API Key**：立即验证并应用新密钥

**技术实现**：
```kotlin
// 配置变更监听器
interface ConfigChangeListener<T> {
    fun onConfigChanged(oldValue: T, newValue: T)
}

// 配置管理器
class ConfigHotReloadManager {
    private val listeners = mutableListOf<ConfigChangeListener<*>>()
    
    fun <T> watch(configKey: String, currentValue: T, listener: (T) -> Unit) {
        val changeListener = object : ConfigChangeListener<T> {
            override fun onConfigChanged(oldValue: T, newValue: T) {
                if (oldValue != newValue) {
                    listener(newValue)
                }
            }
        }
        listeners.add(changeListener)
    }
    
    fun notifyConfigChanged(configKey: String, newValue: Any) {
        // 通知所有监听器
        listeners.forEach { listener ->
            // 类型安全的配置更新
        }
    }
}

// JCEF端热更新
// 在React组件中监听配置变更
useEffect(() => {
    const unsubscribe = window.ccBackend.onConfigChange((newConfig) => {
        if (newConfig.type === 'theme') {
            updateTheme(newConfig.value);
        } else if (newConfig.type === 'model') {
            updateModel(newConfig.value);
        }
    });
    
    return () => unsubscribe();
}, []);
```

**性能优化**：
- **防抖处理**：配置变更300ms防抖，避免频繁更新
- **增量更新**：只更新变更的配置项，而非全量替换
- **懒加载**：主题资源按需加载，减少内存占用

#### 3.1.3 响应式布局设计

**功能描述**：
根据ToolWindow大小自动调整布局，支持不同屏幕尺寸。

**布局规格**：
- **最小宽度**：600px（小于此宽度显示警告）
- **推荐宽度**：800px - 1200px
- **响应式断点**：
  - 小屏（< 800px）：单列布局，预览区隐藏
  - 中屏（800px - 1200px）：左右分栏（60%:40%）
  - 大屏（> 1200px）：左右分栏（50%:50%）

**技术实现**：
```typescript
// React响应式布局
const ChatLayout: React.FC = () => {
    const [width, setWidth] = useState(window.innerWidth);
    
    useEffect(() => {
        const resizeObserver = new ResizeObserver(entries => {
            setWidth(entries[0].contentRect.width);
        });
        
        const container = document.getElementById('chat-container');
        resizeObserver.observe(container);
        
        return () => resizeObserver.disconnect();
    }, []);
    
    const layoutMode = width < 800 ? 'single' : 'split';
    
    return (
        <div className={`chat-layout ${layoutMode}`}>
            {layoutMode === 'split' ? (
                <>
                    <ChatPanel width="60%" />
                    <PreviewPanel width="40%" />
                </>
            ) : (
                <ChatPanel width="100%" />
            )}
        </div>
    );
};
```

---

### 3.2 交互增强系统

#### 3.2.1 提示词优化器（PromptOptimizer）

**功能描述**：
用户输入提示词后，可在发送前让AI优化提示词，提高回复质量。

**优化策略**：
1. **明确化**：添加具体上下文和约束条件
2. **结构化**：将自然语言转换为结构化指令
3. **示例增强**：添加输入/输出示例
4. **角色设定**：为AI添加专业角色身份

**UI交互流程**：
1. 用户输入提示词："帮我写个排序算法"
2. 点击"优化提示词"按钮（快捷键：Ctrl+Shift+O）
3. 显示优化后的提示词：
   ```
   作为一位资深的算法工程师，请帮我实现一个高效的排序算法。
   
   要求：
   - 时间复杂度：O(n log n)
   - 空间复杂度：O(1)
   - 使用Python实现
   - 包含详细的注释和时间复杂度分析
   
   请提供：
   1. 算法实现代码
   2. 时间复杂度分析
   3. 空间复杂度分析
   4. 使用示例
   ```
4. 用户可编辑优化后的提示词
5. 点击"发送"或"取消优化"

**技术实现**：
```kotlin
class PromptOptimizer {
    suspend fun optimizePrompt(originalPrompt: String): OptimizedPrompt {
        // 调用Claude API优化提示词
        val response = claudeApi.complete(
            message = """
                请优化以下提示词，使其更清晰、具体、结构化：
                
                原始提示词：$originalPrompt
                
                请返回优化后的提示词，保持JSON格式：
                {
                    "optimizedPrompt": "优化后的提示词",
                    "improvements": ["改进点1", "改进点2"],
                    "confidence": 0.95
                }
            """.trimIndent()
        )
        
        return parseOptimizedPrompt(response)
    }
}
```

#### 3.2.2 代码快捷操作（CodeQuickActions）

**功能描述**：
选中的代码块可通过快捷操作快速发起对话或放入对话框。

**快捷操作列表**：
1. **解释代码**（Explain Code）：Ctrl+Shift+E
2. **优化代码**（Optimize Code）：Ctrl+Shift+O
3. **添加注释**（Add Comments）：Ctrl+Shift+/
4. **生成测试**（Generate Tests）：Ctrl+Shift+T
5. **重构代码**（Refactor Code）：Ctrl+Shift+R
6. **查找Bug**（Find Bugs）：Ctrl+Shift+B
7. **转换为对话**（Add to Chat）：Ctrl+Shift+C

**UI实现**：
```kotlin
class CodeQuickAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val selectedText = editor.selectionModel.selectedText ?: return
        
        when (e.actionCommand) {
            "explainCode" -> {
                val prompt = """
                    请解释以下代码的功能、逻辑和潜在问题：
                    
                    ```kotlin
                    $selectedText
                    ```
                    
                    请提供：
                    1. 代码功能概述
                    2. 关键逻辑分析
                    3. 潜在问题或改进建议
                """.trimIndent()
                
                ChatOrchestrator.getInstance(project).sendPrompt(prompt)
            }
            "addToChat" -> {
                ChatToolWindowFactory.addToChatContext(selectedText)
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.project?.let { 
            FileEditorManager.getInstance(it).selectedTextEditor 
        }
        val hasSelection = editor?.selectionModel?.selectedText?.isNotEmpty() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }
}
```

#### 3.2.3 多模态输入处理（MultimodalInputHandler）

**功能描述**：
支持图片、附件等直接放入对话上下文，增强AI理解能力。

**支持的输入类型**：
1. **图片**：
   - 截图粘贴（Ctrl+V）
   - 拖拽图片文件
   - 从剪贴板粘贴图片
   - 支持格式：PNG, JPG, JPEG, GIF, WebP
   
2. **附件**：
   - 拖拽文件到对话框
   - 点击附件按钮上传
   - 支持格式：PDF, DOCX, TXT, MD, JSON, XML, YAML
   
3. **代码文件**：
   - 拖拽代码文件直接分析
   - 支持语法高亮预览

**技术实现**：
```kotlin
class MultimodalInputHandler {
    fun handleImageDrop(imageFile: File): ContentPart {
        // 转换为Base64
        val base64Image = imageFile.readBytes().encodeBase64()
        val mimeType = Files.probeContentType(imageFile.toPath())
        
        return ContentPart.Image(
            mimeType = mimeType,
            data = base64Image
        )
    }
    
    fun handleAttachment(file: File): ContentPart {
        val content = when (file.extension.lowercase()) {
            "pdf" -> extractPdfContent(file)
            "docx" -> extractDocxContent(file)
            "txt", "md", "json", "xml", "yaml" -> file.readText()
            else -> throw UnsupportedFileTypeException(file.extension)
        }
        
        return ContentPart.Text(
            text = "[文件: ${file.name}]\n$content"
        )
    }
    
    fun buildMultimodalMessage(
        text: String,
        images: List<ContentPart.Image>,
        attachments: List<ContentPart.Text>
    ): MultimodalMessage {
        return MultimodalMessage(
            text = text,
            images = images,
            attachments = attachments
        )
    }
}
```

**JCEF前端实现**：
```typescript
// 拖拽处理
const ChatInput: React.FC = () => {
    const handleDrop = async (e: React.DragEvent) => {
        e.preventDefault();
        
        const files = Array.from(e.dataTransfer.files);
        const contentParts: ContentPart[] = [];
        
        for (const file of files) {
            if (file.type.startsWith('image/')) {
                const base64 = await fileToBase64(file);
                contentParts.push({
                    type: 'image',
                    mimeType: file.type,
                    data: base64
                });
            } else {
                const content = await window.ccBackend.readFile(file.path);
                contentParts.push({
                    type: 'text',
                    text: `[文件: ${file.name}]\n${content}`
                });
            }
        }
        
        setAttachments(prev => [...prev, ...contentParts]);
    };
    
    return (
        <div 
            className="chat-input-container"
            onDrop={handleDrop}
            onDragOver={(e) => e.preventDefault()}
        >
            {/* 输入框和附件预览 */}
        </div>
    );
};
```

#### 3.2.4 对话引用系统（ConversationReferenceSystem）

**功能描述**：
可引用历史消息中的内容，方便上下文延续。

**引用方式**：
1. **右键菜单引用**：右键点击历史消息 → "引用此消息"
2. **拖拽引用**：拖拽历史消息到输入框
3. **快捷键引用**：选中历史消息后按 Ctrl+Shift+Q
4. **手动引用**：输入 `@消息ID` 或 `@消息摘要`

**引用显示**：
- 引用的消息以特殊样式显示（灰色背景、左侧竖线）
- 显示引用消息的时间戳和发送者
- 点击引用可跳转到原消息

**技术实现**：
```kotlin
data class MessageReference(
    val messageId: String,
    val excerpt: String,
    val timestamp: Long,
    val sender: MessageSender
)

class ConversationReferenceSystem {
    fun createReference(message: ChatMessage): MessageReference {
        return MessageReference(
            messageId = message.id,
            excerpt = message.content.take(100) + "...",
            timestamp = message.timestamp,
            sender = message.sender
        )
    }
    
    fun buildPromptWithReferences(
        userPrompt: String,
        references: List<MessageReference>
    ): String {
        val referenceText = references.joinToString("\n") { ref ->
            """
            [@${ref.messageId}] (${formatTimestamp(ref.timestamp)}):
            ${ref.excerpt}
            """.trimIndent()
        }
        
        return """
            $referenceText
            
            ---
            
            用户问题：$userPrompt
        """.trimIndent()
    }
}
```

#### 3.2.5 交互式请求引擎（InteractiveRequestEngine）⭐核心

**功能描述**：
AI处理过程中遇到不明确的点时，可主动向用户提问，根据用户回答动态调整方案。

**应用场景**：
1. **代码生成场景**：
   - AI："您希望使用什么框架？Spring Boot / Quarkus / Micronaut"
   - 用户选择后，AI根据框架生成相应代码
   
2. **重构场景**：
   - AI："检测到3种重构方案，性能/可读性/兼容性，您优先考虑哪个？"
   - 用户选择"性能"，AI生成优化后的代码
   
3. **Bug修复场景**：
   - AI："发现2个潜在Bug，建议优先修复哪个？"
   - 用户选择Bug #1，AI提供修复方案

**技术实现**：
```kotlin
// 交互式请求数据结构
data class InteractiveQuestion(
    val questionId: String,
    val question: String,
    val questionType: QuestionType,
    val options: List<QuestionOption>? = null,
    val allowMultiple: Boolean = false,
    val context: Map<String, Any>
)

enum class QuestionType {
    SINGLE_CHOICE,    // 单选题
    MULTIPLE_CHOICE,  // 多选题
    TEXT_INPUT,       // 文本输入
    CONFIRMATION,     // 确认（是/否）
    CODE_REVIEW       // 代码审查选择
}

data class QuestionOption(
    val id: String,
    val label: String,
    val description: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

// 交互式请求引擎
class InteractiveRequestEngine(
    private val chatOrchestrator: ChatOrchestrator
) {
    private val pendingQuestions = ConcurrentHashMap<String, InteractiveQuestion>()
    
    suspend fun handleAIResponse(response: String): ProcessResult {
        // 检测AI是否需要向用户提问
        val questions = extractQuestions(response)
        
        if (questions.isNotEmpty()) {
            // 暂停当前处理，向用户展示问题
            questions.forEach { question ->
                pendingQuestions[question.questionId] = question
                showQuestionToUser(question)
            }
            
            return ProcessResult.WaitingForUserInput(questions.map { it.questionId })
        } else {
            // 正常处理
            return ProcessResult.Completed(response)
        }
    }
    
    private fun extractQuestions(response: String): List<InteractiveQuestion> {
        // 解析AI响应中的问题
        // 可以通过特殊标记或自然语言处理
        return listOf(
            InteractiveQuestion(
                questionId = "framework-choice",
                question = "您希望使用什么框架？",
                questionType = QuestionType.SINGLE_CHOICE,
                options = listOf(
                    QuestionOption("spring", "Spring Boot", "成熟的Java企业级框架"),
                    QuestionOption("quarkus", "Quarkus", "云原生Kotlin框架"),
                    QuestionOption("micronaut", "Micronaut", "低内存占用框架")
                )
            )
        )
    }
    
    suspend fun submitAnswer(questionId: String, answer: Any) {
        val question = pendingQuestions.remove(questionId) ?: return
        
        // 将用户答案添加到对话上下文
        val contextUpdate = buildContextUpdate(question, answer)
        chatOrchestrator.updateContext(contextUpdate)
        
        // 继续处理
        chatOrchestrator.resumeProcessing()
    }
    
    private fun buildContextUpdate(
        question: InteractiveQuestion,
        answer: Any
    ): ContextUpdate {
        return when (question.questionType) {
            QuestionType.SINGLE_CHOICE -> {
                val option = question.options?.find { it.id == answer }
                ContextUpdate(
                    type = "question_answer",
                    data = mapOf(
                        "question" to question.question,
                        "answer" to (option?.label ?: answer)
                    )
                )
            }
            // 其他类型...
        }
    }
}
```

**前端实现（React）**：
```typescript
const InteractiveQuestionPanel: React.FC<{
    question: InteractiveQuestion;
    onAnswer: (answer: any) => void;
}> = ({ question, onAnswer }) => {
    const [selectedAnswer, setSelectedAnswer] = useState<any>(null);
    
    const handleSubmit = () => {
        onAnswer(selectedAnswer);
    };
    
    return (
        <div className="interactive-question">
            <div className="question-header">
                <Icon name="help-circle" />
                <span className="question-text">{question.question}</span>
            </div>
            
            {question.questionType === 'SINGLE_CHOICE' && (
                <div className="options-list">
                    {question.options?.map(option => (
                        <label key={option.id} className="option-item">
                            <input
                                type="radio"
                                name={question.questionId}
                                value={option.id}
                                onChange={(e) => setSelectedAnswer(e.target.value)}
                            />
                            <div className="option-content">
                                <div className="option-label">{option.label}</div>
                                {option.description && (
                                    <div className="option-description">
                                        {option.description}
                                    </div>
                                )}
                            </div>
                        </label>
                    ))}
                </div>
            )}
            
            <div className="question-actions">
                <button onClick={handleSubmit} disabled={!selectedAnswer}>
                    确认选择
                </button>
                <button onClick={() => onAnswer(null)}>
                    跳过
                </button>
            </div>
        </div>
    );
};
```

---

### 3.3 会话管理系统

#### 3.3.1 多会话管理器（MultiSessionManager）

**功能描述**：
支持同时管理多个独立会话，每个会话保持独立的上下文和状态。

**会话类型**：
1. **项目会话**（Project Session）：
   - 绑定到当前项目
   - 自动包含项目上下文
   - 项目关闭时会话暂停
   
2. **全局会话**（Global Session）：
   - 跨项目共享
   - 适合通用性问题
   - 手动管理生命周期

3. **临时会话**（Temporary Session）：
   - 快速问答场景
   - 不保存历史记录
   - 关闭后自动删除

**会话管理功能**：
- **新建会话**：点击"+"按钮创建新会话
- **会话切换**：Tab标签页切换
- **会话重命名**：双击Tab标题编辑
- **会话删除**：右键菜单删除
- **会话导出**：导出为Markdown/PDF
- **会话搜索**：搜索所有会话内容

**技术实现**：
```kotlin
data class ChatSession(
    val id: String,
    val name: String,
    val type: SessionType,
    val projectId: String? = null,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val context: SessionContext = SessionContext(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

enum class SessionType {
    PROJECT,
    GLOBAL,
    TEMPORARY
}

data class SessionContext(
    val modelConfig: ModelConfig,
    val enabledSkills: List<String> = emptyList(),
    val enabledMcpServers: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

class MultiSessionManager {
    private val sessions = ConcurrentHashMap<String, ChatSession>()
    private val currentSessionId = AtomicReference<String>()
    
    fun createSession(
        name: String,
        type: SessionType,
        projectId: String? = null
    ): ChatSession {
        val session = ChatSession(
            id = generateSessionId(),
            name = name,
            type = type,
            projectId = projectId
        )
        
        sessions[session.id] = session
        switchToSession(session.id)
        
        return session
    }
    
    fun switchToSession(sessionId: String) {
        val session = sessions[sessionId] ?: throw SessionNotFoundException(sessionId)
        
        // 保存当前会话状态
        getCurrentSession()?.isActive = false
        
        // 切换到新会话
        session.isActive = true
        currentSessionId.set(sessionId)
        
        // 通知UI更新
        EventBus.publish(SessionChangedEvent(sessionId))
    }
    
    fun deleteSession(sessionId: String) {
        sessions.remove(sessionId)
        
        // 如果删除的是当前会话，切换到其他会话
        if (currentSessionId.get() == sessionId) {
            val firstSession = sessions.values.firstOrNull()
            firstSession?.let { switchToSession(it.id) }
        }
    }
    
    fun searchSessions(query: String): List<ChatSession> {
        return sessions.values.filter { session ->
            session.name.contains(query, ignoreCase = true) ||
            session.messages.any { 
                it.content.contains(query, ignoreCase = true) 
            }
        }
    }
}
```

#### 3.3.2 流式输出引擎（StreamingOutputEngine）

**功能描述**：
实时显示AI回复，支持打字机效果，提升用户体验。

**流式输出特性**：
1. **SSE/Stream支持**：基于Server-Sent Events的实时推送
2. **打字机效果**：逐字显示，可调节速度
3. **Markdown实时渲染**：边接收边渲染Markdown
4. **代码块高亮**：代码块实时语法高亮
5. **可打断**：用户可随时停止生成

**技术实现**：
```kotlin
class StreamingOutputEngine(
    private val browser: JBCefBrowser
) {
    private val jsQuery = JBCefJSQuery.create(browser)
    
    fun startStreaming(sessionId: String, messageId: String) {
        // 注册流式输出端点
        jsQuery.addHandler { messageData ->
            val data = Json.parseToJsonElement(messageData).jsonObject
            when (data["type"]?.jsonPrimitive?.content) {
                "chunk" -> {
                    // 接收文本块
                    val chunk = data["content"]?.jsonPrimitive?.content ?: ""
                    appendToMessage(messageId, chunk)
                }
                "done" -> {
                    // 流式输出结束
                    finalizeMessage(messageId)
                }
                "error" -> {
                    // 错误处理
                    handleError(messageId, data["error"]?.jsonPrimitive?.content)
                }
            }
            null
        }
        
        // 注入JS端点
        browser.cefClient.executeJavaScript(
            """
            window.ccStreaming = {
                onChunk: (chunk) => $jsQuery.invoke(JSON.stringify({
                    type: 'chunk',
                    content: chunk
                })),
                onDone: () => $jsQuery.invoke(JSON.stringify({ type: 'done' })),
                onError: (error) => $jsQuery.invoke(JSON.stringify({
                    type: 'error',
                    error: error
                }))
            };
            """.trimIndent(),
            null, 0
        )
    }
    
    private fun appendToMessage(messageId: String, chunk: String) {
        // 通知前端追加内容
        browser.cefClient.executeJavaScript(
            """
            window.chatApp.appendMessageChunk('$messageId', ${escapeJs(chunk)});
            """.trimIndent(),
            null, 0
        )
    }
    
    fun stopStreaming(messageId: String) {
        browser.cefClient.executeJavaScript(
            """
            window.chatApp.stopStreaming('$messageId');
            """.trimIndent(),
            null, 0
        )
    }
}
```

**前端实现**：
```typescript
const StreamingMessage: React.FC<{
    messageId: string;
    onComplete: () => void;
}> = ({ messageId, onComplete }) => {
    const [content, setContent] = useState('');
    const [isStreaming, setIsStreaming] = useState(true);
    const streamRef = useRef<any>(null);
    
    useEffect(() => {
        // 注册流式输出回调
        streamRef.current = window.ccStreaming;
        
        return () => {
            if (isStreaming) {
                streamRef.current?.stopStreaming(messageId);
            }
        };
    }, [messageId]);
    
    const appendChunk = (chunk: string) => {
        setContent(prev => prev + chunk);
    };
    
    return (
        <div className={`message ${isStreaming ? 'streaming' : ''}`}>
            <MarkdownRenderer content={content} />
            {isStreaming && <CursorBlink />}
        </div>
    );
};
```

#### 3.3.3 会话中断与恢复（SessionInterruptRecovery）

**功能描述**：
用户可随时停止正在进行的会话，并支持恢复中断的对话。

**中断场景**：
1. **用户主动停止**：点击"停止生成"按钮
2. **网络中断**：连接超时或断网
3. **进程崩溃**：CLI进程异常退出
4. **IDE关闭**：IDE意外关闭后恢复

**恢复策略**：
1. **保存上下文**：定期保存会话状态
2. **断点续传**：从中断点继续对话
3. **错误重试**：自动重试失败请求
4. **状态恢复**：IDE重启后恢复会话

**技术实现**：
```kotlin
class SessionInterruptRecovery {
    private val checkpointInterval = 5000L // 5秒保存一次检查点
    private val checkpoints = ConcurrentHashMap<String, SessionCheckpoint>()
    
    fun startCheckpointing(sessionId: String) {
        coroutineScope.launch {
            while (isActive) {
                delay(checkpointInterval)
                saveCheckpoint(sessionId)
            }
        }
    }
    
    private fun saveCheckpoint(sessionId: String) {
        val session = sessionManager.getSession(sessionId)
        checkpoints[sessionId] = SessionCheckpoint(
            sessionId = sessionId,
            messages = session.messages.toList(),
            context = session.context,
            timestamp = System.currentTimeMillis()
        )
        
        // 持久化到磁盘
        checkpointStorage.save(checkpoints[sessionId]!!)
    }
    
    fun recoverSession(sessionId: String): RecoveryResult {
        val checkpoint = checkpoints[sessionId] 
            ?: checkpointStorage.load(sessionId)
        
        return if (checkpoint != null) {
            // 恢复会话状态
            val session = sessionManager.getSession(sessionId)
            session.messages.clear()
            session.messages.addAll(checkpoint.messages)
            session.context = checkpoint.context
            
            RecoveryResult.Success(checkpoint)
        } else {
            RecoveryResult.NoCheckpointFound
        }
    }
}

enum class RecoveryResult {
    Success(SessionCheckpoint),
    NoCheckpointFound,
    CorruptedCheckpoint
}
```

#### 3.3.4 历史会话检索（HistorySessionSearch）

**功能描述**：
快速搜索和过滤历史会话，支持全文检索。

**搜索功能**：
1. **全文搜索**：搜索所有会话内容
2. **日期过滤**：按日期范围筛选
3. **类型过滤**：按会话类型筛选
4. **标签过滤**：按自定义标签筛选
5. **智能推荐**：推荐相关历史会话

**技术实现**：
```kotlin
class HistorySessionSearch(
    private val sessionManager: MultiSessionManager
) {
    private val searchIndex = LuceneIndex()
    
    fun indexSession(session: ChatSession) {
        session.messages.forEach { message ->
            searchIndex.addDocument(
                Document(
                    id = "${session.id}_${message.id}",
                    content = message.content,
                    sessionId = session.id,
                    timestamp = message.timestamp,
                    metadata = mapOf(
                        "sender" to message.sender.name,
                        "type" to session.type.name
                    )
                )
            )
        }
    }
    
    fun search(
        query: String,
        filters: SearchFilters = SearchFilters()
    ): List<SearchResult> {
        val luceneQuery = buildQuery(query, filters)
        val results = searchIndex.search(luceneQuery)
        
        return results.map { doc ->
            SearchResult(
                sessionId = doc.get("sessionId"),
                messageId = doc.get("id").substringAfter('_'),
                excerpt = extractExcerpt(doc.get("content"), query),
                score = doc.score,
                timestamp = doc.get("timestamp").toLong()
            )
        }
    }
    
    private fun buildQuery(query: String, filters: SearchFilters): BooleanQuery {
        val booleanQuery = BooleanQuery()
        
        // 全文搜索
        booleanQuery.add(BooleanClause(
            TermQuery("content", query),
            BooleanClause.Occur.SHOULD
        ))
        
        // 过滤器
        if (filters.sessionType != null) {
            booleanQuery.add(BooleanClause(
                TermQuery("type", filters.sessionType.name),
                BooleanClause.Occur.MUST
            ))
        }
        
        if (filters.dateRange != null) {
            booleanQuery.add(BooleanClause(
                RangeQuery(
                    "timestamp",
                    filters.dateRange.start,
                    filters.dateRange.end
                ),
                BooleanClause.Occur.MUST
            ))
        }
        
        return booleanQuery
    }
}
```

#### 3.3.5 任务进度可视化（TaskProgressVisualization）

**功能描述**：
显示AI任务分解和执行进度，让用户了解AI的工作状态。

**进度显示类型**：
1. **任务分解**：显示AI将任务分解为几个子任务
2. **执行进度**：显示当前执行到第几个任务
3. **步骤详情**：显示每个步骤的详细状态
4. **预估时间**：预估剩余时间

**UI设计**：
```
┌─────────────────────────────────────────────┐
│  任务进度 (3/5)                              │
│  █████████████████████░░░░░░░░░             │
│                                              │
│  ✓ 步骤1: 分析代码结构        (已完成)       │
│  ✓ 步骤2: 识别性能瓶颈        (已完成)       │
│  → 步骤3: 优化算法实现        (进行中)       │
│  ⏳ 步骤4: 添加单元测试        (等待中)       │
│  ⏳ 步骤5: 生成文档            (等待中)       │
│                                              │
│  预计剩余时间: 2分30秒                       │
└─────────────────────────────────────────────┘
```

**技术实现**：
```kotlin
data class TaskProgress(
    val taskId: String,
    val totalSteps: Int,
    val currentStep: Int,
    val steps: List<TaskStep>,
    val estimatedTimeRemaining: Long? = null
)

data class TaskStep(
    val id: String,
    val name: String,
    val description: String,
    val status: StepStatus,
    val output: String? = null,
    val error: String? = null
)

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

class TaskProgressTracker {
    private val activeTasks = ConcurrentHashMap<String, TaskProgress>()
    
    fun startTask(taskId: String, steps: List<TaskStep>) {
        activeTasks[taskId] = TaskProgress(
            taskId = taskId,
            totalSteps = steps.size,
            currentStep = 0,
            steps = steps
        )
        
        notifyProgressUpdate(activeTasks[taskId]!!)
    }
    
    fun updateStep(
        taskId: String,
        stepId: String,
        status: StepStatus,
        output: String? = null
    ) {
        val task = activeTasks[taskId] ?: return
        
        val stepIndex = task.steps.indexOfFirst { it.id == stepId }
        if (stepIndex >= 0) {
            task.steps[stepIndex] = task.steps[stepIndex].copy(
                status = status,
                output = output
            )
            
            if (status == StepStatus.COMPLETED) {
                task.currentStep = stepIndex + 1
            }
            
            notifyProgressUpdate(task)
        }
    }
    
    private fun notifyProgressUpdate(progress: TaskProgress) {
        // 通知JCEF更新进度条
        browser.cefClient.executeJavaScript(
            """
            window.chatApp.updateTaskProgress(${Json.encodeToString(progress)});
            """.trimIndent(),
            null, 0
        )
    }
}
```

**解析AI的任务分解信息**：
```kotlin
class TaskParser {
    fun parseTaskDecomposition(response: String): List<TaskStep> {
        // 解析AI响应中的任务分解
        // 可以通过特殊标记或结构化输出
        val steps = mutableListOf<TaskStep>()
        
        // 方法1: 解析特殊标记
        val taskPattern = Regex("""\[Task (\d+)[:\s]+([^\]]+)\]""")
        taskPattern.findAll(response).forEach { match ->
            steps.add(TaskStep(
                id = match.groupValues[1],
                name = match.groupValues[2],
                description = "",
                status = StepStatus.PENDING
            ))
        }
        
        // 方法2: 解析JSON结构
        if (response.contains("\"tasks\":")) {
            val json = Json.parseToJsonElement(response)
            val tasksArray = json.jsonObject["tasks"]?.jsonArray
            tasksArray?.forEach { taskJson ->
                val task = taskJson.jsonObject
                steps.add(TaskStep(
                    id = task["id"]?.jsonPrimitive?.content ?: "",
                    name = task["name"]?.jsonPrimitive?.content ?: "",
                    description = task["description"]?.jsonPrimitive?.content ?: "",
                    status = StepStatus.PENDING
                ))
            }
        }
        
        return steps
    }
}
```

---

### 3.4 模型配置系统

#### 3.4.1 多供应商适配器（MultiProviderAdapter）

**功能描述**：
支持多个AI供应商，快捷切换不同供应商的模型。

**支持的供应商**：
1. **Anthropic**：Claude 3.5 Sonnet, Claude 3 Opus, Claude 3 Haiku
2. **OpenAI**：GPT-4, GPT-4 Turbo, GPT-3.5 Turbo
3. **DeepSeek**：DeepSeek-V3, DeepSeek-Coder
4. **Google**：Gemini Pro, Gemini Ultra
5. **Meta**：Llama 3, Llama 2
6. **自定义**：支持OpenAI兼容的自定义端点

**技术实现**：
```kotlin
interface AIProvider {
    val name: String
    val availableModels: List<String>
    suspend fun chat(request: ChatRequest): ChatResponse
    suspend fun streamChat(request: ChatRequest): Flow<String>
}

class AnthropicProvider(
    private val apiKey: String
) : AIProvider {
    override val name = "Anthropic"
    override val availableModels = listOf(
        "claude-3-5-sonnet-20241022",
        "claude-3-opus-20240229",
        "claude-3-haiku-20240307"
    )
    
    override suspend fun chat(request: ChatRequest): ChatResponse {
        val httpClient = HttpClient(CIO)
        val response = httpClient.post("https://api.anthropic.com/v1/messages") {
            headers {
                append("x-api-key", apiKey)
                append("anthropic-version", "2023-06-01")
                append("content-type", "application/json")
            }
            setBody(
                buildJsonObject {
                    put("model", request.model)
                    put("max_tokens", request.maxTokens)
                    put("messages", buildJsonArray {
                        request.messages.forEach { message ->
                            add(buildJsonObject {
                                put("role", message.role)
                                put("content", message.content)
                            })
                        }
                    })
                }
            )
        }
        
        return parseAnthropicResponse(response)
    }
    
    override suspend fun streamChat(request: ChatRequest): Flow<String> = flow {
        val httpClient = HttpClient(CIO)
        val response = httpClient.post("https://api.anthropic.com/v1/messages") {
            // ... 设置SSE请求
        }
        
        response.bodyAsChannel().parseSSE().collect { event ->
            when (event.type) {
                "content_block_delta" -> {
                    emit(event.data["delta"]?.get("text")?.asString() ?: "")
                }
            }
        }
    }
}

class OpenAIProvider(
    private val apiKey: String
) : AIProvider {
    override val name = "OpenAI"
    override val availableModels = listOf(
        "gpt-4",
        "gpt-4-turbo",
        "gpt-3.5-turbo"
    )
    
    override suspend fun chat(request: ChatRequest): ChatResponse {
        // 实现OpenAI API调用
    }
    
    override suspend fun streamChat(request: ChatRequest): Flow<String> {
        // 实现OpenAI流式调用
    }
}

class MultiProviderAdapter {
    private val providers = ConcurrentHashMap<String, AIProvider>()
    
    fun registerProvider(provider: AIProvider) {
        providers[provider.name] = provider
    }
    
    suspend fun chat(
        providerName: String,
        request: ChatRequest
    ): ChatResponse {
        val provider = providers[providerName]
            ?: throw ProviderNotFoundException(providerName)
        
        return provider.chat(request)
    }
    
    suspend fun streamChat(
        providerName: String,
        request: ChatRequest
    ): Flow<String> {
        val provider = providers[providerName]
            ?: throw ProviderNotFoundException(providerName)
        
        return provider.streamChat(request)
    }
}
```

#### 3.4.2 模型切换器（ModelSwitcher）

**功能描述**：
快捷切换不同模型，无需重启IDE。

**切换方式**：
1. **快捷切换**：底部状态栏模型选择器
2. **会话级切换**：每个会话独立选择模型
3. **智能推荐**：根据任务类型推荐最佳模型

**技术实现**：
```kotlin
class ModelSwitcher(
    private val providerAdapter: MultiProviderAdapter,
    private val configManager: ConfigManager
) {
    fun switchModel(
        sessionId: String,
        providerName: String,
        modelName: String
    ) {
        val session = sessionManager.getSession(sessionId)
        
        // 验证模型是否可用
        val provider = providerAdapter.getProvider(providerName)
        if (modelName !in provider.availableModels) {
            throw ModelNotAvailableException(modelName, providerName)
        }
        
        // 更新会话配置
        session.context.modelConfig = ModelConfig(
            provider = providerName,
            model = modelName
        )
        
        // 通知UI更新
        notifyModelChanged(sessionId, providerName, modelName)
        
        // 保存配置
        configManager.saveSessionConfig(sessionId, session.context)
    }
    
    fun getRecommendedModel(taskType: TaskType): ModelRecommendation {
        return when (taskType) {
            TaskType.CODE_GENERATION -> ModelRecommendation(
                provider = "Anthropic",
                model = "claude-3-5-sonnet-20241022",
                reason = "Claude 3.5 Sonnet在代码生成任务中表现最佳"
            )
            TaskType.CODE_REVIEW -> ModelRecommendation(
                provider = "Anthropic",
                model = "claude-3-opus-20240229",
                reason = "Claude 3 Opus在代码审查中更细致"
            )
            TaskType.DOCUMENTATION -> ModelRecommendation(
                provider = "OpenAI",
                model = "gpt-4-turbo",
                reason = "GPT-4 Turbo在文档生成方面更流畅"
            )
        }
    }
}
```

#### 3.4.3 对话模式管理器（ConversationModeManager）

**功能描述**：
支持不同对话模式，对应Claude Code的不同场景。

**对话模式**：
1. **Thinking模式**：
   - 深度思考，逐步推理
   - 适合复杂问题分析
   - 显示思考过程
   
2. **Plan模式**：
   - 先规划后执行
   - 显示任务分解
   - 适合大型任务
   
3. **Auto模式**：
   - 快速响应，直接执行
   - 适合简单问答
   - 默认模式

**技术实现**：
```kotlin
enum class ConversationMode {
    THINKING,
    PLANNING,
    AUTO
}

data class ConversationModeConfig(
    val mode: ConversationMode,
    val showThinking: Boolean = true,
    val showPlan: Boolean = true,
    val autoExecute: Boolean = false
)

class ConversationModeManager {
    private val currentMode = AtomicReference(ConversationMode.AUTO)
    
    fun setMode(mode: ConversationMode, sessionId: String) {
        currentMode.set(mode)
        
        val config = when (mode) {
            ConversationMode.THINKING -> ConversationModeConfig(
                mode = mode,
                showThinking = true,
                showPlan = false,
                autoExecute = false
            )
            ConversationMode.PLANNING -> ConversationModeConfig(
                mode = mode,
                showThinking = false,
                showPlan = true,
                autoExecute = false
            )
            ConversationMode.AUTO -> ConversationModeConfig(
                mode = mode,
                showThinking = false,
                showPlan = false,
                autoExecute = true
            )
        }
        
        // 更新会话配置
        val session = sessionManager.getSession(sessionId)
        session.context.modeConfig = config
        
        // 通知UI更新
        notifyModeChanged(sessionId, mode)
    }
    
    fun buildPromptWithMode(
        originalPrompt: String,
        mode: ConversationMode
    ): String {
        return when (mode) {
            ConversationMode.THINKING -> """
                请深入思考以下问题，逐步分析：
                
                $originalPrompt
                
                请按以下格式回答：
                1. 问题分析
                2. 思考过程
                3. 解决方案
                4. 结论
            """.trimIndent()
            
            ConversationMode.PLANNING -> """
                请为以下任务制定详细计划：
                
                $originalPrompt
                
                请按以下格式回答：
                1. 任务理解
                2. 任务分解（步骤列表）
                3. 执行计划
                4. 风险评估
            """.trimIndent()
            
            ConversationMode.AUTO -> originalPrompt
        }
    }
}
```

---

### 3.5 Claude Code生态集成

#### 3.5.1 Skills管理器（SkillsManager）

**功能描述**：
可视化管理Prompt技能模板，支持创建、编辑、删除、导入、导出。

**Skill数据结构**：
```kotlin
data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val category: SkillCategory,
    val prompt: String,
    val variables: List<SkillVariable> = emptyList(),
    val shortcut: String? = null,
    val enabled: Boolean = true,
    val scope: SkillScope = SkillScope.GLOBAL
)

enum class SkillCategory {
    CODE_GENERATION,
    CODE_REVIEW,
    REFACTORING,
    TESTING,
    DOCUMENTATION,
    DEBUGGING,
    PERFORMANCE
}

enum class SkillScope {
    GLOBAL,
    PROJECT
}

data class SkillVariable(
    val name: String,
    val type: VariableType,
    val defaultValue: Any? = null,
    val required: Boolean = true,
    val options: List<Any>? = null
)

enum class VariableType {
    TEXT,
    NUMBER,
    ENUM,
    BOOLEAN,
    CODE
}
```

**Skills管理UI**：
```kotlin
class SkillsManagementPanel : JPanel() {
    private val skillsList = JBList<Skill>()
    private val skillEditor = SkillEditorPanel()
    private val previewPanel = SkillPreviewPanel()
    
    fun createNewSkill() {
        val skill = Skill(
            id = UUID.randomUUID().toString(),
            name = "New Skill",
            description = "",
            icon = "⚡",
            category = SkillCategory.CODE_GENERATION,
            prompt = "",
            enabled = true
        )
        
        skillsList.addSkill(skill)
        skillEditor.editSkill(skill)
    }
    
    fun editSkill(skill: Skill) {
        skillEditor.editSkill(skill)
        previewPanel.previewSkill(skill)
    }
    
    fun deleteSkill(skill: Skill) {
        skillsList.removeSkill(skill)
        skillsManager.deleteSkill(skill.id)
    }
    
    fun exportSkill(skill: Skill): File {
        val json = Json.encodeToString(skill)
        val file = File("${skill.name}.json")
        file.writeText(json)
        return file
    }
    
    fun importSkill(file: File) {
        val json = file.readText()
        val skill = Json.decodeFromString<Skill>(json)
        skillsManager.saveSkill(skill)
        skillsList.addSkill(skill)
    }
}
```

**Skill执行**：
```kotlin
class SkillExecutor {
    suspend fun executeSkill(
        skill: Skill,
        context: ExecutionContext
    ): SkillResult {
        // 解析变量
        val resolvedPrompt = resolveVariables(skill.prompt, skill.variables, context)
        
        // 构建消息
        val message = ChatMessage(
            role = "user",
            content = resolvedPrompt,
            attachments = context.attachments
        )
        
        // 发送到AI
        val response = chatOrchestrator.sendMessage(message)
        
        return SkillResult(
            skillId = skill.id,
            response = response,
            executionTime = response.executionTime,
            tokensUsed = response.tokensUsed
        )
    }
    
    private fun resolveVariables(
        prompt: String,
        variables: List<SkillVariable>,
        context: ExecutionContext
    ): String {
        var resolved = prompt
        
        variables.forEach { variable ->
            val value = context.getVariableValue(variable.name)
                ?: variable.defaultValue
                ?: if (variable.required) {
                    throw VariableNotProvidedException(variable.name)
                } else {
                    ""
                }
            
            resolved = resolved.replace("{{${variable.name}}}", value.toString())
        }
        
        return resolved
    }
}
```

#### 3.5.2 Agents管理器（AgentsManager）

**功能描述**：
管理AI Agent配置，定义Agent行为模式和能力范围。

**Agent数据结构**：
```kotlin
data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val avatar: String,
    val systemPrompt: String,
    val capabilities: List<AgentCapability>,
    val constraints: List<AgentConstraint> = emptyList(),
    val tools: List<String> = emptyList(),
    val mode: AgentMode = AgentMode.BALANCED,
    val scope: AgentScope = AgentScope.PROJECT
)

enum class AgentCapability {
    CODE_GENERATION,
    CODE_REVIEW,
    REFACTORING,
    TESTING,
    DOCUMENTATION,
    DEBUGGING,
    FILE_OPERATION,
    TERMINAL_OPERATION
}

data class AgentConstraint(
    val type: ConstraintType,
    val description: String,
    val parameters: Map<String, Any> = emptyMap()
)

enum class ConstraintType {
    MAX_TOKENS,
    ALLOWED_FILE_TYPES,
    FORBIDDEN_PATTERNS,
    RESOURCE_LIMITS
}

enum class AgentMode {
    CAUTIOUS,    // 仅提供建议
    BALANCED,    // 建议为主，低风险自动执行
    AGGRESSIVE   // 自动执行高风险操作
}

enum class AgentScope {
    GLOBAL,
    PROJECT,
    SESSION
}
```

**Agent执行引擎**：
```kotlin
class AgentExecutor {
    suspend fun executeAgent(
        agent: Agent,
        task: AgentTask,
        context: ExecutionContext
    ): AgentResult {
        // 检查Agent能力
        if (!agent.capabilities.contains(task.requiredCapability)) {
            throw AgentNotCapableException(agent.id, task.requiredCapability)
        }
        
        // 应用约束
        applyConstraints(agent.constraints, task, context)
        
        // 构建系统提示
        val systemPrompt = buildSystemPrompt(agent, task)
        
        // 执行任务
        val result = when (agent.mode) {
            AgentMode.CAUTIOUS -> executeCautiously(agent, task, systemPrompt, context)
            AgentMode.BALANCED -> executeBalanced(agent, task, systemPrompt, context)
            AgentMode.AGGRESSIVE -> executeAggressively(agent, task, systemPrompt, context)
        }
        
        return result
    }
    
    private suspend fun executeBalanced(
        agent: Agent,
        task: AgentTask,
        systemPrompt: String,
        context: ExecutionContext
    ): AgentResult {
        // 先提供建议
        val suggestion = chatOrchestrator.sendMessage(
            ChatMessage(
                role = "system",
                content = systemPrompt
            ),
            ChatMessage(
                role = "user",
                content = task.description
            )
        )
        
        // 低风险操作自动执行
        val actions = parseActions(suggestion.content)
        val safeActions = actions.filter { isSafeAction(it) }
        
        safeActions.forEach { action ->
            executeAction(action, context)
        }
        
        // 高风险操作需要用户确认
        val riskyActions = actions.filter { !isSafeAction(it) }
        if (riskyActions.isNotEmpty()) {
            requestUserConfirmation(riskyActions, context)
        }
        
        return AgentResult(
            agentId = agent.id,
            suggestion = suggestion,
            executedActions = safeActions,
            pendingActions = riskyActions
        )
    }
}
```

#### 3.5.3 MCP服务器管理器（McpServerManager）

**功能描述**：
配置和管理MCP（Model Context Protocol）服务器连接。

**MCP服务器数据结构**：
```kotlin
data class McpServer(
    val id: String,
    val name: String,
    val description: String,
    val command: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
    val status: McpServerStatus = McpServerStatus.DISCONNECTED,
    val capabilities: List<String> = emptyList(),
    val scope: McpScope = McpScope.PROJECT
)

enum class McpServerStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR,
    CONNECTING
}

enum class McpScope {
    GLOBAL,
    PROJECT
}
```

**MCP连接管理**：
```kotlin
class McpServerManager {
    private val servers = ConcurrentHashMap<String, McpServer>()
    private val connections = ConcurrentHashMap<String, Process>()
    
    suspend fun startServer(serverId: String) {
        val server = servers[serverId] ?: throw McpServerNotFoundException(serverId)
        
        // 更新状态
        server.status = McpServerStatus.CONNECTING
        notifyServerStatusChanged(server)
        
        try {
            // 启动进程
            val processBuilder = ProcessBuilder(server.command)
                .apply {
                    command(*server.args.toTypedArray())
                    environment().putAll(server.env)
                }
            
            val process = processBuilder.start()
            connections[serverId] = process
            
            // 等待连接确认
            waitForConnection(serverId, timeout = 30_000)
            
            // 更新状态
            server.status = McpServerStatus.CONNECTED
            notifyServerStatusChanged(server)
            
        } catch (e: Exception) {
            server.status = McpServerStatus.ERROR
            notifyServerStatusChanged(server)
            throw McpServerStartException(serverId, e)
        }
    }
    
    suspend fun stopServer(serverId: String) {
        val process = connections.remove(serverId) ?: return
        
        process.destroy()
        
        val server = servers[serverId]
        server?.status = McpServerStatus.DISCONNECTED
        server?.let { notifyServerStatusChanged(it) }
    }
    
    suspend fun testConnection(server: McpServer): TestResult {
        return try {
            startServer(server.id)
            
            // 测试基本功能
            val capabilities = queryCapabilities(server.id)
            
            stopServer(server.id)
            
            TestResult.Success(capabilities)
        } catch (e: Exception) {
            TestResult.Failure(e.message ?: "Unknown error")
        }
    }
    
    private suspend fun queryCapabilities(serverId: String): List<String> {
        // 查询MCP服务器能力
        // 通过stdin/stdout通信
        return listOf("filesystem", "database", "github")
    }
}
```

#### 3.5.4 作用域管理（ScopeManager）

**功能描述**：
管理全局配置与项目配置的优先级和合并策略。

**作用域类型**：
1. **全局作用域**：
   - 适用于所有项目
   - 存储在用户目录
   - 优先级最低
   
2. **项目作用域**：
   - 仅适用于当前项目
   - 存储在项目目录
   - 优先级高于全局
   
3. **会话作用域**：
   - 仅适用于当前会话
   - 临时存储
   - 优先级最高

**配置合并策略**：
```kotlin
class ScopeManager {
    fun getEffectiveConfig(project: Project?): EffectiveConfig {
        val globalConfig = configStorage.loadGlobalConfig()
        val projectConfig = project?.let { configStorage.loadProjectConfig(it) }
        val sessionConfig = sessionManager.getCurrentSession()?.context
        
        return EffectiveConfig(
            // 合并Skills：会话 > 项目 > 全局
            skills = mergeSkills(
                global = globalConfig.skills,
                project = projectConfig?.skills,
                session = sessionConfig?.enabledSkills
            ),
            
            // 合并MCP服务器：会话 > 项目 > 全局
            mcpServers = mergeMcpServers(
                global = globalConfig.mcpServers,
                project = projectConfig?.mcpServers,
                session = sessionConfig?.enabledMcpServers
            ),
            
            // 合并模型配置：会话 > 项目 > 全局
            modelConfig = sessionConfig?.modelConfig 
                ?: projectConfig?.modelConfig 
                ?: globalConfig.modelConfig
        )
    }
    
    private fun mergeSkills(
        global: List<Skill>,
        project: List<Skill>?,
        session: List<String>?
    ): List<Skill> {
        val merged = global.toMutableList()
        
        // 项目级别覆盖或新增
        project?.forEach { projectSkill ->
            val index = merged.indexOfFirst { it.id == projectSkill.id }
            if (index >= 0) {
                merged[index] = projectSkill
            } else {
                merged.add(projectSkill)
            }
        }
        
        // 会话级别过滤
        return if (session != null) {
            merged.filter { it.id in session }
        } else {
            merged
        }
    }
}
```

---

## 4. 技术架构深度设计

### 4.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Presentation Layer                           │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌──────────────────────────────────────────┐ │
│  │   Swing UI Layer     │  │         JCEF UI Layer                    │ │
│  │  - Settings Panel    │  │  - Chat Window (React 18)                │ │
│  │  - Popup Actions     │  │  - Session Manager (多Tab)               │ │
│  │  - Status Bar        │  │  - Task Progress Dashboard               │ │
│  │  - Notifications     │  │  - Theme Customizer                      │ │
│  │  - Quick Actions     │  │  - Skills/Agents/MCP UI                  │ │
│  └──────────────────────┘  └──────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                           Application Layer                            │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Chat Orchestration                              │ │
│  │  - ChatOrchestrator                                                │ │
│  │  - SessionManager                                                  │ │
│  │  - StreamingOutputEngine                                           │ │
│  │  - InteractiveRequestEngine ⭐                                     │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Context & Prompt Management                     │ │
│  │  - ContextProvider                                                 │ │
│  │  - PromptOptimizer                                                 │ │
│  │  - MultimodalInputHandler                                         │ │
│  │  - ConversationReferenceSystem                                    │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Configuration Management                       │ │
│  │  - ConfigManager                                                   │ │
│  │  - ThemeManager                                                    │ │
│  │  - ScopeManager (全局/项目/会话)                                   │ │
│  │  - HotReloadNotifier ⭐                                            │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Claude Code Ecosystem                           │ │
│  │  - SkillsManager                                                   │ │
│  │  - AgentsManager                                                   │ │
│  │  - McpServerManager                                                │ │
│  │  - SkillExecutor                                                   │ │
│  │  - AgentExecutor                                                   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                           Adaptation Layer                             │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    CLI Communication                               │ │
│  │  - StdioBridge                                                     │ │
│  │  - ProcessPool                                                     │ │
│  │  - MessageParser                                                   │ │
│  │  - StreamingResponseParser                                         │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Multi-Model Adapter                             │ │
│  │  - MultiProviderAdapter                                            │ │
│  │  - AnthropicProvider                                               │ │
│  │  - OpenAIProvider                                                  │ │
│  │  - DeepSeekProvider                                                │ │
│  │  - ModelSwitcher                                                   │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    IDE Integration                                 │ │
│  │  - DiffManager                                                     │ │
│  │  - CodeApplier                                                     │ │
│  │  - TerminalBridge                                                  │ │
│  │  - VersionDetector                                                 │ │
│  └────────────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────┤
│                           Infrastructure Layer                         │
├─────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Storage & Security                              │ │
│  │  - SecureStringStorage                                             │ │
│  │  - ConfigStorage                                                   │ │
│  │  - SessionStorage                                                  │ │
│  │  - CheckpointStorage                                               │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Event & State Management                        │ │
│  │  - EventBus                                                        │ │
│  │  - StateManager                                                    │ │
│  │  - TaskProgressTracker                                             │ │
│  │  - SessionInterruptRecovery                                        │ │
│  └────────────────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                    Monitoring & Utilities                          │ │
│  │  - Logger (4a1e级别)                                              │ │
│  │  - MetricsCollector                                                │ │
│  │  - CacheManager                                                    │ │
│  │  - ErrorRecoveryManager                                            │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
           ↕                                ↕
    Claude Code CLI              JetBrains Platform API
    (Node.js Process)             (PSI/VCS/RunConfig)
```

### 4.2 核心组件设计

#### 4.2.1 ChatOrchestrator（聊天编排器）

**职责**：
- 管理聊天消息流
- 协调各个模块交互
- 处理流式输出
- 管理会话状态

**接口定义**：
```kotlin
interface ChatOrchestrator {
    suspend fun sendMessage(message: ChatMessage): ChatResponse
    suspend fun sendMultimodalMessage(message: MultimodalMessage): ChatResponse
    suspend fun streamMessage(message: ChatMessage): Flow<String>
    fun cancelStreaming(sessionId: String)
    fun getContext(sessionId: String): SessionContext
    fun updateContext(sessionId: String, update: ContextUpdate)
}

class ChatOrchestratorImpl(
    private val providerAdapter: MultiProviderAdapter,
    private val sessionManager: MultiSessionManager,
    private val streamingEngine: StreamingOutputEngine,
    private val interactiveEngine: InteractiveRequestEngine
) : ChatOrchestrator {
    
    override suspend fun sendMessage(message: ChatMessage): ChatResponse {
        val session = sessionManager.getCurrentSession()
        
        // 1. 添加用户消息到会话
        session.messages.add(message)
        
        // 2. 构建完整上下文
        val fullContext = buildFullContext(session, message)
        
        // 3. 调用AI模型
        val response = providerAdapter.chat(
            session.context.modelConfig.provider,
            ChatRequest(
                model = session.context.modelConfig.model,
                messages = fullContext,
                maxTokens = 4096
            )
        )
        
        // 4. 处理交互式请求
        val processedResponse = interactiveEngine.handleAIResponse(response.content)
        
        // 5. 添加AI回复到会话
        val aiMessage = ChatMessage(
            role = "assistant",
            content = when (processedResponse) {
                is ProcessResult.Completed -> processedResponse.content
                is ProcessResult.WaitingForUserInput -> "等待用户输入..."
            }
        )
        session.messages.add(aiMessage)
        
        return ChatResponse(
            content = aiMessage.content,
            tokensUsed = response.usage.totalTokens,
            executionTime = response.executionTime
        )
    }
    
    override suspend fun streamMessage(message: ChatMessage): Flow<String> = flow {
        val session = sessionManager.getCurrentSession()
        
        // 1. 添加用户消息到会话
        session.messages.add(message)
        
        // 2. 创建AI消息占位符
        val aiMessage = ChatMessage(
            role = "assistant",
            content = ""
        )
        session.messages.add(aiMessage)
        
        // 3. 启动流式输出
        streamingEngine.startStreaming(session.id, aiMessage.id)
        
        // 4. 流式调用AI模型
        providerAdapter.streamChat(
            session.context.modelConfig.provider,
            ChatRequest(
                model = session.context.modelConfig.model,
                messages = buildFullContext(session, message),
                maxTokens = 4096
            )
        ).collect { chunk ->
            // 5. 实时更新消息内容
            aiMessage.content += chunk
            emit(chunk)
        }
        
        // 6. 完成流式输出
        streamingEngine.stopStreaming(session.id)
    }
}
```

#### 4.2.2 StreamingOutputEngine（流式输出引擎）

**职责**：
- 管理流式输出连接
- 处理SSE事件
- 实时更新UI

**接口定义**：
```kotlin
interface StreamingOutputEngine {
    fun startStreaming(sessionId: String, messageId: String)
    fun stopStreaming(messageId: String)
    fun onChunk(chunk: String)
    fun onComplete()
    fun onError(error: String)
}
```

#### 4.2.3 InteractiveRequestEngine（交互式请求引擎）

**职责**：
- 检测AI的交互式请求
- 向用户展示问题
- 处理用户回答
- 继续处理流程

**接口定义**：
```kotlin
interface InteractiveRequestEngine {
    suspend fun handleAIResponse(response: String): ProcessResult
    suspend fun submitAnswer(questionId: String, answer: Any)
    fun showQuestionToUser(question: InteractiveQuestion)
}
```

#### 4.2.4 ConfigHotReloadManager（配置热更新管理器）

**职责**：
- 监听配置变更
- 通知相关组件
- 实时应用配置

**接口定义**：
```kotlin
interface ConfigHotReloadManager {
    fun <T> watch(configKey: String, currentValue: T, listener: (T) -> Unit)
    fun notifyConfigChanged(configKey: String, newValue: Any)
    fun batchUpdate(updates: Map<String, Any>)
}
```

### 4.3 JCEF集成架构

#### 4.3.1 Java ↔ JS 双向通信

**Java → JS 通信**：
```kotlin
class JcefBridge {
    private val browser: JBCefBrowser
    private val jsQuery: JBCefJSQuery
    
    init {
        jsQuery = JBCefJSQuery.create(browser)
        
        // 注册Java端点
        jsQuery.addHandler { request ->
            val data = Json.parseToJsonElement(request).jsonObject
            val action = data["action"]?.jsonPrimitive?.content
            
            when (action) {
                "sendMessage" -> handleSendMessage(data)
                "getConfig" -> handleGetConfig(data)
                "updateTheme" -> handleUpdateTheme(data)
                else -> null
            }
        }
        
        // 注入JS端点
        browser.cefClient.executeJavaScript(
            """
            window.ccBackend = {
                send: (data) => $jsQuery.invoke(JSON.stringify(data)),
                
                // 便捷方法
                sendMessage: (message) => window.ccBackend.send({
                    action: 'sendMessage',
                    message: message
                }),
                
                getConfig: (key) => window.ccBackend.send({
                    action: 'getConfig',
                    key: key
                }),
                
                updateTheme: (theme) => window.ccBackend.send({
                    action: 'updateTheme',
                    theme: theme
                })
            };
            """.trimIndent(),
            null, 0
        )
    }
    
    private fun handleSendMessage(data: JsonObject): String? {
        val message = data["message"]?.jsonPrimitive?.content ?: return null
        
        GlobalScope.launch {
            val response = chatOrchestrator.sendMessage(
                ChatMessage(role = "user", content = message)
            )
            
            // 通知JS
            notifyJs("messageReceived", mapOf(
                "content" to response.content,
                "tokensUsed" to response.tokensUsed
            ))
        }
        
        return null
    }
    
    private fun notifyJs(event: String, data: Map<String, Any>) {
        browser.cefClient.executeJavaScript(
            """
            window.ccEvents.emit('$event', ${Json.encodeToString(data)});
            """.trimIndent(),
            null, 0
        )
    }
}
```

**JS → Java 通信**：
```typescript
// 在React组件中调用Java方法
const ChatInput: React.FC = () => {
    const handleSend = async (message: string) => {
        // 调用Java方法
        await window.ccBackend.sendMessage(message);
        
        // 或者通过事件监听
        window.ccEvents.on('messageReceived', (data) => {
            console.log('收到回复:', data.content);
        });
    };
    
    return (
        <input
            type="text"
            onKeyDown={(e) => {
                if (e.key === 'Enter') {
                    handleSend(e.currentTarget.value);
                }
            }}
        />
    );
};
```

#### 4.3.2 React组件架构

**组件层级**：
```
App
├── ChatLayout
│   ├── SessionTabs
│   ├── ChatPanel
│   │   ├── MessageList
│   │   │   ├── MessageItem (用户消息)
│   │   │   ├── MessageItem (AI消息)
│   │   │   └── InteractiveQuestionPanel ⭐
│   │   └── ChatInput
│   │       ├── TextInput
│   │       ├── AttachmentPreview
│   │       └── SendButton
│   └── PreviewPanel
│       ├── CodePreview
│       └── DiffViewer
├── TaskProgressDashboard
├── SettingsPanel
└── ThemeCustomizer
```

**状态管理**：
```typescript
// 使用React Context + Zustand进行状态管理
interface AppState {
    // 会话状态
    sessions: Session[];
    currentSessionId: string;
    
    // UI状态
    theme: ThemeConfig;
    sidebarOpen: boolean;
    
    // 任务进度
    taskProgress: TaskProgress | null;
    
    // 操作
    switchSession: (sessionId: string) => void;
    sendMessage: (message: string) => Promise<void>;
    updateTheme: (theme: ThemeConfig) => void;
}

const useAppStore = create<AppState>((set, get) => ({
    sessions: [],
    currentSessionId: '',
    theme: defaultTheme,
    sidebarOpen: true,
    taskProgress: null,
    
    switchSession: (sessionId) => {
        set({ currentSessionId: sessionId });
        window.ccBackend.switchSession(sessionId);
    },
    
    sendMessage: async (message) => {
        const { currentSessionId, sessions } = get();
        const session = sessions.find(s => s.id === currentSessionId);
        
        if (!session) return;
        
        // 添加用户消息
        set(state => ({
            sessions: state.sessions.map(s => 
                s.id === currentSessionId
                    ? { ...s, messages: [...s.messages, { 
                        role: 'user', 
                        content: message,
                        timestamp: Date.now()
                      }]}
                    : s
            )
        }));
        
        // 调用Java后端
        const response = await window.ccBackend.sendMessage(message);
        
        // 添加AI回复
        set(state => ({
            sessions: state.sessions.map(s => 
                s.id === currentSessionId
                    ? { ...s, messages: [...s.messages, { 
                        role: 'assistant', 
                        content: response.content,
                        timestamp: Date.now()
                      }]}
                    : s
            )
        }));
    },
    
    updateTheme: (theme) => {
        set({ theme });
        window.ccBackend.updateTheme(theme);
    }
}));
```

#### 4.3.3 性能优化策略

**1. 虚拟滚动**：
```typescript
import { FixedSizeList } from 'react-window';

const MessageList: React.FC<{ messages: ChatMessage[] }> = ({ messages }) => {
    return (
        <FixedSizeList
            height={600}
            itemCount={messages.length}
            itemSize={100}
            width="100%"
        >
            {({ index, style }) => (
                <div style={style}>
                    <MessageItem message={messages[index]} />
                </div>
            )}
        </FixedSizeList>
    );
};
```

**2. Markdown解析缓存**：
```typescript
const markdownCache = new Map<string, ReactNode>();

const MarkdownRenderer: React.FC<{ content: string }> = ({ content }) => {
    const cached = markdownCache.get(content);
    
    if (cached) {
        return cached;
    }
    
    const rendered = React.memo(() => (
        <ReactMarkdown 
            components={{
                code: CodeBlock,
                pre: PreBlock
            }}
        >
            {content}
        </ReactMarkdown>
    ));
    
    markdownCache.set(content, rendered);
    return rendered;
};
```

**3. 代码块懒加载**：
```typescript
const CodeBlock: React.FC<{ language: string; code: string }> = ({ language, code }) => {
    const [highlighted, setHighlighted] = useState<string>('');
    
    useEffect(() => {
        // 延迟高亮
        const timeout = setTimeout(() => {
            import('highlight.js').then(hljs => {
                const highlighted = hljs.highlight(code, { language }).value;
                setHighlighted(highlighted);
            });
        }, 100);
        
        return () => clearTimeout(timeout);
    }, [code, language]);
    
    return (
        <pre>
            <code dangerouslySetInnerHTML={{ __html: highlighted || code }} />
        </pre>
    );
};
```

### 4.4 状态管理设计

#### 4.4.1 会话状态管理

```kotlin
data class SessionState(
    val sessionId: String,
    val status: SessionStatus,
    val messages: List<ChatMessage>,
    val context: SessionContext,
    val streamingMessageId: String? = null,
    val pendingQuestions: List<InteractiveQuestion> = emptyList()
)

enum class SessionStatus {
    IDLE,
    THINKING,
    STREAMING,
    WAITING_FOR_USER,
    ERROR
}

class SessionStateManager {
    private val states = ConcurrentHashMap<String, SessionState>()
    
    fun updateState(sessionId: String, update: (SessionState) -> SessionState) {
        states.compute(sessionId) { _, currentState ->
            update(currentState ?: createInitialState(sessionId))
        }
        
        // 通知UI
        notifyStateChanged(sessionId, states[sessionId])
    }
    
    fun getState(sessionId: String): SessionState? {
        return states[sessionId]
    }
}
```

#### 4.4.2 配置状态管理

```kotlin
data class ConfigState(
    val theme: ThemeConfig,
    val modelConfig: ModelConfig,
    val skills: List<Skill>,
    val mcpServers: List<McpServer>,
    val version: Long = System.currentTimeMillis()
)

class ConfigStateManager {
    private val currentState = AtomicReference<ConfigState>()
    private val listeners = mutableListOf<ConfigChangeListener>()
    
    fun updateConfig(update: (ConfigState) -> ConfigState) {
        val newState = update(currentState.get() ?: loadInitialConfig())
        currentState.set(newState)
        
        // 通知监听器
        listeners.forEach { listener ->
            listener.onConfigChanged(newState)
        }
    }
    
    fun subscribe(listener: ConfigChangeListener): Disposable {
        listeners.add(listener)
        
        return Disposable {
            listeners.remove(listener)
        }
    }
}
```

#### 4.4.3 任务进度状态管理

```kotlin
data class TaskProgressState(
    val taskId: String,
    val totalSteps: Int,
    val currentStep: Int,
    val steps: List<StepState>,
    val status: TaskStatus,
    val estimatedTimeRemaining: Long? = null
)

data class StepState(
    val id: String,
    val name: String,
    val status: StepStatus,
    val output: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null
)

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

class TaskProgressManager {
    private val tasks = ConcurrentHashMap<String, TaskProgressState>()
    
    fun createTask(taskId: String, steps: List<StepInfo>): TaskProgressState {
        val task = TaskProgressState(
            taskId = taskId,
            totalSteps = steps.size,
            currentStep = 0,
            steps = steps.map { StepState(
                id = it.id,
                name = it.name,
                status = StepStatus.PENDING
            )},
            status = TaskStatus.PENDING
        )
        
        tasks[taskId] = task
        notifyTaskCreated(task)
        
        return task
    }
    
    fun updateStep(taskId: String, stepId: String, status: StepStatus) {
        val task = tasks[taskId] ?: return
        
        task.steps = task.steps.map { step ->
            if (step.id == stepId) {
                step.copy(
                    status = status,
                    startTime = if (status == StepStatus.IN_PROGRESS) System.currentTimeMillis() else step.startTime,
                    endTime = if (status == StepStatus.COMPLETED) System.currentTimeMillis() else step.endTime
                )
            } else {
                step
            }
        }
        
        // 更新当前步骤
        if (status == StepStatus.IN_PROGRESS) {
            task.currentStep = task.steps.indexOfFirst { it.id == stepId }
        }
        
        // 检查是否全部完成
        if (task.steps.all { it.status == StepStatus.COMPLETED }) {
            task.status = TaskStatus.COMPLETED
        }
        
        notifyTaskUpdated(task)
    }
}
```

---

## 5. 详细开发计划（WBS）

### Phase 1: 核心基础（6周）

**目标**：搭建基础架构，实现核心通信链路和基础UI

#### Sprint 1.1: 架构搭建与CLI通信（2周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T1.1.1** | 搭建项目基础架构（Gradle配置、依赖管理） | 1人天 | Kotlin DSL配置 | 项目可正常构建和运行 |
| **T1.1.2** | 实现StdioBridge核心通信逻辑 | 2人天 | 消息边界检测、进程管理 | 可发送消息并收到回复 |
| **T1.1.3** | 实现ProcessPool进程池管理 | 2人天 | 进程生命周期管理、并发控制 | 支持5个并发请求 |
| **T1.1.4** | 实现VersionDetector版本探测 | 1人天 | 版本解析兼容性 | 检测准确率100% |
| **T1.1.5** | 实现SecureStorage加密存储 | 1人天 | PasswordSafe API集成 | API Key安全存储 |
| **T1.1.6** | 编写通信层单元测试 | 1.5人天 | 模拟进程输入输出 | 测试覆盖率>90% |

**里程碑**：CLI通信链路打通，可发送"Hello"并收到回复

#### Sprint 1.2: 基础UI框架（2周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T1.2.1** | 使用Kotlin UI DSL实现设置页 | 2人天 | Configurable接口实现 | 设置项可保存/读取 |
| **T1.2.2** | 创建ToolWindow容器 | 1人天 | ToolWindowFactory注册 | ToolWindow可正常打开 |
| **T1.2.3** | 搭建JCEF环境，加载React应用 | 2人天 | JBCefBrowser初始化、React构建配置 | 可显示空白聊天界面 |
| **T1.2.4** | 实现基础React聊天组件 | 2人天 | 组件状态管理 | 可显示消息列表 |
| **T1.2.5** | 实现Java-JS双向通信 | 2人天 | JBCefJSQuery端点注册 | 消息往返延迟<50ms |
| **T1.2.6** | 实现状态栏Widget | 1人天 | StatusBarWidget注册 | 状态栏显示当前模型 |

**里程碑**：可通过UI发送消息并收到CLI回复

#### Sprint 1.3: 多模型适配（2周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T1.3.1** | 实现AnthropicProvider适配器 | 2人天 | API认证、SSE解析 | 可调用Claude API |
| **T1.3.2** | 实现OpenAIProvider适配器 | 1.5人天 | API格式差异处理 | 可调用GPT API |
| **T1.3.3** | 实现DeepSeekProvider适配器 | 1人天 | 兼容OpenAI协议 | 可调用DeepSeek API |
| **T1.3.4** | 实现MultiProviderAdapter | 1.5人天 | 供应商管理、错误处理 | 可动态切换供应商 |
| **T1.3.5** | 实现ModelSwitcher模型切换 | 1人天 | 模型验证、配置更新 | 切换耗时<1s |
| **T1.3.6** | 编写适配器单元测试 | 1人天 | Mock API响应 | 测试覆盖率>85% |

**里程碑**：支持多个AI供应商和模型切换

### Phase 2: UI与交互（5周）

**目标**：实现完整UI系统和交互增强功能

#### Sprint 2.1: 主题系统（1.5周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T2.1.1** | 设计主题数据结构 | 0.5人天 | 扩展性设计 | 支持6套预设主题 |
| **T2.1.2** | 实现ThemeManager主题管理器 | 1.5人天 | 主题切换、JCEF通知 | 主题切换<100ms |
| **T2.1.3** | 实现主题编辑器UI | 2人天 | 颜色选择器、实时预览 | 可自定义主题 |
| **T2.1.4** | 实现配置热更新机制 | 2人天 | 监听器模式、防抖处理 | 配置变更实时生效 |
| **T2.1.5** | 实现响应式布局 | 1.5人天 | ResizeObserver、断点设计 | 支持800-1200px宽度 |

**里程碑**：完整的主题系统，支持6套预设主题和自定义主题

#### Sprint 2.2: 交互增强（2周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T2.2.1** | 实现PromptOptimizer提示词优化 | 2人天 | AI调用、结果解析 | 优化后提示词质量提升>30% |
| **T2.2.2** | 实现代码快捷操作 | 1.5人天 | AnAction注册、上下文收集 | 支持7种快捷操作 |
| **T2.2.3** | 实现多模态输入处理 | 2人天 | 文件解析、Base64编码 | 支持图片和附件 |
| **T2.2.4** | 实现对话引用系统 | 1.5人天 | 消息索引、引用渲染 | 可引用历史消息 |
| **T2.2.5** | 实现交互式请求引擎⭐ | 2.5人天 | 状态机、UI交互 | AI可主动提问 |

**里程碑**：完整的交互增强系统

#### Sprint 2.3: Markdown渲染（1.5周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T2.3.1** | 集成marked.js Markdown解析 | 1人天 | 自定义渲染规则 | 支持GFM扩展语法 |
| **T2.3.2** | 集成highlight.js代码高亮 | 1.5人天 | 20+语言支持 | 代码块渲染正确 |
| **T2.3.3** | 实现代码块复制按钮 | 1人天 | JS事件处理 | 一键复制功能正常 |
| **T2.3.4** | 支持LaTeX公式渲染 | 1.5人天 | KaTeX集成 | 公式显示正确 |
| **T2.3.5** | 优化渲染性能 | 1人天 | 虚拟滚动、缓存 | 长消息渲染<200ms |

**里程碑**：完整的Markdown渲染系统

### Phase 3: 会话与生态（4周）

**目标**：实现多会话管理和Claude Code生态集成

#### Sprint 3.1: 多会话管理（2周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T3.1.1** | 实现MultiSessionManager | 2人天 | 会话隔离、状态管理 | 支持10+并发会话 |
| **T3.1.2** | 实现会话Tab切换 | 1.5人天 | React Tab组件 | 切换延迟<100ms |
| **T3.1.3** | 实现会话搜索功能 | 1.5人天 | Lucene全文索引 | 搜索响应<500ms |
| **T3.1.4** | 实现会话导入导出 | 1人天 | Markdown/PDF导出 | 导出文件格式正确 |
| **T3.1.5** | 实现会话自动命名 | 1人天 | NLP摘要生成 | 命名准确率>70% |

**里程碑**：完整的多会话管理系统

#### Sprint 3.2: 流式输出与中断（1周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T3.2.1** | 实现StreamingOutputEngine | 2人天 | SSE解析、实时渲染 | 流式输出首字延迟<500ms |
| **T3.2.2** | 实现会话中断功能 | 1.5人天 | 进程终止、状态恢复 | 中断响应<200ms |
| **T3.2.3** | 实现会话恢复机制 | 1.5人天 | 检查点保存、状态恢复 | 恢复成功率>95% |

**里程碑**：完整的流式输出和中断恢复系统

#### Sprint 3.3: 任务进度可视化（1周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T3.3.1** | 实现TaskParser任务解析 | 1人天 | AI响应解析 | 解析准确率>80% |
| **T3.3.2** | 实现TaskProgressTracker | 1.5人天 | 进度跟踪、状态更新 | 实时更新进度 |
| **T3.3.3** | 实现进度条UI | 1人天 | React动画 | 进度条流畅更新 |
| **T3.3.4** | 实现预估时间计算 | 1人天 | 时间估算算法 | 误差<30% |

**里程碑**：完整的任务进度可视化系统

#### Sprint 3.4: Claude Code生态集成（3周）

| 任务ID | 任务描述 | 工作量 | 技术难点 | 验收标准 |
|--------|----------|--------|----------|----------|
| **T3.4.1** | 实现SkillsManager | 2人天 | Skill数据结构、执行引擎 | 支持10+内置Skills |
| **T3.4.2** | 实现AgentsManager | 2人天 | Agent行为模式、约束 | 支持3种Agent模式 |
| **T3.4.3** | 实现McpServerManager | 2人天 | 进程管理、连接测试 | 支持5+MCP服务器 |
| **T3.4.4** | 实现ScopeManager作用域管理 | 1.5人天 | 配置合并策略 | 全局/项目/会话隔离 |
| **T3.4.5** | 实现生态UI（Skills/Agents/MCP） | 2.5人天 | 拖拽排序、表单验证 | UI操作流畅 |

**里程碑**：完整的Claude Code生态集成

### Phase 4: 测试与发布（3周）

**目标**：全面测试、性能优化、发布准备

#### Sprint 4.1: 测试（1.5周）

| 任务ID | 任务描述 | 工作量 | 验收标准 |
|--------|----------|--------|----------|
| **T4.1.1** | 功能测试（5大模块） | 2人天 | 功能完成度100% |
| **T4.1.2** | 性能测试（响应时间、内存） | 1.5人天 | 满足性能指标 |
| **T4.1.3** | 兼容性测试（IDE版本、OS） | 2人天 | 支持95%+用户 |
| **T4.1.4** | 安全测试（API Key、数据传输） | 1人天 | 通过安全审计 |
| **T4.1.5** | 压力测试（并发、长时间运行） | 1人天 | 无内存泄漏 |

**里程碑**：通过所有测试

#### Sprint 4.2: 优化（1周）

| 任务ID | 任务描述 | 工作量 | 验收标准 |
|--------|----------|--------|----------|
| **T4.2.1** | 性能优化（启动速度、渲染） | 2人天 | 启动<1.2s，渲染<200ms |
| **T4.2.2** | 内存优化（JCEF、缓存） | 1.5人天 | 内存占用<500MB |
| **T4.2.3** | UI/UX优化（动画、交互） | 1.5人天 | 用户满意度>4星 |

**里程碑**：性能和体验达标

#### Sprint 4.3: 发布（0.5周）

| 任务ID | 任务描述 | 工作量 | 验收标准 |
|--------|----------|--------|----------|
| **T4.3.1** | 编写用户文档 | 1人天 | 文档覆盖90%功能 |
| **T4.3.2** | 准备Marketplace素材 | 0.5人天 | 截图、描述合规 |
| **T4.3.3** | 提交审核 | 0.5人天 | 审核通过 |

**里程碑**：成功发布到JetBrains Marketplace

---

## 6. 验收标准体系

### 6.1 功能验收（按5大模块）

#### 6.1.1 UI与主题系统

| 功能 | 验收标准 |
|------|----------|
| 主题切换 | 支持6套预设主题，切换延迟<100ms |
| 自定义主题 | 可自定义颜色、字体、间距、圆角 |
| 配置热更新 | 所有配置变更无需重启IDE，实时生效 |
| 响应式布局 | 支持800-1200px宽度，布局自动调整 |

#### 6.1.2 交互增强系统

| 功能 | 验收标准 |
|------|----------|
| 提示词优化 | 优化后提示词质量提升>30% |
| 代码快捷操作 | 支持7种快捷操作，响应<200ms |
| 多模态输入 | 支持图片（PNG/JPG）和附件（PDF/DOCX） |
| 对话引用 | 可引用历史消息，显示引用来源 |
| 交互式请求 | AI可主动提问，用户可回答并继续 |

#### 6.1.3 会话管理系统

| 功能 | 验收标准 |
|------|----------|
| 多会话管理 | 支持10+并发会话，切换延迟<100ms |
| 流式输出 | 首字延迟<500ms，打字机效果流畅 |
| 会话中断 | 中断响应<200ms，恢复成功率>95% |
| 历史检索 | 全文搜索响应<500ms |
| 任务进度 | 实时显示进度，预估时间误差<30% |

#### 6.1.4 模型配置系统

| 功能 | 验收标准 |
|------|----------|
| 多供应商 | 支持Anthropic/OpenAI/DeepSeek等 |
| 模型切换 | 切换耗时<1s，无需重启 |
| 对话模式 | 支持Thinking/Plan/Auto三种模式 |

#### 6.1.5 Claude Code生态集成

| 功能 | 验收标准 |
|------|----------|
| Skills管理 | 支持10+内置Skills，可自定义 |
| Agents管理 | 支持3种Agent模式（Cautious/Balanced/Aggressive） |
| MCP服务器 | 支持5+MCP服务器，连接测试准确 |
| 作用域管理 | 全局/项目/会话配置隔离 |

### 6.2 性能验收（量化指标）

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| ToolWindow首次打开 | < 1.2s | 计时器测量 |
| 消息响应延迟 | < 300ms P95 | 日志时间戳差值 |
| 流式输出首字延迟 | < 500ms | Performance API |
| Markdown渲染 | < 200ms/条 | Performance API |
| 主题切换延迟 | < 100ms | 计时器测量 |
| 配置热更新延迟 | < 100ms | 计时器测量 |
| 会话切换延迟 | < 100ms | 计时器测量 |
| 内存占用 | < 500MB | IDEA内存监控 |
| CPU占用（空闲） | < 5% | 系统监控工具 |
| JCEF启动开销 | < 500ms | 进程启动日志 |

### 6.3 兼容性验收

#### 6.3.1 IDE版本

| IDE | 版本范围 | 支持状态 |
|----|----------|----------|
| IntelliJ IDEA | 2022.3+ | ✅ 完全支持 |
| PyCharm | 2023.1+ | ✅ 完全支持 |
| WebStorm | 2023.1+ | ✅ 完全支持 |
| Android Studio | 2023.1+ | ⚠️ 实验性 |
| Rider | 2023.2+ | ⚠️ 实验性 |

**覆盖率目标**: 支持98%+活跃用户

#### 6.3.2 操作系统

| OS | 版本 | 特殊处理 |
|----|------|----------|
| Windows | 10/11 | Node.js路径检测 |
| macOS | 12+ | Homebrew路径支持 |
| Linux | Ubuntu 20.04+ | 环境变量兼容性 |

### 6.4 安全验收

| 场景 | 验证方法 |
|------|----------|
| API Key加密 | 检查存储文件（应为加密内容） |
| HTTPS传输 | 抓包验证（无明文传输） |
| 权限控制 | 仅访问用户授权的文件 |
| 敏感信息脱敏 | 日志中不包含API Key |
| MCP沙箱 | MCP服务器隔离运行 |

---

## 7. 风险管理

### 7.1 技术风险

| 风险ID | 风险描述 | 等级 | 概率 | 影响 | 缓解策略 |
|--------|----------|------|------|------|----------|
| **R1** | JCEF与旧版IDEA不兼容 | P1 | 中 | 严重 | 2022.3以下版本回退到Swing UI |
| **R2** | 流式输出解析失败 | P1 | 中 | 严重 | 支持降级到非流式模式 |
| **R3** | 多模态输入处理性能问题 | P2 | 高 | 中等 | 图片压缩、懒加载 |
| **R4** | 交互式请求状态管理复杂 | P2 | 中 | 中等 | 使用状态机模式 |
| **R5** | 配置热更新导致状态不一致 | P2 | 中 | 中等 | 使用事务机制 |
| **R6** | JCEF内存泄漏 | P1 | 中 | 严重 | 主动dispose、定期回收 |

### 7.2 产品风险

| 风险ID | 风险描述 | 等级 | 概率 | 影响 | 缓解策略 |
|--------|----------|------|------|------|----------|
| **R7** | 用户不理解交互式请求 | P2 | 中 | 中等 | 提供清晰引导和帮助文档 |
| **R8** | 多会话管理复杂度高 | P2 | 高 | 中等 | 提供视频教程 |
| **R9** | 主题自定义过于复杂 | P3 | 低 | 轻微 | 提供预设主题和模板 |
| **R10** | MCP服务器配置困难 | P2 | 中 | 中等 | 提供配置向导 |

### 7.3 进度风险

| 风险ID | 风险描述 | 等级 | 概率 | 影响 | 缓解策略 |
|--------|----------|------|------|------|----------|
| **R11** | JCEF学习曲线陡峭 | P1 | 高 | 严重 | 提前培训、技术预研 |
| **R12** | React生态选择困难 | P2 | 中 | 中等 | 选择成熟方案（React 18 + Zustand） |
| **R13** | 多供应商API差异大 | P2 | 中 | 中等 | 提前调研、设计适配器 |
| **R14** | 测试覆盖不全面 | P2 | 中 | 中等 | 自动化测试、CI集成 |

### 7.4 关键风险应对方案

#### R1: JCEF兼容性风险

**风险详情**: JCEF在IDEA 2022.3以下版本不可用或行为异常。

**缓解策略**:
1. **版本检测**:
```kotlin
val isJcefAvailable = ApplicationManager.getApplication()
    .run { try { JBCefApp.getInstance(); true } catch (e: Exception) { false } }
```

2. **优雅降级**:
```kotlin
if (isJcefAvailable) {
    showJcefChatWindow()
} else {
    showSwingChatWindow()
    showNotification("建议升级IDEA以获得完整体验")
}
```

3. **性能优化**:
- 延迟加载：ToolWindow首次打开时才初始化
- 资源复用：全局单例`JBCefClient`

#### R2: 流式输出解析失败

**风险详情**: SSE解析失败导致流式输出无法正常工作。

**缓解策略**:
1. **支持多种协议**:
- SSE（Server-Sent Events）
- WebSocket
- 轮询（降级方案）

2. **错误处理**:
```kotlin
try {
    provider.streamChat(request).collect { chunk ->
        emit(chunk)
    }
} catch (e: SseParseException) {
    // 降级到非流式模式
    val response = provider.chat(request)
    emit(response.content)
}
```

3. **超时处理**:
- 连接超时：30秒
- 首字超时：10秒
- 块超时：5秒

#### R6: JCEF内存泄漏

**风险详情**: JCEF实例未正确释放导致内存泄漏。

**缓解策略**:
1. **主动释放**:
```kotlin
class ChatToolWindow : ToolWindowFactory {
    private lateinit var browser: JBCefBrowser
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        browser = JBCefBrowserBuilder()
            .setInitialPage(htmlContent)
            .build()
        
        toolWindow.contentManager.addContent(
            ContentFactory.getInstance().createContent(
                browser.component,
                "",
                false
            )
        )
    }
    
    override fun dispose() {
        browser.dispose()
        super.dispose()
    }
}
```

2. **定期回收**:
```kotlin
class JcefMemoryManager {
    private val cleanupInterval = 300_000L // 5分钟
    
    init {
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(cleanupInterval)
                cleanupUnusedBrowsers()
            }
        }
    }
    
    private fun cleanupUnusedBrowsers() {
        // 回收超过10分钟未使用的浏览器实例
    }
}
```

3. **监控**:
- 定期检查内存占用
- 超过阈值时警告用户
- 提供手动清理按钮

---

## 8. 附录

### 8.1 UI原型图（文字描述）

#### 8.1.1 主界面布局

```
┌─────────────────────────────────────────────────────────────────────┐
│  ClaudeCodeJet                                    [设置] [主题] [?] │
├─────────────────────────────────────────────────────────────────────┤
│  [会话1] [会话2] [会话3] [+]                                        │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────┐ ┌───────────────────────────┐ │
│  │  聊天区域（60%）                 │ │  预览区域（40%）          │ │
│  │                                 │ │                           │ │
│  │  [用户] 如何实现快速排序？        │ │  ┌─代码预览─┐            │ │
│  │  [AI] 快速排序是一种高效的...    │ │  │ def quick_sort... │    │ │
│  │                                 │ │  └─────────────┘            │ │
│  │  [用户] 这个实现有什么问题？       │ │  ┌─Diff视图──┐            │ │
│  │  [AI] 检测到以下问题：            │ │  │ - pivot选择   │         │ │
│  │  1. pivot选择可能导致...          │ │  │ - 递归深度    │         │ │
│  │  2. 递归深度可能...               │ │  └─────────────┘            │ │
│  │                                 │ │                           │ │
│  │  [交互式问题] 您希望优先解决哪个问题？                        │ │
│  │  ○ 问题1：pivot选择               │ │                           │ │
│  │  ● 问题2：递归深度                │ │                           │ │
│  │  [确认] [跳过]                    │ │                           │ │
│  │                                 │ │                           │ │
│  │  ┌─────────────────────────────┐ │ │                           │ │
│  │  │ [输入框...]           [发送] │ │ │                           │ │
│  │  │ [附件] [图片] [优化提示词]    │ │ │                           │ │
│  │  └─────────────────────────────┘ │ │                           │ │
│  └─────────────────────────────────┘ └───────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────┤
│  模型: Claude 3.5 Sonnet | 模式: Thinking | Token: 1234/10000      │
└─────────────────────────────────────────────────────────────────────┘
```

#### 8.1.2 设置页面

```
┌─────────────────────────────────────────────────────────────────────┐
│  设置                              [应用] [取消] [重置]             │
├─────────────────────────────────────────────────────────────────────┤
│  [通用] [模型] [Skills] [MCP] [高级]                               │
├─────────────────────────────────────────────────────────────────────┤
│  模型配置                                                            │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  供应商: [Anthropic ▼]                                      │   │
│  │  模型:   [Claude 3.5 Sonnet ▼]                              │   │
│  │  API Key: [sk-ant-xxxxxxxxxxxxxxxxxxxxx] [验证]             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  对话模式                                                            │
│  ○ Thinking  (深度思考，逐步推理)                                   │
│  ● Plan      (先规划后执行)                                         │
│  ○ Auto      (快速响应，直接执行)                                   │
│                                                                      │
│  主题                                                                │
│  ● JetBrains Dark  ○ GitHub Dark  ○ VS Code Dark  ○ 自定义...     │
│                                                                      │
│  热更新                                                              │
│  ☑ 启用配置热更新（无需重启IDE）                                    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

#### 8.1.3 Skills管理页面

```
┌─────────────────────────────────────────────────────────────────────┐
│  Skills管理                                      [新建] [导入] [导出] │
├─────────────────────────────────────────────────────────────────────┤
│  [所有] [代码生成] [代码审查] [重构] [测试] [文档]                  │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 🔍 代码审查                              [编辑] [删除] [禁用]│   │
│  │ Review the following code for bugs, performance issues...   │   │
│  │ 快捷键: Ctrl+Shift+R  |  作用域: 全局                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 🧪 生成测试                              [编辑] [删除] [启用]│   │
│  │ Generate comprehensive unit tests for the following code... │   │
│  │ 快捷键: Ctrl+Shift+T  |  作用域: 项目                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ ⚡ 代码优化                              [编辑] [删除] [启用]│   │
│  │ Optimize the following code for performance and readability │   │
│  │ 快捷键: Ctrl+Shift+O  |  作用域: 全局                       │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

#### 8.1.4 任务进度面板

```
┌─────────────────────────────────────────────────────────────────────┐
│  任务进度 (3/5)                                   [暂停] [取消]     │
│  █████████████████████████████████░░░░░░░░░░░░░░░░░░░░░░           │
│                                                                      │
│  ✓ 步骤1: 分析代码结构              (已完成) 2s                      │
│  ✓ 步骤2: 识别性能瓶颈              (已完成) 5s                      │
│  → 步骤3: 优化算法实现              (进行中) 3s...                   │
│    │  └─ 正在替换排序算法...                                     │
│  ⏳ 步骤4: 添加单元测试              (等待中) 预计30s               │
│  ⏳ 步骤5: 生成文档                  (等待中) 预计15s               │
│                                                                      │
│  预计剩余时间: 48秒                                                  │
│  Token消耗: 1234/10000                                              │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 API接口定义

#### 8.2.1 Java → JavaScript 接口

```typescript
interface JavaToJsBridge {
    // 消息相关
    sendMessage(message: string): Promise<ChatResponse>;
    streamMessage(message: string): void;
    cancelStreaming(sessionId: string): void;
    
    // 配置相关
    getConfig(key: string): any;
    updateConfig(config: Partial<ConfigState>): void;
    updateTheme(theme: ThemeConfig): void;
    
    // 会话相关
    createSession(name: string, type: SessionType): Session;
    switchSession(sessionId: string): void;
    deleteSession(sessionId: string): void;
    searchSessions(query: string): Session[];
    
    // 生态相关
    executeSkill(skillId: string, context: ExecutionContext): SkillResult;
    startAgent(agentId: string, task: AgentTask): void;
    startMcpServer(serverId: string): void;
    stopMcpServer(serverId: string): void;
}
```

#### 8.2.2 JavaScript → Java 接口

```typescript
interface JsToJavaBridge {
    // 消息事件
    onMessageReceived(callback: (message: ChatMessage) => void): void;
    onStreamingChunk(callback: (chunk: string) => void): void;
    onStreamingComplete(callback: () => void): void;
    
    // 配置事件
    onConfigChanged(callback: (config: ConfigState) => void): void;
    onThemeChanged(callback: (theme: ThemeConfig) => void): void;
    
    // 会话事件
    onSessionChanged(callback: (sessionId: string) => void): void;
    onSessionCreated(callback: (session: Session) => void): void;
    onSessionDeleted(callback: (sessionId: string) => void): void;
    
    // 任务进度事件
    onTaskProgress(callback: (progress: TaskProgress) => void): void;
    onStepComplete(callback: (step: StepState) => void): void;
    
    // 交互式请求事件
    onQuestionAsked(callback: (question: InteractiveQuestion) => void): void;
}
```

### 8.3 数据结构设计

#### 8.3.1 核心数据结构

```kotlin
// 消息
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<ContentPart> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

// 多模态内容
sealed class ContentPart {
    data class Text(
        val text: String,
        val language: String? = null
    ) : ContentPart()
    
    data class Image(
        val mimeType: String,
        val data: String  // Base64
    ) : ContentPart()
    
    data class File(
        val name: String,
        val content: String,
        val mimeType: String
    ) : ContentPart()
}

// 会话
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: SessionType,
    val projectId: String? = null,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val context: SessionContext = SessionContext(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

enum class SessionType {
    PROJECT,
    GLOBAL,
    TEMPORARY
}

// 主题
data class ThemeConfig(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val colors: ColorScheme,
    val typography: Typography,
    val spacing: Spacing,
    val borderRadius: BorderRadius
)

// 模型配置
data class ModelConfig(
    val provider: String,
    val model: String,
    val apiKey: String,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
    val topP: Double = 0.9
)
```

### 8.4 配置文件示例

#### 8.4.1 plugin.xml

```xml
<idea-plugin>
    <id>com.claudecodejet</id>
    <name>ClaudeCodeJet</name>
    <version>3.0.0</version>
    <vendor>ClaudeCodeJet Team</vendor>
    
    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.lang</depends>
    
    <extensions defaultExtensionNs="com.intellij">
        <!-- Tool Window -->
        <toolWindow factoryClass="ChatToolWindowFactory" 
                    id="ClaudeCodeJet" 
                    anchor="right"
                    icon="/META-INF/icon.svg"/>
        
        <!-- Application Service -->
        <applicationService serviceImplementation="ConfigManager"/>
        <applicationService serviceImplementation="ThemeManager"/>
        <applicationService serviceImplementation="MultiProviderAdapter"/>
        
        <!-- Project Service -->
        <projectService serviceImplementation="ChatOrchestrator"/>
        <projectService serviceImplementation="MultiSessionManager"/>
        <projectService serviceImplementation="SkillsManager"/>
        <projectService serviceImplementation="AgentsManager"/>
        <projectService serviceImplementation="McpServerManager"/>
        
        <!-- Configurable -->
        <projectConfigurable groupId="tools"
                             displayName="ClaudeCodeJet"
                             instance="MainConfigurable"/>
        
        <!-- Actions -->
        <action id="ChatAction"
                class="ChatAction"
                text="Chat with Claude"
                description="Open Claude chat">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift C"/>
            <add-to-group group-id="EditorPopupMenu" anchor="first"/>
        </action>
        
        <action id="ExplainCodeAction"
                class="ExplainCodeAction"
                text="Explain Code"
                description="Explain selected code">
            <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift E"/>
            <add-to-group group-id="EditorPopupMenu" anchor="after" relative-to-action="ChatAction"/>
        </action>
        
        <!-- StatusBar Widget -->
        <statusBarWidget factoryClass="ModelStatusWidgetFactory" 
                        id="ClaudeCodeJet.ModelStatus"
                        anchor="right"/>
    </extensions>
</idea-plugin>
```

#### 8.4.2 应用配置示例

```json
{
  "version": "3.0.0",
  "theme": {
    "id": "jetbrains-dark",
    "isDark": true,
    "colors": {
      "primary": "#007AFF",
      "background": "#1E1E1E",
      "userMessage": "#2563EB",
      "aiMessage": "#374151",
      "codeBlock": "#111827",
      "border": "#374151",
      "textPrimary": "#F9FAFB",
      "textSecondary": "#9CA3AF"
    },
    "typography": {
      "messageFont": "Inter",
      "codeFont": "JetBrains Mono",
      "fontSize": 14
    },
    "spacing": {
      "messageSpacing": 16,
      "codeBlockPadding": 12
    },
    "borderRadius": {
      "messageBubble": 8,
      "codeBlock": 6
    }
  },
  "model": {
    "provider": "anthropic",
    "model": "claude-3-5-sonnet-20241022",
    "maxTokens": 4096,
    "temperature": 0.7,
    "topP": 0.9
  },
  "conversation": {
    "mode": "auto",
    "showThinking": false,
    "showPlan": false,
    "autoExecute": true
  },
  "skills": [
    {
      "id": "code-review",
      "name": "Code Review",
      "enabled": true,
      "scope": "global"
    },
    {
      "id": "write-tests",
      "name": "Generate Tests",
      "enabled": true,
      "scope": "project"
    }
  ],
  "mcpServers": {
    "filesystem": {
      "enabled": true,
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "E:\\projects"],
      "env": {}
    }
  },
  "hotReload": true,
  "checkpoints": {
    "enabled": true,
    "interval": 5000,
    "maxCount": 10
  }
}
```

---

## 9. 总结

本PRD v3.0企业级完整版在v2.0基础上进行了全面升级，重点强化了以下5大核心能力：

### 9.1 核心升级点

1. **UI与主题系统**：
   - 支持6套预设主题 + 完整自定义主题编辑器
   - 配置热更新机制，所有配置变更无需重启IDE
   - 响应式布局设计，适配不同屏幕尺寸

2. **交互增强系统**：
   - 提示词优化器，提升AI回复质量
   - 代码快捷操作，7种常用操作一键触发
   - 多模态输入支持，图片/附件直接放入对话
   - 对话引用系统，方便上下文延续
   - **交互式请求引擎**，AI可主动提问并动态调整方案

3. **会话管理系统**：
   - 多会话管理器，支持10+并发会话
   - 流式输出引擎，打字机效果流畅
   - 会话中断与恢复，恢复成功率>95%
   - 历史会话检索，全文搜索响应<500ms
   - 任务进度可视化，实时显示AI工作状态

4. **模型配置系统**：
   - 多供应商适配器，支持Anthropic/OpenAI/DeepSeek等
   - 模型切换器，切换耗时<1s
   - 对话模式管理器，支持Thinking/Plan/Auto三种模式

5. **Claude Code生态集成**：
   - Skills管理器，支持10+内置Skills
   - Agents管理器，支持3种Agent模式
   - MCP服务器管理器，支持5+MCP服务器
   - 作用域管理，全局/项目/会话配置隔离

### 9.2 技术亮点

1. **混合架构**：Swing（配置页）+ JCEF（聊天界面），性能与体验平衡
2. **双向通信**：Java ↔ JS高效通信，延迟<50ms
3. **状态管理**：集中式状态管理，配置热更新
4. **性能优化**：虚拟滚动、Markdown缓存、代码块懒加载
5. **可扩展性**：模块化设计，易于添加新功能

### 9.3 项目里程碑

- **Phase 1（6周）**：核心基础架构
- **Phase 2（5周）**：UI与交互增强
- **Phase 3（4周）**：会话管理与生态集成
- **Phase 4（3周）**：测试与发布

**总周期**：18周（约4.5个月）

### 9.4 成功指标

- **用户留存率**：首周>70%，30天>50%
- **使用频次**：DAU/MAU>0.5
- **性能指标**：ToolWindow打开<1.2s，消息延迟<300ms
- **兼容性**：支持98%+活跃用户
- **功能完成度**：5大核心模块100%完成

### 9.5 下一步行动

1. **技术评审**：确认架构可行性，特别是JCEF集成方案
2. **团队组建**：招募2名后端开发，1名前端开发，1名QA
3. **Sprint 1.1启动**：CLI通信验证（关键路径）
4. **技术预研**：JCEF环境搭建、React项目初始化
5. **风险监控**：每周五下午风险回顾，更新风险矩阵

---

**文档维护**：
- 本文档为PRD v3.0正式版，作为开发实施的唯一权威依据
- 重大变更需经过团队评审，并更新此文档
- 所有技术决策必须有追溯记录

**联系方式**：
- 产品负责人：[待定]
- 技术负责人：[待定]
- 项目经理：[待定]

---

**文档版本历史**：
- v1.0：初始版本（2025-01-15）
- v2.0：增加技术架构和WBS拆解（2026-04-01）
- v3.0：企业级完整版，增加5大核心模块（2026-04-08）

---

**End of PRD v3.0**
