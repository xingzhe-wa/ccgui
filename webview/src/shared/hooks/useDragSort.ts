/**
 * 拖拽排序 Hook
 *
 * 参考 jetbrains-cc-gui 的拖拽排序实现
 */

import { useState, useCallback, useEffect } from 'react';

export interface DragSortItem {
  id: string;
}

interface UseDragSortOptions<T extends DragSortItem> {
  /** 列表项 */
  items: T[];
  /** 排序完成回调 */
  onSort: (orderedIds: string[]) => void | Promise<void>;
  /** 固定不参与排序的 ID */
  pinnedIds?: string[];
}

interface DragSortState<T> {
  /** 本地排序后的列表 */
  localItems: T[];
  /** 正在拖拽的 ID */
  draggedId: string | null;
  /** 拖拽悬停的 ID */
  dragOverId: string | null;
  /** 开始拖拽 */
  handleDragStart: (e: React.DragEvent, id: string) => void;
  /** 拖拽经过 */
  handleDragOver: (e: React.DragEvent, id: string) => void;
  /** 拖拽离开 */
  handleDragLeave: (e: React.DragEvent) => void;
  /** 放下 */
  handleDrop: (e: React.DragEvent, id: string) => void;
  /** 拖拽结束 */
  handleDragEnd: () => void;
}

/**
 * 拖拽排序 Hook
 *
 * @example
 * ```tsx
 * const { localItems, draggedId, dragOverId, handleDragStart, handleDragOver, handleDragLeave, handleDrop, handleDragEnd } =
 *   useDragSort({
 *     items: profiles,
 *     onSort: async (orderedIds) => {
 *       await window.ccBackend?.reorderProviderProfiles(orderedIds);
 *     },
 *     pinnedIds: [SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS, SPECIAL_PROVIDER_IDS.CLI_LOGIN]
 *   });
 * ```
 */
export function useDragSort<T extends DragSortItem>({
  items,
  onSort,
  pinnedIds = []
}: UseDragSortOptions<T>): DragSortState<T> {
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<string | null>(null);
  const [localItems, setLocalItems] = useState<T[]>(items);

  // 更新本地 items（当外部 items 变化时）
  useEffect(() => {
    setLocalItems(items);
  }, [items]);

  /**
   * 开始拖拽
   */
  const handleDragStart = useCallback((e: React.DragEvent, id: string) => {
    // 固定项不可拖拽
    if (pinnedIds.includes(id)) {
      e.preventDefault();
      return;
    }
    setDraggedId(id);
    e.dataTransfer.effectAllowed = 'move';
    // 设置拖拽图像（可选）
    // e.dataTransfer.setDragImage(e.target as Element, 0, 0);
  }, [pinnedIds]);

  /**
   * 拖拽经过
   */
  const handleDragOver = useCallback((e: React.DragEvent, id: string) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';

    if (draggedId && draggedId !== id && !pinnedIds.includes(id)) {
      setDragOverId(id);

      // 重新排序
      const draggedIndex = localItems.findIndex(item => item.id === draggedId);
      const targetIndex = localItems.findIndex(item => item.id === id);

      if (draggedId && draggedIndex !== -1 && targetIndex !== -1 && draggedIndex !== targetIndex) {
        const newItems = [...localItems];
        // 确保 draggedIndex 和 targetIndex 是有效的
        if (draggedIndex >= 0 && draggedIndex < newItems.length && targetIndex >= 0 && targetIndex < newItems.length) {
          const removed = newItems.splice(draggedIndex, 1)[0];
          if (removed) {
            newItems.splice(targetIndex, 0, removed);
            setLocalItems(newItems);
          }
        }
      }
    }
  }, [draggedId, localItems, pinnedIds]);

  /**
   * 拖拽离开
   */
  const handleDragLeave = useCallback((e: React.DragEvent) => {
    // 延迟清除，避免闪烁
    // 只有当离开整个拖拽区域时才清除
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const x = e.clientX;
    const y = e.clientY;

    if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) {
      setDragOverId(null);
    }
  }, []);

  /**
   * 放下
   */
  const handleDrop = useCallback(async (e: React.DragEvent, _id: string) => {
    e.preventDefault();
    e.stopPropagation();

    const orderedIds = localItems.map(item => item.id);
    await onSort(orderedIds);

    setDraggedId(null);
    setDragOverId(null);
  }, [localItems, onSort]);

  /**
   * 拖拽结束
   */
  const handleDragEnd = useCallback(() => {
    setDraggedId(null);
    setDragOverId(null);
  }, []);

  return {
    localItems,
    draggedId,
    dragOverId,
    handleDragStart,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleDragEnd
  };
}
