/**
 * ChatInput - 聊天输入框组件
 */

import { memo, useState, useCallback, useRef, useEffect } from 'react';
import { cn } from '@/shared/utils/cn';
import { AutoResizeTextarea } from './AutoResizeTextarea';
import { AttachmentDropZone } from './AttachmentDropZone';
import { ImagePreview } from './ImagePreview';
import { SendButton } from './SendButton';
import { InputToolbar } from './InputToolbar';
import { SlashCommandPalette } from '@/main/components/SlashCommandPalette';
import { ConfigSelect } from '@/main/components/ConfigSelect';
import { useSessionStore } from '@/shared/stores/sessionStore';
import { javaBridge } from '@/lib/java-bridge';
import type { ContentPart, MessageReference } from '@/shared/types';

/**
 * 将文件读取为 base64 字符串
 */
function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      // 提取 base64 数据部分（去除 data:mimeType;base64, 前缀）
      const base64 = result.split(',')[1] ?? '';
      resolve(base64);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

interface ChatInputProps {
  onSend: (content: string, attachments?: ContentPart[], references?: MessageReference[]) => void;
  onCancel?: () => void;
  onRemoveReference?: (messageId: string) => void;
  references?: MessageReference[];
  placeholder?: string;
  disabled?: boolean;
  isStreaming?: boolean;
  className?: string;
}

export const ChatInput = memo<ChatInputProps>(function ChatInput({
  onSend,
  onCancel,
  onRemoveReference,
  references = [],
  placeholder = 'Type a message...',
  disabled = false,
  isStreaming = false,
  className
}) {
  const [text, setText] = useState('');
  const [attachments, setAttachments] = useState<File[]>([]);
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [showSlashPalette, setShowSlashPalette] = useState(false);
  const [optimizationResult, setOptimizationResult] = useState<{
    improvements?: string[];
    confidence?: number;
  } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const currentSessionId = useSessionStore((s) => s.currentSessionId);

  // 自动聚焦到输入框：会话切换后（会话有效时）
  useEffect(() => {
    if (currentSessionId && !disabled) {
      textareaRef.current?.focus();
    }
  }, [currentSessionId, disabled]);

  const handleSend = useCallback(async () => {
    setShowSlashPalette(false);
    if (!text.trim() && attachments.length === 0) return;

    const contentParts: ContentPart[] = [];

    // Read all files and convert to base64
    for (const file of attachments) {
      if (file.type.startsWith('image/')) {
        const base64 = await readFileAsBase64(file);
        contentParts.push({
          type: 'image',
          mimeType: file.type,
          data: base64
        });
      } else {
        const base64 = await readFileAsBase64(file);
        contentParts.push({
          type: 'file',
          name: file.name,
          content: base64,
          mimeType: file.type,
          size: file.size
        });
      }
    }

    onSend(text.trim(), contentParts.length > 0 ? contentParts : undefined, references);
    setText('');
    setAttachments([]);
    setOptimizationResult(null);
  }, [text, attachments, references, onSend]);

  const handleFilesSelected = useCallback((files: File[]) => {
    setAttachments((prev) => [...prev, ...files].slice(0, 5));
    setShowAttachMenu(false);
  }, []);

  const handleRemoveAttachment = useCallback((index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const [isOptimizing, setIsOptimizing] = useState(false);

  const handleOptimize = useCallback(async () => {
    if (!text.trim() || isStreaming || isOptimizing) return;
    try {
      setIsOptimizing(true);
      const result = await javaBridge.optimizePrompt(text);
      if (result?.optimizedPrompt) {
        setText(result.optimizedPrompt);
        setOptimizationResult({
          improvements: result.improvements,
          confidence: result.confidence
        });
        // 3秒后自动隐藏
        setTimeout(() => setOptimizationResult(null), 3000);
      }
    } catch (error) {
      console.error('Failed to optimize prompt:', error);
    } finally {
      setIsOptimizing(false);
    }
  }, [text, isStreaming, isOptimizing]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape' && isStreaming) {
        onCancel?.();
      }
    },
    [isStreaming, onCancel]
  );

  return (
    <div
      className={cn(
        'flex flex-col bg-background-secondary rounded-xl border border-border',
        className
      )}
      onKeyDown={handleKeyDown}
    >
      {/* Attachments preview */}
      {attachments.length > 0 && (
        <div className="flex items-center gap-2 px-4 pt-3 overflow-x-auto">
          {attachments.map((file, index) => (
            <ImagePreview
              key={`${file.name}-${index}`}
              file={file}
              onRemove={() => handleRemoveAttachment(index)}
            />
          ))}
        </div>
      )}

      {/* Toolbar */}
      <div className="px-2 pt-2">
        <InputToolbar
          onAttach={() => setShowAttachMenu(!showAttachMenu)}
          onOptimize={handleOptimize}
          isStreaming={isStreaming}
          isOptimizing={isOptimizing}
          disabled={disabled}
        />
      </div>

      {/* Optimization result banner */}
      {optimizationResult && (
        <div className="mx-4 mb-2 px-3 py-2 rounded-lg bg-accent/20 border border-accent/30">
          <div className="flex items-center justify-between mb-1">
            <span className="text-xs font-medium text-accent">优化完成</span>
            {optimizationResult.confidence != null && (
              <span className="text-xs text-muted-foreground">
                置信度: {Math.round((optimizationResult.confidence) * 100)}%
              </span>
            )}
          </div>
          {optimizationResult.improvements && optimizationResult.improvements.length > 0 && (
            <ul className="text-xs text-foreground-muted space-y-0.5">
              {optimizationResult.improvements.map((item, idx) => (
                <li key={idx} className="flex items-start gap-1">
                  <span className="text-accent mt-0.5">•</span>
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {/* Reference bubbles */}
      {references.length > 0 && (
        <div className="mx-4 mb-2 flex flex-wrap gap-2">
          {references.map((ref) => (
            <div
              key={ref.messageId}
              className="flex items-center gap-1.5 pl-2 pr-1.5 py-1 rounded-full bg-muted border border-border text-xs"
            >
              <span className="text-muted-foreground shrink-0">@</span>
              <span className="text-foreground truncate max-w-[150px]">{ref.excerpt}</span>
              <button
                type="button"
                onClick={() => onRemoveReference?.(ref.messageId)}
                className="shrink-0 p-0.5 rounded hover:bg-background-secondary transition-colors"
                aria-label="移除引用"
              >
                <svg className="w-3 h-3 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Attachment menu */}
      {showAttachMenu && (
        <div className="px-4 pb-2">
          <AttachmentDropZone
            onFilesSelected={handleFilesSelected}
            className="py-3"
          />
        </div>
      )}

      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/*,application/pdf,text/*"
        onChange={(e) => {
          const files = Array.from(e.target.files || []);
          if (files.length > 0) {
            handleFilesSelected(files);
          }
          e.target.value = '';
        }}
        className="hidden"
      />

      {/* Text input area */}
      <div className="relative flex items-end gap-2 px-4 pb-4">
        <div
          className="flex-1 min-w-0"
          onPaste={(e) => {
            const items = Array.from(e.clipboardData?.items ?? []);
            const files: File[] = [];
            for (const item of items) {
              if (item.type.startsWith('image/')) {
                const file = item.getAsFile();
                if (file) files.push(file);
              }
            }
            if (files.length > 0) {
              e.preventDefault();
              handleFilesSelected(files);
            }
          }}
        >
          <AutoResizeTextarea
            ref={textareaRef}
            value={text}
            onChange={(val) => {
              setText(val);
              setShowSlashPalette(val.startsWith('/'));
            }}
            onSubmit={handleSend}
            placeholder={placeholder}
            disabled={disabled}
            maxRows={8}
          />
          {/* Slash Command Palette */}
          {showSlashPalette && (
            <SlashCommandPalette
              filter={text.slice(1)}
              onSelect={(cmd) => {
                setText('');
                setShowSlashPalette(false);
                javaBridge.executeSlashCommand(`/${cmd}`).catch((err) => {
                  console.error('Failed to execute slash command:', err);
                });
              }}
              onClose={() => setShowSlashPalette(false)}
            />
          )}
        </div>

        <SendButton
          onClick={handleSend}
          disabled={disabled || (!text.trim() && attachments.length === 0)}
          loading={isStreaming}
        />
      </div>

      {/* 运行时配置栏 */}
      <div className="flex items-center gap-3 px-4 pb-3">
        <ConfigSelect />
        <div className="flex-1" />
        <span className="text-xs text-muted-foreground">
          <kbd className="px-1 py-0.5 rounded bg-background-elevated text-[10px]">Enter</kbd> 发送
        </span>
      </div>
    </div>
  );
});

export type { ChatInputProps };
