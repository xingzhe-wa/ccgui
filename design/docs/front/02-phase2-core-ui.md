# Phase 2: 核心UI组件 (Core UI)

**优先级**: P0
**预估工期**: 22.5人天 (4.5周)
**前置依赖**: Phase 1 全部完成（JCEF通信/Store框架/路由系统可用）
**阶段目标**: 主题系统可用，消息列表可渲染Markdown，输入组件支持附件

---

## 1. 阶段概览

本阶段在Phase 1通信基础之上，构建完整的用户可见UI层：

1. 6套预设主题 + 主题编辑器（CSS变量方案，切换延迟<100ms）
2. 响应式布局框架（ResizeObserver + 断点设计）
3. 消息列表 + Markdown渲染 + 代码高亮
4. 虚拟滚动支持（1000+消息无卡顿）
5. 输入组件 + 附件拖拽 + 发送快捷键

**完成标志**: 完整的聊天UI可正常显示消息、发送消息、切换主题

---

## 2. 任务清单

### Week 4: 主题系统与布局框架

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T2-W4-01** | 6套预设主题CSS变量定义 | 1人天 | themes/*.css | 所有主题可正常应用 |
| **T2-W4-02** | ThemeConfig数据结构 | 0.5人天 | theme.ts类型 | TypeScript无报错 |
| **T2-W4-03** | 主题切换器组件 | 1人天 | ThemeSwitcher.tsx | 切换延迟<100ms |
| **T2-W4-04** | 主题编辑器UI | 2人天 | ThemeEditor.tsx | 可自定义主题 |
| **T2-W4-05** | 响应式布局实现 | 1.5人天 | ResponsiveLayout.tsx | 支持800-1200px |
| **T2-W4-06** | ChatLayout主布局 | 1.5人天 | ChatLayout.tsx | 布局正确显示 |

### Week 5: 消息组件与Markdown渲染

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T2-W5-01** | MessageItem基础组件 | 1人天 | MessageItem.tsx | 消息正确显示 |
| **T2-W5-02** | react-markdown集成 | 1人天 | MarkdownRenderer.tsx | 支持GFM语法 |
| **T2-W5-03** | highlight.js代码高亮 | 1.5人天 | CodeBlock.tsx | 支持20+语言 |
| **T2-W5-04** | KaTeX公式渲染 | 1人天 | LatexRenderer.tsx | 公式显示正确 |
| **T2-W5-05** | MessageList虚拟滚动 | 2人天 | MessageList.tsx | 支持1000+消息 |
| **T2-W5-06** | 消息操作菜单 | 1人天 | MessageActions.tsx | 操作正常 |

### Week 6: 输入组件与附件处理

| 任务ID | 任务描述 | 工作量 | 产出物 | 验收标准 |
|--------|----------|--------|--------|----------|
| **T2-W6-01** | ChatInput组件 | 1.5人天 | ChatInput.tsx | 输入正常 |
| **T2-W6-02** | TextInput自动高度 | 1人天 | AutoResizeTextarea.tsx | 自动调整高度 |
| **T2-W6-03** | 附件拖拽处理 | 1.5人天 | AttachmentDropZone.tsx | 拖拽正常 |
| **T2-W6-04** | 图片预览组件 | 1人天 | ImagePreview.tsx | 预览正常 |
| **T2-W6-05** | 发送按钮与快捷键 | 1人天 | SendButton.tsx | Enter发送正常 |
| **T2-W6-06** | 输入框工具栏 | 1人天 | InputToolbar.tsx | 工具栏正常 |

---

## 🎨 Week 4: 主题系统与布局框架

### T2-W4-01: 6套预设主题CSS变量定义

**任务描述**: 定义6套预设主题的CSS变量

**实现代码**:

```css
/* src/styles/themes/jetbrains-dark.css */
[data-theme="jetbrains-dark"] {
  --primary: 217 91% 60%;
  --primary-foreground: 0 0% 100%;
  --background: 222 47% 11%;
  --foreground: 210 40% 98%;
  --muted: 217 33% 17%;
  --muted-foreground: 215 20% 65%;
  --accent: 217 33% 17%;
  --accent-foreground: 210 40% 98%;
  --destructive: 0 62% 30%;
  --border: 217 33% 17%;
  --user-message: 217 91% 60%;
  --ai-message: 217 33% 17%;
  --system-message: 38 92% 50%;
  --code-background: 0 0% 15%;
  --code-foreground: 210 40% 98%;
}

/* src/styles/themes/github-dark.css */
[data-theme="github-dark"] {
  --primary: 210 100% 60%;
  --primary-foreground: 0 0% 100%;
  --background: 214 14% 10%;
  --foreground: 210 11% 85%;
  --muted: 214 14% 15%;
  --muted-foreground: 210 11% 60%;
  --accent: 214 14% 15%;
  --accent-foreground: 210 11% 85%;
  --destructive: 0 62% 45%;
  --border: 214 14% 20%;
  --user-message: 210 100% 60%;
  --ai-message: 214 14% 15%;
  --system-message: 45 93% 47%;
  --code-background: 0 0% 0%;
  --code-foreground: 210 11% 85%;
}

/* src/styles/themes/vscode-dark.css */
[data-theme="vscode-dark"] {
  --primary: 207 82% 66%;
  --primary-foreground: 0 0% 100%;
  --background: 222 47% 10%;
  --foreground: 210 11% 85%;
  --muted: 222 47% 15%;
  --muted-foreground: 210 11% 60%;
  --accent: 222 47% 15%;
  --accent-foreground: 210 11% 85%;
  --destructive: 0 62% 50%;
  --border: 222 47% 20%;
  --user-message: 207 82% 66%;
  --ai-message: 222 47% 15%;
  --system-message: 35 92% 50%;
  --code-background: 0 0% 12%;
  --code-foreground: 210 11% 85%;
}

/* src/styles/themes/monokai.css */
[data-theme="monokai"] {
  --primary: 40 100% 50%;
  --primary-foreground: 0 0% 0%;
  --background: 30 10% 8%;
  --foreground: 30 5% 85%;
  --muted: 30 10% 15%;
  --muted-foreground: 30 5% 60%;
  --accent: 30 10% 15%;
  --accent-foreground: 30 5% 85%;
  --destructive: 0 80% 50%;
  --border: 30 10% 20%;
  --user-message: 40 100% 50%;
  --ai-message: 30 10% 15%;
  --system-message: 120 60% 50%;
  --code-background: 0 0% 10%;
  --code-foreground: 30 5% 90%;
}

/* src/styles/themes/solarized-light.css */
[data-theme="solarized-light"] {
  --primary: 205 69% 42%;
  --primary-foreground: 0 0% 100%;
  --background: 44 100% 99%;
  --foreground: 192 15% 20%;
  --muted: 44 20% 95%;
  --muted-foreground: 192 10% 50%;
  --accent: 44 20% 95%;
  --accent-foreground: 192 15% 20%;
  --destructive: 1 71% 50%;
  --border: 192 20% 85%;
  --user-message: 205 69% 42%;
  --ai-message: 44 20% 95%;
  --system-message: 45 90% 45%;
  --code-background: 192 15% 92%;
  --code-foreground: 192 15% 25%;
}

/* src/styles/themes/nord.css */
[data-theme="nord"] {
  --primary: 213 35% 65%;
  --primary-foreground: 0 0% 100%;
  --background: 222 47% 11%;
  --foreground: 218 23% 85%;
  --muted: 220 25% 18%;
  --muted-foreground: 218 15% 55%;
  --accent: 220 25% 18%;
  --accent-foreground: 218 23% 85%;
  --destructive: 350 70% 55%;
  --border: 220 25% 23%;
  --user-message: 213 35% 65%;
  --ai-message: 220 25% 18%;
  --system-message: 210 40% 60%;
  --code-background: 220 25% 15%;
  --code-foreground: 218 23% 85%;
}
```

**主题常量定义**:

```typescript
// src/shared/constants/themes.ts
import type { ThemeConfig } from '@/shared/types';
import { ThemePresets } from '@/shared/types';

export const ThemePresetConfigs: Record<ThemePresets, ThemeConfig> = {
  'jetbrains-dark': {
    id: 'jetbrains-dark',
    name: 'JetBrains Dark',
    isDark: true,
    colors: {
      primary: '#3B82F6',
      background: '#1E1E1E',
      foreground: '#F3F4F6',
      muted: '#2D2D2D',
      mutedForeground: '#9CA3AF',
      accent: '#2D2D2D',
      accentForeground: '#F3F4F6',
      destructive: '#DC2626',
      border: '#2D2D2D',
      userMessage: '#3B82F6',
      aiMessage: '#2D2D2D',
      systemMessage: '#F59E0B',
      codeBackground: '#0D0D0D',
      codeForeground: '#F3F4F6'
    },
    typography: {
      messageFont: 'Inter',
      codeFont: 'JetBrains Mono',
      fontSize: 14,
      fontSizeSmall: 12,
      fontSizeLarge: 16,
      lineHeight: 1.5
    },
    spacing: {
      messageSpacing: 16,
      codeBlockPadding: 12,
      headerHeight: 48,
      sidebarWidth: 280
    },
    borderRadius: {
      messageBubble: 8,
      codeBlock: 6,
      button: 6,
      input: 6,
      modal: 8
    },
    shadow: {
      sm: '0 1px 2px 0 rgb(0 0 0 / 0.05)',
      md: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
      lg: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
      xl: '0 20px 25px -5px rgb(0 0 0 / 0.1)'
    }
  },
  // ... 其他5套主题
};

export const ThemeList = Object.values(ThemePresetConfigs);
```

**验收标准**:
- ✅ 6套主题CSS变量定义完整
- ✅ 切换主题后立即生效
- ✅ 切换延迟 < 100ms
- ✅ 所有UI组件正确应用主题颜色

---

### T2-W4-03: 主题切换器组件

**实现代码**:

```typescript
// src/features/theme/components/ThemeSwitcher.tsx
import { useState } from 'react';
import { Dropdown, DropdownTrigger, DropdownContent, DropdownItem, DropdownLabel } from '@/shared/components/ui/dropdown';
import { Button } from '@/shared/components/ui/button';
import { useThemeStore } from '@/shared/stores/themeStore';
import { ThemePresetConfigs } from '@/shared/constants/themes';
import { ThemePresets } from '@/shared/types';
import { PaletteIcon, CheckIcon } from '@/shared/components/Icon';
import { cn } from '@/shared/utils/cn';

export const ThemeSwitcher = () => {
  const { theme, setTheme, customThemes } = useThemeStore();
  const [isOpen, setIsOpen] = useState(false);

  const handleThemeChange = (themeId: string) => {
    const newTheme = ThemePresetConfigs[themeId as ThemePresets] || customThemes.find(t => t.id === themeId);
    if (newTheme) {
      setTheme(newTheme);
    }
    setIsOpen(false);
  };

  return (
    <Dropdown open={isOpen} onOpenChange={setIsOpen}>
      <DropdownTrigger asChild>
        <Button variant="ghost" size="icon" className="h-8 w-8">
          <PaletteIcon className="h-4 w-4" />
        </Button>
      </DropdownTrigger>
      <DropdownContent align="end" className="w-56">
        <DropdownLabel>预设主题</DropdownLabel>
        {Object.values(ThemePresetConfigs).map((preset) => (
          <DropdownItem
            key={preset.id}
            onClick={() => handleThemeChange(preset.id)}
            className="flex items-center gap-2"
          >
            <div
              className="h-4 w-4 rounded-full border"
              style={{ backgroundColor: preset.colors.primary }}
            />
            <span className="flex-1">{preset.name}</span>
            {theme.id === preset.id && (
              <CheckIcon className="h-4 w-4 text-primary" />
            )}
          </DropdownItem>
        ))}
        
        {customThemes.length > 0 && (
          <>
            <DropdownLabel className="mt-2">自定义主题</DropdownLabel>
            {customThemes.map((custom) => (
              <DropdownItem
                key={custom.id}
                onClick={() => handleThemeChange(custom.id)}
                className="flex items-center gap-2"
              >
                <div
                  className="h-4 w-4 rounded-full border"
                  style={{ backgroundColor: custom.colors.primary }}
                />
                <span className="flex-1">{custom.name}</span>
                {theme.id === custom.id && (
                  <CheckIcon className="h-4 w-4 text-primary" />
                )}
              </DropdownItem>
            ))}
          </>
        )}
      </DropdownContent>
    </Dropdown>
  );
};
```

---

### T2-W4-04: 主题编辑器UI

**实现代码**:

```typescript
// src/features/theme/components/ThemeEditor.tsx
import { useState, useEffect } from 'react';
import { useThemeStore } from '@/shared/stores/themeStore';
import { Button } from '@/shared/components/ui/button';
import { Input } from '@/shared/components/ui/input';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/shared/components/ui/tabs';
import { ColorPicker } from './ColorPicker';
import { FontSelector } from './FontSelector';
import { Slider } from './Slider';
import { SaveIcon, UndoIcon } from '@/shared/components/Icon';

export const ThemeEditor = () => {
  const { theme, setTheme, saveCustomTheme, isEditing, startEditing, finishEditing } = useThemeStore();
  const [editingTheme, setEditingTheme] = useState({ ...theme });
  const [hasChanges, setHasChanges] = useState(false);

  useEffect(() => {
    setHasChanges(JSON.stringify(editingTheme) !== JSON.stringify(theme));
  }, [editingTheme, theme]);

  const handleColorChange = (colorKey: string, value: string) => {
    setEditingTheme((prev) => ({
      ...prev,
      colors: { ...prev.colors, [colorKey]: value }
    }));
  };

  const handleTypographyChange = (key: string, value: string | number) => {
    setEditingTheme((prev) => ({
      ...prev,
      typography: { ...prev.typography, [key]: value }
    }));
  };

  const handleSpacingChange = (key: string, value: number) => {
    setEditingTheme((prev) => ({
      ...prev,
      spacing: { ...prev.spacing, [key]: value }
    }));
  };

  const handleSave = () => {
    saveCustomTheme({
      ...editingTheme,
      id: `custom-${Date.now()}`,
      name: `${editingTheme.name} (Copy)`
    });
    setTheme(editingTheme);
    finishEditing();
  };

  const handleReset = () => {
    setEditingTheme({ ...theme });
  };

  const handlePreview = () => {
    setTheme(editingTheme);
  };

  return (
    <div className="p-6">
      {/* 头部 */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">主题编辑器</h2>
          <p className="text-muted-foreground">自定义你的界面外观</p>
        </div>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={handleReset} disabled={!hasChanges}>
            <UndoIcon className="mr-2 h-4 w-4" />
            重置
          </Button>
          <Button variant="outline" onClick={handlePreview} disabled={!hasChanges}>
            预览
          </Button>
          <Button onClick={handleSave} disabled={!hasChanges}>
            <SaveIcon className="mr-2 h-4 w-4" />
            保存
          </Button>
        </div>
      </div>

      {/* 主题编辑器主体 */}
      <Tabs defaultValue="colors" className="w-full">
        <TabsList className="grid w-full grid-cols-5">
          <TabsTrigger value="colors">颜色</TabsTrigger>
          <TabsTrigger value="typography">字体</TabsTrigger>
          <TabsTrigger value="spacing">间距</TabsTrigger>
          <TabsTrigger value="radius">圆角</TabsTrigger>
          <TabsTrigger value="shadow">阴影</TabsTrigger>
        </TabsList>

        {/* 颜色设置 */}
        <TabsContent value="colors" className="space-y-6">
          <div className="grid grid-cols-2 gap-6">
            {/* 主色调 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">主色调</label>
              <ColorPicker
                value={editingTheme.colors.primary}
                onChange={(value) => handleColorChange('primary', value)}
              />
            </div>

            {/* 背景色 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">背景色</label>
              <ColorPicker
                value={editingTheme.colors.background}
                onChange={(value) => handleColorChange('background', value)}
              />
            </div>

            {/* 用户消息色 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">用户消息色</label>
              <ColorPicker
                value={editingTheme.colors.userMessage}
                onChange={(value) => handleColorChange('userMessage', value)}
              />
            </div>

            {/* AI消息色 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">AI消息色</label>
              <ColorPicker
                value={editingTheme.colors.aiMessage}
                onChange={(value) => handleColorChange('aiMessage', value)}
              />
            </div>

            {/* 代码块背景 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">代码块背景</label>
              <ColorPicker
                value={editingTheme.colors.codeBackground}
                onChange={(value) => handleColorChange('codeBackground', value)}
              />
            </div>

            {/* 边框色 */}
            <div className="space-y-2">
              <label className="text-sm font-medium">边框色</label>
              <ColorPicker
                value={editingTheme.colors.border}
                onChange={(value) => handleColorChange('border', value)}
              />
            </div>
          </div>
        </TabsContent>

        {/* 字体设置 */}
        <TabsContent value="typography" className="space-y-6">
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">消息字体</label>
              <FontSelector
                value={editingTheme.typography.messageFont}
                onChange={(value) => handleTypographyChange('messageFont', value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">代码字体</label>
              <FontSelector
                value={editingTheme.typography.codeFont}
                onChange={(value) => handleTypographyChange('codeFont', value)}
                monospace
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">字体大小: {editingTheme.typography.fontSize}px</label>
              <Slider
                min={10}
                max={18}
                step={1}
                value={editingTheme.typography.fontSize}
                onChange={(value) => handleTypographyChange('fontSize', value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">行高: {editingTheme.typography.lineHeight}</label>
              <Slider
                min={1.0}
                max={2.0}
                step={0.1}
                value={editingTheme.typography.lineHeight}
                onChange={(value) => handleTypographyChange('lineHeight', value)}
              />
            </div>
          </div>
        </TabsContent>

        {/* 间距设置 */}
        <TabsContent value="spacing" className="space-y-6">
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">消息间距: {editingTheme.spacing.messageSpacing}px</label>
              <Slider
                min={8}
                max={24}
                step={4}
                value={editingTheme.spacing.messageSpacing}
                onChange={(value) => handleSpacingChange('messageSpacing', value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">代码块内边距: {editingTheme.spacing.codeBlockPadding}px</label>
              <Slider
                min={8}
                max={16}
                step={4}
                value={editingTheme.spacing.codeBlockPadding}
                onChange={(value) => handleSpacingChange('codeBlockPadding', value)}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">头部栏高度: {editingTheme.spacing.headerHeight}px</label>
              <Slider
                min={40}
                max={60}
                step={4}
                value={editingTheme.spacing.headerHeight}
                onChange={(value) => handleSpacingChange('headerHeight', value)}
              />
            </div>
          </div>
        </TabsContent>

        {/* 圆角设置 */}
        <TabsContent value="radius" className="space-y-6">
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">消息气泡圆角: {editingTheme.borderRadius.messageBubble}px</label>
              <Slider
                min={4}
                max={16}
                step={2}
                value={editingTheme.borderRadius.messageBubble}
                onChange={(value) => setEditingTheme(prev => ({
                  ...prev,
                  borderRadius: { ...prev.borderRadius, messageBubble: value }
                }))}
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">代码块圆角: {editingTheme.borderRadius.codeBlock}px</label>
              <Slider
                min={4}
                max={12}
                step={2}
                value={editingTheme.borderRadius.codeBlock}
                onChange={(value) => setEditingTheme(prev => ({
                  ...prev,
                  borderRadius: { ...prev.borderRadius, codeBlock: value }
                }))}
              />
            </div>
          </div>
        </TabsContent>

        {/* 阴影设置 */}
        <TabsContent value="shadow" className="space-y-6">
          <div className="space-y-4">
            <div className="rounded-lg border p-4" style={{ boxShadow: editingTheme.shadow.sm }}>
              <span className="text-sm font-medium">小阴影</span>
            </div>
            <div className="rounded-lg border p-4" style={{ boxShadow: editingTheme.shadow.md }}>
              <span className="text-sm font-medium">中阴影</span>
            </div>
            <div className="rounded-lg border p-4" style={{ boxShadow: editingTheme.shadow.lg }}>
              <span className="text-sm font-medium">大阴影</span>
            </div>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
};
```

---

### T2-W4-05: 响应式布局实现

**实现代码**:

```typescript
// src/shared/components/layout/ResponsiveLayout.tsx
import { useEffect, useState, useRef } from 'react';
import { cn } from '@/shared/utils/cn';

export interface ResponsiveLayoutProps {
  children: React.ReactNode;
  className?: string;
}

export type LayoutMode = 'single' | 'split';
export type Breakpoint = 'sm' | 'md' | 'lg';

export const ResponsiveLayout = ({ children, className }: ResponsiveLayoutProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [layoutMode, setLayoutMode] = useState<LayoutMode>('split');
  const [breakpoint, setBreakpoint] = useState<Breakpoint>('lg');

  useEffect(() => {
    if (!containerRef.current) return;

    const resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const newWidth = entry.contentRect.width;
        setWidth(newWidth);

        // 计算断点
        let newBreakpoint: Breakpoint;
        if (newWidth < 800) {
          newBreakpoint = 'sm';
          setLayoutMode('single');
        } else if (newWidth < 1200) {
          newBreakpoint = 'md';
          setLayoutMode('split');
        } else {
          newBreakpoint = 'lg';
          setLayoutMode('split');
        }
        setBreakpoint(newBreakpoint);
      }
    });

    resizeObserver.observe(containerRef.current);

    return () => resizeObserver.disconnect();
  }, []);

  return (
    <div
      ref={containerRef}
      className={cn('h-full w-full', className)}
      data-layout-mode={layoutMode}
      data-breakpoint={breakpoint}
    >
      {typeof children === 'function'
        ? children({ width, layoutMode, breakpoint })
        : children
      }
    </div>
  );
};

// 使用示例
export const ChatLayout = () => {
  return (
    <ResponsiveLayout>
      {({ layoutMode, breakpoint }) => (
        <div className="flex h-full">
          {layoutMode === 'split' ? (
            <>
              {/* 聊天区域 */}
              <div className="w-3/5 min-w-[300px]">
                <ChatPanel />
              </div>
              
              {/* 预览区域 */}
              <div className="w-2/5 min-w-[250px] border-l">
                <PreviewPanel />
              </div>
            </>
          ) : (
            /* 单列模式 */
            <div className="w-full">
              <ChatPanel />
            </div>
          )}
        </div>
      )}
    </ResponsiveLayout>
  );
};
```

**验收标准**:
- ✅ < 800px: 单列布局
- ✅ 800-1200px: 分栏布局（60:40）
- ✅ > 1200px: 分栏布局（50:50）
- ✅ ResizeObserver正常工作
- ✅ 布局切换无闪烁

---

## 📝 Week 5: 消息组件与Markdown渲染

### T2-W5-01: MessageItem基础组件

**实现代码**:

```typescript
// src/features/chat/components/MessageItem.tsx
import { memo } from 'react';
import { MessageRole } from '@/shared/types';
import { useTheme } from '@/shared/hooks/useTheme';
import { MarkdownRenderer } from '@/shared/components/markdown/MarkdownRenderer';
import { MessageActions } from './MessageActions';
import { MessageAvatar } from './MessageAvatar';
import { MessageTimestamp } from './MessageTimestamp';
import { MessageReference } from './MessageReference';
import { cn } from '@/shared/utils/cn';

export interface MessageItemProps {
  id: string;
  role: MessageRole;
  content: string;
  timestamp: number;
  references?: Array<{
    messageId: string;
    excerpt: string;
    timestamp: number;
  }>;
  isStreaming?: boolean;
  onReply?: (messageId: string) => void;
  onCopy?: (content: string) => void;
  onDelete?: (messageId: string) => void;
  onReferenceClick?: (messageId: string) => void;
  className?: string;
}

export const MessageItem = memo(({
  id,
  role,
  content,
  timestamp,
  references,
  isStreaming = false,
  onReply,
  onCopy,
  onDelete,
  onReferenceClick,
  className
}: MessageItemProps) => {
  const { theme } = useTheme();
  const isUser = role === MessageRole.USER;

  return (
    <div
      className={cn(
        'group flex gap-3 px-4 py-3 transition-colors',
        'hover:bg-muted/50',
        isUser && 'flex-row-reverse',
        className
      )}
    >
      {/* 头像 */}
      <MessageAvatar role={role} />

      {/* 消息内容 */}
      <div className={cn('flex-1 space-y-2', isUser && 'items-end')}>
        {/* 发送者信息 */}
        <div className={cn('flex items-center gap-2', isUser && 'flex-row-reverse')}>
          <span className="text-sm font-medium">
            {isUser ? '你' : 'Claude'}
          </span>
          <MessageTimestamp timestamp={timestamp} />
        </div>

        {/* 引用内容 */}
        {references && references.length > 0 && (
          <div className="space-y-1">
            {references.map((ref) => (
              <MessageReference
                key={ref.messageId}
                excerpt={ref.excerpt}
                timestamp={ref.timestamp}
                onClick={() => onReferenceClick?.(ref.messageId)}
              />
            ))}
          </div>
        )}

        {/* 消息气泡 */}
        <div
          className={cn(
            'inline-block rounded-lg px-4 py-2 max-w-[80%]',
            isUser
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted text-foreground',
            isStreaming && 'animate-pulse'
          )}
        >
          <MarkdownRenderer content={content} />
          {isStreaming && <TypingCursor />}
        </div>

        {/* 操作按钮（悬浮显示） */}
        <MessageActions
          isVisible={!isUser}
          onReply={() => onReply?.(id)}
          onCopy={() => onCopy?.(content)}
          onDelete={() => onDelete?.(id)}
        />
      </div>
    </div>
  );
});

MessageItem.displayName = 'MessageItem';

// TypingCursor子组件
const TypingCursor = () => (
  <span className="ml-1 inline-block h-4 w-0.5 animate-pulse bg-current" />
);
```

---

### T2-W5-02: react-markdown集成

**实现代码**:

```typescript
// src/shared/components/markdown/MarkdownRenderer.tsx
import { memo, useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import { CodeBlock } from './CodeBlock';
import { LatexRenderer } from './LatexRenderer';
import { cn } from '@/shared/utils/cn';

export interface MarkdownRendererProps {
  content: string;
  className?: string;
}

// Markdown解析缓存
const markdownCache = new Map<string, React.ReactNode>();
const MAX_CACHE_SIZE = 100;

export const MarkdownRenderer = memo(({ content, className }: MarkdownRendererProps) => {
  const rendered = useMemo(() => {
    // 检查缓存
    const cached = markdownCache.get(content);
    if (cached) {
      return cached;
    }

    // 解析Markdown
    const element = (
      <div className={cn('prose dark:prose-invert max-w-none', className)}>
        <ReactMarkdown
          remarkPlugins={[remarkGfm, remarkMath]}
          rehypePlugins={[rehypeKatex]}
          components={{
            // 代码块渲染
            code: CodeBlock,

            // LaTeX公式渲染（行内）
            span: ({ node, inline, ...props }) => {
              if (inline && props.className?.includes('math-inline')) {
                return <LatexRenderer inline {...props} />;
              }
              return <span {...props} />;
            },

            // 图片渲染
            img: ({ src, alt, ...props }) => (
              <img
                src={src}
                alt={alt}
                loading="lazy"
                className="max-w-full h-auto rounded-lg"
                {...props}
              />
            ),

            // 链接渲染
            a: ({ href, children, ...props }) => (
              <a
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary hover:underline"
                {...props}
              >
                {children}
              </a>
            ),

            // 表格渲染
            table: ({ children }) => (
              <div className="overflow-x-auto my-4">
                <table className="min-w-full border-collapse border border-border">
                  {children}
                </table>
              </div>
            ),

            // 列表渲染
            ul: ({ children }) => (
              <ul className="my-2 list-disc list-inside">
                {children}
              </ul>
            ),

            ol: ({ children }) => (
              <ol className="my-2 list-decimal list-inside">
                {children}
              </ol>
            ),

            // 引用渲染
            blockquote: ({ children }) => (
              <blockquote className="my-2 border-l-4 border-primary pl-4 italic text-muted-foreground">
                {children}
              </blockquote>
            )
          }}
        >
          {content}
        </ReactMarkdown>
      </div>
    );

    // 缓存结果
    if (markdownCache.size >= MAX_CACHE_SIZE) {
      const firstKey = markdownCache.keys().next().value;
      markdownCache.delete(firstKey);
    }
    markdownCache.set(content, element);

    return element;
  }, [content, className]);

  return rendered;
});

MarkdownRenderer.displayName = 'MarkdownRenderer';

// 清除缓存
export function clearMarkdownCache() {
  markdownCache.clear();
}
```

---

### T2-W5-03: highlight.js代码高亮

**实现代码**:

```typescript
// src/shared/components/markdown/CodeBlock.tsx
import { memo, useEffect, useState, useRef } from 'react';
import { Button } from '@/shared/components/ui/button';
import { CopyIcon, CheckIcon } from '@/shared/components/Icon';
import { cn } from '@/shared/utils/cn';

export interface CodeBlockProps {
  language?: string;
  value: string;
  inline?: boolean;
}

export const CodeBlock = memo(({ language = 'text', value, inline }: CodeBlockProps) => {
  const [highlighted, setHighlighted] = useState('');
  const [copied, setCopied] = useState(false);
  const preRef = useRef<HTMLPreElement>(null);

  useEffect(() => {
    // 延迟高亮，避免阻塞渲染
    const timeout = setTimeout(async () => {
      try {
        const hljs = (await import('highlight.js')).default;
        const lang = language && hljs.getLanguage(language) ? language : 'plaintext';
        const result = hljs.highlight(value, { language: lang }).value;
        setHighlighted(result);
      } catch (error) {
        console.error('Highlight.js error:', error);
        setHighlighted(value);
      }
    }, 100);

    return () => clearTimeout(timeout);
  }, [value, language]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (error) {
      console.error('Copy failed:', error);
    }
  };

  if (inline) {
    return (
      <code className="px-1.5 py-0.5 rounded bg-muted text-sm font-mono">
        {highlighted || value}
      </code>
    );
  }

  return (
    <div className="group relative my-4">
      {/* 语言标签 */}
      {language && (
        <div className="absolute top-2 right-2 px-2 py-1 text-xs font-mono text-muted-foreground bg-muted rounded">
          {language}
        </div>
      )}

      {/* 复制按钮 */}
      <Button
        variant="ghost"
        size="icon"
        className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"
        onClick={handleCopy}
      >
        {copied ? (
          <CheckIcon className="h-4 w-4 text-green-500" />
        ) : (
          <CopyIcon className="h-4 w-4" />
        )}
      </Button>

      {/* 代码块 */}
      <pre
        ref={preRef}
        className={cn(
          'overflow-x-auto rounded-lg p-4 bg-code-background text-code-foreground text-sm',
          'border border-border'
        )}
      >
        <code
          className="font-mono"
          dangerouslySetInnerHTML={{ __html: highlighted || value }}
        />
      </pre>
    </div>
  );
});

CodeBlock.displayName = 'CodeBlock';
```

---

### T2-W5-05: MessageList虚拟滚动

**实现代码**:

```typescript
// src/features/chat/components/MessageList.tsx
import { useRef, useEffect } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useSessionStore } from '@/shared/stores/sessionStore';
import { MessageItem } from './MessageItem';
import { cn } from '@/shared/utils/cn';

export interface MessageListProps {
  className?: string;
  autoScroll?: boolean;
}

export const MessageList = memo(({ className, autoScroll = true }: MessageListProps) => {
  const { messages } = useSessionStore();
  const parentRef = useRef<HTMLDivElement>(null);
  const isUserScrolling = useRef(false);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 100, // 预估消息高度
    overscan: 5 // 预渲染数量
  });

  // 自动滚动到底部
  useEffect(() => {
    if (!autoScroll || isUserScrolling.current) return;

    const scrollElement = parentRef.current;
    if (!scrollElement) return;

    scrollElement.scrollTop = scrollElement.scrollHeight;
  }, [messages.length, autoScroll]);

  // 检测用户滚动
  useEffect(() => {
    const scrollElement = parentRef.current;
    if (!scrollElement) return;

    let scrollTimeout: NodeJS.Timeout;

    const handleScroll = () => {
      isUserScrolling.current = true;
      clearTimeout(scrollTimeout);
      scrollTimeout = setTimeout(() => {
        // 如果接近底部，恢复自动滚动
        const { scrollTop, scrollHeight, clientHeight } = scrollElement;
        const distanceToBottom = scrollHeight - scrollTop - clientHeight;
        if (distanceToBottom < 100) {
          isUserScrolling.current = false;
        }
      }, 500);
    };

    scrollElement.addEventListener('scroll', handleScroll);
    return () => {
      scrollElement.removeEventListener('scroll', handleScroll);
      clearTimeout(scrollTimeout);
    };
  }, []);

  const virtualItems = virtualizer.getVirtualItems();

  return (
    <div
      ref={parentRef}
      className={cn('h-full overflow-auto', className)}
    >
      <div
        style={{
          height: `${virtualizer.getTotalSize()}px`,
          position: 'relative',
          width: '100%'
        }}
      >
        {virtualItems.map((virtualItem) => (
          <div
            key={virtualItem.key}
            data-index={virtualItem.index}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${virtualItem.start}px)`
            }}
          >
            <MessageItem {...messages[virtualItem.index]} />
          </div>
        ))}
      </div>
    </div>
  );
});

MessageList.displayName = 'MessageList';
```

**验收标准**:
- ✅ 支持1000+条消息
- ✅ 渲染性能 < 200ms/条
- ✅ 自动滚动到底部
- ✅ 用户滚动时暂停自动滚动
- ✅ 接近底部时恢复自动滚动

---

## 3. 任务依赖与执行顺序

```
T2.1 主题系统 (Week 4)
├── T2-W4-01 预设主题CSS变量        ← 依赖 Phase1的TailwindCSS配置
├── T2-W4-02 ThemeConfig数据结构    ← 依赖 Phase1的类型系统(11-types.md)
├── T2-W4-03 主题切换器组件          ← 依赖 T2-W4-02 + Phase1的themeStore
├── T2-W4-04 主题编辑器UI            ← 依赖 T2-W4-03
├── T2-W4-05 响应式布局              ← 依赖 Phase1的布局组件
└── T2-W4-06 ChatLayout主布局        ← 依赖 T2-W4-05

T2.2 消息组件 (Week 5)
├── T2-W5-01 MessageItem组件         ← 依赖 Phase1的ChatMessage类型
├── T2-W5-02 react-markdown集成      ← 依赖 T2-W5-01
├── T2-W5-03 highlight.js代码高亮    ← 依赖 T2-W5-02
├── T2-W5-04 KaTeX公式渲染           ← 依赖 T2-W5-02
├── T2-W5-05 MessageList虚拟滚动     ← 依赖 T2-W5-01 + @tanstack/react-virtual
└── T2-W5-06 消息操作菜单            ← 依赖 T2-W5-01

T2.3 输入组件 (Week 6)
├── T2-W6-01 ChatInput组件           ← 依赖 Phase1的JavaBridge
├── T2-W6-02 TextInput自动高度       ← 依赖 T2-W6-01
├── T2-W6-03 附件拖拽处理            ← 依赖 T2-W6-01
├── T2-W6-04 图片预览组件            ← 依赖 T2-W6-03
├── T2-W6-05 发送按钮与快捷键        ← 依赖 T2-W6-01
└── T2-W6-06 输入框工具栏            ← 依赖 T2-W6-05
```

**关键路径**: T2-W4-02 → T2-W5-01 → T2-W5-05 → T2-W6-01

---

## 4. 验收标准

### 功能验收
- [ ] 6套预设主题可正常应用，切换延迟 < 100ms
- [ ] 主题编辑器可自定义颜色/字体/间距/圆角
- [ ] 响应式布局支持 < 800px / 800-1200px / > 1200px 三个断点
- [ ] 消息列表支持1000+消息（虚拟滚动）
- [ ] Markdown渲染支持GFM表格、代码高亮、LaTeX公式
- [ ] 输入框支持自动高度调整
- [ ] 附件拖拽/粘贴正常工作

### 性能验收
- [ ] 虚拟滚动渲染 < 200ms/条
- [ ] Markdown解析缓存命中率 > 80%
- [ ] 主题切换无闪烁（CSS变量方案）
- [ ] 代码块懒加载不阻塞主线程

### 代码质量验收
- [ ] 所有组件使用React.memo优化
- [ ] 事件处理使用useCallback
- [ ] 复杂计算使用useMemo
- [ ] TypeScript类型100%覆盖

---

## 5. 文件清单汇总

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `src/styles/themes/jetbrains-dark.css` | JetBrains Dark主题变量 |
| `src/styles/themes/github-dark.css` | GitHub Dark主题变量 |
| `src/styles/themes/vscode-dark.css` | VS Code Dark主题变量 |
| `src/styles/themes/monokai.css` | Monokai主题变量 |
| `src/styles/themes/solarized-light.css` | Solarized Light主题变量 |
| `src/styles/themes/nord.css` | Nord主题变量 |
| `src/shared/constants/themes.ts` | 主题预设数据 |
| `src/shared/hooks/useTheme.ts` | 主题切换Hook |
| `src/shared/hooks/useVirtualList.ts` | 虚拟滚动Hook |
| `src/shared/components/markdown/MarkdownRenderer.tsx` | Markdown渲染器 |
| `src/shared/components/markdown/CodeBlock.tsx` | 代码块组件（highlight.js） |
| `src/shared/components/markdown/LatexRenderer.tsx` | LaTeX公式组件（KaTeX） |
| `src/shared/components/layout/ChatLayout.tsx` | 聊天布局组件 |
| `src/shared/components/layout/ResponsiveLayout.tsx` | 响应式布局组件 |
| `src/features/chat/components/MessageList.tsx` | 消息列表（虚拟滚动） |
| `src/features/chat/components/MessageItem.tsx` | 消息项组件 |
| `src/features/chat/components/MessageActions.tsx` | 消息操作菜单 |
| `src/features/chat/components/ChatInput.tsx` | 输入组件 |
| `src/features/chat/components/AttachmentPreview.tsx` | 附件预览 |
| `src/features/theme/components/ThemeSwitcher.tsx` | 主题切换器 |
| `src/features/theme/components/ThemeEditor.tsx` | 主题编辑器 |

---

## 6. 相关文档

- [总览](./00-overview.md)
- [技术架构设计](./10-architecture.md)
- [类型定义规范](./11-types.md)
- [组件设计规范](./12-components.md)
- [Phase 1: 基础架构](./01-phase1-foundation.md)
- [Phase 3: 交互增强](./03-phase3-interaction.md)
