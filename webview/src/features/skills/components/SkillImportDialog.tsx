/**
 * SkillImportDialog - Skill 导入对话框
 *
 * 提供文件导入功能，支持 JSON 格式的 Skill 数据导入。
 */

import { useState, useCallback, useRef } from 'react';
import { useSkillsStore } from '@/shared/stores/skillsStore';
import { javaBridge } from '@/lib/java-bridge';
import { Upload, FileText, AlertCircle, CheckCircle } from 'lucide-react';
import { Button } from '@/shared/components/ui/button/Button';
import { cn } from '@/shared/utils/cn';

interface SkillImportDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: (count: number) => void;
}

type ImportMode = 'json' | 'markdown';

export function SkillImportDialog({ isOpen, onClose, onSuccess }: SkillImportDialogProps) {
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<number | null>(null);
  const [importMode, setImportMode] = useState<ImportMode>('json');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { importFromMarkdown } = useSkillsStore();

  const handleImport = useCallback(
    async (file: File) => {
      setImporting(true);
      setError(null);
      setSuccess(null);

      try {
        const text = await file.text();

        if (importMode === 'json') {
          const result = await javaBridge.importSkill(text);
          if (result.success) {
            setSuccess(result.count || 1);
            onSuccess?.(result.count || 1);
          } else {
            setError(result.error || 'Import failed');
          }
        } else {
          // Markdown 模式
          const result = importFromMarkdown(text);
          if (result.failed === 0) {
            setSuccess(result.success);
            onSuccess?.(result.success);
          } else {
            setError(`导入完成但有 ${result.failed} 个失败: ${result.errors.join(', ')}`);
          }
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Import failed');
      } finally {
        setImporting(false);
      }
    },
    [importMode, importFromMarkdown, onSuccess]
  );

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        handleImport(file);
      }
    },
    [handleImport]
  );

  const handleClose = useCallback(() => {
    setError(null);
    setSuccess(null);
    setImporting(false);
    onClose();
  }, [onClose]);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      const file = e.dataTransfer.files?.[0];
      if (file) {
        handleImport(file);
      }
    },
    [handleImport]
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
  }, []);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-background border rounded-lg shadow-xl max-w-md w-full mx-4 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b">
          <div className="flex items-center gap-2">
            <Upload className="h-5 w-5" />
            <span className="font-medium">导入 Skill</span>
          </div>
          <button onClick={handleClose} className="p-1 hover:bg-accent rounded">
            <span className="sr-only">关闭</span>
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="p-4 space-y-4">
          {/* 导入模式选择 */}
          <div className="flex gap-2">
            <Button
              variant={importMode === 'json' ? 'primary' : 'outline'}
              size="sm"
              onClick={() => setImportMode('json')}
            >
              JSON
            </Button>
            <Button
              variant={importMode === 'markdown' ? 'primary' : 'outline'}
              size="sm"
              onClick={() => setImportMode('markdown')}
            >
              Markdown
            </Button>
          </div>

          {/* 文件上传区域 */}
          <div
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            className={cn(
              'border-2 border-dashed rounded-lg p-8 text-center transition-colors cursor-pointer',
              'hover:border-primary/50 hover:bg-primary/5',
              importing && 'opacity-50 pointer-events-none'
            )}
            onClick={() => fileInputRef.current?.click()}
          >
            <input
              ref={fileInputRef}
              type="file"
              accept={importMode === 'json' ? '.json' : '.md,.markdown'}
              onChange={handleFileChange}
              className="hidden"
              disabled={importing}
            />

            {importing ? (
              <div className="flex flex-col items-center gap-2">
                <div className="h-8 w-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                <p className="text-sm text-muted-foreground">正在导入...</p>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-2">
                <FileText className="h-8 w-8 text-muted-foreground" />
                <p className="text-sm font-medium">点击选择文件或拖拽到此处</p>
                <p className="text-xs text-muted-foreground">
                  {importMode === 'json' ? '支持 .json 格式' : '支持 .md, .markdown 格式'}
                </p>
              </div>
            )}
          </div>

          {/* 错误提示 */}
          {error && (
            <div className="flex items-start gap-2 p-3 rounded-lg bg-destructive/10 text-destructive">
              <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
              <p className="text-sm">{error}</p>
            </div>
          )}

          {/* 成功提示 */}
          {success !== null && (
            <div className="flex items-start gap-2 p-3 rounded-lg bg-green-500/10 text-green-600">
              <CheckCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
              <p className="text-sm">成功导入 {success} 个 Skill</p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 px-4 py-3 border-t bg-muted/50">
          <Button variant="outline" onClick={handleClose}>
            关闭
          </Button>
        </div>
      </div>
    </div>
  );
}