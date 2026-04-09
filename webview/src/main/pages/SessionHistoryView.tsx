/**
 * SessionHistoryView - 会话历史页面
 */

import { lazy, Suspense, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';
import { useAppStore } from '@/shared/stores';
import { javaBridge } from '@/lib/java-bridge';
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

  const handleExportSession = useCallback(async (session: ChatSession) => {
    try {
      const data = await javaBridge.exportSession(session.id, 'json');
      if (!data) return;
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `session-${session.name.replace(/[^a-z0-9]/gi, '-')}-${session.id.slice(0, 8)}.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error('Export session failed:', e);
    }
  }, []);

  const handleImportSession = useCallback(async () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const text = await file.text();
      try {
        const result = await javaBridge.importSession(text);
        if (result) {
          // Switch to the imported session
          switchSession(result.id);
          navigate('/');
        }
      } catch (err) {
        console.error('Import session failed:', err);
      }
    };
    input.click();
  }, [switchSession, navigate]);

  return (
    <div className="h-full overflow-hidden">
      <Suspense fallback={<LoadingFallback />}>
        <LazySessionHistory
          sessions={sessions}
          onSelectSession={handleSelectSession}
          onDeleteSession={handleDeleteSession}
          onExportSession={handleExportSession}
          onImportSession={handleImportSession}
        />
      </Suspense>
    </div>
  );
}
