import { describe, expect, test } from 'vitest';
import { http, HttpResponse, server } from '../test/server';
import { apiClient } from './apiClient';

describe('apiClient', () => {
  test('encodes a JSON payload', async () => {
    let body: string | undefined;
    server.use(
      http.post('*/api/library', async ({ request }) => {
        body = await request.text();
        return HttpResponse.json({ id: 'item-1' }, { status: 201 });
      }),
    );

    await apiClient({
      url: '/api/library',
      method: 'POST',
      data: { book: { title: 'Dune' }, status: 'OWNED' },
    });

    expect(body).toBe('{"book":{"title":"Dune"},"status":"OWNED"}');
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

    await apiClient({
      url: '/api/import/csv',
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      data: csv,
    });

    expect(body).toBe(csv);
    expect(contentType).toContain('text/plain');
  });
});
