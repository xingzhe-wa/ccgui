/**
 * NewSessionButton - 新建会话按钮组件
 */

import { memo, useCallback } from 'react';
import { Plus } from 'lucide-react';
import { useAppStore } from '@/shared/stores/appStore';
import { Button } from '@/shared/components/ui/button/Button';
import { SessionType } from '@/shared/types';

export interface NewSessionButtonProps {
  className?: string;
}

/**
 * 新建会话按钮
 */
export const NewSessionButton = memo<NewSessionButtonProps>(function NewSessionButton({
  className
}: NewSessionButtonProps) {
  const { createSession } = useAppStore();

  const handleClick = useCallback(async () => {
    try {
      await createSession('新会话', SessionType.TEMPORARY);
    } catch (error) {
      console.error('Failed to create session:', error);
    }
  }, [createSession]);

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={handleClick}
      className={className}
      title="新建会话"
    >
      <Plus className="h-4 w-4" />
    </Button>
  );
});