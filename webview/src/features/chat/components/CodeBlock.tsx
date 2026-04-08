/**
 * CodeBlock - 代码块渲染组件
 */

import { memo, useState, useCallback, useEffect, useMemo } from 'react';
import { cn } from '@/shared/utils/cn';
import hljs from 'highlight.js';

interface CodeBlockProps {
  code: string;
  language?: string;
  showLineNumbers?: boolean;
  className?: string;
}

export const CodeBlock = memo<CodeBlockProps>(function CodeBlock({
  code,
  language,
  showLineNumbers = true,
  className
}) {
  const [copied, setCopied] = useState(false);

  // Cleanup timeout on unmount or when copied changes
  useEffect(() => {
    if (copied) {
      const timer = setTimeout(() => setCopied(false), 2000);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [copied]);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
    } catch (error) {
      console.error('Failed to copy:', error);
    }
  }, [code]);

  const highlightedCode = useMemo(() => {
    return language
      ? hljs.highlight(code, { language }).value
      : hljs.highlightAuto(code).value;
  }, [code, language]);

  const lines = useMemo(() => code.split('\n'), [code]);

  return (
    <div className={cn('group relative rounded-lg overflow-hidden bg-code-background', className)}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-code-background/80 border-b border-border">
        <span className="text-xs text-code-foreground/60 font-mono">{language || 'plaintext'}</span>
        <button
          type="button"
          onClick={handleCopy}
          className={cn(
            'flex items-center gap-1 px-2 py-1 rounded text-xs',
            'bg-transparent hover:bg-white/10 transition-colors',
            'text-code-foreground/60 hover:text-code-foreground'
          )}
          aria-label="Copy code"
        >
          {copied ? (
            <>
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
              <span>Copied!</span>
            </>
          ) : (
            <>
              <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
                />
              </svg>
              <span>Copy</span>
            </>
          )}
        </button>
      </div>

      {/* Code Content */}
      <div className="overflow-x-auto">
        <pre className="p-4 m-0">
          <code className={cn('font-mono text-sm leading-relaxed', !showLineNumbers && 'inline')}>
            {showLineNumbers ? (
              <table className="w-full border-collapse">
                <tbody>
                  {lines.map((_line, index) => (
                    <tr key={index} className="hover:bg-white/5">
                      <td className="pr-4 text-right text-code-foreground/40 select-none w-10">
                        {index + 1}
                      </td>
                      <td
                        className="text-code-foreground whitespace-pre"
                        dangerouslySetInnerHTML={{
                          __html: highlightedCode.split('\n')[index] || ''
                        }}
                      />
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <span dangerouslySetInnerHTML={{ __html: highlightedCode }} />
            )}
          </code>
        </pre>
      </div>
    </div>
  );
});

export type { CodeBlockProps };
