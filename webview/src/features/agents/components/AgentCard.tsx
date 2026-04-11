/**
 * AgentCard - Agent 卡片组件
 *
 * 展示单个 Agent 的基本信息，支持编辑和删除操作。
 */

import { memo, useCallback, useState } from 'react';
import { Edit2, Trash2, Play, Copy, Settings, Power } from 'lucide-react';
import type { Agent } from '@/shared/types';
import { AgentMode } from '@/shared/types';
import { cn } from '@/shared/utils/cn';
import { Button } from '@/shared/components/ui/button/Button';

export interface AgentCardProps {
  /** Agent 数据 */
  agent: Agent;
  /** 是否选中 */
  isSelected?: boolean;
  /** 是否运行中 */
  isRunning?: boolean;
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
  /** 复制回调 */
  onDuplicate?: () => void;
  /** 配置回调 */
  onConfigure?: () => void;
  /** 启用/禁用回调 */
  onToggleEnabled?: (agent: Agent) => void;
  className?: string;
}

const modeLabels: Record<AgentMode, string> = {
  [AgentMode.CAUTIOUS]: '谨慎',
  [AgentMode.BALANCED]: '平衡',
  [AgentMode.AGGRESSIVE]: '激进'
};

const modeColors: Record<AgentMode, string> = {
  [AgentMode.CAUTIOUS]: 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300',
  [AgentMode.BALANCED]: 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300',
  [AgentMode.AGGRESSIVE]: 'bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300'
};

/**
 * Agent 卡片组件
 */
export const AgentCard = memo<AgentCardProps>(function AgentCard({
  agent,
  isSelected = false,
  isRunning = false,
  onClick,
  onEdit,
  onDelete,
  onStart,
  onStop,
  onDuplicate,
  onConfigure,
  onToggleEnabled,
  className
}: AgentCardProps) {
  const [localEnabled, setLocalEnabled] = useState(agent.enabled);

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

  const handleDuplicate = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onDuplicate?.();
    },
    [onDuplicate]
  );

  const handleConfigure = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      onConfigure?.();
    },
    [onConfigure]
  );

  const handleToggleEnabled = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      const newEnabled = !localEnabled;
      setLocalEnabled(newEnabled);
      onToggleEnabled?.({ ...agent, enabled: newEnabled });
    },
    [agent, localEnabled, onToggleEnabled]
  );

  // 同步外部 enabled 状态
  useState(() => {
    setLocalEnabled(agent.enabled);
  });

  return (
    <div
      onClick={onClick}
      className={cn(
        'group relative rounded-lg border bg-background p-4 transition-all cursor-pointer',
        'hover:shadow-md hover:border-primary/50',
        isSelected && 'border-primary bg-primary/5',
        !localEnabled && 'opacity-60',
        className
      )}
    >
      {/* 头部 */}
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="text-2xl">{agent.avatar || '🤖'}</span>
          <div>
            <h3 className="font-medium text-sm">{agent.name}</h3>
            <span
              className={cn(
                'text-xs px-2 py-0.5 rounded',
                modeColors[agent.mode]
              )}
            >
              {modeLabels[agent.mode]}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {/* 启用/禁用开关 */}
          <button
            type="button"
            onClick={handleToggleEnabled}
            className={cn(
              'flex items-center gap-1 px-2 py-1 rounded text-xs font-medium transition-colors',
              localEnabled
                ? 'bg-green-100 text-green-700 hover:bg-green-200 dark:bg-green-900 dark:text-green-300'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            )}
            title={localEnabled ? '禁用 Agent' : '启用 Agent'}
          >
            <Power className={cn('w-3 h-3', localEnabled && 'text-green-600 dark:text-green-400')} />
            {localEnabled ? '已启用' : '已禁用'}
          </button>
          {isRunning && (
            <span className="text-xs px-2 py-0.5 bg-green-100 rounded text-green-700 dark:bg-green-900 dark:text-green-300">
              运行中
            </span>
          )}
        </div>
      </div>

      {/* 描述 */}
      <p className="text-xs text-muted-foreground line-clamp-2 mb-3">
        {agent.description || '暂无描述'}
      </p>

      {/* 能力标签 */}
      {agent.capabilities && agent.capabilities.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-3">
          {agent.capabilities.slice(0, 3).map((cap) => (
            <span
              key={cap}
              className="text-xs px-1.5 py-0.5 bg-muted rounded text-muted-foreground"
            >
              {cap.replace('_', ' ')}
            </span>
          ))}
          {agent.capabilities.length > 3 && (
            <span className="text-xs text-muted-foreground">
              +{agent.capabilities.length - 3}
            </span>
          )}
        </div>
      )}

      {/* 工具数量 */}
      {agent.tools && agent.tools.length > 0 && (
        <div className="text-xs text-muted-foreground mb-3">
          {agent.tools.length} 个工具
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        {isRunning ? (
          onStop && (
            <Button variant="ghost" size="icon" onClick={handleStop} title="停止">
              <span className="h-4 w-4 flex items-center justify-center bg-destructive rounded-sm" />
            </Button>
          )
        ) : (
          onStart && (
            <Button variant="ghost" size="icon" onClick={handleStart} title="启动">
              <Play className="h-4 w-4" />
            </Button>
          )
        )}
        {onConfigure && (
          <Button variant="ghost" size="icon" onClick={handleConfigure} title="配置">
            <Settings className="h-4 w-4" />
          </Button>
        )}
        {onDuplicate && (
          <Button variant="ghost" size="icon" onClick={handleDuplicate} title="复制">
            <Copy className="h-4 w-4" />
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
