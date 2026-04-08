/**
 * useJavaBridge - Java 通信 Hook
 *
 * 提供类型安全的 Java 后端 API 调用接口
 */

import { useState, useEffect, useCallback } from 'react';
import type { ChatResponse, MultimodalMessage, ChatSession, SessionType } from '@/shared/types';

interface UseJavaBridgeReturn {
  isReady: boolean;
  error: Error | null;
  sendMessage: (message: string) => Promise<ChatResponse>;
  sendMultimodalMessage: (message: MultimodalMessage) => Promise<ChatResponse>;
  createSession: (name: string, type: SessionType) => Promise<ChatSession>;
  switchSession: (sessionId: string) => Promise<void>;
  deleteSession: (sessionId: string) => Promise<void>;
}

export function useJavaBridge(): UseJavaBridgeReturn {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // 等待 Java 后端注入
    let intervalId: ReturnType<typeof setInterval> | null = null;

    const checkReady = () => {
      if (window.ccBackend && window.ccEvents) {
        setIsReady(true);
        setError(null);
        return true;
      }
      return false;
    };

    if (!checkReady()) {
      intervalId = setInterval(() => {
        if (checkReady() && intervalId) {
          clearInterval(intervalId);
        }
      }, 100);
    }

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, []);

  const sendMessage = useCallback(async (message: string): Promise<ChatResponse> => {
    if (!isReady) {
      throw new Error('Java bridge not ready');
    }
    try {
      return await window.ccBackend.sendMessage(message);
    } catch (err) {
      setError(err as Error);
      throw err;
    }
  }, [isReady]);

  const sendMultimodalMessage = useCallback(
    async (message: MultimodalMessage): Promise<ChatResponse> => {
      if (!isReady) {
        throw new Error('Java bridge not ready');
      }
      try {
        return await window.ccBackend.sendMultimodalMessage(message);
      } catch (err) {
        setError(err as Error);
        throw err;
      }
    },
    [isReady]
  );

  const createSession = useCallback(
    async (name: string, type: SessionType): Promise<ChatSession> => {
      if (!isReady) {
        throw new Error('Java bridge not ready');
      }
      try {
        return await window.ccBackend.createSession(name, type);
      } catch (err) {
        setError(err as Error);
        throw err;
      }
    },
    [isReady]
  );

  const switchSession = useCallback(
    async (sessionId: string): Promise<void> => {
      if (!isReady) {
        throw new Error('Java bridge not ready');
      }
      try {
        await window.ccBackend.switchSession(sessionId);
      } catch (err) {
        setError(err as Error);
        throw err;
      }
    },
    [isReady]
  );

  const deleteSession = useCallback(
    async (sessionId: string): Promise<void> => {
      if (!isReady) {
        throw new Error('Java bridge not ready');
      }
      try {
        await window.ccBackend.deleteSession(sessionId);
      } catch (err) {
        setError(err as Error);
        throw err;
      }
    },
    [isReady]
  );

  return {
    isReady,
    error,
    sendMessage,
    sendMultimodalMessage,
    createSession,
    switchSession,
    deleteSession
  };
}
