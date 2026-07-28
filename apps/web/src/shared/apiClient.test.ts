import { describe, expect, test } from 'vitest';
import { http, HttpResponse, server } from '../test/server';
import { postApiImportCsv, postApiLibrary } from '../api/generated/librarius';
import { apiClient, ApiError } from './apiClient';

// The call goes through the generated function rather than straight to the mutator:
// since orval 8 the request body and the query string are built by the generated code,
// so a test that called `apiClient` directly would no longer cover how a payload is
// encoded — the very thing these two cases exist to lock down.
describe('apiClient', () => {
  test('encodes a JSON payload', async () => {
    let body: string | undefined;
    server.use(
      http.post('*/api/library', async ({ request }) => {
        body = await request.text();
        return HttpResponse.json({ id: 'item-1' }, { status: 201 });
      }),
    );

    await postApiLibrary({ book: { kind: 'BOOK', title: 'Dune' }, status: 'OWNED' });

    expect(body).toBe('{"book":{"kind":"BOOK","title":"Dune"},"status":"OWNED"}');
  });

  // A CSV is not JSON: encoding it as such turned the whole file into a single quoted
  // line with escaped newlines, and the import created one title named after the
  // entire file.
  test('sends a text/plain payload as it is', async () => {
    const csv = 'titre;auteur;statut\nDune;Frank Herbert;lu\n';
    let body: string | undefined;
    let contentType: string | null = null;
    server.use(
      http.post('*/api/import/csv', async ({ request }) => {
        body = await request.text();
        contentType = request.headers.get('content-type');
        return HttpResponse.json({ source: 'csv', imported: 1, skipped: 0, total: 1 });
      }),
    );

    await postApiImportCsv(csv);

    expect(body).toBe(csv);
    expect(contentType).toContain('text/plain');
  });

  // `RequestInit.headers` is legitimately a `Headers` instance or an array of pairs, and
  // an object spread drops both silently — which would have sent the Content-Type of the
  // CSV import into the void.
  test('keeps the headers the caller passed, whatever shape they came in', async () => {
    let contentType: string | null = null;
    server.use(
      http.post('*/api/import/csv', ({ request }) => {
        contentType = request.headers.get('content-type');
        return HttpResponse.json({ source: 'csv', imported: 0, skipped: 0, total: 0 });
      }),
    );

    await apiClient('/api/import/csv', {
      method: 'POST',
      headers: new Headers({ 'Content-Type': 'text/plain' }),
      body: 'a;b;c\n',
    });

    expect(contentType).toContain('text/plain');
  });

  test('raises the status of a failed call', async () => {
    server.use(http.get('*/api/stats', () => new HttpResponse(null, { status: 500 })));

    await expect(apiClient('/api/stats', { method: 'GET' })).rejects.toBeInstanceOf(ApiError);
  });
});
