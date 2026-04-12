/**
 * JavaBridge - Java ↔ JavaScript 通信封装
 *
 * 封装 JBCefJSQuery 的底层调用，提供类型安全的异步接口
 * 简化 Bridge: 使用 ccBackend.send(action, params) 格式
 */

// JavaBridge 类实现后端 API 接口
// 注意：send 方法由 window.ccBackend 提供，这里通过类型断言使用
class JavaBridge {
  // 使用字符串类型的 queryId，与后端保持一致
  private pendingRequests = new Map<string, {
    resolve: (value: any) => void;
    reject: (error: Error) => void;
  }>();
  private responseHandler: (() => void) | null = null;
  private _isReady = false;

  /**
   * 检查 Bridge 是否已就绪
   */
  get isReady(): boolean {
    return this._isReady && !!window.ccBackend && !!window.ccEvents;
  }

  /**
   * 生成唯一的 queryId（字符串格式）
   */
  private generateQueryId(): string {
    return `q_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * 通用调用方法
   * 通过 JBCefJSQuery 向 Java 发送请求
   */
  private async invoke<T>(action: string, params?: any): Promise<T> {
    // 如果 bridge 未就绪，抛出明确错误
    if (!this.isReady) {
      console.warn(`[JavaBridge] Bridge not ready for action: ${action}`);
      throw new Error(`Bridge not ready for action: ${action}. Call init() first and wait for isReady to be true.`);
    }

    const queryId = this.generateQueryId();

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
      // 传递 queryId 到 params 中，让后端使用前端生成的 queryId
      try {
        if (window.ccBackend && window.ccBackend.send) {
          const paramsWithQueryId = { ...params, _queryId: queryId };
          window.ccBackend.send(action, paramsWithQueryId);
        } else {
          throw new Error('Bridge send method not available');
        }
      } catch (error) {
        clearTimeout(timeout);
        this.pendingRequests.delete(queryId);
        reject(error);
      }
    });
  }

  /**
   * 初始化：等待 Java 注入并监听响应
   * 返回 Promise，在 bridge 完全就绪后 resolve
   */
  async init(): Promise<void> {
    console.log('[JavaBridge] init() called, waiting for Java bridge injection...');

    // 如果已经 ready，直接返回
    if (this.isReady) {
      console.log('[JavaBridge] Already initialized');
      return;
    }

    // 如果已有监听器，先清理
    if (this.responseHandler) {
      this.responseHandler();
      this.responseHandler = null;
    }

    return new Promise<void>((resolve, reject) => {
      const MAX_WAIT_TIME = 10000; // 10秒最大等待时间
      const CHECK_INTERVAL = 50;
      let elapsed = 0;

      const checkReady = () => {
        console.log(`[JavaBridge] Checking bridge... window.ccBackend=${!!window.ccBackend}, window.ccEvents=${!!window.ccEvents}`);
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
          this._isReady = true;
          console.log('[JavaBridge] Initialized successfully');
          resolve();
        } else {
          elapsed += CHECK_INTERVAL;
          if (elapsed >= MAX_WAIT_TIME) {
            console.error('[JavaBridge] Initialization timeout after', MAX_WAIT_TIME, 'ms');
            reject(new Error(`Bridge initialization timeout after ${MAX_WAIT_TIME}ms`));
          } else {
            // 使用 setTimeout 而非 requestAnimationFrame，更可控
            setTimeout(checkReady, CHECK_INTERVAL);
          }
        }
      };

      // 立即检查一次
      checkReady();
    });
  }

  /**
   * 强制重置 Bridge 状态（用于错误恢复）
   */
  reset(): void {
    this._isReady = false;
    if (this.responseHandler) {
      this.responseHandler();
      this.responseHandler = null;
    }
    // 清理所有 pending 请求
    this.pendingRequests.forEach((pending) => {
      pending.reject(new Error('Bridge reset'));
    });
    this.pendingRequests.clear();
  }

  // ========== 实现 JavaBackendAPI 接口 ==========

  async sendMessage(message: string): Promise<any> {
    return this.invoke('sendMessage', { message });
  }

  async sendMultimodalMessage(message: any): Promise<any> {
    return this.invoke('sendMultimodalMessage', { message });
  }

  streamMessage(message: string): Promise<any> {
    const queryId = this.generateQueryId();

    return new Promise<any>((resolve, reject) => {
      // 设置超时（30秒）
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(queryId);
        reject(new Error(`streamMessage timeout`));
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
      // 简化 Bridge 使用 send(action, params) 格式
      try {
        if (window.ccBackend && window.ccBackend.send) {
          const paramsWithQueryId = { message, _queryId: queryId };
          window.ccBackend.send('streamMessage', paramsWithQueryId);
        } else {
          throw new Error('Bridge not ready');
        }
      } catch (error) {
        clearTimeout(timeout);
        this.pendingRequests.delete(queryId);
        reject(error);
      }
    });
  }

  cancelStreaming(sessionId: string): void {
    if (window.ccBackend && window.ccBackend.send) {
      window.ccBackend.send('cancelStreaming', { sessionId });
    }
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

  async getModelConfig(): Promise<{
    provider: string;
    model: string;
    apiKey: string;
    baseUrl: string;
  }> {
    return this.invoke('getModelConfig', {});
  }

  async updateModelConfig(config: {
    provider?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
  }): Promise<void> {
    return this.invoke('updateModelConfig', config);
  }

  // ---- Editor Integration ----

  async getSelectedText(): Promise<{
    text: string;
    fileName: string;
    language: string;
    hasSelection: boolean;
  }> {
    return this.invoke('getSelectedText', {});
  }

  async replaceSelectedText(text: string): Promise<void> {
    return this.invoke('replaceSelectedText', { text });
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

  async importSkill(json: string): Promise<{ success: boolean; count?: number; error?: string }> {
    return this.invoke('importSkill', { json });
  }

  async exportSkill(skillId: string): Promise<{ success: boolean; json?: string; error?: string }> {
    return this.invoke('exportSkill', { skillId });
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

  // ========== Chat Config ==========

  async getChatConfig(): Promise<{
    conversationMode: string;
    currentAgentId: string | null;
    streamingEnabled: boolean;
  }> {
    return this.invoke('getChatConfig', {});
  }

  async updateChatConfig(config: {
    conversationMode?: string;
    currentAgentId?: string | null;
    streamingEnabled?: boolean;
  }): Promise<void> {
    return this.invoke('updateChatConfig', { config });
  }

  // ========== Task Status ==========

  async getTaskStatus(): Promise<{
    tasks: Array<{
      taskId: string;
      name: string;
      status: string;
      currentStep: number;
      totalSteps: number;
      progress: number;
    }>;
    activeSubagents: Array<{
      agentId: string;
      agentName: string;
      taskId: string;
      startTime: number;
      status: string;
    }>;
    diffRecords: Array<Record<string, never>>;
  }> {
    return this.invoke('getTaskStatus', {});
  }

  // ========== Slash Commands ==========

  async executeSlashCommand(command: string): Promise<{
    success: boolean;
    message?: string;
    error?: string;
  }> {
    return this.invoke('executeSlashCommand', { command });
  }

  // ========== IDE Theme ==========

  async getIdeTheme(): Promise<{ isDark: boolean }> {
    return this.invoke('getIdeTheme', {});
  }

  // ========== Provider Profiles ==========

  async getProviderProfiles(): Promise<{
    profiles: Array<{
      id: string;
      name: string;
      provider: string;
      model: string;
      apiKey: string;
      baseUrl: string;
      sonnetModel: string;
      opusModel: string;
      maxModel: string;
      maxRetries: number;
    }>;
    activeProfileId: string | null;
  }> {
    return this.invoke('getProviderProfiles', {});
  }

  async createProviderProfile(profile: {
    id: string;
    name: string;
    provider?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    sonnetModel?: string;
    opusModel?: string;
    maxModel?: string;
    maxRetries?: number;
  }): Promise<{ success: boolean; id?: string }> {
    return this.invoke('createProviderProfile', profile);
  }

  async updateProviderProfile(profile: {
    id: string;
    name: string;
    provider?: string;
    model?: string;
    apiKey?: string;
    baseUrl?: string;
    sonnetModel?: string;
    opusModel?: string;
    maxModel?: string;
    maxRetries?: number;
  }): Promise<{ success: boolean }> {
    return this.invoke('updateProviderProfile', profile);
  }

  async deleteProviderProfile(profileId: string): Promise<{ success: boolean }> {
    return this.invoke('deleteProviderProfile', { profileId });
  }

  async setActiveProviderProfile(profileId: string | null): Promise<{ success: boolean }> {
    return this.invoke('setActiveProviderProfile', { profileId: profileId ?? '' });
  }
}

// 导出单例
export const javaBridge = new JavaBridge();

// 自动初始化
javaBridge.init().catch((error) => {
  console.error('Failed to initialize Java bridge:', error);
});
