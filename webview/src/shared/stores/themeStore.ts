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
 * HEX 颜色转 HSL 字符串（Tailwind 标准格式）
 * 输入: "#0d47a1" → 输出: "217 81% 34%"
 */
function hexToHsl(hex: string): string {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  let h = 0;
  let s = 0;
  const l = (max + min) / 2;

  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
      case g: h = ((b - r) / d + 2) / 6; break;
      case b: h = ((r - g) / d + 4) / 6; break;
    }
  }

  return `${Math.round(h * 360)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
}

/**
 * 应用主题到 DOM
 *
 * 策略：将 HEX 颜色转为 HSL 格式，设置到 Tailwind 认识的 CSS 变量名。
 * 变量名遵循 globals.css 中 `:root` / `.dark` 的定义。
 * 这样 Tailwind 的 `hsl(var(--primary))` 能正确读取到运行时主题色。
 */
function applyTheme(theme: ThemeConfig): void {
  const root = document.documentElement;
  const { colors } = theme;

  // ========== 核心：Tailwind 标准 HSL 变量（用于 hsl(var(--xxx)) 引用） ==========
  root.style.setProperty('--primary', hexToHsl(colors.primary));
  root.style.setProperty('--primary-foreground', hexToHsl(colors.accentForeground));
  root.style.setProperty('--background', hexToHsl(colors.background));
  root.style.setProperty('--foreground', hexToHsl(colors.foreground));
  root.style.setProperty('--muted', hexToHsl(colors.muted));
  root.style.setProperty('--muted-foreground', hexToHsl(colors.mutedForeground));
  root.style.setProperty('--accent', hexToHsl(colors.accent));
  root.style.setProperty('--accent-foreground', hexToHsl(colors.accentForeground));
  root.style.setProperty('--destructive', hexToHsl(colors.destructive));
  root.style.setProperty('--destructive-foreground', hexToHsl(colors.accentForeground));
  root.style.setProperty('--border', hexToHsl(colors.border));
  root.style.setProperty('--input', hexToHsl(colors.border));
  root.style.setProperty('--ring', hexToHsl(colors.primary));
  root.style.setProperty('--card', hexToHsl(colors.background));
  root.style.setProperty('--card-foreground', hexToHsl(colors.foreground));
  root.style.setProperty('--popover', hexToHsl(colors.background));
  root.style.setProperty('--popover-foreground', hexToHsl(colors.foreground));
  root.style.setProperty('--secondary', hexToHsl(colors.muted));
  root.style.setProperty('--secondary-foreground', hexToHsl(colors.foreground));

  // ========== 自定义变量：消息气泡、代码块等 ==========
  // 消息气泡（Tailwind 格式：--userMessage, --aiMessage, --systemMessage）
  root.style.setProperty('--userMessage', hexToHsl(colors.userMessage));
  root.style.setProperty('--userMessageForeground', hexToHsl(colors.foreground));
  root.style.setProperty('--aiMessage', hexToHsl(colors.aiMessage));
  root.style.setProperty('--aiMessageForeground', hexToHsl(colors.foreground));
  root.style.setProperty('--systemMessage', hexToHsl(colors.systemMessage));
  root.style.setProperty('--systemMessageForeground', hexToHsl(colors.foreground));
  // 代码块
  root.style.setProperty('--code-background', hexToHsl(colors.codeBackground));
  root.style.setProperty('--code-foreground', hexToHsl(colors.codeForeground));
  // 背景层级
  root.style.setProperty('--background-secondary', hexToHsl(colors.muted));
  root.style.setProperty('--background-elevated', hexToHsl(colors.background));
  // 文字变体
  root.style.setProperty('--foreground-muted', hexToHsl(colors.mutedForeground));

  // ========== 排版 ==========
  root.style.setProperty('--font-message', theme.typography.messageFont);
  root.style.setProperty('--font-code', theme.typography.codeFont);
  root.style.setProperty('--font-size-base', `${theme.typography.fontSize}px`);
  root.style.setProperty('--font-size-small', `${theme.typography.fontSizeSmall}px`);
  root.style.setProperty('--font-size-large', `${theme.typography.fontSizeLarge}px`);
  root.style.setProperty('--line-height', `${theme.typography.lineHeight}`);

  // ========== 间距 ==========
  root.style.setProperty('--spacing-message', `${theme.spacing.messageSpacing}px`);
  root.style.setProperty('--spacing-code', `${theme.spacing.codeBlockPadding}px`);
  root.style.setProperty('--header-height', `${theme.spacing.headerHeight}px`);
  root.style.setProperty('--sidebar-width', `${theme.spacing.sidebarWidth}px`);

  // ========== 圆角 ==========
  root.style.setProperty('--radius-message', `${theme.borderRadius.messageBubble}px`);
  root.style.setProperty('--radius-code', `${theme.borderRadius.codeBlock}px`);
  root.style.setProperty('--radius-button', `${theme.borderRadius.button}px`);
  root.style.setProperty('--radius-input', `${theme.borderRadius.input}px`);
  root.style.setProperty('--radius-modal', `${theme.borderRadius.modal}px`);

  // ========== 阴影 ==========
  root.style.setProperty('--shadow-sm', theme.shadow.sm);
  root.style.setProperty('--shadow-md', theme.shadow.md);
  root.style.setProperty('--shadow-lg', theme.shadow.lg);
  root.style.setProperty('--shadow-xl', theme.shadow.xl);

  // ========== 主题模式 ==========
  root.setAttribute('data-theme', theme.isDark ? 'dark' : 'light');
}
