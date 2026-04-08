/**
 * ClaudeCodeJet 前端类型定义导出索引
 *
 * 所有类型必须从 @/@shared/types 导入
 * 类型引用优先级: 11-types.md > 10-architecture.md > 12-components.md > Phase 文档
 */

// ============== 基础类型 ==============

/**
 * 通用ID类型
 */
export type ID = string;

/**
 * 时间戳类型（毫秒）
 */
export type Timestamp = number;

/**
 * 提取类型（美化类型显示）
 */
export type Prettify<T> = {
  [K in keyof T]: T[K];
};

/**
 * 深度可选类型
 */
export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

/**
 * 深度只读类型
 */
export type DeepReadonly<T> = {
  readonly [P in keyof T]: T[P] extends object ? DeepReadonly<T[P]> : T[P];
};

// ============== 工具类型 ==============

/**
 * 函数类型
 */
export type Fn<Args extends any[] = any[], Result = any> = (...args: Args) => Result;

/**
 * 异步函数类型
 */
export type AsyncFn<Args extends any[] = any[], Result = any> = (...args: Args) => Promise<Result>;

/**
 * 事件处理器类型
 */
export type EventHandler<T = any> = (event: T) => void;

/**
 * 类名类型
 */
export type ClassValue = ClassArray | ClassDictionary | string | number | null | boolean | undefined;

/**
 * 类名数组
 */
interface ClassArray extends Array<ClassValue> {}

/**
 * 类名字典
 */
interface ClassDictionary {
  [id: string]: any;
}

/**
 * 可空类型
 */
export type Nullable<T> = T | null;

// ============== 类型导出 ==============

// 聊天相关
export * from './chat';

// 会话相关
export * from './session';

// 主题相关
export * from './theme';

// 生态相关
export * from './ecosystem';

// 交互相关
export * from './interaction';

// 任务相关
export * from './task';

// 通信相关
export * from './bridge';

// UI相关
export * from './ui';
