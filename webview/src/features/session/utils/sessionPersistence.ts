/**
 * 会话持久化工具
 *
 * 提供会话数据的导入导出功能，支持：
 * - 会话列表的本地持久化（通过 localStorage）
 * - 会话数据的备份和恢复
 */

import type { ChatSession } from '@/shared/types';

const SESSION_STORAGE_KEY = 'ccgui-sessions';
const SESSION_BACKUP_PREFIX = 'ccgui-session-backup-';

/**
 * 保存会话列表到本地存储
 */
export function saveSessionsToStorage(sessions: ChatSession[]): void {
  try {
    localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(sessions));
  } catch (error) {
    console.error('Failed to save sessions to storage:', error);
    // 处理存储满的情况
    if (error instanceof DOMException && error.name === 'QuotaExceededException') {
      // 可以尝试清理旧数据或提示用户
      console.warn('Storage quota exceeded, consider cleaning up old sessions');
    }
  }
}

/**
 * 从本地存储加载会话列表
 */
export function loadSessionsFromStorage(): ChatSession[] {
  try {
    const data = localStorage.getItem(SESSION_STORAGE_KEY);
    if (!data) return [];
    return JSON.parse(data) as ChatSession[];
  } catch (error) {
    console.error('Failed to load sessions from storage:', error);
    return [];
  }
}

/**
 * 创建会话备份
 */
export function backupSession(session: ChatSession): void {
  try {
    const backupKey = `${SESSION_BACKUP_PREFIX}${session.id}`;
    const backupData = {
      session,
      timestamp: Date.now()
    };
    localStorage.setItem(backupKey, JSON.stringify(backupData));
  } catch (error) {
    console.error('Failed to backup session:', error);
  }
}

/**
 * 获取会话备份
 */
export function getSessionBackup(sessionId: string): (ChatSession & { timestamp: number }) | null {
  try {
    const backupKey = `${SESSION_BACKUP_PREFIX}${sessionId}`;
    const data = localStorage.getItem(backupKey);
    if (!data) return null;
    return JSON.parse(data);
  } catch (error) {
    console.error('Failed to get session backup:', error);
    return null;
  }
}

/**
 * 删除会话备份
 */
export function deleteSessionBackup(sessionId: string): void {
  const backupKey = `${SESSION_BACKUP_PREFIX}${sessionId}`;
  localStorage.removeItem(backupKey);
}

/**
 * 清理所有过期的会话备份（超过7天的）
 */
export function cleanupExpiredBackups(): void {
  const EXPIRY_DAYS = 7;
  const EXPIRY_MS = EXPIRY_DAYS * 24 * 60 * 60 * 1000;
  const now = Date.now();

  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i);
    if (key?.startsWith(SESSION_BACKUP_PREFIX)) {
      try {
        const data = localStorage.getItem(key);
        if (data) {
          const backup = JSON.parse(data);
          if (backup.timestamp && now - backup.timestamp > EXPIRY_MS) {
            localStorage.removeItem(key);
          }
        }
      } catch {
        // 忽略解析错误
      }
    }
  }
}

/**
 * 导出单个会话为 JSON 格式
 */
export function exportSessionAsJson(session: ChatSession): string {
  return JSON.stringify(session, null, 2);
}

/**
 * 从 JSON 导入会话
 */
export function importSessionFromJson(json: string): ChatSession | null {
  try {
    const data = JSON.parse(json);
    // 验证必要的字段
    if (!data.id || !data.name || !data.type || !data.messages || !data.context) {
      console.error('Invalid session data structure');
      return null;
    }
    return data as ChatSession;
  } catch (error) {
    console.error('Failed to parse session JSON:', error);
    return null;
  }
}

/**
 * 下载会话为 JSON 文件
 */
export function downloadSessionAsJson(session: ChatSession): void {
  const json = exportSessionAsJson(session);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = `session-${session.name.replace(/[^a-z0-9]/gi, '-')}-${session.id.slice(0, 8)}.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * 从文件导入会话
 */
export function importSessionFromFile(file: File): Promise<ChatSession | null> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      resolve(importSessionFromJson(content));
    };
    reader.onerror = () => {
      console.error('Failed to read file');
      resolve(null);
    };
    reader.readAsText(file);
  });
}