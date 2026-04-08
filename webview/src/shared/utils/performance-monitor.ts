/**
 * performance-monitor.ts - 性能监控工具
 *
 * 提供性能测量和监控功能。
 *
 * @module performance-monitor
 */

/**
 * 性能测量结果
 */
export interface PerformanceMeasure {
  name: string;
  duration: number;
  startTime: number;
}

/**
 * 性能标记
 */
export interface PerformanceMark {
  name: string;
  startTime: number;
}

/**
 * 性能计时器类
 *
 * 用于测量代码执行时间。
 */
export class PerformanceTimer {
  private startTime: number;
  private endTime?: number;
  private name: string;

  constructor(name: string) {
    this.name = name;
    this.startTime = performance.now();
  }

  /**
   * 结束计时
   */
  end(): number {
    this.endTime = performance.now();
    return this.endTime - this.startTime;
  }

  /**
   * 获取耗时（毫秒）
   */
  getDuration(): number {
    const end = this.endTime ?? performance.now();
    return end - this.startTime;
  }

  /**
   * 获取计时器名称
   */
  getName(): string {
    return this.name;
  }
}

/**
 * 测量函数执行时间
 *
 * @param name - 测量名称
 * @param fn - 要测量的函数
 * @returns 函数执行结果和耗时
 */
export async function measureAsync<T>(
  name: string,
  fn: () => Promise<T>
): Promise<{ result: T; duration: number }> {
  const timer = new PerformanceTimer(name);
  const result = await fn();
  const duration = timer.end();
  return { result, duration };
}

/**
 * 测量同步函数执行时间
 *
 * @param name - 测量名称
 * @param fn - 要测量的函数
 * @returns 函数执行结果和耗时
 */
export function measure<T>(
  name: string,
  fn: () => T
): { result: T; duration: number } {
  const timer = new PerformanceTimer(name);
  const result = fn();
  const duration = timer.end();
  return { result, duration };
}

/**
 * 创建性能标记
 *
 * @param name - 标记名称
 */
export function mark(name: string): void {
  if (typeof performance !== 'undefined' && performance.mark) {
    performance.mark(name);
  }
}

/**
 * 测量两个标记之间的时间
 *
 * @param name - 测量名称
 * @param startMark - 起始标记
 * @param endMark - 结束标记
 */
export function measureBetweenMarks(
  name: string,
  startMark: string,
  endMark: string
): number | undefined {
  if (typeof performance !== 'undefined' && performance.measure) {
    try {
      performance.measure(name, startMark, endMark);
      const entries = performance.getEntriesByName(name, 'measure');
      if (entries.length > 0) {
        const duration = entries[0]?.duration;
        if (duration !== undefined) {
          // 清理测量条目
          performance.clearMarks(startMark);
          performance.clearMarks(endMark);
          performance.clearMeasures(name);
          return duration;
        }
      }
    } catch {
      // 标记不存在时忽略错误
    }
  }
  return undefined;
}

/**
 * 获取性能指标
 *
 * @returns 性能指标对象
 */
export function getPerformanceMetrics(): {
  navigationTiming: PerformanceNavigationTiming | null;
  resourceTiming: PerformanceResourceTiming[];
  marks: PerformanceMark[];
  measures: PerformanceMeasure[];
} {
  if (typeof performance === 'undefined') {
    return {
      navigationTiming: null,
      resourceTiming: [],
      marks: [],
      measures: []
    };
  }

  const navEntries = performance.getEntriesByType('navigation');
  const navEntry = navEntries.length > 0 ? navEntries[0] : null;
  return {
    navigationTiming: navEntry ? (navEntry as PerformanceNavigationTiming) : null,
    resourceTiming: Array.from(performance.getEntriesByType('resource')) as PerformanceResourceTiming[],
    marks: Array.from(performance.getEntriesByType('mark')) as unknown as PerformanceMark[],
    measures: Array.from(performance.getEntriesByType('measure')) as unknown as PerformanceMeasure[]
  };
}

/**
 * 获取最近的性能测量
 *
 * @param type - 性能条目类型
 * @param name - 过滤名称（可选）
 * @returns 性能条目数组
 */
export function getRecentEntries(
  type: string,
  name?: string
): PerformanceEntry[] {
  if (typeof performance === 'undefined') {
    return [];
  }

  const entries = performance.getEntriesByType(type);
  if (name) {
    return entries.filter((e) => e.name === name);
  }
  return entries;
}

/**
 * 格式化时间为可读字符串
 *
 * @param ms - 毫秒数
 * @returns 格式化后的字符串
 */
export function formatDuration(ms: number): string {
  if (ms < 1) {
    return `${(ms * 1000).toFixed(2)}μs`;
  }
  if (ms < 1000) {
    return `${ms.toFixed(2)}ms`;
  }
  return `${(ms / 1000).toFixed(2)}s`;
}

/**
 * 性能指标观察器
 *
 * 用于持续监控特定性能指标。
 * 注意：命名为 PerformanceMetricsObserver 以避免与全局 PerformanceObserver API 冲突。
 */
export class PerformanceMetricsObserver {
  private observers: globalThis.PerformanceObserver[] = [];
  private entries: Map<string, number[]> = new Map();

  /**
   * 观察特定类型的性能条目
   *
   * @param type - 性能条目类型
   * @param callback - 回调函数
   */
  observe(type: string, callback: (entries: PerformanceObserverEntryList) => void): void {
    if (typeof window === 'undefined' || !('PerformanceObserver' in window)) {
      return;
    }

    const observer = new globalThis.PerformanceObserver((list: PerformanceObserverEntryList) => {
      // PerformanceObserverEntryList 使用 getEntries() 方法获取条目数组
      const entries = list.getEntries();
      for (const entry of entries) {
        const duration = entry.duration || 0;
        const name = entry.name;

        if (!this.entries.has(name)) {
          this.entries.set(name, []);
        }
        this.entries.get(name)!.push(duration);
      }
      callback(list);
    });

    observer.observe({ type, buffered: true });
    this.observers.push(observer);
  }

  /**
   * 获取收集的数据
   *
   * @param name - 性能条目名称
   * @returns 数组形式的耗时数据
   */
  getData(name: string): number[] {
    return this.entries.get(name) || [];
  }

  /**
   * 获取统计数据
   *
   * @param name - 性能条目名称
   * @returns 统计信息
   */
  getStats(name: string): { count: number; min: number; max: number; avg: number; p95: number } | null {
    const data = this.getData(name);
    if (data.length === 0) return null;

    const sorted = [...data].sort((a, b) => a - b);
    const sum = data.reduce((a, b) => a + b, 0);
    const length = sorted.length;

    return {
      count: length,
      min: sorted[0] ?? 0,
      max: sorted[length - 1] ?? 0,
      avg: sum / length,
      p95: sorted[Math.floor(length * 0.95)] ?? sorted[length - 1] ?? 0
    };
  }

  /**
   * 断开所有观察
   */
  disconnect(): void {
    for (const observer of this.observers) {
      observer.disconnect();
    }
    this.observers = [];
    this.entries.clear();
  }
}

/**
 * 性能报告生成器
 */
export class PerformanceReport {
  private measures: Map<string, number[]> = new Map();

  /**
   * 记录一次测量
   *
   * @param name - 测量名称
   * @param duration - 耗时（毫秒）
   */
  record(name: string, duration: number): void {
    if (!this.measures.has(name)) {
      this.measures.set(name, []);
    }
    this.measures.get(name)!.push(duration);
  }

  /**
   * 生成报告
   *
   * @returns 性能报告
   */
  generate(): {
    summary: { totalMeasures: number; totalSamples: number };
    details: Array<{ name: string; count: number; min: number; max: number; avg: number; p95: number }>;
  } {
    const details: Array<{
      name: string;
      count: number;
      min: number;
      max: number;
      avg: number;
      p95: number;
    }> = [];

    for (const [name, data] of this.measures.entries()) {
      const sorted = [...data].sort((a, b) => a - b);
      const sum = data.reduce((a, b) => a + b, 0);
      const length = sorted.length;

      details.push({
        name,
        count: length,
        min: sorted[0] ?? 0,
        max: sorted[length - 1] ?? 0,
        avg: sum / length,
        p95: sorted[Math.floor(length * 0.95)] ?? sorted[length - 1] ?? 0
      });
    }

    // 按 P95 排序，找出最慢的操作
    details.sort((a, b) => b.p95 - a.p95);

    return {
      summary: {
        totalMeasures: this.measures.size,
        totalSamples: Array.from(this.measures.values()).reduce((sum, data) => sum + data.length, 0)
      },
      details
    };
  }

  /**
   * 清空数据
   */
  clear(): void {
    this.measures.clear();
  }
}
