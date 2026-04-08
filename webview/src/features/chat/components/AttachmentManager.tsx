/**
 * AttachmentManager - 附件预览管理组件
 *
 * 提供附件的预览、删除、重排功能。
 */

import { memo, useCallback } from 'react';
import { cn } from '@/shared/utils/cn';
import { formatFileSize } from '@/shared/utils/file-parser';

export interface AttachmentFile {
  /** 文件ID */
  id: string;
  /** 文件名 */
  name: string;
  /** MIME类型 */
  mimeType: string;
  /** 文件大小 */
  size: number;
  /** 缩略图URL */
  thumbnailUrl?: string;
  /** base64内容 */
  content?: string;
}

export interface AttachmentManagerProps {
  /** 附件列表 */
  files: AttachmentFile[];
  /** 移除附件回调 */
  onRemove?: (id: string) => void;
  /** 重新排序回调 */
  onReorder?: (fromIndex: number, toIndex: number) => void;
  /** 预览附件回调 */
  onPreview?: (file: AttachmentFile) => void;
  /** 最大显示数量 */
  maxVisible?: number;
  className?: string;
}

/**
 * 附件预览管理组件
 *
 * 提供附件的预览、删除、重排功能。
 */
export const AttachmentManager = memo<AttachmentManagerProps>(function AttachmentManager({
  files,
  onRemove,
  onReorder: _onReorder,
  onPreview,
  maxVisible = 5,
  className
}: AttachmentManagerProps) {
  const visibleFiles = files.slice(0, maxVisible);
  const hiddenCount = files.length - maxVisible;

  const handleRemove = useCallback(
    (id: string) => {
      onRemove?.(id);
    },
    [onRemove]
  );

  const handlePreview = useCallback(
    (file: AttachmentFile) => {
      onPreview?.(file);
    },
    [onPreview]
  );

  if (files.length === 0) {
    return null;
  }

  return (
    <div className={cn('flex flex-wrap gap-2', className)}>
      {visibleFiles.map((file) => (
        <AttachmentPreview
          key={file.id}
          file={file}
          onRemove={handleRemove}
          onPreview={handlePreview}
        />
      ))}
      {hiddenCount > 0 && (
        <div className="flex items-center justify-center w-20 h-20 rounded-lg bg-muted text-muted-foreground text-sm">
          +{hiddenCount}
        </div>
      )}
    </div>
  );
});

/**
 * 单个附件预览
 */
interface AttachmentPreviewProps {
  file: AttachmentFile;
  onRemove?: (id: string) => void;
  onPreview?: (file: AttachmentFile) => void;
}

const AttachmentPreview = memo<AttachmentPreviewProps>(function AttachmentPreview({
  file,
  onRemove,
  onPreview
}: AttachmentPreviewProps) {
  const isImage = file.mimeType.startsWith('image/');

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation();
    onRemove?.(file.id);
  };

  const handleClick = () => {
    onPreview?.(file);
  };

  return (
    <div
      className="group relative flex items-center justify-center w-20 h-20 rounded-lg border border-border bg-muted overflow-hidden cursor-pointer hover:border-primary transition-colors"
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onPreview?.(file);
        }
      }}
    >
      {isImage && file.thumbnailUrl ? (
        <img
          src={file.thumbnailUrl}
          alt={file.name}
          className="w-full h-full object-cover"
        />
      ) : (
        <div className="flex flex-col items-center justify-center p-1 text-center">
          <FileIcon mimeType={file.mimeType} className="w-8 h-8 text-muted-foreground mb-1" />
          <span className="text-xs text-muted-foreground truncate max-w-full px-1">
            {file.name.length > 8 ? file.name.slice(0, 8) + '...' : file.name}
          </span>
        </div>
      )}

      {/* 移除按钮 */}
      {onRemove && (
        <button
          type="button"
          onClick={handleRemove}
          className="absolute top-1 right-1 w-5 h-5 rounded-full bg-destructive text-destructive-foreground opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"
          aria-label={`移除 ${file.name}`}
        >
          <CloseIcon className="w-3 h-3" />
        </button>
      )}

      {/* 文件大小 */}
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/50 to-transparent p-1">
        <span className="text-xs text-white/80">{formatFileSize(file.size)}</span>
      </div>
    </div>
  );
});

/**
 * 文件图标组件
 */
const FileIcon = ({ mimeType, className }: { mimeType: string; className?: string }) => {
  if (mimeType.startsWith('image/')) {
    return (
      <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
        <circle cx="8.5" cy="8.5" r="1.5" />
        <polyline points="21 15 16 10 5 21" />
      </svg>
    );
  }

  if (mimeType.startsWith('video/')) {
    return (
      <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18" />
        <line x1="7" y1="2" x2="7" y2="22" />
        <line x1="17" y1="2" x2="17" y2="22" />
        <line x1="2" y1="12" x2="22" y2="12" />
        <line x1="2" y1="7" x2="7" y2="7" />
        <line x1="2" y1="17" x2="7" y2="17" />
        <line x1="17" y1="17" x2="22" y2="17" />
        <line x1="17" y1="7" x2="22" y2="7" />
      </svg>
    );
  }

  if (mimeType.startsWith('audio/')) {
    return (
      <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M9 18V5l12-2v13" />
        <circle cx="6" cy="18" r="3" />
        <circle cx="18" cy="16" r="3" />
      </svg>
    );
  }

  if (mimeType === 'application/pdf') {
    return (
      <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
        <polyline points="14 2 14 8 20 8" />
        <line x1="16" y1="13" x2="8" y2="13" />
        <line x1="16" y1="17" x2="8" y2="17" />
        <polyline points="10 9 9 9 8 9" />
      </svg>
    );
  }

  // 默认文件图标
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
    </svg>
  );
};

/**
 * 关闭图标
 */
const CloseIcon = ({ className }: { className?: string }) => (
  <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
    <line x1="18" y1="6" x2="6" y2="18" />
    <line x1="6" y1="6" x2="18" y2="18" />
  </svg>
);
