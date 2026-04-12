# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CCGUI is an IntelliJ IDEA plugin that provides a chat interface for Claude Code (Claude AI coding assistant). It uses JCEF (Java Chromium Embedded Framework) to embed a React-based web UI within the IDE.

**Tech Stack:**
- **Backend**: Kotlin 2.1.20, IntelliJ Platform Gradle Plugin, target JVM 21
- **Frontend**: React 18, TypeScript, Vite 5, TailwindCSS, Zustand
- **Bridge**: Custom JCEF Bridge for JS↔Java communication (JBCefJSQuery-based)

## Build Commands

### Backend (Kotlin/Gradle)
```bash
# Build the entire plugin (includes frontend build)
./gradlew buildPlugin

# Run IDE with plugin for development
./gradlew runIde

# Run tests
./gradlew test

# Run verifications
./gradlew verifyPlugin

# Run linting/inspections
./gradlew check
```

### Frontend (React/Vite)
```bash
cd webview

# Development server (hot reload at localhost:3000)
npm run dev

# Build frontend (called automatically by Gradle)
npm run build

# Type check
npm run typecheck

# Lint
npm run lint

# Run tests
npm run test
npm run test:ui        # UI mode
npm run test:coverage  # With coverage
```

**Important**: The Gradle `buildPlugin` task automatically runs `npm run build` and copies frontend assets to `src/main/resources/webview/dist/`. When working on frontend, run `npm run dev` for faster iteration.

## High-Level Architecture

### Bridge Communication Pattern

The plugin uses a **custom JCEF Bridge** for bidirectional JS↔Java communication:

```
[React Frontend] → window.ccBackend.send() → [JBCefJSQuery] → [Kotlin Handler]
                                                           ↓
[Claude CLI Process] ← [ClaudeCodeClient] ← [BridgeManager] ← [JsRequestHandler]
                                                           ↓
[StreamingOutputEngine] → [CefBrowserPanel] → window.ccEvents.emit() → [React EventBus]
```

**Key Components:**
- `CefBrowserPanel.kt` - JCEF browser wrapper, injects Bridge JavaScript
- `JsRequestHandler.kt` - Routes JS requests to appropriate Kotlin handlers
- `java-bridge.ts` - Frontend wrapper for Bridge communication
- `event-bus.ts` - Frontend event bus for component communication

### Backend Package Structure

```
src/main/kotlin/com/github/claudecode/ccgui/
├── adaptation/sdk/          # Claude Code CLI integration
│   ├── ClaudeCodeClient.kt  # Main CLI client
│   ├── SdkSessionManager.kt # Session management
│   └── SdkPermissionHandler.kt # Permission requests
├── application/             # Business logic services
│   ├── orchestrator/ChatOrchestrator.kt # Central chat coordinator
│   ├── session/SessionService.kt
│   ├── streaming/StreamingOutputEngine.kt
│   ├── mcp/McpServerManager.kt
│   ├── agent/AgentsManager.kt
│   └── skill/SkillsManager.kt
├── bridge/                  # Bridge integration
├── browser/                 # JCEF browser wrapper
│   ├── CefBrowserPanel.kt
│   └── handler/JsRequestHandler.kt
├── infrastructure/          # Cross-cutting concerns
│   ├── eventbus/EventBus.kt
│   ├── storage/
│   └── state/StateManager.kt
├── model/                   # Data models
│   ├── message/ChatMessage.kt
│   ├── session/ChatSession.kt
│   └── config/
├── toolWindow/              # IDE integration
│   ├── MyToolWindowFactory.kt
│   └── FrontendHttpServer.kt
└── action/                  # IDE actions (CodeExplainAction, etc.)
```

### Frontend Structure

```
webview/src/
├── main/                    # Main application pages
│   ├── pages/ChatView.tsx
│   ├── pages/SettingsView.tsx
│   └── components/AppLayout.tsx
├── features/                # Feature modules
│   ├── chat/               # Chat components (MessageItem, MarkdownRenderer, etc.)
│   ├── session/            # Session management (SessionTabs, SessionHistory)
│   ├── streaming/          # Streaming components (StreamingMessage, StopButton)
│   ├── interaction/        # Interactive questions (InteractiveQuestionPanel)
│   ├── model/              # Model/provider configuration
│   └── theme/              # Theme editor
├── shared/                  # Shared utilities
│   ├── stores/             # Zustand stores (sessionStore, themeStore, streamingStore)
│   ├── hooks/              # Custom React hooks (useJavaBridge, useStreaming, etc.)
│   ├── types/              # TypeScript type definitions
│   ├── utils/              # Utility functions (event-bus, sse-parser, etc.)
│   └── i18n/               # Internationalization (en-US, zh-CN)
└── styles/                  # Global styles
    ├── globals.css         # CSS variables + Tailwind directives
    └── themes/             # Predefined theme CSS files
```

## Key Development Patterns

### Adding a New Backend Action Handler

When adding a new action callable from frontend:

1. **Add handler in `CefBrowserPanel.handleJsRequest()` when clause:**
   ```kotlin
   "myNewAction" -> handleMyNewAction(queryId, params)
   ```

2. **Implement the handler method:**
   ```kotlin
   private fun handleMyNewAction(queryId: Int, params: JsonElement?): Any? {
       val input = params?.asJsonObject?.get("input")?.asString ?: return null
       val result = myService.doSomething(input)
       sendResponseToJs("myNewAction", queryId, result)
       return null
   }
   ```

3. **For streaming actions, use StreamCallback pattern** (see methodology.md section 4.3)

### Event Naming Convention

**Critical**: Java event names MUST match JS EventBus constant values exactly:

| Java sendToJavaScript() | Event Name | JS EventBus Constant |
|------------------------|------------|---------------------|
| `sendToJavaScript("streaming:chunk", ...)` | `streaming:chunk` | `Events.STREAMING_CHUNK` |
| `sendToJavaScript("streaming:complete", ...)` | `streaming:complete` | `Events.STREAMING_COMPLETE` |
| `sendToJavaScript("response", ...)` | `response` | (internal to java-bridge.ts) |

### CSS Variable Theme System

The project uses **HSL format CSS variables** for TailwindCSS compatibility:

```typescript
// ThemeConfig stores HEX colors
interface ThemeConfig {
  colors: { primary: "#0d47a1", ... }
}

// Runtime conversion to HSL
function hexToHsl(hex: string): string {
  // "#0d47a1" → "217 81% 34%" (space-separated)
}

// Applied to CSS variables
root.style.setProperty('--primary', hexToHsl(colors.primary));
```

**Key files**: `themeStore.ts`, `globals.css`, `tailwind.config.js`

### Thread Safety in JCEF Context

- JCEF callbacks run on **non-EDT threads**
- Always wrap Swing UI updates in `invokeLater`:
  ```kotlin
  ApplicationManager.getApplication().invokeLater {
      toolWindow.setTitle("New Title")
  }
  ```
- Use `MutableStateFlow` for concurrent state in streaming context

### Store Synchronization

The app uses multiple Zustand stores. When changing "current" state across stores:

```typescript
switchSession: (sessionId) => {
  set({ currentSessionId: sessionId });
  // CRITICAL: Sync to other stores
  useSessionStore.getState().setCurrentSession(sessionId);
}
```

## Important Conventions

### Logging

Use the `logger<T>()` extension function, **NOT** `LoggerUtils.logger<T>()`:

```kotlin
private val log = logger<MyClass>()
```

### Error Boundaries

All React root components must be wrapped in `ErrorBoundary` - JCEF has no browser DevTools for debugging.

### Third-Party CSS

When importing npm packages with CSS, import both JS and CSS:

```typescript
import hljs from 'highlight.js';
import 'highlight.js/styles/github-dark.css'; // Don't forget this!
```

### CLI Availability Check

Always check CLI availability before using it (fast-fail pattern):

```kotlin
if (!claudeClient.isCliAvailable()) {
    callback.onStreamError("Claude CLI is not installed...")
    return
}
```

## File Quick Reference

| Purpose | File Path |
|---------|-----------|
| JCEF Bridge core | `src/main/kotlin/.../browser/CefBrowserPanel.kt` |
| Message entry point | `src/main/kotlin/.../application/orchestrator/ChatOrchestrator.kt` |
| JS Request routing | `src/main/kotlin/.../browser/handler/JsRequestHandler.kt` |
| CLI client | `src/main/kotlin/.../adaptation/sdk/ClaudeCodeClient.kt` |
| Frontend Bridge | `webview/src/shared/hooks/useJavaBridge.ts` |
| Frontend EventBus | `webview/src/shared/utils/event-bus.ts` |
| Theme system | `webview/src/shared/stores/themeStore.ts` |
| CSS variables | `webview/src/styles/globals.css` |
| Build config | `build.gradle.kts` |

## Development Methodology

See `design/methodology.md` for comprehensive development guidelines, including:
- "Gap-First" debugging approach
- Layer-by-layer verification
- Three-stage validation model
- Common pitfalls and solutions

Key principle: **Verify each layer independently before testing end-to-end**.

---

# Technical Architecture Constraints

> **CRITICAL**: All development MUST follow the technical architecture defined in:
> **`design/Claude_Code_IDEA_Plugin_Technical_Architecture-init.md`** (v3.1 PIT 合规增强版)
>
> This document contains:
> - Core design principles (lightweight, native-first, lazy loading)
> - Three-layer architecture (Presentation / Service / Infrastructure)
> - PIT compliance checklist (PIT-001 through PIT-013)
> - JCEF lifecycle requirements
> - Frontend-backend communication protocol
> - Claude Code daemon parameter limits

**Before making any changes:**
1. Read the relevant section of the architecture document
2. Check the PIT compliance checklist for applicable items
3. Follow the established patterns in existing code

---
