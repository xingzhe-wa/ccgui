# 后端 API 实现总结

## 实现日期
2026-04-11

## 实现概述

为 `cc-gui` 项目实现了缺失的后端 API，以支持供应商管理模块的拖拽排序和 cc-switch 配置转换功能。

## 实现的 API

### 1. reorderProviderProfiles

**前端调用**:
```typescript
await window.ccBackend?.reorderProviderProfiles(orderedIds);
```

**后端实现位置**:
- `ConfigManager.reorderProviderProfiles(orderedIds: List<String>)`
- `CefBrowserPanel.handleReorderProviderProfiles(queryId, params)`

**功能描述**:
根据前端传入的 ID 列表重新排序供应商配置。特殊供应商（Local Settings、CLI Login、Disabled）不会被排序，保持固定在顶部。

**参数**:
- `orderedIds`: `List<String>` - 按新顺序排列的 Profile ID 列表

**返回值**:
```json
{
  "success": true
}
```

**实现细节**:
1. 将现有配置按 ID 映射为 Map
2. 按 orderedIds 顺序重新排列配置
3. 保留未在 orderedIds 中的配置（如特殊供应商）
4. 保存到配置文件

---

### 2. convertCcSwitchProfile

**前端调用**:
```typescript
const result = await window.ccBackend?.convertCcSwitchProfile(profileId);
```

**后端实现位置**:
- `ConfigManager.convertCcSwitchProfile(profileId: String): ProviderProfile?`
- `CefBrowserPanel.handleConvertCcSwitchProfile(queryId, params)`

**功能描述**:
将从 cc-switch 导入的配置转换为本地配置，解除与 cc-switch 的关联，使其可自由编辑。

**参数**:
- `profileId`: `String` - 要转换的 Profile ID

**返回值**:
```json
{
  "success": true,
  "profile": {
    "id": "original_id_local",
    "name": "Original Name (Local)",
    "provider": "anthropic",
    "source": "local",
    "model": "claude-sonnet-4-20250514",
    "apiKey": "sk-xxx",
    "baseUrl": "https://api.anthropic.com",
    "sonnetModel": "claude-sonnet-4-20250514",
    "opusModel": "claude-opus-4-20250514",
    "maxModel": "claude-3-5-haiku-20250514",
    "maxRetries": 3,
    "createdAt": 1234567890000,
    "updatedAt": 1234567890000
  }
}
```

**错误返回**:
```json
{
  "success": false,
  "error": "Failed to convert profile"
}
```

**实现细节**:
1. 检查配置来源是否为 cc-switch
2. 创建本地配置副本（ID 添加 `_local` 后缀，名称添加 `(Local)` 后缀）
3. 保存新配置
4. 删除原配置
5. 如果原配置是激活的，激活新配置

---

## 修改的文件

### 1. ConfigManager.kt

**文件路径**: `E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\application\config\ConfigManager.kt`

**新增方法**:

```kotlin
/**
 * 重新排序 Provider Profiles
 */
fun reorderProviderProfiles(orderedIds: List<String>)

/**
 * 转换 cc-switch 配置为本地配置
 */
fun convertCcSwitchProfile(profileId: String): ProviderProfile?
```

### 2. CefBrowserPanel.kt

**文件路径**: `E:\work-File\code\ccgui\src\main\kotlin\com\github\xingzhewa\ccgui\browser\CefBrowserPanel.kt`

**修改内容**:

1. **在 handleJsRequest 方法中添加新的 action 处理**:
```kotlin
"reorderProviderProfiles" -> handleReorderProviderProfiles(queryId, params)
"convertCcSwitchProfile" -> handleConvertCcSwitchProfile(queryId, params)
```

2. **更新 handleGetProviderProfiles 方法**:
- 添加 `source`、`createdAt`、`updatedAt` 字段到返回数据

3. **新增处理器方法**:
```kotlin
private fun handleReorderProviderProfiles(queryId: Int, params: JsonElement?): Any?
private fun handleConvertCcSwitchProfile(queryId: Int, params: JsonElement?): Any?
```

---

## API 完整列表

### Provider Profiles 相关

| Action | 方法 | 描述 |
|--------|------|------|
| `getProviderProfiles` | `handleGetProviderProfiles` | 获取所有供应商配置 |
| `createProviderProfile` | `handleCreateProviderProfile` | 创建新的供应商配置 |
| `updateProviderProfile` | `handleUpdateProviderProfile` | 更新供应商配置 |
| `deleteProviderProfile` | `handleDeleteProviderProfile` | 删除供应商配置 |
| `setActiveProviderProfile` | `handleSetActiveProviderProfile` | 设置激活的供应商配置 |
| `reorderProviderProfiles` | `handleReorderProviderProfiles` | **新增** - 重新排序供应商配置 |
| `convertCcSwitchProfile` | `handleConvertCcSwitchProfile` | **新增** - 转换 cc-switch 配置 |

---

## 测试建议

### 测试 reorderProviderProfiles

1. 创建多个供应商配置
2. 拖拽改变顺序
3. 刷新页面验证顺序是否保持
4. 验证特殊供应商始终在顶部

### 测试 convertCcSwitchProfile

1. 创建一个 source 为 "cc-switch" 的配置
2. 点击转换按钮
3. 验证新配置 ID 添加了 "_local" 后缀
4. 验证新配置 source 变为 "local"
5. 验证原配置已被删除
6. 验证如果原配置是激活的，新配置自动激活

---

## 注意事项

### 1. 特殊供应商处理

特殊供应商（Local Settings、CLI Login、Disabled）的 ID：
- `__disabled__`
- `__local_settings_json__`
- `__cli_login__`

这些供应商：
- 不会参与拖拽排序
- 始终显示在列表顶部
- 不可编辑和删除

### 2. cc-switch 配置识别

cc-switch 导入的配置特征：
- `source` 字段为 "cc-switch"
- 需要先转换才能编辑
- 转换后 `source` 变为 "local"

### 3. 配置持久化

所有配置变更都会：
- 保存到项目级 `ccgui-config.xml` 文件
- 通过 `ConfigStorage` 进行持久化
- 触发 `ConfigChangedEvent` 事件

---

## 后续工作

### 可选的增强功能

1. **cc-switch 导入功能**:
   - 读取 `~/.cc-switch/cc-switch.db` 数据库
   - 解析供应商配置
   - 显示导入预览对话框
   - 批量导入配置

2. **拖拽排序动画优化**:
   - 添加拖拽时的平滑动画效果
   - 显示拖拽位置的预览

3. **配置验证**:
   - 创建配置时验证必填字段
   - API Key 格式验证
   - Base URL 可达性测试

---

## 实现完成状态

✅ `reorderProviderProfiles` - 已实现
✅ `convertCcSwitchProfile` - 已实现
✅ `handleGetProviderProfiles` - 已更新包含新字段
✅ 前后端类型对齐 - 已完成

所有缺失的后端 API 已实现完成，可以与前端代码正常配合工作。
