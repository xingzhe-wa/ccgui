/**
 * i18n 国际化配置
 */

import { zhCN } from './zh-CN';
import { enUS } from './en-US';

export type Locale = 'zh-CN' | 'en-US';
export type Translation = typeof zhCN;

// 语言包映射
const resources: Record<Locale, Translation> = {
  'zh-CN': zhCN,
  'en-US': enUS,
};

// 默认语言
const DEFAULT_LOCALE: Locale = 'zh-CN';

/**
 * i18n 管理类
 */
class I18nManager {
  private locale: Locale = DEFAULT_LOCALE;
  private listeners: Set<(locale: Locale) => void> = new Set();

  /**
   * 获取当前语言
   */
  getLocale(): Locale {
    return this.locale;
  }

  /**
   * 设置语言
   */
  setLocale(locale: Locale): void {
    if (locale !== this.locale) {
      this.locale = locale;
      this.listeners.forEach((listener) => listener(locale));
    }
  }

  /**
   * 获取翻译文本
   */
  t(path: string, params?: Record<string, string | number>): string {
    const keys = path.split('.');
    let value: any = resources[this.locale];

    for (const key of keys) {
      if (value && typeof value === 'object' && key in value) {
        value = value[key];
      } else {
        // Fallback to default locale
        value = resources[DEFAULT_LOCALE];
        for (const k of keys) {
          if (value && typeof value === 'object' && k in value) {
            value = value[k];
          } else {
            return path; // Return key if translation not found
          }
        }
        break;
      }
    }

    if (typeof value !== 'string') {
      return path;
    }

    // Replace parameters
    if (params) {
      return value.replace(/\{(\w+)\}/g, (match, key) => {
        return params[key]?.toString() ?? match;
      });
    }

    return value;
  }

  /**
   * 订阅语言变化
   */
  subscribe(listener: (locale: Locale) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /**
   * 初始化语言设置
   */
  async init(): Promise<void> {
    // 从 localStorage 读取用户设置
    const savedLocale = localStorage.getItem('ccgui-locale') as Locale;
    if (savedLocale && savedLocale in resources) {
      this.setLocale(savedLocale);
    } else {
      // 根据浏览器语言自动选择
      const browserLang = navigator.language;
      if (browserLang.startsWith('zh')) {
        this.setLocale('zh-CN');
      } else {
        this.setLocale('en-US');
      }
    }
  }

  /**
   * 保存语言设置
   */
  saveLocale(locale: Locale): void {
    localStorage.setItem('ccgui-locale', locale);
    this.setLocale(locale);
  }
}

// 单例实例
export const i18n = new I18nManager();

/**
 * React Hook: 使用翻译
 */
export function useTranslation() {
  const locale = i18n.getLocale();

  return {
    locale,
    t: (path: string, params?: Record<string, string | number>) => i18n.t(path, params),
    setLocale: (locale: Locale) => i18n.saveLocale(locale),
  };
}

/**
 * 工具函数: 获取翻译文本
 */
export function t(path: string, params?: Record<string, string | number>): string {
  return i18n.t(path, params);
}

// 初始化 i18n
i18n.init().catch(console.error);
