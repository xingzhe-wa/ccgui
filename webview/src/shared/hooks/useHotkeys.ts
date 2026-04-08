/**
 * useHotkeys - 快捷键绑定 Hook
 *
 * 使用ref存储hotkeys避免频繁绑定/解绑。
 * 支持修饰键组合（Ctrl/Shift/Alt/Meta）。
 * 组件卸载时自动清理事件监听。
 */

import { useEffect, useRef } from 'react';

export type HotkeyCallback = (e: KeyboardEvent) => void;

/**
 * 快捷键定义
 */
export interface HotkeyItem {
  /** 键名 */
  key: string;
  /** 是否需要Ctrl键 */
  ctrlKey?: boolean;
  /** 是否需要Shift键 */
  shiftKey?: boolean;
  /** 是否需要Alt键 */
  altKey?: boolean;
  /** 是否需要Meta键 */
  metaKey?: boolean;
  /** 回调函数 */
  callback: HotkeyCallback;
  /** 描述（用于调试） */
  description?: string;
  /** 是否阻止默认行为 */
  preventDefault?: boolean;
}

/**
 * 快捷键绑定Hook
 *
 * 使用ref存储hotkeys避免频繁绑定/解绑。
 * 支持修饰键组合（Ctrl/Shift/Alt/Meta）。
 * 组件卸载时自动清理事件监听。
 *
 * @param hotkeys - 快捷键定义数组
 */
export const useHotkeys = (hotkeys: HotkeyItem[]) => {
  const hotkeysRef = useRef(hotkeys);

  useEffect(() => {
    hotkeysRef.current = hotkeys;
  }, [hotkeys]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      for (const hotkey of hotkeysRef.current) {
        const {
          key,
          ctrlKey = false,
          shiftKey = false,
          altKey = false,
          metaKey = false,
          callback,
          preventDefault = true
        } = hotkey;

        const keyMatch = e.key.toLowerCase() === key.toLowerCase();
        const ctrlMatch = e.ctrlKey === ctrlKey;
        const shiftMatch = e.shiftKey === shiftKey;
        const altMatch = e.altKey === altKey;
        const metaMatch = e.metaKey === metaKey;

        if (keyMatch && ctrlMatch && shiftMatch && altMatch && metaMatch) {
          if (preventDefault) {
            e.preventDefault();
          }
          callback(e);
          break;
        }
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, []);
};

/**
 * 快捷键描述生成器
 */
export const describeHotkey = (hotkey: HotkeyItem): string => {
  const parts: string[] = [];
  if (hotkey.ctrlKey) parts.push('Ctrl');
  if (hotkey.shiftKey) parts.push('Shift');
  if (hotkey.altKey) parts.push('Alt');
  if (hotkey.metaKey) parts.push('Meta');
  parts.push(hotkey.key.toUpperCase());
  return parts.join('+');
};
