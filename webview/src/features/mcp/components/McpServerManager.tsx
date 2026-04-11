/**
 * McpServerManager - MCP 服务器管理器组件
 *
 * 统一管理 MCP 服务器的 CRUD 操作和列表展示。
 */

import { memo, useCallback, useState, useEffect } from 'react';
import { Plus, Search, RefreshCw } from 'lucide-react';
import type { McpServer } from '@/shared/types';
import { McpServerStatus } from '@/shared/types/ecosystem';
import { useMcpStore } from '@/shared/stores/mcpStore';
import { McpServerList } from './McpServerList';
import { McpServerConfig } from './McpServerConfig';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';
import { javaBridge } from '@/lib/java-bridge';

export interface McpServerManagerProps {
  className?: string;
}

/**
 * MCP 服务器管理器
 */
export const McpServerManager = memo<McpServerManagerProps>(function McpServerManager({
  className
}: McpServerManagerProps) {
  const {
    servers,
    connectionStatuses,
    isLoading,
    saveServer,
    deleteServer,
    startServer,
    stopServer,
    testConnection,
    refreshServers
  } = useMcpStore();

  const [selectedServerId, setSelectedServerId] = useState<string | undefined>();
  const [editingServer, setEditingServer] = useState<McpServer | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // 加载数据
  useEffect(() => {
    refreshServers();
  }, [refreshServers]);

  // 过滤后的 servers
  const filteredServers = servers.filter(
    (server) =>
      server.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      server.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      server.command.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // 获取连接中的服务器数量
  const connectingCount = Object.values(connectionStatuses).filter(
    (status) => status === McpServerStatus.CONNECTING
  ).length;

  // 创建
  const handleCreate = useCallback(() => {
    setEditingServer(null);
    setIsCreating(true);
  }, []);

  // 编辑
  const handleEdit = useCallback((server: McpServer) => {
    setEditingServer(server);
    setIsCreating(false);
  }, []);

  // 保存
  const handleSave = useCallback(
    (server: McpServer) => {
      saveServer(server);
      setEditingServer(null);
      setIsCreating(false);
    },
    [saveServer]
  );

  // 删除
  const handleDelete = useCallback(
    (serverId: string) => {
      deleteServer(serverId);
      if (selectedServerId === serverId) {
        setSelectedServerId(undefined);
      }
    },
    [deleteServer, selectedServerId]
  );

  // 关闭编辑器
  const handleCloseEditor = useCallback(() => {
    setEditingServer(null);
    setIsCreating(false);
  }, []);

  // 启动
  const handleStart = useCallback(
    async (server: McpServer) => {
      try {
        startServer(server.id); // 先更新本地状态为CONNECTED
        await javaBridge.startMcpServer(server.id);
        // 成功后保持CONNECTED状态（由javaBridge调用结果决定）
      } catch {
        testConnection(server.id, false); // 失败时更新为ERROR
      }
    },
    [startServer, testConnection]
  );

  // 停止
  const handleStop = useCallback(
    async (server: McpServer) => {
      try {
        await javaBridge.stopMcpServer(server.id);
        stopServer(server.id); // 成功后更新为DISCONNECTED
      } catch {
        stopServer(server.id); // 失败也停止
      }
    },
    [stopServer]
  );

  // 测试连接
  const handleTest = useCallback(
    async (server: McpServer) => {
      try {
        testConnection(server.id, true); // 先设置为连接中
        const result = await javaBridge.testMcpServer(server.id);
        testConnection(server.id, result.success === true);
      } catch {
        testConnection(server.id, false);
      }
    },
    [testConnection]
  );

  // 刷新
  const handleRefresh = useCallback(() => {
    refreshServers();
  }, [refreshServers]);

  // 启用/禁用
  const handleToggleEnabled = useCallback(
    (server: McpServer) => {
      saveServer(server);
    },
    [saveServer]
  );

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* 头部 */}
      <div className="flex items-center justify-between px-6 py-4 border-b">
        <div>
          <h1 className="text-xl font-semibold">MCP 服务器</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {servers.length} 个服务器，{connectingCount} 个连接中
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleRefresh} disabled={isLoading}>
            <RefreshCw className={cn('h-4 w-4 mr-2', isLoading && 'animate-spin')} />
            刷新
          </Button>
          <Button onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            添加服务器
          </Button>
        </div>
      </div>

      {/* 搜索栏 */}
      <div className="px-6 py-3 border-b">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="search"
            placeholder="搜索服务器..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9 w-full max-w-md px-3 py-2 rounded-md border border-input bg-background text-sm"
          />
        </div>
      </div>

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto p-6">
        <McpServerList
          servers={filteredServers}
          connectionStatuses={connectionStatuses}
          selectedId={selectedServerId}
          onSelect={(server) => setSelectedServerId(server.id)}
          onEdit={handleEdit}
          onDelete={handleDelete}
          onStart={handleStart}
          onStop={handleStop}
          onTest={handleTest}
          onToggleEnabled={handleToggleEnabled}
        />
      </div>

      {/* 编辑器弹窗 */}
      {(isCreating || editingServer) && (
        <McpServerConfig
          server={editingServer}
          onSave={handleSave}
          onClose={handleCloseEditor}
        />
      )}
    </div>
  );
});
