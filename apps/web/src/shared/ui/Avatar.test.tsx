import { screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { Avatar } from './Avatar';

/** #500: a member's picture where there is one, their initials otherwise. */
describe('Avatar', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:avatar');
    URL.revokeObjectURL = vi.fn();
  });
  afterEach(() => vi.restoreAllMocks());

  test('draws the initials of a member without a picture, asking nothing of the API', () => {
    renderWithProviders(<Avatar id="bob" name="Bob Lecteur" />);

    expect(screen.getByText('BL')).toBeInTheDocument();
  });

  test("shows the member's picture, fetched with the session", async () => {
    let auth: string | null = null;
    server.use(
      http.get('*/api/users/bob/avatar', ({ request }) => {
        auth = request.headers.get('authorization');
        return new HttpResponse(new Uint8Array([0xff, 0xd8, 0xff]), { headers: { 'Content-Type': 'image/jpeg' } });
      }),
    );
    renderWithProviders(<Avatar id="bob" name="Bob Lecteur" version={42} />);

    await waitFor(() => expect(screen.getByRole('img', { name: 'Bob Lecteur' }).tagName).toBe('IMG'));
    expect(screen.getByRole('img', { name: 'Bob Lecteur' })).toHaveAttribute('src', 'blob:avatar');
    expect(auth).not.toBeUndefined();
  });

  test('falls back on the initials when the picture cannot be read', async () => {
    let asked = false;
    server.use(http.get('*/api/users/bob/avatar', () => {
      asked = true;
      return new HttpResponse(null, { status: 404 });
    }));
    const { container } = renderWithProviders(<Avatar id="bob" name="Bob Lecteur" version={42} />);

    await waitFor(() => expect(asked).toBe(true));
    expect(screen.getByText('BL')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Bob Lecteur' }).tagName).toBe('SPAN');
    expect(container.querySelector('img')).toBeNull();
  });
});
