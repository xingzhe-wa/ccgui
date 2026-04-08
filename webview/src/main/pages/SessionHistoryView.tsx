/**
 * SessionHistoryView - 会话历史页面
 */

import { lazy, Suspense, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';
import { useAppStore } from '@/shared/stores';
import type { ChatSession } from '@/shared/types';

const LazySessionHistory = lazy(() =>
  import('@/features/session/components/SessionHistory').then((m) => ({
    default: m.SessionHistory
  }))
);

export function SessionHistoryView(): JSX.Element {
  const sessions = useAppStore((s) => s.sessions);
  const switchSession = useAppStore((s) => s.switchSession);
  const deleteSession = useAppStore((s) => s.deleteSession);
  const navigate = useNavigate();

  const handleSelectSession = useCallback(
    (sessionId: string) => {
      switchSession(sessionId);
      navigate('/');
    },
    [switchSession, navigate]
  );

  const handleDeleteSession = useCallback(
    (sessionId: string) => {
      deleteSession(sessionId);
    },
    [deleteSession]
  );

  const handleExportSession = useCallback((_session: ChatSession) => {
    // TODO: 触发导出流程
  }, []);

  return (
    <div className="h-full overflow-hidden">
      <Suspense fallback={<LoadingFallback />}>
        <LazySessionHistory
          sessions={sessions}
          onSelectSession={handleSelectSession}
          onDeleteSession={handleDeleteSession}
          onExportSession={handleExportSession}
        />
      </Suspense>
    </div>
  );
}
