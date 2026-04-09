# 功能修复执行计划 — v0.0.4

**文档版本**: 1.0
**基于**: PRD-Gap-Analysis-v0.0.3.md
**目标版本**: v0.0.4
**预计工期**: 约 3-4 个工作日
**日期**: 2026-04-10

---

## 执行进度

| Phase | Task | 状态 | 完成日期 |
|-------|------|------|---------|
| （历史） | Phase 1/2/3 全部完成 | ✅ | 2026-04-10 |
| 新增 | v0.0.3 Review 发现的新 Gap | ✅ 全部完成 | 2026-04-10 |
| G1 | handleSendMultimodalMessage 实现 | ✅ 已完成 | 2026-04-10 |
| G2 | streamMessage 响应处理修复 | ✅ 已完成 | 2026-04-10 |

---

## 1. v0.0.3 Review 发现的 Gap

### Task G1: handleSendMultimodalMessage 实现（P1）

**问题**: `CefBrowserPanel.handleSendMultimodalMessage()` 返回 error，多模态附件发送完全不可用。前端 UI 已完整（拖拽、粘贴、预览），但后端未实现。

**涉及文件**:
- `src/main/kotlin/com/github/xingzhewa/ccgui/browser/CefBrowserPanel.kt` — `handleSendMultimodalMessage` 实现
- `src/main/kotlin/com/github/xingzhewa/ccgui/bridge/BridgeManager.kt` — 多模态消息路由
- `src/main/kotlin/com/github/xingzhewa/ccgui/application/multimodal/MultimodalInputHandler.kt` — 已有框架，需激活

**验收标准**:
- [ ] 发送图片附件后，消息气泡中能显示缩略图
- [ ] 发送文件附件后，消息气泡中能显示文件名
- [ ] `./gradlew build` 成功

**工作量**: 2 人天

---

### Task G2: streamMessage 响应处理修复（P2）

**问题**: `java-bridge.ts` 中 `streamMessage` 是 void 函数，不等待响应。后端会调用 `onResponse`，但前端不接收。

**涉及文件**:
- `webview/src/lib/java-bridge.ts` — `streamMessage` 改为返回 Promise

**验收标准**:
- [ ] `streamMessage` 返回 Promise，可等待响应或错误
- [ ] `ChatView.tsx` 中的流式消息正确处理错误状态

**工作量**: 0.5 人天

---

## 2. 工期估算汇总

| Task | 工作量 |
|------|--------|
| G1: MultimodalMessage 实现 | ✅ 已完成 |
| G2: streamMessage 响应处理 | ✅ 已完成 |
| G3: 响应式布局断点补全（P3） | 0.5 天 |

---

## 3. Sprint 排期建议

### Sprint 12（2.5 天）✅ 已完成

- Task G1: MultimodalMessage 实现 ✅
- Task G2: streamMessage 响应处理修复 ✅

---

## 4. v0.0.4 新增 Gap（P3）

### Task G3: 响应式布局断点补全（P3）

**问题**: 当前实现 `<768px` 隐藏 `MessageDetail`，`>=768px` 显示固定 `w-80`。PRD 规定三断点（`<800px` 单列 / `800-1200px` 60:40 分栏 / `>1200px` 50:50 分栏）。

**现状**: `ChatView.tsx` 中 `ResizeObserver` 只监听宽度，`containerWidth >= 768` 时显示 `MessageDetail w-80`，无动态比例调整。

**涉及文件**:
- `webview/src/main/pages/ChatView.tsx` — 布局 class 动态化

**验收标准**:
- [ ] <800px：单列布局，MessageDetail 隐藏
- [ ] 800-1200px：左右分栏 60%:40%
- [ ] >1200px：左右分栏 50%:50%
- [ ] `npm run build` 成功

**工作量**: 0.5 人天

---

*计划版本：v1.0 | 编制日期：2026-04-10 | 更新日期：2026-04-10 | 基于：PRD-Gap-Analysis-v0.0.3.md*
