/**
 * useFileDrop - 文件拖拽 Hook
 *
 * 使用dragCounter解决子元素触发dragEnter/dragLeave的问题。
 * 支持MIME类型和扩展名过滤。
 */

import { useCallback, useState } from 'react';

export interface UseFileDropOptions {
  /** 文件放下回调 */
  onDrop?: (files: File[]) => void;
  /** 拖拽进入回调 */
  onDragEnter?: () => void;
  /** 拖拽离开回调 */
  onDragLeave?: () => void;
  /** 接受的MIME类型或扩展名，逗号分隔 */
  accept?: string;
  /** 是否允许多文件 */
  multiple?: boolean;
}

export interface UseFileDropReturn {
  /** 是否正在拖拽 */
  isDragging: boolean;
  /** 拖拽事件props */
  dropProps: {
    onDragEnter: (e: React.DragEvent) => void;
    onDragLeave: (e: React.DragEvent) => void;
    onDragOver: (e: React.DragEvent) => void;
    onDrop: (e: React.DragEvent) => void;
  };
}

/**
 * 文件拖拽Hook
 *
 * 使用dragCounter解决子元素触发dragEnter/dragLeave的问题。
 * 支持MIME类型和扩展名过滤。
 *
 * @param options - 配置选项
 * @returns 拖拽状态和事件props
 */
export const useFileDrop = (options: UseFileDropOptions = {}): UseFileDropReturn => {
  const { onDrop, onDragEnter, onDragLeave, accept, multiple = true } = options;
  const [isDragging, setIsDragging] = useState(false);
  const [, setDragCounter] = useState(0);

  const handleDragEnter = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragCounter((prev) => {
        const next = prev + 1;
        if (next === 1) {
          setIsDragging(true);
          onDragEnter?.();
        }
        return next;
      });
    },
    [onDragEnter]
  );

  const handleDragLeave = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragCounter((prev) => {
        const next = prev - 1;
        if (next === 0) {
          setIsDragging(false);
          onDragLeave?.();
        }
        return next;
      });
    },
    [onDragLeave]
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragCounter(0);
      setIsDragging(false);

      const files = Array.from(e.dataTransfer.files);

      // 过滤文件类型
      let filteredFiles = files;
      if (accept) {
        const acceptedTypes = accept.split(',').map((t) => t.trim());
        filteredFiles = files.filter((file) => {
          return acceptedTypes.some((type) => {
            if (type.startsWith('.')) {
              return file.name.toLowerCase().endsWith(type.toLowerCase());
            }
            if (file.type) {
              return file.type === type || file.type.startsWith(type + '/');
            }
            return false;
          });
        });
      }

      if (!multiple && filteredFiles.length > 0) {
        const firstFile = filteredFiles[0];
        if (firstFile) {
          filteredFiles = [firstFile];
        }
      }

      if (filteredFiles.length > 0) {
        onDrop?.(filteredFiles);
      }
    },
    [accept, multiple, onDrop]
  );

  const dropProps = {
    onDragEnter: handleDragEnter,
    onDragLeave: handleDragLeave,
    onDragOver: handleDragOver,
    onDrop: handleDrop
  };

  return {
    isDragging,
    dropProps
  };
};
