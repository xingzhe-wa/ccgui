/**
 * StopButton - 停止生成按钮
 *
 * 仅在当前消息正在流式输出时显示。
 * 点击后调用streamingStore.cancelStreaming()，同时通知后端停止。
 */

import { memo } from 'react';
import { Button } from '@/shared/components/ui/button';
import { useStreamingStore } from '@/shared/stores/streamingStore';
import { cn } from '@/shared/utils/cn';

export interface StopButtonProps {
  /** 消息ID */
  messageId: string;
  /** 停止回调 */
  onStop?: () => void;
  className?: string;
}

/**
 * 停止生成按钮
 *
 * 仅在当前消息正在流式输出时显示。
 * 点击后调用streamingStore.cancelStreaming()，同时通知后端停止。
 */
export const StopButton = memo<StopButtonProps>(function StopButton({
  messageId,
  onStop,
  className
}: StopButtonProps) {
  const { streamingMessageId, cancelStreaming } = useStreamingStore();
  const isActive = streamingMessageId === messageId;

  const handleStop = () => {
    cancelStreaming();
    onStop?.();
  };

  if (!isActive) {
    return null;
  }

  return (
    <Button
      variant="destructive"
      size="sm"
      onClick={handleStop}
      className={cn('gap-2', className)}
    >
      <StopIcon className="h-4 w-4" />
      停止生成
    </Button>
  );
});

/**
 * 停止图标
 */
const StopIcon = ({ className }: { className?: string }) => (
  <svg
    className={className}
    viewBox="0 0 24 24"
    fill="currentColor"
    xmlns="http://www.w3.org/2000/svg"
  >
    <rect x="6" y="6" width="12" height="12" rx="1" />
  </svg>
);
