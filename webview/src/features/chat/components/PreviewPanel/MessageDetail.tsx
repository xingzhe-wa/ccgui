/**
 * MessageDetail - 消息详情面板
 *
 * 显示选中消息的完整内容和元数据
 */

import { memo } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/shared/utils/cn';
import { MarkdownRenderer } from '../MarkdownRenderer';
import type { ChatMessage } from '@/shared/types';
import { MessageStatus } from '@/shared/types';
import { MessageRole } from '@/shared/types';

interface MessageDetailProps {
  message: ChatMessage;
  onClose: () => void;
  className?: string;
}

function formatTimestamp(ts: number): string {
  return new Date(ts).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}

function getRoleLabel(role: MessageRole): string {
  switch (role) {
    case 'user':
      return '用户';
    case 'assistant':
      return '助手';
    case 'system':
      return '系统';
    default:
      return role;
  }
}

export const MessageDetail = memo<MessageDetailProps>(function MessageDetail({
  message,
  onClose,
  className
}) {
  return (
    <div className={cn('flex flex-col h-full bg-background-secondary', className)}>
      {/* 头部 */}
      <div className="flex items-center justify-between border-b px-4 py-3">
        <h3 className="text-sm font-medium">消息详情</h3>
        <button
          onClick={onClose}
          className="p-1 rounded-md hover:bg-accent transition-colors"
          aria-label="关闭"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* 消息内容 */}
      <div className="flex-1 overflow-y-auto p-4">
        {/* 角色和和时间 */}
        <div className="mb-4 space-y-1">
          <div className="flex items-center gap-2">
            <span
              className={cn(
                'px-2 py-0.5 rounded text-xs font-medium',
                message.role === 'user'
                  ? 'bg-primary/10 text-primary'
                  : message.role === 'assistant'
                    ? 'bg-green-500/10 text-green-600'
                    : 'bg-muted text-muted-foreground'
              )}
            >
              {getRoleLabel(message.role)}
            </span>
            <span className="text-xs text-muted-foreground">
              {formatTimestamp(message.timestamp)}
            </span>
          </div>
        </div>

        {/* 消息内容 */}
        <div className="prose prose-sm dark:prose-invert max-w-none">
          <MarkdownRenderer content={message.content} />
        </div>

        {/* 附件信息 */}
        {message.attachments && message.attachments.length > 0 && (
          <div className="mt-4 pt-4 border-t">
            <h4 className="text-xs font-medium text-muted-foreground mb-2">
              附件 ({message.attachments.length})
            </h4>
            <div className="space-y-2">
              {message.attachments.map((att, i) => (
                <div
                  key={i}
                  className="flex items-center gap-2 text-xs p-2 rounded bg-accent/50"
                >
                  <span className="truncate flex-1">
                    {att.type === 'image' ? '🖼️ ' : att.type === 'file' ? '📎 ' : '📎 '}
                    {att.type === 'file' && 'name' in att ? att.name : `${att.type} attachment`}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 元数据 */}
        <div className="mt-4 pt-4 border-t space-y-1">
          <h4 className="text-xs font-medium text-muted-foreground mb-2">元数据</h4>
          <div className="text-xs text-muted-foreground space-y-1">
            <p>
              <span className="font-medium">消息ID:</span> {message.id}
            </p>
            <p>
              <span className="font-medium">状态:</span>{' '}
              {message.status === MessageStatus.PENDING
                ? '发送中'
                : message.status === MessageStatus.SENT
                  ? '已发送'
                  : message.status === MessageStatus.STREAMING
                    ? '流式输出中'
                    : message.status === MessageStatus.FAILED
                      ? '失败'
                      : '已完成'}
            </p>
            {message.metadata && (
              <>
                <p>
                  <span className="font-medium">Token数:</span>{' '}
                  {message.metadata.tokenCount ?? '-'}
                </p>
                <p>
                  <span className="font-medium">延迟:</span>{' '}
                  {message.metadata.latencyMs != null
                    ? `${message.metadata.latencyMs}ms`
                    : '-'}
                </p>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});
