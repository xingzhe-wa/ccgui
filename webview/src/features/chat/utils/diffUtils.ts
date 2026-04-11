/**
 * diffUtils - Diff 计算工具
 *
 * 对应架构文档中的 Diff 计算实现
 * 使用 LCS 算法计算文本差异
 */

/**
 * Diff 行类型
 */
export enum DiffLineType {
  UNCHANGED = 'unchanged',
  ADDED = 'added',
  DELETED = 'deleted'
}

/**
 * Diff 行
 */
export interface DiffLine {
  type: DiffLineType;
  content: string;
  lineNumber?: number;
}

/**
 * Diff 结果
 */
export interface DiffResult {
  lines: DiffLine[];
  additions: number;
  deletions: number;
}

/**
 * 使用 LCS 算法计算 Diff
 * 对应架构文档中的 computeDiff 函数
 *
 * @param oldLines 旧行数组
 * @param newLines 新行数组
 * @returns Diff 结果
 */
export function computeDiff(oldLines: string[], newLines: string[]): DiffResult {
  const m = oldLines.length;
  const n = newLines.length;

  // 构建 DP 表，使用 any 类型绕过严格检查
  const dp: number[][] = [];
  for (let i = 0; i <= m; i++) {
    const row: number[] = [];
    for (let j = 0; j <= n; j++) {
      row[j] = 0;
    }
    dp[i] = row;
  }

  for (let i = 1; i <= m; i++) {
    const row = dp[i] as number[];
    const prevRow = dp[i - 1] as number[];
    for (let j = 1; j <= n; j++) {
      if (oldLines[i - 1] === newLines[j - 1]) {
        row[j] = prevRow[j - 1]! + 1;
      } else {
        row[j] = Math.max(prevRow[j]!, row[j - 1]!);
      }
    }
  }

  // 回溯生成 diff
  const lines: DiffLine[] = [];
  let i = m;
  let j = n;
  let additions = 0;
  let deletions = 0;

  while (i > 0 || j > 0) {
    const currentRow = dp[i] as number[];
    const prevRow = i > 0 ? dp[i - 1] as number[] : undefined;

    if (i > 0 && j > 0 && oldLines[i - 1] === newLines[j - 1]) {
      lines.unshift({
        type: DiffLineType.UNCHANGED,
        content: oldLines[i - 1]!
      });
      i--;
      j--;
    } else if (j > 0 && (i === 0 || (currentRow[j - 1] ?? 0) >= (prevRow?.[j] ?? 0))) {
      lines.unshift({
        type: DiffLineType.ADDED,
        content: newLines[j - 1]!
      });
      additions++;
      j--;
    } else {
      lines.unshift({
        type: DiffLineType.DELETED,
        content: oldLines[i - 1]!
      });
      deletions++;
      i--;
    }
  }

  return { lines, additions, deletions };
}

/**
 * 简化版 Diff（用于小文本）
 * 直接比较行差异
 */
export function computeSimpleDiff(oldText: string, newText: string): DiffResult {
  const oldLines = oldText.split('\n');
  const newLines = newText.split('\n');
  return computeDiff(oldLines, newLines);
}

/**
 * 格式化 Diff 结果为统一格式
 * 转换为标准 unified diff 格式
 */
export function formatUnifiedDiff(diff: DiffResult): string {
  const lines: string[] = [];

  for (const line of diff.lines) {
    switch (line.type) {
      case DiffLineType.UNCHANGED:
        lines.push(` ${line.content}`);
        break;
      case DiffLineType.ADDED:
        lines.push(`+${line.content}`);
        break;
      case DiffLineType.DELETED:
        lines.push(`-${line.content}`);
        break;
    }
  }

  return lines.join('\n');
}

/**
 * 获取 Diff 统计信息
 */
export interface DiffStats {
  totalChanges: number;
  additions: number;
  deletions: number;
  unchanged: number;
  changePercentage: number;
}

export function getDiffStats(diff: DiffResult): DiffStats {
  const totalLines = diff.lines.length;
  const changedLines = diff.additions + diff.deletions;
  const unchanged = totalLines - changedLines;
  const changePercentage = totalLines > 0 ? (changedLines / totalLines) * 100 : 0;

  return {
    totalChanges: changedLines,
    additions: diff.additions,
    deletions: diff.deletions,
    unchanged,
    changePercentage
  };
}

/**
 * 检测文件类型（用于语法高亮）
 */
export function detectFileType(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase();
  const typeMap: Record<string, string> = {
    'js': 'javascript',
    'jsx': 'javascript',
    'ts': 'typescript',
    'tsx': 'typescript',
    'py': 'python',
    'java': 'java',
    'kt': 'kotlin',
    'go': 'go',
    'rs': 'rust',
    'cpp': 'cpp',
    'c': 'c',
    'cs': 'csharp',
    'php': 'php',
    'rb': 'ruby',
    'sh': 'bash',
    'bash': 'bash',
    'zsh': 'bash',
    'sql': 'sql',
    'json': 'json',
    'yaml': 'yaml',
    'yml': 'yaml',
    'xml': 'xml',
    'html': 'html',
    'css': 'css',
    'scss': 'scss',
    'less': 'less',
    'md': 'markdown',
    'txt': 'text'
  };

  return typeMap[ext || ''] || 'text';
}
