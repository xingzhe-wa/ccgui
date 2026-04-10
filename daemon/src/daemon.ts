/**
 * Claude SDK Daemon
 *
 * 长驻 Node.js 进程，通过 NDJSON 与 Java 通信
 *
 * 架构变更 (v3.2):
 * - 不再是 HTTP 服务器，而是纯管道处理器
 * - 通过 stdin 接收请求，stdout 发送响应
 * - NDJSON 格式：每行一个完整 JSON 对象
 */

import { AgentSdkService } from './services/agent-sdk';
import * as readline from 'readline';

// === 全局状态 ===
let agentSdk: AgentSdkService;
let activeRequestId: string | null = null;
let sdkReady = false;

// === stdout 拦截 ===
const _originalStdoutWrite = process.stdout.write.bind(process.stdout);
const _originalStderrWrite = process.stderr.write.bind(process.stderr);

process.stdout.write = function (
  chunk: string | Buffer,
  encoding?: BufferEncoding | ((err?: Error | null) => void),
  callback?: (err?: Error | null) => void
): boolean {
  const text = typeof chunk === 'string' ? chunk : chunk.toString('utf8');

  if (activeRequestId) {
    // 有活跃请求：打标签
    for (const line of text.split('\n')) {
      if (line.length > 0) {
        _originalStdoutWrite(JSON.stringify({ id: activeRequestId, line }) + '\n');
      }
    }
    if (typeof encoding === 'function') (encoding as (err?: Error | null) => void)();
    if (typeof callback === 'function') (callback as (err?: Error | null) => void)();
    return true;
  }

  // 无活跃请求：检查是否是 daemon 自身事件
  if (text.trim().startsWith('{')) {
    return _originalStdoutWrite(chunk, encoding as BufferEncoding, callback);
  }
  if (text.trim().length > 0) {
    for (const line of text.split('\n')) {
      if (line.length > 0) {
        _originalStdoutWrite(
          JSON.stringify({ type: 'daemon', event: 'log', message: line }) + '\n'
        );
      }
    }
  }
  if (typeof encoding === 'function') (encoding as (err?: Error | null) => void)();
  if (typeof callback === 'function') (callback as (err?: Error | null) => void)();
  return true;
};

process.stderr.write = function (
  chunk: string | Buffer,
  encoding?: BufferEncoding | ((err?: Error | null) => void),
  callback?: (err?: Error | null) => void
): boolean {
  const text = typeof chunk === 'string' ? chunk : chunk.toString('utf8');
  if (activeRequestId && text.trim().length > 0) {
    _originalStderrWrite(
      JSON.stringify({ id: activeRequestId, type: 'stderr', message: text }) + '\n'
    );
  }
  if (typeof encoding === 'function') (encoding as (err?: Error | null) => void)();
  if (typeof callback === 'function') (callback as (err?: Error | null) => void)();
  return true;
};

// === process.exit 拦截 ===
const _originalExit = process.exit;
(process as any).exit = function (code?: number): never {
  if (sdkReady) {
    if (activeRequestId) {
      _originalStdoutWrite(
        JSON.stringify({
          id: activeRequestId,
          done: true,
          success: code === 0,
        }) + '\n'
      );
    }
    throw new Error(`process.exit(${code}) intercepted`);
  }
  _originalExit(code ?? 0);
  throw new Error('unreachable');
};

// === SDK 预加载 ===
async function preloadSdks(): Promise<void> {
  agentSdk = new AgentSdkService();

  _originalStdoutWrite(
    JSON.stringify({ type: 'daemon', event: 'sdk_loaded', provider: 'claude' }) + '\n'
  );
}

// === 请求处理 ===
async function processRequest(request: { id: string; method: string; params?: any }): Promise<void> {
  const { id, method, params = {} } = request;
  activeRequestId = id;

  try {
    if (method === 'heartbeat') {
      _originalStdoutWrite(
        JSON.stringify({ id, type: 'heartbeat', ts: Date.now() }) + '\n'
      );
      return;
    }

    if (method === 'shutdown') {
      _originalStdoutWrite(
        JSON.stringify({ type: 'daemon', event: 'shutting_down' }) + '\n'
      );
      process.exit(0);
      return;
    }

    if (method === 'abort') {
      if (agentSdk) {
        await agentSdk.cancelCurrentRequest();
      }
      _originalStdoutWrite(JSON.stringify({ id, done: true, success: true }) + '\n');
      return;
    }

    if (method === 'claude.send') {
      const { message, sessionId, cwd, model } = params;

      // 发送流式事件
      _originalStdoutWrite(JSON.stringify({ id, line: '[MESSAGE_START]' }) + '\n');
      _originalStdoutWrite(JSON.stringify({ id, line: '[STREAM_START]' }) + '\n');

      const result = await agentSdk.sendMessageStream(
        message,
        sessionId,
        cwd,
        model,
        (event) => {
          _originalStdoutWrite(JSON.stringify({ id, line: event }) + '\n');
        }
      );

      _originalStdoutWrite(JSON.stringify({ id, line: '[USAGE]' }) + '\n');
      _originalStdoutWrite(JSON.stringify({ id, line: '[MESSAGE_END]' }) + '\n');
      _originalStdoutWrite(JSON.stringify({ id, done: true, success: true }) + '\n');
      return;
    }

    if (method === 'claude.preconnect') {
      _originalStdoutWrite(JSON.stringify({ id, done: true, success: true }) + '\n');
      return;
    }

    if (method === 'claude.resetRuntime') {
      _originalStdoutWrite(JSON.stringify({ id, done: true, success: true }) + '\n');
      return;
    }

    // 未知方法
    _originalStdoutWrite(
      JSON.stringify({ id, done: false, error: `Unknown method: ${method}` }) + '\n'
    );
  } catch (error: any) {
    _originalStdoutWrite(
      JSON.stringify({
        id,
        done: true,
        success: false,
        error: error.message || 'Unknown error',
      }) + '\n'
    );
  } finally {
    activeRequestId = null;
  }
}

// === 主循环 ===
async function main(): Promise<void> {
  // 1. 发送 starting 事件
  _originalStdoutWrite(
    JSON.stringify({ type: 'daemon', event: 'starting', pid: process.pid }) + '\n'
  );

  // 2. 预加载 SDK（阻塞，等待完成）
  try {
    await preloadSdks();
  } catch (error: any) {
    _originalStdoutWrite(
      JSON.stringify({
        type: 'daemon',
        event: 'error',
        message: `SDK preload failed: ${error.message}`,
      }) + '\n'
    );
    process.exit(1);
    return;
  }

  // 3. 发送 ready 事件（Java 端等待此事件）
  sdkReady = true;
  _originalStdoutWrite(
    JSON.stringify({ type: 'daemon', event: 'ready', pid: process.pid, sdkPreloaded: true }) + '\n'
  );

  // 4. 设置父进程监控（防止僵尸进程）
  const initialPpid = process.ppid;
  setInterval(() => {
    try {
      process.kill(initialPpid, 0);
    } catch (e: any) {
      if (e.code === 'ESRCH') {
        console.error('[Daemon] Parent process died, exiting...');
        process.exit(0);
      }
    }
  }, 10_000);

  // 5. 进入请求监听循环
  const rl = readline.createInterface({
    input: process.stdin,
    crlfDelay: Infinity,
  });

  let commandQueue = Promise.resolve();

  rl.on('line', (line: string) => {
    if (!line.trim()) return;

    try {
      const request = JSON.parse(line);
      commandQueue = commandQueue.then(() => processRequest(request));
    } catch (e: any) {
      console.error('[Daemon] Failed to parse request:', e.message);
    }
  });

  rl.on('close', () => {
    console.error('[Daemon] stdin closed, exiting...');
    process.exit(0);
  });
}

// === 启动 ===
main().catch((error) => {
  console.error('[Daemon] Fatal error:', error);
  process.exit(1);
});
