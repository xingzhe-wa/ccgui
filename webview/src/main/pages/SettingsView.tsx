/**
 * SettingsView - 设置页面
 *
 * 组合 ThemeSwitcher + ThemeEditor + ModelConfigPanel + 偏好设置面板
 */

import { lazy, Suspense, useState, useCallback } from 'react';
import { ThemeSwitcher } from '@/features/theme/components/ThemeSwitcher';
import { ModelConfigPanel } from '@/features/model/components';
import { LoadingFallback } from '@/shared/components/ui/LoadingFallback';

const LazyThemeEditor = lazy(() =>
  import('@/features/theme/components/ThemeEditor').then((m) => ({
    default: m.ThemeEditor
  }))
);

export function SettingsView(): JSX.Element {
  const [brightness, setBrightness] = useState(100);
  const [saturation, setSaturation] = useState(100);

  // 应用亮度和饱和度到当前主题
  const applyBrightnessSaturation = useCallback((bright: number, satu: number) => {
    // 将亮度转换为滤镜值（0-100 -> 0.5-1.5）
    const brightnessFilter = 0.5 + (bright / 100) * 1.0;
    // 将饱和度转换为滤镜值（0-100 -> 0-2）
    const saturationFilter = satu / 100;

    // 应用到主题的 CSS 变量
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

  return (
    <div className="h-full overflow-y-auto p-6">
      <h1 className="mb-6 text-2xl font-bold">设置</h1>

      {/* 模型配置 */}
      <section className="mb-8">
        <h2 className="mb-4 text-lg font-semibold">模型配置</h2>
        <div className="rounded-lg border p-4">
          <ModelConfigPanel />
        </div>
      </section>

      {/* 主题设置 */}
      <section className="mb-8">
        <h2 className="mb-4 text-lg font-semibold">主题</h2>
        <div className="rounded-lg border p-4">
          <div className="flex items-center gap-6">
            {/* 左侧：预设主题下拉 */}
            <div className="flex-1">
              <ThemeSwitcher />
            </div>

            {/* 右侧：亮度和饱和度滑块 */}
            <div className="w-64 space-y-3">
              {/* 亮度 */}
              <div>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm text-foreground">亮度</span>
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
                  <span className="text-sm text-foreground">饱和度</span>
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
        </div>
      </section>

      {/* 主题编辑器 */}
      <section className="mb-8">
        <h2 className="mb-4 text-lg font-semibold">自定义主题</h2>
        <div className="rounded-lg border p-4">
          <Suspense fallback={<LoadingFallback />}>
            <LazyThemeEditor />
          </Suspense>
        </div>
      </section>

      {/* 关于 */}
      <section>
        <h2 className="mb-4 text-lg font-semibold">关于</h2>
        <div className="rounded-lg border p-4 text-sm text-muted-foreground">
          <p>CC Assistant v0.0.1</p>
          <p className="mt-1">AI-Powered Coding Assistant for IntelliJ IDEA</p>
        </div>
      </section>
    </div>
  );
}
