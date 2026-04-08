# v0.0.1 发版协作 Prompts

## 使用方式

每个 Sprint prompt 是一个**自包含的任务包**，包含：
- 项目背景（不需要额外上下文）
- 具体任务清单（精确到文件级别）
- 验收标准（明确的完成定义）
- 交接话术（标准化交接）

### 协作流程

```
开发者 A 拿到 sprint-1 prompt
  → 执行任务 → 勾选验收标准 → 记录执行记录 → 交接

开发者 B 拿到 sprint-2 prompt + A 的执行记录
  → 执行任务 → 勾选验收标准 → 记录执行记录 → 交接

...依次类推直到 sprint-5 完成
```

### 如何使用 Prompt

1. 将对应 Sprint 的 prompt 全文复制给 AI（Claude / Cursor / Copilot Chat）
2. AI 会读取指定的文件并执行任务
3. 每个 prompt 包含 "需要读取的文件" 列表，AI 会自行读取
4. 完成后勾选 "验收标准"，在末尾记录实际执行情况

## Prompt 文件列表

| 文件 | Sprint | 预计耗时 | 核心目标 |
|------|--------|---------|---------|
| `sprint-1-build-pipeline.md` | Sprint 1 | 1 天 | 打通构建，`buildPlugin` 产出可用 .zip |
| `sprint-2-css-theme.md` | Sprint 2 | 1 天 | 修复 CSS 变量，主题切换生效 |
| `sprint-3-e2e-integration.md` | Sprint 3 | 2 天 | 端到端联调，消息收发链路跑通 |
| `sprint-4-stability.md` | Sprint 4 | 1 天 | 修 Bug + 错误兜底 + 稳定运行 |
| `sprint-5-release.md` | Sprint 5 | 0.5 天 | 最终打包 + 安装验证 + Git tag |

## 关联文档

| 文件 | 说明 |
|------|------|
| `design/docs/release-v0.0.1-plan.md` | 完整发版执行方案 |
| `design/docs/backend/00-overview.md` | 后端架构总览 |
| `design/docs/front/00-overview.md` | 前端架构总览 |
| `design/docs/front/10-architecture.md` | 前端组件层级设计 |
| `design/docs/front/20-dev-guide.md` | 前端开发进度评估 |

## 注意事项

1. **顺序严格**：Sprint 1-5 必须按顺序执行，每个 Sprint 依赖上一个的产出
2. **验收标准不可跳过**：每个 checkbox 必须勾选后才能交接
3. **执行记录必填**：在 prompt 末尾记录实际发现的问题和修复方案，这是下一位开发者的关键上下文
4. **Sprint 3 是关键路径**：端到端联调最容易出问题，预留充足时间
5. **Windows 兼容性**：所有开发者注意 Windows 环境下的路径和进程管理问题
