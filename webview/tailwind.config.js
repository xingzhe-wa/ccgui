/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))'
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))'
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))'
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))'
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))'
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))'
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))'
        },
        // 自定义颜色：消息气泡
        userMessage: {
          DEFAULT: 'hsl(var(--userMessage))',
          foreground: 'hsl(var(--userMessageForeground))'
        },
        aiMessage: {
          DEFAULT: 'hsl(var(--aiMessage))',
          foreground: 'hsl(var(--aiMessageForeground))'
        },
        systemMessage: {
          DEFAULT: 'hsl(var(--systemMessage))',
          foreground: 'hsl(var(--systemMessageForeground))'
        },
        // 自定义颜色：代码块
        codeBackground: 'hsl(var(--code-background))',
        codeForeground: 'hsl(var(--code-foreground))',
        // 自定义颜色：背景层级
        'background-secondary': 'hsl(var(--background-secondary))',
        'background-elevated': 'hsl(var(--background-elevated))',
        // 自定义颜色：文字变体
        'foreground-muted': 'hsl(var(--foreground-muted))'
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)'
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' }
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' }
        }
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out'
      }
    }
  },
  plugins: []
};
