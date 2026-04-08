/**
 * useTheme - 主题切换 Hook
 */

import { useEffect } from 'react';
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

  useEffect(() => {
    // 应用主题到 DOM
    applyThemeToDOM(currentTheme);
  }, [currentTheme]);

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

/**
 * 应用主题到 DOM
 */
function applyThemeToDOM(theme: ThemeConfig): void {
  const root = document.documentElement;

  // 应用颜色 CSS 变量
  const colorMappings: Record<string, string> = {
    primary: theme.colors.primary,
    background: theme.colors.background,
    foreground: theme.colors.foreground,
    muted: theme.colors.muted,
    mutedForeground: theme.colors.mutedForeground,
    accent: theme.colors.accent,
    accentForeground: theme.colors.accentForeground,
    destructive: theme.colors.destructive,
    border: theme.colors.border,
    userMessage: theme.colors.userMessage,
    aiMessage: theme.colors.aiMessage,
    systemMessage: theme.colors.systemMessage,
    codeBackground: theme.colors.codeBackground,
    codeForeground: theme.colors.codeForeground
  };

  Object.entries(colorMappings).forEach(([key, value]) => {
    root.style.setProperty(`--color-${kebabCase(key)}`, value);
  });

  // 应用字体
  root.style.setProperty('--font-message', theme.typography.messageFont);
  root.style.setProperty('--font-code', theme.typography.codeFont);

  // 应用间距
  root.style.setProperty('--spacing-message', `${theme.spacing.messageSpacing}px`);
  root.style.setProperty('--header-height', `${theme.spacing.headerHeight}px`);
  root.style.setProperty('--sidebar-width', `${theme.spacing.sidebarWidth}px`);

  // 应用圆角
  root.style.setProperty('--radius-message', `${theme.borderRadius.messageBubble}px`);
  root.style.setProperty('--radius-button', `${theme.borderRadius.button}px`);
  root.style.setProperty('--radius-input', `${theme.borderRadius.input}px`);

  // 应用阴影
  root.style.setProperty('--shadow-sm', theme.shadow.sm);
  root.style.setProperty('--shadow-md', theme.shadow.md);
  root.style.setProperty('--shadow-lg', theme.shadow.lg);

  // 应用主题模式
  root.setAttribute('data-theme', theme.isDark ? 'dark' : 'light');
}

function kebabCase(str: string): string {
  return str.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
}
