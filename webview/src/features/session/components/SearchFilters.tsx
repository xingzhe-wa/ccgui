/**
 * SearchFilters - 搜索过滤器组件
 *
 * 提供会话搜索的过滤功能，支持：
 * - 按会话类型过滤
 * - 按日期范围过滤
 * - 按标签过滤
 */

import { memo, useState, useCallback } from 'react';
import { Filter, Calendar, Tag, X } from 'lucide-react';
import { SessionType } from '@/shared/types';
import { cn } from '@/shared/utils/cn';

export interface SearchFiltersProps {
  /** 当前过滤器值 */
  filters: {
    type?: SessionType;
    dateRange?: {
      start: number;
      end: number;
    };
    tags?: string[];
  };
  /** 过滤器变更回调 */
  onFiltersChange: (filters: SearchFiltersProps['filters']) => void;
  className?: string;
}

/**
 * 搜索过滤器组件
 */
export const SearchFilters = memo<SearchFiltersProps>(function SearchFilters({
  filters,
  onFiltersChange,
  className
}: SearchFiltersProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const handleTypeChange = useCallback(
    (type: SessionType | undefined) => {
      onFiltersChange({ ...filters, type });
    },
    [filters, onFiltersChange]
  );

  const handleDateRangeChange = useCallback(
    (dateRange: { start: number; end: number } | undefined) => {
      onFiltersChange({ ...filters, dateRange });
    },
    [filters, onFiltersChange]
  );

  const handleClearFilters = useCallback(() => {
    onFiltersChange({});
  }, [onFiltersChange]);

  const hasActiveFilters = filters.type || filters.dateRange || (filters.tags && filters.tags.length > 0);

  return (
    <div className={cn('relative', className)}>
      {/* 过滤按钮 */}
      <button
        type="button"
        onClick={() => setIsExpanded(!isExpanded)}
        className={cn(
          'flex items-center gap-1.5 px-3 py-1.5 text-sm rounded-md transition-colors',
          'border border-border hover:bg-accent',
          hasActiveFilters && 'bg-primary/10 border-primary/30 text-primary'
        )}
      >
        <Filter className="h-3.5 w-3.5" />
        <span>筛选</span>
        {hasActiveFilters && (
          <span className="ml-1 px-1.5 py-0.5 text-xs bg-primary/20 rounded-full">
            {(filters.type ? 1 : 0) + (filters.dateRange ? 1 : 0) + (filters.tags?.length ?? 0)}
          </span>
        )}
      </button>

      {/* 过滤器面板 */}
      {isExpanded && (
        <div className="absolute top-full left-0 mt-2 w-64 bg-background border border-border rounded-lg shadow-lg z-50">
          <div className="p-3 space-y-4">
            {/* 会话类型过滤 */}
            <div>
              <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground mb-2">
                <Tag className="h-3 w-3" />
                会话类型
              </label>
              <div className="flex flex-wrap gap-1.5">
                {[
                  { value: undefined, label: '全部' },
                  { value: SessionType.PROJECT, label: '项目' },
                  { value: SessionType.GLOBAL, label: '全局' },
                  { value: SessionType.TEMPORARY, label: '临时' }
                ].map((option) => (
                  <button
                    key={option.label}
                    type="button"
                    onClick={() => handleTypeChange(option.value)}
                    className={cn(
                      'px-2 py-1 text-xs rounded-md transition-colors',
                      filters.type === option.value
                        ? 'bg-primary text-primary-foreground'
                        : 'bg-accent hover:bg-accent/80'
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>

            {/* 日期范围过滤 */}
            <div>
              <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground mb-2">
                <Calendar className="h-3 w-3" />
                日期范围
              </label>
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="date"
                  value={filters.dateRange?.start ? new Date(filters.dateRange.start).toISOString().split('T')[0] : ''}
                  onChange={(e) =>
                    handleDateRangeChange({
                      start: e.target.value ? new Date(e.target.value).getTime() : 0,
                      end: filters.dateRange?.end ?? Date.now()
                    })
                  }
                  className="px-2 py-1 text-xs border border-border rounded bg-background"
                />
                <input
                  type="date"
                  value={filters.dateRange?.end ? new Date(filters.dateRange.end).toISOString().split('T')[0] : ''}
                  onChange={(e) =>
                    handleDateRangeChange({
                      start: filters.dateRange?.start ?? 0,
                      end: e.target.value ? new Date(e.target.value).getTime() : Date.now()
                    })
                  }
                  className="px-2 py-1 text-xs border border-border rounded bg-background"
                />
              </div>
            </div>

            {/* 清除过滤器 */}
            {hasActiveFilters && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-accent rounded-md transition-colors"
              >
                <X className="h-3 w-3" />
                清除所有筛选
              </button>
            )}
          </div>
        </div>
      )}

      {/* 点击外部关闭 */}
      {isExpanded && (
        <div
          className="fixed inset-0 z-[51]"
          onClick={() => setIsExpanded(false)}
        />
      )}
    </div>
  );
});