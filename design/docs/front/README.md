# ClaudeCodeJet 前端开发文档索引

**文档版本**: 1.0
**创建日期**: 2026-04-08
**维护者**: Frontend Team

---

## 📚 文档结构

```
E:\work-File\code\ccgui\design\docs\front\
├── 00-roadmap.md                    # 总体开发路线图 ⭐
├── 10-architecture.md               # 技术架构设计 ⭐
├── 11-types.md                      # 类型定义规范 ⭐
├── 12-components.md                 # 组件设计规范 ⭐
├── 01-phase1-foundation.md          # Phase 1: 基础架构搭建
├── 02-phase6-optimization.md        # Phase 2-6: 开发计划概要
└── README.md                        # 本文档（索引）
```

---

## 🎯 快速导航

### 📋 规划与架构文档

| 文档 | 描述 | 目标读者 |
|------|------|----------|
| [00-roadmap.md](./00-roadmap.md) | **总体开发路线图**<br>15周开发计划，6个阶段概览，技术栈确认，里程碑定义 | 全体成员 |
| [10-architecture.md](./10-architecture.md) | **技术架构设计**<br>分层架构，组件设计，状态管理，通信层，工具层 | 前端开发 |
| [11-types.md](./11-types.md) | **类型定义规范**<br>完整的TypeScript类型定义，包括聊天、会话、主题、生态等类型 | 前端开发 |
| [12-components.md](./12-components.md) | **组件设计规范**<br>组件设计原则，基础UI组件，复合组件，业务组件，性能优化 | 前端开发 |

### 📝 阶段开发计划

| 阶段 | 文档 | 周期 | 状态 |
|------|------|------|------|
| **Phase 1** | [01-phase1-foundation.md](./01-phase1-foundation.md) | Week 1-3 | 📝 计划中 |
| **Phase 2** | [02-phase6-optimization.md](./02-phase6-optimization.md) | Week 4-6 | 📝 计划中 |
| **Phase 3** | [02-phase6-optimization.md](./02-phase6-optimization.md) | Week 7-9 | 📝 计划中 |
| **Phase 4** | [02-phase6-optimization.md](./02-phase6-optimization.md) | Week 10-11 | 📝 计划中 |
| **Phase 5** | [02-phase6-optimization.md](./02-phase6-optimization.md) | Week 12-13 | 📝 计划中 |
| **Phase 6** | [02-phase6-optimization.md](./02-phase6-optimization.md) | Week 14-15 | 📝 计划中 |

---

## 🎓 文档阅读指南

### 对于新加入的成员

**推荐阅读顺序**：
1. 📖 [00-roadmap.md](./00-roadmap.md) - 了解整体规划
2. 📖 [10-architecture.md](./10-architecture.md) - 理解技术架构
3. 📖 [11-types.md](./11-types.md) - 熟悉类型系统
4. 📖 [12-components.md](./12-components.md) - 学习组件设计
5. 📖 [01-phase1-foundation.md](./01-phase1-foundation.md) - 开始第一阶段开发

### 对于前端开发人员

**核心参考文档**：
- 🔧 **日常开发**: [11-types.md](./11-types.md) + [12-components.md](./12-components.md)
- 🏗️ **架构决策**: [10-architecture.md](./10-architecture.md)
- 📅 **任务跟踪**: [00-roadmap.md](./00-roadmap.md) + 阶段计划文档
- 🐛 **问题排查**: [10-architecture.md](./10-architecture.md) 通信层部分

### 对于项目经理

**核心关注文档**：
- 📊 [00-roadmap.md](./00-roadmap.md) - 进度和里程碑
- 📋 各阶段计划文档 - 任务拆解和验收标准

---

## 🔑 关键技术点索引

### JCEF相关
- **JCEF初始化**: [01-phase1-foundation.md](./01-phase1-foundation.md) → T1-W2-01
- **Java-JS通信**: [10-architecture.md](./10-architecture.md) → 通信层
- **类型定义**: [11-types.md](./11-types.md) → bridge.ts

### 状态管理
- **Zustand Store设计**: [10-architecture.md](./10-architecture.md) → 状态管理层
- **appStore**: [01-phase1-foundation.md](./01-phase1-foundation.md) → T1-W3-02
- **sessionStore**: [01-phase1-foundation.md](./01-phase1-foundation.md) → T1-W3-03
- **themeStore**: [01-phase1-foundation.md](./01-phase1-foundation.md) → T1-W3-04

### UI组件
- **基础组件库**: [12-components.md](./12-components.md) → 基础UI组件
- **MessageItem**: [02-phase6-optimization.md](./02-phase6-optimization.md) → T2-W5-01
- **ChatInput**: [02-phase6-optimization.md](./02-phase6-optimization.md) → T2-W6-01
- **InteractiveQuestionPanel**: [12-components.md](./12-components.md) → 业务组件

### 性能优化
- **虚拟滚动**: [12-components.md](./12-components.md) → 组件性能优化
- **Markdown缓存**: [10-architecture.md](./10-architecture.md) → 工具层
- **代码分割**: [02-phase6-optimization.md](./02-phase6-optimization.md) → T6-W14-03

---

## 📊 任务统计

### 总体任务统计

| 类别 | 数量 | 说明 |
|------|------|------|
| **总任务数** | 43 | 所有前端开发任务 |
| **P0任务** | 17 | 核心关键任务 |
| **P1任务** | 21 | 重要任务 |
| **P2任务** | 5 | 优化任务 |

### 按模块统计

| 模块 | 任务数 | P0 | P1 | P2 |
|------|--------|----|----|-----|
| UI与主题系统 | 8 | 3 | 4 | 1 |
| 交互增强系统 | 12 | 4 | 6 | 2 |
| 会话管理系统 | 10 | 5 | 4 | 1 |
| 模型配置系统 | 5 | 2 | 3 | 0 |
| Claude Code生态集成 | 8 | 3 | 4 | 1 |

### 按阶段统计

| 阶段 | 周期 | 任务数 | 关键产出 |
|------|------|--------|----------|
| Phase 1 | Week 1-3 | 18 | 项目脚手架、JCEF通信、状态管理 |
| Phase 2 | Week 4-6 | 18 | 主题系统、消息组件、输入组件 |
| Phase 3 | Week 7-9 | 18 | 流式输出、交互式请求、多模态输入 |
| Phase 4 | Week 10-11 | 12 | 多会话管理、搜索、导入导出 |
| Phase 5 | Week 12-13 | 12 | Skills/Agents/MCP管理器 |
| Phase 6 | Week 14-15 | 12 | 性能优化、测试、发布 |

---

## 🎯 里程碑与验收

### Milestone 1: 基础架构完成 (Week 3)
**验收标准**：
- ✅ React项目可正常启动和构建
- ✅ TypeScript编译无错误
- ✅ JCEF环境检测正常
- ✅ Java-JS双向通信正常
- ✅ 事件总线功能正常
- ✅ Zustand状态管理正常
- ✅ React Router路由正常

### Milestone 2: 核心UI完成 (Week 6)
**验收标准**：
- ✅ 6套预设主题 + 主题编辑器
- ✅ 响应式布局（800-1200px）
- ✅ 消息列表 + Markdown渲染
- ✅ 输入框 + 附件预览

### Milestone 3: 交互增强完成 (Week 9)
**验收标准**：
- ✅ SSE流式输出 + 打字机效果
- ✅ 交互式请求引擎UI
- ✅ 多模态输入（图片/附件）
- ✅ 代码快捷操作

### Milestone 4: 会话管理完成 (Week 11)
**验收标准**：
- ✅ 多会话Tab切换
- ✅ 虚拟滚动消息列表
- ✅ 会话搜索与过滤
- ✅ 会话导入/导出

### Milestone 5: 生态集成完成 (Week 13)
**验收标准**：
- ✅ Skills管理器UI
- ✅ Agents管理器UI
- ✅ MCP服务器管理器UI
- ✅ 作用域管理UI

### Milestone 6: 发布准备 (Week 15)
**验收标准**：
- ✅ 性能优化（启动<1.2s，渲染<200ms）
- ✅ 内存优化（<500MB）
- ✅ 单元测试覆盖率>80%
- ✅ E2E测试通过

---

## 🔧 开发环境设置

### 必需软件
```bash
# Node.js >= 18.0.0
node --version

# npm >= 9.0.0
npm --version

# Git >= 2.30
git --version
```

### 项目初始化
```bash
# 1. 克隆项目
git clone <repository-url>
cd ccgui

# 2. 安装依赖
cd frontend
npm install

# 3. 启动开发服务器
npm run dev

# 4. 构建生产版本
npm run build

# 5. 运行测试
npm run test
```

### VS Code配置
```json
// .vscode/settings.json
{
  "typescript.tsdk": "node_modules/typescript/lib",
  "typescript.enablePromptUseWorkspaceTsdk": true,
  "editor.formatOnSave": true,
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  }
}
```

---

## 📖 代码规范

### 命名规范
```typescript
// 组件：PascalCase
const MessageItem: React.FC<Props> = () => {};

// Hook：camelCase with 'use' prefix
const useStreaming = () => {};

// 类型/接口：PascalCase
interface ChatMessage {}
type MessageStatus = 'pending' | 'sent';

// 常量：UPPER_SNAKE_CASE
const MAX_RETRIES = 3;

// 函数：camelCase
const sendMessage = () => {};

// 私有成员：underscore prefix
const _privateMethod = () => {};
```

### 文件组织
```typescript
// 文件顶部：导入顺序
// 1. React相关
import { useState, useEffect } from 'react';

// 2. 第三方库
import { useStore } from 'zustand';

// 3. 类型导入
import type { ChatMessage } from '@/shared/types';

// 4. 组件导入
import { Button } from '@/shared/components/ui/button';

// 5. 工具函数导入
import { cn } from '@/shared/utils/cn';

// 6. 相对路径导入
import { LocalComponent } from './components';

// 文件中部：组件定义
export const MyComponent: React.FC<Props> = () => {
  // ...
};

// 文件底部：类型导出
export type { MyComponentProps };
```

---

## 🐛 常见问题

### Q1: JCEF环境初始化失败
**症状**: 显示"Not running in JCEF environment"
**解决方案**: 
1. 确保在IntelliJ IDEA插件环境中运行
2. 检查Java端是否正确注入了window.ccBackend
3. 查看浏览器控制台错误信息

### Q2: TypeScript类型错误
**症状**: window.ccBackend报类型错误
**解决方案**: 
1. 确保已正确导入bridge类型定义
2. 检查tsconfig.json路径别名配置
3. 重启TypeScript服务器

### Q3: 样式不生效
**症状**: TailwindCSS类名不工作
**解决方案**: 
1. 确保已正确配置tailwind.config.js
2. 检查postcss配置
3. 清除缓存重新构建

### Q4: 虚拟滚动显示异常
**症状**: 消息列表显示不正确
**解决方案**: 
1. 检查estimateSize是否合理
2. 确保消息有唯一的key
3. 检查容器高度是否正确设置

---

## 📞 联系方式

### 技术支持
- **前端技术负责人**: [待定]
- **架构师**: [待定]
- **项目经理**: [待定]

### 文档维护
- **问题反馈**: 在项目仓库提Issue
- **文档更新**: 提交PR到design/docs/front目录

---

## 🔄 文档更新日志

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|----------|------|
| 1.0 | 2026-04-08 | 初始版本，创建所有核心文档 | Frontend Team |

---

**最后更新**: 2026-04-08
**文档状态**: ✅ 最新
