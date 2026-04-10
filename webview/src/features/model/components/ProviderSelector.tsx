/**
 * ProviderSelector - AI 供应商选择器
 */

import { memo } from 'react';
import { cn } from '@/shared/utils/cn';

export interface Provider {
  id: string;
  name: string;
  models: string[];
  defaultModel: string;
}

export const PROVIDERS: Provider[] = [
  {
    id: 'anthropic',
    name: 'Anthropic (Claude)',
    models: [
      'claude-sonnet-4-20250514',
      'claude-opus-4-20250514',
      'claude-3-5-sonnet-20241022',
      'claude-3-opus-20240229',
      'claude-3-haiku-20240307'
    ],
    defaultModel: 'claude-sonnet-4-20250514'
  },
  {
    id: 'openai',
    name: 'OpenAI (GPT)',
    models: [
      'gpt-4o',
      'gpt-4o-mini',
      'gpt-4-turbo',
      'gpt-4',
      'gpt-3.5-turbo'
    ],
    defaultModel: 'gpt-4o'
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    models: [
      'deepseek-chat',
      'deepseek-coder'
    ],
    defaultModel: 'deepseek-chat'
  },
  {
    id: 'glm',
    name: '智谱 GLM',
    models: [
      'glm-4',
      'glm-4-flash',
      'glm-4-plus',
      'glm-3-turbo'
    ],
    defaultModel: 'glm-4-flash'
  },
  {
    id: 'minimax',
    name: 'MiniMax',
    models: [
      'abab6.5s-chat',
      'abab6.5-chat',
      'abab5.5-chat'
    ],
    defaultModel: 'abab6.5s-chat'
  },
  {
    id: 'custom',
    name: '自定义 (OpenAI 兼容)',
    models: [],
    defaultModel: ''
  }
];

interface ProviderSelectorProps {
  value: string;
  onChange: (providerId: string) => void;
  className?: string;
}

export const ProviderSelector = memo<ProviderSelectorProps>(function ProviderSelector({
  value,
  onChange,
  className
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={cn(
        'w-full px-3 py-2 rounded-md',
        'bg-background border border-border',
        'text-foreground text-sm',
        'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
        'cursor-pointer',
        className
      )}
    >
      {PROVIDERS.map((provider) => (
        <option key={provider.id} value={provider.id}>
          {provider.name}
        </option>
      ))}
    </select>
  );
});

interface ModelSelectorProps {
  providerId: string;
  value: string;
  onChange: (model: string) => void;
  className?: string;
}

export const ModelSelector = memo<ModelSelectorProps>(function ModelSelector({
  providerId,
  value,
  onChange,
  className
}) {
  const provider = PROVIDERS.find((p) => p.id === providerId);

  if (!provider || provider.models.length === 0) {
    return (
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="输入模型名称"
        className={cn(
          'w-full px-3 py-2 rounded-md',
          'bg-background border border-border',
          'text-foreground text-sm',
          'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
          className
        )}
      />
    );
  }

  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={cn(
        'w-full px-3 py-2 rounded-md',
        'bg-background border border-border',
        'text-foreground text-sm',
        'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
        'cursor-pointer',
        className
      )}
    >
      {provider.models.map((model) => (
        <option key={model} value={model}>
          {model}
        </option>
      ))}
    </select>
  );
});
