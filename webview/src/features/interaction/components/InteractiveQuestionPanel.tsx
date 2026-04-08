/**
 * InteractiveQuestionPanel - 交互式问题面板
 *
 * 根据后端InteractiveRequestEngine推送的问题类型，
 * 渲染对应的交互UI（单选/多选/文本输入/确认）。
 * 用户提交后通过submitAnswer()回传给后端。
 */

import { memo, useState, useEffect, useCallback, useRef } from 'react';
import { QuestionType } from '@/shared/types/interaction';
import { Button } from '@/shared/components/ui/button';
import { SingleChoiceOptions } from './SingleChoiceOptions';
import { MultipleChoiceOptions } from './MultipleChoiceOptions';
import { ConfirmationOptions } from './ConfirmationOptions';
import { cn } from '@/shared/utils/cn';

export interface InteractiveQuestionPanelProps {
  /** 问题ID */
  questionId: string;
  /** 问题文本 */
  question: string;
  /** 问题类型 */
  questionType: QuestionType;
  /** 选项列表 */
  options?: Array<{
    id: string;
    label: string;
    description?: string;
    icon?: string;
  }>;
  /** 是否允许多选 */
  allowMultiple?: boolean;
  /** 是否必需回答 */
  required?: boolean;
  /** 占位符文本 */
  placeholder?: string;
  /** 超时时间（秒） */
  timeout?: number;
  /** 答案提交回调 */
  onAnswer: (answer: any) => void;
  /** 跳过回调 */
  onSkip?: () => void;
  className?: string;
}

/**
 * 交互式问题面板
 *
 * 根据后端InteractiveRequestEngine推送的问题类型，
 * 渲染对应的交互UI（单选/多选/文本输入/确认）。
 * 用户提交后通过submitAnswer()回传给后端。
 */
export const InteractiveQuestionPanel = memo<InteractiveQuestionPanelProps>(function InteractiveQuestionPanel({
  questionId,
  question,
  questionType,
  options = [],
  allowMultiple: _allowMultiple = false,
  required = true,
  placeholder,
  timeout,
  onAnswer,
  onSkip,
  className
}: InteractiveQuestionPanelProps) {
  const [selectedAnswer, setSelectedAnswer] = useState<any>(null);
  const [textInput, setTextInput] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 使用 ref 存储 onSkip 回调，避免超时处理被不必要地重置
  const onSkipRef = useRef(onSkip);
  onSkipRef.current = onSkip;

  // 超时处理
  useEffect(() => {
    if (!timeout || timeout <= 0) return;

    const timer = setTimeout(() => {
      setIsSubmitting(false);
      // 超时时触发跳过逻辑
      onSkipRef.current?.();
    }, timeout * 1000);

    return () => clearTimeout(timer);
  }, [timeout]);

  const handleSubmit = useCallback(async () => {
    if (required && !selectedAnswer && !textInput) return;

    setIsSubmitting(true);
    try {
      const answer = questionType === QuestionType.TEXT_INPUT ? textInput : selectedAnswer;
      await window.ccBackend?.submitAnswer(questionId, answer);
      onAnswer(answer);
    } finally {
      setIsSubmitting(false);
    }
  }, [questionId, questionType, selectedAnswer, textInput, required, onAnswer]);

  const handleSkip = useCallback(() => {
    onSkip?.();
  }, [onSkip]);

  const canSubmit = required
    ? questionType === QuestionType.TEXT_INPUT
      ? textInput.trim().length > 0
      : selectedAnswer !== null
    : true;

  return (
    <div className={cn('my-4 rounded-lg border border-primary/20 bg-primary/5 p-4', className)}>
      {/* 问题头部 */}
      <div className="mb-4 flex items-start gap-3">
        <div className="mt-0.5 h-6 w-6 text-primary flex-shrink-0">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
            <line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </div>
        <div className="flex-1">
          <p className="font-medium text-foreground">{question}</p>
          {required && <p className="mt-1 text-xs text-muted-foreground">* 必需回答</p>}
        </div>
        {timeout && timeout > 0 && <CountdownTimer seconds={timeout} />}
      </div>

      {/* 问题内容 */}
      <div className="mb-4">
        {questionType === QuestionType.SINGLE_CHOICE && (
          <SingleChoiceOptions
            questionId={questionId}
            options={options}
            selected={selectedAnswer ?? ''}
            onChange={setSelectedAnswer}
          />
        )}

        {questionType === QuestionType.MULTIPLE_CHOICE && (
          <MultipleChoiceOptions
            questionId={questionId}
            options={options}
            selected={selectedAnswer ?? []}
            onChange={setSelectedAnswer}
          />
        )}

        {questionType === QuestionType.TEXT_INPUT && (
          <div className="space-y-2">
            <input
              type="text"
              value={textInput}
              onChange={(e) => setTextInput(e.target.value)}
              placeholder={placeholder || '请输入...'}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
        )}

        {questionType === QuestionType.CONFIRMATION && (
          <ConfirmationOptions
            selected={selectedAnswer}
            onChange={setSelectedAnswer}
          />
        )}
      </div>

      {/* 操作按钮 */}
      <div className="flex justify-end gap-2">
        {onSkip && (
          <Button variant="ghost" onClick={handleSkip} disabled={isSubmitting}>
            跳过
          </Button>
        )}
        <Button
          onClick={handleSubmit}
          disabled={isSubmitting || !canSubmit}
          isLoading={isSubmitting}
        >
          确认
        </Button>
      </div>
    </div>
  );
});

/**
 * 倒计时组件
 */
const CountdownTimer = ({ seconds }: { seconds: number }) => {
  const [remaining, setRemaining] = useState(seconds);

  useEffect(() => {
    // 使用 seconds 作为依赖，当组件重新挂载时会重置
    // 不依赖 remaining 避免每秒重建 interval
    if (seconds <= 0) return;

    const interval = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [seconds]);

  const minutes = Math.floor(remaining / 60);
  const secs = remaining % 60;

  return (
    <div
      className={cn(
        'text-xs font-mono tabular-nums',
        remaining <= 10 ? 'text-destructive' : 'text-muted-foreground'
      )}
      aria-label={`剩余时间 ${minutes} 分 ${secs} 秒`}
    >
      {minutes}:{secs.toString().padStart(2, '0')}
    </div>
  );
};
