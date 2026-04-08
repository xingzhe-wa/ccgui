/**
 * 存储管理器
 * 用于前端内部数据持久化（通过 sessionStorage）
 */
class StorageManager {
  private prefix = 'ccgui_';

  /**
   * 设置项
   */
  setItem<T>(key: string, value: T): void {
    try {
      const serialized = JSON.stringify(value);
      sessionStorage.setItem(this.prefix + key, serialized);
    } catch (error) {
      console.error('Failed to set item:', error);
    }
  }

  /**
   * 获取项
   */
  getItem<T>(key: string): T | null {
    try {
      const item = sessionStorage.getItem(this.prefix + key);
      if (item === null) return null;
      return JSON.parse(item) as T;
    } catch (error) {
      console.error('Failed to get item:', error);
      return null;
    }
  }

  /**
   * 删除项
   */
  removeItem(key: string): void {
    sessionStorage.removeItem(this.prefix + key);
  }

  /**
   * 清空所有项
   */
  clear(): void {
    const keys = Object.keys(sessionStorage);
    keys.forEach((key) => {
      if (key.startsWith(this.prefix)) {
        sessionStorage.removeItem(key);
      }
    });
  }

  /**
   * 获取所有键
   */
  keys(): string[] {
    const allKeys = Object.keys(sessionStorage);
    return allKeys
      .filter((key) => key.startsWith(this.prefix))
      .map((key) => key.slice(this.prefix.length));
  }

  /**
   * 批量设置
   */
  setItems<T extends Record<string, any>>(items: T): void {
    Object.entries(items).forEach(([key, value]) => {
      this.setItem(key, value);
    });
  }

  /**
   * 批量获取
   */
  getItems<T extends string[]>(keys: T): Record<T[number], any> {
    const result: Record<string, any> = {};
    keys.forEach((key) => {
      result[key] = this.getItem(key);
    });
    return result as Record<T[number], any>;
  }
}

export const storageManager = new StorageManager();
