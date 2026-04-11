/**
 * StreamingToggle - 流式输出开关组件
 */

import { memo } from 'react';
import { useChatConfigStore } from '@/shared/stores/chatConfigStore';
import { cn } from '@/shared/utils/cn';

interface StreamingToggleProps {
  className?: string;
}

export const StreamingToggle = memo<StreamingToggleProps>(function StreamingToggle({
  className
}) {
  const { streamingEnabled, setStreamingEnabled } = useChatConfigStore();

  return (
    <label className={cn('flex items-center gap-1 text-xs cursor-pointer', className)}>
      <input
        type="checkbox"
        checked={streamingEnabled}
        onChange={(e) => setStreamingEnabled(e.target.checked)}
        className="accent-primary"
      />
      <span className={cn(streamingEnabled ? 'text-foreground' : 'text-muted-foreground')}>
        Stream
      </span>
    </label>
  );
});
