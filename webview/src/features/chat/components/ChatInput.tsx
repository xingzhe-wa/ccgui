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
import { useSessionStore } from '@/shared/stores/sessionStore';
import { javaBridge } from '@/lib/java-bridge';
import type { ContentPart } from '@/shared/types';

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
  onSend: (content: string, attachments?: ContentPart[]) => void;
  onCancel?: () => void;
  placeholder?: string;
  disabled?: boolean;
  isStreaming?: boolean;
  className?: string;
}

export const ChatInput = memo<ChatInputProps>(function ChatInput({
  onSend,
  onCancel,
  placeholder = 'Type a message...',
  disabled = false,
  isStreaming = false,
  className
}) {
  const [text, setText] = useState('');
  const [attachments, setAttachments] = useState<File[]>([]);
  const [showAttachMenu, setShowAttachMenu] = useState(false);
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

    onSend(text.trim(), contentParts.length > 0 ? contentParts : undefined);
    setText('');
    setAttachments([]);
  }, [text, attachments, onSend]);

  const handleFilesSelected = useCallback((files: File[]) => {
    setAttachments((prev) => [...prev, ...files].slice(0, 5));
    setShowAttachMenu(false);
  }, []);

  const handleRemoveAttachment = useCallback((index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const handleOptimize = useCallback(async () => {
    if (!text.trim() || isStreaming) return;
    try {
      const result = await javaBridge.optimizePrompt(text);
      if (result?.optimizedPrompt) {
        setText(result.optimizedPrompt);
      }
    } catch (error) {
      console.error('Failed to optimize prompt:', error);
    }
  }, [text, isStreaming]);

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
          disabled={disabled}
        />
      </div>

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
      <div className="flex items-end gap-2 px-4 pb-4">
        <div className="flex-1 min-w-0">
          <AutoResizeTextarea
            ref={textareaRef}
            value={text}
            onChange={setText}
            onSubmit={handleSend}
            placeholder={placeholder}
            disabled={disabled}
            maxRows={8}
          />
        </div>

        <SendButton
          onClick={handleSend}
          disabled={disabled || (!text.trim() && attachments.length === 0)}
          loading={isStreaming}
        />
      </div>

      {/* Hint text */}
      <div className="px-4 pb-3 text-xs text-foreground-muted">
        Press <kbd className="px-1.5 py-0.5 rounded bg-background-elevated">Enter</kbd> to send,{' '}
        <kbd className="px-1.5 py-0.5 rounded bg-background-elevated">Shift+Enter</kbd> for new line
      </div>
    </div>
  );
});

export type { ChatInputProps };
