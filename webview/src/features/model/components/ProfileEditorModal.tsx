/**
 * ProfileEditorModal - 供应商配置编辑弹窗 (简化版)
 *
 * 只保留核心字段：名称、供应商、API Key、Base URL
 */

import { memo, useState, useEffect, useCallback } from 'react';
import { PROVIDERS, ModelSelector } from './ProviderSelector';
import { JsonConfigEditor } from './JsonConfigEditor';
import { cn } from '@/shared/utils/cn';
import type { ProviderProfile } from '@/shared/stores/providerProfilesStore';

interface ProfileEditorModalProps {
  /** 是否打开 */
  isOpen: boolean;
  /** 关闭回调 */
  onClose: () => void;
  /** 初始配置（编辑模式） */
  initialProfile?: ProviderProfile | null;
  /** 保存回调 */
  onSave: (profile: Omit<ProviderProfile, 'id' | 'createdAt' | 'updatedAt'> & { id?: string }, isNew: boolean) => Promise<void>;
}

const EMPTY_PROFILE: Omit<ProviderProfile, 'id' | 'createdAt' | 'updatedAt'> = {
  name: '',
  provider: 'anthropic',
  source: 'local',
  model: 'claude-sonnet-4-20250514',
  apiKey: '',
  baseUrl: '',
  sonnetModel: 'claude-sonnet-4-20250514',
  opusModel: 'claude-opus-4-20250514',
  maxModel: 'claude-3-5-haiku-20250514',
  maxRetries: 3
};

const DEFAULT_PROFILE_VALUES: Omit<ProviderProfile, 'id' | 'createdAt' | 'updatedAt'> = {
  name: '',
  provider: 'anthropic',
  source: 'local',
  model: 'claude-sonnet-4-20250514',
  apiKey: '',
  baseUrl: '',
  sonnetModel: 'claude-sonnet-4-20250514',
  opusModel: 'claude-opus-4-20250514',
  maxModel: 'claude-3-5-haiku-20250514',
  maxRetries: 3
};

const sectionClass = 'space-y-1.5';
const labelClass = 'block text-sm font-medium text-foreground';
const hintClass = 'text-xs text-muted-foreground';

export const ProfileEditorModal = memo<ProfileEditorModalProps>(function ProfileEditorModal({
  isOpen,
  onClose,
  initialProfile,
  onSave
}) {
  const [formData, setFormData] = useState<Omit<ProviderProfile, 'id' | 'createdAt' | 'updatedAt'>>(DEFAULT_PROFILE_VALUES);
  const [saving, setSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEditing = !!initialProfile;

  useEffect(() => {
    if (isOpen) {
      if (initialProfile) {
        setFormData({
          name: initialProfile.name,
          provider: initialProfile.provider,
          source: initialProfile.source,
          model: initialProfile.model,
          apiKey: initialProfile.apiKey,
          baseUrl: initialProfile.baseUrl,
          sonnetModel: initialProfile.sonnetModel,
          opusModel: initialProfile.opusModel,
          maxModel: initialProfile.maxModel,
          maxRetries: initialProfile.maxRetries
        });
      } else {
        const provider = PROVIDERS[0]!;
        setFormData({
          ...DEFAULT_PROFILE_VALUES,
          name: provider.name, // 默认使用供应商名称
          provider: provider.id,
          model: provider.defaultModel,
          sonnetModel: provider.sonnetDefault ?? provider.defaultModel,
          opusModel: provider.opusDefault ?? provider.defaultModel,
          maxModel: provider.maxDefault ?? provider.defaultModel,
          baseUrl: provider.defaultBaseUrl ?? ''
        });
      }
      setError(null);
    }
  }, [isOpen, initialProfile]);

  const updateField = useCallback(<K extends keyof typeof EMPTY_PROFILE>(field: K, value: typeof EMPTY_PROFILE[K]) => {
    setFormData(prev => {
      const next = { ...prev, [field]: value };
      if (field === 'provider') {
        const provider = PROVIDERS.find(p => p.id === value);
        if (provider) {
          next.model = provider.defaultModel;
          next.sonnetModel = provider.sonnetDefault ?? provider.defaultModel;
          next.opusModel = provider.opusDefault ?? provider.defaultModel;
          next.maxModel = provider.maxDefault ?? provider.defaultModel;
          next.baseUrl = provider.defaultBaseUrl ?? '';
          // 如果名称为空或与之前的供应商默认名称匹配，则更新为新供应商的默认名称
          if (!prev.name.trim() || prev.name === PROVIDERS.find(p => p.id === prev.provider)?.name) {
            next.name = provider.name;
          }
        }
      }
      return next;
    });
  }, []);

  const handleJsonImport = useCallback((parsed: Record<string, unknown>) => {
    setFormData(prev => {
      let provider = prev.provider;
      let apiKey = prev.apiKey;
      let baseUrl = prev.baseUrl;
      let sonnetModel = prev.sonnetModel;
      let opusModel = prev.opusModel;
      let maxModel = prev.maxModel;

      // 支持完整 settings.json 格式: { providers: [{ settingsConfig: { env: {...} } }] }
      if (parsed.providers && Array.isArray(parsed.providers) && parsed.providers.length > 0) {
        const firstProvider = parsed.providers[0] as Record<string, unknown>;
        const settingsConfig = firstProvider.settingsConfig as { env?: Record<string, string> } | undefined;
        if (settingsConfig?.env) {
          const env = settingsConfig.env;
          if (env.ANTHROPIC_AUTH_TOKEN) apiKey = env.ANTHROPIC_AUTH_TOKEN;
          if (env.ANTHROPIC_BASE_URL) {
            baseUrl = env.ANTHROPIC_BASE_URL;
            // 推断 provider 类型
            if (baseUrl.includes('anthropic')) provider = 'anthropic';
            else if (baseUrl.includes('openai')) provider = 'openai';
            else if (baseUrl.includes('deepseek')) provider = 'deepseek';
            else if (baseUrl.includes('minimax')) provider = 'minimax';
          }
          if (env.ANTHROPIC_DEFAULT_SONNET_MODEL) sonnetModel = env.ANTHROPIC_DEFAULT_SONNET_MODEL;
          if (env.ANTHROPIC_DEFAULT_OPUS_MODEL) opusModel = env.ANTHROPIC_DEFAULT_OPUS_MODEL;
          if (env.ANTHROPIC_DEFAULT_MAX_MODEL) maxModel = env.ANTHROPIC_DEFAULT_MAX_MODEL;
        }
      } else {
        // 直接是 env 对象
        const env = parsed as Record<string, string>;
        if (env.ANTHROPIC_AUTH_TOKEN) apiKey = env.ANTHROPIC_AUTH_TOKEN;
        if (env.ANTHROPIC_BASE_URL) {
          baseUrl = env.ANTHROPIC_BASE_URL;
          if (baseUrl.includes('anthropic')) provider = 'anthropic';
          else if (baseUrl.includes('openai')) provider = 'openai';
          else if (baseUrl.includes('deepseek')) provider = 'deepseek';
          else if (baseUrl.includes('minimax')) provider = 'minimax';
        }
        if (env.ANTHROPIC_DEFAULT_SONNET_MODEL) sonnetModel = env.ANTHROPIC_DEFAULT_SONNET_MODEL;
        if (env.ANTHROPIC_DEFAULT_OPUS_MODEL) opusModel = env.ANTHROPIC_DEFAULT_OPUS_MODEL;
        if (env.ANTHROPIC_DEFAULT_MAX_MODEL) maxModel = env.ANTHROPIC_DEFAULT_MAX_MODEL;
        if (env.ANTHROPIC_MAX_MODEL) maxModel = env.ANTHROPIC_MAX_MODEL;
        if (env.ANTHROPIC_OPUS_MODEL) opusModel = env.ANTHROPIC_OPUS_MODEL;
      }

      const providerDef = PROVIDERS.find(p => p.id === provider);
      const cascaded = providerDef ? {
        model: providerDef.defaultModel,
        sonnetModel: providerDef.sonnetDefault ?? providerDef.defaultModel,
        opusModel: providerDef.opusDefault ?? providerDef.defaultModel,
        maxModel: providerDef.maxDefault ?? providerDef.defaultModel,
        baseUrl: providerDef.defaultBaseUrl ?? ''
      } : {
        model: prev.model,
        sonnetModel: prev.sonnetModel,
        opusModel: prev.opusModel,
        maxModel: prev.maxModel,
        baseUrl: prev.baseUrl
      };

      return {
        ...prev,
        provider,
        model: cascaded.model,
        apiKey: (apiKey ?? prev.apiKey) || '',
        baseUrl: (baseUrl ?? cascaded.baseUrl) || prev.baseUrl,
        sonnetModel: (sonnetModel ?? cascaded.sonnetModel) || prev.sonnetModel,
        opusModel: (opusModel ?? cascaded.opusModel) || prev.opusModel,
        maxModel: (maxModel ?? cascaded.maxModel) || prev.maxModel,
      };
    });
  }, []);

  const handleJsonExport = useCallback((): string => {
    // 导出为 providers 数组格式
    const providerObj = {
      id: formData.name.toLowerCase().replace(/\s+/g, '-'),
      name: formData.name,
      settingsConfig: {
        env: {}
      }
    };
    const env = (providerObj.settingsConfig as { env: Record<string, string> }).env;

    if (formData.apiKey) {
      env['ANTHROPIC_AUTH_TOKEN'] = formData.apiKey;
    }
    if (formData.baseUrl) {
      env['ANTHROPIC_BASE_URL'] = formData.baseUrl;
    }
    if (formData.sonnetModel) {
      env['ANTHROPIC_DEFAULT_SONNET_MODEL'] = formData.sonnetModel;
    }
    if (formData.opusModel) {
      env['ANTHROPIC_DEFAULT_OPUS_MODEL'] = formData.opusModel;
    }
    if (formData.maxModel) {
      env['ANTHROPIC_DEFAULT_MAX_MODEL'] = formData.maxModel;
    }

    return JSON.stringify({ providers: [providerObj] }, null, 2);
  }, [formData]);

  const handleSave = useCallback(async () => {
    if (!formData.name.trim()) {
      setError('请输入配置名称');
      return;
    }
    try {
      setSaving(true);
      setError(null);
      await onSave(formData, !initialProfile);
      onClose();
    } catch (e) {
      setError('保存失败');
    } finally {
      setSaving(false);
    }
  }, [formData, initialProfile, onSave, onClose]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* 遮罩 */}
      <div
        className="absolute inset-0 bg-black/50"
        onClick={onClose}
      />

      {/* 弹窗 */}
      <div className="relative w-full max-w-lg max-h-[90vh] bg-popover border border-border rounded-lg shadow-xl overflow-hidden flex flex-col">
        {/* 头部 */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
          <h2 className="text-base font-semibold text-foreground">
            {isEditing ? '编辑配置' : '新建配置'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded hover:bg-muted transition-colors"
          >
            <svg className="w-5 h-5 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 内容 */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {error && (
            <div className="px-4 py-3 rounded-md bg-destructive/10 border border-destructive/20 text-destructive text-sm">
              {error}
            </div>
          )}

          {/* 配置名称 */}
          <section className={sectionClass}>
            <label className={labelClass}>配置名称</label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => updateField('name', e.target.value)}
              placeholder="例如：我的 Claude 配置"
              className={cn(
                'w-full px-3 py-2 rounded-md',
                'bg-background border border-border',
                'text-foreground text-sm',
                'focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary',
                'placeholder:text-muted-foreground',
                'transition-colors'
              )}
            />
          </section>

          {/* JSON 一键配置 */}
          <details className="group">
            <summary className="flex items-center gap-2 text-sm font-medium text-foreground cursor-pointer hover:text-primary transition-colors">
              <svg
                className="w-4 h-4 transition-transform group-open:rotate-90"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
              JSON 一键配置
              <span className="text-xs text-muted-foreground font-normal ml-1">
                （粘贴或导入 .claude/settings.json 格式）
              </span>
            </summary>
            <div className="mt-3">
              <JsonConfigEditor
                value={handleJsonExport()}
                onImport={handleJsonImport}
                onExport={handleJsonExport}
              />
            </div>
          </details>

          {/* 供应商快捷卡片 */}
          <section className={sectionClass}>
            <label className={labelClass}>
              AI 供应商
              <span className={hintClass + ' font-normal ml-1'}>（点击选择，自动填充默认配置）</span>
            </label>
            <div className="grid grid-cols-3 gap-2">
              {PROVIDERS.filter(p => p.id !== 'custom').map(provider => (
                <button
                  key={provider.id}
                  type="button"
                  onClick={() => updateField('provider', provider.id)}
                  className={cn(
                    'px-3 py-2 rounded-md text-xs text-center transition-colors border',
                    formData.provider === provider.id
                      ? 'bg-primary/10 border-primary/30 text-primary font-medium'
                      : 'bg-background border-border text-muted-foreground hover:border-primary/50'
                  )}
                >
                  {provider.name.split(' ')[0]}
                </button>
              ))}
            </div>
          </section>

          {/* API Key */}
          <section className={sectionClass}>
            <div className="flex items-center justify-between">
              <label className={labelClass}>API Key</label>
              <button
                type="button"
                onClick={() => setShowApiKey(!showApiKey)}
                className="text-xs text-muted-foreground hover:text-foreground transition-colors"
              >
                {showApiKey ? '隐藏' : '显示'}
              </button>
            </div>
            <input
              type={showApiKey ? 'text' : 'password'}
              value={formData.apiKey}
              onChange={(e) => updateField('apiKey', e.target.value)}
              placeholder="输入 API Key"
              className={cn(
                'w-full px-3 py-2 rounded-md',
                'bg-background border border-border',
                'text-foreground text-sm',
                'focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary',
                'placeholder:text-muted-foreground',
                'transition-colors'
              )}
            />
            <p className={hintClass}>API Key 会加密存储，仅本地保存</p>
          </section>

          {/* Base URL */}
          <section className={sectionClass}>
            <label className={labelClass}>
              API Base URL
              <span className={hintClass + ' font-normal ml-1'}>（可选，覆盖默认值）</span>
            </label>
            <input
              type="text"
              value={formData.baseUrl}
              onChange={(e) => updateField('baseUrl', e.target.value)}
              placeholder={
                PROVIDERS.find(p => p.id === formData.provider)?.defaultBaseUrl
                  || 'https://api.anthropic.com'
              }
              className={cn(
                'w-full px-3 py-2 rounded-md',
                'bg-background border border-border',
                'text-foreground text-sm',
                'focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary',
                'placeholder:text-muted-foreground',
                'transition-colors'
              )}
            />
          </section>

          {/* 高级设置（可选） */}
          <details className="group">
            <summary className="flex items-center gap-2 text-sm font-medium text-foreground cursor-pointer hover:text-primary transition-colors">
              <svg
                className="w-4 h-4 transition-transform group-open:rotate-90"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
              </svg>
              高级设置
              <span className="text-xs text-muted-foreground font-normal ml-1">
                （模型配置）
              </span>
            </summary>
            <div className="mt-3 space-y-4">
              {/* 默认模型 */}
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">默认模型</label>
                <ModelSelector
                  providerId={formData.provider}
                  value={formData.model}
                  onChange={(model) => updateField('model', model)}
                />
              </div>

              {/* Sonnet 模型 */}
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">
                  Sonnet 模型
                  <span className="text-xs text-muted-foreground font-normal ml-1">（快速任务）</span>
                </label>
                <ModelSelector
                  providerId={formData.provider}
                  value={formData.sonnetModel}
                  onChange={(model) => updateField('sonnetModel', model)}
                />
              </div>

              {/* Opus 模型 */}
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">
                  Opus 模型
                  <span className="text-xs text-muted-foreground font-normal ml-1">（复杂任务）</span>
                </label>
                <ModelSelector
                  providerId={formData.provider}
                  value={formData.opusModel}
                  onChange={(model) => updateField('opusModel', model)}
                />
              </div>

              {/* Haiku 模型 */}
              <div>
                <label className="block text-sm font-medium text-foreground mb-1">
                  Haiku 模型
                  <span className="text-xs text-muted-foreground font-normal ml-1">（轻量任务）</span>
                </label>
                <ModelSelector
                  providerId={formData.provider}
                  value={formData.maxModel}
                  onChange={(model) => updateField('maxModel', model)}
                />
              </div>
            </div>
          </details>
        </div>

        {/* 底部按钮 */}
        <div className="flex justify-end gap-2 px-4 py-3 border-t border-border shrink-0">
          <button
            type="button"
            onClick={onClose}
            className={cn(
              'px-4 py-2 rounded-md text-sm font-medium',
              'bg-background-secondary text-foreground',
              'hover:bg-background-elevated transition-colors'
            )}
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className={cn(
              'px-5 py-2 rounded-md text-sm font-medium',
              'bg-primary text-primary-foreground',
              'hover:opacity-90 active:scale-[0.98]',
              'transition-all',
              'disabled:opacity-50 disabled:cursor-not-allowed'
            )}
          >
            {saving ? '保存中...' : '保存配置'}
          </button>
        </div>
      </div>
    </div>
  );
});
