/**
 * 文件解析器工具
 *
 * 提供文件类型检测、MIME类型映射、文件大小格式化等功能。
 */

import type { ContentPart } from '@/shared/types';

// ============== 类型定义 ==============

/**
 * 解析后的文件元数据
 */
export interface FileMetadata {
  /** 文件名 */
  name: string;
  /** MIME类型 */
  mimeType: string;
  /** 文件大小（字节） */
  size: number;
  /** 缩略图URL（如果是图片） */
  thumbnailUrl?: string;
  /** 是否可以预览 */
  canPreview: boolean;
  /** 文件类型分类 */
  category: 'image' | 'video' | 'audio' | 'document' | 'other';
}

/**
 * 解析结果
 */
export interface ParseResult {
  /** 文件元数据 */
  metadata: FileMetadata;
  /** 文件内容（base64） */
  content: string;
  /** ContentPart格式 */
  contentPart: ContentPart;
}

// ============== 常量 ==============

/**
 * 支持的图片类型
 */
const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp', 'image/svg+xml'];

/**
 * 支持的视频类型
 */
const VIDEO_TYPES = ['video/mp4', 'video/webm', 'video/ogg'];

/**
 * 支持的音频类型
 */
const AUDIO_TYPES = ['audio/mpeg', 'audio/ogg', 'audio/wav', 'audio/webm'];

/**
 * 支持的文档类型
 */
const DOCUMENT_TYPES = [
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain',
  'text/html',
  'text/css',
  'text/javascript',
  'application/json',
  'application/xml',
  'text/markdown'
];

/**
 * 可预览的类型
 */
const PREVIEWABLE_TYPES = [...IMAGE_TYPES, ...DOCUMENT_TYPES];

// ============== 工具函数 ==============

/**
 * 判断文件类型分类
 */
function getFileCategory(mimeType: string): FileMetadata['category'] {
  if (IMAGE_TYPES.includes(mimeType)) return 'image';
  if (VIDEO_TYPES.includes(mimeType)) return 'video';
  if (AUDIO_TYPES.includes(mimeType)) return 'audio';
  if (DOCUMENT_TYPES.includes(mimeType)) return 'document';
  return 'other';
}

/**
 * 检查文件是否可预览
 */
function isPreviewable(mimeType: string): boolean {
  return PREVIEWABLE_TYPES.includes(mimeType);
}

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

/**
 * 获取文件扩展名
 */
export function getFileExtension(filename: string): string {
  const lastDot = filename.lastIndexOf('.');
  return lastDot > 0 ? filename.slice(lastDot + 1).toLowerCase() : '';
}

/**
 * 根据扩展名推断MIME类型
 */
export function getMimeTypeFromExtension(extension: string): string {
  const mimeMap: Record<string, string> = {
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    webp: 'image/webp',
    svg: 'image/svg+xml',
    mp4: 'video/mp4',
    webm: 'video/webm',
    ogg: 'video/ogg',
    mp3: 'audio/mpeg',
    wav: 'audio/wav',
    oga: 'audio/ogg',
    pdf: 'application/pdf',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    txt: 'text/plain',
    html: 'text/html',
    css: 'text/css',
    js: 'text/javascript',
    json: 'application/json',
    xml: 'application/xml',
    md: 'text/markdown'
  };
  return mimeMap[extension.toLowerCase()] || 'application/octet-stream';
}

// ============== 主函数 ==============

/**
 * 读取文件为base64
 */
function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      const base64 = result.split(',')[1] ?? '';
      resolve(base64);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

/**
 * 创建图片缩略图
 */
function createThumbnail(file: File, maxSize = 100): Promise<string | undefined> {
  return new Promise((resolve) => {
    if (!IMAGE_TYPES.includes(file.type)) {
      resolve(undefined);
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(undefined);
          return;
        }

        const ratio = Math.min(maxSize / img.width, maxSize / img.height);
        canvas.width = img.width * ratio;
        canvas.height = img.height * ratio;

        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL('image/jpeg', 0.8));
      };
      img.onerror = () => resolve(undefined);
      img.src = e.target?.result as string;
    };
    reader.onerror = () => resolve(undefined);
    reader.readAsDataURL(file);
  });
}

/**
 * 解析文件
 */
export async function parseFile(file: File): Promise<ParseResult> {
  const base64 = await readFileAsBase64(file);
  const thumbnailUrl = await createThumbnail(file);

  const metadata: FileMetadata = {
    name: file.name,
    mimeType: file.type || getMimeTypeFromExtension(getFileExtension(file.name)),
    size: file.size,
    thumbnailUrl,
    canPreview: isPreviewable(file.type || getMimeTypeFromExtension(getFileExtension(file.name))),
    category: getFileCategory(file.type || getMimeTypeFromExtension(getFileExtension(file.name)))
  };

  let contentPart: ContentPart;

  if (IMAGE_TYPES.includes(file.type)) {
    contentPart = {
      type: 'image',
      mimeType: file.type,
      data: base64
    };
  } else {
    contentPart = {
      type: 'file',
      name: file.name,
      content: base64,
      mimeType: file.type || 'application/octet-stream',
      size: file.size
    };
  }

  return {
    metadata,
    content: base64,
    contentPart
  };
}

/**
 * 批量解析文件
 */
export async function parseFiles(files: File[]): Promise<ParseResult[]> {
  return Promise.all(files.map(parseFile));
}
