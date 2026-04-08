/**
 * appStore.test.ts - appStore 单元测试
 *
 * 测试应用全局状态管理的核心功能。
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { useAppStore } from '../appStore';
import type { ChatSession } from '@/shared/types';
import { SessionType, SessionStatus, TaskStatus } from '@/shared/types';
import { getMockCcBackend, resetJcefMocks } from '@/test/utils/test-utils';

describe('appStore', () => {
  beforeEach(() => {
    // 重置 store 状态
    useAppStore.setState({
      sessions: [],
      currentSessionId: '',
      ui: {
        sidebarOpen: true,
        sidebarWidth: 280,
        previewPanelOpen: false,
        previewPanelWidth: 400,
        activeModal: null,
        modalData: null,
        notifications: [],
        isLoading: false,
        loadingMessage: '',
      },
      activeTaskProgress: null,
      error: null,
    });
    resetJcefMocks();
  });

  describe('初始状态', () => {
    it('应该有正确的初始状态', () => {
      const state = useAppStore.getState();

      expect(state.sessions).toEqual([]);
      expect(state.currentSessionId).toBe('');
      expect(state.ui.sidebarOpen).toBe(true);
      expect(state.ui.previewPanelOpen).toBe(false);
      expect(state.activeTaskProgress).toBeNull();
      expect(state.error).toBeNull();
    });
  });

  describe('会话操作', () => {
    describe('switchSession', () => {
      it('应该切换当前会话 ID', () => {
        const { switchSession } = useAppStore.getState();

        switchSession('session-123');

        expect(useAppStore.getState().currentSessionId).toBe('session-123');
      });

      it('应该调用后端 switchSession 方法', () => {
        const mockBackend = getMockCcBackend();
        const { switchSession } = useAppStore.getState();

        switchSession('session-456');

        expect(mockBackend.switchSession).toHaveBeenCalledWith('session-456');
      });
    });

    describe('createSession', () => {
      it('应该成功创建新会话', async () => {
        const mockSession: ChatSession = {
          id: 'new-session-1',
          name: 'New Session',
          type: SessionType.PROJECT,
          messages: [],
          context: {
            modelConfig: {
              provider: 'test',
              model: 'test-model',
              maxTokens: 4000,
              temperature: 0.7,
              topP: 0.9,
            },
            enabledSkills: [],
            enabledMcpServers: [],
            metadata: {},
          },
          createdAt: Date.now(),
          updatedAt: Date.now(),
          isActive: true,
          status: SessionStatus.IDLE,
        };

        const mockBackend = getMockCcBackend();
        mockBackend.createSession.mockResolvedValueOnce(mockSession);

        const { createSession } = useAppStore.getState();
        const result = await createSession('New Session', SessionType.PROJECT);

        expect(result).toEqual(mockSession);
        expect(useAppStore.getState().sessions).toContainEqual(mockSession);
        expect(useAppStore.getState().currentSessionId).toBe('new-session-1');
      });

      it('创建失败时应该返回 null', async () => {
        const mockBackend = getMockCcBackend();
        mockBackend.createSession.mockRejectedValueOnce(new Error('Creation failed'));

        const { createSession } = useAppStore.getState();
        const result = await createSession('Failed Session', SessionType.PROJECT);

        expect(result).toBeNull();
        expect(useAppStore.getState().sessions).toEqual([]);
      });

      it('后端返回 null 时应该返回 null', async () => {
        const mockBackend = getMockCcBackend();
        mockBackend.createSession.mockResolvedValueOnce(null as unknown as ChatSession);

        const { createSession } = useAppStore.getState();
        const result = await createSession('Null Session', SessionType.PROJECT);

        expect(result).toBeNull();
      });
    });

    describe('deleteSession', () => {
      it('应该删除指定会话', () => {
        const mockSessions: ChatSession[] = [
          {
            id: 'session-1',
            name: 'Session 1',
            type: SessionType.PROJECT,
            messages: [],
            context: {
              modelConfig: {
                provider: 'test',
                model: 'test-model',
                maxTokens: 4000,
                temperature: 0.7,
                topP: 0.9,
              },
              enabledSkills: [],
              enabledMcpServers: [],
              metadata: {},
            },
            createdAt: Date.now(),
            updatedAt: Date.now(),
            isActive: true,
            status: SessionStatus.IDLE,
          },
          {
            id: 'session-2',
            name: 'Session 2',
            type: SessionType.PROJECT,
            messages: [],
            context: {
              modelConfig: {
                provider: 'test',
                model: 'test-model',
                maxTokens: 4000,
                temperature: 0.7,
                topP: 0.9,
              },
              enabledSkills: [],
              enabledMcpServers: [],
              metadata: {},
            },
            createdAt: Date.now(),
            updatedAt: Date.now(),
            isActive: true,
            status: SessionStatus.IDLE,
          },
        ];

        useAppStore.setState({ sessions: mockSessions });
        const mockBackend = getMockCcBackend();
        const { deleteSession } = useAppStore.getState();

        deleteSession('session-1');

        expect(useAppStore.getState().sessions).toHaveLength(1);
        expect(useAppStore.getState().sessions[0]?.id).toBe('session-2');
        expect(mockBackend.deleteSession).toHaveBeenCalledWith('session-1');
      });

      it('删除当前会话时应该清空 currentSessionId', () => {
        const mockSession: ChatSession = {
          id: 'current-session',
          name: 'Current',
          type: SessionType.PROJECT,
          messages: [],
          context: {
            modelConfig: {
              provider: 'test',
              model: 'test-model',
              maxTokens: 4000,
              temperature: 0.7,
              topP: 0.9,
            },
            enabledSkills: [],
            enabledMcpServers: [],
            metadata: {},
          },
          createdAt: Date.now(),
          updatedAt: Date.now(),
          isActive: true,
          status: SessionStatus.IDLE,
        };

        useAppStore.setState({ sessions: [mockSession], currentSessionId: 'current-session' });
        const { deleteSession } = useAppStore.getState();

        deleteSession('current-session');

        expect(useAppStore.getState().currentSessionId).toBe('');
        expect(useAppStore.getState().sessions).toEqual([]);
      });
    });

    describe('updateSession', () => {
      it('应该更新指定会话', () => {
        const mockSession: ChatSession = {
          id: 'session-1',
          name: 'Original Name',
          type: SessionType.PROJECT,
          messages: [],
          context: {
            modelConfig: {
              provider: 'test',
              model: 'test-model',
              maxTokens: 4000,
              temperature: 0.7,
              topP: 0.9,
            },
            enabledSkills: [],
            enabledMcpServers: [],
            metadata: {},
          },
          createdAt: 1000,
          updatedAt: 1000,
          isActive: true,
          status: SessionStatus.IDLE,
        };

        useAppStore.setState({ sessions: [mockSession] });
        const { updateSession } = useAppStore.getState();

        updateSession('session-1', { name: 'Updated Name' });

        const updated = useAppStore.getState().sessions[0];
        if (updated) {
          expect(updated.name).toBe('Updated Name');
          expect(updated.id).toBe('session-1');
        }
      });

      it('应该支持多个字段更新', () => {
        const mockSession: ChatSession = {
          id: 'session-1',
          name: 'Original',
          type: SessionType.PROJECT,
          messages: [],
          context: {
            modelConfig: {
              provider: 'test',
              model: 'test-model',
              maxTokens: 4000,
              temperature: 0.7,
              topP: 0.9,
            },
            enabledSkills: [],
            enabledMcpServers: [],
            metadata: {},
          },
          createdAt: 1000,
          updatedAt: 1000,
          isActive: true,
          status: SessionStatus.IDLE,
        };

        useAppStore.setState({ sessions: [mockSession] });
        const { updateSession } = useAppStore.getState();

        updateSession('session-1', {
          name: 'Updated',
          status: SessionStatus.THINKING,
          updatedAt: 2000,
        });

        const updated = useAppStore.getState().sessions[0];
        if (updated) {
          expect(updated.name).toBe('Updated');
          expect(updated.status).toBe(SessionStatus.THINKING);
          expect(updated.updatedAt).toBe(2000);
        }
      });
    });

    describe('reorderSessions', () => {
      it('应该重新排序会话列表', () => {
        const mockSessions: ChatSession[] = [
          {
            id: '1',
            name: 'A',
            type: SessionType.PROJECT,
            messages: [],
            context: {
              modelConfig: {
                provider: 'test',
                model: 'test-model',
                maxTokens: 4000,
                temperature: 0.7,
                topP: 0.9,
              },
              enabledSkills: [],
              enabledMcpServers: [],
              metadata: {},
            },
            createdAt: 1,
            updatedAt: 1,
            isActive: true,
            status: SessionStatus.IDLE,
          },
          {
            id: '2',
            name: 'B',
            type: SessionType.PROJECT,
            messages: [],
            context: {
              modelConfig: {
                provider: 'test',
                model: 'test-model',
                maxTokens: 4000,
                temperature: 0.7,
                topP: 0.9,
              },
              enabledSkills: [],
              enabledMcpServers: [],
              metadata: {},
            },
            createdAt: 2,
            updatedAt: 2,
            isActive: true,
            status: SessionStatus.IDLE,
          },
          {
            id: '3',
            name: 'C',
            type: SessionType.PROJECT,
            messages: [],
            context: {
              modelConfig: {
                provider: 'test',
                model: 'test-model',
                maxTokens: 4000,
                temperature: 0.7,
                topP: 0.9,
              },
              enabledSkills: [],
              enabledMcpServers: [],
              metadata: {},
            },
            createdAt: 3,
            updatedAt: 3,
            isActive: true,
            status: SessionStatus.IDLE,
          },
        ];

        useAppStore.setState({ sessions: mockSessions });
        const { reorderSessions } = useAppStore.getState();

        const reordered = [mockSessions[2]!, mockSessions[0]!, mockSessions[1]!];
        reorderSessions(reordered);

        expect(useAppStore.getState().sessions).toEqual(reordered);
      });
    });
  });

  describe('UI 操作', () => {
    describe('toggleSidebar', () => {
      it('应该切换侧边栏状态', () => {
        const { toggleSidebar } = useAppStore.getState();

        toggleSidebar();
        expect(useAppStore.getState().ui.sidebarOpen).toBe(false);

        toggleSidebar();
        expect(useAppStore.getState().ui.sidebarOpen).toBe(true);
      });
    });

    describe('togglePreviewPanel', () => {
      it('应该切换预览面板状态', () => {
        const { togglePreviewPanel } = useAppStore.getState();

        togglePreviewPanel();
        expect(useAppStore.getState().ui.previewPanelOpen).toBe(true);

        togglePreviewPanel();
        expect(useAppStore.getState().ui.previewPanelOpen).toBe(false);
      });
    });

    describe('setSidebarOpen', () => {
      it('应该设置侧边栏开关状态', () => {
        const { setSidebarOpen } = useAppStore.getState();

        setSidebarOpen(false);
        expect(useAppStore.getState().ui.sidebarOpen).toBe(false);

        setSidebarOpen(true);
        expect(useAppStore.getState().ui.sidebarOpen).toBe(true);
      });
    });

    describe('openModal', () => {
      it('应该打开模态框并设置数据', () => {
        const modalData = { sessionId: 'test-123', action: 'export' };
        const { openModal } = useAppStore.getState();

        openModal('export-modal', modalData);

        expect(useAppStore.getState().ui.activeModal).toBe('export-modal');
        expect(useAppStore.getState().ui.modalData).toEqual(modalData);
      });
    });

    describe('closeModal', () => {
      it('应该关闭模态框并清空数据', () => {
        useAppStore.setState({
          ui: {
            sidebarOpen: true,
            sidebarWidth: 280,
            previewPanelOpen: false,
            previewPanelWidth: 400,
            activeModal: 'test-modal',
            modalData: { test: 'data' },
            notifications: [],
            isLoading: false,
            loadingMessage: '',
          },
        });

        const { closeModal } = useAppStore.getState();
        closeModal();

        expect(useAppStore.getState().ui.activeModal).toBeNull();
        expect(useAppStore.getState().ui.modalData).toBeNull();
      });
    });
  });

  describe('任务进度操作', () => {
    describe('setTaskProgress', () => {
      it('应该设置任务进度', () => {
        const progress = {
          taskId: 'task-1',
          totalSteps: 5,
          currentStep: 2,
          steps: [],
          status: TaskStatus.IN_PROGRESS,
        };

        const { setTaskProgress } = useAppStore.getState();
        setTaskProgress(progress);

        expect(useAppStore.getState().activeTaskProgress).toEqual(progress);
      });

      it('应该清除任务进度', () => {
        useAppStore.setState({
          activeTaskProgress: {
            taskId: 'task-1',
            totalSteps: 5,
            currentStep: 2,
            steps: [],
            status: TaskStatus.IN_PROGRESS,
          },
        });

        const { setTaskProgress } = useAppStore.getState();
        setTaskProgress(null);

        expect(useAppStore.getState().activeTaskProgress).toBeNull();
      });
    });
  });

  describe('错误操作', () => {
    describe('setError', () => {
      it('应该设置错误', () => {
        const error = new Error('Test error');
        const { setError } = useAppStore.getState();

        setError(error);

        expect(useAppStore.getState().error).toEqual(error);
      });

      it('应该清除错误', () => {
        useAppStore.setState({ error: new Error('Existing error') });
        const { setError } = useAppStore.getState();

        setError(null);

        expect(useAppStore.getState().error).toBeNull();
      });
    });
  });

  describe('initializeSessions', () => {
    it('应该从后端加载会话列表', async () => {
      const mockSessions: ChatSession[] = [
        {
          id: 'session-1',
          name: 'Session 1',
          type: SessionType.PROJECT,
          messages: [],
          context: {
            modelConfig: {
              provider: 'test',
              model: 'test-model',
              maxTokens: 4000,
              temperature: 0.7,
              topP: 0.9,
            },
            enabledSkills: [],
            enabledMcpServers: [],
            metadata: {},
          },
          createdAt: Date.now(),
          updatedAt: Date.now(),
          isActive: true,
          status: SessionStatus.IDLE,
        },
      ];

      const mockBackend = getMockCcBackend();
      mockBackend.searchSessions.mockResolvedValueOnce(mockSessions);

      const { initializeSessions } = useAppStore.getState();
      await initializeSessions();

      expect(useAppStore.getState().sessions).toEqual(mockSessions);
      expect(mockBackend.searchSessions).toHaveBeenCalledWith('');
    });

    it('加载失败时应该记录错误但不抛出', async () => {
      const mockBackend = getMockCcBackend();
      mockBackend.searchSessions.mockRejectedValueOnce(new Error('Load failed'));

      const { initializeSessions } = useAppStore.getState();

      // 不应该抛出错误
      await expect(initializeSessions()).resolves.not.toThrow();
    });
  });

  describe('状态不可变性', () => {
    it('switchSession 不应该修改原 state 对象', () => {
      const originalState = useAppStore.getState();
      const { switchSession } = useAppStore.getState();

      switchSession('new-id');

      const newState = useAppStore.getState();
      expect(newState).not.toBe(originalState);
    });

    it('updateSession 应该创建新数组而不是修改原数组', () => {
      const mockSession: ChatSession = {
        id: '1',
        name: 'A',
        type: SessionType.PROJECT,
        messages: [],
        context: {
          modelConfig: {
            provider: 'test',
            model: 'test-model',
            maxTokens: 4000,
            temperature: 0.7,
            topP: 0.9,
          },
          enabledSkills: [],
          enabledMcpServers: [],
          metadata: {},
        },
        createdAt: 1,
        updatedAt: 1,
        isActive: true,
        status: SessionStatus.IDLE,
      };
      useAppStore.setState({ sessions: [mockSession] });

      const originalSessions = useAppStore.getState().sessions;
      const { updateSession } = useAppStore.getState();

      updateSession('1', { name: 'Updated' });

      expect(useAppStore.getState().sessions).not.toBe(originalSessions);
    });
  });
});
