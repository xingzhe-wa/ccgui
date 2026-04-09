/**
 * JavaBridge - Java ↔ JavaScript 通信封装
 *
 * 封装 JBCefJSQuery 的底层调用，提供类型安全的异步接口
 */

import type { JavaBackendAPI } from '@/shared/types';

// JavaBridge 类实现 JavaBackendAPI 接口
// 注意：send 方法由 window.ccBackend 提供，这里通过类型断言使用
class JavaBridge {
  // 实现 JavaBackendAPI 接口所需的方法（实际调用 window.ccBackend）
  private queryId = 0;
  private pendingRequests = new Map<number, {
    resolve: (value: any) => void;
    reject: (error: Error) => void;
  }>();
  private responseHandler: (() => void) | null = null;

  /**
   * 通用调用方法
   * 通过 JBCefJSQuery 向 Java 发送请求
   */
  private async invoke<T>(action: string, params?: any): Promise<T> {
    const queryId = ++this.queryId;

    return new Promise<T>((resolve, reject) => {
      // 设置超时（30秒）
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(queryId);
        reject(new Error(`Java call timeout: ${action}`));
      }, 30000);

      // 存储pending请求
      this.pendingRequests.set(queryId, {
        resolve: (value) => {
          clearTimeout(timeout);
          resolve(value);
        },
        reject: (error) => {
          clearTimeout(timeout);
          reject(error);
        }
      });

      // 调用Java（通过JBCefJSQuery注入的send方法）
      try {
        (window.ccBackend as JavaBackendAPI).send({
          queryId,
          action,
          params
        });
      } catch (error) {
        clearTimeout(timeout);
        this.pendingRequests.delete(queryId);
        reject(error);
      }
    });
  }

  /**
   * 初始化：等待 Java 注入并监听响应
   */
  async init(): Promise<void> {
    // 如果已有监听器，先清理
    if (this.responseHandler) {
      this.responseHandler();
      this.responseHandler = null;
    }

    return new Promise<void>((resolve) => {
      // 等待Java注入完成
      const checkReady = () => {
        if (window.ccBackend && window.ccEvents) {
          // 监听响应事件，并存储取消订阅函数
          this.responseHandler = window.ccEvents.on('response', (data: any) => {
            const { queryId, result, error } = data;
            const pending = this.pendingRequests.get(queryId);

            if (pending) {
              if (error) {
                pending.reject(new Error(error));
              } else {
                pending.resolve(result);
              }
              this.pendingRequests.delete(queryId);
            }
          });
          resolve();
        } else {
          requestAnimationFrame(checkReady);
        }
      };
      checkReady();
    });
  }

  // ========== 实现 JavaBackendAPI 接口 ==========

  async sendMessage(message: string): Promise<any> {
    return this.invoke('sendMessage', { message });
  }

  async sendMultimodalMessage(message: any): Promise<any> {
    return this.invoke('sendMultimodalMessage', { message });
  }

  streamMessage(message: string): void {
    const queryId = ++this.queryId;
    (window.ccBackend as JavaBackendAPI).send({ queryId, action: 'streamMessage', params: { message } });
  }

  cancelStreaming(sessionId: string): void {
    const queryId = ++this.queryId;
    (window.ccBackend as JavaBackendAPI).send({ queryId, action: 'cancelStreaming', params: { sessionId } });
  }

  async getConfig(key: string): Promise<any> {
    return this.invoke('getConfig', { key });
  }

  async setConfig(key: string, value: any): Promise<void> {
    return this.invoke('setConfig', { key, value });
  }

  async updateConfig(config: any): Promise<void> {
    return this.invoke('updateConfig', { config });
  }

  async getThemes(): Promise<any[]> {
    return this.invoke('getThemes');
  }

  async updateTheme(theme: any): Promise<void> {
    return this.invoke('updateTheme', { theme });
  }

  async saveCustomTheme(theme: any): Promise<void> {
    return this.invoke('saveCustomTheme', { theme });
  }

  async deleteCustomTheme(themeId: string): Promise<void> {
    return this.invoke('deleteCustomTheme', { themeId });
  }

  async createSession(name: string, type: any): Promise<any> {
    return this.invoke('createSession', { name, type });
  }

  async switchSession(sessionId: string): Promise<void> {
    return this.invoke('switchSession', { sessionId });
  }

  async deleteSession(sessionId: string): Promise<void> {
    return this.invoke('deleteSession', { sessionId });
  }

  async searchSessions(query: string): Promise<any[]> {
    return this.invoke('searchSessions', { query });
  }

  async exportSession(sessionId: string, format: any): Promise<Blob> {
    return this.invoke('exportSession', { sessionId, format });
  }

  async importSession(data: string): Promise<any> {
    return this.invoke('importSession', { data });
  }

  async executeSkill(skillId: string, context: any): Promise<any> {
    return this.invoke('executeSkill', { skillId, context });
  }

  async getSkills(): Promise<any[]> {
    return this.invoke('getSkills');
  }

  async saveSkill(skill: any): Promise<void> {
    return this.invoke('saveSkill', { skill });
  }

  async deleteSkill(skillId: string): Promise<void> {
    return this.invoke('deleteSkill', { skillId });
  }

  async startAgent(agentId: string, task: any): Promise<void> {
    return this.invoke('startAgent', { agentId, task });
  }

  async stopAgent(agentId: string): Promise<void> {
    return this.invoke('stopAgent', { agentId });
  }

  async getAgents(): Promise<any[]> {
    return this.invoke('getAgents');
  }

  async saveAgent(agent: any): Promise<void> {
    return this.invoke('saveAgent', { agent });
  }

  async deleteAgent(agentId: string): Promise<void> {
    return this.invoke('deleteAgent', { agentId });
  }

  async startMcpServer(serverId: string): Promise<void> {
    return this.invoke('startMcpServer', { serverId });
  }

  async stopMcpServer(serverId: string): Promise<void> {
    return this.invoke('stopMcpServer', { serverId });
  }

  async testMcpServer(serverId: string): Promise<any> {
    return this.invoke('testMcpServer', { serverId });
  }

  async getMcpServers(): Promise<any[]> {
    return this.invoke('getMcpServers');
  }

  async saveMcpServer(server: any): Promise<void> {
    return this.invoke('saveMcpServer', { server });
  }

  async deleteMcpServer(serverId: string): Promise<void> {
    return this.invoke('deleteMcpServer', { serverId });
  }

  async optimizePrompt(prompt: string): Promise<{
    optimizedPrompt: string;
    addedContextCount: number;
    improvements?: string[];
    confidence?: number;
  }> {
    return this.invoke('optimizePrompt', { prompt });
  }

  async submitAnswer(questionId: string, answer: any): Promise<void> {
    return this.invoke('submitAnswer', { questionId, answer });
  }
}

// 导出单例
export const javaBridge = new JavaBridge();

// 自动初始化
javaBridge.init().catch((error) => {
  console.error('Failed to initialize Java bridge:', error);
});
