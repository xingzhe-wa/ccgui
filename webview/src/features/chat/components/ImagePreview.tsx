/**
 * ImagePreview - 图片预览组件
 */

import { memo, useState, useCallback } from 'react';
import { cn } from '@/shared/utils/cn';

interface ImagePreviewProps {
  file: File;
  onRemove?: () => void;
  className?: string;
}

export const ImagePreview = memo<ImagePreviewProps>(function ImagePreview({ file, onRemove, className }) {
  const [isLoaded, setIsLoaded] = useState(false);
  const [error, setError] = useState(false);

  const handleRemove = useCallback(() => {
    onRemove?.();
  }, [onRemove]);

  return (
    <div className={cn('relative group', className)}>
      <div className="relative w-20 h-20 rounded-lg overflow-hidden bg-background-secondary">
        {!isLoaded && !error && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-6 h-6 border-2 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {error ? (
          <div className="absolute inset-0 flex items-center justify-center text-foreground-muted">
            <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
              />
            </svg>
          </div>
        ) : (
          <img
            src={URL.createObjectURL(file)}
            alt={file.name}
            className={cn(
              'w-full h-full object-cover transition-opacity',
              isLoaded ? 'opacity-100' : 'opacity-0'
            )}
            onLoad={() => setIsLoaded(true)}
            onError={() => setError(true)}
          />
        )}

        {/* Remove button */}
        <button
          type="button"
          onClick={handleRemove}
          className={cn(
            'absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full',
            'bg-destructive text-destructive-foreground',
            'flex items-center justify-center',
            'opacity-0 group-hover:opacity-100 transition-opacity',
            'hover:opacity-90'
          )}
          aria-label="Remove image"
        >
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* File name tooltip */}
        <div className="absolute bottom-0 left-0 right-0 px-1 py-0.5 bg-black/50 truncate">
          <span className="text-[10px] text-white/80 truncate">{file.name}</span>
        </div>
      </div>
    </div>
  );
});

export type { ImagePreviewProps };
