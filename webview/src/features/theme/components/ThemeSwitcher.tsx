/**
 * ThemeSwitcher - 主题切换组件
 */

import { memo, useState, useRef, useEffect } from 'react';
import { useTheme } from '@/shared/hooks';
import { cn } from '@/shared/utils/cn';
import { ThemePresets } from '@/shared/types';
import { javaBridge } from '@/lib/java-bridge';

interface ThemeSwitcherProps {
  className?: string;
}

const THEME_OPTIONS: Array<{ value: ThemePresets; label: string; color: string }> = [
  { value: ThemePresets.FOLLOW_IDEA, label: '跟随 IDEA', color: 'hsl(220, 14%, 71%)' },
  { value: ThemePresets.JETBRAINS_DARK, label: 'JetBrains Dark', color: 'hsl(217, 91%, 60%)' },
  { value: ThemePresets.JETBRAINS_LIGHT, label: 'JetBrains Light', color: 'hsl(210, 20%, 96%)' },
  { value: ThemePresets.GITHUB_DARK, label: 'GitHub Dark', color: 'hsl(210, 100%, 67%)' },
  { value: ThemePresets.VS_CODE_DARK, label: 'VSCode Dark', color: 'hsl(0, 100%, 50%)' },
  { value: ThemePresets.MONOKAI, label: 'Monokai', color: 'hsl(330, 100%, 65%)' },
  { value: ThemePresets.NORD, label: 'Nord', color: 'hsl(198, 52%, 60%)' },
  { value: ThemePresets.SOLARIZED_LIGHT, label: 'Solarized Light', color: 'hsl(211, 100%, 50%)' }
];

export const ThemeSwitcher = memo<ThemeSwitcherProps>(function ThemeSwitcher({ className }) {
  const { theme, setPresetTheme } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const currentThemeOption = THEME_OPTIONS.find((opt) => opt.value === theme.id) ?? THEME_OPTIONS[0]!;

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const handleSelect = async (preset: ThemePresets) => {
    if (preset === ThemePresets.FOLLOW_IDEA) {
      try {
        const { isDark } = await javaBridge.getIdeTheme();
        setPresetTheme(isDark ? ThemePresets.JETBRAINS_DARK : ThemePresets.JETBRAINS_LIGHT);
      } catch {
        // fallback to dark
        setPresetTheme(ThemePresets.JETBRAINS_DARK);
      }
    } else {
      setPresetTheme(preset);
    }
    setIsOpen(false);
  };

  return (
    <div ref={dropdownRef} className={cn('relative', className)}>
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          'flex items-center gap-2 px-3 py-2 rounded-md w-full',
          'bg-background-secondary hover:bg-background-elevated',
          'border border-border transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background'
        )}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
      >
        <span
          className="w-4 h-4 rounded-full border border-border"
          style={{ backgroundColor: currentThemeOption.color }}
        />
        <span className="text-sm text-foreground">{currentThemeOption.label}</span>
        <svg
          className={cn('w-4 h-4 text-foreground-muted transition-transform', isOpen && 'rotate-180')}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <ul
          className={cn(
            'absolute z-[10000] bottom-full left-0 mb-1 w-full min-w-[180px]',
            'bg-background-elevated border border-border rounded-md shadow-lg',
            'py-1 overflow-hidden'
          )}
          role="listbox"
        >
          {THEME_OPTIONS.map((option) => (
            <li key={option.value}>
              <button
                type="button"
                onClick={() => handleSelect(option.value)}
                className={cn(
                  'w-full flex items-center gap-3 px-3 py-2',
                  'hover:bg-background-secondary transition-colors',
                  'focus:outline-none focus:bg-background-secondary',
                  theme.id === option.value && 'bg-primary/10 text-primary'
                )}
                role="option"
                aria-selected={theme.id === option.value}
              >
                <span
                  className="w-4 h-4 rounded-full border border-border"
                  style={{ backgroundColor: option.color }}
                />
                <span className="text-sm text-foreground">{option.label}</span>
                {theme.id === option.value && (
                  <svg
                    className="w-4 h-4 ml-auto text-primary"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
});

export type { ThemeSwitcherProps };
