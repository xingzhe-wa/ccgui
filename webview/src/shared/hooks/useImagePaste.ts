/**
 * useImagePaste - 图片粘贴 Hook
 *
 * 监听paste事件，从clipboardData中提取图片文件。
 * 需要绑定到具体的DOM元素上使用。
 */

import { useEffect, useRef, useCallback } from 'react';

export interface UseImagePasteOptions {
  /** 粘贴回调 */
  onPaste?: (files: File[]) => void;
  /** 接受的MIME类型列表 */
  accept?: string[];
}

export interface UseImagePasteReturn {
  /** 目标元素ref */
  targetRef: React.RefObject<HTMLElement | null>;
}

/**
 * 图片粘贴Hook
 *
 * 监听paste事件，从clipboardData中提取图片文件。
 * 需要绑定到具体的DOM元素上使用。
 *
 * @param options - 配置选项
 * @returns 目标元素ref
 */
export const useImagePaste = (
  options: UseImagePasteOptions = {}
): UseImagePasteReturn => {
  const { onPaste, accept = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'] } =
    options;
  const targetRef = useRef<HTMLElement | null>(null);

  const handlePaste = useCallback(
    (e: ClipboardEvent) => {
      const clipboardData = e.clipboardData;
      if (!clipboardData) return;

      const items = Array.from(clipboardData.items);
      const imageFiles: File[] = [];

      for (const item of items) {
        if (item.type.startsWith('image/')) {
          const file = item.getAsFile();
          if (file && accept.includes(file.type)) {
            imageFiles.push(file);
          }
        }
      }

      if (imageFiles.length > 0) {
        e.preventDefault();
        onPaste?.(imageFiles);
      }
    },
    [onPaste, accept]
  );

  useEffect(() => {
    const element = targetRef.current;
    if (!element) return;

    element.addEventListener('paste', handlePaste);
    return () => {
      element.removeEventListener('paste', handlePaste);
    };
  }, [handlePaste]);

  return { targetRef };
};
