/**
 * 主题相关类型定义
 */

import type { ID } from './index';

// ============== 颜色方案 ==============

/**
 * 颜色方案
 */
export interface ColorScheme {
  // 主色调
  primary: string;
  // 背景色
  background: string;
  // 前景色
  foreground: string;
  // 静音色
  muted: string;
  mutedForeground: string;
  // 强调色
  accent: string;
  accentForeground: string;
  // 危险色
  destructive: string;
  // 边框色
  border: string;
  // 消息颜色
  userMessage: string;
  aiMessage: string;
  systemMessage: string;
  // 代码块颜色
  codeBackground: string;
  codeForeground: string;
}

/**
 * 字体配置
 */
export interface Typography {
  messageFont: string;
  codeFont: string;
  fontSize: number;
  fontSizeSmall: number;
  fontSizeLarge: number;
  lineHeight: number;
}

/**
 * 间距配置
 */
export interface Spacing {
  messageSpacing: number;
  codeBlockPadding: number;
  headerHeight: number;
  sidebarWidth: number;
}

/**
 * 圆角配置
 */
export interface BorderRadius {
  messageBubble: number;
  codeBlock: number;
  button: number;
  input: number;
  modal: number;
}

/**
 * 阴影配置
 */
export interface Shadow {
  sm: string;
  md: string;
  lg: string;
  xl: string;
}

// ============== 主题配置 ==============

/**
 * 主题配置
 */
export interface ThemeConfig {
  id: ID;
  name: string;
  isDark: boolean;
  colors: ColorScheme;
  typography: Typography;
  spacing: Spacing;
  borderRadius: BorderRadius;
  shadow: Shadow;
}

/**
 * 主题预设
 * 注意：这是枚举，不是普通对象
 */
export enum ThemePresets {
  JETBRAINS_DARK = 'jetbrains-dark',
  JETBRAINS_LIGHT = 'jetbrains-light',
  GITHUB_DARK = 'github-dark',
  GITHUB_LIGHT = 'github-light',
  VS_CODE_DARK = 'vscode-dark',
  MONOKAI = 'monokai',
  SOLARIZED_LIGHT = 'solarized-light',
  SOLARIZED_DARK = 'solarized-dark',
  NORD = 'nord',
  CUSTOM = 'custom'
}

/**
 * 主题编辑器状态
 */
export interface ThemeEditorState {
  currentTab: 'colors' | 'typography' | 'spacing' | 'radius' | 'shadow';
  selectedCategory: string;
  isPreviewMode: boolean;
  hasUnsavedChanges: boolean;
}

// ============== 主题预设配置 ==============

/**
 * JetBrains Dark 主题配置
 */
export const JetBrainsDarkConfig: ThemeConfig = {
  id: 'jetbrains-dark',
  name: 'JetBrains Dark',
  isDark: true,
  colors: {
    primary: '#0d47a1',
    background: '#1e1e1e',
    foreground: '#d4d4d4',
    muted: '#2d2d2d',
    mutedForeground: '#858585',
    accent: '#0d47a1',
    accentForeground: '#ffffff',
    destructive: '#f44336',
    border: '#3c3c3c',
    userMessage: '#0d47a1',
    aiMessage: '#2d2d2d',
    systemMessage: '#ff9800',
    codeBackground: '#1e1e1e',
    codeForeground: '#d4d4d4'
  },
  typography: {
    messageFont: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'JetBrains Mono, Fira Code, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 12,
    codeBlock: 8,
    button: 6,
    input: 6,
    modal: 12
  },
  shadow: {
    sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
    md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
    lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
    xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)'
  }
};

/**
 * GitHub Dark 主题配置
 */
export const GitHubDarkConfig: ThemeConfig = {
  id: 'github-dark',
  name: 'GitHub Dark',
  isDark: true,
  colors: {
    primary: '#58a6ff',
    background: '#0d1117',
    foreground: '#c9d1d9',
    muted: '#161b22',
    mutedForeground: '#8b949e',
    accent: '#58a6ff',
    accentForeground: '#ffffff',
    destructive: '#f85149',
    border: '#30363d',
    userMessage: '#58a6ff',
    aiMessage: '#161b22',
    systemMessage: '#f0883e',
    codeBackground: '#0d1117',
    codeForeground: '#c9d1d9'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'SFMono-Regular, Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 8,
    codeBlock: 6,
    button: 6,
    input: 6,
    modal: 8
  },
  shadow: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.3)',
    md: '0 4px 6px rgba(0, 0, 0, 0.4)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.5)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.6)'
  }
};

/**
 * VSCode Dark 主题配置
 */
export const VSCodeDarkConfig: ThemeConfig = {
  id: 'vscode-dark',
  name: 'VSCode Dark',
  isDark: true,
  colors: {
    primary: '#007acc',
    background: '#1e1e1e',
    foreground: '#d4d4d4',
    muted: '#252526',
    mutedForeground: '#858585',
    accent: '#007acc',
    accentForeground: '#ffffff',
    destructive: '#f14c4c',
    border: '#3c3c3c',
    userMessage: '#007acc',
    aiMessage: '#252526',
    systemMessage: '#dcdcaa',
    codeBackground: '#1e1e1e',
    codeForeground: '#d4d4d4'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 4,
    codeBlock: 0,
    button: 4,
    input: 4,
    modal: 8
  },
  shadow: {
    sm: '0 2px 4px rgba(0, 0, 0, 0.4)',
    md: '0 4px 8px rgba(0, 0, 0, 0.5)',
    lg: '0 8px 16px rgba(0, 0, 0, 0.6)',
    xl: '0 12px 24px rgba(0, 0, 0, 0.7)'
  }
};

/**
 * Monokai 主题配置
 */
export const MonokaiConfig: ThemeConfig = {
  id: 'monokai',
  name: 'Monokai',
  isDark: true,
  colors: {
    primary: '#f92672',
    background: '#272822',
    foreground: '#f8f8f2',
    muted: '#3e3d32',
    mutedForeground: '#75715e',
    accent: '#f92672',
    accentForeground: '#ffffff',
    destructive: '#f92672',
    border: '#3e3d32',
    userMessage: '#f92672',
    aiMessage: '#3e3d32',
    systemMessage: '#e6db74',
    codeBackground: '#272822',
    codeForeground: '#f8f8f2'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'Fira Code, Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 4,
    codeBlock: 0,
    button: 4,
    input: 4,
    modal: 8
  },
  shadow: {
    sm: '0 2px 4px rgba(0, 0, 0, 0.4)',
    md: '0 4px 8px rgba(0, 0, 0, 0.5)',
    lg: '0 8px 16px rgba(0, 0, 0, 0.6)',
    xl: '0 12px 24px rgba(0, 0, 0, 0.7)'
  }
};

/**
 * Nord 主题配置
 */
export const NordConfig: ThemeConfig = {
  id: 'nord',
  name: 'Nord',
  isDark: true,
  colors: {
    primary: '#88c0d0',
    background: '#2e3440',
    foreground: '#eceff4',
    muted: '#3b4252',
    mutedForeground: '#d8dee9',
    accent: '#88c0d0',
    accentForeground: '#2e3440',
    destructive: '#bf616a',
    border: '#434c5e',
    userMessage: '#88c0d0',
    aiMessage: '#3b4252',
    systemMessage: '#a3be8c',
    codeBackground: '#2e3440',
    codeForeground: '#eceff4'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'Fira Code, Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 8,
    codeBlock: 4,
    button: 4,
    input: 4,
    modal: 8
  },
  shadow: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.3)',
    md: '0 4px 6px rgba(0, 0, 0, 0.4)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.5)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.6)'
  }
};

/**
 * Solarized Light 主题配置
 */
export const SolarizedLightConfig: ThemeConfig = {
  id: 'solarized-light',
  name: 'Solarized Light',
  isDark: false,
  colors: {
    primary: '#268bd2',
    background: '#fdf6e3',
    foreground: '#657b83',
    muted: '#eee8d5',
    mutedForeground: '#93a1a1',
    accent: '#268bd2',
    accentForeground: '#ffffff',
    destructive: '#dc322f',
    border: '#93a1a1',
    userMessage: '#268bd2',
    aiMessage: '#eee8d5',
    systemMessage: '#b58900',
    codeBackground: '#eee8d5',
    codeForeground: '#657b83'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'Fira Code, Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 8,
    codeBlock: 4,
    button: 4,
    input: 4,
    modal: 8
  },
  shadow: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.1)',
    md: '0 4px 6px rgba(0, 0, 0, 0.1)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.15)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.2)'
  }
};

/**
 * JetBrains Light 主题配置
 */
export const JetBrainsLightConfig: ThemeConfig = {
  id: 'jetbrains-light',
  name: 'JetBrains Light',
  isDark: false,
  colors: {
    primary: '#0d47a1',
    background: '#ffffff',
    foreground: '#333333',
    muted: '#f5f5f5',
    mutedForeground: '#666666',
    accent: '#0d47a1',
    accentForeground: '#ffffff',
    destructive: '#f44336',
    border: '#e0e0e0',
    userMessage: '#0d47a1',
    aiMessage: '#f5f5f5',
    systemMessage: '#ff9800',
    codeBackground: '#f5f5f5',
    codeForeground: '#333333'
  },
  typography: {
    messageFont: 'Inter, -apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'JetBrains Mono, Fira Code, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 12,
    codeBlock: 8,
    button: 6,
    input: 6,
    modal: 12
  },
  shadow: {
    sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
    md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
    lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
    xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)'
  }
};

/**
 * GitHub Light 主题配置
 */
export const GitHubLightConfig: ThemeConfig = {
  id: 'github-light',
  name: 'GitHub Light',
  isDark: false,
  colors: {
    primary: '#0969da',
    background: '#ffffff',
    foreground: '#24292f',
    muted: '#f6f8fa',
    mutedForeground: '#57606a',
    accent: '#0969da',
    accentForeground: '#ffffff',
    destructive: '#cf222e',
    border: '#d0d7de',
    userMessage: '#0969da',
    aiMessage: '#f6f8fa',
    systemMessage: '#bf8700',
    codeBackground: '#f6f8fa',
    codeForeground: '#24292f'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'SFMono-Regular, Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 8,
    codeBlock: 6,
    button: 6,
    input: 6,
    modal: 8
  },
  shadow: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.1)',
    md: '0 4px 6px rgba(0, 0, 0, 0.1)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.15)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.2)'
  }
};

/**
 * Solarized Dark 主题配置
 */
export const SolarizedDarkConfig: ThemeConfig = {
  id: 'solarized-dark',
  name: 'Solarized Dark',
  isDark: true,
  colors: {
    primary: '#268bd2',
    background: '#002b36',
    foreground: '#839496',
    muted: '#073642',
    mutedForeground: '#586e75',
    accent: '#268bd2',
    accentForeground: '#002b36',
    destructive: '#dc322f',
    border: '#073642',
    userMessage: '#268bd2',
    aiMessage: '#073642',
    systemMessage: '#b58900',
    codeBackground: '#073642',
    codeForeground: '#839496'
  },
  typography: {
    messageFont: '-apple-system, BlinkMacSystemFont, sans-serif',
    codeFont: 'Fira Code, Consolas, monospace',
    fontSize: 14,
    fontSizeSmall: 12,
    fontSizeLarge: 16,
    lineHeight: 1.5
  },
  spacing: {
    messageSpacing: 16,
    codeBlockPadding: 12,
    headerHeight: 48,
    sidebarWidth: 280
  },
  borderRadius: {
    messageBubble: 8,
    codeBlock: 4,
    button: 4,
    input: 4,
    modal: 8
  },
  shadow: {
    sm: '0 1px 2px rgba(0, 0, 0, 0.3)',
    md: '0 4px 6px rgba(0, 0, 0, 0.4)',
    lg: '0 10px 15px rgba(0, 0, 0, 0.5)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.6)'
  }
};
