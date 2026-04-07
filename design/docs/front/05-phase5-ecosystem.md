# Phase 5: 生态集成 (Ecosystem Integration)

**优先级**: P1
**预估工期**: 17.5人天 (3.5周)
**前置依赖**: Phase 4（会话管理可用，后端Core Services已提供Skills/Agents/MCP管理API）
**阶段目标**: Skills/Agents/MCP管理器UI完整可用，作用域管理可用

---

## 1. 阶段概览

本阶段构建Claude Code生态组件的可视化管理界面：

1. Skills管理器（CRUD + 分类过滤 + 导入/导出）
2. Agents管理器（CRUD + 3种模式配置 + 启动/停止）
3. MCP服务器管理器（CRUD + 连接测试 + 状态监控）
4. 作用域管理（全局/项目/会话三级作用域）

**完成标志**: 可视化管理所有Claude Code生态组件，CRUD操作正常

**与后端协作点**:
- 后端Phase 5的 `SkillsManager` 提供 Skills CRUD API（`getSkills`/`saveSkill`/`deleteSkill`/`executeSkill`）
- 后端Phase 5的 `AgentsManager` 提供 Agents CRUD API（`getAgents`/`saveAgent`/`deleteAgent`/`startAgent`/`stopAgent`）
- 后端Phase 5的 `McpServerManager` 提供 MCP 服务器管理API（`getMcpServers`/`saveMcpServer`/`startMcpServer`/`stopMcpServer`/`testMcpServer`）
- 所有操作通过 `window.ccBackend` 调用，前端不直接访问文件系统

---

## 2. 任务清单

### Week 12: Skills/Agents管理器

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T5-W12-01** | Skills管理器UI | 2人天 | SkillsManager.tsx | UI正常，CRUD完整 |
| **T5-W12-02** | SkillCard组件 | 1人天 | SkillCard.tsx | 卡片显示正常 |
| **T5-W12-03** | SkillEditor表单 | 2人天 | SkillEditor.tsx | 表单验证正常，支持prompt编辑 |
| **T5-W12-04** | Agents管理器UI | 2人天 | AgentsManager.tsx | UI正常，CRUD完整 |
| **T5-W12-05** | AgentCard组件 | 1人天 | AgentCard.tsx | 卡片显示正常，状态标识 |
| **T5-W12-06** | AgentEditor表单 | 2人天 | AgentEditor.tsx | 3种模式配置正常 |

### Week 13: MCP服务器管理器

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T5-W13-01** | MCP服务器管理器UI | 2人天 | McpServerManager.tsx | UI正常 |
| **T5-W13-02** | McpServerCard组件 | 1人天 | McpServerCard.tsx | 状态显示正确 |
| **T5-W13-03** | 服务器配置表单 | 1.5人天 | McpServerConfig.tsx | 表单验证正常 |
| **T5-W13-04** | 连接状态指示器 | 1人天 | ConnectionStatus.tsx | 状态实时更新 |
| **T5-W13-05** | 测试连接功能 | 1人天 | testConnection.ts | 测试结果正确显示 |
| **T5-W13-06** | 作用域管理UI | 1人天 | ScopeManager.tsx | 三级作用域隔离正常 |

---

## 3. Week 12: Skills/Agents管理器

### T5-W12-01: Skills管理器UI

**实现代码**:

```typescript
// src/features/skills/components/SkillsManager.tsx
import { useState, useMemo, memo } from 'react';
import { Button } from '@/shared/components/ui/button';
import { useSkillsStore } from '@/shared/stores/skillsStore';
import { SkillsList } from './SkillsList';
import { SkillEditor } from './SkillEditor';
import { SkillImportDialog } from './SkillImportDialog';
import type { Skill, SkillCategory } from '@/shared/types';
import { SkillCategory } from '@/shared/types';

/**
 * Skills管理器
 *
 * 管理AI技能模板的CRUD操作。
 * 分类过滤：代码生成/代码审查/重构/测试/文档/自定义。
 * 支持导入/导出JSON格式。
 */
export const SkillsManager = memo(() => {
  const { skills, createSkill, updateSkill, deleteSkill, importSkills, exportSkills } = useSkillsStore();
  const [editingSkill, setEditingSkill] = useState<Skill | null>(null);
  const [showImportDialog, setShowImportDialog] = useState(false);
  const [filter, setFilter] = useState<'all' | SkillCategory>('all');

  const filteredSkills = useMemo(() => {
    if (filter === 'all') return skills;
    return skills.filter(s => s.category === filter);
  }, [skills, filter]);

  const handleCreateSkill = () => {
    setEditingSkill({
      id: '',
      name: 'New Skill',
      description: '',
      category: SkillCategory.CODE_GENERATION,
      prompt: '',
      enabled: true,
      scope: 'project',
      createdAt: Date.now(),
      updatedAt: Date.now()
    });
  };

  const handleSaveSkill = async (skill: Skill) => {
    if (skill.id) {
      await window.ccBackend?.saveSkill(skill);
      updateSkill(skill);
    } else {
      const created = await window.ccBackend?.saveSkill({
        ...skill,
        id: `skill-${Date.now()}`
      });
      if (created) createSkill(created);
    }
    setEditingSkill(null);
  };

  const handleDeleteSkill = async (skillId: string) => {
    await window.ccBackend?.deleteSkill(skillId);
    deleteSkill(skillId);
  };

  const handleExportSkills = () => {
    const data = JSON.stringify(filteredSkills, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `skills-export-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="p-6">
      {/* 头部 */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">Skills管理</h2>
          <p className="text-muted-foreground">管理你的AI技能模板</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setShowImportDialog(true)}>
            导入
          </Button>
          <Button variant="outline" onClick={handleExportSkills}>
            导出
          </Button>
          <Button onClick={handleCreateSkill}>
            新建Skill
          </Button>
        </div>
      </div>

      {/* 分类过滤器 */}
      <div className="mb-4 flex gap-2">
        {(['all', SkillCategory.CODE_GENERATION, SkillCategory.CODE_REVIEW, SkillCategory.REFACTORING, SkillCategory.TESTING, SkillCategory.DOCUMENTATION, SkillCategory.CUSTOM] as const).map((cat) => (
          <Button
            key={cat}
            variant={filter === cat ? 'default' : 'ghost'}
            size="sm"
            onClick={() => setFilter(cat)}
          >
            {cat === 'all' ? '全部' : cat.replace('_', ' ')}
          </Button>
        ))}
      </div>

      {/* Skills列表 */}
      <SkillsList
        skills={filteredSkills}
        onEdit={setEditingSkill}
        onDelete={handleDeleteSkill}
      />

      {/* 编辑器 */}
      {editingSkill && (
        <SkillEditor
          skill={editingSkill}
          onSave={handleSaveSkill}
          onClose={() => setEditingSkill(null)}
        />
      )}

      {/* 导入对话框 */}
      {showImportDialog && (
        <SkillImportDialog
          onImport={async (imported) => {
            for (const skill of imported) {
              await window.ccBackend?.saveSkill(skill);
            }
            importSkills(imported);
            setShowImportDialog(false);
          }}
          onClose={() => setShowImportDialog(false)}
        />
      )}
    </div>
  );
});

SkillsManager.displayName = 'SkillsManager';
```

---

### T5-W12-04: Agents管理器UI

**实现代码**:

```typescript
// src/features/agents/components/AgentsManager.tsx
import { useState, memo } from 'react';
import { Button } from '@/shared/components/ui/button';
import { useAgentsStore } from '@/shared/stores/agentsStore';
import { AgentsList } from './AgentsList';
import { AgentEditor } from './AgentEditor';
import { AgentMode } from '@/shared/types';
import type { Agent } from '@/shared/types';

/**
 * Agents管理器
 *
 * 管理AI Agent配置的CRUD操作。
 * 支持3种Agent模式：谨慎(cautious)/均衡(balanced)/激进(aggressive)。
 * 可启动/停止Agent运行。
 */
export const AgentsManager = memo(() => {
  const { agents, createAgent, updateAgent, deleteAgent } = useAgentsStore();
  const [editingAgent, setEditingAgent] = useState<Agent | null>(null);

  const handleCreateAgent = () => {
    setEditingAgent({
      id: '',
      name: 'New Agent',
      description: '',
      mode: AgentMode.BALANCED,
      systemPrompt: '',
      tools: [],
      enabled: true,
      scope: 'project',
      createdAt: Date.now(),
      updatedAt: Date.now()
    });
  };

  const handleSaveAgent = async (agent: Agent) => {
    if (agent.id) {
      await window.ccBackend?.saveAgent(agent);
      updateAgent(agent);
    } else {
      const created = await window.ccBackend?.saveAgent({
        ...agent,
        id: `agent-${Date.now()}`
      });
      if (created) createAgent(created);
    }
    setEditingAgent(null);
  };

  const handleStartAgent = async (agentId: string) => {
    await window.ccBackend?.startAgent(agentId, {});
  };

  const handleStopAgent = async (agentId: string) => {
    await window.ccBackend?.stopAgent(agentId);
  };

  return (
    <div className="p-6">
      {/* 头部 */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">Agents管理</h2>
          <p className="text-muted-foreground">配置和管理AI Agent</p>
        </div>
        <Button onClick={handleCreateAgent}>
          新建Agent
        </Button>
      </div>

      {/* Agents列表 */}
      <AgentsList
        agents={agents}
        onStart={handleStartAgent}
        onStop={handleStopAgent}
        onEdit={setEditingAgent}
        onDelete={async (id) => {
          await window.ccBackend?.deleteAgent(id);
          deleteAgent(id);
        }}
      />

      {/* 编辑器 */}
      {editingAgent && (
        <AgentEditor
          agent={editingAgent}
          onSave={handleSaveAgent}
          onClose={() => setEditingAgent(null)}
        />
      )}
    </div>
  );
});

AgentsManager.displayName = 'AgentsManager';
```

---

## 4. Week 13: MCP服务器管理器

### T5-W13-01: MCP服务器管理器UI

**实现代码**:

```typescript
// src/features/mcp/components/McpServerManager.tsx
import { useState, memo } from 'react';
import { Button } from '@/shared/components/ui/button';
import { useMcpStore } from '@/shared/stores/mcpStore';
import { McpServerList } from './McpServerList';
import { McpServerConfig } from './McpServerConfig';
import type { McpServer } from '@/shared/types';

/**
 * MCP服务器管理器
 *
 * 管理 Model Context Protocol 服务器配置。
 * 支持启动/停止服务器、测试连接、查看capabilities。
 */
export const McpServerManager = memo(() => {
  const { servers, startServer, stopServer, testConnection, saveServer, deleteServer } = useMcpStore();
  const [editingServer, setEditingServer] = useState<McpServer | null>(null);

  const handleStartServer = async (serverId: string) => {
    await window.ccBackend?.startMcpServer(serverId);
    startServer(serverId);
  };

  const handleStopServer = async (serverId: string) => {
    await window.ccBackend?.stopMcpServer(serverId);
    stopServer(serverId);
  };

  const handleTestConnection = async (serverId: string) => {
    const result = await window.ccBackend?.testMcpServer(serverId);
    if (result?.success) {
      testConnection(serverId);
    }
    return result;
  };

  const handleSaveServer = async (server: McpServer) => {
    await window.ccBackend?.saveMcpServer(server);
    saveServer(server);
    setEditingServer(null);
  };

  const handleDeleteServer = async (serverId: string) => {
    await window.ccBackend?.deleteMcpServer(serverId);
    deleteServer(serverId);
  };

  return (
    <div className="p-6">
      {/* 头部 */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">MCP服务器管理</h2>
          <p className="text-muted-foreground">配置和管理Model Context Protocol服务器</p>
        </div>
        <Button onClick={() => setEditingServer({} as McpServer)}>
          添加服务器
        </Button>
      </div>

      {/* 服务器列表 */}
      <McpServerList
        servers={servers}
        onStart={handleStartServer}
        onStop={handleStopServer}
        onTest={handleTestConnection}
        onEdit={setEditingServer}
        onDelete={handleDeleteServer}
      />

      {/* 配置对话框 */}
      {editingServer && (
        <McpServerConfig
          server={editingServer}
          onSave={handleSaveServer}
          onClose={() => setEditingServer(null)}
        />
      )}
    </div>
  );
});

McpServerManager.displayName = 'McpServerManager';
```

---

### T5-W13-06: 作用域管理UI

**实现代码**:

```typescript
// src/features/mcp/components/ScopeManager.tsx
import { memo } from 'react';
import { cn } from '@/shared/utils/cn';
import type { SkillScope, AgentScope, McpScope } from '@/shared/types';

/**
 * Scope 联合类型：根据泛型动态确定可用的作用域
 */
type Scope = SkillScope | AgentScope | McpScope;

interface ScopeManagerProps<T extends Scope = Scope> {
  currentScope: T;
  onScopeChange: (scope: T) => void;
  availableScopes?: T[];
  className?: string;
}

/**
 * 作用域管理器
 *
 * Claude Code的三级作用域：全局/项目/会话。
 * Skills、Agents、MCP服务器都有作用域属性。
 * 通过泛型参数 T 限定可用作用域范围。
 */
export const ScopeManager = memo(<T extends Scope = Scope>({
  currentScope,
  onScopeChange,
  availableScopes,
  className
}: ScopeManagerProps<T>) => {
  // 根据 Scope 联合类型动态确定默认 availableScopes
  const scopes: T[] = availableScopes ?? (['global', 'project', 'session'] as T[]);

  const scopeConfig: Record<string, { label: string; description: string }> = {
    global: {
      label: '全局',
      description: '所有项目和会话共享'
    },
    project: {
      label: '项目',
      description: '仅当前项目内可用'
    },
    session: {
      label: '会话',
      description: '仅当前会话内可用'
    }
  };

  return (
    <div className={cn('flex gap-2', className)}>
      {scopes.map((scope) => (
        <button
          key={scope}
          onClick={() => onScopeChange(scope)}
          className={cn(
            'flex-1 rounded-md border p-3 text-left transition-colors',
            'hover:bg-accent',
            currentScope === scope && 'border-primary bg-primary/5'
          )}
        >
          <div className="font-medium">{scopeConfig[scope].label}</div>
          <div className="text-xs text-muted-foreground">{scopeConfig[scope].description}</div>
        </button>
      ))}
    </div>
  );
});

ScopeManager.displayName = 'ScopeManager';
```

---

## 4.5 Store 接口定义与 Zustand 实现

### skillsStore

```typescript
// src/shared/stores/skillsStore.ts
import { create } from 'zustand';
import type { Skill, SkillCategory } from '@/shared/types';

interface SkillsStoreState {
  /** 所有 Skills 列表 */
  skills: Skill[];
  /** 加载状态 */
  isLoading: boolean;
  /** 错误信息 */
  error: string | null;

  // 操作
  setSkills: (skills: Skill[]) => void;
  createSkill: (skill: Skill) => void;
  updateSkill: (skill: Skill) => void;
  deleteSkill: (skillId: string) => void;
  importSkills: (skills: Skill[]) => void;
  exportSkills: () => Skill[];
  /** 按分类过滤 */
  getSkillsByCategory: (category: SkillCategory) => Skill[];
  /** 刷新数据（从后端加载） */
  refreshSkills: () => Promise<void>;
}

export const useSkillsStore = create<SkillsStoreState>((set, get) => ({
  skills: [],
  isLoading: false,
  error: null,

  setSkills: (skills) => set({ skills }),

  createSkill: (skill) => set((state) => ({
    skills: [...state.skills, skill]
  })),

  updateSkill: (skill) => set((state) => ({
    skills: state.skills.map(s => s.id === skill.id ? skill : s)
  })),

  deleteSkill: (skillId) => set((state) => ({
    skills: state.skills.filter(s => s.id !== skillId)
  })),

  importSkills: (imported) => set((state) => ({
    skills: [...state.skills, ...imported]
  })),

  exportSkills: () => get().skills,

  getSkillsByCategory: (category) =>
    get().skills.filter(s => s.category === category),

  refreshSkills: async () => {
    set({ isLoading: true, error: null });
    try {
      const skills = await window.ccBackend?.getSkills() ?? [];
      set({ skills, isLoading: false });
    } catch (error) {
      set({ error: String(error), isLoading: false });
    }
  }
}));
```

---

### agentsStore

```typescript
// src/shared/stores/agentsStore.ts
import { create } from 'zustand';
import type { Agent, AgentMode } from '@/shared/types';

interface AgentsStoreState {
  /** 所有 Agents 列表 */
  agents: Agent[];
  /** 正在运行的 Agent ID 集合 */
  runningAgentIds: Set<string>;
  /** 加载状态 */
  isLoading: boolean;
  /** 错误信息 */
  error: string | null;

  // 操作
  setAgents: (agents: Agent[]) => void;
  createAgent: (agent: Agent) => void;
  updateAgent: (agent: Agent) => void;
  deleteAgent: (agentId: string) => void;
  /** 标记 Agent 为运行中 */
  startAgent: (agentId: string) => void;
  /** 标记 Agent 为已停止 */
  stopAgent: (agentId: string) => void;
  /** 按模式过滤 */
  getAgentsByMode: (mode: AgentMode) => Agent[];
  /** 刷新数据（从后端加载） */
  refreshAgents: () => Promise<void>;
}

export const useAgentsStore = create<AgentsStoreState>((set, get) => ({
  agents: [],
  runningAgentIds: new Set(),
  isLoading: false,
  error: null,

  setAgents: (agents) => set({ agents }),

  createAgent: (agent) => set((state) => ({
    agents: [...state.agents, agent]
  })),

  updateAgent: (agent) => set((state) => ({
    agents: state.agents.map(a => a.id === agent.id ? agent : a)
  })),

  deleteAgent: (agentId) => set((state) => ({
    agents: state.agents.filter(a => a.id !== agentId)
  })),

  startAgent: (agentId) => set((state) => {
    const newSet = new Set(state.runningAgentIds);
    newSet.add(agentId);
    return { runningAgentIds: newSet };
  }),

  stopAgent: (agentId) => set((state) => {
    const newSet = new Set(state.runningAgentIds);
    newSet.delete(agentId);
    return { runningAgentIds: newSet };
  }),

  getAgentsByMode: (mode) =>
    get().agents.filter(a => a.mode === mode),

  refreshAgents: async () => {
    set({ isLoading: true, error: null });
    try {
      const agents = await window.ccBackend?.getAgents() ?? [];
      set({ agents, isLoading: false });
    } catch (error) {
      set({ error: String(error), isLoading: false });
    }
  }
}));
```

---

### mcpStore

```typescript
// src/shared/stores/mcpStore.ts
import { create } from 'zustand';
import type { McpServer, McpConnectionStatus } from '@/shared/types';

interface McpStoreState {
  /** MCP 服务器列表 */
  servers: McpServer[];
  /** 各服务器连接状态 (serverId -> status) */
  connectionStatuses: Record<string, McpConnectionStatus>;
  /** 加载状态 */
  isLoading: boolean;
  /** 错误信息 */
  error: string | null;

  // 操作
  setServers: (servers: McpServer[]) => void;
  saveServer: (server: McpServer) => void;
  deleteServer: (serverId: string) => void;
  /** 启动服务器（更新状态为 connected） */
  startServer: (serverId: string) => void;
  /** 停止服务器（更新状态为 disconnected） */
  stopServer: (serverId: string) => void;
  /** 测试连接（更新状态） */
  testConnection: (serverId: string) => void;
  /** 更新单个服务器连接状态 */
  setConnectionStatus: (serverId: string, status: McpConnectionStatus) => void;
  /** 刷新数据（从后端加载） */
  refreshServers: () => Promise<void>;
}

export const useMcpStore = create<McpStoreState>((set, get) => ({
  servers: [],
  connectionStatuses: {},
  isLoading: false,
  error: null,

  setServers: (servers) => set({ servers }),

  saveServer: (server) => set((state) => {
    const exists = state.servers.some(s => s.id === server.id);
    return {
      servers: exists
        ? state.servers.map(s => s.id === server.id ? server : s)
        : [...state.servers, server]
    };
  }),

  deleteServer: (serverId) => set((state) => ({
    servers: state.servers.filter(s => s.id !== serverId)
  })),

  startServer: (serverId) => set((state) => ({
    connectionStatuses: {
      ...state.connectionStatuses,
      [serverId]: 'connected' as McpConnectionStatus
    }
  })),

  stopServer: (serverId) => set((state) => ({
    connectionStatuses: {
      ...state.connectionStatuses,
      [serverId]: 'disconnected' as McpConnectionStatus
    }
  })),

  testConnection: (serverId) => set((state) => ({
    connectionStatuses: {
      ...state.connectionStatuses,
      [serverId]: 'connected' as McpConnectionStatus
    }
  })),

  setConnectionStatus: (serverId, status) => set((state) => ({
    connectionStatuses: {
      ...state.connectionStatuses,
      [serverId]: status
    }
  })),

  refreshServers: async () => {
    set({ isLoading: true, error: null });
    try {
      const servers = await window.ccBackend?.getMcpServers() ?? [];
      set({ servers, isLoading: false });
    } catch (error) {
      set({ error: String(error), isLoading: false });
    }
  }
}));
```

---

## 5. 任务依赖与执行顺序

```
T5.1 Skills/Agents管理器 (Week 12)
├── T5-W12-01 Skills管理器UI          ← 依赖 Phase1的JavaBridge + Phase2的UI组件
├── T5-W12-02 SkillCard组件           ← 依赖 T5-W12-01
├── T5-W12-03 SkillEditor表单         ← 依赖 T5-W12-01
├── T5-W12-04 Agents管理器UI          ← 依赖 Phase1的JavaBridge
├── T5-W12-05 AgentCard组件           ← 依赖 T5-W12-04
└── T5-W12-06 AgentEditor表单         ← 依赖 T5-W12-04

T5.2 MCP服务器管理器 (Week 13)
├── T5-W13-01 MCP服务器管理器UI       ← 依赖 Phase1的JavaBridge
├── T5-W13-02 McpServerCard组件       ← 依赖 T5-W13-01
├── T5-W13-03 服务器配置表单          ← 依赖 T5-W13-01
├── T5-W13-04 连接状态指示器          ← 依赖 T5-W13-01 + Phase1的EventBus
├── T5-W13-05 测试连接功能            ← 依赖 T5-W13-01
└── T5-W13-06 作用域管理UI            ← 依赖 Phase1的类型定义
```

**关键路径**: T5-W12-01 → T5-W12-03 → T5-W13-01 → T5-W13-03

---

## 6. 验收标准

### 功能验收
- [ ] Skills CRUD完整（创建/编辑/删除/导入/导出）
- [ ] Agents CRUD完整（创建/编辑/删除/启动/停止）
- [ ] MCP服务器 CRUD完整（创建/编辑/删除/启动/停止/测试连接）
- [ ] 作用域管理正确（全局/项目/会话隔离）
- [ ] 分类过滤正常

### 性能验收
- [ ] Skills列表渲染 < 200ms
- [ ] Agents启动/停止响应 < 500ms
- [ ] MCP连接测试 < 3s

### 代码质量验收
- [ ] 所有操作通过JavaBridge调用后端
- [ ] TypeScript类型完整
- [ ] 错误处理完善

---

## 7. 文件清单汇总

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `src/features/skills/components/SkillsManager.tsx` | Skills管理器 |
| `src/features/skills/components/SkillsList.tsx` | Skills列表 |
| `src/features/skills/components/SkillCard.tsx` | Skill卡片 |
| `src/features/skills/components/SkillEditor.tsx` | Skill编辑器 |
| `src/features/skills/components/SkillImportDialog.tsx` | Skill导入对话框 |
| `src/features/agents/components/AgentsManager.tsx` | Agents管理器 |
| `src/features/agents/components/AgentsList.tsx` | Agents列表 |
| `src/features/agents/components/AgentCard.tsx` | Agent卡片 |
| `src/features/agents/components/AgentEditor.tsx` | Agent编辑器 |
| `src/features/mcp/components/McpServerManager.tsx` | MCP服务器管理器 |
| `src/features/mcp/components/McpServerList.tsx` | MCP服务器列表 |
| `src/features/mcp/components/McpServerCard.tsx` | MCP服务器卡片 |
| `src/features/mcp/components/McpServerConfig.tsx` | MCP服务器配置表单 |
| `src/features/mcp/components/ConnectionStatus.tsx` | 连接状态指示器 |
| `src/features/mcp/components/ScopeManager.tsx` | 作用域管理器 |
| `src/shared/stores/skillsStore.ts` | Skills状态Store |
| `src/shared/stores/agentsStore.ts` | Agents状态Store |
| `src/shared/stores/mcpStore.ts` | MCP状态Store |

---

## 8. 相关文档

- [总览](./00-overview.md)
- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 4: 会话管理](./04-phase4-session.md)
- [Phase 6: 性能优化](./06-phase6-optimization.md)
- [后端Phase 5: 生态集成](../backend/05-phase5-ecosystem.md) ← SkillsManager/AgentsManager/McpServerManager
