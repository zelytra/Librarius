import type { CapacitorConfig } from '@capacitor/cli';

/**
 * Native shell configuration.
 *
 * This workspace holds no application code: the native container loads the very bundle
 * `apps/web` produces, so a screen written once runs in the browser and on the device.
 * `webDir` therefore points outside the package, at the web build output, and
 * `@librarius/web` is declared as a workspace dependency so that
 * `pnpm --filter "@librarius/mobile..." build` refreshes `dist/` before `cap sync`
 * copies it into the native projects.
 */
const config: CapacitorConfig = {
  appId: 'fr.zelytra.librarius',
  // Shown under the icon on the device, hence French like the rest of the interface.
  appName: 'Ma Bibliothèque',
  webDir: '../web/dist',
};

export default config;
