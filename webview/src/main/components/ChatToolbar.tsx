/**
 * ChatToolbar - ChatView 顶部工具栏
 *
 * 组合 ModeSwitcher + AgentSelector + StreamingToggle
 */

import { memo } from 'react';
import { ModeSwitcher } from './ModeSwitcher';
import { AgentSelector } from './AgentSelector';
import { StreamingToggle } from './StreamingToggle';
import { cn } from '@/shared/utils/cn';

interface ChatToolbarProps {
  className?: string;
}

export const ChatToolbar = memo<ChatToolbarProps>(function ChatToolbar({
  className
}) {
  return (
    <div className={cn(
      'flex items-center gap-3 px-4 py-2 border-b border-border',
      'bg-muted/30',
      className
    )}>
      <ModeSwitcher />
      <div className="h-4 w-px bg-border" />
      <AgentSelector />
      <div className="h-4 w-px bg-border" />
      <StreamingToggle />
    </div>
  );
});
