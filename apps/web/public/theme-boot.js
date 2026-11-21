// Applies the stored theme before the first paint (loaded synchronously from index.html).
// A file rather than an inline script since #580: the Content-Security-Policy allows the
// app's own scripts only. A React effect runs after
// it, so the light palette would flash on every load — and the stylesheet is
// not parsed yet either, hence the inline background on the root element.
// Mirrors src/shared/theme/themes.ts; the background colours are the --bg of
// each palette in src/shared/styles/tokens.css.
(function () {
  var BACKGROUNDS = {
    creme: '#f3ede3',
    sauge: '#dfe6da',
    rose: '#f1e2df',
    ocean: '#dde8ee',
    lavande: '#e7e2ee',
    sable: '#efe6d6',
    nuit: '#211e1a',
    ardoise: '#1b2129',
  };
  var stored = null;
  try {
    stored = localStorage.getItem('librarius.theme');
  } catch (e) {
    /* storage unavailable: fall back to the system preference */
  }
  var prefersDark =
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches;
  // Anything else — "systeme", nothing stored, a stale value — follows the system.
  var palette = BACKGROUNDS[stored] ? stored : prefersDark ? 'nuit' : 'creme';

  var root = document.documentElement;
  root.setAttribute('data-theme', palette);
  root.style.backgroundColor = BACKGROUNDS[palette];
  document
    .querySelector('meta[name="theme-color"]')
    .setAttribute('content', BACKGROUNDS[palette]);
})();
