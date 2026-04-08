/**
 * AttachmentDropZone - 附件拖拽上传区域
 */

import { memo, useState, useCallback, DragEvent } from 'react';
import { cn } from '@/shared/utils/cn';

interface AttachmentDropZoneProps {
  onFilesSelected: (files: File[]) => void;
  accept?: string[];
  maxSize?: number; // bytes
  maxFiles?: number;
  disabled?: boolean;
  className?: string;
}

export const AttachmentDropZone = memo<AttachmentDropZoneProps>(function AttachmentDropZone({
  onFilesSelected,
  accept = ['image/*', 'application/pdf', 'text/*'],
  maxSize = 10 * 1024 * 1024, // 10MB
  maxFiles = 5,
  disabled = false,
  className
}) {
  const [isDragging, setIsDragging] = useState(false);

  const handleDragEnter = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!disabled) {
      setIsDragging(true);
    }
  }, [disabled]);

  const handleDragLeave = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  }, []);

  const handleDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
  }, []);

  const handleDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragging(false);

      if (disabled) return;

      const files = Array.from(e.dataTransfer.files);
      const validFiles = files.filter((file) => {
        // Check file type
        const isValidType = accept.some((pattern) => {
          if (pattern.endsWith('/*')) {
            return file.type.startsWith(pattern.replace('/*', '/'));
          }
          return file.type === pattern;
        });

        // Check file size
        const isValidSize = file.size <= maxSize;

        return isValidType && isValidSize;
      });

      if (validFiles.length > 0) {
        onFilesSelected(validFiles.slice(0, maxFiles));
      }
    },
    [disabled, accept, maxSize, maxFiles, onFilesSelected]
  );

  const handleFileInput = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(e.target.files || []);
      if (files.length > 0) {
        onFilesSelected(files.slice(0, maxFiles));
      }
      // Reset input
      e.target.value = '';
    },
    [maxFiles, onFilesSelected]
  );

  return (
    <div
      className={cn(
        'relative rounded-lg border-2 border-dashed p-4 transition-all',
        isDragging
          ? 'border-primary bg-primary/5'
          : 'border-border hover:border-foreground-muted',
        disabled && 'opacity-50 cursor-not-allowed',
        className
      )}
      onDragEnter={handleDragEnter}
      onDragLeave={handleDragLeave}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      <input
        type="file"
        multiple
        accept={accept.join(',')}
        onChange={handleFileInput}
        disabled={disabled}
        className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
      />

      <div className="flex flex-col items-center text-center">
        <svg
          className={cn('w-8 h-8 mb-2', isDragging ? 'text-primary' : 'text-foreground-muted')}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={1.5}
            d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
          />
        </svg>
        <p className="text-sm text-foreground-muted">
          <span className="text-primary font-medium">Click to upload</span> or drag and drop
        </p>
        <p className="text-xs text-foreground-muted mt-1">
          Images, PDFs, text files up to {maxSize / 1024 / 1024}MB
        </p>
      </div>
    </div>
  );
});

export type { AttachmentDropZoneProps };
