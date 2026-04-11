/**
 * ProviderQuickCards - 供应商快捷卡片
 *
 * 网格布局展示 AI 供应商，点击自动填充除 API Key 外的所有配置
 */

import { memo } from 'react';
import { PROVIDERS, type Provider } from './ProviderSelector';
import { cn } from '@/shared/utils/cn';

interface ProviderQuickCardsProps {
  selectedProvider: string;
  onSelect: (providerId: string) => void;
  className?: string;
}

/** 供应商描述映射 */
const PROVIDER_DESC: Record<string, string> = {
  anthropic: 'Claude 系列',
  openai: 'GPT 系列',
  deepseek: 'DeepSeek 系列',
  glm: 'GLM 系列',
  minimax: 'MiniMax 系列',
  custom: 'OpenAI 兼容',
};

export const ProviderQuickCards = memo<ProviderQuickCardsProps>(function ProviderQuickCards({
  selectedProvider,
  onSelect,
  className
}) {
  return (
    <div className={cn('grid grid-cols-3 gap-2', className)}>
      {PROVIDERS.map((provider: Provider) => {
        const isSelected = selectedProvider === provider.id;
        return (
          <button
            key={provider.id}
            type="button"
            onClick={() => onSelect(provider.id)}
            className={cn(
              'flex flex-col items-start gap-0.5 rounded-lg border p-3 text-left transition-all',
              isSelected
                ? 'border-primary bg-primary/5 ring-1 ring-primary'
                : 'border-border hover:border-muted-foreground hover:bg-accent/50'
            )}
          >
            <span className={cn(
              'text-sm font-medium',
              isSelected ? 'text-primary' : 'text-foreground'
            )}>
              {provider.name}
            </span>
            <span className="text-xs text-muted-foreground">
              {PROVIDER_DESC[provider.id] ?? ''}
            </span>
          </button>
        );
      })}
    </div>
  );
});
