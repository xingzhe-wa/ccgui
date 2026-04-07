# Phase 6: 性能优化与测试 (Optimization & Testing)

**优先级**: P1-P2
**预估工期**: 18人天 (3.5周)
**前置依赖**: Phase 1-5 全部完成
**阶段目标**: 性能指标达标，测试覆盖率>80%，无内存泄漏，可发布

---

## 1. 阶段概览

本阶段对所有已完成的模块进行系统性的性能优化和质量保障：

1. 虚拟滚动优化（动态高度计算 + 渲染性能调优）
2. Markdown缓存优化（LRU缓存 + 缓存命中率>80%）
3. 组件/路由懒加载（代码分割 + 预加载策略）
4. 内存泄漏检测与修复（EventBus清理 + 组件生命周期）
5. Bundle优化（tree-shaking + 按需加载）
6. 单元测试 + E2E测试 + 性能测试

**完成标志**: 所有性能指标通过，测试覆盖率>80%，可发布

**性能指标目标**:

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| ToolWindow首次打开 | < 1.2s | 计时器 |
| 消息响应延迟 | < 300ms P95 | 日志时间戳 |
| 流式输出首字延迟 | < 500ms | SSE事件时间戳 |
| Markdown渲染 | < 200ms/条 | performance.now() |
| 主题切换延迟 | < 100ms | CSS变量切换计时 |
| 会话切换延迟 | < 100ms | Store更新计时 |
| 内存占用 | < 500MB | IDEA内存监控 |

---

## 2. 任务清单

### Week 14: 性能优化与内存管理

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T6-W14-01** | 虚拟滚动优化 | 2人天 | MessageList优化 | 支持1000+消息，动态高度 |
| **T6-W14-02** | Markdown缓存优化 | 1.5人天 | markdown-cache.ts | 缓存命中率>80% |
| **T6-W14-03** | 组件懒加载 | 1人天 | React.lazy配置 | 首屏加载<1.2s |
| **T6-W14-04** | 图片懒加载 | 1人天 | ImageLazyLoad.tsx | IntersectionObserver正常 |
| **T6-W14-05** | 内存泄漏检测 | 2人天 | 内存分析报告 | 无内存泄漏 |
| **T6-W14-06** | Bundle优化 | 1.5人天 | 代码分割配置 | Bundle体积优化30%+ |

### Week 15: 集成测试与问题修复

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T6-W15-01** | 单元测试编写 | 2人天 | 组件单元测试 | 覆盖率>80% |
| **T6-W15-02** | E2E测试编写 | 2人天 | E2E测试 | 核心流程覆盖 |
| **T6-W15-03** | 性能测试 | 1.5人天 | 性能报告 | 满足所有性能指标 |
| **T6-W15-04** | 兼容性测试 | 1人天 | 兼容性报告 | IDEA 2025.2+ |
| **T6-W15-05** | Bug修复 | 2人天 | 问题修复 | 阻塞问题清零 |
| **T6-W15-06** | 发布准备 | 0.5人天 | 发布检查清单 | 发布就绪 |

---

## 3. Week 14: 性能优化与内存管理

### T6-W14-01: 虚拟滚动优化

**任务描述**: 优化MessageList虚拟滚动，支持动态高度计算

**实现代码**:

```typescript
// src/shared/hooks/useVirtualList.ts (优化版)
import { useRef, useCallback } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import type { ChatMessage } from '@/shared/types';
import { MessageRole } from '@/shared/types';

/**
 * 根据消息内容估算高度
 *
 * 考虑因素：
 * - 基础高度（头像+间距）
 * - 文本行数
 * - 代码块
 * - 附件
 */
const estimateMessageSize = (message: ChatMessage): number => {
  const baseHeight = 80;
  const contentLines = message.content.split('\n').length;
  const lineHeight = 22;
  const hasCodeBlock = message.content.includes('```');
  const hasImage = message.attachments?.some(a => a.type.startsWith('image/')) ?? false;
  const hasTable = message.content.includes('|');
  
  let height = baseHeight + (contentLines * lineHeight);
  
  if (hasCodeBlock) {
    // 代码块额外高度
    const codeBlockCount = (message.content.match(/```/g) || []).length / 2;
    height += codeBlockCount * 200;
  }
  
  if (hasImage) {
    height += 200;
  }
  
  if (hasTable) {
    height += 100;
  }
  
  return Math.max(height, 60);
};

export const useOptimizedVirtualList = (
  messages: ChatMessage[],
  scrollElement: HTMLDivElement | null
) => {
  const measuredSizes = useRef<Map<number, number>>(new Map());

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => scrollElement,
    estimateSize: (index) => {
      // 优先使用已测量的高度
      const measured = measuredSizes.current.get(index);
      if (measured) return measured;
      return estimateMessageSize(messages[index]);
    },
    overscan: 5,
    measureElement: (el) => {
      // 测量实际高度并缓存
      const height = el.getBoundingClientRect().height;
      const index = Number(el.dataset.index);
      measuredSizes.current.set(index, height);
      return height;
    }
  });

  return virtualizer;
};
```

**验收标准**:
- ✅ 1000+消息滚动无卡顿
- ✅ 动态高度计算准确（误差<20%）
- ✅ 已测量元素使用实际高度

---

### T6-W14-02: Markdown缓存优化

**实现代码**:

```typescript
// src/shared/utils/markdown-cache.ts

/**
 * LRU缓存实现
 *
 * 用于缓存Markdown渲染结果，避免重复解析相同内容。
 * TTL机制防止缓存过期数据。
 */
class LRUCache<T> {
  private cache = new Map<string, { value: T; timestamp: number }>();
  private readonly maxSize: number;
  private readonly ttl: number;

  constructor(options: { max: number; ttl: number }) {
    this.maxSize = options.max;
    this.ttl = options.ttl;
  }

  get(key: string): T | undefined {
    const entry = this.cache.get(key);
    if (!entry) return undefined;

    // 检查过期
    if (Date.now() - entry.timestamp > this.ttl) {
      this.cache.delete(key);
      return undefined;
    }

    // 移到末尾（最近访问）
    this.cache.delete(key);
    this.cache.set(key, entry);
    return entry.value;
  }

  set(key: string, value: T): void {
    // 如果已存在，先删除
    this.cache.delete(key);

    // 如果超过最大容量，删除最老的
    if (this.cache.size >= this.maxSize) {
      const firstKey = this.cache.keys().next().value;
      if (firstKey !== undefined) {
        this.cache.delete(firstKey);
      }
    }

    this.cache.set(key, { value, timestamp: Date.now() });
  }

  has(key: string): boolean {
    return this.get(key) !== undefined;
  }

  clear(): void {
    this.cache.clear();
  }

  get size(): number {
    return this.cache.size;
  }
}

// Markdown渲染缓存实例
export const markdownCache = new LRUCache<React.ReactNode>({
  max: 100,
  ttl: 5 * 60 * 1000 // 5分钟
});
```

---

### T6-W14-03: 组件懒加载

**实现代码**:

```typescript
// src/main/lazy-components.ts
import { lazy } from 'react';

/**
 * 懒加载组件
 *
 * 使用React.lazy实现代码分割，仅在需要时加载。
 * 搭配Suspense使用，显示loading状态。
 */
export const LazySkillsManager = lazy(() =>
  import('@/features/skills/components/SkillsManager').then(m => ({ default: m.SkillsManager }))
);

export const LazyAgentsManager = lazy(() =>
  import('@/features/agents/components/AgentsManager').then(m => ({ default: m.AgentsManager }))
);

export const LazyMcpServerManager = lazy(() =>
  import('@/features/mcp/components/McpServerManager').then(m => ({ default: m.McpServerManager }))
);

export const LazyThemeEditor = lazy(() =>
  import('@/features/theme/components/ThemeEditor').then(m => ({ default: m.ThemeEditor }))
);

export const LazySessionSearch = lazy(() =>
  import('@/features/session/components/SessionSearch').then(m => ({ default: m.SessionSearch }))
);
```

**Vite构建优化**:

```typescript
// vite.config.ts (优化版)
export default defineConfig({
  // ...
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom'],
          'vendor-router': ['react-router-dom'],
          'vendor-state': ['zustand'],
          'vendor-markdown': ['react-markdown', 'remark-gfm', 'remark-math', 'rehype-katex'],
          'vendor-highlight': ['highlight.js'],
          'vendor-virtual': ['@tanstack/react-virtual'],
          'vendor-dnd': ['@dnd-kit/core', '@dnd-kit/sortable']
        }
      }
    },
    // 启用压缩
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true, // 生产环境移除console.log
        drop_debugger: true
      }
    }
  }
});
```

---

### T6-W14-04: 图片懒加载

**实现代码**:

```typescript
// src/shared/components/ui/LazyImage.tsx
import { useState, useEffect, useRef, memo } from 'react';
import { cn } from '@/shared/utils/cn';

interface LazyImageProps {
  src: string;
  alt: string;
  className?: string;
  rootMargin?: string;
}

/**
 * 懒加载图片组件
 *
 * 使用IntersectionObserver检测可见性。
 * 进入可视区域100px范围内时开始加载。
 * 加载完成前显示占位符。
 */
export const LazyImage = memo(({ src, alt, className, rootMargin = '100px' }: LazyImageProps) => {
  const [imageSrc, setImageSrc] = useState<string | undefined>();
  const [isLoaded, setIsLoaded] = useState(false);
  const [hasError, setHasError] = useState(false);
  const imgRef = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const element = imgRef.current;
    if (!element) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !imageSrc) {
          setImageSrc(src);
        }
      },
      { rootMargin }
    );

    observer.observe(element);
    return () => observer.disconnect();
  }, [src, rootMargin, imageSrc]);

  return (
    <div ref={imgRef} className={cn('relative', className)}>
      {!isLoaded && !hasError && (
        <div className="absolute inset-0 animate-pulse bg-muted rounded" />
      )}
      {hasError && (
        <div className="flex items-center justify-center h-32 bg-muted rounded text-muted-foreground text-sm">
          图片加载失败
        </div>
      )}
      {imageSrc && (
        <img
          src={imageSrc}
          alt={alt}
          loading="lazy"
          onLoad={() => setIsLoaded(true)}
          onError={() => setHasError(true)}
          className={cn(
            'max-w-full h-auto rounded transition-opacity',
            isLoaded ? 'opacity-100' : 'opacity-0'
          )}
        />
      )}
    </div>
  );
});

LazyImage.displayName = 'LazyImage';
```

---

### T6-W14-05: 内存泄漏检测

**检测清单**:

```typescript
/**
 * 内存泄漏检测清单
 *
 * 以下场景需要逐一检查：
 */

// 1. EventBus监听器清理
// ✅ 所有useStreaming/useHotkeys中的事件订阅必须在useEffect cleanup中取消
// 检测方法：在组件挂载/卸载后调用 eventBus.getListenerCount()

// 2. 定时器清理
// ✅ setInterval/setTimeout必须在useEffect cleanup中clearInterval/clearTimeout
// 检测方法：Chrome DevTools -> Performance -> 检查Timer列

// 3. DOM引用清理
// ✅ useRef引用的DOM元素在组件卸载后自动清理
// ✅ ResizeObserver/IntersectionObserver必须在cleanup中disconnect()

// 4. Zustand Store订阅
// ✅ zustand的useStore hook内部管理订阅，组件卸载时自动取消
// ⚠️ 手动使用store.subscribe()的必须在cleanup中unsubscribe

// 5. JCEF Browser
// ⚠️ JBCefBrowser实例必须调用dispose()释放C++资源
// 检测方法：IDEA内存监控，检查CefBrowser对象数量

// 6. React组件内存泄漏模式
// ✅ 闭包中引用大对象：使用useRef代替useState存储大对象
// ✅ 未取消的fetch请求：使用AbortController
// ✅ 状态更新到已卸载组件：使用isMounted标记
```

---

## 4. Week 15: 集成测试与问题修复

### T6-W15-01: 单元测试编写

**测试框架**: Vitest + @testing-library/react + jsdom

**测试示例**:

```typescript
// src/features/chat/components/__tests__/MessageItem.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { MessageItem } from '../MessageItem';
import { MessageRole } from '@/shared/types';

describe('MessageItem', () => {
  const mockProps = {
    id: 'msg-1',
    role: MessageRole.ASSISTANT,
    content: 'Hello, **World**!',
    timestamp: Date.now(),
    onReply: vi.fn(),
    onCopy: vi.fn(),
    onDelete: vi.fn()
  };

  it('should render message content', () => {
    render(<MessageItem {...mockProps} />);
    expect(screen.getByText(/Hello/)).toBeInTheDocument();
  });

  it('should render user message with reversed layout', () => {
    const { container } = render(
      <MessageItem {...mockProps} role={MessageRole.USER} />
    );
    expect(container.querySelector('.flex-row-reverse')).toBeInTheDocument();
  });

  it('should call onCopy when copy button clicked', async () => {
    render(<MessageItem {...mockProps} />);
    
    const messageItem = screen.getByTestId('message-item');
    fireEvent.mouseEnter(messageItem);
    
    const copyButton = await screen.findByRole('button', { name: /copy/i });
    fireEvent.click(copyButton);
    
    expect(mockProps.onCopy).toHaveBeenCalledWith('Hello, **World**!');
  });

  it('should show streaming cursor when isStreaming=true', () => {
    render(<MessageItem {...mockProps} isStreaming={true} />);
    expect(screen.getByTestId('typing-cursor')).toBeInTheDocument();
  });
});
```

```typescript
// src/shared/utils/__tests__/sse-parser.test.ts
import { describe, it, expect } from 'vitest';
import { SSEParser } from '../sse-parser';

describe('SSEParser', () => {
  it('should parse single event', () => {
    const parser = new SSEParser();
    const events = parser.parse('data: hello\n\n');
    expect(events).toHaveLength(1);
    expect(events[0].data).toBe('hello\n');
  });

  it('should parse event with type', () => {
    const parser = new SSEParser();
    const events = parser.parse('event: delta\ndata: {"text":"hi"}\n\n');
    expect(events).toHaveLength(1);
    expect(events[0].type).toBe('delta');
    expect(events[0].data).toBe('{"text":"hi"}\n');
  });

  it('should handle partial chunks', () => {
    const parser = new SSEParser();
    
    const events1 = parser.parse('data: hel');
    expect(events1).toHaveLength(0);
    
    const events2 = parser.parse('lo\n\n');
    expect(events2).toHaveLength(1);
    expect(events2[0].data).toBe('hello\n');
  });

  it('should skip comment lines', () => {
    const parser = new SSEParser();
    const events = parser.parse(': this is a comment\ndata: test\n\n');
    expect(events).toHaveLength(1);
    expect(events[0].data).toBe('test\n');
  });

  it('should concatenate multiple data fields', () => {
    const parser = new SSEParser();
    const events = parser.parse('data: line1\ndata: line2\n\n');
    expect(events).toHaveLength(1);
    expect(events[0].data).toBe('line1\nline2\n');
  });
});
```

---

### T6-W15-02: E2E测试

**测试框架**: Playwright（非JCEF环境下的浏览器测试）

**测试示例**:

```typescript
// e2e/chat.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Chat Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000');
  });

  test('should send a message and display it', async ({ page }) => {
    // 输入消息
    const input = page.getByPlaceholder(/输入消息/);
    await input.fill('Hello, Claude!');
    
    // 发送
    await page.keyboard.press('Enter');
    
    // 验证消息显示
    await expect(page.getByText('Hello, Claude!')).toBeVisible();
  });

  test('should switch themes', async ({ page }) => {
    // 打开主题切换器
    await page.getByRole('button', { name: /theme/i }).click();
    
    // 选择Monokai主题
    await page.getByText('Monokai').click();
    
    // 验证主题已应用
    const themeAttr = await page.getAttribute('data-theme', 'html');
    expect(themeAttr).toBe('monokai');
  });

  test('should handle file drop', async ({ page }) => {
    // 模拟文件拖拽（需要构造DataTransfer）
    const dropZone = page.getByText(/拖拽文件/);
    await expect(dropZone).toBeVisible();
  });
});
```

---

## 5. 任务依赖与执行顺序

```
T6.1 性能优化 (Week 14)
├── T6-W14-01 虚拟滚动优化           ← 依赖 Phase2的MessageList
├── T6-W14-02 Markdown缓存优化       ← 依赖 Phase2的MarkdownRenderer
├── T6-W14-03 组件懒加载             ← 依赖 Phase5的所有管理器组件
├── T6-W14-04 图片懒加载             ← 依赖 Phase3的多模态输入
├── T6-W14-05 内存泄漏检测           ← 依赖 Phase1-5全部组件
└── T6-W14-06 Bundle优化             ← 依赖 T6-W14-03

T6.2 集成测试 (Week 15)
├── T6-W15-01 单元测试编写           ← 依赖 Phase1-5全部组件
├── T6-W15-02 E2E测试编写            ← 依赖 Phase1-5全部功能
├── T6-W15-03 性能测试               ← 依赖 T6-W14-01~06
├── T6-W15-04 兼容性测试             ← 依赖 T6-W15-01
├── T6-W15-05 Bug修复                ← 依赖 T6-W15-01~04
└── T6-W15-06 发布准备               ← 依赖 T6-W15-05
```

**关键路径**: T6-W14-05 → T6-W15-01 → T6-W15-05 → T6-W15-06

---

## 6. 验收标准

### 性能验收
- [ ] ToolWindow首次打开 < 1.2s
- [ ] 消息响应延迟 < 300ms P95
- [ ] 流式输出首字延迟 < 500ms
- [ ] Markdown渲染 < 200ms/条
- [ ] 主题切换延迟 < 100ms
- [ ] 会话切换延迟 < 100ms
- [ ] 内存占用 < 500MB
- [ ] Markdown缓存命中率 > 80%

### 测试验收
- [ ] 单元测试覆盖率 > 80%
- [ ] E2E测试覆盖核心用户流程
- [ ] 性能测试报告通过
- [ ] 兼容性测试通过（IDEA 2025.2+）
- [ ] 无阻塞级Bug

### 代码质量验收
- [ ] 无内存泄漏
- [ ] 无console.log残留（生产构建）
- [ ] Bundle体积合理（< 2MB gzipped）
- [ ] 所有TypeScript类型完整

---

## 7. 文件清单汇总

### 新增/修改文件

| 文件路径 | 说明 |
|----------|------|
| `src/shared/hooks/useVirtualList.ts` | 优化版虚拟滚动Hook |
| `src/shared/utils/markdown-cache.ts` | Markdown LRU缓存 |
| `src/shared/components/ui/LazyImage.tsx` | 懒加载图片组件 |
| `src/main/lazy-components.ts` | React.lazy懒加载配置 |
| `vite.config.ts` | 构建优化配置（修改） |
| `src/features/chat/components/__tests__/MessageItem.test.tsx` | MessageItem单元测试 |
| `src/shared/utils/__tests__/sse-parser.test.ts` | SSE解析器单元测试 |
| `src/shared/stores/__tests__/streamingStore.test.ts` | 流式Store单元测试 |
| `e2e/chat.spec.ts` | 聊天流程E2E测试 |
| `e2e/theme.spec.ts` | 主题切换E2E测试 |
| `e2e/session.spec.ts` | 会话管理E2E测试 |

---

## 8. 相关文档

- [总览](./00-overview.md)
- [技术架构设计](./10-architecture.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 2: 核心UI](./02-phase2-core-ui.md)
- [Phase 3: 交互增强](./03-phase3-interaction.md)
- [Phase 4: 会话管理](./04-phase4-session.md)
- [Phase 5: 生态集成](./05-phase5-ecosystem.md)
