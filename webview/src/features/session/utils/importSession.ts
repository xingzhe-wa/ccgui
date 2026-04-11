/**
 * 会话导入工具
 *
 * 提供会话数据的导入功能，支持：
 * - 从 JSON 文件导入
 * - 从 Markdown 文件导入
 * - 验证导入数据的完整性
 */

import type { ChatSession, ChatMessage } from '@/shared/types';
import { SessionStatus, SessionType, MessageRole } from '@/shared/types';

/**
 * 导入会话验证结果
 */
export interface ImportValidationResult {
  isValid: boolean;
  errors: string[];
  warnings: string[];
  session?: ChatSession;
}

/**
 * 从 JSON 内容解析会话
 */
export function parseSessionFromJson(jsonContent: string): ImportValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  try {
    const data = JSON.parse(jsonContent);

    // 验证必要字段
    if (!data.id) {
      errors.push('Missing required field: id');
    }
    if (!data.name) {
      errors.push('Missing required field: name');
    }
    if (!data.type) {
      errors.push('Missing required field: type');
    }
    if (!Array.isArray(data.messages)) {
      errors.push('Missing or invalid field: messages (must be an array)');
    }

    if (errors.length > 0) {
      return { isValid: false, errors, warnings };
    }

    // 规范化数据
    const session: ChatSession = {
      id: data.id,
      name: data.name,
      type: data.type as SessionType,
      projectId: data.projectId,
      messages: (data.messages || []).map((msg: Record<string, unknown>) => ({
        id: msg.id || crypto.randomUUID(),
        role: msg.role || 'user',
        content: msg.content || '',
        timestamp: msg.timestamp || Date.now(),
        attachments: msg.attachments,
        references: msg.references,
        metadata: msg.metadata,
        status: msg.status || 'completed'
      })) as ChatMessage[],
      context: data.context || {
        modelConfig: {
          provider: 'default',
          model: 'default',
          maxTokens: 4096,
          temperature: 0.7,
          topP: 1
        },
        enabledSkills: [],
        enabledMcpServers: [],
        metadata: {}
      },
      createdAt: data.createdAt || Date.now(),
      updatedAt: data.updatedAt || Date.now(),
      isActive: false,
      status: data.status || SessionStatus.IDLE,
      isInitialized: true,
    };

    // 检查消息数量
    if (session.messages.length === 0) {
      warnings.push('Session has no messages');
    }

    return { isValid: true, errors, warnings, session };
  } catch (error) {
    return {
      isValid: false,
      errors: [`Failed to parse JSON: ${error instanceof Error ? error.message : 'Unknown error'}`],
      warnings: []
    };
  }
}

/**
 * 从 Markdown 内容解析会话（基础实现）
 *
 * 尝试从 Markdown 中提取会话信息
 * 注意：Markdown 格式不如 JSON 完整，恢复的数据可能有限
 */
export function parseSessionFromMarkdown(markdownContent: string, name: string): ImportValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // 简单检查 Markdown 内容
  if (!markdownContent.trim()) {
    errors.push('Markdown content is empty');
    return { isValid: false, errors, warnings };
  }

  warnings.push('Markdown import provides limited session recovery. JSON export is recommended.');

  // 提取标题（如果存在）
  const titleMatch = markdownContent.match(/^#\s+(.+)$/m);
  const sessionName = titleMatch?.[1] ?? name;

  // 提取消息（简化处理，仅提取文本内容）
  const messages: ChatMessage[] = [];
  const messageBlocks = markdownContent.split(/^---+$/m);

  for (const block of messageBlocks) {
    const lines = block.trim().split('\n');
    if (lines.length === 0) continue;

    // 检查是否包含用户/助手消息标记
    const roleMatch = block.match(/\*\*(User|Assistant)\*\*/);
    if (roleMatch && roleMatch[1]) {
      const role = roleMatch[1].toLowerCase() === 'user' ? MessageRole.USER : MessageRole.ASSISTANT;
      const content = lines
        .filter((line) => !line.startsWith('#') && !line.match(/\*\*(User|Assistant)\*\*/))
        .join('\n')
        .trim();

      if (content) {
        messages.push({
          id: crypto.randomUUID(),
          role,
          content,
          timestamp: Date.now()
        });
      }
    }
  }

  if (messages.length === 0) {
    warnings.push('No recognizable messages found in Markdown');
  }

  // 创建基础会话对象
  const session: ChatSession = {
    id: crypto.randomUUID(),
    name: sessionName,
    type: SessionType.TEMPORARY,
    messages,
    context: {
      modelConfig: {
        provider: 'default',
        model: 'default',
        maxTokens: 4096,
        temperature: 0.7,
        topP: 1
      },
      enabledSkills: [],
      enabledMcpServers: [],
      metadata: { importedFrom: 'markdown' }
    },
    createdAt: Date.now(),
    updatedAt: Date.now(),
    isActive: false,
    status: SessionStatus.IDLE,
    isInitialized: true,
  };

  return { isValid: true, errors, warnings, session };
}

/**
 * 验证导入的会话
 */
export function validateSession(session: ChatSession): ImportValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // 验证 ID
  if (!session.id || typeof session.id !== 'string') {
    errors.push('Invalid session ID');
  }

  // 验证名称
  if (!session.name || typeof session.name !== 'string') {
    errors.push('Invalid session name');
  }

  // 验证类型
  if (!Object.values(SessionType).includes(session.type)) {
    errors.push(`Invalid session type: ${session.type}`);
  }

  // 验证消息
  if (!Array.isArray(session.messages)) {
    errors.push('Messages must be an array');
  } else {
    for (let i = 0; i < session.messages.length; i++) {
      const msg = session.messages[i];
      if (!msg) {
        warnings.push(`Message at index ${i} is undefined`);
        continue;
      }
      if (!msg.id) {
        warnings.push(`Message at index ${i} missing ID, will be assigned one`);
      }
      if (!msg.content) {
        warnings.push(`Message at index ${i} missing content`);
      }
    }
  }

  return {
    isValid: errors.length === 0,
    errors,
    warnings
  };
}

/**
 * 导入会话文件
 */
export async function importSessionFromFile(file: File): Promise<ImportValidationResult> {
  return new Promise((resolve) => {
    const reader = new FileReader();

    reader.onload = (e) => {
      const content = e.target?.result as string;
      const fileName = file.name.toLowerCase();

      if (fileName.endsWith('.json')) {
        resolve(parseSessionFromJson(content));
      } else if (fileName.endsWith('.md') || fileName.endsWith('.markdown')) {
        const name = fileName.replace(/\.(md|markdown)$/i, '');
        resolve(parseSessionFromMarkdown(content, name));
      } else {
        resolve({
          isValid: false,
          errors: ['Unsupported file format. Please use .json or .md files.'],
          warnings: []
        });
      }
    };

    reader.onerror = () => {
      resolve({
        isValid: false,
        errors: ['Failed to read file'],
        warnings: []
      });
    };

    reader.readAsText(file);
  });
}

/**
 * 批量导入会话
 */
export async function importSessionsFromFiles(files: File[]): Promise<{
  successful: ChatSession[];
  failed: { file: File; error: string }[];
}> {
  const successful: ChatSession[] = [];
  const failed: { file: File; error: string }[] = [];

  for (const file of files) {
    const result = await importSessionFromFile(file);
    if (result.isValid && result.session) {
      successful.push(result.session);
    } else {
      failed.push({
        file,
        error: result.errors.join(', ')
      });
    }
  }

  return { successful, failed };
}