/**
 * LazyImage - 懒加载图片组件
 *
 * 使用 IntersectionObserver 检测可见性。
 * 进入可视区域范围内时开始加载。
 * 加载完成前显示占位符。
 *
 * @module LazyImage
 */

import { memo, useState, useEffect, useRef } from 'react';
import { cn } from '@/shared/utils/cn';

export interface LazyImageProps {
  /** 图片URL */
  src: string;
  /** 替代文本 */
  alt: string;
  /** 额外的类名 */
  className?: string;
  /** 容器样式 */
  containerClassName?: string;
  /** rootMargin - 提前加载距离 */
  rootMargin?: string;
  /** 占位符背景色 */
  placeholderColor?: string;
  /** 加载失败时的占位内容 */
  fallback?: React.ReactNode;
  /** 图片加载完成回调 */
  onLoad?: () => void;
  /** 图片加载失败回调 */
  onError?: () => void;
}

/**
 * 懒加载图片组件
 */
export const LazyImage = memo<LazyImageProps>(function LazyImage({
  src,
  alt,
  className,
  containerClassName,
  rootMargin = '100px',
  placeholderColor = 'bg-muted',
  fallback,
  onLoad,
  onError
}: LazyImageProps) {
  const [imageSrc, setImageSrc] = useState<string | undefined>();
  const [isLoaded, setIsLoaded] = useState(false);
  const [hasError, setHasError] = useState(false);
  const imgRef = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const element = imgRef.current;
    if (!element) return;

    // 检查浏览器是否支持 IntersectionObserver
    if (!('IntersectionObserver' in window)) {
      // 不支持则直接加载
      setImageSrc(src);
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry && entry.isIntersecting && !imageSrc) {
          setImageSrc(src);
          observer.disconnect();
        }
      },
      { rootMargin }
    );

    observer.observe(element);

    return () => {
      observer.disconnect();
    };
  }, [src, rootMargin, imageSrc]);

  const handleLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    // 验证图片真正加载成功（检查 naturalWidth）
    // 某些情况下 onLoad 会在 404 等错误后触发，但 naturalWidth 为 0
    const img = e.currentTarget;
    if (img.naturalWidth === 0) {
      // 图片实际未加载成功，当作错误处理
      setHasError(true);
      onError?.();
      return;
    }
    setIsLoaded(true);
    onLoad?.();
  };

  const handleError = () => {
    setHasError(true);
    onError?.();
  };

  return (
    <div ref={imgRef} className={cn('relative overflow-hidden', containerClassName)}>
      {/* 加载中占位符 */}
      {!isLoaded && !hasError && (
        <div
          className={cn(
            'absolute inset-0 animate-pulse rounded',
            placeholderColor
          )}
          aria-hidden="true"
        />
      )}

      {/* 加载失败显示 */}
      {hasError && (
        <div className="flex items-center justify-center p-4 bg-muted rounded text-muted-foreground text-sm">
          {fallback || <span>图片加载失败</span>}
        </div>
      )}

      {/* 图片 */}
      {imageSrc && (
        <img
          src={imageSrc}
          alt={alt}
          loading="lazy"
          onLoad={handleLoad}
          onError={handleError}
          className={cn(
            'max-w-full h-auto rounded transition-opacity duration-200',
            isLoaded ? 'opacity-100' : 'opacity-0',
            className
          )}
        />
      )}
    </div>
  );
});

LazyImage.displayName = 'LazyImage';
