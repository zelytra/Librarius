import { InAppBrowser } from '@capgo/inappbrowser';
import { inAppCollectorScript, isCollectedFile, type CollectedFile } from './collector';

type Source = CollectedFile['source'];

/** Where the in-app window opens: a page from which the site's sign-in is one tap away. */
export const IN_APP_START: Record<Source, string> = {
  booknode: 'https://booknode.com/',
  babelio: 'https://www.babelio.com/connection.php',
  mangacollec: 'https://www.mangacollec.com/',
};

const TITLES: Record<Source, string> = { booknode: 'Booknode', babelio: 'Babelio', mangacollec: 'Mangacollec' };

/**
 * The in-app import (#570): opens the source site in a window the app owns, where the reader
 * signs in as they would anywhere, and injects the collector into every page they load. It
 * resolves with the file the collector posts back — and closes the window — or with `null`
 * when the reader closes the window first. Native only: a browser cannot script another origin,
 * and keeps the bookmarklet.
 */
export async function collectInApp(source: Source): Promise<CollectedFile | null> {
  return new Promise((resolve) => {
    const handles: Promise<{ remove: () => Promise<unknown> }>[] = [];
    let settled = false;
    const finish = (file: CollectedFile | null) => {
      if (settled) return;
      settled = true;
      for (const handle of handles) void handle.then((h) => h.remove());
      if (file) void InAppBrowser.close().catch(() => {});
      resolve(file);
    };

    handles.push(
      InAppBrowser.addListener('browserPageLoaded', () => {
        void InAppBrowser.executeScript({ code: inAppCollectorScript() });
      }),
      InAppBrowser.addListener('messageFromWebview', (event) => {
        if (isCollectedFile(event.detail)) finish(event.detail);
      }),
      InAppBrowser.addListener('closeEvent', () => finish(null)),
    );

    InAppBrowser.openWebView({ url: IN_APP_START[source], title: TITLES[source] }).catch(() => finish(null));
  });
}
