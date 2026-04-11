/**
 * Provider Profiles Store
 *
 * 多供应商配置管理，支持创建/编辑/删除/激活配置
 * 支持特殊供应商（Local Settings、CLI Login、Disabled）
 */

import { create } from 'zustand';
import { SPECIAL_PROVIDER_IDS, isSpecialProviderId } from '@/shared/constants/specialProviders';

export interface ProviderProfile {
  id: string;
  name: string;
  provider: string;
  source: string;  // local, cc-switch, cli-login, disabled
  model: string;
  apiKey: string;
  baseUrl: string;
  sonnetModel: string;
  opusModel: string;
  maxModel: string;
  maxRetries: number;
  createdAt: number;
  updatedAt: number;
}

interface ProviderProfilesState {
  // ========== 状态 ==========
  profiles: ProviderProfile[];
  activeProfileId: string | null;
  isLoading: boolean;
  error: string | null;

  // ========== 操作 ==========
  loadProfiles: () => Promise<void>;
  createProfile: (profile: Omit<ProviderProfile, 'id' | 'createdAt' | 'updatedAt'> & { id?: string }) => Promise<string | null>;
  updateProfile: (profile: ProviderProfile) => Promise<boolean>;
  deleteProfile: (profileId: string) => Promise<boolean>;
  setActiveProfile: (profileId: string | null) => Promise<boolean>;
  reorderProfiles: (orderedIds: string[]) => Promise<boolean>;
  convertCcSwitchProfile: (profileId: string) => Promise<ProviderProfile | null>;
  getActiveProfile: () => ProviderProfile | null;
  getRegularProfiles: () => ProviderProfile[];
  getSpecialProfiles: () => ProviderProfile[];
}

export const useProviderProfilesStore = create<ProviderProfilesState>((set, get) => ({
  // ========== 初始状态 ==========
  profiles: [],
  activeProfileId: null,
  isLoading: false,
  error: null,

  // ========== 操作 ==========

  loadProfiles: async () => {
    try {
      set({ isLoading: true, error: null });
      const result = await window.ccBackend?.getProviderProfiles();
      if (result) {
        // 确保特殊供应商始终存在
        let profiles = result.profiles || [];

        // 添加缺失的特殊供应商
        const specialIds = Object.values(SPECIAL_PROVIDER_IDS);
        const existingSpecialIds = profiles.filter(p => isSpecialProviderId(p.id)).map(p => p.id);

        for (const specialId of specialIds) {
          if (!existingSpecialIds.includes(specialId)) {
            const specialProfile = createSpecialProfile(specialId);
            profiles = [specialProfile, ...profiles];
          }
        }

        set({
          profiles,
          activeProfileId: result.activeProfileId || null
        });
      }
    } catch (e) {
      console.error('Failed to load provider profiles:', e);
      set({ error: '加载配置失败' });
    } finally {
      set({ isLoading: false });
    }
  },

  createProfile: async (profile) => {
    try {
      set({ error: null });
      const now = Date.now();
      const id = profile.id || `profile-${now}`;
      const profileWithId: ProviderProfile = {
        ...profile,
        id,
        createdAt: now,
        updatedAt: now
      };
      const result = await window.ccBackend?.createProviderProfile(profileWithId);
      if (result?.success) {
        await get().loadProfiles();
        return id;
      }
      return null;
    } catch (e) {
      console.error('Failed to create provider profile:', e);
      set({ error: '创建配置失败' });
      return null;
    }
  },

  updateProfile: async (profile) => {
    try {
      set({ error: null });
      const updatedProfile = {
        ...profile,
        updatedAt: Date.now()
      };
      const result = await window.ccBackend?.updateProviderProfile(updatedProfile);
      if (result?.success) {
        await get().loadProfiles();
        return true;
      }
      return false;
    } catch (e) {
      console.error('Failed to update provider profile:', e);
      set({ error: '更新配置失败' });
      return false;
    }
  },

  deleteProfile: async (profileId) => {
    try {
      set({ error: null });
      const result = await window.ccBackend?.deleteProviderProfile(profileId);
      if (result?.success) {
        await get().loadProfiles();
        return true;
      }
      return false;
    } catch (e) {
      console.error('Failed to delete provider profile:', e);
      set({ error: '删除配置失败' });
      return false;
    }
  },

  setActiveProfile: async (profileId) => {
    try {
      set({ error: null });
      const result = await window.ccBackend?.setActiveProviderProfile(profileId ?? null);
      if (result?.success) {
        set({ activeProfileId: profileId });
        return true;
      }
      return false;
    } catch (e) {
      console.error('Failed to set active provider profile:', e);
      set({ error: '激活配置失败' });
      return false;
    }
  },

  reorderProfiles: async (orderedIds) => {
    try {
      set({ error: null });
      const result = await window.ccBackend?.reorderProviderProfiles(orderedIds);
      if (result?.success) {
        await get().loadProfiles();
        return true;
      }
      return false;
    } catch (e) {
      console.error('Failed to reorder provider profiles:', e);
      set({ error: '排序配置失败' });
      return false;
    }
  },

  convertCcSwitchProfile: async (profileId) => {
    try {
      set({ error: null });
      const result = await window.ccBackend?.convertCcSwitchProfile(profileId);
      if (result?.profile) {
        await get().loadProfiles();
        return result.profile;
      }
      return null;
    } catch (e) {
      console.error('Failed to convert cc-switch profile:', e);
      set({ error: '转换配置失败' });
      return null;
    }
  },

  getActiveProfile: () => {
    const { profiles, activeProfileId } = get();
    if (!activeProfileId) return null;
    return profiles.find(p => p.id === activeProfileId) || null;
  },

  getRegularProfiles: () => {
    const { profiles } = get();
    return profiles.filter(p => !isSpecialProviderId(p.id));
  },

  getSpecialProfiles: () => {
    const { profiles } = get();
    return profiles.filter(p => isSpecialProviderId(p.id));
  }
}));

/**
 * 创建特殊供应商配置
 */
function createSpecialProfile(type: string): ProviderProfile {
  const now = Date.now();
  const baseProfile = {
    createdAt: now,
    updatedAt: now
  };
  switch (type) {
    case SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS:
      return {
        id: SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS,
        name: 'Local Settings',
        provider: 'special',
        source: 'local',
        model: '',
        apiKey: '',
        baseUrl: '',
        sonnetModel: '',
        opusModel: '',
        maxModel: '',
        maxRetries: 3,
        ...baseProfile
      };
    case SPECIAL_PROVIDER_IDS.CLI_LOGIN:
      return {
        id: SPECIAL_PROVIDER_IDS.CLI_LOGIN,
        name: 'CLI Login',
        provider: 'special',
        source: 'cli-login',
        model: '',
        apiKey: '',
        baseUrl: '',
        sonnetModel: '',
        opusModel: '',
        maxModel: '',
        maxRetries: 3,
        ...baseProfile
      };
    case SPECIAL_PROVIDER_IDS.DISABLED:
      return {
        id: SPECIAL_PROVIDER_IDS.DISABLED,
        name: 'Disabled',
        provider: 'special',
        source: 'disabled',
        model: '',
        apiKey: '',
        baseUrl: '',
        sonnetModel: '',
        opusModel: '',
        maxModel: '',
        maxRetries: 3,
        ...baseProfile
      };
    default:
      throw new Error(`Unknown special provider type: ${type}`);
  }
}
