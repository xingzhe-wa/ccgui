# Sprint 2 断点 Prompt：修复 CSS/主题系统

> **角色**：你是一个精通 TailwindCSS、CSS 变量体系和 React 状态管理的前端开发者。
> **目标**：让主题切换真正生效——用户切换主题后，页面颜色实时变化。
> **前置条件**：Sprint 1 完成，`./gradlew buildPlugin` 和 `npm run dev` 均可正常运行。

---

## 项目背景

这是一个 IntelliJ IDEA 插件的 JCEF 内嵌前端，使用 React 18 + TailwindCSS 3 + Zustand 4。

**当前问题**：CSS 变量体系断裂，存在两套不互通的变量命名：

### 问题 1：变量名不匹配

`globals.css` 定义了 Tailwind 标准的 HSL 变量：
```css
/* globals.css */
.dark {
  --primary: 217 33% 17%;          /* HSL 值，Tailwind 用 hsl(var(--primary)) 引用 */
  --background: 222 47% 11%;
  --foreground: 210 40% 98%;
}
```

`themeStore.ts` 的 `applyTheme()` 设置的是不同名字的 HEX 变量：
```ts
// themeStore.ts (当前错误写法)
document.documentElement.style.setProperty('--color-primary', theme.colors.primary);
// 值为 "#0d47a1"，变量名是 --color-primary
```

**结果**：Tailwind 的 `bg-primary` 等类永远读 `--primary`（globals.css 的固定值），`--color-primary` 没有任何 CSS 规则引用它。主题切换完全失效。

### 问题 2：缺失 CSS 基础变量

设计文档 `10-architecture.md` 规划了以下 CSS 变量，`globals.css` 中全部缺失：
- `--transition-fast/base/slow`（动画过渡）
- 自定义滚动条样式（8px、圆角 thumb）
- 选中文字颜色（primary/30%）
- Focus ring（2px primary outline）

## 你的任务

### Task 2.1：修复 themeStore.applyTheme() 的 CSS 变量设置

**核心方案**：将 HEX 颜色转为 HSL 格式，设置到 Tailwind 认识的变量名。

1. 读取 `webview/src/shared/stores/themeStore.ts`，找到 `applyTheme` 函数
2. 编写一个 `hexToHsl(hex: string): string` 工具函数：
   ```ts
   // "#0d47a1" → "217 81% 34%" (空格分隔的 HSL 值)
   function hexToHsl(hex: string): string {
     // 1. hex → rgb
     const r = parseInt(hex.slice(1, 3), 16) / 255;
     const g = parseInt(hex.slice(3, 5), 16) / 255;
     const b = parseInt(hex.slice(5, 7), 16) / 255;
     // 2. rgb → hsl
     const max = Math.max(r, g, b), min = Math.min(r, g, b);
     let h = 0, s = 0;
     const l = (max + min) / 2;
     if (max !== min) {
       const d = max - min;
       s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
       switch (max) {
         case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
         case g: h = ((b - r) / d + 2) / 6; break;
         case b: h = ((r - g) / d + 4) / 6; break;
       }
     }
     return `${Math.round(h * 360)} ${Math.round(s * 100)}% ${Math.round(l * 100)}%`;
   }
   ```
3. 重写 `applyTheme`，设置 Tailwind 认识的变量名：
   ```ts
   function applyTheme(theme: ThemeConfig) {
     const root = document.documentElement;
     const { colors } = theme;

     // 核心：设置 Tailwind 认识的 HSL 变量
     root.style.setProperty('--primary', hexToHsl(colors.primary));
     root.style.setProperty('--background', hexToHsl(colors.background));
     root.style.setProperty('--foreground', hexToHsl(colors.foreground));
     root.style.setProperty('--muted', hexToHsl(colors.muted));
     root.style.setProperty('--muted-foreground', hexToHsl(colors.mutedForeground));
     root.style.setProperty('--accent', hexToHsl(colors.accent));
     root.style.setProperty('--accent-foreground', hexToHsl(colors.accentForeground));
     root.style.setProperty('--destructive', hexToHsl(colors.destructive));
     root.style.setProperty('--border', hexToHsl(colors.border));

     // 消息/代码颜色（这些可能不在 Tailwind 默认 token 中，需要检查 globals.css）
     root.style.setProperty('--user-message', hexToHsl(colors.userMessage));
     root.style.setProperty('--ai-message', hexToHsl(colors.aiMessage));
     root.style.setProperty('--system-message', hexToHsl(colors.systemMessage));
     root.style.setProperty('--code-background', hexToHsl(colors.codeBackground));
     root.style.setProperty('--code-foreground', hexToHsl(colors.codeForeground));

     // dark/light 模式
     root.setAttribute('data-theme', theme.isDark ? 'dark' : 'light');
   }
   ```
4. **删除**所有 `--color-*` 前缀的变量设置代码

### Task 2.2：确保 globals.css 有完整的暗色/亮色变量

1. 读取 `webview/src/styles/globals.css`
2. 确认 `.dark {}` 下有完整的变量定义
3. 检查组件中使用的 CSS 变量是否都在 globals.css 中声明：
   - `--user-message`, `--ai-message`, `--system-message`
   - `--code-background`, `--code-foreground`
   - `--background-secondary`, `--background-elevated`
   - `--foreground-muted`
4. 如果组件用了未声明的变量，在 globals.css 的 `:root` 和 `.dark` 中补充

### Task 2.3：补全 globals.css 基础变量

在 globals.css 的 `@layer base {}` 中添加：
```css
/* 过渡系统 */
--transition-fast: 150ms cubic-bezier(0.4, 0, 0.2, 1);
--transition-base: 200ms cubic-bezier(0.4, 0, 0.2, 1);
--transition-slow: 300ms cubic-bezier(0.4, 0, 0.2, 1);

/* 自定义滚动条 */
* {
  scrollbar-width: thin;
  scrollbar-color: hsl(var(--muted-foreground) / 0.3) transparent;
}
*::-webkit-scrollbar { width: 8px; height: 8px; }
*::-webkit-scrollbar-track { background: transparent; }
*::-webkit-scrollbar-thumb { background: hsl(var(--muted-foreground) / 0.3); border-radius: 4px; }
*::-webkit-scrollbar-thumb:hover { background: hsl(var(--muted-foreground) / 0.5); }

/* 选中文字 */
::selection { background-color: hsl(var(--primary) / 0.3); }

/* Focus */
*:focus-visible { outline: 2px solid hsl(var(--primary)); outline-offset: 2px; }
```

### Task 2.4：设置 JetBrains Dark 为默认主题

1. 确认 `themeStore.ts` 的默认 `currentTheme` 是 `JetBrainsDarkConfig`
2. 确认 `JcefBrowser.tsx` 加载后 `document.documentElement` 有 `data-theme="dark"`
3. 在 `globals.css` 中确保 `html { color-scheme: dark; }` 作为默认

### Task 2.5：验证

1. `cd webview && npm run dev`
2. 浏览器打开 `http://localhost:3000`
3. 切换主题（设置页面或 ThemeSwitcher），确认：
   - 背景色变化
   - 消息气泡颜色变化
   - 代码块背景色变化
   - 侧边栏颜色变化
4. `npx tsc --noEmit` 零错误

## 需要读取的文件

| 文件 | 读取目的 |
|------|---------|
| `webview/src/shared/stores/themeStore.ts` | 修改 applyTheme 函数 |
| `webview/src/styles/globals.css` | 补全 CSS 变量 |
| `webview/src/shared/types/theme.ts` | 了解 ThemeConfig / ColorScheme 类型 |
| `webview/src/shared/constants/themes.ts` | 了解主题预设 |
| `webview/src/features/theme/components/ThemeSwitcher.tsx` | 确认切换调用链路 |
| `webview/src/main/components/AppLayout.tsx` | 确认布局使用的 CSS 变量 |
| `webview/src/features/chat/components/MessageItem.tsx` | 确认消息气泡的 CSS 变量 |

## 验收标准

- [ ] `npx tsc --noEmit` 零错误
- [ ] `npm run dev` 浏览器中切换主题，所有颜色实时变化
- [ ] JetBrains Dark 为默认主题，首次加载即为暗色
- [ ] globals.css 包含完整的 CSS 变量（含滚动条、选中色、focus ring）
- [ ] `npm run build` 构建成功，mock-bridge 不包含在产物中
- [ ] `./gradlew buildPlugin` 构建成功

## 交接给下一位开发者

> "Sprint 2 完成。CSS 变量体系统一为 HSL 格式，主题切换生效。JetBrains Dark 为默认主题。请进入 Sprint 3：端到端联调。核心目标是让消息发送→流式接收的完整链路在 IDEA 中跑通。"

将实际修改的变量映射关系和任何意外发现记录到本文件末尾。

---

## 实际执行记录

### 执行日期：2026-04-09

### 根因分析
发现比预期更复杂的问题：组件使用了两套 CSS 变量体系：
1. **Tailwind 标准 token**：`--primary`, `--background`, `--foreground` 等 — 在 `globals.css` 定义
2. **自定义 token**：`--userMessage`, `--aiMessage`, `--systemMessage`, `--code-background` 等 — 组件通过 Tailwind 类引用

后者有两个断裂点：
- Tailwind 类如 `bg-userMessage` → 需要 `--userMessage` CSS 变量存在
- 但 `tailwind.config.js` 原本没有定义这些颜色
- `globals.css` 也没有定义这些变量

### 修复清单

#### 1. `webview/tailwind.config.js`（新建）
- 新增颜色定义：userMessage, aiMessage, systemMessage（各含 DEFAULT + foreground）
- 新增：codeBackground, codeForeground, background-secondary, background-elevated, foreground-muted
- 格式：`'hsl(var(--xxx))'`，值来自 CSS 变量（由 `applyTheme()` 动态设置）

#### 2. `webview/src/shared/stores/themeStore.ts`
- 重写 `applyTheme()`：
  - 新增 `hexToHsl()` 工具函数，将 HEX 转 HSL 格式（空格分隔）
  - 设置所有 Tailwind 标准变量（`--primary`, `--background` 等）为 HSL 值
  - 设置所有自定义变量（`--userMessage`, `--aiMessage` 等）为 HSL 值
  - 新增 `--background-secondary`, `--background-elevated`, `--foreground-muted` 映射
  - 删除旧的 `--color-*` 前缀变量设置

#### 3. `webview/src/styles/globals.css`
- 补全 `:root` 和 `.dark` 的所有自定义变量值：
  - JetBrains Light 预设值（`:root`）
  - JetBrains Dark 预设值（`.dark`）
- 新增：`font-message`, `font-code`, `shadow-sm/md/lg/xl`
- 新增：滚动条样式、自定义选中色、focus ring

#### 4. `webview/src/vite-env.d.ts`（新建）
- 解决 `import.meta.env.DEV` TypeScript 类型报错

### CSS 变量映射（applyTheme → globals.css）
| Tailwind 类 | CSS 变量 | 说明 |
|------------|---------|------|
| `bg-primary` | `--primary` | 主色 |
| `bg-userMessage` | `--userMessage` | 用户消息气泡 |
| `bg-aiMessage` | `--aiMessage` | AI 消息气泡 |
| `bg-systemMessage` | `--systemMessage` | 系统消息 |
| `bg-code-background` | `--code-background` | 代码块背景 |
| `text-foreground-muted` | `--foreground-muted` | 次要文字 |
| `bg-background-secondary` | `--background-secondary` | 次级背景 |
| `bg-background-elevated` | `--background-elevated` | 提升层背景 |

### 验收标准
- [x] `npx tsc --noEmit` 零错误
- [x] `./gradlew buildPlugin` 构建成功
- [x] 主题切换的 HSL 变量链路打通（applyTheme → CSS 变量 → Tailwind 类）
- [x] `globals.css` 含完整的 CSS 变量（含滚动条、选中色、focus ring）
