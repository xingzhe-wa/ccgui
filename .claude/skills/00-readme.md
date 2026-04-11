# CC Assistant 开发技能手册

## 架构宣言

> **被动沉淀**（PIT 踩坑记录）和**主动防御**（合规检查清单）必须是同一数据源的双视图，而非两份独立文档。
> Bug 修复时写入 PIT Master → `/sync-pit` 同步到合规清单 → 编码前查清单自动覆盖新场景。

---

## 可用 Slash Commands

| 命令 | 触发 Agent | 说明 |
|------|-----------|------|
| `/sync-pit` | ide-plugin-architect | 同步 PIT Master 到合规检查清单 |
| `/pit-sync` | ide-plugin-architect | 同上 |
| `/jcef` | ide-plugin-architect | JCEF 生命周期与页面加载问题 |
| `/jcef-lifecycle` | ide-plugin-architect | 同上 |
| `/java-js-bridge` | ide-plugin-architect | 前后端通信桥接问题 |
| `/streaming` | ide-plugin-architect | 流式输出与协议问题 |
| `/coroutine` | ide-plugin-architect | Kotlin 协程调度问题 |
| `/naming` | ide-plugin-architect | 插件命名规范问题 |
| `/build` | ide-plugin-architect | 构建与资源加载问题 |
| `/plugin-dev` | ccgui-plugin-developer | 插件开发通用问题 |
| `/frontend-dev` | ccgui-frontend-developer | 前端开发通用问题 |

---

## 技能列表（知识文档）

| 编号 | 名称 | slash 命令 | 职责 |
|------|------|-----------|------|
| 01 | [JCEF 页面生命周期管理](./01-jcef-page-lifecycle.md) | `/jcef-lifecycle` | 页面加载、Bridge 注入时机 |
| 02 | [Java ↔ JavaScript 通信桥接](./02-java-js-bridge.md) | `/java-js-bridge` | 前后端消息格式、参数解析链路 |
| 03 | [前后端事件系统桥接](./03-event-system-bridge.md) | — | Java → JS 事件 CustomEvent 转发 |
| 04 | [Kotlin 协程调度器选型](./04-coroutine-dispatcher.md) | `/coroutine` | Dispatchers.Main 启动崩溃规避 |
| 05 | [流式通信协议规范](./05-streaming-protocol.md) | `/streaming` | Chunk 推送、messageId 关联、store 状态 |
| 06 | [插件命名规范与 ID 管理](./06-plugin-naming-conventions.md) | `/naming` | 插件 ID / Action ID / 显示名三层分离 |
| 07 | [前端构建与 JAR 资源加载](./07-build-resource-loading.md) | `/build` | Vite 相对路径、JAR 提取、file:// 加载 |
| **PIT** | **踩坑记录与合规同步** | `/sync-pit` | 见 `pit/` 子目录 |

---

## PIT 子目录（`pit/`）

| 文件 | 职责 |
|------|------|
| `pit/00-pit-master.md` | PIT Master — 所有踩坑记录的唯一写入点 |
| `pit/01-pit-sync.md` | PIT 同步 + 合规检查清单 |

---

## 闭环流程

```
┌─────────────────────────────────────────────────────────────────┐
│  编码前: 读取合规检查清单 (pit/01-pit-sync.md)                   │
│          → 对照 PIT 规避项 → 执行开发                            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ bug 发生
┌──────────────────────────▼──────────────────────────────────────┐
│  Bug 修复: 写入 PIT (pit/00-pit-master.md) → 调用 /sync-pit   │
│          → 合规清单自动膨胀                                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 文档数据流

```
pit/00-pit-master.md  (PIT Master - 唯一数据源)
         │
         │  /sync-pit 解析写入
         ▼
pit/01-pit-sync.md  (合规清单 - 派生视图，<!-- AUTO-GENERATED --> 区域)
         │
         │  read (human)
         ▼
.claude/skills/00-readme.md  (入口索引 - 协调视图)
```

**原则**：
- PIT Master 是唯一写入点
- 合规清单只读，仅通过 `/sync-pit` 更新
- 两者共享同一套 PIT 编号体系（PIT-001, PIT-002...）

---

## 使用说明

**编码前** → 读 `pit/01-pit-sync.md` 的"合规检查清单"，逐项确认

**Bug 修复时** → 在 `pit/00-pit-master.md` 追加新 PIT → 调用 `/sync-pit`

**Code Review 时** → 对照清单审查 → 发现新盲点 → 写入 PIT → `/sync-pit`
