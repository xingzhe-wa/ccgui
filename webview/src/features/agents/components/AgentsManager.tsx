/**
 * AgentsManager - Agents 管理器组件
 *
 * 统一管理 Agents 的 CRUD 操作和列表展示。
 */

import { memo, useCallback, useState, useEffect } from 'react';
import { Plus, Search } from 'lucide-react';
import type { Agent } from '@/shared/types';
import { useAgentsStore } from '@/shared/stores/agentsStore';
import { AgentsList } from './AgentsList';
import { AgentEditor } from './AgentEditor';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface AgentsManagerProps {
  className?: string;
}

/**
 * Agents 管理器
 */
export const AgentsManager = memo<AgentsManagerProps>(function AgentsManager({
  className
}: AgentsManagerProps) {
  const { agents, runningAgentIds, createAgent, updateAgent, deleteAgent, startAgent, stopAgent, refreshAgents } =
    useAgentsStore();

  const [selectedAgentId, setSelectedAgentId] = useState<string | undefined>();
  const [editingAgent, setEditingAgent] = useState<Agent | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // 加载数据
  useEffect(() => {
    refreshAgents();
  }, [refreshAgents]);

  // 过滤后的 agents
  const filteredAgents = agents.filter(
    (agent) =>
      agent.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      agent.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // 创建
  const handleCreate = useCallback(() => {
    setEditingAgent(null);
    setIsCreating(true);
  }, []);

  // 编辑
  const handleEdit = useCallback((agent: Agent) => {
    setEditingAgent(agent);
    setIsCreating(false);
  }, []);

  // 保存
  const handleSave = useCallback(
    (agent: Agent) => {
      if (editingAgent) {
        updateAgent(agent);
      } else {
        createAgent(agent);
      }
      setEditingAgent(null);
      setIsCreating(false);
    },
    [editingAgent, createAgent, updateAgent]
  );

  // 删除
  const handleDelete = useCallback(
    (agentId: string) => {
      deleteAgent(agentId);
      if (selectedAgentId === agentId) {
        setSelectedAgentId(undefined);
      }
    },
    [deleteAgent, selectedAgentId]
  );

  // 关闭编辑器
  const handleCloseEditor = useCallback(() => {
    setEditingAgent(null);
    setIsCreating(false);
  }, []);

  // 启动/停止
  const handleStart = useCallback(
    (agent: Agent) => {
      startAgent(agent.id);
    },
    [startAgent]
  );

  const handleStop = useCallback(
    (agent: Agent) => {
      stopAgent(agent.id);
    },
    [stopAgent]
  );

  // 复制
  const handleDuplicate = useCallback(
    (agent: Agent) => {
      const duplicated: Agent = {
        ...agent,
        id: `agent-${Date.now()}`,
        name: `${agent.name} (副本)`
      };
      createAgent(duplicated);
    },
    [createAgent]
  );

  // 配置
  const handleConfigure = useCallback((agent: Agent) => {
    setEditingAgent(agent);
    setIsCreating(false);
  }, []);

  // 启用/禁用
  const handleToggleEnabled = useCallback((agent: Agent) => {
    updateAgent(agent);
  }, [updateAgent]);

  return (
    <div className={cn('flex flex-col h-full', className)}>
      {/* 头部 */}
      <div className="flex items-center justify-between px-6 py-4 border-b">
        <div>
          <h1 className="text-xl font-semibold">Agents</h1>
          <p className="text-sm text-muted-foreground mt-0.5">
            {agents.length} 个 Agents，{runningAgentIds.size} 个运行中
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button onClick={handleCreate}>
            <Plus className="h-4 w-4 mr-2" />
            新建 Agent
          </Button>
        </div>
      </div>

      {/* 搜索栏 */}
      <div className="px-6 py-3 border-b">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="search"
            placeholder="搜索 Agents..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-9 w-full max-w-md px-3 py-2 rounded-md border border-input bg-background text-sm"
          />
        </div>
      </div>

      {/* 列表 */}
      <div className="flex-1 overflow-y-auto p-6">
        <AgentsList
          agents={filteredAgents}
          selectedId={selectedAgentId}
          runningIds={runningAgentIds}
          onSelect={(agent) => setSelectedAgentId(agent.id)}
          onEdit={handleEdit}
          onDelete={handleDelete}
          onStart={handleStart}
          onStop={handleStop}
          onDuplicate={handleDuplicate}
          onConfigure={handleConfigure}
          onToggleEnabled={handleToggleEnabled}
        />
      </div>

      {/* 编辑器弹窗 */}
      {(isCreating || editingAgent) && (
        <AgentEditor
          agent={editingAgent}
          onSave={handleSave}
          onClose={handleCloseEditor}
        />
      )}
    </div>
  );
});
