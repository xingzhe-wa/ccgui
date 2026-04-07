# ClaudeCodeJet 前端开发路线图

**文档版本**: 1.0
**创建日期**: 2026-04-08
**负责人**: Frontend Team
**目标周期**: 15周

---

## 📋 总体规划

### 开发阶段概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ClaudeCodeJet 前端开发路线图                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Phase 1: 基础架构搭建 (3周)                                                 │
│  ├─ Week 1: 项目初始化与脚手架                                               │
│  ├─ Week 2: JCEF环境搭建与通信桥接                                           │
│  └─ Week 3: 状态管理与路由系统                                               │
│                                                                              │
│  Phase 2: 核心UI组件 (3周)                                                   │
│  ├─ Week 4: 主题系统与布局框架                                               │
│  ├─ Week 5: 消息组件与Markdown渲染                                           │
│  └─ Week 6: 输入组件与附件处理                                               │
│                                                                              │
│  Phase 3: 交互增强系统 (3周)                                                 │
│  ├─ Week 7: 流式输出引擎                                                     │
│  ├─ Week 8: 交互式请求引擎                                                   │
│  └─ Week 9: 多模态输入与快捷操作                                             │
│                                                                              │
│  Phase 4: 会话管理系统 (2周)                                                 │
│  ├─ Week 10: 多会话管理与Tab切换                                            │
│  └─ Week 11: 会话搜索与导入导出                                              │
│                                                                              │
│  Phase 5: 生态集成 (2周)                                                     │
│  ├─ Week 12: Skills/Agents管理器                                            │
│  └─ Week 13: MCP服务器管理器                                                │
│                                                                              │
│  Phase 6: 性能优化与测试 (2周)                                               │
│  ├─ Week 14: 性能优化与内存管理                                              │
│  └─ Week 15: 集成测试与问题修复                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 阶段交付物

| 阶段 | 主要交付物 | 验收标准 |
|------|-----------|----------|
| **Phase 1** | 项目脚手架、JCEF通信桥接、状态管理框架 | 可通过UI发送"Hello"并收到回复 |
| **Phase 2** | 主题系统、消息列表、输入组件 | 可正常显示消息列表和发送消息 |
| **Phase 3** | 流式输出、交互式请求、多模态输入 | 支持打字机效果和图片上传 |
| **Phase 4** | 多会话管理、搜索、导入导出 | 支持10+并发会话 |
| **Phase 5** | Skills/Agents/MCP管理器UI | 可配置生态组件 |
| **Phase 6** | 性能优化报告、测试报告 | 满足所有性能指标 |

---

## 📊 里程碑计划

### Milestone 1: 基础架构完成 (Week 3)
- ✅ React 18 + TypeScript 项目搭建完成
- ✅ JCEF浏览器初始化与生命周期管理
- ✅ Java↔JS双向通信桥接
- ✅ Zustand状态管理框架
- ✅ React Router路由配置

### Milestone 2: 核心UI完成 (Week 6)
- ✅ 6套预设主题 + 主题编辑器
- ✅ 响应式布局（800-1200px）
- ✅ 消息列表 + Markdown渲染
- ✅ 输入框 + 附件预览

### Milestone 3: 交互增强完成 (Week 9)
- ✅ SSE流式输出 + 打字机效果
- ✅ 交互式请求引擎UI
- ✅ 多模态输入（图片/附件）
- ✅ 代码快捷操作

### Milestone 4: 会话管理完成 (Week 11)
- ✅ 多会话Tab切换
- ✅ 虚拟滚动消息列表
- ✅ 会话搜索与过滤
- ✅ 会话导入/导出

### Milestone 5: 生态集成完成 (Week 13)
- ✅ Skills管理器UI
- ✅ Agents管理器UI
- ✅ MCP服务器管理器UI
- ✅ 作用域管理UI

### Milestone 6: 发布准备 (Week 15)
- ✅ 性能优化（启动<1.2s，渲染<200ms）
- ✅ 内存优化（<500MB）
- ✅ 单元测试覆盖率>80%
- ✅ E2E测试通过

---

## 🔧 技术栈确认

### 核心框架
```json
{
  "react": "^18.3.1",
  "react-dom": "^18.3.1",
  "typescript": "^5.3.3",
  "zustand": "^4.5.0",
  "react-router-dom": "^6.21.1"
}
```

### UI与样式
```json
{
  "@radix-ui/react-tabs": "^1.0.4",
  "@radix-ui/react-dialog": "^1.0.5",
  "@radix-ui/react-dropdown-menu": "^2.0.6",
  "tailwindcss": "^3.4.1",
  "class-variance-authority": "^0.7.0",
  "clsx": "^2.1.0",
  "tailwind-merge": "^2.2.0"
}
```

### Markdown与代码高亮
```json
{
  "react-markdown": "^9.0.1",
  "remark-gfm": "^4.0.0",
  "rehype-katex": "^7.0.0",
  "remark-math": "^6.0.0",
  "highlight.js": "^11.9.0",
  "react-syntax-highlighter": "^15.5.0"
}
```

### 虚拟滚动
```json
{
  "@tanstack/react-virtual": "^3.0.1"
}
```

### 拖拽
```json
{
  "@dnd-kit/core": "^6.1.0",
  "@dnd-kit/sortable": "^8.0.0",
  "@dnd-kit/utilities": "^3.2.2"
}
```

### 工具库
```json
{
  "date-fns": "^3.0.6",
  "nanoid": "^5.0.4",
  "lodash-es": "^4.17.21",
  "use-debounce": "^10.0.0"
}
```

---

## 📁 项目目录结构

```
src/
├── main/                           # 主入口
│   ├── index.tsx                   # React入口
│   ├── App.tsx                     # 根组件
│   └── main.css                    # 全局样式
│
├── shared/                         # 共享模块
│   ├── components/                 # 共享组件
│   │   ├── ui/                     # 基础UI组件
│   │   │   ├── button/             # 按钮组件
│   │   │   ├── input/              # 输入框组件
│   │   │   ├── dialog/             # 对话框组件
│   │   │   ├── dropdown/           # 下拉菜单组件
│   │   │   └── tabs/               # 标签页组件
│   │   ├── layout/                 # 布局组件
│   │   │   ├── ChatLayout.tsx      # 聊天布局
│   │   │   └── ResponsiveLayout.tsx # 响应式布局
│   │   └── markdown/               # Markdown组件
│   │       ├── MarkdownRenderer.tsx
│   │       ├── CodeBlock.tsx
│   │       └── LatexRenderer.tsx
│   │
│   ├── hooks/                      # 共享Hooks
│   │   ├── useJavaBridge.ts        # Java通信Hook
│   │   ├── useTheme.ts             # 主题Hook
│   │   ├── useDebounce.ts          # 防抖Hook
│   │   ├── useVirtualList.ts       # 虚拟列表Hook
│   │   └── useStreaming.ts         # 流式输出Hook
│   │
│   ├── stores/                     # 全局Store
│   │   ├── appStore.ts             # 应用状态Store
│   │   ├── sessionStore.ts         # 会话状态Store
│   │   ├── themeStore.ts           # 主题状态Store
│   │   └── configStore.ts          # 配置状态Store
│   │
│   ├── types/                      # 类型定义
│   │   ├── index.ts                # 统一导出
│   │   ├── chat.ts                 # 聊天相关类型
│   │   ├── session.ts              # 会话相关类型
│   │   ├── theme.ts                # 主题相关类型
│   │   ├── ecosystem.ts            # 生态相关类型
│   │   └── bridge.ts               # 通信桥接类型
│   │
│   ├── utils/                      # 工具函数
│   │   ├── java-bridge.ts          # Java通信封装
│   │   ├── event-bus.ts            # 事件总线
│   │   ├── storage.ts              # 本地存储
│   │   ├── markdown-parser.ts      # Markdown解析
│   │   └── cn.ts                   # 类名合并工具
│   │
│   └── constants/                  # 常量定义
│       ├── themes.ts               # 主题常量
│       ├── keybindings.ts          # 快捷键绑定
│       └── config.ts               # 配置常量
│
├── features/                       # 功能模块
│   ├── chat/                       # 聊天功能
│   │   ├── components/             # 聊天组件
│   │   │   ├── ChatWindow.tsx      # 聊天窗口
│   │   │   ├── MessageList.tsx     # 消息列表
│   │   │   ├── MessageItem.tsx     # 消息项
│   │   │   ├── ChatInput.tsx       # 输入框
│   │   │   └── AttachmentPreview.tsx # 附件预览
│   │   ├── hooks/                  # 聊天Hooks
│   │   │   ├── useSendMessage.ts   # 发送消息Hook
│   │   │   └── useStreaming.ts     # 流式输出Hook
│   │   └── index.ts                # 入口
│   │
│   ├── session/                    # 会话管理
│   │   ├── components/
│   │   │   ├── SessionTabs.tsx     # 会话标签
│   │   │   ├── SessionSearch.tsx   # 会话搜索
│   │   │   └── SessionExport.tsx   # 会话导出
│   │   └── hooks/
│   │       ├── useSessionManager.ts
│   │       └── useSessionSwitch.ts
│   │
│   ├── interaction/                # 交互增强
│   │   ├── components/
│   │   │   ├── PromptOptimizer.tsx # 提示词优化
│   │   │   ├── InteractiveQuestion.tsx # 交互式问题
│   │   │   └── MultimodalInput.tsx # 多模态输入
│   │   └── hooks/
│   │
│   ├── streaming/                  # 流式输出
│   │   ├── components/
│   │   │   ├── StreamingMessage.tsx
│   │   │   └── TypingCursor.tsx
│   │   └── hooks/
│   │       └── useSSE.ts
│   │
│   ├── theme/                      # 主题系统
│   │   ├── components/
│   │   │   ├── ThemeSwitcher.tsx   # 主题切换器
│   │   │   └── ThemeEditor.tsx     # 主题编辑器
│   │   └── hooks/
│   │       └── useTheme.ts
│   │
│   ├── skills/                     # Skills管理
│   │   ├── components/
│   │   │   ├── SkillsList.tsx
│   │   │   ├── SkillCard.tsx
│   │   │   └── SkillEditor.tsx
│   │   └── hooks/
│   │
│   ├── agents/                     # Agents管理
│   │   ├── components/
│   │   │   ├── AgentsList.tsx
│   │   │   ├── AgentCard.tsx
│   │   │   └── AgentEditor.tsx
│   │   └── hooks/
│   │
│   ├── mcp/                        # MCP管理
│   │   ├── components/
│   │   │   ├── McpServersList.tsx
│   │   │   ├── McpServerCard.tsx
│   │   │   └── McpServerConfig.tsx
│   │   └── hooks/
│   │
│   └── tasks/                      # 任务进度
│       ├── components/
│       │   ├── TaskProgress.tsx
│       │   ├── StepItem.tsx
│       │   └── ProgressBar.tsx
│       └── hooks/
│
├── lib/                            # 核心库
│   ├── java-bridge.ts              # Java通信封装
│   ├── event-bus.ts                # 事件总线
│   └── storage.ts                  # 本地存储
│
└── styles/                         # 样式文件
    ├── globals.css                 # 全局样式
    ├── themes/                     # 主题样式
    │   ├── jetbrains-dark.css
    │   ├── github-dark.css
    │   └── ...
    └── components.css              # 组件样式
```

---

## 🔄 开发流程规范

### 分支策略
```
main (生产分支)
  ├── develop (开发分支)
  │   ├── feature/chat-basic (功能分支)
  │   ├── feature/streaming (功能分支)
  │   └── feature/theme-system (功能分支)
  └── release/* (发布分支)
```

### 提交规范
```
feat: 新功能
fix: 修复
docs: 文档
style: 格式
refactor: 重构
perf: 性能优化
test: 测试
chore: 构建/工具

示例: feat(chat): 实现消息列表组件
```

### Code Review清单
- [ ] TypeScript类型完整
- [ ] 组件使用memo优化
- [ ] 事件处理使用useCallback
- [ ] 复杂计算使用useMemo
- [ ] 副作用正确清理
- [ ] 无内存泄漏风险
- [ ] 响应式设计完整
- [ ] 无障碍属性完善
- [ ] 性能指标达标

---

## 📈 进度跟踪

### 每周检查点
- **周一**: 任务分配与技术讨论
- **周三**: 中期进度检查
- **周五**: 代码审查与Demo演示

### 风险预警
- **黄色预警**: 任务延期超过2天
- **橙色预警**: 任务延期超过5天
- **红色预警**: 关键路径任务延期

---

## 📚 参考文档

- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 2: 核心UI](./02-phase2-core-ui.md)
- [Phase 3: 交互增强](./03-phase3-interaction.md)
- [Phase 4: 会话管理](./04-phase4-session.md)
- [Phase 5: 生态集成](./05-phase5-ecosystem.md)
- [Phase 6: 性能优化](./06-phase6-optimization.md)
