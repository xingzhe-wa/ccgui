#!/usr/bin/env node

/**
 * Copy dist/index.html to src/main/resources/html/claude-chat.html
 * This script is run automatically after `npm run build`
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const cwd = process.cwd();
const distFile = path.resolve(cwd, 'dist/index.html');
const targetDir = path.resolve(cwd, '../src/main/resources/html');
const targetFile = path.resolve(targetDir, 'claude-chat.html');

console.log('[copy-dist] Copying built frontend to resources...');
console.log('[copy-dist] Source:', distFile);
console.log('[copy-dist] Target:', targetFile);

try {
  // Ensure target directory exists
  if (!fs.existsSync(targetDir)) {
    fs.mkdirSync(targetDir, { recursive: true });
    console.log('[copy-dist] Created directory:', targetDir);
  }

  // Check if source file exists
  if (!fs.existsSync(distFile)) {
    console.error('[copy-dist] ERROR: Source file not found:', distFile);
    console.error('[copy-dist] Make sure to run "npm run build" first');
    process.exit(1);
  }

  // Copy file
  const content = fs.readFileSync(distFile, 'utf-8');
  fs.writeFileSync(targetFile, content, 'utf-8');

  const stats = fs.statSync(targetFile);
  console.log(`[copy-dist] Successfully copied ${stats.size} bytes`);
  console.log('[copy-dist] Done!');
} catch (err) {
  console.error('[copy-dist] ERROR:', err.message);
  process.exit(1);
}
