/**
 * JCEF 环境专用日志工具
 * 在生产环境保留关键日志，便于调试
 */

/**
 * 后端扩展接口（包含日志所需的额外属性）
 */
interface CcBackendExtended {
  isDevMode?: boolean;
}

/**
 * 获取扩展的后端对象（包含 isDevMode 属性）
 */
function getExtendedBackend(): CcBackendExtended | null {
  if (typeof window === 'undefined') {
    return null;
  }
  return (window.ccBackend as any) || null;
}

export const logger = {
  /**
   * 调试日志 - 仅在开发模式输出
   */
  debug: (...args: any[]) => {
    const backend = getExtendedBackend();
    if (backend?.isDevMode) {
      console.log('[cc-gui debug]', ...args);
    }
  },

  /**
   * 错误日志 - 始终输出，并发送到后端
   */
  error: (...args: any[]) => {
    console.error('[cc-gui error]', ...args);
    if (typeof window !== 'undefined' && window.ccBackend?.send) {
      try {
        // 使用底层 send 方法发送错误日志
        window.ccBackend.send({
          queryId: 0,
          action: 'logError',
          params: { message: args.map(String).join(' ') }
        });
      } catch {
        // 忽略日志发送失败
      }
    }
  },

  /**
   * Bridge 通信日志 - 仅在开发模式输出
   */
  bridge: (action: string, params?: any) => {
    const backend = getExtendedBackend();
    if (backend?.isDevMode) {
      console.log(`[cc-gui bridge] ${action}`, params ?? '');
    }
  },

  /**
   * 信息日志 - 始终输出
   */
  info: (...args: any[]) => {
    console.info('[cc-gui info]', ...args);
  },

  /**
   * 警告日志 - 始终输出
   */
  warn: (...args: any[]) => {
    console.warn('[cc-gui warn]', ...args);
  }
};
