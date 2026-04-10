/**
 * ModelConfigPanel - 模型配置面板
 *
 * 提供项目级 AI 供应商和模型配置
 */

import { memo, useState, useEffect, useCallback } from 'react';
import { javaBridge } from '@/lib/java-bridge';
import { ProviderSelector, ModelSelector, PROVIDERS } from './ProviderSelector';
import { cn } from '@/shared/utils/cn';

interface ModelConfig {
  provider: string;
  model: string;
  apiKey: string;
  baseUrl: string;
  maxTokens: number;
  temperature: number;
  topP: number;
  maxRetries: number;
}

const DEFAULT_CONFIG: ModelConfig = {
  provider: 'anthropic',
  model: 'claude-sonnet-4-20250514',
  apiKey: '',
  baseUrl: '',
  maxTokens: 8192,
  temperature: 1.0,
  topP: 0.999,
  maxRetries: 3
};

interface ModelConfigPanelProps {
  className?: string;
}

export const ModelConfigPanel = memo<ModelConfigPanelProps>(function ModelConfigPanel({
  className
}) {
  const [config, setConfig] = useState<ModelConfig>(DEFAULT_CONFIG);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  // 加载当前配置
  useEffect(() => {
    const loadConfig = async () => {
      try {
        setLoading(true);
        setError(null);
        const currentConfig = await javaBridge.getModelConfig();
        setConfig(currentConfig);
      } catch (e) {
        console.error('Failed to load model config:', e);
        setError('加载配置失败');
      } finally {
        setLoading(false);
      }
    };
    loadConfig();
  }, []);

  // 保存配置
  const handleSave = useCallback(async () => {
    try {
      setSaving(true);
      setError(null);
      setSuccess(false);
      await javaBridge.updateModelConfig(config);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 2000);
    } catch (e) {
      console.error('Failed to save model config:', e);
      setError('保存配置失败');
    } finally {
      setSaving(false);
    }
  }, [config]);

  // 更新字段
  const updateField = <K extends keyof ModelConfig>(field: K, value: ModelConfig[K]) => {
    setConfig((prev) => {
      const next = { ...prev, [field]: value };
      // 当切换到自定义 provider 时，清空 baseUrl
      if (field === 'provider' && value === 'custom') {
        next.baseUrl = '';
        next.model = '';
      }
      // 当切换 provider 时，自动选择默认模型
      if (field === 'provider') {
        const provider = PROVIDERS.find((p) => p.id === value);
        if (provider && provider.models.length > 0) {
          next.model = provider.defaultModel;
        }
      }
      return next;
    });
    setSuccess(false);
  };

  if (loading) {
    return (
      <div className={cn('flex items-center justify-center p-6', className)}>
        <div className="text-muted-foreground text-sm">加载中...</div>
      </div>
    );
  }

  return (
    <div className={cn('space-y-6', className)}>
      {/* 状态提示 */}
      {error && (
        <div className="px-4 py-3 rounded-md bg-destructive/10 border border-destructive/20 text-destructive text-sm">
          {error}
        </div>
      )}
      {success && (
        <div className="px-4 py-3 rounded-md bg-green-500/10 border border-green-500/20 text-green-600 text-sm">
          配置已保存
        </div>
      )}

      {/* 供应商选择 */}
      <section>
        <label className="block text-sm font-medium text-foreground mb-2">
          AI 供应商
        </label>
        <ProviderSelector
          value={config.provider}
          onChange={(provider) => updateField('provider', provider)}
        />
      </section>

      {/* 模型选择 */}
      <section>
        <label className="block text-sm font-medium text-foreground mb-2">
          模型
        </label>
        <ModelSelector
          providerId={config.provider}
          value={config.model}
          onChange={(model) => updateField('model', model)}
        />
      </section>

      {/* API Key */}
      <section>
        <label className="block text-sm font-medium text-foreground mb-2">
          API Key
        </label>
        <input
          type="password"
          value={config.apiKey}
          onChange={(e) => updateField('apiKey', e.target.value)}
          placeholder="输入 API Key"
          className={cn(
            'w-full px-3 py-2 rounded-md',
            'bg-background border border-border',
            'text-foreground text-sm',
            'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
            'placeholder:text-muted-foreground'
          )}
        />
        <p className="mt-1 text-xs text-muted-foreground">
          API Key 会加密存储，仅本地保存
        </p>
      </section>

      {/* Base URL（自定义供应商时显示） */}
      {config.provider === 'custom' && (
        <section>
          <label className="block text-sm font-medium text-foreground mb-2">
            API Base URL
          </label>
          <input
            type="text"
            value={config.baseUrl}
            onChange={(e) => updateField('baseUrl', e.target.value)}
            placeholder="https://api.openai.com/v1"
            className={cn(
              'w-full px-3 py-2 rounded-md',
              'bg-background border border-border',
              'text-foreground text-sm',
              'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
              'placeholder:text-muted-foreground'
            )}
          />
        </section>
      )}

      {/* 高级设置 */}
      <details className="group">
        <summary className="flex items-center gap-2 text-sm font-medium text-foreground cursor-pointer hover:text-primary">
          <svg
            className="w-4 h-4 transition-transform group-open:rotate-90"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
          高级设置
        </summary>
        <div className="mt-4 space-y-4 pl-6">
          {/* Max Tokens */}
          <div>
            <label className="block text-sm text-foreground mb-2">
              最大 Tokens: {config.maxTokens}
            </label>
            <input
              type="range"
              min="256"
              max="128000"
              step="256"
              value={config.maxTokens}
              onChange={(e) => updateField('maxTokens', parseInt(e.target.value))}
              className="w-full accent-primary"
            />
          </div>

          {/* Temperature */}
          <div>
            <label className="block text-sm text-foreground mb-2">
              Temperature: {config.temperature.toFixed(1)}
            </label>
            <input
              type="range"
              min="0"
              max="2"
              step="0.1"
              value={config.temperature}
              onChange={(e) => updateField('temperature', parseFloat(e.target.value))}
              className="w-full accent-primary"
            />
          </div>

          {/* Top P */}
          <div>
            <label className="block text-sm text-foreground mb-2">
              Top P: {config.topP.toFixed(3)}
            </label>
            <input
              type="range"
              min="0"
              max="1"
              step="0.001"
              value={config.topP}
              onChange={(e) => updateField('topP', parseFloat(e.target.value))}
              className="w-full accent-primary"
            />
          </div>

          {/* Max Retries */}
          <div>
            <label className="block text-sm text-foreground mb-2">
              最大重试次数: {config.maxRetries}
            </label>
            <input
              type="range"
              min="1"
              max="10"
              step="1"
              value={config.maxRetries}
              onChange={(e) => updateField('maxRetries', parseInt(e.target.value))}
              className="w-full accent-primary"
            />
          </div>
        </div>
      </details>

      {/* 保存按钮 */}
      <div className="flex justify-end gap-2 pt-4 border-t border-border">
        <button
          type="button"
          onClick={handleSave}
          disabled={saving}
          className={cn(
            'px-4 py-2 rounded-md text-sm font-medium',
            'bg-primary text-primary-foreground',
            'hover:opacity-90 transition-opacity',
            'disabled:opacity-50 disabled:cursor-not-allowed'
          )}
        >
          {saving ? '保存中...' : '保存配置'}
        </button>
      </div>
    </div>
  );
});
