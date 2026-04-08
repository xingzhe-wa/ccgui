/**
 * ConnectionStatus - MCP 连接状态指示器组件
 *
 * 显示 MCP 服务器的连接状态。
 */

import { memo } from 'react';
import { CheckCircle2, XCircle, Loader2, AlertCircle } from 'lucide-react';
import { McpServerStatus } from '@/shared/types';
import { cn } from '@/shared/utils/cn';

export interface ConnectionStatusProps {
  /** 连接状态 */
  status: McpServerStatus;
  /** 是否显示标签 */
  showLabel?: boolean;
  /** 尺寸 */
  size?: 'sm' | 'md' | 'lg';
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
    icon: Loader2,
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

const sizeConfig = {
  sm: { icon: 'h-3 w-3', text: 'text-xs', dot: 'h-1.5 w-1.5' },
  md: { icon: 'h-4 w-4', text: 'text-sm', dot: 'h-2 w-2' },
  lg: { icon: 'h-5 w-5', text: 'text-base', dot: 'h-2.5 w-2.5' }
};

/**
 * 连接状态指示器
 */
export const ConnectionStatus = memo<ConnectionStatusProps>(function ConnectionStatus({
  status,
  showLabel = true,
  size = 'md',
  className
}: ConnectionStatusProps) {
  const config = statusConfig[status] ?? statusConfig[McpServerStatus.DISCONNECTED];
  const sizes = sizeConfig[size];
  const Icon = config.icon;

  const isConnecting = status === McpServerStatus.CONNECTING;

  return (
    <div className={cn('flex items-center gap-1.5', className)}>
      {isConnecting ? (
        <Icon className={cn(sizes.icon, config.color, 'animate-spin')} />
      ) : (
        <Icon className={cn(sizes.icon, config.color)} />
      )}
      {showLabel && (
        <span className={cn(sizes.text, config.color)}>{config.label}</span>
      )}
    </div>
  );
});
