# 供应商管理模块优化总结

## 优化概述

参考 `jetbrains-cc-gui` 项目的供应商管理模块设计，对 `cc-gui` 项目的供应商管理模块进行了全面优化，包括：

1. ✅ 添加特殊供应商支持（Local Settings、CLI Login、Disabled）
2. ✅ 添加拖拽排序功能
3. ✅ 优化 UI 布局为卡片式
4. ✅ 添加 cc-switch 导入功能准备
5. ✅ 优化前后端数据模型

## 优化内容详情

### 1. 后端优化

#### 1.1 ProviderProfile 模型优化

**文件**: `E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\model\config\ProviderProfile.kt`

**新增内容**:
- 添加 `SpecialProviderIds` 对象，定义特殊供应商 ID 常量
- 添加 `source` 字段，标识配置来源（local/cc-switch/cli-login/disabled）
- 添加 `createdAt` 和 `updatedAt` 时间戳字段
- 添加 `isSpecialProvider()` 方法，判断是否为特殊供应商
- 添加 `isEditable()` 方法，判断是否可编辑
- 添加 `isDeletable()` 方法，判断是否可删除
- 添加 `isSortable()` 方法，判断是否可排序
- 添加 `toLocalProfile()` 方法，转换 cc-switch 配置为本地配置
- 添加 `createSpecialProvider()` 伴生对象方法，创建特殊供应商配置
- 添加 `fromCcSwitch()` 伴生对象方法，从 cc-switch 导入配置

**特殊供应商 ID**:
```kotlin
object SpecialProviderIds {
    const val DISABLED = "__disabled__"
    const val LOCAL_SETTINGS = "__local_settings_json__"
    const val CLI_LOGIN = "__cli_login__"
}
```

### 2. 前端优化

#### 2.1 特殊供应商常量

**文件**: `E:\work-File\code\ccgui\webview\src\shared\constants\specialProviders.ts`

**新增内容**:
- `SPECIAL_PROVIDER_IDS` 常量对象
- `SPECIAL_PROVIDERS` 配置对象（包含名称、描述、图标）
- `isSpecialProviderId()` 类型守卫函数
- `isSpecialProvider()` 判断函数

#### 2.2 拖拽排序 Hook

**文件**: `E:\work-File\code\ccgui\webview\src\shared\hooks\useDragSort.ts`

**新增内容**:
- `useDragSort` Hook，支持拖拽排序
- 支持固定项（pinnedIds）不可拖拽
- 自动处理拖拽过程中的视觉反馈
- 拖拽完成后触发排序回调

**使用示例**:
```tsx
const { localItems, draggedId, dragOverId, handleDragStart, handleDragOver, handleDragLeave, handleDrop, handleDragEnd } =
  useDragSort({
    items: profiles,
    onSort: async (orderedIds) => {
      await window.ccBackend?.reorderProviderProfiles(orderedIds);
    },
    pinnedIds: [SPECIAL_PROVIDER_IDS.LOCAL_SETTINGS, SPECIAL_PROVIDER_IDS.CLI_LOGIN]
  });
```

#### 2.3 ProviderList 组件

**文件**: `E:\work-File\code\ccgui\webview\src\features\model\components\ProviderList.tsx`

**新增内容**:
- 卡片式布局，替代原有列表式布局
- `SpecialProviderCard` 组件，显示特殊供应商
- `ProviderCard` 组件，显示常规供应商
- 支持拖拽排序
- 支持 cc-switch 标记和转换按钮
- 拖拽手柄（GripVertical 图标）
- 激活状态指示

**UI 特点**:
- 特殊供应商：左侧橙色边框，特殊图标
- 常规供应商：卡片式布局，拖拽手柄，操作按钮
- 激活状态：蓝色边框和背景
- 拖拽状态：半透明效果
- cc-switch 标记：灰色标签

#### 2.4 ProviderProfilesStore 优化

**文件**: `E:\work-File\code\ccgui\webview\src\shared\stores\providerProfilesStore.ts`

**新增内容**:
- 更新 `ProviderProfile` 接口，添加 `source`、`createdAt`、`updatedAt` 字段
- 添加 `reorderProfiles()` 方法，支持排序
- 添加 `convertCcSwitchProfile()` 方法，支持转换 cc-switch 配置
- 添加 `getRegularProfiles()` 方法，获取常规供应商
- 添加 `getSpecialProfiles()` 方法，获取特殊供应商
- `loadProfiles()` 自动确保特殊供应商始终存在

### 3. 文档

**文件**: `E:\work-File\code\ccgui\docs\PROVIDER_MANAGEMENT_OPTIMIZATION.md`

**内容**:
- 详细的对比分析（jetbrains-cc-gui vs cc-gui）
- 优化目标和方案
- 具体实施计划
- 代码示例和架构对比

## 优化效果

### UI 效果对比

**优化前**:
- 列表式布局
- 无特殊供应商支持
- 无拖拽排序
- 无 cc-switch 导入

**优化后**:
- 卡片式布局
- 特殊供应商（Local Settings、CLI Login）显示在顶部
- 支持拖拽排序
- cc-switch 标记和转换按钮
- 更清晰的视觉层次

### 功能增强

1. **特殊供应商**: 用户可以选择使用本地 settings.json 或 CLI Login
2. **拖拽排序**: 用户可以自定义供应商顺序
3. **cc-switch 支持**: 为从 cc-switch 导入配置做好准备
4. **编辑保护**: cc-switch 配置编辑前有警告

## 后续工作

### 需要后端支持的功能

1. **cc-switch 导入功能**:
   - 需要后端实现读取 cc-switch 数据库
   - 需要后端实现导入预览和保存

2. **拖拽排序持久化**:
   - 需要后端实现 `reorderProviderProfiles()` 方法
   - 需要在配置中保存排序顺序

3. **特殊供应商激活**:
   - 需要后端实现特殊供应商的激活逻辑
   - Local Settings: 直接使用 ~/.claude/settings.json
   - CLI Login: 设置 CLI Login 授权标记

### 测试建议

1. 测试特殊供应商的启用和禁用
2. 测试拖拽排序功能
3. 测试 cc-switch 导入功能（后端实现后）
4. 测试配置的 CRUD 操作
5. 测试配置切换功能

## 注意事项

### 兼容性

- 保持与现有配置格式的兼容性
- 特殊供应商 ID 与 jetbrains-cc-gui 保持一致

### 安全性

- API Key 需要加密存储
- CLI Login 模式需要正确的权限处理

### 用户体验

- 特殊供应商需要明显的视觉区分
- 拖拽排序需要流畅的动画效果
- 导入功能需要清晰的进度提示

## 文件清单

### 新增文件

1. `E:\work-File\code\ccgui\webview\src\shared\constants\specialProviders.ts`
2. `E:\work-File\code\ccgui\webview\src\shared\hooks\useDragSort.ts`
3. `E:\work-File\code\ccgui\webview\src\features\model\components\ProviderList.tsx`
4. `E:\work-File\code\ccgui\docs\PROVIDER_MANAGEMENT_OPTIMIZATION.md`
5. `E:\work-File\code\ccgui\docs\PROVIDER_MANAGEMENT_OPTIMIZATION_SUMMARY.md`

### 修改文件

1. `E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\model\config\ProviderProfile.kt`
2. `E:\work-File\code\ccgui\webview\src\shared\stores\providerProfilesStore.ts`

## 总结

本次优化参考了 `jetbrains-cc-gui` 项目的供应商管理模块设计，为 `cc-gui` 项目添加了特殊供应商支持、拖拽排序功能和更美观的卡片式 UI 布局。优化后的模块在保持原有功能的基础上，提供了更丰富的功能和更好的用户体验。

后续还需要后端支持 cc-switch 导入功能、拖拽排序持久化和特殊供应商激活逻辑，以完整实现所有优化功能。
