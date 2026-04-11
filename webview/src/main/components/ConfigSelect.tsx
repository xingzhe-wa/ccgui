/**
 * ConfigSelect - 组合配置下拉选择器
 *
 * 参考 jetbrains-cc-gui 的 ConfigSelect 实现
 * 组合 Agent 选择、Streaming 开关、Thinking 模式切换
 */

import { useState, useEffect, useRef, useCallback, memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Settings, Bot, RefreshCw, Brain, ChevronRight, Plus, Info, Check } from 'lucide-react';
import { useChatConfigStore, type ConversationMode } from '@/shared/stores/chatConfigStore';
import { useAgentsStore } from '@/shared/stores/agentsStore';
import { cn } from '@/shared/utils/cn';

interface ConfigSelectProps {
  className?: string;
}

const THINKING_MODES: { mode: ConversationMode; label: string; icon: typeof Brain }[] = [
  { mode: 'AUTO', label: '自动', icon: Brain },
  { mode: 'THINKING', label: '深度思考', icon: Brain },
  { mode: 'PLANNING', label: '规划模式', icon: Brain },
];

export const ConfigSelect = memo<ConfigSelectProps>(function ConfigSelect({
  className
}) {
  const navigate = useNavigate();

  const {
    currentAgentId,
    setCurrentAgent,
    streamingEnabled,
    setStreamingEnabled,
    conversationMode,
    setConversationMode
  } = useChatConfigStore();

  const { agents, refreshAgents } = useAgentsStore();

  const [isOpen, setIsOpen] = useState(false);
  const [activeSubmenu, setActiveSubmenu] = useState<'none' | 'agent' | 'thinking'>('none');

  const buttonRef = useRef<HTMLButtonElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const currentAgent = agents.find(a => a.id === currentAgentId);
  const currentMode = THINKING_MODES.find(m => m.mode === conversationMode);

  // 加载 agents
  useEffect(() => {
    refreshAgents();
  }, [refreshAgents]);

  // 点击外部关闭
  useEffect(() => {
    if (!isOpen) return;

    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node) &&
        buttonRef.current &&
        !buttonRef.current.contains(e.target as Node)
      ) {
        setIsOpen(false);
        setActiveSubmenu('none');
      }
    };

    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleClickOutside);
    }, 0);

    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleToggle = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setIsOpen(!isOpen);
    if (!isOpen) {
      setActiveSubmenu('none');
    }
  }, [isOpen]);

  const handleAgentSelect = useCallback((agentId: string | null) => {
    setCurrentAgent(agentId);
    setIsOpen(false);
    setActiveSubmenu('none');
  }, [setCurrentAgent]);

  const handleOpenAgentSettings = useCallback(() => {
    setIsOpen(false);
    setActiveSubmenu('none');
    // 打开 Agent 设置页面
    navigate('/tools?tab=agents');
  }, [navigate]);

  const handleThinkingSelect = useCallback((mode: ConversationMode) => {
    setConversationMode(mode);
    setIsOpen(false);
    setActiveSubmenu('none');
  }, [setConversationMode]);

  const handleStreamingToggle = useCallback((e: React.MouseEvent) => {
    e.stopPropagation();
    setStreamingEnabled(!streamingEnabled);
  }, [streamingEnabled, setStreamingEnabled]);

  // 渲染 Agent 子菜单
  const renderAgentSubmenu = () => (
    <div
      className="absolute left-full bottom-0 ml-[-30px] z-[10001] min-w-[320px] max-w-[360px] max-h-[300px] overflow-y-auto bg-popover border border-border rounded-lg shadow-lg"
      onMouseEnter={(e) => {
        e.stopPropagation();
        setActiveSubmenu('agent');
      }}
    >
      <div className="py-1">
        {/* 无 Agent 选项 */}
        <button
          type="button"
          onClick={() => handleAgentSelect(null)}
          className={cn(
            'w-full flex items-center gap-2 px-3 py-2 text-xs text-left hover:bg-muted transition-colors',
            !currentAgentId && 'bg-primary/10 text-primary'
          )}
        >
          <Info className="w-4 h-4 shrink-0" />
          <div className="flex-1 min-w-0">
            <span className="font-medium">无 Agent</span>
          </div>
          {!currentAgentId && <Check className="w-4 h-4 shrink-0 text-primary" />}
        </button>

        <div className="border-t border-border my-1" />

        {/* Agent 列表 */}
        {agents.map(agent => {
          const isSelected = agent.id === currentAgentId;
          return (
            <button
              key={agent.id}
              type="button"
              onClick={() => handleAgentSelect(agent.id)}
              className={cn(
                'w-full flex items-start gap-2 px-3 py-2 text-xs text-left hover:bg-muted transition-colors',
                isSelected && 'bg-primary/10'
              )}
            >
              <Bot className="w-4 h-4 shrink-0 mt-0.5" />
              <div className="flex-1 min-w-0">
                <span className={cn('font-medium truncate block', isSelected ? 'text-primary' : 'text-foreground')}>
                  {agent.name}
                </span>
                {agent.description && (
                  <span className="text-muted-foreground truncate block text-[10px]">
                    {agent.description.length > 60 ? agent.description.substring(0, 60) + '...' : agent.description}
                  </span>
                )}
              </div>
              {isSelected && <Check className="w-4 h-4 shrink-0 text-primary" />}
            </button>
          );
        })}

        {/* 创建新 Agent */}
        <div className="border-t border-border my-1" />
        <button
          type="button"
          onClick={handleOpenAgentSettings}
          className="w-full flex items-center gap-2 px-3 py-2 text-xs text-left hover:bg-muted transition-colors text-primary"
        >
          <Plus className="w-4 h-4 shrink-0" />
          <div className="flex-1 min-w-0">
            <span className="font-medium">创建新 Agent</span>
            <span className="text-muted-foreground truncate block text-[10px]">打开 Agent 设置页面</span>
          </div>
        </button>
      </div>
    </div>
  );

  // 渲染 Thinking 子菜单
  const renderThinkingSubmenu = () => (
    <div
      className="absolute left-full bottom-0 ml-[-30px] z-[10001] min-w-[200px] bg-popover border border-border rounded-lg shadow-lg"
      onMouseEnter={(e) => {
        e.stopPropagation();
        setActiveSubmenu('thinking');
      }}
    >
      <div className="py-1">
        {THINKING_MODES.map(({ mode, label, icon: Icon }) => {
          const isSelected = mode === conversationMode;
          return (
            <button
              key={mode}
              type="button"
              onClick={() => handleThinkingSelect(mode)}
              className={cn(
                'w-full flex items-center gap-2 px-3 py-2 text-xs text-left hover:bg-muted transition-colors',
                isSelected && 'bg-primary/10 text-primary'
              )}
            >
              <Icon className="w-4 h-4 shrink-0" />
              <span className="flex-1 font-medium">{label}</span>
              {isSelected && <Check className="w-4 h-4 shrink-0 text-primary" />}
            </button>
          );
        })}
      </div>
    </div>
  );

  return (
    <div className={cn('relative inline-block', className)}>
      {/* 设置按钮 */}
      <button
        ref={buttonRef}
        type="button"
        onClick={handleToggle}
        className={cn(
          'flex items-center gap-1 px-2 py-1 rounded-md text-xs transition-colors',
          'border border-border hover:border-primary/50',
          currentAgentId
            ? 'bg-primary/10 text-primary'
            : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
        )}
        title="配置"
      >
        <Settings className="w-3.5 h-3.5" />
      </button>

      {/* 下拉菜单 */}
      {isOpen && (
        <div
          ref={dropdownRef}
          className="absolute z-[10000] bg-popover border border-border rounded-lg shadow-lg min-w-[200px]"
          style={{
            bottom: '100%',
            left: 0,
            marginBottom: '4px'
          }}
        >
          {/* Agent 选项 - 悬停显示子菜单 */}
          <div
            className="relative"
            onMouseEnter={() => setActiveSubmenu('agent')}
            onMouseLeave={() => setActiveSubmenu('none')}
          >
            <div className="flex items-center gap-2 px-3 py-2.5 text-xs hover:bg-muted cursor-pointer">
              <Bot className="w-4 h-4 shrink-0" />
              <div className="flex-1 min-w-0">
                <span className="font-medium">Agent</span>
                {currentAgent && (
                  <span className="text-muted-foreground truncate block text-[10px]">
                    {currentAgent.name}
                  </span>
                )}
              </div>
              <ChevronRight className="w-3 h-3 shrink-0 text-muted-foreground" />
            </div>

            {/* Agent 子菜单 */}
            {activeSubmenu === 'agent' && renderAgentSubmenu()}
          </div>

          {/* 分隔线 */}
          <div className="border-t border-border mx-2 my-1" />

          {/* Streaming 开关 */}
          <div
            className="flex items-center justify-between px-3 py-2.5 text-xs hover:bg-muted cursor-pointer"
            onClick={handleStreamingToggle}
          >
            <div className="flex items-center gap-2">
              <RefreshCw className={cn('w-4 h-4', streamingEnabled && 'text-primary')} />
              <span>流式输出</span>
            </div>
            <div
              className={cn(
                'w-9 h-5 rounded-full relative transition-colors',
                streamingEnabled ? 'bg-primary' : 'bg-muted'
              )}
            >
              <div
                className={cn(
                  'absolute top-0.5 w-4 h-4 rounded-full bg-white transition-transform',
                  streamingEnabled ? 'translate-x-4' : 'translate-x-0.5'
                )}
              />
            </div>
          </div>

          {/* 分隔线 */}
          <div className="border-t border-border mx-2 my-1" />

          {/* Thinking 模式 - 悬停显示子菜单 */}
          <div
            className="relative"
            onMouseEnter={() => setActiveSubmenu('thinking')}
            onMouseLeave={() => setActiveSubmenu('none')}
          >
            <div className="flex items-center gap-2 px-3 py-2.5 text-xs hover:bg-muted cursor-pointer">
              <Brain className={cn('w-4 h-4 shrink-0', conversationMode !== 'AUTO' && 'text-primary')} />
              <div className="flex-1 min-w-0">
                <span className="font-medium">思考模式</span>
                {currentMode && (
                  <span className="text-muted-foreground truncate block text-[10px]">
                    {currentMode.label}
                  </span>
                )}
              </div>
              <ChevronRight className="w-3 h-3 shrink-0 text-muted-foreground" />
            </div>

            {/* Thinking 子菜单 */}
            {activeSubmenu === 'thinking' && renderThinkingSubmenu()}
          </div>
        </div>
      )}
    </div>
  );
});
