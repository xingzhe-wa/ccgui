# Phase 2-6: 前端开发计划概要

**文档版本**: 1.0
**创建日期**: 2026-04-08

---

## 📊 各阶段概览

```
Phase 1: 基础架构搭建 ✅ (详见 01-phase1-foundation.md)
├─ Week 1: 项目初始化与脚手架
├─ Week 2: JCEF环境搭建与通信桥接
└─ Week 3: 状态管理与路由系统

Phase 2: 核心UI组件 (3周)
├─ Week 4: 主题系统与布局框架
├─ Week 5: 消息组件与Markdown渲染
└─ Week 6: 输入组件与附件处理

Phase 3: 交互增强系统 (3周)
├─ Week 7: 流式输出引擎
├─ Week 8: 交互式请求引擎
└─ Week 9: 多模态输入与快捷操作

Phase 4: 会话管理系统 (2周)
├─ Week 10: 多会话管理与Tab切换
└─ Week 11: 会话搜索与导入导出

Phase 5: 生态集成 (2周)
├─ Week 12: Skills/Agents管理器
└─ Week 13: MCP服务器管理器

Phase 6: 性能优化与测试 (2周)
├─ Week 14: 性能优化与内存管理
└─ Week 15: 集成测试与问题修复
```

---

## Phase 2: 核心UI组件 (Week 4-6)

### Week 4: 主题系统与布局框架

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T2-W4-01 | 6套预设主题CSS变量定义 | 1人天 | themes/*.css |
| T2-W4-02 | ThemeConfig数据结构 | 0.5人天 | theme.ts类型 |
| T2-W4-03 | 主题切换器组件 | 1人天 | ThemeSwitcher.tsx |
| T2-W4-04 | 主题编辑器UI | 2人天 | ThemeEditor.tsx |
| T2-W4-05 | 响应式布局实现 | 1.5人天 | ResponsiveLayout.tsx |
| T2-W4-06 | ChatLayout主布局 | 1.5人天 | ChatLayout.tsx |

**核心实现**:

```typescript
// 主题系统核心组件
const ThemeSwitcher = () => {
  const { theme, setTheme, customThemes } = useThemeStore();
  const [isOpen, setIsOpen] = useState(false);

  return (
    <Dropdown open={isOpen} onOpenChange={setIsOpen}>
      <DropdownTrigger>
        <Button variant="ghost" size="icon">
          <PaletteIcon />
        </Button>
      </DropdownTrigger>
      <DropdownContent align="end" className="w-56">
        <DropdownLabel>预设主题</DropdownLabel>
        {Object.values(ThemePresets).map((preset) => (
          <DropdownItem
            key={preset.id}
            onClick={() => setTheme(preset)}
            isSelected={theme.id === preset.id}
          >
            <div className="flex items-center gap-2">
              <ThemePreview theme={preset} />
              <span>{preset.name}</span>
            </div>
          </DropdownItem>
        ))}
        {customThemes.length > 0 && (
          <>
            <DropdownSeparator />
            <DropdownLabel>自定义主题</DropdownLabel>
            {customThemes.map((custom) => (
              <DropdownItem
                key={custom.id}
                onClick={() => setTheme(custom)}
              >
                {custom.name}
              </DropdownItem>
            ))}
          </>
        )}
        <DropdownSeparator />
        <DropdownItem onClick={() => setIsOpen(false)}>
          <Link to="/settings/theme">主题编辑器</Link>
        </DropdownItem>
      </DropdownContent>
    </Dropdown>
  );
};
```

### Week 5: 消息组件与Markdown渲染

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T2-W5-01 | MessageItem基础组件 | 1人天 | MessageItem.tsx |
| T2-W5-02 | react-markdown集成 | 1人天 | MarkdownRenderer.tsx |
| T2-W5-03 | highlight.js代码高亮 | 1.5人天 | CodeBlock.tsx |
| T2-W5-04 | KaTeX公式渲染 | 1人天 | LatexRenderer.tsx |
| T2-W5-05 | MessageList虚拟滚动 | 2人天 | MessageList.tsx |
| T2-W5-06 | 消息操作菜单 | 1人天 | MessageActions.tsx |

**虚拟滚动实现**:

```typescript
import { useVirtualizer } from '@tanstack/react-virtual';

const MessageList = ({ messages }) => {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 100,
    overscan: 5
  });

  return (
    <div ref={parentRef} className="h-full overflow-auto">
      <div style={{ height: `${virtualizer.getTotalSize()}px` }}>
        {virtualizer.getVirtualItems().map((virtualItem) => (
          <div
            key={virtualItem.key}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${virtualItem.start}px)`
            }}
          >
            <MessageItem message={messages[virtualItem.index]} />
          </div>
        ))}
      </div>
    </div>
  );
};
```

### Week 6: 输入组件与附件处理

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T2-W6-01 | ChatInput组件 | 1.5人天 | ChatInput.tsx |
| T2-W6-02 | TextInput自动高度 | 1人天 | AutoResizeTextarea.tsx |
| T2-W6-03 | 附件拖拽处理 | 1.5人天 | AttachmentDropZone.tsx |
| T2-W6-04 | 图片预览组件 | 1人天 | ImagePreview.tsx |
| T2-W6-05 | 发送按钮与快捷键 | 1人天 | SendButton.tsx |
| T2-W6-06 | 输入框工具栏 | 1人天 | InputToolbar.tsx |

---

## Phase 3: 交互增强系统 (Week 7-9)

### Week 7: 流式输出引擎

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T3-W7-01 | SSE事件解析器 | 1.5人天 | sse-parser.ts |
| T3-W7-02 | useStreaming Hook | 1人天 | useStreaming.ts |
| T3-W7-03 | StreamingMessage组件 | 1.5人天 | StreamingMessage.tsx |
| T3-W7-04 | 打字机效果动画 | 1人天 | TypingCursor.tsx |
| T3-W7-05 | 停止生成按钮 | 0.5人天 | StopButton.tsx |
| T3-W7-06 | 流式输出状态管理 | 1人天 | streamingStore.ts |

**SSE解析实现**:

```typescript
class SSEParser {
  parse(chunk: string): SSEEvent[] {
    const events: SSEEvent[] = [];
    const lines = chunk.split('\n');

    let currentEvent: Partial<SSEEvent> = {};

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        currentEvent.data = line.slice(6);
      } else if (line.startsWith('event: ')) {
        currentEvent.type = line.slice(7);
      } else if (line === '') {
        if (currentEvent.data) {
          events.push({
            type: currentEvent.type || 'message',
            data: currentEvent.data
          });
          currentEvent = {};
        }
      }
    }

    return events;
  }
}
```

### Week 8: 交互式请求引擎

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T3-W8-01 | InteractiveQuestionPanel | 2人天 | InteractiveQuestionPanel.tsx |
| T3-W8-02 | SingleChoiceOptions | 1人天 | SingleChoiceOptions.tsx |
| T3-W8-03 | MultipleChoiceOptions | 1人天 | MultipleChoiceOptions.tsx |
| T3-W8-04 | TextInput选项 | 0.5人天 | TextInput.tsx |
| T3-W8-05 | Confirmation选项 | 0.5人天 | ConfirmationOptions.tsx |
| T3-W8-06 | 问题状态管理 | 1人天 | questionStore.ts |

### Week 9: 多模态输入与快捷操作

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T3-W9-01 | 文件拖拽处理 | 1人天 | useFileDrop.ts |
| T3-W9-02 | 图片粘贴处理 | 1人天 | useImagePaste.ts |
| T3-W9-03 | 文件解析器 | 1.5人天 | file-parser.ts |
| T3-W9-04 | 附件预览管理 | 1人天 | AttachmentManager.tsx |
| T3-W9-05 | 快捷键绑定 | 1人天 | useHotkeys.ts |
| T3-W9-06 | 快捷操作面板 | 1人天 | QuickActionsPanel.tsx |

---

## Phase 4: 会话管理系统 (Week 10-11)

### Week 10: 多会话管理与Tab切换

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T4-W10-01 | SessionTabs组件 | 1.5人天 | SessionTabs.tsx |
| T4-W10-02 | TabItem组件 | 1人天 | TabItem.tsx |
| T4-W10-03 | Tab拖拽排序 | 1.5人天 | useTabDrag.ts |
| T4-W10-04 | 会话切换动画 | 1人天 | TabSwitchAnimation.tsx |
| T4-W10-05 | 会话上下文隔离 | 1.5人天 | sessionStore.ts |
| T4-W10-06 | 会话持久化 | 1人天 | sessionPersistence.ts |

### Week 11: 会话搜索与导入导出

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T4-W11-01 | SessionSearch组件 | 1.5人天 | SessionSearch.tsx |
| T4-W11-02 | 搜索过滤器 | 1人天 | SearchFilters.tsx |
| T4-W11-03 | 会话导出Markdown | 1人天 | exportToMarkdown.ts |
| T4-W11-04 | 会话导出PDF | 1.5人天 | exportToPDF.ts |
| T4-W11-05 | 会话导入 | 1人天 | importSession.ts |
| T4-W11-06 | 会话历史记录 | 0.5人天 | SessionHistory.tsx |

---

## Phase 5: 生态集成 (Week 12-13)

### Week 12: Skills/Agents管理器

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T5-W12-01 | Skills管理器UI | 2人天 | SkillsManager.tsx |
| T5-W12-02 | SkillCard组件 | 1人天 | SkillCard.tsx |
| T5-W12-03 | SkillEditor表单 | 2人天 | SkillEditor.tsx |
| T5-W12-04 | Agents管理器UI | 2人天 | AgentsManager.tsx |
| T5-W12-05 | AgentCard组件 | 1人天 | AgentCard.tsx |
| T5-W12-06 | AgentEditor表单 | 2人天 | AgentEditor.tsx |

### Week 13: MCP服务器管理器

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T5-W13-01 | MCP服务器管理器UI | 2人天 | McpServerManager.tsx |
| T5-W13-02 | McpServerCard组件 | 1人天 | McpServerCard.tsx |
| T5-W13-03 | 服务器配置表单 | 1.5人天 | McpServerConfig.tsx |
| T5-W13-04 | 连接状态指示器 | 1人天 | ConnectionStatus.tsx |
| T5-W13-05 | 测试连接功能 | 1人天 | testConnection.ts |
| T5-W13-06 | 作用域管理UI | 1人天 | ScopeManager.tsx |

---

## Phase 6: 性能优化与测试 (Week 14-15)

### Week 14: 性能优化与内存管理

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T6-W14-01 | 虚拟滚动优化 | 2人天 | 虚拟滚动调优 |
| T6-W14-02 | Markdown缓存优化 | 1.5人天 | markdown-cache.ts |
| T6-W14-03 | 组件懒加载 | 1人天 | React.lazy配置 |
| T6-W14-04 | 图片懒加载 | 1人天 | ImageLazyLoad.tsx |
| T6-W14-05 | 内存泄漏检测 | 2人天 | 内存分析报告 |
| T6-W14-06 | Bundle优化 | 1.5人天 | 代码分割优化 |

### Week 15: 集成测试与问题修复

| 任务ID | 任务描述 | 工作量 | 产出物 |
|--------|----------|--------|--------|
| T6-W15-01 | 单元测试编写 | 2人天 | 组件单元测试 |
| T6-W15-02 | E2E测试编写 | 2人天 | Playwright测试 |
| T6-W15-03 | 性能测试 | 1.5人天 | 性能测试报告 |
| T6-W15-04 | 兼容性测试 | 1人天 | 兼容性报告 |
| T6-W15-05 | Bug修复 | 2人天 | 问题修复 |
| T6-W15-06 | 发布准备 | 0.5人天 | 发布检查清单 |

---

## 🎯 整体验收标准

### 功能完整性
- [ ] 所有5大模块功能100%完成
- [ ] 43个前端任务全部完成
- [ ] 17个P0核心任务通过验收

### 性能指标
- [ ] ToolWindow首次打开 < 1.2s
- [ ] 消息响应延迟 < 300ms P95
- [ ] 流式输出首字延迟 < 500ms
- [ ] Markdown渲染 < 200ms/条
- [ ] 主题切换延迟 < 100ms
- [ ] 内存占用 < 500MB

### 代码质量
- [ ] TypeScript覆盖率100%
- [ ] ESLint错误0个
- [ ] 单元测试覆盖率 > 80%
- [ ] 组件文档完整

---

## 📚 相关文档

- [总体路线图](./00-roadmap.md)
- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
