/**
 * McpServerList - MCP 服务器列表组件
 *
 * 展示 MCP 服务器列表，支持网格布局。
 */

import { memo } from 'react';
import { FileX } from 'lucide-react';
import type { McpServer, McpServerStatus } from '@/shared/types';
import { McpServerCard } from './McpServerCard';
import { cn } from '@/shared/utils/cn';

export interface McpServerListProps {
  /** MCP 服务器列表 */
  servers: McpServer[];
  /** 连接状态映射 */
  connectionStatuses?: Record<string, McpServerStatus>;
  /** 选中的服务器 ID */
  selectedId?: string;
  /** 点击服务器回调 */
  onSelect?: (server: McpServer) => void;
  /** 编辑服务器回调 */
  onEdit?: (server: McpServer) => void;
  /** 删除服务器回调 */
  onDelete?: (serverId: string) => void;
  /** 启动服务器回调 */
  onStart?: (server: McpServer) => void;
  /** 停止服务器回调 */
  onStop?: (server: McpServer) => void;
  /** 测试连接回调 */
  onTest?: (server: McpServer) => void;
  /** 配置服务器回调 */
  onConfigure?: (server: McpServer) => void;
  className?: string;
}

/**
 * MCP 服务器列表组件
 */
export const McpServerList = memo<McpServerListProps>(function McpServerList({
  servers,
  connectionStatuses = {},
  selectedId,
  onSelect,
  onEdit,
  onDelete,
  onStart,
  onStop,
  onTest,
  onConfigure,
  className
}: McpServerListProps) {
  if (servers.length === 0) {
    return (
      <div className={cn('flex flex-col items-center justify-center py-12 text-center', className)}>
        <FileX className="h-12 w-12 text-muted-foreground/30 mb-4" />
        <p className="text-muted-foreground">暂无 MCP 服务器</p>
        <p className="text-xs text-muted-foreground mt-1">点击上方按钮添加第一个 MCP 服务器</p>
      </div>
    );
  }

  return (
    <div className={cn('grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4', className)}>
      {servers.map((server) => (
        <McpServerCard
          key={server.id}
          server={server}
          connectionStatus={connectionStatuses[server.id] || server.status || 'disconnected'}
          isSelected={server.id === selectedId}
          onClick={() => onSelect?.(server)}
          onEdit={() => onEdit?.(server)}
          onDelete={() => onDelete?.(server.id)}
          onStart={() => onStart?.(server)}
          onStop={() => onStop?.(server)}
          onTest={() => onTest?.(server)}
          onConfigure={() => onConfigure?.(server)}
        />
      ))}
    </div>
  );
});
