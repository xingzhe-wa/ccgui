/**
 * 会话导出为 Markdown 工具
 *
 * 将会话消息导出为格式化的 Markdown 文档
 */

import type { ChatSession, ChatMessage } from '@/shared/types';

/**
 * 将单条消息转换为 Markdown 格式
 */
function formatMessageAsMarkdown(message: ChatMessage): string {
  const role = message.role === 'user' ? '**User**' : '**Assistant**';
  const timestamp = message.timestamp
    ? new Date(message.timestamp).toLocaleString()
    : '';

  let content = '';

  // 处理内容部分
  if (message.content) {
    if (Array.isArray(message.content)) {
      // 处理多模态内容
      for (const part of message.content) {
        if (part.type === 'text') {
          content += part.text;
        } else if (part.type === 'image') {
          content += `![Image](data:${part.mimeType};base64,${part.data.slice(0, 20)}...)\n`;
        }
      }
    } else {
      content = message.content;
    }
  }

  return `### ${role} ${timestamp ? `(${timestamp})` : ''}\n\n${content}\n`;
}

/**
 * 将会话导出为 Markdown 字符串
 */
export function exportSessionToMarkdown(session: ChatSession): string {
  const header = `# ${session.name}\n\n`;
  const meta = [
    `**Session ID**: ${session.id}`,
    `**Type**: ${session.type}`,
    `**Created**: ${new Date(session.createdAt).toLocaleString()}`,
    `**Updated**: ${new Date(session.updatedAt).toLocaleString()}`,
    session.projectId ? `**Project ID**: ${session.projectId}` : null,
    ''
  ]
    .filter(Boolean)
    .join('\n');

  const divider = '---\n\n';
  const messages = session.messages
    .map(formatMessageAsMarkdown)
    .join('\n---\n\n');

  const footer = `\n\n---\n\n*Exported from CC Assistant on ${new Date().toLocaleString()}*`;

  return header + meta + divider + messages + footer;
}

/**
 * 将多个会话导出为一个 Markdown 文件
 */
export function exportSessionsToMarkdown(sessions: ChatSession[]): string {
  const header = `# CC Assistant 会话导出\n\n`;
  const timestamp = `**导出时间**: ${new Date().toLocaleString()}\n\n`;
  const count = `**会话数量**: ${sessions.length}\n\n---\n\n`;

  const sessionContents = sessions
    .map((session, index) => {
      return `## ${index + 1}. ${session.name}\n\n${exportSessionToMarkdown(session)}`;
    })
    .join('\n\n---\n\n');

  return header + timestamp + count + sessionContents;
}

/**
 * 下载会话为 Markdown 文件
 */
export function downloadSessionAsMarkdown(session: ChatSession): void {
  const markdown = exportSessionToMarkdown(session);
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = `${session.name.replace(/[^a-z0-9\u4e00-\u9fa5]/gi, '-')}-${session.id.slice(0, 8)}.md`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/**
 * 下载多个会话为一个 Markdown 文件
 */
export function downloadSessionsAsMarkdown(sessions: ChatSession[]): void {
  const markdown = exportSessionsToMarkdown(sessions);
  const timestamp = new Date().toISOString().slice(0, 10);
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);

  const link = document.createElement('a');
  link.href = url;
  link.download = `ccgui-sessions-export-${timestamp}.md`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}