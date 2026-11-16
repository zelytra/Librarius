/**
 * The two images the Play Console asks for before a listing can be published (#530):
 *
 * - a 512 × 512 application icon, PNG, no transparency;
 * - a 1024 × 500 feature graphic, PNG or JPEG.
 *
 * Both are rendered from the SVG sources beside them, so changing the artwork is editing a
 * file rather than reopening an image editor. The launcher icon is *not* one of these: it is
 * generated from `assets/` into the native project by `assets:generate`.
 */
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import sharp from 'sharp';

const store = fileURLToPath(new URL('../store/', import.meta.url));
mkdirSync(store, { recursive: true });

const images = [
  { source: 'icon-512.svg', output: 'icon-512.png', width: 512, height: 512 },
  { source: 'feature-graphic.svg', output: 'feature-graphic.png', width: 1024, height: 500 },
];

for (const { source, output, width, height } of images) {
  await sharp(join(store, source), { density: 384 })
    .resize(width, height, { fit: 'fill' })
    // Play refuses an icon with an alpha channel; the sources paint their own background, so
    // flattening only drops the channel.
    .flatten({ background: '#f3ede3' })
    .png()
    .toFile(join(store, output));
  console.log(`${output}: ${width} × ${height}`);
}
