/**
 * 特殊供应商常量
 *
 * 参考 jetbrains-cc-gui 的特殊供应商设计
 */

export const SPECIAL_PROVIDER_IDS = {
  /** 禁用供应商 */
  DISABLED: '__disabled__',

  /** 本地 Settings.json 供应商（使用 ~/.claude/settings.json） */
  LOCAL_SETTINGS: '__local_settings_json__',

  /** CLI Login 供应商（使用 Claude CLI 原生 OAuth 登录） */
  CLI_LOGIN: '__cli_login__'
} as const;

export type SpecialProviderId = typeof SPECIAL_PROVIDER_IDS[keyof typeof SPECIAL_PROVIDER_IDS];

/**
 * 特殊供应商配置
 */
export const SPECIAL_PROVIDERS = {
  [SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS]: {
    id: SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS,
    name: 'Local Settings',
    description: '使用 ~/.claude/settings.json 配置',
    icon: 'file'
  },
  [SPECIAL_PROVIDER_IDS.CLI_LOGIN]: {
    id: SPECIAL_PROVIDER_IDS.CLI_LOGIN,
    name: 'CLI Login',
    description: '使用 Claude CLI 原生 OAuth 登录',
    icon: 'key'
  },
  [SPECIAL_PROVIDER_IDS.DISABLED]: {
    id: SPECIAL_PROVIDER_IDS.DISABLED,
    name: 'Disabled',
    description: '禁用 Claude 供应商',
    icon: 'circle-slash'
  }
} as const;

/**
 * 判断是否为特殊供应商 ID
 */
export function isSpecialProviderId(id: string): id is SpecialProviderId {
  return Object.values(SPECIAL_PROVIDER_IDS).includes(id as SpecialProviderId);
}

/**
 * 判断是否为特殊供应商
 */
export function isSpecialProvider(provider: { id: string }): boolean {
  return isSpecialProviderId(provider.id);
}
