/**
 * useTheme - 主题切换 Hook
 *
 * 注意：applyTheme 已移至 themeStore.ts，由 setTheme/setPresetTheme 自动调用
 * 这里不需要重复调用 applyTheme
 */

import { useThemeStore } from '@/shared/stores';
import type { ThemeConfig, ThemePresets } from '@/shared/types';

interface UseThemeReturn {
  theme: ThemeConfig;
  isDark: boolean;
  isEditing: boolean;
  setTheme: (theme: ThemeConfig) => void;
  setPresetTheme: (preset: ThemePresets) => void;
  startEditing: () => void;
  finishEditing: () => void;
}

export function useTheme(): UseThemeReturn {
  const {
    currentTheme,
    isEditing,
    setTheme,
    setPresetTheme,
    startEditing,
    finishEditing
  } = useThemeStore();

  // 注意：主题应用由 themeStore.ts 中的 setTheme/setPresetTheme 自动处理
  // 不需要在此处重复调用 applyTheme

  return {
    theme: currentTheme,
    isDark: currentTheme.isDark,
    isEditing,
    setTheme,
    setPresetTheme,
    startEditing,
    finishEditing
  };
}
