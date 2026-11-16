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
  plugins: {
    // An app that targets API 35 or later draws edge to edge, and Capacitor 8 leaves the
    // insets to CSS (`insetsHandling: "css"`, its default): `index.html` asks for
    // `viewport-fit=cover` and the shell reads `env(safe-area-inset-*)` (#529). The hint
    // below says so up front, so the first frame is not laid out as `contain` and then
    // shifted once the meta tag is read.
    SystemBars: {
      initialViewportFitValueHint: 'cover',
    },
  },
};

export default config;
