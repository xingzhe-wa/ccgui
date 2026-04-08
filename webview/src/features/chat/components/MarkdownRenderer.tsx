/**
 * MarkdownRenderer - Markdown 内容渲染组件
 */

import { memo, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import { cn } from '@/shared/utils/cn';
import { CodeBlock } from './CodeBlock';

interface MarkdownRendererProps {
  content: string;
  className?: string;
  skipCodeBlock?: boolean;
}

export const MarkdownRenderer = memo<MarkdownRendererProps>(function MarkdownRenderer({
  content,
  className,
  skipCodeBlock = false
}) {
  const components = useMemo(
    () => ({
      code({ node: _node, className, children, ...props }: any) {
        const inline = !className;
        const match = /language-(\w+)/.exec(className || '');

        if (!inline && match && !skipCodeBlock) {
          return (
            <CodeBlock
              code={String(children).replace(/\n$/, '')}
              language={match[1]}
            />
          );
        }

        return (
          <code
            className={cn(
              'px-1.5 py-0.5 rounded text-sm font-mono',
              'bg-code-background text-code-foreground',
              className
            )}
            {...props}
          >
            {children}
          </code>
        );
      },
      pre({ children }: any) {
        if (skipCodeBlock) {
          return <pre className="p-4 rounded-lg bg-code-background overflow-x-auto">{children}</pre>;
        }
        return <>{children}</>;
      },
      a({ href, children, ...props }: any) {
        return (
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary underline hover:opacity-80"
            {...props}
          >
            {children}
          </a>
        );
      },
      table({ children }: any) {
        return (
          <div className="overflow-x-auto my-4">
            <table className="min-w-full divide-y divide-border">{children}</table>
          </div>
        );
      },
      th({ children }: any) {
        return (
          <th className="px-4 py-2 text-left text-sm font-medium text-foreground bg-background-secondary">
            {children}
          </th>
        );
      },
      td({ children }: any) {
        return (
          <td className="px-4 py-2 text-sm text-foreground border-t border-border">{children}</td>
        );
      },
      blockquote({ children }: any) {
        return (
          <blockquote className="border-l-4 border-primary pl-4 my-4 text-foreground-muted italic">
            {children}
          </blockquote>
        );
      },
      ul({ children }: any) {
        return <ul className="list-disc list-inside my-2 space-y-1 text-foreground">{children}</ul>;
      },
      ol({ children }: any) {
        return <ol className="list-decimal list-inside my-2 space-y-1 text-foreground">{children}</ol>;
      },
      li({ children }: any) {
        return <li className="text-foreground">{children}</li>;
      },
      p({ children }: any) {
        return <p className="my-2 text-foreground leading-relaxed">{children}</p>;
      },
      h1({ children }: any) {
        return <h1 className="text-2xl font-bold text-foreground mt-6 mb-4">{children}</h1>;
      },
      h2({ children }: any) {
        return <h2 className="text-xl font-bold text-foreground mt-5 mb-3">{children}</h2>;
      },
      h3({ children }: any) {
        return <h3 className="text-lg font-semibold text-foreground mt-4 mb-2">{children}</h3>;
      },
      h4({ children }: any) {
        return <h4 className="text-base font-semibold text-foreground mt-3 mb-2">{children}</h4>;
      },
      hr() {
        return <hr className="my-6 border-border" />;
      },
      img({ src, alt }: any) {
        return (
          <img
            src={src}
            alt={alt}
            className="max-w-full h-auto rounded-lg my-4"
            loading="lazy"
          />
        );
      }
    }),
    [skipCodeBlock]
  );

  return (
    <div className={cn('markdown-content', className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={components}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
});

export type { MarkdownRendererProps };
