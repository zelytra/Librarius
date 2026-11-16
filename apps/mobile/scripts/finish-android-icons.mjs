/**
 * Second half of `assets:generate`.
 *
 * `@capacitor/assets` writes the adaptive icon as two inset layers:
 *
 *     <background><inset android:drawable="@mipmap/ic_launcher_background" android:inset="16.7%" /></background>
 *     <foreground><inset android:drawable="@mipmap/ic_launcher_foreground" android:inset="16.7%" /></foreground>
 *
 * The inset is right for the foreground — it is what keeps the mark inside the safe zone no
 * launcher mask can clip — and wrong for the background, which has to cover the whole 108dp
 * canvas: inset, it leaves the four corners transparent, and a round mask then cuts a hole
 * where the icon's colour should be.
 *
 * This script rewrites the two generated files so the background is painted edge to edge, and
 * removes the three placeholder drawables of Capacitor's own template that nothing references
 * any more. It runs straight after the generator, so one command still produces a finished set
 * of resources (#526).
 */
import { rmSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const res = fileURLToPath(new URL('../android/app/src/main/res/', import.meta.url));

const adaptiveIcon = `<?xml version="1.0" encoding="utf-8"?>
<!-- Written by scripts/finish-android-icons.mjs; read it before editing this by hand. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground>
        <inset android:drawable="@mipmap/ic_launcher_foreground" android:inset="16.7%" />
    </foreground>
</adaptive-icon>
`;

for (const file of ['ic_launcher.xml', 'ic_launcher_round.xml']) {
  writeFileSync(join(res, 'mipmap-anydpi-v26', file), adaptiveIcon);
}

// Capacitor's template ships a vector background, a vector foreground and a colour for them;
// the generated bitmaps replaced all three.
const leftovers = [
  'drawable/ic_launcher_background.xml',
  'drawable-v24/ic_launcher_foreground.xml',
  'values/ic_launcher_background.xml',
];
let removed = 0;
for (const leftover of leftovers) {
  const path = join(res, leftover);
  if (existsSync(path)) {
    rmSync(path);
    removed += 1;
  }
}

console.log(`adaptive icon: background painted edge to edge, ${removed} template leftovers removed`);
