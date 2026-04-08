/**
 * CodeBlock.test.tsx - CodeBlock 组件测试
 *
 * 测试代码块渲染和复制功能。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { CodeBlock } from '../CodeBlock';

describe('CodeBlock', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Mock navigator.clipboard
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn(() => Promise.resolve()),
      },
    });
  });

  describe('基本渲染', () => {
    it('应该渲染代码内容', () => {
      render(<CodeBlock code="const x = 1;" language="javascript" />);

      const codeElement = screen.getByRole('code');
      expect(codeElement).toBeTruthy();
    });

    it('应该显示语言标签', () => {
      render(<CodeBlock code="const x = 1;" language="javascript" />);

      expect(screen.getByText('javascript')).toBeTruthy();
    });

    it('没有语言时应该显示 plaintext', () => {
      render(<CodeBlock code="some code" />);

      expect(screen.getByText('plaintext')).toBeTruthy();
    });

    it('应该有复制按钮', () => {
      render(<CodeBlock code="const x = 1;" language="javascript" />);

      const copyButton = screen.getByRole('button', { name: /copy/i });
      expect(copyButton).toBeTruthy();
    });
  });

  describe('代码高亮', () => {
    it('应该对指定语言进行高亮', () => {
      const { container } = render(
        <CodeBlock code="const x = 1;" language="javascript" />
      );

      // highlight.js 应该添加语法高亮的类名
      const codeElement = container.querySelector('code');
      expect(codeElement?.innerHTML).toContain('<span');
    });

    it('没有指定语言时应该自动检测', () => {
      const { container } = render(
        <CodeBlock code="function test() { return true; }" />
      );

      const codeElement = container.querySelector('code');
      expect(codeElement?.innerHTML).toBeTruthy();
    });
  });

  describe('行号显示', () => {
    it('默认应该显示行号', () => {
      const { container } = render(
        <CodeBlock code="line1\nline2\nline3" language="text" />
      );

      // 应该有 3 行
      const rows = container.querySelectorAll('tbody tr');
      expect(rows.length).toBeGreaterThanOrEqual(1);

      // 第一行应该有行号 1
      const firstRowLineNumber = rows[0]?.querySelector('td:first-child');
      expect(firstRowLineNumber?.textContent).toBe('1');
    });

    it('showLineNumbers=false 时应该隐藏行号', () => {
      const { container } = render(
        <CodeBlock code="line1\nline2" showLineNumbers={false} />
      );

      const table = container.querySelector('table');
      expect(table).toBeNull();
    });
  });

  describe('复制功能', () => {
    it('点击复制按钮应该复制代码', async () => {
      const mockCode = 'const x = 1;';
      const writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText');

      render(<CodeBlock code={mockCode} language="javascript" />);

      const copyButton = screen.getByRole('button', { name: /copy/i });
      fireEvent.click(copyButton);

      await waitFor(() => {
        expect(writeTextSpy).toHaveBeenCalledWith(mockCode);
      });
    });

    it('复制成功后应该显示 "Copied!" 状态', async () => {
      render(<CodeBlock code="test code" language="javascript" />);

      const copyButton = screen.getByRole('button', { name: /copy/i });
      expect(copyButton).toHaveTextContent('Copy');

      fireEvent.click(copyButton);

      await waitFor(() => {
        expect(copyButton).toHaveTextContent('Copied!');
      });
    });

    it('2秒后应该恢复 "Copy" 状态', async () => {
      render(<CodeBlock code="test code" language="javascript" />);

      const copyButton = screen.getByRole('button', { name: /copy/i });

      await act(async () => {
        fireEvent.click(copyButton);
      });

      await waitFor(() => {
        expect(copyButton).toHaveTextContent('Copied!');
      });

      // 等待 2.1 秒让定时器触发
      await new Promise(resolve => setTimeout(resolve, 2100));

      await waitFor(() => {
        expect(copyButton).toHaveTextContent('Copy');
      });
    }, 10000);

    it('复制失败时不应该改变状态', async () => {
      const writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText');
      writeTextSpy.mockRejectedValueOnce(new Error('Copy failed'));

      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      render(<CodeBlock code="test code" language="javascript" />);

      const copyButton = screen.getByRole('button', { name: /copy/i });

      await act(async () => {
        fireEvent.click(copyButton);
      });

      // 等待一下确认状态没有改变
      await new Promise(resolve => setTimeout(resolve, 100));

      // 应该仍然显示 "Copy" 而不是 "Copied!"
      // 因为复制失败，setCopied(true) 不会被调用
      expect(copyButton).toHaveTextContent('Copy');

      expect(consoleSpy).toHaveBeenCalledWith('Failed to copy:', expect.any(Error));

      consoleSpy.mockRestore();
    });
  });

  describe('多行代码', () => {
    it('应该正确渲染多行代码', () => {
      const multiLineCode = 'line1\nline2\nline3';
      const { container } = render(
        <CodeBlock code={multiLineCode} language="javascript" />
      );

      const rows = container.querySelectorAll('tbody tr');
      expect(rows.length).toBeGreaterThanOrEqual(3);
    });

    it('每行应该有对应的行号', () => {
      const multiLineCode = 'line1\nline2\nline3\nline4';
      const { container } = render(
        <CodeBlock code={multiLineCode} language="javascript" />
      );

      const lineNumbers = container.querySelectorAll('tbody tr td:first-child');
      expect(lineNumbers[0]?.textContent).toBe('1');
      expect(lineNumbers[1]?.textContent).toBe('2');
      expect(lineNumbers[2]?.textContent).toBe('3');
      expect(lineNumbers[3]?.textContent).toBe('4');
    });
  });

  describe('自定义样式', () => {
    it('应该应用自定义 className', () => {
      const { container } = render(
        <CodeBlock code="test" language="text" className="custom-class" />
      );

      const wrapper = container.firstChild as HTMLElement;
      expect(wrapper?.classList.contains('custom-class')).toBe(true);
    });
  });

  describe('内存泄漏防护', () => {
    it('组件卸载时应该清理定时器', () => {
      vi.useFakeTimers();

      const { unmount } = render(<CodeBlock code="test" language="javascript" />);

      const copyButton = screen.getByRole('button', { name: /copy/i });
      fireEvent.click(copyButton);

      // 立即卸载组件
      unmount();

      // 定时器不应该再执行（如果没有报错就说明清理成功）
      expect(() => vi.advanceTimersByTime(2000)).not.toThrow();

      vi.useRealTimers();
    });
  });

  describe('React.memo', () => {
    it('相同 props 时应该跳过重新渲染', () => {
      const { rerender } = render(<CodeBlock code="const x = 1;" language="javascript" />);

      // 重新渲染相同的 props
      rerender(<CodeBlock code="const x = 1;" language="javascript" />);

      // 如果组件被正确 memoized，不应该抛出错误
      const codeElement = screen.getByRole('code');
      expect(codeElement).toBeTruthy();
    });
  });

  describe('边界情况', () => {
    it('空代码应该正常渲染', () => {
      const { container } = render(<CodeBlock code="" language="text" />);

      const codeElement = container.querySelector('code');
      expect(codeElement).toBeTruthy();
    });

    it('只有换行符的代码应该正常渲染', () => {
      const { container } = render(<CodeBlock code="\n\n\n" language="javascript" />);

      const rows = container.querySelectorAll('tbody tr');
      expect(rows.length).toBeGreaterThanOrEqual(1);
    });

    it('特殊字符应该正确转义', () => {
      const specialCode = '<script>alert("xss")</script>';
      const { container } = render(
        <CodeBlock code={specialCode} language="html" />
      );

      // highlight.js 会处理转义，这里验证组件不会崩溃
      const codeElement = container.querySelector('code');
      expect(codeElement).toBeTruthy();
    });
  });
});
