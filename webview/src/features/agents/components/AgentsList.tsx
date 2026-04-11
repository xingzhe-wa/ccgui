/**
 * AgentsList - Agents 列表组件
 *
 * 展示 Agents 列表，支持网格布局。
 */

import { memo } from 'react';
import { FileX } from 'lucide-react';
import type { Agent } from '@/shared/types';
import { AgentCard } from './AgentCard';
import { cn } from '@/shared/utils/cn';

export interface AgentsListProps {
  /** Agents 列表 */
  agents: Agent[];
  /** 选中的 Agent ID */
  selectedId?: string;
  /** 运行中的 Agent ID 集合 */
  runningIds?: Set<string>;
  /** 点击 Agent 回调 */
  onSelect?: (agent: Agent) => void;
  /** 编辑 Agent 回调 */
  onEdit?: (agent: Agent) => void;
  /** 删除 Agent 回调 */
  onDelete?: (agentId: string) => void;
  /** 启动 Agent 回调 */
  onStart?: (agent: Agent) => void;
  /** 停止 Agent 回调 */
  onStop?: (agent: Agent) => void;
  /** 复制 Agent 回调 */
  onDuplicate?: (agent: Agent) => void;
  /** 配置 Agent 回调 */
  onConfigure?: (agent: Agent) => void;
  /** 启用/禁用 Agent 回调 */
  onToggleEnabled?: (agent: Agent) => void;
  className?: string;
}

/**
 * Agents 列表组件
 */
export const AgentsList = memo<AgentsListProps>(function AgentsList({
  agents,
  selectedId,
  runningIds = new Set(),
  onSelect,
  onEdit,
  onDelete,
  onStart,
  onStop,
  onDuplicate,
  onConfigure,
  onToggleEnabled,
  className
}: AgentsListProps) {
  if (agents.length === 0) {
    return (
      <div className={cn('flex flex-col items-center justify-center py-12 text-center', className)}>
        <FileX className="h-12 w-12 text-muted-foreground/30 mb-4" />
        <p className="text-muted-foreground">暂无 Agents</p>
        <p className="text-xs text-muted-foreground mt-1">点击上方按钮创建第一个 Agent</p>
      </div>
    );
  }

  return (
    <div className={cn('grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4', className)}>
      {agents.map((agent) => (
        <AgentCard
          key={agent.id}
          agent={agent}
          isSelected={agent.id === selectedId}
          isRunning={runningIds.has(agent.id)}
          onClick={() => onSelect?.(agent)}
          onEdit={() => onEdit?.(agent)}
          onDelete={() => onDelete?.(agent.id)}
          onStart={() => onStart?.(agent)}
          onStop={() => onStop?.(agent)}
          onDuplicate={() => onDuplicate?.(agent)}
          onConfigure={() => onConfigure?.(agent)}
          onToggleEnabled={onToggleEnabled}
        />
      ))}
    </div>
  );
});
