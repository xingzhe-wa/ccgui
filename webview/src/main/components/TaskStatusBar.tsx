/**
 * TaskStatusBar - 任务状态栏组件
 *
 * 显示任务进度、Subagent 状态、Diff 文件记录
 * 每 2 秒轮询一次 getTaskStatus
 */

import { memo, useEffect, useState, useRef } from 'react';
import { javaBridge } from '@/lib/java-bridge';
import { cn } from '@/shared/utils/cn';

interface TaskProgress {
  taskId: string;
  name: string;
  status: string;
  currentStep: number;
  totalSteps: number;
  progress: number;
}

interface SubagentStatus {
  agentId: string;
  agentName: string;
  taskId: string;
  startTime: number;
  status: string;
}

interface DiffRecord {
  filePath: string;
  status: 'ADDED' | 'MODIFIED' | 'DELETED';
  hunks: number;
  timestamp: number;
  diffContent?: string; // Optional: actual diff content
}

interface TaskStatusData {
  tasks: TaskProgress[];
  activeSubagents: SubagentStatus[];
  diffRecords: DiffRecord[] | Record<string, never>[];
}

interface TaskStatusBarProps {
  className?: string;
}

/** 单个 Diff 记录行（可展开） */
const DiffRecordRow = memo<{
  diff: DiffRecord;
  defaultExpanded?: boolean;
}>(({ diff, defaultExpanded = false }) => {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <div className="rounded-md border border-border/50 overflow-hidden">
      {/* 行头 - 可点击展开 */}
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-2 px-2 py-1.5 text-xs hover:bg-muted/30 transition-colors text-left"
      >
        <svg
          className={cn('w-3 h-3 shrink-0 transition-transform', expanded && 'rotate-90')}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        <span className={cn(
          'px-1 rounded text-[10px] font-medium shrink-0',
          diff.status === 'ADDED' && 'bg-green-500/20 text-green-400',
          diff.status === 'MODIFIED' && 'bg-yellow-500/20 text-yellow-400',
          diff.status === 'DELETED' && 'bg-red-500/20 text-red-400'
        )}>
          {diff.status}
        </span>
        <span className="truncate flex-1 text-foreground">{diff.filePath}</span>
        {diff.hunks > 0 && (
          <span className="text-muted-foreground shrink-0">{diff.hunks} hunks</span>
        )}
      </button>

      {/* 展开内容 */}
      {expanded && (
        <div className="px-4 py-2 bg-code-background/50 border-t border-border/50">
          {diff.diffContent ? (
            <pre className="text-xs font-mono text-code-foreground overflow-x-auto whitespace-pre-wrap">
              {diff.diffContent}
            </pre>
          ) : (
            <div className="text-xs text-muted-foreground italic">
              <p>文件路径: <span className="text-foreground font-mono">{diff.filePath}</span></p>
              <p>状态: {diff.status}</p>
              {diff.hunks > 0 && <p>块数: {diff.hunks}</p>}
              <p className="mt-1 text-primary/70">后端暂未提供 diff 详情内容</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
});

export const TaskStatusBar = memo<TaskStatusBarProps>(function TaskStatusBar({
  className
}) {
  const [data, setData] = useState<TaskStatusData>({ tasks: [], activeSubagents: [], diffRecords: [] });
  const [expanded, setExpanded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const result = await javaBridge.getTaskStatus();
        // result 可能为 null，使用空数据作为默认值
        const DEFAULT_DATA: TaskStatusData = { tasks: [], activeSubagents: [], diffRecords: [] };
        setData(result ?? DEFAULT_DATA);
        setLoadError(null);
      } catch (e) {
        setLoadError('获取任务状态失败');
      }
    };

    fetchStatus();
    intervalRef.current = setInterval(fetchStatus, 2000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  const totalTasks = data.tasks.length;
  const completedTasks = data.tasks.filter(t => t.status === 'COMPLETED').length;
  const activeSubagentCount = data.activeSubagents.length;
  const diffRecordCount = data.diffRecords.length;

  const hasContent = totalTasks > 0 || activeSubagentCount > 0 || diffRecordCount > 0;

  if (!hasContent && !loadError) {
    return <div className={cn('h-0', className)} />;
  }

  if (loadError) {
    return (
      <div className={cn('px-4 py-1.5 text-xs text-destructive', className)}>
        {loadError}
      </div>
    );
  }

  return (
    <div className={cn('border-t border-border bg-muted/20', className)}>
      {/* 折叠头部 */}
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center justify-between px-4 py-1.5 text-xs hover:bg-muted/40 transition-colors"
      >
        <div className="flex items-center gap-3">
          {totalTasks > 0 && (
            <span className="flex items-center gap-1">
              <span className="text-muted-foreground">任务:</span>
              <span className="text-foreground">{completedTasks}/{totalTasks}</span>
            </span>
          )}
          {activeSubagentCount > 0 && (
            <span className="flex items-center gap-1">
              <span className="text-muted-foreground">Subagent:</span>
              <span className="text-blue-400">{activeSubagentCount} 运行中</span>
            </span>
          )}
          {diffRecordCount > 0 && (
            <span className="flex items-center gap-1">
              <span className="text-muted-foreground">Diff:</span>
              <span className="text-green-400">{diffRecordCount} 文件</span>
            </span>
          )}
        </div>
        <svg
          className={cn('w-3 h-3 transition-transform', expanded && 'rotate-180')}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* 展开内容 */}
      {expanded && (
        <div className="px-4 pb-3 space-y-3">
          {/* 任务进度 */}
          {data.tasks.length > 0 && (
            <div>
              <h4 className="text-[10px] font-medium text-muted-foreground mb-1">任务进度</h4>
              <div className="space-y-1">
                {data.tasks.map(task => (
                  <div key={task.taskId} className="flex items-center gap-2 text-xs">
                    <div className="w-20 truncate">{task.name}</div>
                    <div className="flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
                      <div
                        className={cn(
                          'h-full transition-all',
                          task.status === 'COMPLETED' && 'bg-green-500',
                          task.status === 'FAILED' && 'bg-red-500',
                          task.status === 'IN_PROGRESS' && 'bg-blue-500'
                        )}
                        style={{ width: `${task.progress}%` }}
                      />
                    </div>
                    <span className="text-muted-foreground w-12 text-right">
                      {task.currentStep}/{task.totalSteps}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Subagent 状态 */}
          {data.activeSubagents.length > 0 && (
            <div>
              <h4 className="text-[10px] font-medium text-muted-foreground mb-1">Subagent 状态</h4>
              <div className="space-y-1">
                {data.activeSubagents.map(sub => (
                  <div key={sub.agentId} className="flex items-center gap-2 text-xs">
                    <span className={cn(
                      'w-2 h-2 rounded-full',
                      sub.status === 'RUNNING' ? 'bg-blue-500 animate-pulse' : 'bg-muted'
                    )} />
                    <span className="font-medium">{sub.agentName}</span>
                    <span className="text-muted-foreground truncate flex-1">{sub.taskId}</span>
                    <span className="text-muted-foreground">{sub.status}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Diff 记录 */}
          <div>
            <h4 className="text-[10px] font-medium text-muted-foreground mb-1">Diff 文件修改</h4>
            {data.diffRecords.length > 0 ? (
              <div className="space-y-1">
                {data.diffRecords.map((diff, i) => (
                  <DiffRecordRow key={i} diff={diff as DiffRecord} />
                ))}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground/60 italic py-1">
                暂无文件变更记录
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
});
