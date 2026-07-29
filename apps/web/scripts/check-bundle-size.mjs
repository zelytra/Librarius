#!/usr/bin/env node
/**
 * Bundle budget for the PWA — run after `pnpm web:build`, blocking in CI.
 *
 *   node apps/web/scripts/check-bundle-size.mjs [--dist <path>]
 *
 * The app is read on a phone, often on a bookshop's mobile network, so what matters is
 * the number of bytes on the wire, not the number on disk: every text asset is measured
 * gzipped, at the compression level nginx serves it with (see apps/web/nginx.conf).
 *
 * Three things are measured, because they fail in three different ways:
 *
 *   - the *initial payload*, everything index.html pulls before the first screen can
 *     paint. That is the figure the user waits on;
 *   - each *deferred asset* taken on its own, so one screen quietly importing a chart
 *     or date library shows up as that screen rather than diluted in a total;
 *   - the *whole build*, since the service worker precaches all of it right after the
 *     first load — bytes parked in a lazy chunk are still bytes the phone downloads.
 *
 * Budgets are deliberately close to the current measurement: a budget with 60% of slack
 * catches nothing. Raising one is allowed, silently is not — the number lives here, in
 * the diff, and the pull request has to say why it moved.
 */

import { gzipSync } from 'node:zlib';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Budgets in kB (1000 bytes, the unit Vite reports), measured on 2026-07-29 against
 * 9d1b440, once the routes were code split.
 *
 * - `initial` — measured 137.3 kB gz, of which react-dom alone is 56.5 kB,
 *   oidc-client-ts 17.4 kB, i18next 14.2 kB, react-router 13.5 kB and query-core
 *   11.6 kB. Set at 155 kB: ~13% of room, enough for a shared dependency or for the
 *   shell to grow, not enough to absorb a UI kit unnoticed. It also stays clear of the
 *   200 kB gz ceiling issue #79 sets for the product, which leaves somewhere to raise
 *   it to on purpose.
 * - `chunk` — measured 3.6 kB gz for the heaviest screen (Discover, after #146) and
 *   5.2 kB for the Workbox runtime. Set at 10 kB: a screen can double or triple as it
 *   gains features, but a charting library landing in Stats (~50 kB gz) fails, and it
 *   fails naming Stats.
 * - `total` — measured 175.3 kB. Set at 200 kB, the same ~15% of room, as the backstop
 *   for what the other two cannot see: an uncompressed image dropped into public/, a
 *   locale file, a second font.
 */
const BUDGET_KB = {
  initial: 155,
  chunk: 10,
  total: 200,
};

/** Compressed on the wire by nginx; anything else (images, fonts) is already binary. */
const COMPRESSED = new Set(['.js', '.mjs', '.css', '.html', '.json', '.svg', '.webmanifest']);

const here = dirname(fileURLToPath(import.meta.url));
const distArg = process.argv.indexOf('--dist');
const dist = resolve(distArg === -1 ? join(here, '..', 'dist') : process.argv[distArg + 1]);

function listFiles(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = join(dir, entry.name);
    return entry.isDirectory() ? listFiles(full) : [full];
  });
}

/** Bytes actually transferred: gzipped for text, as-is for the rest. */
function transferSize(file) {
  const body = readFileSync(file);
  const ext = file.slice(file.lastIndexOf('.'));
  return COMPRESSED.has(ext) ? gzipSync(body).length : body.length;
}

/**
 * The initial payload, read from the built index.html rather than guessed: whatever
 * Vite decided to inline, preload or split, the browser downloads exactly the scripts,
 * stylesheets and module preloads listed there.
 */
function initialAssets(html) {
  const assets = new Set();
  for (const [tag] of html.matchAll(/<(?:script|link)\b[^>]*>/g)) {
    const isScript = tag.startsWith('<script');
    const isRenderPath = /rel="(?:stylesheet|modulepreload)"/.test(tag);
    if (!isScript && !isRenderPath) continue;
    const href = tag.match(/(?:src|href)="([^"]+)"/)?.[1];
    // Third parties (the Google Fonts stylesheet) are not ours to budget.
    if (href && !/^(?:https?:)?\/\//.test(href)) assets.add(href.replace(/^\//, ''));
  }
  return [...assets];
}

const kB = (bytes) => bytes / 1000;
const fmt = (bytes) => `${kB(bytes).toFixed(1)} kB`;
const pad = (name) => name.padEnd(42);

const files = listFiles(dist).map((file) => ({
  name: file.slice(dist.length + 1).replaceAll('\\', '/'),
  bytes: transferSize(file),
}));
const byName = new Map(files.map((f) => [f.name, f]));

const initialNames = initialAssets(readFileSync(join(dist, 'index.html'), 'utf8'));
// A budget that measures nothing passes for ever. Both cases below mean the parsing no
// longer matches what Vite emits, and have to be read as a broken check, not as a pass.
if (initialNames.length === 0) {
  console.error(`No script or stylesheet found in ${join(dist, 'index.html')} — check broken.`);
  process.exit(2);
}
const missing = initialNames.filter((name) => !byName.has(name));
if (missing.length > 0) {
  console.error(`index.html references files that are not in ${dist}: ${missing.join(', ')}`);
  process.exit(2);
}

const initial = initialNames.map((name) => byName.get(name));
const initialBytes = initial.reduce((sum, f) => sum + f.bytes, 0);
const totalBytes = files.reduce((sum, f) => sum + f.bytes, 0);
// Everything the browser fetches later: the lazy routes and the service worker runtime.
const deferred = files
  .filter((f) => /\.(?:js|css)$/.test(f.name) && !initialNames.includes(f.name))
  .sort((a, b) => b.bytes - a.bytes);

const failures = [];
function check(label, bytes, budgetKB, detail) {
  const over = bytes - budgetKB * 1000;
  if (over > 0) failures.push({ label, bytes, budgetKB, over, detail });
  return `${fmt(bytes)} / ${budgetKB.toFixed(1)} kB  (${Math.round((kB(bytes) / budgetKB) * 100)}% of budget)`;
}

console.log(`\nBundle budget — ${dist}\n`);

console.log('Initial payload — what index.html loads before the first screen paints');
for (const f of initial) console.log(`  ${pad(f.name)}${fmt(f.bytes).padStart(10)}`);
console.log(`  ${pad('')}${'—'.repeat(10)}`);
console.log(`  ${pad('initial')}${check('Initial payload', initialBytes, BUDGET_KB.initial, initial)}\n`);

console.log('Deferred assets — the lazy routes and the service worker runtime');
for (const f of deferred) {
  console.log(`  ${pad(f.name)}${fmt(f.bytes).padStart(10)}`);
  check(`Deferred asset ${f.name}`, f.bytes, BUDGET_KB.chunk, [f]);
}
console.log('');

console.log('Whole build — what the service worker precaches after the first load');
console.log(`  ${pad(`${files.length} files`)}${check('Whole build', totalBytes, BUDGET_KB.total, files)}\n`);

console.log('Sizes are gzipped for text assets, raw for the rest.\n');

if (failures.length === 0) {
  console.log('OK — every budget is respected.\n');
  process.exit(0);
}

const lines = [`${failures.length} bundle budget(s) exceeded:`, ''];
for (const f of failures) {
  const percent = ((f.over / (f.budgetKB * 1000)) * 100).toFixed(1);
  lines.push(`  ${f.label}: ${fmt(f.bytes)}, budget ${f.budgetKB.toFixed(1)} kB`);
  lines.push(`    over by ${fmt(f.over)} (+${percent}%)`);
  const heaviest = [...f.detail].sort((a, b) => b.bytes - a.bytes).slice(0, 5);
  if (heaviest.length > 1) {
    lines.push('    heaviest files:');
    for (const file of heaviest) lines.push(`      ${pad(file.name)}${fmt(file.bytes).padStart(10)}`);
  }
  lines.push('');
}
lines.push('Either bring the payload back down — a dependency that can be lazy loaded, an');
lines.push('import pulling a whole library — or raise the budget in');
lines.push('apps/web/scripts/check-bundle-size.mjs and say why in the pull request.');

const report = lines.join('\n');
console.error(report);
// Surfaces the reason at the top of the run rather than only in the step log. The
// percent signs have to be escaped before the newlines, or their own escape is eaten.
if (process.env.GITHUB_ACTIONS === 'true') {
  const escaped = report.replaceAll('%', '%25').replaceAll('\r', '%0D').replaceAll('\n', '%0A');
  console.log(`::error title=Bundle budget exceeded::${escaped}`);
}
process.exit(1);
