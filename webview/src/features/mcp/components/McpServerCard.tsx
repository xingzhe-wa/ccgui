/**
 * McpServerCard - MCP 服务器卡片组件
 *
 * 展示单个 MCP 服务器的基本信息，支持启动/停止、编辑和删除操作。
 */

import { memo, useCallback } from 'react';
import { Edit2, Trash2, Play, Square, RefreshCw, AlertCircle, CheckCircle2, XCircle, Power } from 'lucide-react';
import type { McpServer } from '@/shared/types';
import { McpServerStatus } from '@/shared/types';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface McpServerCardProps {
  /** MCP 服务器数据 */
  server: McpServer;
  /** 连接状态 */
  connectionStatus?: McpServerStatus;
  /** 是否选中 */
  isSelected?: boolean;
  /** 点击回调 */
  onClick?: () => void;
  /** 编辑回调 */
  onEdit?: () => void;
  /** 删除回调 */
  onDelete?: () => void;
  /** 启动回调 */
  onStart?: () => void;
  /** 停止回调 */
  onStop?: () => void;
  /** 测试连接回调 */
  onTest?: () => void;
  /** 启用/禁用回调 */
  onToggleEnabled?: (server: McpServer) => void;
  className?: string;
}

const statusConfig: Record<
  McpServerStatus,
  { label: string; icon: typeof CheckCircle2; color: string; bgColor: string }
> = {
  [McpServerStatus.CONNECTED]: {
    label: '已连接',
    icon: CheckCircle2,
    color: 'text-green-600 dark:text-green-400',
    bgColor: 'bg-green-100 dark:bg-green-900/30'
  },
  [McpServerStatus.DISCONNECTED]: {
    label: '已断开',
    icon: XCircle,
    color: 'text-muted-foreground',
    bgColor: 'bg-muted'
  },
  [McpServerStatus.CONNECTING]: {
    label: '连接中',
    icon: RefreshCw,
    color: 'text-blue-600 dark:text-blue-400',
    bgColor: 'bg-blue-100 dark:bg-blue-900/30'
  },
  [McpServerStatus.ERROR]: {
    label: '错误',
    icon: AlertCircle,
    color: 'text-destructive',
    bgColor: 'bg-destructive/10'
  }
};

/**
 * MCP 服务器卡片组件
 */
export const McpServerCard = memo<McpServerCardProps>(function McpServerCard({
  server,
  connectionStatus = McpServerStatus.DISCONNECTED,
  isSelected = false,
  onClick,
  onEdit,
  onDelete,
  onStart,
  onStop,
  onTest,
  onToggleEnabled,
  className
}: McpServerCardProps) {
  const status = statusConfig[connectionStatus] || statusConfig[McpServerStatus.DISCONNECTED];

  const handleEdit = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onEdit?.();
    },
    [onEdit]
  );

  const handleDelete = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onDelete?.();
    },
    [onDelete]
  );

  const handleStart = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onStart?.();
    },
    [onStart]
  );

  const handleStop = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onStop?.();
    },
    [onStop]
  );

  const handleTest = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onTest?.();
    },
    [onTest]
  );

  const handleToggleEnabled = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onToggleEnabled?.({ ...server, enabled: !server.enabled });
    },
    [server, onToggleEnabled]
  );

  const isConnecting = connectionStatus === McpServerStatus.CONNECTING;
  const isConnected = connectionStatus === McpServerStatus.CONNECTED;
  const isError = connectionStatus === McpServerStatus.ERROR;

  return (
    <div
      onClick={onClick}
      className={cn(
        'group relative rounded-lg border bg-background p-4 transition-all cursor-pointer',
        'hover:shadow-md hover:border-primary/50',
        isSelected && 'border-primary bg-primary/5',
        !server.enabled && 'opacity-60',
        className
      )}
    >
      {/* 头部 */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-2xl">🔌</span>
          <div>
            <h3 className="font-medium text-sm">{server.name}</h3>
            <span className={cn('text-xs px-2 py-0.5 rounded', status.bgColor, status.color)}>
              {isConnecting && (
                <RefreshCw className="h-3 w-3 inline animate-spin mr-1" />
              )}
              {status.label}
            </span>
          </div>
        </div>
        {/* 启用/禁用开关 */}
        <button
          type="button"
          onClick={handleToggleEnabled}
          className={cn(
            'flex items-center gap-1 px-2 py-1 rounded text-xs font-medium transition-colors',
            server.enabled
              ? 'bg-green-100 text-green-700 hover:bg-green-200 dark:bg-green-900 dark:text-green-300'
              : 'bg-muted text-muted-foreground hover:bg-muted/80'
          )}
          title={server.enabled ? '禁用服务器' : '启用服务器'}
        >
          <Power className={cn('w-3 h-3', server.enabled && 'text-green-600 dark:text-green-400')} />
          {server.enabled ? '已启用' : '已禁用'}
        </button>
      </div>

      {/* 描述 */}
      <p className="text-xs text-muted-foreground line-clamp-2 mb-3">
        {server.description || '暂无描述'}
      </p>

      {/* 命令 */}
      <div className="mb-3">
        <code className="text-xs bg-muted px-2 py-1 rounded block truncate">
          {server.command} {server.args?.join(' ') || ''}
        </code>
      </div>

      {/* 能力标签 */}
      {server.capabilities && server.capabilities.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-3">
          {server.capabilities.slice(0, 3).map((cap) => (
            <span
              key={cap}
              className="text-xs px-1.5 py-0.5 bg-muted rounded text-muted-foreground"
            >
              {cap}
            </span>
          ))}
          {server.capabilities.length > 3 && (
            <span className="text-xs text-muted-foreground">
              +{server.capabilities.length - 3}
            </span>
          )}
        </div>
      )}

      {/* 错误信息 */}
      {isError && server.error && (
        <div className="mb-3 text-xs text-destructive bg-destructive/10 rounded p-2">
          {server.error}
        </div>
      )}

      {/* 上次连接时间 */}
      {server.lastConnected && (
        <div className="text-xs text-muted-foreground mb-3">
          上次连接: {new Date(server.lastConnected).toLocaleString()}
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {isConnected ? (
          onStop && (
            <Button variant="ghost" size="icon" onClick={handleStop} title="停止">
              <Square className="h-4 w-4" />
            </Button>
          )
        ) : !isConnecting && onStart ? (
          <Button variant="ghost" size="icon" onClick={handleStart} title="启动">
            <Play className="h-4 w-4" />
          </Button>
        ) : null}
        {onTest && !isConnecting && (
          <Button variant="ghost" size="icon" onClick={handleTest} title="测试连接">
            <RefreshCw className="h-4 w-4" />
          </Button>
        )}
        {onEdit && (
          <Button variant="ghost" size="icon" onClick={handleEdit} title="编辑">
            <Edit2 className="h-4 w-4" />
          </Button>
        )}
        {onDelete && (
          <Button
            variant="ghost"
            size="icon"
            onClick={handleDelete}
            title="删除"
            className="hover:text-destructive hover:bg-destructive/10"
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        )}
      </div>
    </div>
  );
});
