/**
 * ProviderList - 供应商列表组件
 *
 * 完全参考 jetbrains-cc-gui 的实现，支持：
 * - 特殊供应商（Local Settings、CLI Login）
 * - 拖拽排序
 * - cc-switch 导入标记和转换
 * - 独立的确认对话框
 */

import { useState, useCallback, useRef, useEffect, memo } from 'react';
import { Plus, CloudDownload, GripVertical, Edit, Trash2, Check, Play, CircleSlash, File, Key, RefreshCw } from 'lucide-react';
import { cn } from '@/shared/utils/cn';
import { useProviderProfilesStore, type ProviderProfile } from '@/shared/stores/providerProfilesStore';
import { SPECIAL_PROVIDER_IDS, isSpecialProviderId } from '@/shared/constants/specialProviders';
import { useDragSort } from '@/shared/hooks/useDragSort';

interface ProviderListProps {
  onEdit: (profile: ProviderProfile) => void;
  onDelete: (profileId: string) => void;
  onSwitch: (id: string) => void;
  addToast?: (message: string, type: 'info' | 'success' | 'warning' | 'error') => void;
}

export const ProviderList = memo<ProviderListProps>(function ProviderList({
  onEdit,
  onDelete,
  onSwitch,
  addToast
}) {
  const {
    profiles,
    activeProfileId,
    reorderProfiles
  } = useProviderProfilesStore();

  // 确认对话框状态
  const [showLocalConfirm, setShowLocalConfirm] = useState(false);
  const [showLocalDisableConfirm, setShowLocalDisableConfirm] = useState(false);
  const [showCliLoginConfirm, setShowCliLoginConfirm] = useState(false);
  const [showCliLoginDisableConfirm, setShowCliLoginDisableConfirm] = useState(false);
  const [convertingProvider, setConvertingProvider] = useState<ProviderProfile | null>(null);
  const [editingCcSwitchProvider, setEditingCcSwitchProvider] = useState<ProviderProfile | null>(null);

  // 导入菜单状态
  const [importMenuOpen, setImportMenuOpen] = useState(false);
  const importMenuRef = useRef<HTMLDivElement>(null);

  // 拖拽排序
  const { localItems, draggedId, dragOverId, handleDragStart, handleDragOver, handleDragLeave, handleDrop, handleDragEnd } =
    useDragSort<ProviderProfile>({
      items: profiles,
      onSort: async (orderedIds) => {
        await reorderProfiles(orderedIds);
      },
      pinnedIds: [SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS, SPECIAL_PROVIDER_IDS.CLI_LOGIN]
    });

  // 处理点击外部关闭导入菜单
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (importMenuRef.current && !importMenuRef.current.contains(event.target as Node)) {
        setImportMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 判断是否激活
  const isActive = (id: string) => activeProfileId === id;

  // 处理切换供应商
  const handleSwitchClick = useCallback(async (id: string) => {
    if (id === SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS) {
      setShowLocalConfirm(true);
    } else if (id === SPECIAL_PROVIDER_IDS.CLI_LOGIN) {
      setShowCliLoginConfirm(true);
    } else {
      await onSwitch(id);
    }
  }, [onSwitch]);

  // 处理禁用
  const handleDisableClick = useCallback(async (id: string) => {
    if (id === SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS) {
      setShowLocalDisableConfirm(true);
    } else if (id === SPECIAL_PROVIDER_IDS.CLI_LOGIN) {
      setShowCliLoginDisableConfirm(true);
    } else {
      // 对于常规供应商，禁用相当于切换到 Disabled
      await onSwitch(SPECIAL_PROVIDER_IDS.DISABLED);
    }
  }, [onSwitch]);

  // 处理编辑
  const handleEditClick = useCallback((provider: ProviderProfile) => {
    if (provider.source === 'cc-switch') {
      setEditingCcSwitchProvider(provider);
    } else {
      onEdit(provider);
    }
  }, [onEdit]);

  // 处理转换
  const handleConvert = useCallback(async () => {
    if (convertingProvider) {
      const oldId = convertingProvider.id;
      const newId = `${oldId}_local`;

      const newProvider: ProviderProfile = {
        ...convertingProvider,
        id: newId,
        name: `${convertingProvider.name} (Local)`,
        source: 'local'
      };

      // 删除旧配置
      await onDelete(oldId);
      // 创建新配置
      await onSwitch(newId);
      // 这里需要创建新配置，暂时通过 onEdit 来处理
      onEdit(newProvider);

      setConvertingProvider(null);
      if (editingCcSwitchProvider?.id === convertingProvider.id) {
        setEditingCcSwitchProvider(null);
      }
      addToast?.('转换成功', 'success');
    }
  }, [convertingProvider, editingCcSwitchProvider, onDelete, onSwitch, onEdit, addToast]);

  // 分离特殊供应商和常规供应商
  const regularProviders = localItems.filter(p => !isSpecialProviderId(p.id));

  return (
    <div className="space-y-4">
      {/* 编辑 cc-switch 配置警告对话框 */}
      {editingCcSwitchProvider && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="bg-popover border border-border rounded-lg shadow-xl p-6 max-w-md">
            <div className="flex items-center gap-2 mb-4">
              <CircleSlash className="w-5 h-5 text-orange-500" />
              <h3 className="text-lg font-semibold text-foreground">编辑 cc-switch 配置</h3>
            </div>
            <p className="text-sm text-muted-foreground mb-6">
              此配置是从 cc-switch 导入的，直接编辑可能会在下次导入时被覆盖。建议先转换为本地配置。
            </p>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => setEditingCcSwitchProvider(null)}
                className="px-4 py-2 rounded text-sm bg-background-secondary text-foreground hover:bg-background-elevated"
              >
                取消
              </button>
              <button
                onClick={() => {
                  const p = editingCcSwitchProvider;
                  setEditingCcSwitchProvider(null);
                  onEdit(p);
                }}
                className="px-4 py-2 rounded text-sm bg-background-secondary text-foreground hover:bg-background-elevated"
              >
                继续编辑
              </button>
              <button
                onClick={() => setConvertingProvider(editingCcSwitchProvider)}
                className="px-4 py-2 rounded text-sm bg-orange-500/10 text-orange-500 hover:bg-orange-500/20"
              >
                转换并编辑
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 转换确认对话框 */}
      {convertingProvider && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
          <div className="bg-popover border border-border rounded-lg shadow-xl p-6 max-w-md">
            <div className="flex items-center gap-2 mb-4">
              <RefreshCw className="w-5 h-5 text-primary" />
              <h3 className="text-lg font-semibold text-foreground">转换为本地配置</h3>
            </div>
            <p className="text-sm text-muted-foreground mb-2">
              确定要将 <strong>{convertingProvider.name}</strong> 转换为本地配置吗？
            </p>
            <p className="text-sm text-muted-foreground mb-6">
              转换后将不再与 cc-switch 同步，可以自由编辑。
            </p>
            <div className="flex gap-2 justify-end">
              <button
                onClick={() => {
                  setConvertingProvider(null);
                  if (editingCcSwitchProvider) {
                    setEditingCcSwitchProvider(null);
                  }
                }}
                className="px-4 py-2 rounded text-sm bg-background-secondary text-foreground hover:bg-background-elevated"
              >
                取消
              </button>
              <button
                onClick={handleConvert}
                className="px-4 py-2 rounded text-sm bg-primary text-primary-foreground hover:opacity-90"
              >
                确认转换
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Local Settings 启用确认 */}
      {showLocalConfirm && (
        <ConfirmDialog
          icon={<File className="w-5 h-5 text-orange-500" />}
          title="启用 Local Settings"
          description="将直接使用 ~/.claude/settings.json 配置，插件管理的配置将被覆盖。"
          detail="启用后，所有供应商配置将从本地 settings.json 文件读取。"
          confirmText="授权并启用"
          onCancel={() => setShowLocalConfirm(false)}
          onConfirm={async () => {
            setShowLocalConfirm(false);
            await handleSwitchClick(SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS);
          }}
        />
      )}

      {/* Local Settings 禁用确认 */}
      {showLocalDisableConfirm && (
        <ConfirmDialog
          icon={<CircleSlash className="w-5 h-5 text-destructive" />}
          title="撤销 Local Settings 授权"
          description="将停止使用 ~/.claude/settings.json 配置，切换到插件管理的配置。"
          confirmText="撤销授权"
          onCancel={() => setShowLocalDisableConfirm(false)}
          onConfirm={async () => {
            setShowLocalDisableConfirm(false);
            await handleDisableClick(SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS);
          }}
        />
      )}

      {/* CLI Login 启用确认 */}
      {showCliLoginConfirm && (
        <ConfirmDialog
          icon={<Key className="w-5 h-5 text-orange-500" />}
          title="启用 CLI Login"
          description="将使用 Claude CLI 原生 OAuth 登录，无需手动配置 API Key。"
          detail="启用后，将通过 CLI 的 OAuth 登录状态进行认证。"
          confirmText="授权并启用"
          onCancel={() => setShowCliLoginConfirm(false)}
          onConfirm={async () => {
            setShowCliLoginConfirm(false);
            await handleSwitchClick(SPECIAL_PROVIDER_IDS.CLI_LOGIN);
          }}
        />
      )}

      {/* CLI Login 禁用确认 */}
      {showCliLoginDisableConfirm && (
        <ConfirmDialog
          icon={<CircleSlash className="w-5 h-5 text-destructive" />}
          title="撤销 CLI Login 授权"
          description="将停止使用 Claude CLI OAuth 登录。"
          confirmText="撤销授权"
          onCancel={() => setShowCliLoginDisableConfirm(false)}
          onConfirm={async () => {
            setShowCliLoginDisableConfirm(false);
            await handleDisableClick(SPECIAL_PROVIDER_IDS.CLI_LOGIN);
          }}
        />
      )}

      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">已保存配置</h3>
        <div className="flex items-center gap-2">
          <div className="relative" ref={importMenuRef}>
            <button
              onClick={() => setImportMenuOpen(!importMenuOpen)}
              className={cn(
                'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium',
                'border border-border bg-background text-foreground',
                'hover:bg-background-secondary transition-colors'
              )}
            >
              <CloudDownload className="w-3.5 h-3.5" />
              导入
            </button>
            {importMenuOpen && (
              <div className="absolute right-0 top-full mt-1 bg-popover border border-border rounded-md shadow-lg py-1 min-w-[150px]">
                <button
                  className="w-full px-3 py-2 text-left text-xs hover:bg-background-secondary flex items-center gap-2"
                  onClick={() => {
                    setImportMenuOpen(false);
                    addToast?.('功能开发中...', 'info');
                  }}
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  从 cc-switch 导入
                </button>
              </div>
            )}
          </div>
          <button
            onClick={() => onEdit(null as any)}
            className={cn(
              'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium',
              'bg-primary text-primary-foreground',
              'hover:opacity-90 transition-opacity'
            )}
          >
            <Plus className="w-3.5 h-3.5" />
            新建配置
          </button>
        </div>
      </div>

      {/* 供应商列表 */}
      <div className="space-y-3">
        {/* 特殊供应商：Local Settings */}
        <div
          className={cn(
            'flex items-center gap-3 p-4 rounded-lg border transition-all',
            'border-l-4',
            isActive(SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS)
              ? 'bg-primary/5 border-primary/20 border-l-primary'
              : 'bg-background-secondary border-border hover:border-primary/30 border-l-orange-500'
          )}
        >
          <div className="shrink-0 w-8 h-8 rounded-full bg-orange-500/10 flex items-center justify-center text-orange-500">
            <File className="w-4 h-4" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-foreground">Local Settings</div>
            <div className="text-xs text-muted-foreground truncate">使用 ~/.claude/settings.json 配置</div>
          </div>
          <div className="shrink-0">
            {isActive(SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS) ? (
              <button
                onClick={() => setShowLocalDisableConfirm(true)}
                className="px-3 py-1.5 rounded-full text-xs font-medium bg-orange-500/10 text-orange-500 border border-orange-500/20 hover:bg-orange-500/20 transition-colors"
              >
                <CircleSlash className="w-3 h-3 inline mr-1" />
                撤销授权
              </button>
            ) : (
              <button
                onClick={() => setShowLocalConfirm(true)}
                className="px-3 py-1.5 rounded-full text-xs font-medium bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
              >
                <Play className="w-3 h-3 inline mr-1" />
                授权并启用
              </button>
            )}
          </div>
        </div>

        {/* 特殊供应商：CLI Login */}
        <div
          className={cn(
            'flex items-center gap-3 p-4 rounded-lg border transition-all',
            'border-l-4',
            isActive(SPECIAL_PROVIDER_IDS.CLI_LOGIN)
              ? 'bg-primary/5 border-primary/20 border-l-primary'
              : 'bg-background-secondary border-border hover:border-primary/30 border-l-orange-500'
          )}
        >
          <div className="shrink-0 w-8 h-8 rounded-full bg-orange-500/10 flex items-center justify-center text-orange-500">
            <Key className="w-4 h-4" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-foreground">CLI Login</div>
            <div className="text-xs text-muted-foreground truncate">使用 Claude CLI 原生 OAuth 登录</div>
          </div>
          <div className="shrink-0">
            {isActive(SPECIAL_PROVIDER_IDS.CLI_LOGIN) ? (
              <button
                onClick={() => setShowCliLoginDisableConfirm(true)}
                className="px-3 py-1.5 rounded-full text-xs font-medium bg-orange-500/10 text-orange-500 border border-orange-500/20 hover:bg-orange-500/20 transition-colors"
              >
                <CircleSlash className="w-3 h-3 inline mr-1" />
                撤销授权
              </button>
            ) : (
              <button
                onClick={() => setShowCliLoginConfirm(true)}
                className="px-3 py-1.5 rounded-full text-xs font-medium bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
              >
                <Play className="w-3 h-3 inline mr-1" />
                授权并启用
              </button>
            )}
          </div>
        </div>

        {/* 常规供应商 */}
        {regularProviders.map((profile) => (
          <div
            key={profile.id}
            draggable
            onDragStart={(e) => handleDragStart(e, profile.id)}
            onDragOver={(e) => handleDragOver(e, profile.id)}
            onDragLeave={handleDragLeave}
            onDrop={(e) => handleDrop(e, profile.id)}
            onDragEnd={handleDragEnd}
            className={cn(
              'relative flex items-center gap-3 p-4 rounded-lg border transition-all',
              isActive(profile.id)
                ? 'bg-primary/5 border-primary/20'
                : 'bg-background-secondary border-border hover:border-primary/30',
              draggedId === profile.id && 'opacity-50',
              dragOverId === profile.id && 'border-primary'
            )}
          >
            {/* 拖拽手柄 */}
            <div className="cursor-grab active:cursor-grabbing text-muted-foreground hover:text-foreground shrink-0">
              <GripVertical className="w-4 h-4" />
            </div>

            {/* 供应商信息 */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-foreground truncate">{profile.name}</span>
                {profile.source === 'cc-switch' && (
                  <span className="shrink-0 px-1.5 py-0.5 rounded text-xs bg-muted text-muted-foreground border border-border">
                    cc-switch
                  </span>
                )}
              </div>
              <div className="text-xs text-muted-foreground truncate">
                {profile.provider} / {profile.model || '默认模型'}
              </div>
            </div>

            {/* 操作按钮 */}
            <div className="flex items-center gap-1 shrink-0">
              {isActive(profile.id) ? (
                <span className="flex items-center gap-1 px-2 py-1 rounded text-xs text-primary font-medium">
                  <Check className="w-3 h-3" />
                  使用中
                </span>
              ) : (
                <button
                  onClick={() => handleSwitchClick(profile.id)}
                  className="px-2.5 py-1 rounded text-xs bg-primary/10 text-primary hover:bg-primary/20 font-medium transition-colors"
                >
                  <Play className="w-3 h-3 inline mr-1" />
                  启用
                </button>
              )}

              <div className="w-px h-5 bg-border mx-1" />

              <div className="flex items-center gap-0.5">
                {profile.source === 'cc-switch' && (
                  <button
                    onClick={() => setConvertingProvider(profile)}
                    className="p-1.5 rounded hover:bg-background-elevated text-muted-foreground hover:text-foreground transition-colors"
                    title="转换为本地配置"
                  >
                    <RefreshCw className="w-4 h-4" />
                  </button>
                )}
                <button
                  onClick={() => handleEditClick(profile)}
                  className="p-1.5 rounded hover:bg-background-elevated text-muted-foreground hover:text-foreground transition-colors"
                  title="编辑"
                >
                  <Edit className="w-4 h-4" />
                </button>
                <button
                  onClick={() => onDelete(profile.id)}
                  className="p-1.5 rounded hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-colors"
                  title="删除"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        ))}

        {/* 空状态 */}
        {regularProviders.length === 0 && (
          <div className="flex flex-col items-center justify-center py-12 px-4 text-center border-2 border-dashed border-border rounded-lg bg-background-secondary">
            <File className="w-12 h-12 text-muted-foreground/50 mb-3" />
            <p className="text-sm text-muted-foreground">暂无供应商配置</p>
            <p className="text-xs text-muted-foreground mt-1">点击"新建配置"按钮创建第一个配置</p>
          </div>
        )}
      </div>
    </div>
  );
});

/**
 * 确认对话框组件
 */
interface ConfirmDialogProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  detail?: string;
  confirmText?: string;
  onCancel: () => void;
  onConfirm: () => void;
}

function ConfirmDialog({
  icon,
  title,
  description,
  detail,
  confirmText = '确认',
  onCancel,
  onConfirm
}: ConfirmDialogProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="bg-popover border border-border rounded-lg shadow-xl p-6 max-w-md">
        <div className="flex items-center gap-2 mb-4">
          {icon}
          <h3 className="text-lg font-semibold text-foreground">{title}</h3>
        </div>
        <p className="text-sm text-muted-foreground mb-2">{description}</p>
        {detail && (
          <p className="text-sm text-muted-foreground mb-6 opacity-80">{detail}</p>
        )}
        <div className="flex gap-2 justify-end">
          <button
            onClick={onCancel}
            className="px-4 py-2 rounded text-sm bg-background-secondary text-foreground hover:bg-background-elevated"
          >
            取消
          </button>
          <button
            onClick={onConfirm}
            className="px-4 py-2 rounded text-sm bg-primary text-primary-foreground hover:opacity-90"
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
