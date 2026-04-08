/**
 * SessionSearch - 会话搜索组件
 *
 * 提供会话搜索功能，支持：
 * - 实时搜索
 * - 按会话名称、消息内容搜索
 * - 调用后端 API 进行全文搜索
 */

import { memo, useState, useCallback, useEffect, useRef } from 'react';
import { Search, X, FileText, MessageSquare } from 'lucide-react';
import { useDebounce } from '@/shared/hooks/useDebounce';
import { cn } from '@/shared/utils/cn';
import type { SessionSearchResult, ChatSession } from '@/shared/types';

export interface SessionSearchProps {
  /** 搜索结果回调 */
  onSearchResults?: (results: SessionSearchResult[]) => void;
  /** 选中搜索结果回调 */
  onSelectResult?: (sessionId: string) => void;
  /** 是否显示 */
  isOpen?: boolean;
  /** 关闭回调 */
  onClose?: () => void;
  className?: string;
}

interface SearchState {
  query: string;
  results: SessionSearchResult[];
  isLoading: boolean;
  error: string | null;
}

/**
 * 会话搜索组件
 */
export const SessionSearch = memo<SessionSearchProps>(function SessionSearch({
  onSearchResults,
  onSelectResult,
  isOpen = true,
  onClose,
  className
}: SessionSearchProps) {
  const [state, setState] = useState<SearchState>({
    query: '',
    results: [],
    isLoading: false,
    error: null
  });

  const inputRef = useRef<HTMLInputElement>(null);
  const debouncedQuery = useDebounce(state.query, 300);

  // 聚焦输入框
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  // 执行搜索
  useEffect(() => {
    let cancelled = false;

    const performSearch = async () => {
      if (!debouncedQuery.trim()) {
        if (!cancelled) {
          setState((prev) => ({ ...prev, results: [], isLoading: false }));
          onSearchResults?.([]);
        }
        return;
      }

      if (!cancelled) {
        setState((prev) => ({ ...prev, isLoading: true, error: null }));
      }

      try {
        // 调用后端搜索 API
        const results = await window.ccBackend?.searchSessions(debouncedQuery);

        if (cancelled) return;

        if (results && Array.isArray(results)) {
          // 将 ChatSession[] 转换为 SessionSearchResult[]
          const searchResults: SessionSearchResult[] = (results as ChatSession[]).map(
            (session) => ({
              sessionId: session.id,
              sessionName: session.name,
              excerpt: session.messages[0]?.content.slice(0, 100) ?? '',
              score: 1,
              timestamp: session.updatedAt
            })
          );

          setState((prev) => ({
            ...prev,
            results: searchResults,
            isLoading: false
          }));
          onSearchResults?.(searchResults);
        } else {
          // 后端 API 不可用时，返回空结果
          setState((prev) => ({
            ...prev,
            results: [],
            isLoading: false
          }));
          onSearchResults?.([]);
        }
      } catch (error) {
        if (cancelled) return;
        const errorMessage = error instanceof Error ? error.message : '搜索失败';
        setState((prev) => ({
          ...prev,
          error: errorMessage,
          isLoading: false
        }));
      }
    };

    performSearch();

    return () => {
      cancelled = true;
    };
  }, [debouncedQuery, onSearchResults]);

  const handleQueryChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setState((prev) => ({ ...prev, query: e.target.value }));
  }, []);

  const handleClear = useCallback(() => {
    setState((prev) => ({ ...prev, query: '', results: [], error: null }));
    onClose?.();
  }, [onClose]);

  const handleSelectResult = useCallback(
    (sessionId: string) => {
      onSelectResult?.(sessionId);
      handleClear();
    },
    [onSelectResult, handleClear]
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        handleClear();
      }
    },
    [handleClear]
  );

  if (!isOpen) return null;

  return (
    <div
      className={cn(
        'absolute top-0 left-0 right-0 z-50 bg-background border-b border-border shadow-lg',
        className
      )}
    >
      {/* 搜索输入框 */}
      <div className="flex items-center gap-2 px-4 py-3">
        <Search className="h-4 w-4 text-muted-foreground" />
        <input
          ref={inputRef}
          type="text"
          value={state.query}
          onChange={handleQueryChange}
          onKeyDown={handleKeyDown}
          placeholder="搜索会话..."
          className="flex-1 bg-transparent outline-none text-sm placeholder:text-muted-foreground"
        />
        {state.isLoading && (
          <div className="h-4 w-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        )}
        {state.query && !state.isLoading && (
          <button
            type="button"
            onClick={handleClear}
            className="p-1 hover:bg-accent rounded-sm"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>

      {/* 搜索结果 */}
      {state.results.length > 0 && (
        <div className="max-h-[300px] overflow-y-auto border-t border-border">
          {state.results.map((result) => (
            <button
              key={result.sessionId}
              type="button"
              onClick={() => handleSelectResult(result.sessionId)}
              className="w-full flex items-start gap-3 px-4 py-3 hover:bg-accent transition-colors text-left"
            >
              <FileText className="h-4 w-4 mt-0.5 text-muted-foreground shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium truncate">{result.sessionName}</p>
                <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">
                  {result.excerpt}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  {new Date(result.timestamp).toLocaleDateString()}
                </p>
              </div>
            </button>
          ))}
        </div>
      )}

      {/* 空结果 */}
      {state.query && !state.isLoading && state.results.length === 0 && !state.error && (
        <div className="px-4 py-6 text-center text-sm text-muted-foreground border-t border-border">
          <MessageSquare className="h-8 w-8 mx-auto mb-2 opacity-30" />
          <p>未找到相关会话</p>
        </div>
      )}

      {/* 错误状态 */}
      {state.error && (
        <div className="px-4 py-3 text-sm text-destructive border-t border-border">
          {state.error}
        </div>
      )}
    </div>
  );
});