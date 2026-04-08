/**
 * 主题常量定义
 */

import { ThemePresets } from '@/shared/types/theme';

/**
 * 主题预设 ID 列表
 */
export const THEME_PRESET_IDS = Object.values(ThemePresets);

/**
 * 预设主题显示名称
 */
export const THEME_PRESET_NAMES: Record<ThemePresets, string> = {
  [ThemePresets.JETBRAINS_DARK]: 'JetBrains Dark',
  [ThemePresets.JETBRAINS_LIGHT]: 'JetBrains Light',
  [ThemePresets.GITHUB_DARK]: 'GitHub Dark',
  [ThemePresets.GITHUB_LIGHT]: 'GitHub Light',
  [ThemePresets.VS_CODE_DARK]: 'VS Code Dark',
  [ThemePresets.MONOKAI]: 'Monokai',
  [ThemePresets.SOLARIZED_LIGHT]: 'Solarized Light',
  [ThemePresets.SOLARIZED_DARK]: 'Solarized Dark',
  [ThemePresets.NORD]: 'Nord',
  [ThemePresets.CUSTOM]: 'Custom'
};
