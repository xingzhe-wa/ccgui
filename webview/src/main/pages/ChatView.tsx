/**
 * ChatView - 聊天主页面
 *
 * 单栏布局：消息列表 + 输入区域
 * 点击消息显示详情 Dialog（不再使用右侧分栏）
 */

import { memo, useCallback, useMemo, useRef, useState } from 'react';
import { shallow } from 'zustand/shallow';
import { MessageList } from '@/features/chat/components/MessageList';
import { ChatInput } from '@/features/chat/components/ChatInput';
import { QuickActionsPanel } from '@/features/chat/components/QuickActionsPanel';
import { StreamingMessage } from '@/features/streaming/components/StreamingMessage';
import { StopButton } from '@/features/streaming/components/StopButton';
import { InteractiveQuestionPanel } from '@/features/interaction/components/InteractiveQuestionPanel';
import { MessageDetail } from '@/features/chat/components/PreviewPanel/MessageDetail';
import { TaskStatusBar } from '@/main/components/TaskStatusBar';
import { useSessionStore } from '@/shared/stores/sessionStore';
import { useStreamingStore, useStreamingState } from '@/shared/stores/streamingStore';
import { useQuestionStore } from '@/shared/stores/questionStore';
import { useAppStore } from '@/shared/stores/appStore';
import { javaBridge } from '@/lib/java-bridge';
import type { ChatMessage, ContentPart, MessageReference, QuestionAnswer } from '@/shared/types';
import { MessageRole, MessageStatus } from '@/shared/types';
import type { QuestionType } from '@/shared/types/interaction';

export const ChatView = memo(function ChatView(): JSX.Element {
  // 使用 shallow 比较优化：避免 sessionState 变化时不必要的重渲染
  const currentSessionId = useSessionStore((s) => s.currentSessionId);
  const sessionState = useSessionStore(
    (s) => (s.currentSessionId ? s.sessionStates[s.currentSessionId] : undefined),
    shallow
  );
  const addMessage = useSessionStore((s) => s.addMessage);
  const currentQuestion = useQuestionStore((s) => s.currentQuestion);
  const setAnswer = useQuestionStore((s) => s.setAnswer);
  const submitAnswer = useQuestionStore((s) => s.submitAnswer);
  const skipQuestion = useQuestionStore((s) => s.skipQuestion);

  // 使用优化的选择器：只获取需要的状态字段
  const { streamingMessageId, isStreaming } = useStreamingState();
  const startStreaming = useStreamingStore((s) => s.startStreaming);

  const messages = useMemo<ChatMessage[]>(
    () => sessionState?.messages ?? [],
    [sessionState?.messages]
  );

  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null);
  const [references, setReferences] = useState<MessageReference[]>([]);
  const [quickActionsExpanded, setQuickActionsExpanded] = useState(true);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedMessage = useMemo(
    () => messages.find((m) => m.id === selectedMessageId) ?? null,
    [messages, selectedMessageId]
  );

  const handleSend = useCallback(
    (content: string, attachments?: ContentPart[], refs?: MessageReference[]) => {
      if (!currentSessionId) return;

      // 将引用格式化为文本内容
      const refsText = refs && refs.length > 0
        ? refs.map((r) => `[@${r.messageId.slice(0, 8)}]: ${r.excerpt}`).join('\n') + '\n\n'
        : '';
      const fullContent = refsText + content;

      const userMessage: ChatMessage = {
        id: `msg-${Date.now()}`,
        role: MessageRole.USER,
        content: fullContent,
        timestamp: Date.now(),
        attachments,
        references: refs,
        status: MessageStatus.SENT
      };
      addMessage(userMessage);

      const aiMessageId = `msg-${Date.now()}-ai`;
      const aiMessage: ChatMessage = {
        id: aiMessageId,
        role: MessageRole.ASSISTANT,
        content: '',
        timestamp: Date.now(),
        status: MessageStatus.PENDING
      };
      addMessage(aiMessage);
      startStreaming(aiMessageId);

      // 通过 JavaBridge 通信层发送
      if (attachments && attachments.length > 0) {
        javaBridge.sendMultimodalMessage({
          sessionId: currentSessionId,
          content: fullContent,
          attachments,
          messageId: aiMessageId
        });
      } else {
        javaBridge.sendMessage(
          JSON.stringify({ sessionId: currentSessionId, content: fullContent, messageId: aiMessageId })
        );
      }

      // 首次发送消息后，将会话加入历史列表
      const currentSession = useAppStore.getState().sessions.find((s) => s.id === currentSessionId);
      if (currentSession && !currentSession.isInitialized) {
        useAppStore.getState().markSessionInitialized(currentSessionId);
      }

      // 发送后清空引用
      setReferences([]);
    },
    [currentSessionId, addMessage, startStreaming]
  );

  const handleCancel = useCallback(() => {
    useStreamingStore.getState().cancelStreaming();
  }, []);

  const handleReply = useCallback(
    (messageId: string) => {
      const message = messages.find((m) => m.id === messageId);
      if (message) {
        handleSend(`> ${message.content.slice(0, 100)}\n\n`);
      }
    },
    [messages, handleSend]
  );

  const handleCopy = useCallback((_messageId: string, content: string) => {
    navigator.clipboard.writeText(content);
  }, []);

  const handleDelete = useCallback((messageId: string) => {
    useSessionStore.getState().removeMessage(messageId);
  }, []);

  const handleQuote = useCallback((messageId: string, excerpt: string) => {
    const message = messages.find((m) => m.id === messageId);
    if (!message) return;
    const ref: MessageReference = {
      messageId: message.id,
      excerpt,
      timestamp: message.timestamp,
      sender: message.role
    };
    setReferences((prev) => {
      if (prev.some((r) => r.messageId === messageId)) return prev;
      return [...prev, ref];
    });
  }, [messages]);

  const handleRemoveReference = useCallback((messageId: string) => {
    setReferences((prev) => prev.filter((r) => r.messageId !== messageId));
  }, []);

  const handleSelectMessage = useCallback((messageId: string) => {
    setSelectedMessageId((prev) => (prev === messageId ? null : messageId));
  }, []);

  const handleQuickAction = useCallback(
    async (action: string) => {
      // 从 IDE 编辑器获取当前选区文本
      let selectedText = '';
      let fileName = '';
      let language = '';
      try {
        const result = await javaBridge.getSelectedText();
        selectedText = result.text ?? '';
        fileName = result.fileName ?? '';
        language = result.language ?? '';
      } catch {
        // 非 IDE 环境 fallback
      }

      const codeLabel = language ? `\`\`\`${language}` : '```';
      const codeBlock = selectedText
        ? `${codeLabel}${fileName ? ` // ${fileName}` : ''}\n${selectedText}\n\`\`\``
        : '(未选中代码)';

      const actionPrompts: Record<string, string> = {
        explain: `请解释以下代码的功能、关键逻辑和潜在问题：

${codeBlock}

请提供：
1. 代码功能概述
2. 关键逻辑分析
3. 潜在问题或改进建议`,
        optimize: `请优化以下代码，提高性能和可读性：

${codeBlock}

请提供优化后的代码和优化说明。`,
        polish: `请润色以下代码，使其表达更清晰：

${codeBlock}`,
        translate: `请翻译以下代码注释为中文（如果已是中文则转为英文）：

${codeBlock}`,
        review: `请对以下代码进行质量审查：

${codeBlock}

请检查：
1. 代码规范遵循情况
2. 潜在 Bug
3. 安全风险
4. 性能问题`,
        debug: `请分析以下代码并添加合适的调试代码：

${codeBlock}`,
        test: `请为以下代码生成单元测试：

${codeBlock}`
      };
      const prompt = actionPrompts[action] || action;
      handleSend(prompt);
    },
    [handleSend]
  );

  const handleAnswer = useCallback(
    (answer: QuestionAnswer) => {
      setAnswer(answer);
      submitAnswer();
    },
    [setAnswer, submitAnswer]
  );

  return (
    <div ref={containerRef} className="flex h-full flex-col">
      {/* 消息列表 */}
      <div className="flex-1 overflow-hidden">
        <MessageList
          messages={messages}
          onReply={handleReply}
          onDelete={handleDelete}
          onCopy={handleCopy}
          onSelect={handleSelectMessage}
          onQuote={handleQuote}
          selectedMessageId={selectedMessageId}
        />
      </div>

      {/* 消息详情 Dialog - 点击消息时显示 */}
      {selectedMessage && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-background border rounded-lg shadow-xl max-w-2xl w-full max-h-[80vh] flex flex-col mx-4 overflow-hidden">
            <MessageDetail
              message={selectedMessage}
              onClose={() => setSelectedMessageId(null)}
            />
          </div>
        </div>
      )}

      {/* 任务状态栏 */}
      <TaskStatusBar />

      {/* 流式输出消息 */}
      {streamingMessageId && isStreaming && (
        <div className="border-t px-4 py-2">
          <StreamingMessage messageId={streamingMessageId} />
          <div className="mt-2 flex justify-center">
            <StopButton messageId={streamingMessageId} />
          </div>
        </div>
      )}

      {/* 交互式问题面板 */}
      {currentQuestion && (
        <div className="border-t px-4 py-2">
          <InteractiveQuestionPanel
            questionId={currentQuestion.questionId}
            question={currentQuestion.question}
            questionType={currentQuestion.questionType as QuestionType}
            options={currentQuestion.options?.map((o) => ({
              id: o.id,
              label: o.label,
              description: o.description,
              icon: o.icon
            }))}
            required={currentQuestion.required}
            timeout={currentQuestion.timeout ? Math.floor(currentQuestion.timeout / 1000) : undefined}
            onAnswer={handleAnswer}
            onSkip={skipQuestion}
          />
        </div>
      )}

      {/* 快捷操作 */}
      <div className="border-t px-4 py-3">
        <QuickActionsPanel
          onAction={handleQuickAction}
          expanded={quickActionsExpanded}
          onToggle={() => setQuickActionsExpanded(!quickActionsExpanded)}
        />
      </div>

      {/* 输入区域 */}
      <div className="border-t px-4 py-3">
        <ChatInput
          onSend={handleSend}
          onCancel={handleCancel}
          references={references}
          onRemoveReference={handleRemoveReference}
          isStreaming={isStreaming}
          disabled={!currentSessionId}
          placeholder={
            currentSessionId ? '输入消息...' : '请先创建或选择一个会话...'
          }
        />
      </div>
    </div>
  );
});
