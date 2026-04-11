/**
 * SessionTabs - 会话标签栏组件
 *
 * 使用 @dnd-kit 实现 Tab 拖拽排序。
 * Tab 切换通过 appStore.switchSession() 触发。
 */

import { memo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAppStore } from '@/shared/stores/appStore';
import { TabItem } from './TabItem';
import { NewSessionButton } from './NewSessionButton';
import {
  DndContext,
  closestCenter,
  type DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors
} from '@dnd-kit/core';
import {
  SortableContext,
  horizontalListSortingStrategy,
  useSortable
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { cn } from '@/shared/utils/cn';

export interface SessionTabsProps {
  className?: string;
}

/**
 * 会话标签栏
 *
 * 支持拖拽排序、新建会话、关闭会话。
 * 使用 @dnd-kit 实现平滑的拖拽体验。
 */
export const SessionTabs = memo<SessionTabsProps>(function SessionTabs({ className }) {
  const { sessions, currentSessionId, switchSession, reorderSessions } = useAppStore();
  const navigate = useNavigate();

  // 配置拖拽传感器，8px 移动后才触发拖拽，避免与点击冲突
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8
      }
    })
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      if (over && active.id !== over.id) {
        const oldIndex = sessions.findIndex((s) => s.id === active.id);
        const newIndex = sessions.findIndex((s) => s.id === over.id);
        if (oldIndex !== -1 && newIndex !== -1) {
          const newSessions = [...sessions];
          const [removed] = newSessions.splice(oldIndex, 1);
          if (removed) {
            newSessions.splice(newIndex, 0, removed);
          }
          reorderSessions(newSessions);
        }
      }
    },
    [sessions, reorderSessions]
  );

  const handleTabClick = useCallback(
    (sessionId: string) => {
      switchSession(sessionId);
      navigate('/');
    },
    [switchSession, navigate]
  );

  return (
    <div className={cn('flex items-center border-b bg-background', className)}>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
      >
        <SortableContext
          items={sessions.map((s) => s.id)}
          strategy={horizontalListSortingStrategy}
        >
          <div className="flex items-center gap-1 overflow-x-auto px-2 py-1">
            {sessions.map((session) => (
              <SortableTabWrapper
                key={session.id}
                sessionId={session.id}
                isActive={session.id === currentSessionId}
                onClick={handleTabClick}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>
      <NewSessionButton />
    </div>
  );
});

/**
 * 可排序 Tab 包装器
 */
interface SortableTabWrapperProps {
  sessionId: string;
  isActive: boolean;
  onClick: (sessionId: string) => void;
}

const SortableTabWrapper = memo<SortableTabWrapperProps>(function SortableTabWrapper({
  sessionId,
  isActive,
  onClick
}) {
  const { sessions, deleteSession } = useAppStore();
  const session = sessions.find((s) => s.id === sessionId);

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: sessionId });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 1 : 0
  };

  if (!session) return null;

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <TabItem
        session={session}
        isActive={isActive}
        onClick={() => onClick(sessionId)}
        onClose={() => deleteSession(sessionId)}
      />
    </div>
  );
});
