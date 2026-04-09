/**
 * SessionHistory - 会话历史记录组件
 *
 * 提供会话历史浏览功能，支持：
 * - 按日期分组显示会话
 * - 会话搜索和过滤
 * - 会话预览
 * - 恢复已删除的会话
 */

import { memo, useState, useCallback, useMemo } from 'react';
import { Calendar, Clock, Search, Trash2, RotateCcw, ChevronRight } from 'lucide-react';
import type { ChatSession, SessionType } from '@/shared/types';
import { SessionType as SessionTypeEnum } from '@/shared/types';
import { cn } from '@/shared/utils/cn';

export interface SessionHistoryProps {
  /** 会话列表 */
  sessions: ChatSession[];
  /** 加载状态 */
  isLoading?: boolean;
  /** 选中会话回调 */
  onSelectSession?: (sessionId: string) => void;
  /** 删除会话回调 */
  onDeleteSession?: (sessionId: string) => void;
  /** 恢复会话回调 */
  onRestoreSession?: (sessionId: string) => void;
  /** 导出会话回调 */
  onExportSession?: (session: ChatSession) => void;
  /** 导入会话回调 */
  onImportSession?: () => void;
  className?: string;
}

/** 按日期分组后的会话类型 */
interface GroupedSessions {
  date: string;
  sessions: ChatSession[];
}

const ITEMS_PER_PAGE = 20;

/**
 * 按日期分组会话
 */
function groupSessionsByDate(sessions: ChatSession[]): GroupedSessions[] {
  const groups: Record<string, ChatSession[]> = {};

  for (const session of sessions) {
    const date = new Date(session.updatedAt).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });

    if (!groups[date]) {
      groups[date] = [];
    }
    groups[date].push(session);
  }

  return Object.entries(groups).map(([date, sessions]) => ({
    date,
    sessions: sessions.sort((a, b) => b.updatedAt - a.updatedAt)
  }));
}

/**
 * 获取相对时间字符串
 */
function getRelativeTime(timestamp: number): string {
  const now = Date.now();
  const diff = now - timestamp;
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  if (hours < 24) return `${hours} 小时前`;
  if (days < 7) return `${days} 天前`;
  return '';
}

/**
 * 会话历史记录组件
 */
export const SessionHistory = memo<SessionHistoryProps>(function SessionHistory({
  sessions,
  isLoading = false,
  onSelectSession,
  onDeleteSession,
  onRestoreSession,
  onExportSession,
  onImportSession,
  className
}: SessionHistoryProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<SessionType | 'all'>('all');
  const [visibleCount, setVisibleCount] = useState(ITEMS_PER_PAGE);

  // 过滤和搜索会话
  const filteredSessions = useMemo(() => {
    return sessions.filter((session) => {
      // 类型过滤
      if (filterType !== 'all' && session.type !== filterType) {
        return false;
      }

      // 搜索过滤
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const matchesName = session.name.toLowerCase().includes(query);
        const matchesMessage = session.messages.some((msg) =>
          msg.content.toLowerCase().includes(query)
        );
        return matchesName || matchesMessage;
      }

      return true;
    });
  }, [sessions, searchQuery, filterType]);

  // 按日期分组
  const groupedSessions = useMemo(
    () => groupSessionsByDate(filteredSessions),
    [filteredSessions]
  );

  // 可见的分组（分页）
  const visibleGroups = useMemo(() => {
    const result: GroupedSessions[] = [];
    let count = 0;

    for (const group of groupedSessions) {
      result.push(group);
      count += group.sessions.length;
      if (count >= visibleCount) break;
    }

    return result;
  }, [groupedSessions, visibleCount]);

  // 是否还有更多
  const hasMore = useMemo(() => {
    let total = 0;
    for (const group of groupedSessions) {
      total += group.sessions.length;
      if (total >= visibleCount) return true;
    }
    return false;
  }, [groupedSessions, visibleCount]);

  const handleLoadMore = useCallback(() => {
    setVisibleCount((prev) => prev + ITEMS_PER_PAGE);
  }, []);

  const handleSessionClick = useCallback(
    (sessionId: string) => {
      onSelectSession?.(sessionId);
    },
    [onSelectSession]
  );

  const handleDelete = useCallback(
    (e: React.MouseEvent, sessionId: string) => {
      e.stopPropagation();
      onDeleteSession?.(sessionId);
    },
    [onDeleteSession]
  );

  const handleRestore = useCallback(
    (e: React.MouseEvent, sessionId: string) => {
      e.stopPropagation();
      onRestoreSession?.(sessionId);
    },
    [onRestoreSession]
  );

  const handleExport = useCallback(
    (e: React.MouseEvent, session: ChatSession) => {
      e.stopPropagation();
      onExportSession?.(session);
    },
    [onExportSession]
  );

  const getSessionIcon = (type: SessionType) => {
    switch (type) {
      case SessionTypeEnum.PROJECT:
        return '📁';
      case SessionTypeEnum.GLOBAL:
        return '🌐';
      case SessionTypeEnum.TEMPORARY:
        return '💬';
      default:
        return '💬';
    }
  };

  if (isLoading) {
    return (
      <div className={cn('flex items-center justify-center h-64', className)}>
        <div className="flex flex-col items-center gap-3">
          <div className="h-8 w-8 border-3 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-muted-foreground">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* 搜索和过滤栏 */}
      <div className="flex items-center gap-3 p-4 border-b border-border">
        {/* 搜索框 */}
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索会话..."
            className={cn(
              'w-full h-9 pl-9 pr-3 text-sm',
              'bg-accent/50 border border-border rounded-md',
              'placeholder:text-muted-foreground',
              'focus:outline-none focus:ring-2 focus:ring-ring'
            )}
          />
        </div>

        {/* 类型过滤器 */}
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value as SessionType | 'all')}
          className={cn(
            'h-9 px-3 text-sm',
            'bg-accent/50 border border-border rounded-md',
            'focus:outline-none focus:ring-2 focus:ring-ring'
          )}
        >
          <option value="all">全部</option>
          <option value={SessionTypeEnum.PROJECT}>项目</option>
          <option value={SessionTypeEnum.GLOBAL}>全局</option>
          <option value={SessionTypeEnum.TEMPORARY}>临时</option>
        </select>

        {/* 导入按钮 */}
        {onImportSession && (
          <button
            onClick={onImportSession}
            className={cn(
              'h-9 px-3 text-sm',
              'bg-accent/50 border border-border rounded-md',
              'hover:bg-accent transition-colors',
              'flex items-center gap-1.5'
            )}
          >
            导入
          </button>
        )}
      </div>

      {/* 会话列表 */}
      <div className="flex-1 overflow-y-auto">
        {groupedSessions.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 text-center">
            <Calendar className="h-12 w-12 text-muted-foreground/30 mb-3" />
            <p className="text-muted-foreground">
              {searchQuery ? '未找到匹配的会话' : '暂无会话历史'}
            </p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {visibleGroups.map((group) => (
              <div key={group.date}>
                {/* 日期标题 */}
                <div className="sticky top-0 z-10 bg-background px-4 py-2">
                  <h3 className="text-xs font-medium text-muted-foreground flex items-center gap-2">
                    <Calendar className="h-3 w-3" />
                    {group.date}
                  </h3>
                </div>

                {/* 会话列表 */}
                <div className="divide-y divide-border/50">
                  {group.sessions.map((session) => (
                    <button
                      key={session.id}
                      onClick={() => handleSessionClick(session.id)}
                      className={cn(
                        'w-full flex items-center gap-3 px-4 py-3',
                        'hover:bg-accent/50 transition-colors',
                        'text-left group'
                      )}
                    >
                      {/* 会话图标 */}
                      <span className="text-xl">{getSessionIcon(session.type)}</span>

                      {/* 会话信息 */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <h4 className="text-sm font-medium truncate">
                            {session.name}
                          </h4>
                          <span className="text-xs text-muted-foreground">
                            {getRelativeTime(session.updatedAt)}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground truncate mt-0.5">
                          {session.messages.length > 0
                            ? session.messages[session.messages.length - 1]?.content?.slice(0, 50) ||
                              '暂无消息'
                            : '暂无消息'}
                        </p>
                        <div className="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
                          <span className="flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {new Date(session.updatedAt).toLocaleTimeString('zh-CN', {
                              hour: '2-digit',
                              minute: '2-digit'
                            })}
                          </span>
                          <span>{session.messages.length} 条消息</span>
                        </div>
                      </div>

                      {/* 操作按钮 */}
                      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        {onExportSession && (
                          <button
                            onClick={(e) => handleExport(e, session)}
                            className={cn(
                              'p-1.5 rounded-md',
                              'hover:bg-accent',
                              'text-muted-foreground hover:text-foreground'
                            )}
                            title="导出"
                          >
                            <RotateCcw className="h-4 w-4" />
                          </button>
                        )}
                        {onRestoreSession && (
                          <button
                            onClick={(e) => handleRestore(e, session.id)}
                            className={cn(
                              'p-1.5 rounded-md',
                              'hover:bg-accent',
                              'text-muted-foreground hover:text-foreground'
                            )}
                            title="恢复"
                          >
                            <RotateCcw className="h-4 w-4" />
                          </button>
                        )}
                        {onDeleteSession && (
                          <button
                            onClick={(e) => handleDelete(e, session.id)}
                            className={cn(
                              'p-1.5 rounded-md',
                              'hover:bg-destructive/10',
                              'text-muted-foreground hover:text-destructive'
                            )}
                            title="删除"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        )}
                        <ChevronRight className="h-4 w-4 text-muted-foreground" />
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 加载更多 */}
      {hasMore && (
        <div className="p-4 border-t border-border">
          <button
            onClick={handleLoadMore}
            className={cn(
              'w-full h-9 text-sm',
              'bg-accent/50 border border-border rounded-md',
              'hover:bg-accent transition-colors'
            )}
          >
            加载更多
          </button>
        </div>
      )}
    </div>
  );
});