/**
 * ModeSwitcher - 对话模式切换组件
 *
 * 支持 AUTO / THINKING / PLANNING 三种模式切换
 * Hover 时显示浮层详细说明
 */

import { memo, useState, useRef } from 'react';
import { useChatConfigStore, type ConversationMode } from '@/shared/stores/chatConfigStore';
import { cn } from '@/shared/utils/cn';

interface ModeInfo {
  value: ConversationMode;
  label: string;
  shortDesc: string;
  popoverTitle: string;
  popoverContent: string;
}

const MODES: ModeInfo[] = [
  {
    value: 'AUTO',
    label: 'AUTO',
    shortDesc: '智能自动',
    popoverTitle: 'AUTO 智能模式',
    popoverContent: '智能自动模式，根据任务复杂度自动选择合适模型。适合日常问答和简单任务，快速响应。'
  },
  {
    value: 'THINKING',
    label: 'THINK',
    shortDesc: '深度思考',
    popoverTitle: 'THINKING 深度思考模式',
    popoverContent: '深度思考模式，使用 Opus 模型进行多步推理。适合复杂问题、代码调试和架构设计，提供更详细的分析过程。'
  },
  {
    value: 'PLANNING',
    label: 'PLAN',
    shortDesc: '规划先行',
    popoverTitle: 'PLANNING 规划模式',
    popoverContent: '规划先行模式，先制定执行计划再逐步实施。适合大型重构、多文件修改和复杂任务，避免走弯路。'
  }
];

interface ModeSwitcherProps {
  className?: string;
}

export const ModeSwitcher = memo<ModeSwitcherProps>(function ModeSwitcher({
  className
}) {
  const { conversationMode, setConversationMode } = useChatConfigStore();
  const [hoveredMode, setHoveredMode] = useState<ConversationMode | null>(null);
  const [popoverPosition, setPopoverPosition] = useState<'top' | 'bottom'>('bottom');
  const containerRef = useRef<HTMLDivElement>(null);

  const handleMouseEnter = (mode: ConversationMode) => {
    // Determine popover position based on available space
    if (containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      setPopoverPosition(spaceBelow < 200 ? 'top' : 'bottom');
    }
    setHoveredMode(mode);
  };

  return (
    <div ref={containerRef} className={cn('relative', className)}>
      <div className="flex items-center gap-1">
        {MODES.map((mode) => (
          <button
            key={mode.value}
            onClick={() => setConversationMode(mode.value)}
            onMouseEnter={() => handleMouseEnter(mode.value)}
            onMouseLeave={() => setHoveredMode(null)}
            title={`${mode.shortDesc}：${mode.popoverContent.slice(0, 30)}...`}
            className={cn(
              'px-2 py-1 rounded text-xs font-medium transition-colors relative',
              conversationMode === mode.value
                ? 'bg-primary text-primary-foreground'
                : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
            )}
          >
            {mode.label}
          </button>
        ))}
      </div>

      {/* Hover Popover */}
      {hoveredMode && (
        <div
          className={cn(
            'absolute left-0 z-50 w-64 p-3 rounded-lg border shadow-lg bg-popover',
            'animate-in fade-in zoom-in-95 duration-150',
            popoverPosition === 'bottom' ? 'top-full mt-1' : 'bottom-full mb-1'
          )}
          onMouseEnter={() => setHoveredMode(hoveredMode)}
          onMouseLeave={() => setHoveredMode(null)}
        >
          {MODES.filter(m => m.value === hoveredMode).map(mode => (
            <div key={mode.value}>
              <div className="font-medium text-sm mb-1">{mode.popoverTitle}</div>
              <div className="text-xs text-muted-foreground leading-relaxed">
                {mode.popoverContent}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
});
