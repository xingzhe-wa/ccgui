#!/usr/bin/env node

/**
 * Fix HTML for JCEF compatibility
 * - Remove type="module" from script tags (JCEF doesn't support ES modules)
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const cwd = process.cwd();
const distFile = path.resolve(cwd, 'dist/index.html');

console.log('[fix-html] Fixing HTML for JCEF compatibility...');

try {
    let html = fs.readFileSync(distFile, 'utf-8');

    // 移除 type="module" 属性
    html = html.replace(/<script type="module"/g, '<script');

    // 移除 crossorigin 属性（JCEF 不需要）
    html = html.replace(/ crossorigin>/g, '>');

    const stats = fs.statSync(distFile);
    fs.writeFileSync(distFile, html, 'utf-8');

    console.log(`[fix-html] Fixed HTML (${stats.size} bytes)`);
    console.log('[fix-html] Done!');
} catch (err) {
    console.error('[fix-html] ERROR:', err.message);
    process.exit(1);
}
