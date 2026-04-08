/**
 * 主题状态 Store
 */

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { ThemeConfig, ThemeEditorState } from '@/shared/types';
import {
  ThemePresets,
  JetBrainsDarkConfig,
  JetBrainsLightConfig,
  GitHubDarkConfig,
  GitHubLightConfig,
  VSCodeDarkConfig,
  MonokaiConfig,
  NordConfig,
  SolarizedLightConfig,
  SolarizedDarkConfig
} from '@/shared/types/theme';

interface ThemeState {
  // ========== 状态 ==========
  currentTheme: ThemeConfig;
  customThemes: ThemeConfig[];
  isEditing: boolean;
  editorState: ThemeEditorState;

  // ========== 操作 ==========
  setTheme: (theme: ThemeConfig) => void;
  setPresetTheme: (preset: ThemePresets) => void;
  saveCustomTheme: (theme: ThemeConfig) => void;
  deleteCustomTheme: (themeId: string) => void;
  startEditing: () => void;
  finishEditing: () => void;
  setEditorState: (state: Partial<ThemeEditorState>) => void;
}

/**
 * 根据预设 ID 获取主题配置
 */
function getPresetConfig(preset: ThemePresets): ThemeConfig {
  switch (preset) {
    case ThemePresets.JETBRAINS_DARK:
      return JetBrainsDarkConfig;
    case ThemePresets.JETBRAINS_LIGHT:
      return JetBrainsLightConfig;
    case ThemePresets.GITHUB_DARK:
      return GitHubDarkConfig;
    case ThemePresets.GITHUB_LIGHT:
      return GitHubLightConfig;
    case ThemePresets.VS_CODE_DARK:
      return VSCodeDarkConfig;
    case ThemePresets.MONOKAI:
      return MonokaiConfig;
    case ThemePresets.NORD:
      return NordConfig;
    case ThemePresets.SOLARIZED_LIGHT:
      return SolarizedLightConfig;
    case ThemePresets.SOLARIZED_DARK:
      return SolarizedDarkConfig;
    default:
      return JetBrainsDarkConfig;
  }
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      // ========== 初始状态 ==========
      currentTheme: JetBrainsDarkConfig,
      customThemes: [],
      isEditing: false,
      editorState: {
        currentTab: 'colors',
        selectedCategory: '',
        isPreviewMode: false,
        hasUnsavedChanges: false
      },

      // ========== 操作 ==========
      setTheme: (theme) => {
        set({ currentTheme: theme });
        applyTheme(theme);
        window.ccBackend?.updateTheme(theme);
      },

      setPresetTheme: (preset) => {
        const config = getPresetConfig(preset);
        set({ currentTheme: config });
        applyTheme(config);
        window.ccBackend?.updateTheme(config);
      },

      saveCustomTheme: (theme) => {
        set((state) => ({
          customThemes: [...state.customThemes, theme]
        }));
        window.ccBackend?.saveCustomTheme(theme);
      },

      deleteCustomTheme: (themeId) => {
        set((state) => ({
          customThemes: state.customThemes.filter((t) => t.id !== themeId)
        }));
        window.ccBackend?.deleteCustomTheme(themeId);
      },

      startEditing: () => {
        set({ isEditing: true });
      },

      finishEditing: () => {
        set({
          isEditing: false,
          editorState: {
            ...get().editorState,
            hasUnsavedChanges: false
          }
        });
      },

      setEditorState: (newState) => {
        set((state) => ({
          editorState: { ...state.editorState, ...newState }
        }));
      }
    }),
    {
      name: 'ccgui-theme-storage',
      partialize: (state) => ({
        currentTheme: state.currentTheme,
        customThemes: state.customThemes
      })
    }
  )
);

/**
 * 应用主题到 DOM
 */
function applyTheme(theme: ThemeConfig): void {
  const root = document.documentElement;

  // 应用颜色
  Object.entries(theme.colors).forEach(([key, value]) => {
    root.style.setProperty(`--color-${kebabCase(key)}`, value);
  });

  // 应用字体
  root.style.setProperty('--font-message', theme.typography.messageFont);
  root.style.setProperty('--font-code', theme.typography.codeFont);
  root.style.setProperty('--font-size', `${theme.typography.fontSize}px`);
  root.style.setProperty('--font-size-small', `${theme.typography.fontSizeSmall}px`);
  root.style.setProperty('--font-size-large', `${theme.typography.fontSizeLarge}px`);
  root.style.setProperty('--line-height', `${theme.typography.lineHeight}`);

  // 应用间距
  root.style.setProperty('--spacing-message', `${theme.spacing.messageSpacing}px`);
  root.style.setProperty('--spacing-code', `${theme.spacing.codeBlockPadding}px`);
  root.style.setProperty('--header-height', `${theme.spacing.headerHeight}px`);
  root.style.setProperty('--sidebar-width', `${theme.spacing.sidebarWidth}px`);

  // 应用圆角
  root.style.setProperty('--radius-message', `${theme.borderRadius.messageBubble}px`);
  root.style.setProperty('--radius-code', `${theme.borderRadius.codeBlock}px`);
  root.style.setProperty('--radius-button', `${theme.borderRadius.button}px`);
  root.style.setProperty('--radius-input', `${theme.borderRadius.input}px`);
  root.style.setProperty('--radius-modal', `${theme.borderRadius.modal}px`);

  // 应用阴影
  root.style.setProperty('--shadow-sm', theme.shadow.sm);
  root.style.setProperty('--shadow-md', theme.shadow.md);
  root.style.setProperty('--shadow-lg', theme.shadow.lg);
  root.style.setProperty('--shadow-xl', theme.shadow.xl);

  // 应用主题模式
  root.setAttribute('data-theme', theme.isDark ? 'dark' : 'light');
}

/**
 * 转换为 kebab-case
 */
function kebabCase(str: string): string {
  return str.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
}
