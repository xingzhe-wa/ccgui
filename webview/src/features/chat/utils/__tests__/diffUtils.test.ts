/**
 * diffUtils.test.ts - diffUtils 单元测试
 *
 * 测试 LCS Diff 算法和相关工具函数
 */

import { describe, it, expect } from 'vitest';
import {
  computeDiff,
  computeSimpleDiff,
  formatUnifiedDiff,
  getDiffStats,
  detectFileType,
  DiffLineType
} from '../diffUtils';

describe('diffUtils', () => {
  describe('computeDiff', () => {
    it('应该返回空数组当两个输入都为空', () => {
      const result = computeDiff([], []);
      expect(result.lines).toEqual([]);
      expect(result.additions).toBe(0);
      expect(result.deletions).toBe(0);
    });

    it('应该处理空旧文本的情况', () => {
      const result = computeDiff([], ['line1', 'line2']);
      expect(result.lines).toHaveLength(2);
      expect(result.additions).toBe(2);
      expect(result.deletions).toBe(0);
      expect(result.lines[0]!.type).toBe(DiffLineType.ADDED);
      expect(result.lines[1]!.type).toBe(DiffLineType.ADDED);
    });

    it('应该处理空新文本的情况', () => {
      const result = computeDiff(['line1', 'line2'], []);
      expect(result.lines).toHaveLength(2);
      expect(result.additions).toBe(0);
      expect(result.deletions).toBe(2);
      expect(result.lines[0]!.type).toBe(DiffLineType.DELETED);
      expect(result.lines[1]!.type).toBe(DiffLineType.DELETED);
    });

    it('应该正确识别未变更的行', () => {
      const oldLines = ['line1', 'line2', 'line3'];
      const newLines = ['line1', 'line2', 'line3'];
      const result = computeDiff(oldLines, newLines);

      expect(result.lines).toHaveLength(3);
      expect(result.additions).toBe(0);
      expect(result.deletions).toBe(0);
      result.lines.forEach(line => {
        expect(line.type).toBe(DiffLineType.UNCHANGED);
      });
    });

    it('应该正确识别新增的行', () => {
      const oldLines = ['line1', 'line2'];
      const newLines = ['line1', 'line2', 'line3'];
      const result = computeDiff(oldLines, newLines);

      expect(result.lines).toHaveLength(3);
      expect(result.additions).toBe(1);
      expect(result.deletions).toBe(0);
      expect(result.lines[2]!.type).toBe(DiffLineType.ADDED);
      expect(result.lines[2]!.content).toBe('line3');
    });

    it('应该正确识别删除的行', () => {
      const oldLines = ['line1', 'line2', 'line3'];
      const newLines = ['line1', 'line3'];
      const result = computeDiff(oldLines, newLines);

      expect(result.lines).toHaveLength(3);
      expect(result.additions).toBe(0);
      expect(result.deletions).toBe(1);
      expect(result.lines[1]!.type).toBe(DiffLineType.DELETED);
      expect(result.lines[1]!.content).toBe('line2');
    });

    it('应该正确处理同时有新增和删除的情况', () => {
      const oldLines = ['line1', 'line2', 'line3'];
      const newLines = ['line1', 'modified', 'line3'];
      const result = computeDiff(oldLines, newLines);

      expect(result.lines).toHaveLength(4);
      expect(result.additions).toBe(1);
      expect(result.deletions).toBe(1);

      const deletedLine = result.lines.find(l => l.type === DiffLineType.DELETED);
      const addedLine = result.lines.find(l => l.type === DiffLineType.ADDED);

      expect(deletedLine?.content).toBe('line2');
      expect(addedLine?.content).toBe('modified');
    });

    it('应该处理复杂的代码差异', () => {
      const oldLines = [
        'function hello() {',
        '  console.log("hello");',
        '  return true;',
        '}'
      ];
      const newLines = [
        'function hello() {',
        '  console.log("hello");',
        '  console.log("world");',
        '  return true;',
        '}'
      ];
      const result = computeDiff(oldLines, newLines);

      expect(result.lines.length).toBeGreaterThanOrEqual(5);
      expect(result.additions).toBe(1);
    });

    it('应该处理包含空行的文本', () => {
      const oldLines = ['line1', '', 'line3'];
      const newLines = ['line1', '', 'line3', ''];
      const result = computeDiff(oldLines, newLines);

      expect(result.additions).toBe(1);
    });

    it('应该处理重复行', () => {
      const oldLines = ['line', 'line', 'line'];
      const newLines = ['line', 'line', 'line', 'line'];
      const result = computeDiff(oldLines, newLines);

      expect(result.additions).toBe(1);
      expect(result.deletions).toBe(0);
    });
  });

  describe('computeSimpleDiff', () => {
    it('应该正确分割并比较两段文本', () => {
      const oldText = 'line1\nline2\nline3';
      const newText = 'line1\nmodified\nline3';

      const result = computeSimpleDiff(oldText, newText);

      expect(result.lines.length).toBe(4);
      expect(result.additions).toBe(1);
      expect(result.deletions).toBe(1);
    });

    it('应该处理空字符串', () => {
      // 注意: ''.split('\n') 返回 ['']，所以结果是包含一个空行的 diff
      const result = computeSimpleDiff('', '');

      expect(result.lines).toHaveLength(1);
      expect(result.lines[0]!.type).toBe(DiffLineType.UNCHANGED);
      expect(result.lines[0]!.content).toBe('');
      expect(result.additions).toBe(0);
      expect(result.deletions).toBe(0);
    });

    it('应该处理单行文本', () => {
      const result = computeSimpleDiff('old', 'new');

      expect(result.additions).toBe(1);
      expect(result.deletions).toBe(1);
    });
  });

  describe('formatUnifiedDiff', () => {
    it('应该格式化未变更的行为空格前缀', () => {
      const diff = {
        lines: [{ type: DiffLineType.UNCHANGED, content: 'unchanged line' }],
        additions: 0,
        deletions: 0
      };

      const result = formatUnifiedDiff(diff);
      expect(result).toBe(' unchanged line');
    });

    it('应该格式化新增行为加号前缀', () => {
      const diff = {
        lines: [{ type: DiffLineType.ADDED, content: 'added line' }],
        additions: 1,
        deletions: 0
      };

      const result = formatUnifiedDiff(diff);
      expect(result).toBe('+added line');
    });

    it('应该格式化删除行为减号前缀', () => {
      const diff = {
        lines: [{ type: DiffLineType.DELETED, content: 'deleted line' }],
        additions: 0,
        deletions: 1
      };

      const result = formatUnifiedDiff(diff);
      expect(result).toBe('-deleted line');
    });

    it('应该组合多个行', () => {
      const diff = {
        lines: [
          { type: DiffLineType.UNCHANGED, content: 'same' },
          { type: DiffLineType.DELETED, content: 'removed' },
          { type: DiffLineType.ADDED, content: 'added' },
          { type: DiffLineType.UNCHANGED, content: 'same2' }
        ],
        additions: 1,
        deletions: 1
      };

      const result = formatUnifiedDiff(diff);
      const lines = result.split('\n');

      expect(lines[0]).toBe(' same');
      expect(lines[1]).toBe('-removed');
      expect(lines[2]).toBe('+added');
      expect(lines[3]).toBe(' same2');
    });

    it('应该处理空内容行', () => {
      const diff = {
        lines: [
          { type: DiffLineType.ADDED, content: '' }
        ],
        additions: 1,
        deletions: 0
      };

      const result = formatUnifiedDiff(diff);
      expect(result).toBe('+');
    });
  });

  describe('getDiffStats', () => {
    it('应该计算正确的统计数据', () => {
      const diff = {
        lines: [
          { type: DiffLineType.UNCHANGED, content: 'same' },
          { type: DiffLineType.UNCHANGED, content: 'same2' },
          { type: DiffLineType.ADDED, content: 'added' },
          { type: DiffLineType.DELETED, content: 'deleted' }
        ],
        additions: 1,
        deletions: 1
      };

      const stats = getDiffStats(diff);

      expect(stats.totalChanges).toBe(2);
      expect(stats.additions).toBe(1);
      expect(stats.deletions).toBe(1);
      expect(stats.unchanged).toBe(2);
      expect(stats.changePercentage).toBeCloseTo(50, 1);
    });

    it('应该处理零行的情况', () => {
      const diff = {
        lines: [],
        additions: 0,
        deletions: 0
      };

      const stats = getDiffStats(diff);

      expect(stats.totalChanges).toBe(0);
      expect(stats.unchanged).toBe(0);
      expect(stats.changePercentage).toBe(0);
    });

    it('应该处理100%变更的情况', () => {
      const diff = {
        lines: [
          { type: DiffLineType.ADDED, content: 'new1' },
          { type: DiffLineType.ADDED, content: 'new2' }
        ],
        additions: 2,
        deletions: 0
      };

      const stats = getDiffStats(diff);

      expect(stats.totalChanges).toBe(2);
      expect(stats.unchanged).toBe(0);
      expect(stats.changePercentage).toBe(100);
    });
  });

  describe('detectFileType', () => {
    it('应该正确识别 JavaScript 文件', () => {
      expect(detectFileType('index.js')).toBe('javascript');
      expect(detectFileType('index.jsx')).toBe('javascript');
    });

    it('应该正确识别 TypeScript 文件', () => {
      expect(detectFileType('index.ts')).toBe('typescript');
      expect(detectFileType('index.tsx')).toBe('typescript');
    });

    it('应该正确识别 Python 文件', () => {
      expect(detectFileType('script.py')).toBe('python');
    });

    it('应该正确识别 Java 文件', () => {
      expect(detectFileType('Main.java')).toBe('java');
    });

    it('应该正确识别 Kotlin 文件', () => {
      expect(detectFileType('Main.kt')).toBe('kotlin');
    });

    it('应该正确识别 Go 文件', () => {
      expect(detectFileType('main.go')).toBe('go');
    });

    it('应该正确识别 Rust 文件', () => {
      expect(detectFileType('lib.rs')).toBe('rust');
    });

    it('应该正确识别 C/C++ 文件', () => {
      expect(detectFileType('main.cpp')).toBe('cpp');
      expect(detectFileType('main.c')).toBe('c');
    });

    it('应该正确识别 Shell 文件', () => {
      expect(detectFileType('script.sh')).toBe('bash');
      expect(detectFileType('script.bash')).toBe('bash');
      expect(detectFileType('script.zsh')).toBe('bash');
    });

    it('应该正确识别 SQL 文件', () => {
      expect(detectFileType('query.sql')).toBe('sql');
    });

    it('应该正确识别 JSON 文件', () => {
      expect(detectFileType('config.json')).toBe('json');
    });

    it('应该正确识别 YAML 文件', () => {
      expect(detectFileType('config.yaml')).toBe('yaml');
      expect(detectFileType('config.yml')).toBe('yaml');
    });

    it('应该正确识别 XML 文件', () => {
      expect(detectFileType('data.xml')).toBe('xml');
    });

    it('应该正确识别 HTML 文件', () => {
      expect(detectFileType('index.html')).toBe('html');
    });

    it('应该正确识别 CSS 文件', () => {
      expect(detectFileType('style.css')).toBe('css');
      expect(detectFileType('style.scss')).toBe('scss');
      expect(detectFileType('style.less')).toBe('less');
    });

    it('应该正确识别 Markdown 文件', () => {
      expect(detectFileType('README.md')).toBe('markdown');
    });

    it('应该对未知扩展名返回 text', () => {
      expect(detectFileType('unknown')).toBe('text');
      expect(detectFileType('file.xyz')).toBe('text');
      expect(detectFileType('noextension')).toBe('text');
    });

    it('应该忽略大小写', () => {
      expect(detectFileType('file.JS')).toBe('javascript');
      expect(detectFileType('file.PY')).toBe('python');
    });
  });
});
