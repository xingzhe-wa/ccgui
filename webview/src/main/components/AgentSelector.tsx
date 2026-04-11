/**
 * AgentSelector - Agent 选择组件 V5
 *
 * 使用固定定位和高 z-index 确保下拉列表不被边界遮挡
 */

import { memo, useEffect, useState, useRef, useCallback } from 'react';
import { useChatConfigStore } from '@/shared/stores/chatConfigStore';
import { useAgentsStore } from '@/shared/stores/agentsStore';
import { AgentMode } from '@/shared/types/ecosystem';
import { cn } from '@/shared/utils/cn';

interface AgentSelectorProps {
  className?: string;
}

const modeBadgeClass = (mode: AgentMode) =>
  cn(
    'text-[10px] px-1 rounded',
    mode === AgentMode.CAUTIOUS && 'bg-blue-500/20 text-blue-400',
    mode === AgentMode.BALANCED && 'bg-green-500/20 text-green-400',
    mode === AgentMode.AGGRESSIVE && 'bg-red-500/20 text-red-400'
  );

export const AgentSelector = memo<AgentSelectorProps>(function AgentSelector({
  className
}) {
  const { currentAgentId, setCurrentAgent } = useChatConfigStore();
  const { agents, refreshAgents } = useAgentsStore();

  const [isExpanded, setIsExpanded] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    refreshAgents();
  }, [refreshAgents]);

  // 点击外部关闭
  useEffect(() => {
    if (!isExpanded) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsExpanded(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isExpanded]);

  const currentAgent = agents.find(a => a.id === currentAgentId);

  const handleSelect = useCallback((agentId: string | null) => {
    setCurrentAgent(agentId);
    setIsExpanded(false);
  }, [setCurrentAgent]);

  return (
    <div ref={containerRef} className={cn('relative', className)}>
      {/* 触发按钮 */}
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setIsExpanded(!isExpanded)}
        className={cn(
          'flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs transition-colors',
          'border border-border hover:border-primary/50',
          currentAgentId
            ? 'bg-primary/10 text-primary'
            : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
        )}
      >
        <span className="truncate max-w-[120px]">
          {currentAgent ? currentAgent.name : '无 Agent'}
        </span>
        {currentAgent && (
          <span className={modeBadgeClass(currentAgent.mode)}>
            {currentAgent.mode.slice(0, 4)}
          </span>
        )}
        <svg
          className={cn('w-3.5 h-3.5 shrink-0 transition-transform', isExpanded && 'rotate-180')}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* 展开的列表 - 使用向上展开避免被遮挡 */}
      {isExpanded && (
        <div
          className="absolute z-[10000] bg-popover border border-border rounded-lg shadow-lg overflow-hidden min-w-[288px]"
          style={{
            bottom: '100%',
            left: '0',
            marginBottom: '4px'
          }}
        >
          {/* "无" 选项 */}
          <button
            type="button"
            onClick={() => handleSelect(null)}
            className={cn(
              'w-full flex items-center gap-2 px-3 py-2.5 text-xs text-left hover:bg-muted transition-colors',
              !currentAgentId && 'bg-primary/10 text-primary'
            )}
          >
            <span className="flex-1 font-medium">无 Agent</span>
            {!currentAgentId && (
              <svg className="w-3.5 h-3.5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            )}
          </button>

          {/* 分隔线 */}
          {agents.length > 0 && <div className="border-t border-border" />}

          {/* Agent 列表 */}
          <div className="max-h-64 overflow-y-auto py-1">
            {agents.map(agent => {
              const isSelected = agent.id === currentAgentId;
              return (
                <button
                  key={agent.id}
                  type="button"
                  onClick={() => handleSelect(agent.id)}
                  className={cn(
                    'w-full flex items-center gap-2 px-3 py-2.5 text-xs text-left hover:bg-muted transition-colors',
                    isSelected && 'bg-primary/10'
                  )}
                >
                  <div className="flex-1 min-w-0">
                    <div className={cn('truncate font-medium', isSelected ? 'text-primary' : 'text-foreground')}>
                      {agent.name}
                    </div>
                    <div className="text-muted-foreground truncate text-[10px]">
                      {agent.description || agent.id}
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <span className={modeBadgeClass(agent.mode)}>
                      {agent.mode.slice(0, 4)}
                    </span>
                    {isSelected && (
                      <svg className="w-3.5 h-3.5 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                  </div>
                </button>
              );
            })}
          </div>

          {agents.length === 0 && (
            <div className="px-3 py-6 text-xs text-muted-foreground text-center">
              暂无可用 Agent
            </div>
          )}
        </div>
      )}
    </div>
  );
});
