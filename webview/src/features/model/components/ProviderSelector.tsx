/**
 * ProviderSelector - AI 供应商选择器
 *
 * 使用 input + datalist 实现可选可编辑的下拉输入框
 */

import { memo, useId } from 'react';
import { cn } from '@/shared/utils/cn';

export interface Provider {
  id: string;
  name: string;
  models: string[];
  defaultModel: string;
  /** AUTO 模式默认模型 */
  sonnetDefault?: string;
  /** THINKING 模式默认模型 */
  opusDefault?: string;
  /** PLANNING 模式默认模型 */
  maxDefault?: string;
  /** 默认 Base URL */
  defaultBaseUrl?: string;
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
    defaultModel: 'claude-sonnet-4-20250514',
    sonnetDefault: 'claude-sonnet-4-20250514',
    opusDefault: 'claude-opus-4-20250514',
    maxDefault: 'claude-3-5-haiku-20250514',
    defaultBaseUrl: 'https://api.anthropic.com'
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
    defaultModel: 'gpt-4o',
    sonnetDefault: 'gpt-4o',
    opusDefault: 'gpt-4o',
    maxDefault: 'gpt-4o-mini',
    defaultBaseUrl: 'https://api.openai.com/v1'
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    models: [
      'deepseek-chat',
      'deepseek-coder'
    ],
    defaultModel: 'deepseek-chat',
    sonnetDefault: 'deepseek-chat',
    opusDefault: 'deepseek-chat',
    maxDefault: 'deepseek-coder',
    defaultBaseUrl: 'https://api.deepseek.com'
  },
  {
    id: 'glm',
    name: '智谱 GLM',
    models: [
      'glm-4',
      'glm-4-flash',
      'glm-4-plus',
      'glm-5',
      'glm-3-turbo'
    ],
    defaultModel: 'glm-4-flash',
    sonnetDefault: 'glm-4.7',
    opusDefault: 'glm-5',
    maxDefault: 'glm-4.7',
    defaultBaseUrl: 'https://open.bigmodel.cn/api/paas/v4'
  },
  {
    id: 'minimax',
    name: 'MiniMax',
    models: [
      'MiniMax-M2.7',
      'abab6.5s-chat',
      'abab6.5-chat',
      'abab5.5-chat'
    ],
    defaultModel: 'MiniMax-M2.7',
    sonnetDefault: 'MiniMax-M2.7',
    opusDefault: 'MiniMax-M2.7',
    maxDefault: 'MiniMax-M2.7',
    defaultBaseUrl: 'https://api.minimax.chat/v1'
  },
  {
    id: 'custom',
    name: '自定义 (OpenAI 兼容)',
    models: [],
    defaultModel: '',
    defaultBaseUrl: ''
  }
];

/** 共用输入框样式 */
const inputClass = cn(
  'w-full px-3 py-2 rounded-md',
  'bg-background border border-border',
  'text-foreground text-sm',
  'focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary',
  'transition-colors'
);

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
  const listId = useId();

  return (
    <div className={className}>
      <input
        list={listId}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="输入或选择供应商"
        className={inputClass}
      />
      <datalist id={listId}>
        {PROVIDERS.map((provider) => (
          <option key={provider.id} value={provider.id}>
            {provider.name}
          </option>
        ))}
      </datalist>
    </div>
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
  const listId = useId();
  const provider = PROVIDERS.find((p) => p.id === providerId);

  return (
    <div className={className}>
      <input
        list={listId}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={provider ? `输入或选择 ${provider.name} 模型` : '输入模型名称'}
        className={inputClass}
      />
      {provider && provider.models.length > 0 && (
        <datalist id={listId}>
          {provider.models.map((model) => (
            <option key={model} value={model} />
          ))}
        </datalist>
      )}
    </div>
  );
});

/**
 * Per-mode 模型选择器（用于 AUTO/THINKING/PLANNING 三种模式）
 */
interface ModeModelSelectorProps {
  providerId: string;
  sonnetModel: string;
  opusModel: string;
  maxModel: string;
  onSonnetChange: (model: string) => void;
  onOpusChange: (model: string) => void;
  onMaxChange: (model: string) => void;
  className?: string;
}

export const ModeModelSelector = memo<ModeModelSelectorProps>(function ModeModelSelector({
  providerId,
  sonnetModel,
  opusModel,
  maxModel,
  onSonnetChange,
  onOpusChange,
  onMaxChange,
  className
}) {
  const provider = PROVIDERS.find((p) => p.id === providerId);
  const models = provider?.models ?? [];

  const renderInput = (label: string, value: string, onChange: (v: string) => void, listId: string) => (
    <div className="flex-1">
      <label className="block text-xs text-muted-foreground mb-1">{label}</label>
      <input
        list={listId}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="模型名"
        className={cn(
          'w-full px-2 py-1.5 rounded text-xs',
          'bg-background border border-border',
          'text-foreground',
          'focus:outline-none focus:ring-1 focus:ring-primary focus:border-primary',
          'transition-colors'
        )}
      />
      {models.length > 0 && (
        <datalist id={listId}>
          {models.map((model) => (
            <option key={model} value={model} />
          ))}
        </datalist>
      )}
    </div>
  );

  // 使用固定 id（因为 datalist id 在同一页面需要唯一）
  return (
    <div className={cn('flex gap-2', className)}>
      {renderInput('AUTO (Sonnet)', sonnetModel, onSonnetChange, 'cc-mode-sonnet')}
      {renderInput('THINKING (Opus)', opusModel, onOpusChange, 'cc-mode-opus')}
      {renderInput('PLANNING (Max)', maxModel, onMaxChange, 'cc-mode-max')}
    </div>
  );
});
