/**
 * ChatView - 聊天主页面
 *
 * 组合 MessageList + ChatInput + StreamingMessage + InteractiveQuestionPanel
 * 构成完整的聊天界面。
 */

import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { MessageList } from '@/features/chat/components/MessageList';
import { ChatInput } from '@/features/chat/components/ChatInput';
import { QuickActionsPanel } from '@/features/chat/components/QuickActionsPanel';
import { StreamingMessage } from '@/features/streaming/components/StreamingMessage';
import { StopButton } from '@/features/streaming/components/StopButton';
import { InteractiveQuestionPanel } from '@/features/interaction/components/InteractiveQuestionPanel';
import { MessageDetail } from '@/features/chat/components/PreviewPanel/MessageDetail';
import { useSessionStore } from '@/shared/stores/sessionStore';
import { useStreamingStore } from '@/shared/stores/streamingStore';
import { useQuestionStore } from '@/shared/stores/questionStore';
import { javaBridge } from '@/lib/java-bridge';
import type { ChatMessage, ContentPart, MessageReference, QuestionAnswer } from '@/shared/types';
import { MessageRole, MessageStatus } from '@/shared/types';
import type { QuestionType } from '@/shared/types/interaction';

export const ChatView = memo(function ChatView(): JSX.Element {
  const currentSessionId = useSessionStore((s) => s.currentSessionId);
  const sessionState = useSessionStore((s) =>
    s.currentSessionId ? s.sessionStates[s.currentSessionId] : undefined
  );
  const addMessage = useSessionStore((s) => s.addMessage);
  const currentQuestion = useQuestionStore((s) => s.currentQuestion);
  const setAnswer = useQuestionStore((s) => s.setAnswer);
  const submitAnswer = useQuestionStore((s) => s.submitAnswer);
  const skipQuestion = useQuestionStore((s) => s.skipQuestion);

  const { streamingMessageId, isStreaming, startStreaming } = useStreamingStore();

  const messages = useMemo<ChatMessage[]>(
    () => sessionState?.messages ?? [],
    [sessionState?.messages]
  );

  const [selectedMessageId, setSelectedMessageId] = useState<string | null>(null);
  const [references, setReferences] = useState<MessageReference[]>([]);
  const [containerWidth, setContainerWidth] = useState<number>(window.innerWidth);
  const [quickActionsExpanded, setQuickActionsExpanded] = useState(true);
  const containerRef = useRef<HTMLDivElement>(null);

  // 响应式：监听容器宽度变化，窄屏时隐藏详情面板
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setContainerWidth(entry.contentRect.width);
      }
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

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

      // 通过 JavaBridge 通信层发送，不直接调用 window.ccBackend
      // 包含 messageId 以便 Java 在流式事件中返回该 ID
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
    (action: string) => {
      // 构建结构化的 prompt，而非直接发送 action ID
      const selectedText = ''; // TODO: 从编辑器获取选中代码
      const actionPrompts: Record<string, string> = {
        explain: `请解释以下代码的功能、关键逻辑和潜在问题：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\`

请提供：
1. 代码功能概述
2. 关键逻辑分析
3. 潜在问题或改进建议`,
        optimize: `请优化以下代码，提高性能和可读性：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\`

请提供优化后的代码和优化说明。`,
        polish: `请润色以下代码，使其表达更清晰：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\``,
        translate: `请翻译以下代码注释为中文（如果已是中文则转为英文）：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\``,
        review: `请对以下代码进行质量审查：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\`

请检查：
1. 代码规范遵循情况
2. 潜在 Bug
3. 安全风险
4. 性能问题`,
        debug: `请分析以下代码并添加合适的调试代码：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\``,
        test: `请为以下代码生成单元测试：

\`\`\`
${selectedText || "(未选中代码)"}
\`\`\``
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

  // 响应式布局：根据宽度计算布局模式
  // PRD: <800px 单列 / 800-1200px 60:40 分栏 / >1200px 50:50 分栏
  const layoutMode = containerWidth < 800 ? 'single' : containerWidth <= 1200 ? 'medium' : 'large';

  const messageListWidth = layoutMode === 'single' ? 'w-full' : layoutMode === 'medium' ? 'w-[60%]' : 'w-[50%]';
  const detailWidth = layoutMode === 'single' ? 'w-0' : layoutMode === 'medium' ? 'w-[40%]' : 'w-[50%]';

  return (
    <div ref={containerRef} className="flex h-full flex-col">
      {/* 主内容区（消息列表 + 预览面板） */}
      <div className="flex flex-1 overflow-hidden">
        {/* 消息列表 */}
        <div className={`${messageListWidth} overflow-hidden flex-shrink-0`}>
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

        {/* 消息详情面板 - 响应式断点：单列模式隐藏 / 中屏 40% / 大屏 50% */}
        {selectedMessage && layoutMode !== 'single' && (
          <div className={`${detailWidth} border-l overflow-hidden flex-shrink-0`}>
            <MessageDetail
              message={selectedMessage}
              onClose={() => setSelectedMessageId(null)}
            />
          </div>
        )}
      </div>

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

      {/* 快捷操作（始终显示在输入区域上方） */}
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
