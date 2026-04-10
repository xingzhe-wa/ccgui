/**
 * Claude Agent SDK Service
 *
 * This is a MOCK implementation that demonstrates the architecture.
 * The actual @anthropic-ai/claude-agent-sdk integration will be implemented
 * once the SDK API is confirmed.
 *
 * Architecture:
 * - Session management (start, send, cancel, end)
 * - Message streaming with SSE
 * - OAuth token handling
 */

import { Attachment, SseEvent } from '../types';

export interface AgentConfig {
  apiKey?: string;
  baseUrl?: string;
}

export interface SessionConfig {
  model?: string;
  systemPrompt?: string;
  mcpServers?: string[];
}

export interface MessageResult {
  usage: {
    inputTokens: number;
    outputTokens: number;
    totalTokens: number;
  };
}

/**
 * Callback type for streaming chunks
 */
export type StreamCallback = (event: SseEvent) => void;

/**
 * Generate a simple session ID
 */
function generateSessionId(): string {
  return `sess_${Date.now().toString(36)}_${Math.random().toString(36).substring(2, 8)}`;
}

/**
 * Simulate streaming response for demo purposes
 */
async function simulateStreamingResponse(
  content: string,
  onChunk: StreamCallback,
  delayMs: number = 50
): Promise<MessageResult> {
  // Estimate tokens (rough: ~4 chars per token)
  const inputTokens = Math.ceil(content.length / 4);
  const words = content.split(' ');
  let outputTokens = 0;

  for (const word of words) {
    await new Promise((resolve) => setTimeout(resolve, delayMs));
    onChunk({
      type: 'content_block_delta',
      index: 0,
      delta: { type: 'text_delta', text: word + ' ' },
    });
    outputTokens++;
  }

  return {
    usage: {
      inputTokens,
      outputTokens,
      totalTokens: inputTokens + outputTokens,
    },
  };
}

/**
 * Agent SDK Service (Mock Implementation)
 *
 * This class demonstrates the interface that will be used to communicate
 * with the Claude Agent SDK. Replace the mock implementations with actual
 * SDK calls when the SDK API is confirmed.
 */
export class AgentSdkService {
  private apiKey: string | null = null;
  private currentSession: {
    id: string;
    status: 'ready' | 'streaming' | 'waiting' | 'ended';
    config: SessionConfig;
  } | null = null;

  constructor() {
    console.log('[AgentSdkService] Initialized (mock mode)');
  }

  /**
   * Initialize the SDK with API key
   * In real implementation: Create Anthropic client with API key
   */
  async initialize(config: AgentConfig): Promise<void> {
    if (!config.apiKey) {
      throw new Error('API key is required');
    }

    this.apiKey = config.apiKey;
    console.log('[AgentSdkService] SDK initialized with API key');
  }

  /**
   * Check if SDK is initialized
   */
  isInitialized(): boolean {
    return this.apiKey !== null;
  }

  /**
   * Get current API key
   */
  getApiKey(): string | null {
    return this.apiKey;
  }

  /**
   * Set API key (for OAuth token updates)
   */
  setApiKey(apiKey: string): void {
    this.apiKey = apiKey;
    console.log('[AgentSdkService] API key updated');
  }

  /**
   * Create a new session
   * In real implementation: Call SDK's session creation API
   */
  async startSession(config: SessionConfig = {}): Promise<{
    sessionId: string;
    status: string;
    supportedTools: string[];
  }> {
    if (!this.apiKey) {
      throw new Error('SDK not initialized. Call initialize() first.');
    }

    const sessionId = generateSessionId();

    this.currentSession = {
      id: sessionId,
      status: 'ready',
      config,
    };

    console.log(`[AgentSdkService] Session started: ${sessionId}`);

    // Return supported tools (placeholder)
    return {
      sessionId,
      status: 'ready',
      supportedTools: ['Read', 'Write', 'Bash'],
    };
  }

  /**
   * Send a message with streaming response
   * In real implementation: Call SDK's message sending API with streaming
   */
  async sendMessage(
    content: string,
    attachments: Attachment[] = [],
    onChunk: StreamCallback
  ): Promise<MessageResult> {
    if (!this.currentSession) {
      throw new Error('No active session. Call startSession() first.');
    }

    if (this.currentSession.status === 'streaming') {
      throw new Error('Another request is already in progress');
    }

    this.currentSession.status = 'streaming';

    try {
      // Build enhanced prompt with context
      let prompt = content;
      if (this.currentSession.config.systemPrompt) {
        prompt = `${this.currentSession.config.systemPrompt}\n\nUser: ${content}`;
      }

      if (attachments.length > 0) {
        const attachmentText = attachments
          .map((att) => {
            if (att.type === 'text') {
              return `[File: ${att.name || 'attachment'}]\n${att.content}`;
            } else if (att.type === 'image') {
              return `[Image: ${att.name || 'attachment'}]`;
            }
            return `[Attachment: ${att.name || 'file'}]`;
          })
          .join('\n\n');
        prompt += `\n\n${attachmentText}`;
      }

      // Send content_block_start
      onChunk({
        type: 'content_block_start',
        index: 0,
        blockType: 'text',
      });

      // For demo: echo back a modified version of the input
      // In real implementation: Call SDK and stream response
      const mockResponse = `I received your message: "${content}". This is a mock response from the Claude Agent SDK service. In the actual implementation, this would be replaced with a real response from the Claude Agent SDK.`;

      const result = await simulateStreamingResponse(mockResponse, onChunk);

      // Send content_block_stop
      onChunk({
        type: 'content_block_stop',
        index: 0,
      });

      this.currentSession.status = 'ready';

      return result;
    } catch (error: any) {
      this.currentSession.status = 'ready';
      console.error('[AgentSdkService] Error sending message:', error);

      onChunk({
        type: 'error',
        message: error.message || 'Unknown error occurred',
      });

      throw error;
    }
  }

  /**
   * Send a message with NDJSON streaming (for daemon.ts)
   * Outputs tagged lines directly to stdout via callback
   */
  async sendMessageStream(
    content: string,
    sessionId?: string,
    cwd?: string,
    model?: string,
    onLine?: (line: string) => void
  ): Promise<MessageResult> {
    // Ensure we have a session
    if (!this.currentSession) {
      const result = await this.startSession({ model: model || 'claude-sonnet-4-20250514' });
      sessionId = result.sessionId;
    }

    if (this.currentSession!.status === 'streaming') {
      throw new Error('Another request is already in progress');
    }

    this.currentSession!.status = 'streaming';

    try {
      // Build mock response
      const mockResponse = `I received your message: "${content}". This is a mock response from the Claude Agent SDK service.`;

      // Simulate streaming by sending content_delta lines
      const words = mockResponse.split(' ');
      for (const word of words) {
        onLine?.(`[CONTENT_DELTA] "${word} "`);
        // Small delay to simulate streaming
        await new Promise((resolve) => setTimeout(resolve, 30));
      }

      // Send usage
      const usage = {
        inputTokens: Math.ceil(content.length / 4),
        outputTokens: mockResponse.length / 4,
      };

      onLine?.(`[SESSION_ID] ${this.currentSession!.id}`);
      onLine?.(`[USAGE] {"inputTokens":${usage.inputTokens},"outputTokens":${usage.outputTokens}}`);

      this.currentSession!.status = 'ready';

      return {
        usage: {
          inputTokens: usage.inputTokens,
          outputTokens: usage.outputTokens,
          totalTokens: usage.inputTokens + usage.outputTokens,
        },
      };
    } catch (error: any) {
      this.currentSession!.status = 'ready';
      console.error('[AgentSdkService] Error sending message:', error);
      onLine?.(`[SEND_ERROR] {"message":"${error.message}"}`);
      throw error;
    }
  }

  /**
   * Send a message with SSE streaming wrapper
   */
  async sendMessageStreamWithCallback(
    content: string,
    attachments: Attachment[] = [],
    onEvent: StreamCallback
  ): Promise<MessageResult> {
    // Send message_start event
    onEvent({
      type: 'message_start',
      messageId: `msg_${Date.now().toString(36)}`,
    });

    try {
      const result = await this.sendMessage(content, attachments, (event) => {
        // Route events appropriately
        if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
          onEvent(event);
        } else {
          onEvent(event);
        }
      });

      // Send message_stop event
      onEvent({
        type: 'message_stop',
      });

      // Send usage event
      onEvent({
        type: 'usage',
        inputTokens: result.usage.inputTokens,
        outputTokens: result.usage.outputTokens,
        totalTokens: result.usage.totalTokens,
      });

      return result;
    } catch (error: any) {
      onEvent({
        type: 'error',
        message: error.message || 'Unknown error',
      });
      throw error;
    }
  }

  /**
   * Cancel the current request
   */
  async cancelCurrentRequest(): Promise<void> {
    if (this.currentSession) {
      this.currentSession.status = 'ready';
      console.log('[AgentSdkService] Request cancelled');
    }
  }

  /**
   * End the current session
   */
  async endSession(): Promise<void> {
    if (this.currentSession) {
      console.log(`[AgentSdkService] Ending session: ${this.currentSession.id}`);
      this.currentSession.status = 'ended';
      this.currentSession = null;
    }
  }

  /**
   * Get current session status
   */
  getSessionStatus(): { sessionId: string; status: string } | null {
    if (!this.currentSession) {
      return null;
    }
    return {
      sessionId: this.currentSession.id,
      status: this.currentSession.status,
    };
  }

  /**
   * Health check
   */
  healthCheck(): boolean {
    return this.apiKey !== null;
  }
}
