/**
 * ModelConfigPanel - 模型配置面板
 *
 * 参考 jetbrains-cc-gui 的 ProviderManageSection 实现
 * 支持多供应商配置切换：创建/编辑/删除/激活配置
 */

import { memo, useState, useEffect, useCallback } from 'react';
import { cn } from '@/shared/utils/cn';
import { useProviderProfilesStore, type ProviderProfile } from '@/shared/stores/providerProfilesStore';
import { ProfileEditorModal } from './ProfileEditorModal';
import { ProviderList } from './ProviderList';

interface ModelConfigPanelProps {
  className?: string;
}

export const ModelConfigPanel = memo<ModelConfigPanelProps>(function ModelConfigPanel({
  className
}) {
  const {
    isLoading,
    error,
    loadProfiles,
    createProfile,
    updateProfile,
    deleteProfile,
    setActiveProfile
  } = useProviderProfilesStore();

  // 弹窗状态
  const [modalOpen, setModalOpen] = useState(false);
  const [editingProfile, setEditingProfile] = useState<ProviderProfile | null>(null);
  const [toast, setToast] = useState<{ message: string; type: 'info' | 'success' | 'warning' | 'error' } | null>(null);

  // 加载 profiles
  useEffect(() => {
    loadProfiles();
  }, [loadProfiles]);

  // 显示提示
  const addToast = useCallback((message: string, type: 'info' | 'success' | 'warning' | 'error') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3000);
  }, []);

  // 打开编辑/新建弹窗
  const handleEditProfile = useCallback((profile: ProviderProfile | null) => {
    setEditingProfile(profile);
    setModalOpen(true);
  }, []);

  // 切换供应商
  const handleSwitch = useCallback(async (profileId: string) => {
    await setActiveProfile(profileId);
  }, [setActiveProfile]);

  // 删除配置
  const handleDelete = useCallback(async (profileId: string) => {
    await deleteProfile(profileId);
    addToast('配置已删除', 'success');
  }, [deleteProfile, addToast]);

  // 保存配置
  const handleSave = useCallback(async (data: Omit<ProviderProfile, 'id' | 'createdAt' | 'updatedAt'> & { id?: string }, isNew: boolean) => {
    if (isNew) {
      const id = await createProfile(data);
      if (id) {
        await setActiveProfile(id);
        addToast('配置已创建', 'success');
      }
    } else if (editingProfile) {
      await updateProfile({ ...data, id: editingProfile.id, createdAt: editingProfile.createdAt, updatedAt: Date.now() } as ProviderProfile);
      addToast('配置已更新', 'success');
    }
  }, [createProfile, updateProfile, setActiveProfile, editingProfile, addToast]);

  // 关闭弹窗
  const handleCloseModal = useCallback(() => {
    setModalOpen(false);
    setEditingProfile(null);
  }, []);

  if (isLoading) {
    return (
      <div className={cn('flex items-center justify-center p-6', className)}>
        <div className="text-muted-foreground text-sm">加载中...</div>
      </div>
    );
  }

  return (
    <div className={cn('space-y-4', className)}>
      {/* 提示消息 */}
      {toast && (
        <div className={cn(
          'px-4 py-3 rounded-md border text-sm',
          toast.type === 'success' && 'bg-green-500/10 border-green-500/20 text-green-500',
          toast.type === 'error' && 'bg-destructive/10 border-destructive/20 text-destructive',
          toast.type === 'warning' && 'bg-orange-500/10 border-orange-500/20 text-orange-500',
          toast.type === 'info' && 'bg-blue-500/10 border-blue-500/20 text-blue-500'
        )}>
          {toast.message}
        </div>
      )}

      {/* 错误提示 */}
      {error && (
        <div className="px-4 py-3 rounded-md bg-destructive/10 border border-destructive/20 text-destructive text-sm">
          {error}
        </div>
      )}

      {/* 供应商列表 */}
      <ProviderList
        onEdit={handleEditProfile}
        onDelete={handleDelete}
        onSwitch={handleSwitch}
        addToast={addToast}
      />

      {/* 编辑弹窗 */}
      <ProfileEditorModal
        isOpen={modalOpen}
        onClose={handleCloseModal}
        initialProfile={editingProfile}
        onSave={handleSave}
      />
    </div>
  );
});
