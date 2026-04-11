/**
 * useImageCompression - 图片压缩 Hook
 *
 * 提供图片压缩功能：
 * - 超过指定阈值（默认 500KB）的图片自动压缩
 * - 支持 JPEG、PNG、WebP 格式
 * - 压缩过程中提供进度回调
 */

import { useCallback, useState } from 'react';

export interface CompressionOptions {
  /** 压缩阈值（字节），默认 500KB */
  maxSize?: number;
  /** 输出格式，默认 'image/jpeg' */
  outputFormat?: 'image/jpeg' | 'image/png' | 'image/webp';
  /** 输出质量（0-1），默认 0.8 */
  quality?: number;
  /** 最大宽度，默认 1920 */
  maxWidth?: number;
  /** 最大高度，默认 1920 */
  maxHeight?: number;
}

export interface CompressionProgress {
  fileId: string;
  progress: number; // 0-100
  status: 'pending' | 'compressing' | 'completed' | 'error';
  originalSize: number;
  compressedSize?: number;
  error?: string;
}

export interface CompressedFile {
  file: File;
  originalSize: number;
  compressedSize: number;
  wasCompressed: boolean;
  dataUrl: string;
}

export interface UseImageCompressionReturn {
  /** 压缩进度映射 */
  progressMap: Record<string, CompressionProgress>;
  /** 是否正在压缩 */
  isCompressing: boolean;
  /** 压缩文件 */
  compress: (files: File[]) => Promise<CompressedFile[]>;
  /** 压缩单个文件 */
  compressFile: (file: File) => Promise<CompressedFile>;
  /** 清除进度 */
  clearProgress: () => void;
}

/**
 * 图片压缩 Hook
 *
 * @param options - 压缩配置选项
 * @returns 压缩状态和操作函数
 */
export const useImageCompression = (
  options: CompressionOptions = {}
): UseImageCompressionReturn => {
  const {
    maxSize = 500 * 1024, // 500KB
    outputFormat = 'image/jpeg',
    quality = 0.8,
    maxWidth = 1920,
    maxHeight = 1920
  } = options;

  const [progressMap, setProgressMap] = useState<Record<string, CompressionProgress>>({});
  const [isCompressing, setIsCompressing] = useState(false);

  /**
   * 生成唯一文件 ID
   */
  const generateFileId = useCallback((file: File, index: number): string => {
    return `${file.name}-${file.size}-${index}-${Date.now()}`;
  }, []);

  /**
   * 检查文件是否需要压缩
   */
  const needsCompression = useCallback(
    (file: File): boolean => {
      return file.size > maxSize && file.type.startsWith('image/');
    },
    [maxSize]
  );

  /**
   * 压缩单个图片文件
   */
  const compressFile = useCallback(
    async (file: File): Promise<CompressedFile> => {
      const fileId = generateFileId(file, 0);

      // 初始化进度
      setProgressMap((prev) => ({
        ...prev,
        [fileId]: {
          fileId,
          progress: 0,
          status: 'pending',
          originalSize: file.size
        }
      }));

      // 如果不需要压缩，直接返回
      if (!needsCompression(file)) {
        const dataUrl = await readFileAsDataUrl(file);
        setProgressMap((prev) => ({
          ...prev,
          [fileId]: {
            fileId,
            progress: 100,
            status: 'completed',
            originalSize: file.size,
            compressedSize: file.size
          }
        }));

        return {
          file,
          originalSize: file.size,
          compressedSize: file.size,
          wasCompressed: false,
          dataUrl
        };
      }

      // 开始压缩
      setProgressMap((prev) => ({
        ...prev,
        [fileId]: {
          fileId,
          progress: 10,
          status: 'compressing',
          originalSize: file.size
        }
      }));

      try {
        const compressedBlob = await compressImage(file, outputFormat, quality, maxWidth, maxHeight, (progress) => {
          // 更新压缩进度（10-90%）
          setProgressMap((prev) => ({
            ...prev,
            [fileId]: {
              fileId,
              progress: 10 + Math.round(progress * 80),
              status: 'compressing',
              originalSize: file.size
            }
          }));
        });

        // 创建新的 File 对象
        const compressedFile = new File(
          [compressedBlob],
          file.name,
          { type: outputFormat }
        );

        const dataUrl = await readFileAsDataUrl(compressedFile);

        setProgressMap((prev) => ({
          ...prev,
          [fileId]: {
            fileId,
            progress: 100,
            status: 'completed',
            originalSize: file.size,
            compressedSize: compressedFile.size
          }
        }));

        return {
          file: compressedFile,
          originalSize: file.size,
          compressedSize: compressedFile.size,
          wasCompressed: true,
          dataUrl
        };
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : 'Compression failed';
        setProgressMap((prev) => ({
          ...prev,
          [fileId]: {
            fileId,
            progress: 0,
            status: 'error',
            originalSize: file.size,
            error: errorMessage
          }
        }));

        throw error;
      }
    },
    [generateFileId, needsCompression, outputFormat, quality, maxWidth, maxHeight]
  );

  /**
   * 批量压缩文件
   */
  const compress = useCallback(
    async (files: File[]): Promise<CompressedFile[]> => {
      setIsCompressing(true);

      try {
        const results = await Promise.all(
          files.map((file, index) => {
            const fileId = generateFileId(file, index);
            // 初始化进度
            setProgressMap((prev) => ({
              ...prev,
              [fileId]: {
                fileId,
                progress: 0,
                status: 'pending',
                originalSize: file.size
              }
            }));
            return compressFile(file);
          })
        );

        return results;
      } finally {
        setIsCompressing(false);
      }
    },
    [compressFile, generateFileId]
  );

  /**
   * 清除进度
   */
  const clearProgress = useCallback(() => {
    setProgressMap({});
  }, []);

  return {
    progressMap,
    isCompressing,
    compress,
    compressFile,
    clearProgress
  };
};

// ==================== 辅助函数 ====================

/**
 * 将 File 读取为 DataURL
 */
function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

/**
 * 压缩图片
 */
async function compressImage(
  file: File,
  outputFormat: 'image/jpeg' | 'image/png' | 'image/webp',
  quality: number,
  maxWidth: number,
  maxHeight: number,
  onProgress?: (progress: number) => void
): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    if (!ctx) {
      reject(new Error('Failed to get canvas context'));
      return;
    }

    img.onload = () => {
      onProgress?.(0.1);

      // 计算新的尺寸
      let { width, height } = img;

      if (width > maxWidth || height > maxHeight) {
        const ratio = Math.min(maxWidth / width, maxHeight / height);
        width = Math.round(width * ratio);
        height = Math.round(height * ratio);
      }

      canvas.width = width;
      canvas.height = height;

      onProgress?.(0.3);

      // 绘制压缩后的图片
      ctx.drawImage(img, 0, 0, width, height);

      onProgress?.(0.6);

      // 转换为 Blob
      canvas.toBlob(
        (blob) => {
          if (blob) {
            onProgress?.(1.0);
            resolve(blob);
          } else {
            reject(new Error('Failed to create blob from canvas'));
          }
        },
        outputFormat,
        quality
      );
    };

    img.onerror = () => {
      reject(new Error('Failed to load image'));
    };

    img.src = URL.createObjectURL(file);
  });
}

/**
 * 检查文件是否为支持的图片格式
 */
export function isSupportedImageType(mimeType: string): boolean {
  const supportedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
  return supportedTypes.includes(mimeType);
}

/**
 * 获取支持的图片类型列表
 */
export function getSupportedImageTypes(): string[] {
  return ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
}
