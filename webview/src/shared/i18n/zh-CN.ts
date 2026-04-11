/**
 * 中文 (简体) 语言包
 */

export const zhCN = {
  // 通用
  common: {
    loading: '加载中...',
    saving: '保存中...',
    save: '保存',
    cancel: '取消',
    confirm: '确认',
    delete: '删除',
    edit: '编辑',
    copy: '复制',
    send: '发送',
    retry: '重试',
    settings: '设置',
    search: '搜索',
    clear: '清空',
    refresh: '刷新',
    enable: '启用',
    disable: '禁用',
    add: '添加',
    remove: '移除',
    close: '关闭',
    back: '返回',
    next: '下一步',
    previous: '上一步',
    submit: '提交',
    reset: '重置',
    import: '导入',
    export: '导出',
    upload: '上传',
    download: '下载',
    new: '新建',
    rename: '重命名',
    duplicate: '复制',
    configure: '配置',
    execute: '执行',
    stop: '停止',
    start: '启动',
  },

  // 应用
  app: {
    name: 'CC 助手',
    description: 'AI 编程助手',
    renderError: '应用渲染出错',
    unknownError: '未知错误',
  },

  // 设置页面
  settings: {
    title: '设置',
    tabs: {
      model: '模型配置',
      theme: '主题设置',
      about: '关于',
    },
    theme: {
      preset: '预设主题',
      brightness: '亮度',
      saturation: '饱和度',
      custom: '自定义主题',
    },
    about: {
      version: '版本',
      basedOn: '基于 Claude Agent SDK 构建',
    },
  },

  // 聊天
  chat: {
    placeholder: '输入消息...',
    noSession: '请先创建或选择一个会话...',
    sendMessage: '发送消息',
    thinking: '思考中...',
    error: '发送消息失败',
    newSession: '新建会话',
    switchSession: '切换会话',
    sessionHistory: '会话历史',
    quickActions: '快捷操作',
    optimize: '优化',
    optimizeComplete: '优化完成',
    confidence: '置信度',
    removeReference: '移除引用',
    attach: '附件',
    enterToSend: '发送',
  },

  // 对话模式
  mode: {
    auto: {
      label: 'AUTO',
      shortDesc: '智能自动',
      title: 'AUTO 智能模式',
      description: '智能自动模式，根据任务复杂度自动选择合适模型。适合日常问答和简单任务，快速响应。',
    },
    thinking: {
      label: 'THINK',
      shortDesc: '深度思考',
      title: 'THINKING 深度思考模式',
      description: '深度思考模式，使用 Opus 模型进行多步推理。适合复杂问题、代码调试和架构设计，提供更详细的分析过程。',
    },
    planning: {
      label: 'PLAN',
      shortDesc: '规划先行',
      title: 'PLANNING 规划模式',
      description: '规划先行模式，先制定执行计划再逐步实施。适合大型重构、多文件修改和复杂任务，避免走弯路。',
    },
  },

  // 消息操作
  message: {
    reply: '回复',
    copy: '复制',
    delete: '删除',
    quote: '引用',
    reference: '引用此消息',
  },

  // Agents
  agents: {
    title: 'Agents',
    search: '搜索 Agents...',
    new: '新建 Agent',
    edit: '编辑 Agent',
    delete: '删除 Agent',
    duplicate: '复制 Agent',
    enable: '启用 Agent',
    disable: '禁用 Agent',
    enabled: '已启用',
    disabled: '已禁用',
    start: '启动',
    stop: '停止',
    configure: '配置',
    noDescription: '暂无描述',
    capabilities: {
      codeGeneration: '代码生成',
      codeReview: '代码审查',
      refactoring: '重构',
      testing: '测试',
      documentation: '文档',
      debugging: '调试',
      fileOperation: '文件操作',
      terminalOperation: '终端操作',
    },
    modes: {
      cautious: '谨慎模式 - 仅提供建议',
      balanced: '平衡模式 - 建议为主，低风险自动执行',
      aggressive: '激进模式 - 自动执行高风险操作',
    },
    scopes: {
      global: '全局',
      project: '项目',
      session: '会话',
    },
    form: {
      name: '名称',
      namePlaceholder: '输入 Agent 名称',
      nameRequired: '名称不能为空',
      description: '描述',
      descriptionPlaceholder: '输入 Agent 描述',
      systemPrompt: '系统提示词',
      systemPromptPlaceholder: '输入 Agent 的系统提示词...',
      systemPromptRequired: '系统提示词不能为空',
      capabilities: '能力',
      capabilitiesRequired: '请至少选择一个能力',
      mode: '模式',
      scope: '作用域',
      constraints: '约束',
      constraintsPlaceholder: '约束描述',
    },
  },

  // Skills
  skills: {
    title: '技能',
    search: '搜索技能...',
    new: '新建技能',
    edit: '编辑技能',
    delete: '删除技能',
    duplicate: '复制技能',
    execute: '执行',
    noDescription: '暂无描述',
    categories: {
      codeGeneration: '代码生成',
      codeReview: '代码审查',
      refactoring: '重构',
      testing: '测试',
      documentation: '文档',
      debugging: '调试',
      performance: '性能优化',
    },
  },

  // 模型配置
  model: {
    title: '模型配置',
    provider: '供应商',
    model: '模型',
    defaultModel: '默认模型',
    newProfile: '新建配置',
    editProfile: '编辑配置',
    deleteProfile: '删除配置',
    activate: '激活',
    convertToLocal: '转换为本地配置',
    noConfig: '点击"新建配置"按钮创建第一个配置',
    specialProviders: {
      localSettings: '使用 ~/.claude/settings.json 配置',
      cliLogin: '使用 Claude CLI 原生 OAuth 登录',
      disabled: '禁用 Claude 供应商',
    },
    confirm: {
      enableLocalSettings: {
        title: '启用 Local Settings',
        description: '将直接使用 ~/.claude/settings.json 配置，插件管理的配置将被覆盖。',
        detail: '启用后，所有供应商配置将从本地 settings.json 文件读取。',
        confirm: '授权并启用',
      },
      disableLocalSettings: {
        title: '撤销 Local Settings 授权',
        description: '将停止使用 ~/.claude/settings.json 配置，切换到插件管理的配置。',
        confirm: '撤销授权',
      },
      enableCliLogin: {
        title: '启用 CLI Login',
        description: '将使用 Claude CLI 原生 OAuth 登录，无需手动配置 API Key。',
        detail: '启用后，将通过 CLI 的 OAuth 登录状态进行认证。',
        confirm: '授权并启用',
      },
      disableCliLogin: {
        title: '撤销 CLI Login 授权',
        description: '将停止使用 Claude CLI OAuth 登录。',
        confirm: '撤销授权',
      },
    },
    form: {
      name: '配置名称',
      nameRequired: '请输入配置名称',
      saveFailed: '保存失败',
      provider: '供应商',
      apiKey: 'API 密钥',
      baseUrl: '基础 URL',
      model: '模型',
    },
  },

  // MCP 服务器
  mcp: {
    title: 'MCP 服务器',
    status: {
      connected: '已连接',
      disconnected: '未连接',
      connecting: '连接中...',
      error: '连接错误',
    },
    servers: {
      filesystem: '文件系统',
      filesystemDescription: '提供文件系统操作能力',
    },
  },

  // 主题
  theme: {
    followIdea: '跟随 IDEA',
    light: '浅色',
    dark: '深色',
    highContrast: '高对比度',
  },

  // 工具
  tools: {
    skills: '技能管理',
    providers: '供应商配置',
    theme: '主题设置',
    about: '关于',
  },

  // 错误
  error: {
    loadFailed: '加载失败',
    saveFailed: '保存失败',
    deleteFailed: '删除失败',
    operationFailed: '操作失败',
    networkError: '网络错误',
    unknownError: '未知错误',
  },

  // 确认对话框
  confirm: {
    title: '确认',
    message: '确定要执行此操作吗？',
    delete: '确定要删除吗？',
  },
};

export type Translation = typeof zhCN;
