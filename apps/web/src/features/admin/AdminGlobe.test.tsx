import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { AdminGlobe } = await import('./AdminGlobe');

const serve = (body: object, status = 200) =>
  server.use(http.get('*/api/admin/countries', () => HttpResponse.json(body, { status })));

describe('AdminGlobe (#573)', () => {
  test('lists the members per country, the largest first, beside a point per country on the globe', async () => {
    serve([
      { country: 'FR', members: 12 },
      { country: 'JP', members: 3 },
      { country: 'BV', members: 1 },
    ]);
    renderWithProviders(<AdminGlobe />);

    const list = await screen.findByRole('list', { name: 'Membres par pays' });
    const rows = within(list).getAllByRole('listitem').map((row) => row.textContent);
    expect(rows).toEqual(['France12', 'Japon3', 'Île Bouvet1']);
    expect(screen.getByText('16 membres dans 3 pays')).toBeInTheDocument();
    // The globe faces the largest country, so its point is on the visible side.
    expect(document.querySelector('[data-country="FR"]')).not.toBeNull();
  });

  test('turns with the arrow keys', async () => {
    serve([{ country: 'FR', members: 2 }]);
    renderWithProviders(<AdminGlobe />);

    const globe = await screen.findByRole('group', { name: /Globe/ });
    const before = globe.getAttribute('data-facing');
    fireEvent.keyDown(globe, { key: 'ArrowRight' });
    expect(globe.getAttribute('data-facing')).not.toBe(before);
  });

  test('says when no member has given a country', async () => {
    serve([]);
    renderWithProviders(<AdminGlobe />);
    expect(await screen.findByText("Aucun membre n'a encore indiqué son pays.")).toBeInTheDocument();
  });

  test('says when the counts cannot be loaded', async () => {
    serve({}, 500);
    renderWithProviders(<AdminGlobe />);
    expect(await screen.findByText('Impossible de charger les pays des membres.')).toBeInTheDocument();
  });
});
