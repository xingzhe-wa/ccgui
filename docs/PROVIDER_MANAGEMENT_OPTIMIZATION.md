# 供应商管理模块优化方案

## 一、对比分析

### 1.1 jetbrains-cc-gui vs cc-gui 供应商管理对比

| 功能特性 | jetbrains-cc-gui | cc-gui | 优化建议 |
|---------|------------------|---------|----------|
| **特殊供应商** | ✅ 支持 | ❌ 不支持 | 需要添加 |
| **拖拽排序** | ✅ 支持 | ❌ 不支持 | 需要添加 |
| **cc-switch 导入** | ✅ 支持 | ❌ 不支持 | 需要添加 |
| **编辑保护** | ✅ 支持（cc-switch 配置编辑警告） | ❌ 不支持 | 可选添加 |
| **UI 布局** | 卡片式布局 | 列表式布局 | 建议统一为卡片式 |
| **前端框架** | React + Ant Design + Less | React + Tailwind CSS + shadcn/ui | 保持 Tailwind |
| **状态管理** | React Hooks | Zustand | 保持 Zustand |
| **后端通信** | MessageDispatcher + HandlerContext | CefBrowserPanel + BridgeManager | 优化通信架构 |

### 1.2 特殊供应商对比

| 特殊供应商 | jetbrains-cc-gui | cc-gui | 说明 |
|-----------|------------------|---------|------|
| Local Settings | `__local_settings_json__` | ❌ | 使用 `~/.claude/settings.json` |
| CLI Login | `__cli_login__` | ❌ | 使用 Claude CLI 原生 OAuth |
| Disabled | `__disabled__` | ❌ | 禁用供应商 |

## 二、优化目标

### 2.1 功能目标
1. 添加特殊供应商支持（Local Settings、CLI Login、Disabled）
2. 添加拖拽排序功能
3. 添加 cc-switch 导入功能
4. 优化 UI 布局为卡片式
5. 添加编辑保护机制（可选）

### 2.2 技术目标
1. 保持现有的 Tailwind CSS + shadcn/ui 风格
2. 保持 Zustand 状态管理
3. 优化前后端通信架构
4. 提高代码可维护性

## 三、具体优化方案

### 3.1 后端优化

#### 3.1.1 ProviderProfile 模型优化

```kotlin
// E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\model\config\ProviderProfile.kt

/**
 * 特殊供应商 ID 常量
 */
object SpecialProviderIds {
    const val DISABLED = "__disabled__"
    const val LOCAL_SETTINGS = "__local_settings_json__"
    const val CLI_LOGIN = "__cli_login__"
}

/**
 * 供应商配置 Profile
 *
 * @param id 唯一标识
 * @param name 显示名称
 * @param provider 提供者类型（anthropic/openai/deepseek/custom/special）
 * @param source 配置来源（local/cc-switch/cli-login/disabled）
 * @param model 默认模型
 * @param apiKey API密钥
 * @param baseUrl API基础URL
 * @param sonnetModel AUTO 模式模型
 * @param opusModel THINKING 模式模型
 * @param maxModel PLANNING 模式模型
 * @param maxRetries 最大重试次数
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
data class ProviderProfile(
    val id: String,
    val name: String,
    val provider: String = "anthropic",
    val source: String = "local",  // 新增：local, cc-switch, cli-login, disabled
    val model: String = "claude-sonnet-4-20250514",
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val sonnetModel: String? = null,
    val opusModel: String? = null,
    val maxModel: String? = null,
    val maxRetries: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 判断是否为特殊供应商
     */
    fun isSpecialProvider(): Boolean {
        return id in listOf(
            SpecialProviderIds.DISABLED,
            SpecialProviderIds.LOCAL_SETTINGS,
            SpecialProviderIds.CLI_LOGIN
        )
    }

    /**
     * 判断是否可编辑
     */
    fun isEditable(): Boolean {
        return when (id) {
            SpecialProviderIds.DISABLED, SpecialProviderIds.LOCAL_SETTINGS, SpecialProviderIds.CLI_LOGIN -> false
            else -> source != "cc-switch"  // cc-switch 导入的配置需要先转换
        }
    }

    /**
     * 判断是否可删除
     */
    fun isDeletable(): Boolean {
        return !isSpecialProvider()
    }
}
```

#### 3.1.2 ConfigManager 优化

```kotlin
// E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\application\config\ConfigManager.kt

class ConfigManager(project: Project) {
    // 获取所有供应商配置（包含特殊供应商）
    fun getAllProviderProfiles(): List<ProviderProfile>

    // 获取常规供应商配置（排除特殊供应商）
    fun getRegularProviderProfiles(): List<ProviderProfile>

    // 获取特殊供应商配置
    fun getSpecialProviderProfiles(): List<ProviderProfile>

    // 创建供应商配置
    suspend fun createProviderProfile(profile: ProviderProfile): Result<String>

    // 更新供应商配置
    suspend fun updateProviderProfile(profile: ProviderProfile): Result<Boolean>

    // 删除供应商配置
    suspend fun deleteProviderProfile(profileId: String): Result<Boolean>

    // 切换供应商配置
    suspend fun switchProviderProfile(profileId: String): Result<Boolean>

    // 转换 cc-switch 配置为本地配置
    suspend fun convertCcSwitchProfile(profileId: String): Result<ProviderProfile>

    // 对供应商配置排序
    suspend fun reorderProviderProfiles(orderIds: List<String>): Result<Boolean>

    // 从 cc-switch 导入供应商配置
    suspend fun importFromCcSwitch(dbPath: String?): Result<List<ProviderProfile>>
}
```

#### 3.1.3 ProviderHandler 消息处理器

```kotlin
// E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\handler\provider\ProviderHandler.kt

class ProviderHandler(private val context: HandlerContext) : BaseMessageHandler() {

    companion object {
        val SUPPORTED_TYPES = arrayOf(
            "get_providers",
            "get_current_config",
            "add_provider",
            "update_provider",
            "delete_provider",
            "switch_provider",
            "sort_providers",
            "convert_cc_switch_profile",
            "preview_cc_switch_import",
            "open_file_chooser_for_cc_switch",
            "save_imported_providers",
            "get_cli_login_account_info"
        )
    }

    override fun handle(type: String, content: String): Boolean {
        return when (type) {
            "get_providers" -> handleGetProviders()
            "add_provider" -> handleAddProvider(content)
            "update_provider" -> handleUpdateProvider(content)
            "delete_provider" -> handleDeleteProvider(content)
            "switch_provider" -> handleSwitchProvider(content)
            "sort_providers" -> handleSortProviders(content)
            "convert_cc_switch_profile" -> handleConvertCcSwitchProfile(content)
            "preview_cc_switch_import" -> handlePreviewCcSwitchImport()
            "open_file_chooser_for_cc_switch" -> handleOpenFileChooserForCcSwitch()
            "save_imported_providers" -> handleSaveImportedProviders(content)
            else -> false
        }
    }
}
```

### 3.2 前端优化

#### 3.2.1 ProviderList 组件（卡片式布局）

```tsx
// E:\work-File\code\ccgui\webview\src\features\model\components\ProviderList.tsx

import { cn } from '@/shared/utils/cn';
import { useProviderProfilesStore, type ProviderProfile } from '@/shared/stores/providerProfilesStore';
import { SPECIAL_PROVIDER_IDS } from '@/shared/constants/specialProviders';
import { useDragSort } from '@/shared/hooks/useDragSort';
import { Edit, Trash2, GripVertical, Check, Play, CircleSlash, File, Key } from 'lucide-react';

interface ProviderListProps {
  onEdit: (profile: ProviderProfile) => void;
  onDelete: (profileId: string) => void;
  onSwitch: (profileId: string) => void;
  onConvert: (profile: ProviderProfile) => void;
}

export function ProviderList({ onEdit, onDelete, onSwitch, onConvert }: ProviderListProps) {
  const {
    profiles,
    activeProfileId,
    deleteProfile,
    setActiveProfile
  } = useProviderProfilesStore();

  const [showLocalConfirm, setShowLocalConfirm] = useState(false);
  const [showCliLoginConfirm, setShowCliLoginConfirm] = useState(false);
  const [showDisableConfirm, setShowDisableConfirm] = useState(false);

  const { localItems, draggedId, dragOverId, handleDragStart, handleDragOver, handleDragLeave, handleDrop, handleDragEnd } =
    useDragSort({
      items: profiles,
      onSort: async (orderedIds) => {
        await window.ccBackend?.reorderProviderProfiles(orderedIds);
      },
      pinnedIds: [SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS, SPECIAL_PROVIDER_IDS.CLI_LOGIN]
    });

  const handleSwitchClick = (profileId: string) => {
    if (profileId === SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS) {
      setShowLocalConfirm(true);
    } else if (profileId === SPECIAL_PROVIDER_IDS.CLI_LOGIN) {
      setShowCliLoginConfirm(true);
    } else if (profileId === SPECIAL_PROVIDER_IDS.DISABLED) {
      setShowDisableConfirm(true);
    } else {
      onSwitch(profileId);
    }
  };

  return (
    <div className="flex flex-col gap-3">
      {/* 特殊供应商卡片 */}
      <SpecialProviderCard
        id={SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS}
        name="Local Settings"
        description="使用 ~/.claude/settings.json 配置"
        icon={<File className="w-4 h-4" />}
        isActive={activeProfileId === SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS}
        onSwitch={() => handleSwitchClick(SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS)}
      />

      <SpecialProviderCard
        id={SPECIAL_PROVIDER_IDS.CLI_LOGIN}
        name="CLI Login"
        description="使用 Claude CLI 原生 OAuth 登录"
        icon={<Key className="w-4 h-4" />}
        isActive={activeProfileId === SPECIAL_PROVIDER_IDS.CLI_LOGIN}
        onSwitch={() => handleSwitchClick(SPECIAL_PROVIDER_IDS.CLI_LOGIN)}
      />

      {/* 常规供应商卡片 */}
      {localItems
        .filter(p => !p.isSpecialProvider())
        .map(profile => (
          <ProviderCard
            key={profile.id}
            profile={profile}
            isActive={activeProfileId === profile.id}
            isDragging={draggedId === profile.id}
            isDragOver={dragOverId === profile.id}
            onDragStart={handleDragStart}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onDragEnd={handleDragEnd}
            onEdit={() => onEdit(profile)}
            onDelete={() => onDelete(profile.id)}
            onSwitch={() => onSwitch(profile.id)}
            onConvert={() => onConvert(profile)}
          />
        ))}
    </div>
  );
}

// 特殊供应商卡片组件
function SpecialProviderCard({
  id,
  name,
  description,
  icon,
  isActive,
  onSwitch
}: {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  isActive: boolean;
  onSwitch: () => void;
}) {
  return (
    <div
      className={cn(
        'flex items-center gap-3 p-4 rounded-lg border transition-all',
        'border-l-4',
        isActive
          ? 'bg-primary/5 border-primary/20 border-l-primary'
          : 'bg-background-secondary border-border hover:border-primary/30 border-l-orange-500'
      )}
    >
      <div className="shrink-0 w-8 h-8 rounded-full bg-orange-500/10 flex items-center justify-center text-orange-500">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-medium text-foreground">{name}</div>
        <div className="text-xs text-muted-foreground truncate">{description}</div>
      </div>
      <div className="shrink-0">
        {isActive ? (
          <button className="px-3 py-1.5 rounded-full text-xs font-medium bg-orange-500/10 text-orange-500 border border-orange-500/20">
            撤销授权
          </button>
        ) : (
          <button
            onClick={onSwitch}
            className="px-3 py-1.5 rounded-full text-xs font-medium bg-primary text-primary-foreground hover:opacity-90"
          >
            授权并启用
          </button>
        )}
      </div>
    </div>
  );
}

// 供应商卡片组件
function ProviderCard({
  profile,
  isActive,
  isDragging,
  isDragOver,
  onDragStart,
  onDragOver,
  onDragLeave,
  onDrop,
  onDragEnd,
  onEdit,
  onDelete,
  onSwitch,
  onConvert
}: {
  profile: ProviderProfile;
  isActive: boolean;
  isDragging: boolean;
  isDragOver: boolean;
  onDragStart: (e: React.DragEvent) => void;
  onDragOver: (e: React.DragEvent) => void;
  onDragLeave: (e: React.DragEvent) => void;
  onDrop: (e: React.DragEvent) => void;
  onDragEnd: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onSwitch: () => void;
  onConvert: () => void;
}) {
  return (
    <div
      draggable
      onDragStart={onDragStart}
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
      onDragEnd={onDragEnd}
      className={cn(
        'flex items-center gap-3 p-4 rounded-lg border transition-all',
        isActive
          ? 'bg-primary/5 border-primary/20'
          : 'bg-background-secondary border-border hover:border-primary/30',
        isDragging && 'opacity-50',
        isDragOver && 'border-primary'
      )}
    >
      {/* 拖拽手柄 */}
      <div className="cursor-grab active:cursor-grabbing text-muted-foreground hover:text-foreground">
        <GripVertical className="w-4 h-4" />
      </div>

      {/* 供应商信息 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-foreground truncate">{profile.name}</span>
          {profile.source === 'cc-switch' && (
            <span className="shrink-0 px-1.5 py-0.5 rounded text-xs bg-muted text-muted-foreground">
              cc-switch
            </span>
          )}
        </div>
        <div className="text-xs text-muted-foreground truncate">
          {profile.provider} / {profile.model}
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-1 shrink-0">
        {isActive ? (
          <span className="flex items-center gap-1 px-2 py-1 rounded text-xs text-primary font-medium">
            <Check className="w-3 h-3" />
            使用中
          </span>
        ) : (
          <button
            onClick={onSwitch}
            className="px-2.5 py-1 rounded text-xs bg-primary/10 text-primary hover:bg-primary/20 font-medium"
          >
            启用
          </button>
        )}

        <div className="w-px h-5 bg-border mx-1" />

        <div className="flex items-center gap-0.5">
          {profile.source === 'cc-switch' && (
            <button
              onClick={onConvert}
              className="p-1.5 rounded hover:bg-background-elevated text-muted-foreground hover:text-foreground"
              title="转换为本地配置"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
              </svg>
            </button>
          )}
          <button
            onClick={onEdit}
            className="p-1.5 rounded hover:bg-background-elevated text-muted-foreground hover:text-foreground"
            title="编辑"
          >
            <Edit className="w-4 h-4" />
          </button>
          <button
            onClick={onDelete}
            className="p-1.5 rounded hover:bg-destructive/10 text-muted-foreground hover:text-destructive"
            title="删除"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
```

#### 3.2.2 拖拽排序 Hook

```ts
// E:\work-File\code\ccgui\webview\src\shared\hooks\useDragSort.ts

import { useState, useCallback } from 'react';

interface UseDragSortOptions<T> {
  items: T[];
  onSort: (orderedIds: string[]) => void | Promise<void>;
  pinnedIds?: string[];
}

interface DragSortState<T> {
  localItems: T[];
  draggedId: string | null;
  dragOverId: string | null;
  handleDragStart: (e: React.DragEvent, id: string) => void;
  handleDragOver: (e: React.DragEvent, id: string) => void;
  handleDragLeave: () => void;
  handleDrop: (e: React.DragEvent, id: string) => void;
  handleDragEnd: () => void;
}

export function useDragSort<T extends { id: string }>({
  items,
  onSort,
  pinnedIds = []
}: UseDragSortOptions<T>): DragSortState<T> {
  const [draggedId, setDraggedId] = useState<string | null>(null);
  const [dragOverId, setDragOverId] = useState<string | null>(null);
  const [localItems, setLocalItems] = useState<T[]>(items);

  // 更新本地 items
  useState(() => {
    setLocalItems(items);
  }, [items]);

  const handleDragStart = useCallback((e: React.DragEvent, id: string) => {
    if (pinnedIds.includes(id)) {
      e.preventDefault();
      return;
    }
    setDraggedId(id);
    e.dataTransfer.effectAllowed = 'move';
  }, [pinnedIds]);

  const handleDragOver = useCallback((e: React.DragEvent, id: string) => {
    e.preventDefault();
    if (draggedId && draggedId !== id && !pinnedIds.includes(id)) {
      setDragOverId(id);

      // 重新排序
      const draggedIndex = localItems.findIndex(item => item.id === draggedId);
      const targetIndex = localItems.findIndex(item => item.id === id);

      if (draggedIndex !== -1 && targetIndex !== -1) {
        const newItems = [...localItems];
        const [removed] = newItems.splice(draggedIndex, 1);
        newItems.splice(targetIndex, 0, removed);
        setLocalItems(newItems);
      }
    }
  }, [draggedId, localItems, pinnedIds]);

  const handleDragLeave = useCallback(() => {
    // 延迟清除，避免闪烁
    setTimeout(() => {
      setDragOverId(null);
    }, 100);
  }, []);

  const handleDrop = useCallback(async (e: React.DragEvent, id: string) => {
    e.preventDefault();
    const orderedIds = localItems.map(item => item.id);
    await onSort(orderedIds);
    setDraggedId(null);
    setDragOverId(null);
  }, [localItems, onSort]);

  const handleDragEnd = useCallback(() => {
    setDraggedId(null);
    setDragOverId(null);
  }, []);

  return {
    localItems,
    draggedId,
    dragOverId,
    handleDragStart,
    handleDragOver,
    handleDragLeave,
    handleDrop,
    handleDragEnd
  };
}
```

#### 3.2.3 cc-switch 导入组件

```tsx
// E:\work-File\code\ccgui\webview\src\features\model\components\CcSwitchImportDialog.tsx

import { useState, useEffect } from 'react';
import { cn } from '@/shared/utils/cn';

interface CcSwitchImportDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (providers: ProviderProfile[]) => void;
}

interface ImportProvider {
  id: string;
  name: string;
  status: 'new' | 'update' | 'conflict';
  existingProvider?: ProviderProfile;
}

export function CcSwitchImportDialog({
  isOpen,
  onClose,
  onConfirm
}: CcSwitchImportDialogProps) {
  const [providers, setProviders] = useState<ImportProvider[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(false);
  const [strategy, setStrategy] = useState<'all' | 'selected'>('all');

  useEffect(() => {
    if (isOpen) {
      loadPreview();
    }
  }, [isOpen]);

  const loadPreview = async () => {
    setLoading(true);
    try {
      const result = await window.ccBackend?.previewCcSwitchImport();
      if (result?.providers) {
        setProviders(result.providers);
        setSelectedIds(new Set(result.providers.map((p: ImportProvider) => p.id)));
      }
    } finally {
      setLoading(false);
    }
  };

  const handleToggleSelect = (id: string) => {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleToggleAll = () => {
    if (selectedIds.size === providers.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(providers.map(p => p.id)));
    }
  };

  const handleConfirm = () => {
    const selected = providers.filter(p => selectedIds.has(p.id));
    onConfirm(selected);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* 遮罩 */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />

      {/* 弹窗 */}
      <div className="relative w-full max-w-2xl max-h-[80vh] bg-popover border border-border rounded-lg shadow-xl overflow-hidden flex flex-col">
        {/* 头部 */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
          <h2 className="text-base font-semibold text-foreground">
            从 cc-switch 导入供应商配置
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded hover:bg-muted transition-colors"
          >
            <svg className="w-5 h-5 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* 内容 */}
        <div className="flex-1 overflow-y-auto p-4">
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
              <span className="ml-3 text-sm text-muted-foreground">正在读取 cc-switch 配置...</span>
            </div>
          ) : providers.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-sm text-muted-foreground">未找到 cc-switch 配置</p>
            </div>
          ) : (
            <>
              <p className="text-sm text-muted-foreground mb-4">
                找到 {providers.length} 个供应商配置，请选择要导入的配置：
              </p>

              {/* 导入策略 */}
              <div className="mb-4 p-3 rounded-md bg-background-secondary border border-border">
                <label className="block text-sm font-medium text-foreground mb-2">导入策略</label>
                <div className="space-y-2">
                  <label className="flex items-start gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="strategy"
                      checked={strategy === 'all'}
                      onChange={() => setStrategy('all')}
                      className="mt-1"
                    />
                    <div>
                      <div className="text-sm font-medium">全部导入</div>
                      <div className="text-xs text-muted-foreground">导入所有选中的配置，新增配置直接添加，已存在的配置将被更新</div>
                    </div>
                  </label>
                  <label className="flex items-start gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="strategy"
                      checked={strategy === 'selected'}
                      onChange={() => setStrategy('selected')}
                      className="mt-1"
                    />
                    <div>
                      <div className="text-sm font-medium">仅导入新增</div>
                      <div className="text-xs text-muted-foreground">仅导入不存在的配置，已存在的配置保持不变</div>
                    </div>
                  </label>
                </div>
              </div>

              {/* 表头 */}
              <div className="flex items-center px-3 py-2 rounded bg-background-secondary border border-border text-xs font-medium text-muted-foreground mb-2">
                <div className="w-6">
                  <input
                    type="checkbox"
                    checked={selectedIds.size === providers.length}
                    onChange={handleToggleAll}
                  />
                </div>
                <div className="flex-1 px-2">配置名称</div>
                <div className="flex-1 px-2">配置 ID</div>
                <div className="w-20 px-2">状态</div>
              </div>

              {/* 配置列表 */}
              <div className="space-y-1">
                {providers.map(provider => (
                  <div
                    key={provider.id}
                    className={cn(
                      'flex items-center px-3 py-2 rounded border cursor-pointer transition-colors',
                      selectedIds.has(provider.id)
                        ? 'bg-primary/5 border-primary/30'
                        : 'bg-background border-border hover:border-primary/20'
                    )}
                    onClick={() => handleToggleSelect(provider.id)}
                  >
                    <div className="w-6">
                      <input
                        type="checkbox"
                        checked={selectedIds.has(provider.id)}
                        onChange={() => handleToggleSelect(provider.id)}
                        onClick={e => e.stopPropagation()}
                      />
                    </div>
                    <div className="flex-1 px-2 text-sm text-foreground truncate">{provider.name}</div>
                    <div className="flex-1 px-2 text-xs text-muted-foreground font-mono truncate">{provider.id}</div>
                    <div className="w-20 px-2">
                      {provider.status === 'new' && (
                        <span className="px-2 py-0.5 rounded text-xs bg-green-500/10 text-green-500">新增</span>
                      )}
                      {provider.status === 'update' && (
                        <span className="px-2 py-0.5 rounded text-xs bg-orange-500/10 text-orange-500">更新</span>
                      )}
                      {provider.status === 'conflict' && (
                        <span className="px-2 py-0.5 rounded text-xs bg-red-500/10 text-red-500">冲突</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {/* 底部 */}
        <div className="flex items-center justify-between px-4 py-3 border-t border-border shrink-0">
          <span className="text-xs text-muted-foreground">
            已选择 {selectedIds.size} / {providers.length} 个配置
          </span>
          <div className="flex gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-md text-sm font-medium bg-background-secondary text-foreground hover:bg-background-elevated"
            >
              取消
            </button>
            <button
              onClick={handleConfirm}
              disabled={selectedIds.size === 0}
              className="px-4 py-2 rounded-md text-sm font-medium bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              导入选中的 {selectedIds.size} 个配置
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
```

### 3.3 优化后的 ModelConfigPanel

```tsx
// E:\work-File\code\ccgui\webview\src\features\model\components\ModelConfigPanel.tsx

import { memo, useState, useCallback } from 'react';
import { CloudDownload, Plus } from 'lucide-react';
import { cn } from '@/shared/utils/cn';
import { useProviderProfilesStore, type ProviderProfile } from '@/shared/stores/providerProfilesStore';
import { ProviderList } from './ProviderList';
import { ProfileEditorModal } from './ProfileEditorModal';
import { CcSwitchImportDialog } from './CcSwitchImportDialog';
import { ConfirmDialog } from '@/shared/components/ConfirmDialog';

export const ModelConfigPanel = memo(function ModelConfigPanel() {
  const {
    profiles,
    activeProfileId,
    isLoading,
    error,
    loadProfiles,
    createProfile,
    updateProfile,
    deleteProfile,
    setActiveProfile
  } = useProviderProfilesStore();

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingProfile, setEditingProfile] = useState<ProviderProfile | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [confirmConfig, setConfirmConfig] = useState<{
    isOpen: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
  }>({
    isOpen: false,
    title: '',
    message: '',
    onConfirm: () => {}
  });

  // 加载配置
  useState(() => {
    loadProfiles();
  }, []);

  // 新建配置
  const handleNew = useCallback(() => {
    setEditingProfile(null);
    setEditorOpen(true);
  }, []);

  // 编辑配置
  const handleEdit = useCallback((profile: ProviderProfile) => {
    if (profile.source === 'cc-switch') {
      setConfirmConfig({
        isOpen: true,
        title: '编辑 cc-switch 配置',
        message: '此配置是从 cc-switch 导入的，直接编辑可能会在下次导入时被覆盖。建议先转换为本地配置。',
        onConfirm: () => {
          setConfirmConfig(prev => ({ ...prev, isOpen: false }));
          setEditingProfile(profile);
          setEditorOpen(true);
        }
      });
    } else {
      setEditingProfile(profile);
      setEditorOpen(true);
    }
  }, []);

  // 删除配置
  const handleDelete = useCallback(async (profileId: string) => {
    setConfirmConfig({
      isOpen: true,
      title: '删除配置',
      message: '确定要删除此配置吗？此操作不可撤销。',
      onConfirm: async () => {
        setConfirmConfig(prev => ({ ...prev, isOpen: false }));
        await deleteProfile(profileId);
      }
    });
  }, [deleteProfile]);

  // 切换配置
  const handleSwitch = useCallback(async (profileId: string) => {
    await setActiveProfile(profileId);
  }, [setActiveProfile]);

  // 转换 cc-switch 配置
  const handleConvert = useCallback(async (profile: ProviderProfile) => {
    setConfirmConfig({
      isOpen: true,
      title: '转换为本地配置',
      message: `确定要将 "${profile.name}" 转换为本地配置吗？转换后将不再与 cc-switch 同步。`,
      onConfirm: async () => {
        setConfirmConfig(prev => ({ ...prev, isOpen: false }));
        const newProfile = await window.ccBackend?.convertCcSwitchProfile(profile.id);
        if (newProfile) {
          await loadProfiles();
        }
      }
    });
  }, [loadProfiles]);

  // 保存配置
  const handleSave = useCallback(async (data: Omit<ProviderProfile, 'id'> & { id?: string }, isNew: boolean) => {
    if (isNew) {
      const id = await createProfile(data);
      if (id) {
        await setActiveProfile(id);
      }
    } else if (editingProfile) {
      await updateProfile({ ...data, id: editingProfile.id } as ProviderProfile);
    }
  }, [createProfile, updateProfile, setActiveProfile, editingProfile]);

  // 导入配置
  const handleImport = useCallback(async (providers: ProviderProfile[]) => {
    await window.ccBackend?.saveImportedProviders(providers);
    await loadProfiles();
  }, [loadProfiles]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="text-muted-foreground text-sm">加载中...</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* 错误提示 */}
      {error && (
        <div className="px-4 py-3 rounded-md bg-destructive/10 border border-destructive/20 text-destructive text-sm">
          {error}
        </div>
      )}

      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">供应商配置</h3>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setImportOpen(true)}
            className={cn(
              'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium',
              'border border-border bg-background text-foreground',
              'hover:bg-background-secondary transition-colors'
            )}
          >
            <CloudDownload className="w-3.5 h-3.5" />
            导入
          </button>
          <button
            onClick={handleNew}
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

      {/* 配置列表 */}
      <ProviderList
        onEdit={handleEdit}
        onDelete={handleDelete}
        onSwitch={handleSwitch}
        onConvert={handleConvert}
      />

      {/* 编辑弹窗 */}
      <ProfileEditorModal
        isOpen={editorOpen}
        onClose={() => setEditorOpen(false)}
        initialProfile={editingProfile}
        onSave={handleSave}
      />

      {/* 导入弹窗 */}
      <CcSwitchImportDialog
        isOpen={importOpen}
        onClose={() => setImportOpen(false)}
        onConfirm={handleImport}
      />

      {/* 确认对话框 */}
      <ConfirmDialog
        isOpen={confirmConfig.isOpen}
        title={confirmConfig.title}
        message={confirmConfig.message}
        onConfirm={() => confirmConfig.onConfirm()}
        onCancel={() => setConfirmConfig(prev => ({ ...prev, isOpen: false }))}
      />
    </div>
  );
});
```

## 四、优化后的架构对比

### 4.1 数据流对比

**jetbrains-cc-gui 数据流**:
```
前端 (ProviderList) → sendToJava('add_provider') → ProviderHandler.handleAddProvider()
→ ProviderManager.addProvider() → ClaudeSettingsManager.applyProviderToClaudeSettings()
→ callJavaScript('window.updateProviders') → 前端更新
```

**cc-gui 优化后数据流**:
```
前端 (ProviderList) → window.ccBackend.createProviderProfile() → CefBrowserPanel.jsQuery
→ BridgeManager.createProviderProfile() → ConfigManager.createProviderProfile()
→ ProviderProfilesStore.loadProfiles() → 前端更新
```

### 4.2 组件层级对比

**jetbrains-cc-gui**:
```
SettingsView
└── ProviderManageSection
    └── ProviderList
        ├── ProviderCard (常规供应商)
        ├── SpecialProviderCard (特殊供应商)
        └── ImportConfirmDialog
```

**cc-gui 优化后**:
```
ToolsView
└── ModelConfigPanel
    ├── ProviderList
    │   ├── SpecialProviderCard (特殊供应商)
    │   └── ProviderCard (常规供应商)
    ├── ProfileEditorModal
    └── CcSwitchImportDialog
```

## 五、实施计划

### 5.1 后端实施步骤
1. ✅ 优化 ProviderProfile 模型，添加特殊供应商支持
2. ✅ 扩展 ConfigManager，添加特殊供应商管理方法
3. ✅ 创建 ProviderHandler，统一处理供应商相关消息
4. ✅ 实现 cc-switch 导入功能
5. ✅ 实现拖拽排序功能

### 5.2 前端实施步骤
1. ✅ 创建 useDragSort Hook
2. ✅ 创建 ProviderList 组件（卡片式布局）
3. ✅ 创建 SpecialProviderCard 组件
4. ✅ 创建 CcSwitchImportDialog 组件
5. ✅ 优化 ModelConfigPanel 组件
6. ✅ 更新 ProviderProfilesStore，添加特殊供应商支持

### 5.3 测试步骤
1. 测试特殊供应商的启用和禁用
2. 测试拖拽排序功能
3. 测试 cc-switch 导入功能
4. 测试配置的 CRUD 操作
5. 测试配置切换功能

## 六、注意事项

### 6.1 兼容性
- 保持与现有配置格式的兼容性
- 特殊供应商 ID 需要与 jetbrains-cc-gui 保持一致

### 6.2 安全性
- API Key 需要加密存储
- CLI Login 模式需要正确的权限处理

### 6.3 用户体验
- 特殊供应商需要明显的视觉区分
- 拖拽排序需要流畅的动画效果
- 导入功能需要清晰的进度提示

### 6.4 错误处理
- cc-switch 导入失败时的友好提示
- 配置切换失败时的回滚机制
- 拖拽排序失败时的恢复机制
