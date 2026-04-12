/**
 * SlashCommandPalette - 斜杠命令面板
 *
 * 触发条件：ChatInput 输入 '/' 时显示浮动面板
 * 支持命令：/compact, /clear, /model, /mode, /session, /retry, /export
 */

import { memo, useEffect, useRef, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { javaBridge } from '@/lib/java-bridge';
import { useChatConfigStore } from '@/shared/stores/chatConfigStore';
import { cn } from '@/shared/utils/cn';

export interface SlashCommand {
  command: string;
  description: string;
  icon?: string;
}

/**
 * 需要导航的路由命令
 */
const NAV_ROUTES: Record<string, string> = {
  '/model': '/settings?tab=model',
  '/session': '/history'
};

const DEFAULT_COMMANDS: SlashCommand[] = [
  {
    command: '/compact',
    description: '压缩上下文，减少 token 消耗',
    icon: '🗜️'
  },
  {
    command: '/clear',
    description: '清空当前会话的所有消息',
    icon: '🗑️'
  },
  {
    command: '/retry',
    description: '重试上一条 AI 回复',
    icon: '🔄'
  },
  {
    command: '/export',
    description: '导出会话内容',
    icon: '📤'
  },
  {
    command: '/model',
    description: '切换 AI 模型',
    icon: '🤖'
  },
  {
    command: '/mode',
    description: '切换对话模式 (thinking/plan/auto)',
    icon: '🧠'
  },
  {
    command: '/session',
    description: '查看会话历史',
    icon: '💬'
  }
];

interface SlashCommandPaletteProps {
  filter: string;        // 当前输入框文本（以 / 开头）
  onSelect: (command: string) => void;
  onClose: () => void;
  className?: string;
}

export const SlashCommandPalette = memo<SlashCommandPaletteProps>(function SlashCommandPalette({
  filter,
  onSelect,
  onClose,
  className
}) {
  const navigate = useNavigate();
  const [selectedIndex, setSelectedIndex] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);
  const itemHeight = 36; // 每个命令项的高度

  // 根据 filter 过滤命令
  const filteredCommands = DEFAULT_COMMANDS.filter(cmd =>
    cmd.command.toLowerCase().includes(filter.toLowerCase())
  );

  /**
   * 执行命令逻辑
   * 统一使用 React Router 的 navigate 进行路由跳转
   */
  const executeCommand = useCallback(async (cmd: SlashCommand) => {
    // 路由导航命令
    const navPath = NAV_ROUTES[cmd.command];
    if (navPath) {
      navigate(navPath);
      return;
    }

    // 状态更新命令
    if (cmd.command === '/mode') {
      const modeStore = useChatConfigStore.getState();
      const currentMode = modeStore.conversationMode as string || 'AUTO';
      const modeOrder = ['AUTO', 'THINKING', 'PLANNING'];
      const idx = modeOrder.indexOf(currentMode);
      const next = modeOrder[(idx + 1) % 3] as 'AUTO' | 'THINKING' | 'PLANNING';
      modeStore.setConversationMode(next);
      return;
    }

    // Slash 命令执行
    switch (cmd.command) {
      case '/compact':
      case '/retry':
        await javaBridge.executeSlashCommand(cmd.command);
        break;
      case '/clear':
        await javaBridge.executeSlashCommand('/clear');
        window.location.reload();
        break;
      case '/export': {
        const result = await javaBridge.executeSlashCommand('/export');
        if (result.success) {
          console.log('[SlashCommandPalette] Export result:', result);
        }
        break;
      }
    }
  }, [navigate]);

  // 监听键盘事件
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault();
          setSelectedIndex(prev =>
            prev < filteredCommands.length - 1 ? prev + 1 : 0
          );
          break;
        case 'ArrowUp':
          e.preventDefault();
          setSelectedIndex(prev =>
            prev > 0 ? prev - 1 : filteredCommands.length - 1
          );
          break;
        case 'Enter':
          e.preventDefault();
          if (filteredCommands[selectedIndex]) {
            handleSelect(filteredCommands[selectedIndex]);
          }
          break;
        case 'Escape':
          e.preventDefault();
          onClose();
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [filteredCommands, selectedIndex, onClose]);

  // 滚动时自动调整选中项
  useEffect(() => {
    if (listRef.current) {
      const visibleTop = listRef.current.scrollTop;
      const visibleBottom = visibleTop + listRef.current.clientHeight;
      const selectedTop = selectedIndex * itemHeight;
      const selectedBottom = selectedTop + itemHeight;

      if (selectedBottom > visibleBottom) {
        listRef.current.scrollTop = selectedBottom - listRef.current.clientHeight;
      } else if (selectedTop < visibleTop) {
        listRef.current.scrollTop = selectedTop;
      }
    }
  }, [selectedIndex]);

  const handleSelect = useCallback((cmd: SlashCommand) => {
    executeCommand(cmd);
    onSelect(cmd.command);
  }, [executeCommand, onSelect]);

  if (filteredCommands.length === 0) {
    return (
      <div className={cn(
        'absolute bottom-full left-0 mb-1 w-64',
        'bg-popover border border-border rounded-md shadow-lg',
        'px-3 py-2 text-xs text-muted-foreground',
        className
      )}>
        暂无匹配命令
      </div>
    );
  }

  return (
    <div
      ref={listRef}
      className={cn(
        'absolute bottom-full left-0 mb-1 w-64 max-h-64 overflow-auto',
        'bg-popover border border-border rounded-md shadow-lg',
        'py-1 z-50',
        className
      )}
    >
      {filteredCommands.map((cmd, index) => (
        <button
          key={cmd.command}
          onClick={() => handleSelect(cmd)}
          onMouseEnter={() => setSelectedIndex(index)}
          className={cn(
            'w-full px-3 py-2 flex items-start gap-2 text-left',
            'transition-colors',
            index === selectedIndex ? 'bg-accent' : 'hover:bg-accent/50'
          )}
        >
          <span className="font-mono text-xs text-primary mt-0.5">
            {cmd.command}
          </span>
          <span className="text-xs text-muted-foreground flex-1">
            {cmd.description}
          </span>
        </button>
      ))}
    </div>
  );
});
