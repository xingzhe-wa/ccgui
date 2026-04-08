/**
 * ChatView - 聊天主页面
 *
 * 组合 MessageList + ChatInput + StreamingMessage + InteractiveQuestionPanel
 * 构成完整的聊天界面。
 */

import { memo, useCallback, useMemo } from 'react';
import { MessageList } from '@/features/chat/components/MessageList';
import { ChatInput } from '@/features/chat/components/ChatInput';
import { QuickActionsPanel } from '@/features/chat/components/QuickActionsPanel';
import { StreamingMessage } from '@/features/streaming/components/StreamingMessage';
import { StopButton } from '@/features/streaming/components/StopButton';
import { InteractiveQuestionPanel } from '@/features/interaction/components/InteractiveQuestionPanel';
import { useSessionStore } from '@/shared/stores/sessionStore';
import { useStreamingStore } from '@/shared/stores/streamingStore';
import { useQuestionStore } from '@/shared/stores/questionStore';
import { javaBridge } from '@/lib/java-bridge';
import type { ChatMessage, ContentPart, QuestionAnswer } from '@/shared/types';
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

  const handleSend = useCallback(
    (content: string, attachments?: ContentPart[]) => {
      if (!currentSessionId) return;

      const userMessage: ChatMessage = {
        id: `msg-${Date.now()}`,
        role: MessageRole.USER,
        content,
        timestamp: Date.now(),
        attachments,
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
      if (attachments && attachments.length > 0) {
        javaBridge.sendMultimodalMessage({
          sessionId: currentSessionId,
          content,
          attachments
        });
      } else {
        javaBridge.sendMessage(
          JSON.stringify({ sessionId: currentSessionId, content })
        );
      }
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

  const handleQuickAction = useCallback(
    (action: string) => {
      handleSend(action);
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
    <div className="flex h-full flex-col">
      {/* 消息列表 */}
      <div className="flex-1 overflow-hidden">
        <MessageList
          messages={messages}
          onReply={handleReply}
          onDelete={handleDelete}
          onCopy={handleCopy}
        />
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

      {/* 快捷操作（无消息时显示） */}
      {messages.length === 0 && !isStreaming && (
        <div className="border-t px-4 py-3">
          <QuickActionsPanel onAction={handleQuickAction} />
        </div>
      )}

      {/* 输入区域 */}
      <div className="border-t px-4 py-3">
        <ChatInput
          onSend={handleSend}
          onCancel={handleCancel}
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
