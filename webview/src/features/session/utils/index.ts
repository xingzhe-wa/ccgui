/**
 * Session Feature Utilities
 */

// Persistence
export {
  saveSessionsToStorage,
  loadSessionsFromStorage,
  backupSession,
  getSessionBackup,
  deleteSessionBackup,
  cleanupExpiredBackups,
  exportSessionAsJson,
  importSessionFromJson,
  downloadSessionAsJson,
  importSessionFromFile
} from './sessionPersistence';

// Export
export {
  exportSessionToMarkdown,
  exportSessionsToMarkdown,
  downloadSessionAsMarkdown,
  downloadSessionsAsMarkdown
} from './exportToMarkdown';

export { downloadSessionAsPDF, printSession } from './exportToPDF';

// Import
export {
  parseSessionFromJson,
  parseSessionFromMarkdown,
  validateSession,
  importSessionsFromFiles
} from './importSession';
export type { ImportValidationResult } from './importSession';