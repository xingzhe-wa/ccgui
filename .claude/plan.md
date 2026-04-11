# 设置集成中心优化计划（修正版）

## 需求澄清

1. **运行时配置**（Mode/Agent/Thinking/Streaming）→ 从 ToolbarDropdown 移到 **ChatInput 底部左侧**
2. **导航项（Skills/Agents/MCP）+ 设置项（供应商配置/主题/关于）** → 合并为 **一个独立页面**，左右布局
3. **供应商快捷卡片** → 点击供应商卡片，自动填充除 API Key 外的所有信息

---

## 改造点总览

```
改造前：
  顶部栏: [Logo] [SessionTabs] [📋历史] [☰工具下拉(导航+运行时)] [⚙设置]

改造后：
  顶部栏: [Logo] [SessionTabs] [📋历史] [🧩工具箱(NavLink→/tools)]
  ChatInput 底部: [AUTO|THINK|PLAN] [Agent:xxx] [☐Think] [☐Stream]  ... [Enter发送]
  /tools 页面: 左右布局（6个导航项 | 详情面板）
```

---

## 任务拆分

### F1: ChatInput — 添加运行时配置栏

**文件**: `webview/src/features/chat/components/ChatInput.tsx`

在输入框底部 hint 区域**替换**为运行时配置控件：

```
原来: Press Enter to send, Shift+Enter for new line
改为: [ModeSwitcher] [AgentSelector] [ThinkingToggle] [StreamingToggle] ··· Enter发送
```

- 左侧：ModeSwitcher + AgentSelector + ThinkingToggle + StreamingToggle（复用现有组件）
- 右侧：快捷键提示文字
- 样式：`flex items-center justify-between px-4 pb-3`

---

### F2: AppLayout — 顶部栏简化

**文件**: `webview/src/main/components/AppLayout.tsx`

**改动**：
1. `TAB_ROUTES` 加入 `/tools`，工具页保留 SessionTabs
2. 移除 `ToolbarDropdown` 组件引用
3. 移除单独的 Settings NavLink
4. 替换为单个 NavLink → `/tools`（工具箱图标）

```
顶部栏: [Logo] [SessionTabs(flex-1)] [📋历史] [🧩工具箱]
```

---

### F3: Router — 路由整合

**文件**: `webview/src/main/router.tsx`

**改动**：
- 移除独立路由：`/skills`、`/agents`、`/mcp`、`/settings`
- 新增路由：`/tools` → `ToolsView`（统一工具/设置页面）

```diff
  children: [
    { index: true, element: <ChatView /> },
    { path: 'history', element: <LazySessionHistoryView /> },
-   { path: 'skills', ... },
-   { path: 'agents', ... },
-   { path: 'mcp', ... },
-   { path: 'settings', ... },
+   { path: 'tools', element: <ToolsView /> },
  ]
```

---

### F4: ProviderQuickCards — 供应商快捷卡片（新组件）

**文件**: `webview/src/features/model/components/ProviderQuickCards.tsx`（新建）

**视觉参考**：截图中卡片网格布局

**结构**：
```
grid grid-cols-3 gap-2
  → button.card × 6（anthropic/openai/deepseek/glm/minimax/custom）
     ├── 供应商名称 (text-sm font-medium)
     └── 简短描述 (text-xs text-muted-foreground)
```

**Props**：
```tsx
{ selectedProvider: string; onSelect: (id: string) => void }
```

**选中态**：`border-primary ring-1 ring-primary bg-primary/5`
**非选中**：`border-border hover:border-muted-foreground`

---

### F5: ModelConfigPanel — 集成供应商快捷卡片

**文件**: `webview/src/features/model/components/ModelConfigPanel.tsx`

**改动**：
- 顶部添加 `ProviderQuickCards`，替换原有 `ProviderSelector` 下拉框
- 保留级联逻辑 `updateField`（已实现）
- 保留 JSON 编辑器、模型映射、API Key、Base URL、Thinking Budget、保存按钮

**面板顺序**：
```
1. ProviderQuickCards（卡片网格） ← 新增
2. JSON 一键配置（折叠）
3. Per-Mode 模型映射
4. 当前激活模型
5. API Key
6. API Base URL
7. Thinking Budget
8. 保存按钮
```

---

### F6: ToolsView — 统一工具/设置页面（新页面）

**文件**: `webview/src/main/pages/ToolsView.tsx`（新建）

**左右布局**：

**左侧导航** (w-48, border-r)：
| 序号 | ID | 标签 | 图标 | 右侧面板内容 |
|------|-----|------|------|-------------|
| 1 | `skills` | 技能管理 | ⭐ | `<SkillsManager />` |
| 2 | `agents` | Agent 管理 | 🖥️ | `<AgentsManager />` |
| 3 | `mcp` | MCP Server | 🌐 | `<McpServerManager />` |
| 4 | `provider` | 供应商配置 | 🔧 | `<ModelConfigPanel />` |
| 5 | `theme` | 主题设置 | 🎨 | ThemeSwitcher + 亮度/饱和度 + ThemeEditor |
| 6 | `about` | 关于 | ℹ️ | App info |

**右侧面板** (flex-1, overflow-y-auto, p-6)：
- 根据 `activeSection` 渲染对应组件
- 默认选中 `skills`

**关键点**：
- 内部状态管理（`useState`），不用嵌套路由
- 复用现有独立页面组件（SkillsManager、AgentsManager、McpServerManager、ModelConfigPanel、ThemeSwitcher、ThemeEditor）
- 从 SettingsView 迁移主题设置和关于部分

---

### F7: ToolbarDropdown — 删除

**文件**: `webview/src/main/components/ToolbarDropdown.tsx`

运行时配置已移至 ChatInput，导航已移至 ToolsView，此组件不再需要。

---

## 执行顺序

```
F4 (ProviderQuickCards 新组件)
 → F5 (ModelConfigPanel 集成卡片)
 → F6 (ToolsView 统一页面)
 → F1 (ChatInput 运行时配置)
 → F2 (AppLayout 简化)
 → F3 (Router 整合)
 → F7 (删除 ToolbarDropdown)
 → 编译验证
```

每步完成后 `npx tsc --noEmit` 验证类型。

---

## 验证清单

- [ ] ChatInput 底部显示 Mode/Agent/Think/Stream 控件
- [ ] 顶部栏只有 3 个按钮：历史、工具箱
- [ ] 工具箱 NavLink 跳转到 /tools 页面
- [ ] /tools 页面 SessionTabs 可见，可点击返回聊天
- [ ] 左侧 6 个导航项切换正常
- [ ] Skills/Agents/MCP 管理功能在新布局中正常
- [ ] 供应商卡片网格展示，点击级联填充（除 API Key）
- [ ] JSON 配置级联更新正常
- [ ] 主题设置和关于在新布局中正常

## 后端任务

**无需修改** — 所有 bridge 方法已存在。
