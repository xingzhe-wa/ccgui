/**
 * English Language Pack
 */

export const enUS = {
  // Common
  common: {
    loading: 'Loading...',
    saving: 'Saving...',
    save: 'Save',
    cancel: 'Cancel',
    confirm: 'Confirm',
    delete: 'Delete',
    edit: 'Edit',
    copy: 'Copy',
    send: 'Send',
    retry: 'Retry',
    settings: 'Settings',
    search: 'Search',
    clear: 'Clear',
    refresh: 'Refresh',
    enable: 'Enable',
    disable: 'Disable',
    add: 'Add',
    remove: 'Remove',
    close: 'Close',
    back: 'Back',
    next: 'Next',
    previous: 'Previous',
    submit: 'Submit',
    reset: 'Reset',
    import: 'Import',
    export: 'Export',
    upload: 'Upload',
    download: 'Download',
    new: 'New',
    rename: 'Rename',
    duplicate: 'Duplicate',
    configure: 'Configure',
    execute: 'Execute',
    stop: 'Stop',
    start: 'Start',
  },

  // App
  app: {
    name: 'CC Assistant',
    description: 'AI-Powered Coding Assistant',
    renderError: 'Application render error',
    unknownError: 'Unknown error',
  },

  // Settings
  settings: {
    title: 'Settings',
    tabs: {
      model: 'Model Configuration',
      theme: 'Theme Settings',
      about: 'About',
    },
    theme: {
      preset: 'Preset Themes',
      brightness: 'Brightness',
      saturation: 'Saturation',
      custom: 'Custom Theme',
    },
    about: {
      version: 'Version',
      basedOn: 'Built with Claude Agent SDK',
    },
  },

  // Chat
  chat: {
    placeholder: 'Type a message...',
    noSession: 'Please create or select a session first...',
    sendMessage: 'Send message',
    thinking: 'Thinking...',
    error: 'Failed to send message',
    newSession: 'New Session',
    switchSession: 'Switch Session',
    sessionHistory: 'Session History',
    quickActions: 'Quick Actions',
    optimize: 'Optimize',
    optimizeComplete: 'Optimization complete',
    confidence: 'Confidence',
    removeReference: 'Remove reference',
    attach: 'Attach',
    enterToSend: 'Send',
  },

  // Conversation Modes
  mode: {
    auto: {
      label: 'AUTO',
      shortDesc: 'Smart Auto',
      title: 'AUTO Smart Mode',
      description: 'Smart auto mode that automatically selects the appropriate model based on task complexity. Suitable for daily Q&A and simple tasks with quick response.',
    },
    thinking: {
      label: 'THINK',
      shortDesc: 'Deep Thinking',
      title: 'THINKING Deep Mode',
      description: 'Deep thinking mode that uses Opus model for multi-step reasoning. Suitable for complex problems, code debugging, and architecture design with detailed analysis process.',
    },
    planning: {
      label: 'PLAN',
      shortDesc: 'Plan First',
      title: 'PLANNING Mode',
      description: 'Plan-first mode that creates an execution plan before implementation. Suitable for large refactoring, multi-file modifications, and complex tasks to avoid detours.',
    },
  },

  // Message Actions
  message: {
    reply: 'Reply',
    copy: 'Copy',
    delete: 'Delete',
    quote: 'Quote',
    reference: 'Quote this message',
  },

  // Agents
  agents: {
    title: 'Agents',
    search: 'Search Agents...',
    new: 'New Agent',
    edit: 'Edit Agent',
    delete: 'Delete Agent',
    duplicate: 'Duplicate Agent',
    enable: 'Enable Agent',
    disable: 'Disable Agent',
    enabled: 'Enabled',
    disabled: 'Disabled',
    start: 'Start',
    stop: 'Stop',
    configure: 'Configure',
    noDescription: 'No description',
    capabilities: {
      codeGeneration: 'Code Generation',
      codeReview: 'Code Review',
      refactoring: 'Refactoring',
      testing: 'Testing',
      documentation: 'Documentation',
      debugging: 'Debugging',
      fileOperation: 'File Operation',
      terminalOperation: 'Terminal Operation',
    },
    modes: {
      cautious: 'Cautious Mode - Suggestions only',
      balanced: 'Balanced Mode - Suggestions with low-risk auto-execution',
      aggressive: 'Aggressive Mode - Auto-execute high-risk operations',
    },
    scopes: {
      global: 'Global',
      project: 'Project',
      session: 'Session',
    },
    form: {
      name: 'Name',
      namePlaceholder: 'Enter agent name',
      nameRequired: 'Name cannot be empty',
      description: 'Description',
      descriptionPlaceholder: 'Enter agent description',
      systemPrompt: 'System Prompt',
      systemPromptPlaceholder: 'Enter agent system prompt...',
      systemPromptRequired: 'System prompt cannot be empty',
      capabilities: 'Capabilities',
      capabilitiesRequired: 'Please select at least one capability',
      mode: 'Mode',
      scope: 'Scope',
      constraints: 'Constraints',
      constraintsPlaceholder: 'Constraint description',
    },
  },

  // Skills
  skills: {
    title: 'Skills',
    search: 'Search skills...',
    new: 'New Skill',
    edit: 'Edit Skill',
    delete: 'Delete Skill',
    duplicate: 'Duplicate Skill',
    execute: 'Execute',
    noDescription: 'No description',
    categories: {
      codeGeneration: 'Code Generation',
      codeReview: 'Code Review',
      refactoring: 'Refactoring',
      testing: 'Testing',
      documentation: 'Documentation',
      debugging: 'Debugging',
      performance: 'Performance',
    },
  },

  // Model Configuration
  model: {
    title: 'Model Configuration',
    provider: 'Provider',
    model: 'Model',
    defaultModel: 'Default Model',
    newProfile: 'New Profile',
    editProfile: 'Edit Profile',
    deleteProfile: 'Delete Profile',
    activate: 'Activate',
    convertToLocal: 'Convert to local config',
    noConfig: 'Click "New Profile" button to create the first configuration',
    specialProviders: {
      localSettings: 'Use ~/.claude/settings.json configuration',
      cliLogin: 'Use Claude CLI native OAuth login',
      disabled: 'Disable Claude provider',
    },
    confirm: {
      enableLocalSettings: {
        title: 'Enable Local Settings',
        description: 'Will directly use ~/.claude/settings.json configuration, plugin-managed configuration will be overridden.',
        detail: 'After enabling, all provider configurations will be read from the local settings.json file.',
        confirm: 'Authorize and Enable',
      },
      disableLocalSettings: {
        title: 'Revoke Local Settings Authorization',
        description: 'Will stop using ~/.claude/settings.json configuration and switch to plugin-managed configuration.',
        confirm: 'Revoke Authorization',
      },
      enableCliLogin: {
        title: 'Enable CLI Login',
        description: 'Will use Claude CLI native OAuth login, no need to manually configure API Key.',
        detail: 'After enabling, authentication will be done through CLI OAuth login status.',
        confirm: 'Authorize and Enable',
      },
      disableCliLogin: {
        title: 'Revoke CLI Login Authorization',
        description: 'Will stop using Claude CLI OAuth login.',
        confirm: 'Revoke Authorization',
      },
    },
    form: {
      name: 'Profile Name',
      nameRequired: 'Please enter profile name',
      saveFailed: 'Save failed',
      provider: 'Provider',
      apiKey: 'API Key',
      baseUrl: 'Base URL',
      model: 'Model',
    },
  },

  // MCP Servers
  mcp: {
    title: 'MCP Servers',
    status: {
      connected: 'Connected',
      disconnected: 'Disconnected',
      connecting: 'Connecting...',
      error: 'Connection Error',
    },
    servers: {
      filesystem: 'File System',
      filesystemDescription: 'Provides file system operations',
    },
  },

  // Theme
  theme: {
    followIdea: 'Follow IDEA',
    light: 'Light',
    dark: 'Dark',
    highContrast: 'High Contrast',
  },

  // Tools
  tools: {
    skills: 'Skills Management',
    providers: 'Provider Configuration',
    theme: 'Theme Settings',
    about: 'About',
  },

  // Errors
  error: {
    loadFailed: 'Load failed',
    saveFailed: 'Save failed',
    deleteFailed: 'Delete failed',
    operationFailed: 'Operation failed',
    networkError: 'Network error',
    unknownError: 'Unknown error',
  },

  // Confirmation Dialog
  confirm: {
    title: 'Confirm',
    message: 'Are you sure you want to perform this operation?',
    delete: 'Are you sure you want to delete?',
  },
};

export type Translation = typeof enUS;
