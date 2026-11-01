#!/usr/bin/env node
// Runs every tests/**/*.test.ts (except the UI scenarios) with tsx --test, so a test placed in a new
// folder is never silently skipped. Extra arguments are passed through, e.g. a single file.
'use strict';
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const root = path.resolve(__dirname, '..');
const found = [];
(function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) { if (full !== path.join(root, 'tests', 'ui')) walk(full); }
    else if (entry.name.endsWith('.test.ts')) found.push(path.relative(root, full));
  }
})(path.join(root, 'tests'));
const args = process.argv.slice(2);
const files = args.length ? args : found.sort();
const tsx = path.join(root, 'node_modules', '.bin', process.platform === 'win32' ? 'tsx.cmd' : 'tsx');
const result = spawnSync(tsx, ['--test', ...files], { cwd: root, stdio: 'inherit', shell: process.platform === 'win32' });
process.exit(result.status === null ? 1 : result.status);
