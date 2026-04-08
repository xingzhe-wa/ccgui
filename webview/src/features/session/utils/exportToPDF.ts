/**
 * 会话导出为 PDF 工具
 *
 * 将会话消息导出为 PDF 文档
 * 使用浏览器的打印功能实现 PDF 生成
 */

import type { ChatSession } from '@/shared/types';

/**
 * HTML 转义函数，防止 XSS
 */
function escapeHtml(text: string): string {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * 生成会话的 HTML 表示
 */
function generateSessionHTML(session: ChatSession): string {
  const messagesHTML = session.messages
    .map((msg) => {
      const role = msg.role === 'user' ? '用户' : '助手';
      const timestamp = msg.timestamp
        ? new Date(msg.timestamp).toLocaleString()
        : '';
      const content = Array.isArray(msg.content)
        ? msg.content.map((part) => (part.type === 'text' ? part.text : '')).join('')
        : msg.content;

      return `
        <div class="message ${msg.role}">
          <div class="message-header">
            <span class="role">${escapeHtml(role)}</span>
            <span class="timestamp">${escapeHtml(timestamp)}</span>
          </div>
          <div class="message-content">${escapeHtml(content)}</div>
        </div>
      `;
    })
    .join('');

  return `
    <!DOCTYPE html>
    <html>
      <head>
        <meta charset="UTF-8">
        <title>${escapeHtml(session.name)}</title>
        <style>
          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            color: #333;
          }
          h1 {
            border-bottom: 2px solid #333;
            padding-bottom: 10px;
          }
          .meta {
            color: #666;
            font-size: 0.9em;
            margin-bottom: 20px;
          }
          .message {
            margin: 20px 0;
            padding: 15px;
            border-radius: 8px;
          }
          .message.user {
            background-color: #f0f7ff;
            border-left: 4px solid #0066cc;
          }
          .message.assistant {
            background-color: #f5f5f5;
            border-left: 4px solid #00aa00;
          }
          .message-header {
            display: flex;
            justify-content: space-between;
            margin-bottom: 8px;
            font-size: 0.85em;
          }
          .role {
            font-weight: bold;
          }
          .timestamp {
            color: #888;
          }
          .message-content {
            white-space: pre-wrap;
            word-break: break-word;
          }
          .footer {
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #ddd;
            text-align: center;
            color: #888;
            font-size: 0.8em;
          }
          @media print {
            body {
              padding: 0;
            }
            .message {
              break-inside: avoid;
            }
          }
        </style>
      </head>
      <body>
        <h1>${escapeHtml(session.name)}</h1>
        <div class="meta">
          <p><strong>Session ID:</strong> ${escapeHtml(session.id)}</p>
          <p><strong>Type:</strong> ${escapeHtml(session.type)}</p>
          <p><strong>Created:</strong> ${escapeHtml(new Date(session.createdAt).toLocaleString())}</p>
          <p><strong>Updated:</strong> ${escapeHtml(new Date(session.updatedAt).toLocaleString())}</p>
        </div>
        <div class="messages">
          ${messagesHTML}
        </div>
        <div class="footer">
          <p>Exported from ClaudeCodeJet on ${escapeHtml(new Date().toLocaleString())}</p>
        </div>
      </body>
    </html>
  `;
}

/**
 * 下载会话为 PDF 文件
 *
 * 使用浏览器的打印功能生成 PDF
 */
export function downloadSessionAsPDF(session: ChatSession): void {
  const html = generateSessionHTML(session);
  const printWindow = window.open('', '_blank');

  if (!printWindow) {
    console.error('Failed to open print window. Please check popup blocker settings.');
    return;
  }

  printWindow.document.write(html);
  printWindow.document.close();

  // 等待内容加载完成后触发打印
  printWindow.onload = () => {
    setTimeout(() => {
      printWindow.print();
      // 关闭空白窗口（部分浏览器不支持）
      // printWindow.close();
    }, 250);
  };
}

/**
 * 打印会话（不下载，直接打印）
 */
export function printSession(session: ChatSession): void {
  const html = generateSessionHTML(session);
  const printWindow = window.open('', '_blank');

  if (!printWindow) {
    console.error('Failed to open print window. Please check popup blocker settings.');
    return;
  }

  printWindow.document.write(html);
  printWindow.document.close();

  printWindow.onload = () => {
    setTimeout(() => {
      printWindow.print();
    }, 250);
  };
}