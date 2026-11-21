import { describe, expect, test } from 'vitest';
// The files nginx serves the app with, as written: the shared header set, the image's own
// server block, and the one the chart mounts over it on the cluster (#580).
import headers from '../../security-headers.conf?raw';
import imageConf from '../../nginx.conf?raw';
import chartWeb from '../../../../infra/helm/librarius/templates/web.yaml?raw';
import indexHtml from '../../index.html?raw';
import dockerfile from '../../Dockerfile?raw';

const INCLUDE = 'include /etc/nginx/snippets/security-headers.conf;';
const header = (name: string) => new RegExp(`add_header ${name} "([^"]+)" always;`).exec(headers)?.[1] ?? '';

/** #580: staging answered with no security header at all. */
describe('security headers', () => {
  test('lock scripts to the app itself, framing out, and the other usual headers', () => {
    const csp = header('Content-Security-Policy');
    expect(csp).toMatch(/default-src 'self'/);
    expect(csp).toMatch(/script-src 'self' 'wasm-unsafe-eval';/);
    expect(csp).not.toMatch(/script-src[^;]*'unsafe-inline'/);
    expect(csp).toMatch(/frame-ancestors 'none'/);
    expect(csp).toMatch(/object-src 'none'/);
    expect(csp).toMatch(/base-uri 'self'/);
    expect(header('Strict-Transport-Security')).toMatch(/max-age=\d{8}/);
    expect(header('X-Content-Type-Options')).toBe('nosniff');
    expect(header('X-Frame-Options')).toBe('DENY');
    expect(header('Referrer-Policy')).toBe('strict-origin-when-cross-origin');
    // The scanner needs the camera; nothing else does.
    expect(header('Permissions-Policy')).toMatch(/camera=\(self\)/);
    expect(header('Permissions-Policy')).toMatch(/geolocation=\(\)/);
  });

  // nginx drops the server's add_header in any location that declares one of its own: every
  // such location includes the set again, in both server blocks.
  test.each([
    ['the image', imageConf],
    ['the chart', chartWeb],
  ])('reach every response of %s', (_, conf) => {
    expect(conf).toMatch(/server_tokens off;/);
    const server = conf.slice(conf.indexOf('server {'));
    expect(server.split('add_header').length - 1).toBeGreaterThan(0);
    const blocks = server.split(/\n\s*location /).slice(1);
    for (const block of blocks) {
      const body = block.slice(0, block.indexOf('}'));
      if (/add_header/.test(body)) expect(body).toContain(INCLUDE);
    }
    expect(server.slice(0, server.indexOf('location'))).toContain(INCLUDE);
  });

  test('ship the set inside the image the chart runs', () => {
    expect(dockerfile).toMatch(/COPY apps\/web\/security-headers\.conf \/etc\/nginx\/snippets\/security-headers\.conf/);
  });

  // No inline script, or `script-src 'self'` would stop it: the theme boot is a file.
  test('leaves no inline script in the page', () => {
    expect(indexHtml).not.toMatch(/<script>(?!\s*<\/script>)/);
    expect(indexHtml).toMatch(/<script src="\/theme-boot\.js"><\/script>/);
  });
});
