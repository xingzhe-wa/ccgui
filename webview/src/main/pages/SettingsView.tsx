/**
 * SettingsView - 设置页面
 *
 * 标签页布局：每个设置类别有独立的 logo + 名称标签
 * - 模型配置
 * - 主题设置
 * - 关于
 */

import { lazy, Suspense, useState, useCallback, useEffect, memo } from 'react';
import { ThemeSwitcher } from '@/features/theme/components/ThemeSwitcher';
import { ModelConfigPanel } from '@/features/model/components';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';
import { useTranslation } from '@/shared/i18n';
import { cn } from '@/shared/utils/cn';

const LazyThemeEditor = lazy(() =>
  import('@/features/theme/components/ThemeEditor').then((m) => ({
    default: m.ThemeEditor
  }))
);

export const SettingsView = memo(function SettingsView(): JSX.Element {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState<string>('model');
  const [brightness, setBrightness] = useState(100);
  const [saturation, setSaturation] = useState(100);

  // 应用亮度和饱和度到当前主题
  const applyBrightnessSaturation = useCallback((bright: number, satu: number) => {
    const brightnessFilter = 0.5 + (bright / 100) * 1.0;
    const saturationFilter = satu / 100;
    document.documentElement.style.filter = `brightness(${brightnessFilter}) saturate(${saturationFilter})`;
  }, []);

  const handleBrightnessChange = useCallback((value: number) => {
    setBrightness(value);
    applyBrightnessSaturation(value, saturation);
  }, [saturation, applyBrightnessSaturation]);

  const handleSaturationChange = useCallback((value: number) => {
    setSaturation(value);
    applyBrightnessSaturation(brightness, value);
  }, [brightness, applyBrightnessSaturation]);

  // 组件卸载时重置 CSS filter，避免全局残留
  useEffect(() => {
    return () => {
      document.documentElement.style.filter = '';
    };
  }, []);

  return (
    <div className="flex h-full flex-col">
      {/* 页面标题 */}
      <div className="border-b px-6 py-4">
        <h1 className="text-lg font-semibold">{t('settings.title')}</h1>
      </div>

      {/* 标签页头 */}
      <div className="flex border-b bg-background">
        <button
          onClick={() => setActiveTab('model')}
          className={cn(
            'flex items-center gap-2 px-6 py-3 text-sm font-medium transition-colors',
            'border-b-2 -mb-px',
            activeTab === 'model'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground hover:border-muted'
          )}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-5 w-5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 18.75a1.5 1.5 0 01-1.5-1.5v-6a1.5 1.5 0 011.5-1.5h9a1.5 1.5 0 011.5 1.5v6a1.5 1.5 0 01-1.5 1.5H10.5z" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 15l-3 3m0 0l3 3m-3 3l3-3m-3 3h6" />
          </svg>
          <span>{t('settings.tabs.model')}</span>
        </button>
        <button
          onClick={() => setActiveTab('theme')}
          className={cn(
            'flex items-center gap-2 px-6 py-3 text-sm font-medium transition-colors',
            'border-b-2 -mb-px',
            activeTab === 'theme'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground hover:border-muted'
          )}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-5 w-5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.53 16.122a3 3 0 00-5.78 1.128 2.25 2.25 0 01-2.4 2.245 4.5 4.5 0 008.4-2.245c0-.399-.078-.78-.22-1.128zm0 0a15.998 15.998 0 003.388-1.62m-5.043-.025a15.994 15.994 0 011.622-3.395m3.42 3.42a15.995 15.995 0 004.764-4.648l3.876-5.814a1.151 1.151 0 00-1.597-1.597L14.146 6.32a15.996 15.996 0 00-4.649 4.763m3.42 3.42a6.776 6.776 0 00-3.42-3.42" />
          </svg>
          <span>{t('settings.tabs.theme')}</span>
        </button>
        <button
          onClick={() => setActiveTab('about')}
          className={cn(
            'flex items-center gap-2 px-6 py-3 text-sm font-medium transition-colors',
            'border-b-2 -mb-px',
            activeTab === 'about'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground hover:border-muted'
          )}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-5 w-5">
            <path strokeLinecap="round" strokeLinejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />
          </svg>
          <span>{t('settings.tabs.about')}</span>
        </button>
      </div>

      {/* 标签页内容 */}
      <div className="flex-1 overflow-y-auto p-6">
        {/* 模型配置 */}
        {activeTab === 'model' && (
          <div className="rounded-lg border">
            <ModelConfigPanel />
          </div>
        )}

        {/* 主题设置 */}
        {activeTab === 'theme' && (
          <div className="space-y-6">
            <div className="rounded-lg border p-4">
              <h3 className="text-sm font-medium mb-4">{t('settings.theme.preset')}</h3>
              <ThemeSwitcher />
            </div>

            <div className="rounded-lg border p-4">
              <h3 className="text-sm font-medium mb-4">{t('settings.theme.brightness')} & {t('settings.theme.saturation')}</h3>
              <div className="space-y-4">
                {/* 亮度 */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm text-foreground">{t('settings.theme.brightness')}</span>
                    <span className="text-sm text-muted-foreground">{brightness}%</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={brightness}
                    onChange={(e) => handleBrightnessChange(Number(e.target.value))}
                    className="w-full accent-primary cursor-pointer"
                  />
                </div>

                {/* 饱和度 */}
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm text-foreground">{t('settings.theme.saturation')}</span>
                    <span className="text-sm text-muted-foreground">{saturation}%</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="100"
                    value={saturation}
                    onChange={(e) => handleSaturationChange(Number(e.target.value))}
                    className="w-full accent-primary cursor-pointer"
                  />
                </div>
              </div>
            </div>

            <div className="rounded-lg border p-4">
              <h3 className="text-sm font-medium mb-4">{t('settings.theme.custom')}</h3>
              <Suspense fallback={<LoadingFallback />}>
                <LazyThemeEditor />
              </Suspense>
            </div>
          </div>
        )}

        {/* 关于 */}
        {activeTab === 'about' && (
          <div className="rounded-lg border p-6">
            <div className="flex items-center gap-4 mb-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary">
                <span className="text-lg font-bold text-primary-foreground">CC</span>
              </div>
              <div>
                <h2 className="text-lg font-semibold">{t('app.name')}</h2>
                <p className="text-sm text-muted-foreground">{t('app.description')}</p>
              </div>
            </div>
            <div className="text-sm text-muted-foreground space-y-1">
              <p>{t('settings.about.version')}: v0.0.1</p>
              <p>{t('settings.about.basedOn')}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
});
