# Phase 4: 会话管理系统 (Session Management)

**优先级**: P1
**预估工期**: 14人天 (3周)
**前置依赖**: Phase 1 + Phase 2 + Phase 3 全部完成（消息组件/流式输出/输入组件可用）
**阶段目标**: 多会话Tab管理，会话搜索/导出，会话切换延迟<100ms

---

## 1. 阶段概览

本阶段构建完整的会话生命周期管理：

1. 多会话Tab组件（拖拽排序 + 上下文隔离）
2. 会话搜索（防抖 + 过滤 + 后端搜索API）
3. 会话导入/导出（Markdown/PDF格式）
4. 会话持久化（通过Java后端存储）

**完成标志**: 支持10+并发会话，Tab切换延迟<100ms，搜索响应<500ms

**与后端协作点**:
- 后端Phase 4的 `MultiSessionManager` 提供会话CRUD API（`createSession`/`switchSession`/`deleteSession`）
- 会话数据持久化由后端负责，前端通过 `window.ccBackend` 调用API
- 会话搜索由后端执行（`searchSessions(query)`），前端负责UI和过滤条件传递
- 导出/导入由后端生成文件，前端触发下载

---

## 2. 任务清单

### Week 10: 多会话管理与Tab切换

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T4-W10-01** | SessionTabs组件 | 1.5人天 | SessionTabs.tsx | Tab切换<100ms |
| **T4-W10-02** | TabItem组件 | 1人天 | TabItem.tsx | Tab显示正常，右键菜单可用 |
| **T4-W10-03** | Tab拖拽排序 | 1.5人天 | useTabDrag.ts | @dnd-kit拖拽正常 |
| **T4-W10-04** | 会话切换动画 | 1人天 | TabSwitchAnimation.tsx | 动画流畅 |
| **T4-W10-05** | 会话上下文隔离 | 1.5人天 | sessionStore.ts增强 | 状态隔离正常 |
| **T4-W10-06** | 会话持久化 | 1人天 | sessionPersistence.ts | 持久化正常 |

### Week 11: 会话搜索与导入导出

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T4-W11-01** | SessionSearch组件 | 1.5人天 | SessionSearch.tsx | 搜索响应<500ms |
| **T4-W11-02** | 搜索过滤器 | 1人天 | SearchFilters.tsx | 类型/日期过滤正常 |
| **T4-W11-03** | 会话导出Markdown | 1人天 | exportToMarkdown.ts | 导出.md正常 |
| **T4-W11-04** | 会话导出PDF | 1.5人天 | exportToPDF.ts | PDF生成正常 |
| **T4-W11-05** | 会话导入 | 1人天 | importSession.ts | 导入正常 |
| **T4-W11-06** | 会话历史记录 | 0.5人天 | SessionHistory.tsx | 历史显示正常 |

---

## 3. Week 10: 多会话管理与Tab切换

### T4-W10-01: SessionTabs组件

**任务描述**: 多会话Tab管理组件，支持拖拽排序、新建、关闭

**实现代码**:

```typescript
// src/features/session/components/SessionTabs.tsx
import { useRef, memo } from 'react';
import { useAppStore } from '@/shared/stores/appStore';
import { SessionTab } from './SessionTab';
import { NewSessionButton } from './NewSessionButton';
import { DndContext, closestCenter, type DragEndEvent } from '@dnd-kit/core';
import { SortableContext, horizontalListSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { ChatSession } from '@/shared/types';

/**
 * 会话标签栏
 *
 * 使用@dnd-kit实现Tab拖拽排序。
 * Tab切换通过appStore.switchSession()触发后端switchSession API。
 */
export const SessionTabs = memo(() => {
  const { sessions, currentSessionId, switchSession, reorderSessions } = useAppStore();
  const tabsRef = useRef<HTMLDivElement>(null);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      const oldIndex = sessions.findIndex(s => s.id === active.id);
      const newIndex = sessions.findIndex(s => s.id === over.id);
      const newSessions = [...sessions];
      const [removed] = newSessions.splice(oldIndex, 1);
      newSessions.splice(newIndex, 0, removed);
      reorderSessions(newSessions);
    }
  };

  return (
    <div className="flex items-center gap-1 border-b px-2 bg-background">
      <DndContext collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext
          items={sessions.map(s => s.id)}
          strategy={horizontalListSortingStrategy}
        >
          <div className="flex items-center gap-1 overflow-x-auto" ref={tabsRef}>
            {sessions.map((session) => (
              <SortableTab
                key={session.id}
                session={session}
                isActive={session.id === currentSessionId}
                onClick={() => switchSession(session.id)}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>
      <NewSessionButton />
    </div>
  );
});

SessionTabs.displayName = 'SessionTabs';

// 可排序Tab包装器
const SortableTab = ({ session, isActive, onClick }: {
  session: ChatSession;
  isActive: boolean;
  onClick: () => void;
}) => {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: session.id
  });
  
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 1 : 0
  };

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <SessionTab session={session} isActive={isActive} onClick={onClick} />
    </div>
  );
};
```

**验收标准**:
- ✅ Tab切换延迟 < 100ms
- ✅ 拖拽排序正常工作
- ✅ 新建/关闭会话正常
- ✅ 横向溢出可滚动

---

### T4-W10-05: 会话上下文隔离

**任务描述**: 增强sessionStore，支持多会话状态隔离

**实现代码**:

```typescript
// src/shared/stores/sessionStore.ts (增强)
// 本实现为 Phase 4 增强版，替换 Phase 1 的基础版 sessionStore
import { create } from 'zustand';
import type { ChatSession, ChatMessage, ID, SessionType } from '@/shared/types';

interface SessionStateMap {
  [sessionId: string]: {
    messages: ChatMessage[];
    inputText: string;
    scrollPosition: number;
    attachments: File[];
  };
}

interface SessionStoreState {
  // 会话列表
  sessions: ChatSession[];
  currentSessionId: ID | null;
  
  // 按会话ID隔离的状态
  sessionStates: SessionStateMap;
  
  // 操作
  switchSession: (sessionId: ID) => void;
  addMessage: (sessionId: ID, message: ChatMessage) => void;
  updateMessages: (sessionId: ID, messages: ChatMessage[]) => void;
  setInputText: (sessionId: ID, text: string) => void;
  setScrollPosition: (sessionId: ID, position: number) => void;
  getSessionState: (sessionId: ID) => SessionStateMap[string] | undefined;
  createSession: (name: string, type: SessionType) => Promise<ChatSession>;
  deleteSession: (sessionId: ID) => void;
}

export const useSessionStore = create<SessionStoreState>((set, get) => ({
  sessions: [],
  currentSessionId: null,
  sessionStates: {},

  switchSession: (sessionId) => {
    // 保存当前会话的滚动位置
    const currentId = get().currentSessionId;
    if (currentId) {
      // scrollPosition由组件上报
    }
    
    set({ currentSessionId: sessionId });
    
    // 通知后端切换会话
    window.ccBackend?.switchSession(sessionId);
  },

  addMessage: (sessionId, message) => {
    set((state) => ({
      sessionStates: {
        ...state.sessionStates,
        [sessionId]: {
          ...state.sessionStates[sessionId],
          messages: [...(state.sessionStates[sessionId]?.messages || []), message]
        }
      }
    }));
  },

  updateMessages: (sessionId, messages) => {
    set((state) => ({
      sessionStates: {
        ...state.sessionStates,
        [sessionId]: {
          ...state.sessionStates[sessionId],
          messages
        }
      }
    }));
  },

  setInputText: (sessionId, text) => {
    set((state) => ({
      sessionStates: {
        ...state.sessionStates,
        [sessionId]: {
          ...state.sessionStates[sessionId],
          inputText: text
        }
      }
    }));
  },

  setScrollPosition: (sessionId, position) => {
    set((state) => ({
      sessionStates: {
        ...state.sessionStates,
        [sessionId]: {
          ...state.sessionStates[sessionId],
          scrollPosition: position
        }
      }
    }));
  },

  getSessionState: (sessionId) => {
    return get().sessionStates[sessionId];
  },

  createSession: async (name, type) => {
    const session = await window.ccBackend?.createSession(name, type);
    if (session) {
      set((state) => ({
        sessions: [...state.sessions, session],
        sessionStates: {
          ...state.sessionStates,
          [session.id]: {
            messages: [],
            inputText: '',
            scrollPosition: 0,
            attachments: []
          }
        }
      }));
      get().switchSession(session.id);
    }
    return session;
  },

  deleteSession: (sessionId) => {
    set((state) => {
      const { [sessionId]: _, ...remainingStates } = state.sessionStates;
      return {
        sessions: state.sessions.filter(s => s.id !== sessionId),
        sessionStates: remainingStates
      };
    });
    window.ccBackend?.deleteSession(sessionId);
  }
}));
```

**验收标准**:
- ✅ 切换会话时消息/输入框/滚动位置独立保持
- ✅ 10+并发会话无状态串扰
- ✅ 删除会话时清理对应状态

---

## 4. Week 11: 会话搜索与导入导出

### T4-W11-01: SessionSearch组件

**实现代码**:

```typescript
// src/features/session/components/SessionSearch.tsx
import { useState, useEffect, useMemo, memo } from 'react';
import { Input } from '@/shared/components/ui/input';
import { Button } from '@/shared/components/ui/button';
import { useDebounce } from '@/shared/hooks/useDebounce';
import { cn } from '@/shared/utils/cn';
import type { ChatSession } from '@/shared/types';

interface SearchFilters {
  type: 'all' | 'project' | 'global' | 'temporary';
  dateRange?: { start: Date; end: Date };
}

/**
 * 会话搜索组件
 *
 * 使用useDebounce防抖，300ms延迟后调用后端searchSessions API。
 * 支持按类型和日期范围过滤。
 */
export const SessionSearch = memo(({ onSessionClick }: { onSessionClick?: (session: ChatSession) => void }) => {
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<SearchFilters>({ type: 'all' });
  const [results, setResults] = useState<ChatSession[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  
  const debouncedQuery = useDebounce(query, 300);

  useEffect(() => {
    if (!debouncedQuery.trim()) {
      setResults([]);
      return;
    }

    setIsSearching(true);
    
    window.ccBackend?.searchSessions(debouncedQuery).then((sessions) => {
      const filtered = sessions.filter((session: ChatSession) => {
        if (filters.type !== 'all' && session.type !== filters.type) return false;
        if (filters.dateRange) {
          const date = new Date(session.createdAt);
          if (date < filters.dateRange.start || date > filters.dateRange.end) return false;
        }
        return true;
      });
      setResults(filtered);
    }).finally(() => {
      setIsSearching(false);
    });
  }, [debouncedQuery, filters]);

  return (
    <div className="p-4">
      {/* 搜索输入框 */}
      <div className="mb-4 flex gap-2">
        <div className="relative flex-1">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="搜索会话内容..."
            className="pl-9"
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              &times;
            </button>
          )}
        </div>
      </div>

      {/* 过滤器 */}
      <div className="mb-4 flex gap-2">
        {(['all', 'project', 'global', 'temporary'] as const).map((type) => (
          <Button
            key={type}
            variant={filters.type === type ? 'default' : 'ghost'}
            size="sm"
            onClick={() => setFilters(prev => ({ ...prev, type }))}
          >
            {type === 'all' ? '全部' : type}
          </Button>
        ))}
      </div>

      {/* 搜索结果 */}
      {isSearching && <div className="text-center text-muted-foreground py-4">搜索中...</div>}
      {!isSearching && results.length === 0 && query && (
        <div className="text-center text-muted-foreground py-4">无搜索结果</div>
      )}
      {results.map((session) => (
        <button
          key={session.id}
          onClick={() => onSessionClick?.(session)}
          className="w-full text-left rounded-md border p-3 hover:bg-accent transition-colors mb-2"
        >
          <div className="font-medium">{session.name}</div>
          <div className="text-sm text-muted-foreground">
            {new Date(session.updatedAt).toLocaleString()}
          </div>
        </button>
      ))}
    </div>
  );
});

SessionSearch.displayName = 'SessionSearch';
```

---

### T4-W11-03: 会话导出Markdown

**实现代码**:

```typescript
// src/features/session/utils/exportToMarkdown.ts
import type { ChatMessage, ChatSession } from '@/shared/types';
import { MessageRole } from '@/shared/types';

/**
 * 将会话导出为Markdown格式
 */
export function exportToMarkdown(session: ChatSession, messages: ChatMessage[]): string {
  const lines: string[] = [];
  
  // 头部
  lines.push(`# ${session.name}`);
  lines.push('');
  lines.push(`> 导出时间: ${new Date().toLocaleString()}`);
  lines.push(`> 会话类型: ${session.type}`);
  lines.push(`> 消息数量: ${messages.length}`);
  lines.push('');
  lines.push('---');
  lines.push('');

  // 消息内容
  for (const message of messages) {
    const timestamp = new Date(message.timestamp).toLocaleString();
    const role = message.role === MessageRole.USER ? '用户' : 'Claude';
    
    lines.push(`### ${role} (${timestamp})`);
    lines.push('');
    lines.push(message.content);
    lines.push('');
    
    // 附件信息（ContentPart 联合类型分支处理）
    if (message.attachments && message.attachments.length > 0) {
      lines.push('**附件:**');
      for (const attachment of message.attachments) {
        if (attachment.type === 'file') {
          lines.push(`- ${attachment.name} (${attachment.mimeType})`);
        } else if (attachment.type === 'image') {
          lines.push(`- [图片] ${attachment.alt || attachment.url}`);
        } else if (attachment.type === 'text') {
          lines.push(`- [文本] ${attachment.text.substring(0, 50)}${attachment.text.length > 50 ? '...' : ''}`);
        }
      }
      lines.push('');
    }
    
    lines.push('---');
    lines.push('');
  }

  return lines.join('\n');
}

/**
 * 触发文件下载
 */
export function downloadMarkdown(content: string, filename: string): void {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
```

---

## 5. 任务依赖与执行顺序

```
T4.1 多会话管理 (Week 10)
├── T4-W10-01 SessionTabs组件         ← 依赖 Phase2的UI组件 + @dnd-kit
├── T4-W10-02 TabItem组件             ← 依赖 T4-W10-01
├── T4-W10-03 Tab拖拽排序             ← 依赖 T4-W10-02 + @dnd-kit/sortable
├── T4-W10-04 会话切换动画            ← 依赖 T4-W10-01
├── T4-W10-05 会话上下文隔离          ← 依赖 Phase1的sessionStore + Phase3的streamingStore
└── T4-W10-06 会话持久化              ← 依赖 T4-W10-05 + Phase1的JavaBridge

T4.2 搜索与导入导出 (Week 11)
├── T4-W11-01 SessionSearch组件       ← 依赖 Phase1的JavaBridge + useDebounce
├── T4-W11-02 搜索过滤器             ← 依赖 T4-W11-01
├── T4-W11-03 会话导出Markdown       ← 依赖 Phase1的类型定义
├── T4-W11-04 会话导出PDF            ← 依赖 T4-W11-03 + Phase1的JavaBridge
├── T4-W11-05 会话导入               ← 依赖 Phase1的JavaBridge
└── T4-W11-06 会话历史记录           ← 依赖 T4-W10-01
```

**关键路径**: T4-W10-01 → T4-W10-05 → T4-W11-01

---

## 6. 验收标准

### 功能验收
- [ ] 支持10+并发会话，无状态串扰
- [ ] Tab切换延迟 < 100ms
- [ ] Tab拖拽排序正常
- [ ] 会话搜索响应 < 500ms（防抖后）
- [ ] 会话导出Markdown/PDF正常
- [ ] 会话导入正常

### 性能验收
- [ ] 10个会话切换无卡顿
- [ ] 搜索结果列表使用虚拟滚动（100+结果）
- [ ] 会话状态内存占用合理

### 代码质量验收
- [ ] sessionStore状态隔离正确
- [ ] 事件监听正确清理
- [ ] TypeScript类型完整

---

## 7. 文件清单汇总

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `src/features/session/components/SessionTabs.tsx` | 会话标签栏 |
| `src/features/session/components/SessionTab.tsx` | 单个Tab项 |
| `src/features/session/components/NewSessionButton.tsx` | 新建会话按钮 |
| `src/features/session/components/SessionSearch.tsx` | 会话搜索组件 |
| `src/features/session/components/SearchFilters.tsx` | 搜索过滤器 |
| `src/features/session/components/SessionHistory.tsx` | 会话历史记录 |
| `src/features/session/hooks/useTabDrag.ts` | Tab拖拽Hook |
| `src/features/session/utils/exportToMarkdown.ts` | Markdown导出 |
| `src/features/session/utils/exportToPDF.ts` | PDF导出 |
| `src/features/session/utils/importSession.ts` | 会话导入 |
| `src/shared/stores/sessionStore.ts` | 会话状态Store（增强） |

---

## 8. 相关文档

- [总览](./00-overview.md)
- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 2: 核心UI](./02-phase2-core-ui.md)
- [Phase 3: 交互增强](./03-phase3-interaction.md)
- [Phase 5: 生态集成](./05-phase5-ecosystem.md)
- [后端Phase 4: 功能模块](../backend/04-phase4-features.md) ← MultiSessionManager
