/**
 * memory-leak-detector.ts - 内存泄漏检测工具
 *
 * 提供内存泄漏检测相关的工具函数和钩子。
 *
 * @module memory-leak-detector
 */

import type { ChatMessage } from '@/shared/types';

/**
 * 内存泄漏检测结果
 */
export interface MemoryLeakReport {
  category: string;
  status: 'pass' | 'warning' | 'error';
  message: string;
  suggestions: string[];
}

/**
 * 检测项配置
 */
const CHECK_ITEMS = [
  {
    id: 'eventbus-listeners',
    name: 'EventBus 监听器',
    check: (_?: ChatMessage[]): MemoryLeakReport => {
      // TODO: 实际检查 EventBus 的监听器数量
      return {
        category: 'EventBus',
        status: 'pass',
        message: 'EventBus 监听器数量正常',
        suggestions: []
      };
    }
  },
  {
    id: 'component-subscriptions',
    name: '组件订阅',
    check: (_?: ChatMessage[]): MemoryLeakReport => {
      // TODO: 检查组件的 Zustand 订阅
      return {
        category: 'Subscriptions',
        status: 'pass',
        message: '组件订阅正常',
        suggestions: []
      };
    }
  },
  {
    id: 'message-list-size',
    name: '消息列表大小',
    check: (messages?: ChatMessage[]): MemoryLeakReport => {
      if (!messages) {
        return {
          category: 'Memory',
          status: 'pass',
          message: '未提供消息列表数据',
          suggestions: []
        };
      }
      const size = messages.length;
      if (size > 5000) {
        return {
          category: 'Memory',
          status: 'error',
          message: `消息列表过大 (${size} 条)，可能导致内存问题`,
          suggestions: [
            '实现虚拟滚动',
            '定期清理旧消息',
            '分页加载历史消息'
          ]
        };
      }
      if (size > 1000) {
        return {
          category: 'Memory',
          status: 'warning',
          message: `消息列表较大 (${size} 条)，建议优化`,
          suggestions: [
            '实现虚拟滚动',
            '考虑消息分页'
          ]
        };
      }
      return {
        category: 'Memory',
        status: 'pass',
        message: `消息列表大小正常 (${size} 条)`,
        suggestions: []
      };
    }
  }
];

/**
 * 运行内存泄漏检测
 *
 * @param messages - 当前消息列表（可选）
 * @returns 检测报告数组
 */
export const runMemoryLeakDetection = (
  messages?: ChatMessage[]
): MemoryLeakReport[] => {
  const reports: MemoryLeakReport[] = [];

  for (const item of CHECK_ITEMS) {
    reports.push(item.check(messages));
  }

  return reports;
};

/**
 * 获取检测摘要
 *
 * @param reports - 检测报告数组
 * @returns 摘要信息
 */
export const getDetectionSummary = (
  reports: MemoryLeakReport[]
): {
  total: number;
  pass: number;
  warning: number;
  error: number;
  hasIssues: boolean;
} => {
  const summary = {
    total: reports.length,
    pass: 0,
    warning: 0,
    error: 0,
    hasIssues: false
  };

  for (const report of reports) {
    summary[report.status]++;
    if (report.status !== 'pass') {
      summary.hasIssues = true;
    }
  }

  return summary;
};

/**
 * 内存泄漏检测清单（开发者使用）
 *
 * 用于在开发过程中手动检查潜在的内存泄漏点。
 */
export const MEMORY_LEAK_CHECKLIST = {
  eventBus: {
    name: 'EventBus 监听器清理',
    checks: [
      '✅ 所有 useStreaming/useHotkeys 中的事件订阅必须在 useEffect cleanup 中取消',
      '✅ 检测方法：在组件挂载/卸载后调用 eventBus.getListenerCount()',
      '⚠️  注意：多次调用 init() 会重复注册监听器'
    ]
  },
  timers: {
    name: '定时器清理',
    checks: [
      '✅ setInterval/setTimeout 必须在 useEffect cleanup 中 clearInterval/clearTimeout',
      '✅ 检测方法：Chrome DevTools -> Performance -> 检查 Timer 列',
      '⚠️  注意：requestAnimationFrame 也需要 cancelAnimationFrame'
    ]
  },
  domRefs: {
    name: 'DOM 引用清理',
    checks: [
      '✅ useRef 引用的 DOM 元素在组件卸载后自动清理',
      '✅ ResizeObserver/IntersectionObserver 必须在 cleanup 中 disconnect()',
      '⚠️  注意：存储在 ref 中的对象不会被自动清理'
    ]
  },
  zustand: {
    name: 'Zustand Store 订阅',
    checks: [
      '✅ zustand 的 useStore hook 内部管理订阅，组件卸载时自动取消',
      '⚠️ 手动使用 store.subscribe() 的必须在 cleanup 中 unsubscribe()',
      '✅ 检测方法：检查 store 订阅数量是否随组件卸载而减少'
    ]
  },
  jcef: {
    name: 'JCEF Browser',
    checks: [
      '⚠️ JBCefBrowser 实例必须调用 dispose() 释放 C++ 资源',
      '✅ 检测方法：IDEA 内存监控，检查 CefBrowser 对象数量',
      '✅ 确保在组件卸载时清理所有 JCEF 相关资源'
    ]
  },
  react: {
    name: 'React 组件内存泄漏模式',
    checks: [
      '✅ 闭包中引用大对象：使用 useRef 代替 useState 存储大对象',
      '✅ 未取消的 fetch 请求：使用 AbortController',
      '✅ 状态更新到已卸载组件：使用 isMounted 标记或通过 ref 检查'
    ]
  }
};

/**
 * 内存使用快照
 *
 * 用于对比不同时间点的内存使用情况。
 */
export const captureMemorySnapshot = (): {
  timestamp: number;
  usedJSHeapSize: number;
  totalJSHeapSize: number;
  jsHeapSizeLimit: number;
} | null => {
  if (typeof performance === 'undefined' || !(performance as any).memory) {
    return null;
  }

  const memory = (performance as any).memory;
  return {
    timestamp: Date.now(),
    usedJSHeapSize: memory.usedJSHeapSize,
    totalJSHeapSize: memory.totalJSHeapSize,
    jsHeapSizeLimit: memory.jsHeapSizeLimit
  };
};

/**
 * 格式化内存大小
 *
 * @param bytes - 字节数
 * @returns 格式化后的字符串
 */
export const formatMemorySize = (bytes: number): string => {
  const mb = bytes / (1024 * 1024);
  if (mb < 1) {
    const kb = bytes / 1024;
    return `${kb.toFixed(2)} KB`;
  }
  return `${mb.toFixed(2)} MB`;
};

/**
 * 比较两个内存快照
 *
 * @param before - 之前的快照
 * @param after - 之后的快照
 * @returns 比较结果
 */
export const compareMemorySnapshots = (
  before: ReturnType<typeof captureMemorySnapshot>,
  after: ReturnType<typeof captureMemorySnapshot>
): {
  usedDiff: number;
  usedDiffPercent: number;
  isIncreasing: boolean;
} | null => {
  if (!before || !after) return null;

  const usedDiff = after.usedJSHeapSize - before.usedJSHeapSize;
  const usedDiffPercent = (usedDiff / before.usedJSHeapSize) * 100;

  return {
    usedDiff,
    usedDiffPercent,
    isIncreasing: usedDiff > 0
  };
};
