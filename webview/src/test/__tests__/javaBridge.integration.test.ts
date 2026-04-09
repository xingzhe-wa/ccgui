/**
 * javaBridge.integration.test.ts - JavaBridge 集成测试
 *
 * 测试 Java ↔ JavaScript 通信的集成场景。
 * 重点验证 API 方法调用正确性和参数传递。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { javaBridge } from '@/lib/java-bridge';

describe('JavaBridge Integration', () => {
  let mockSend: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    // 覆盖 window.ccBackend.send 来跟踪调用
    mockSend = vi.fn();
    (window.ccBackend as any).send = mockSend;

    vi.clearAllMocks();
  });

  describe('初始化', () => {
    it('应该成功初始化', async () => {
      await javaBridge.init();
      expect(true).toBe(true); // 如果没有抛出错误，说明初始化成功
    });

    it('多次初始化不应该报错', async () => {
      await javaBridge.init();
      await javaBridge.init();
      await javaBridge.init();
      expect(true).toBe(true);
    });
  });

  describe('消息发送 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('sendMessage 应该发送正确的参数', () => {
      javaBridge.sendMessage('Hello World');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'sendMessage',
        params: { message: 'Hello World' },
      });
    });

    it('sendMultimodalMessage 应该发送正确的参数', () => {
      const message = { text: 'Test', attachments: [] };
      javaBridge.sendMultimodalMessage(message);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'sendMultimodalMessage',
        params: { message },
      });
    });
  });

  describe('流式输出 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('streamMessage 应该发送但不等待响应', () => {
      javaBridge.streamMessage('Streaming content');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'streamMessage',
        params: { message: 'Streaming content' },
      });
    });

    it('cancelStreaming 应该发送正确的参数', () => {
      javaBridge.cancelStreaming('session-123');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'cancelStreaming',
        params: { sessionId: 'session-123' },
      });
    });
  });

  describe('配置 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('getConfig 应该发送正确的参数', () => {
      javaBridge.getConfig('theme');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'getConfig',
        params: { key: 'theme' },
      });
    });

    it('setConfig 应该发送正确的参数', () => {
      javaBridge.setConfig('key', 'value');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'setConfig',
        params: { key: 'key', value: 'value' },
      });
    });

    it('updateConfig 应该发送正确的参数', () => {
      const config = { theme: 'dark', language: 'en' };
      javaBridge.updateConfig(config);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'updateConfig',
        params: { config },
      });
    });
  });

  describe('主题 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('getThemes 应该发送正确的请求', () => {
      javaBridge.getThemes();

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'getThemes',
        params: undefined,
      });
    });

    it('updateTheme 应该发送正确的参数', () => {
      const theme = { id: 'custom-dark', colors: {} };
      javaBridge.updateTheme(theme);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'updateTheme',
        params: { theme },
      });
    });

    it('saveCustomTheme 应该发送正确的参数', () => {
      const theme = { id: 'my-theme', name: 'My Theme' };
      javaBridge.saveCustomTheme(theme);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'saveCustomTheme',
        params: { theme },
      });
    });

    it('deleteCustomTheme 应该发送正确的参数', () => {
      javaBridge.deleteCustomTheme('theme-123');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'deleteCustomTheme',
        params: { themeId: 'theme-123' },
      });
    });
  });

  describe('会话管理 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('createSession 应该发送正确的参数', () => {
      javaBridge.createSession('New Session', 'CHAT');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'createSession',
        params: { name: 'New Session', type: 'CHAT' },
      });
    });

    it('switchSession 应该发送正确的参数', () => {
      javaBridge.switchSession('session-abc');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'switchSession',
        params: { sessionId: 'session-abc' },
      });
    });

    it('deleteSession 应该发送正确的参数', () => {
      javaBridge.deleteSession('session-xyz');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'deleteSession',
        params: { sessionId: 'session-xyz' },
      });
    });

    it('searchSessions 应该发送正确的参数', () => {
      javaBridge.searchSessions('test query');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'searchSessions',
        params: { query: 'test query' },
      });
    });

    it('exportSession 应该发送正确的参数', () => {
      javaBridge.exportSession('session-1', 'markdown');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'exportSession',
        params: { sessionId: 'session-1', format: 'markdown' },
      });
    });

    it('importSession 应该发送正确的参数', () => {
      const data = '{"id":"test","name":"Test"}';
      javaBridge.importSession(data);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'importSession',
        params: { data },
      });
    });
  });

  describe('技能 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('executeSkill 应该发送正确的参数', () => {
      javaBridge.executeSkill('skill-1', { context: 'data' });

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'executeSkill',
        params: { skillId: 'skill-1', context: { context: 'data' } },
      });
    });

    it('getSkills 应该发送正确的请求', () => {
      javaBridge.getSkills();

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'getSkills',
        params: undefined,
      });
    });

    it('saveSkill 应该发送正确的参数', () => {
      const skill = { id: 'skill-1', name: 'Test Skill' };
      javaBridge.saveSkill(skill);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'saveSkill',
        params: { skill },
      });
    });

    it('deleteSkill 应该发送正确的参数', () => {
      javaBridge.deleteSkill('skill-to-delete');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'deleteSkill',
        params: { skillId: 'skill-to-delete' },
      });
    });
  });

  describe('Agent API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('startAgent 应该发送正确的参数', () => {
      javaBridge.startAgent('agent-1', { task: 'Do something' });

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'startAgent',
        params: { agentId: 'agent-1', task: { task: 'Do something' } },
      });
    });

    it('stopAgent 应该发送正确的参数', () => {
      javaBridge.stopAgent('agent-2');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'stopAgent',
        params: { agentId: 'agent-2' },
      });
    });

    it('getAgents 应该发送正确的请求', () => {
      javaBridge.getAgents();

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'getAgents',
        params: undefined,
      });
    });

    it('saveAgent 应该发送正确的参数', () => {
      const agent = { id: 'agent-1', name: 'Test Agent' };
      javaBridge.saveAgent(agent);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'saveAgent',
        params: { agent },
      });
    });

    it('deleteAgent 应该发送正确的参数', () => {
      javaBridge.deleteAgent('agent-delete');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'deleteAgent',
        params: { agentId: 'agent-delete' },
      });
    });
  });

  describe('MCP 服务器 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('startMcpServer 应该发送正确的参数', () => {
      javaBridge.startMcpServer('server-1');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'startMcpServer',
        params: { serverId: 'server-1' },
      });
    });

    it('stopMcpServer 应该发送正确的参数', () => {
      javaBridge.stopMcpServer('server-2');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'stopMcpServer',
        params: { serverId: 'server-2' },
      });
    });

    it('testMcpServer 应该发送正确的参数', () => {
      javaBridge.testMcpServer('server-3');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'testMcpServer',
        params: { serverId: 'server-3' },
      });
    });

    it('getMcpServers 应该发送正确的请求', () => {
      javaBridge.getMcpServers();

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'getMcpServers',
        params: undefined,
      });
    });

    it('saveMcpServer 应该发送正确的参数', () => {
      const server = { id: 'server-1', name: 'Test Server' };
      javaBridge.saveMcpServer(server);

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'saveMcpServer',
        params: { server },
      });
    });

    it('deleteMcpServer 应该发送正确的参数', () => {
      javaBridge.deleteMcpServer('server-delete');

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'deleteMcpServer',
        params: { serverId: 'server-delete' },
      });
    });
  });

  describe('交互式问题 API', () => {
    beforeEach(async () => {
      await javaBridge.init();
    });

    it('submitAnswer 应该发送正确的参数', () => {
      javaBridge.submitAnswer('question-1', { answer: 'Yes' });

      expect(mockSend).toHaveBeenCalledWith({
        queryId: expect.any(Number),
        action: 'submitAnswer',
        params: { questionId: 'question-1', answer: { answer: 'Yes' } },
      });
    });
  });
});
