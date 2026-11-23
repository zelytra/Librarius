import { screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { SaleLinks } from './SaleLinks';

/** Where an edition can be bought (#619). */
describe('SaleLinks', () => {
  test('offers one link per store, opening the store in a new tab', async () => {
    server.use(http.get('*/api/editions/:id/sale-links', () => HttpResponse.json([
      { store: 'FNAC', url: 'https://www.fnac.com/a1' },
      { store: 'AMAZON', url: 'https://www.amazon.fr/dp/1' },
    ])));
    renderWithProviders(<SaleLinks editionId="ed-1" />);

    const fnac = await screen.findByRole('link', { name: /Acheter sur Fnac/ });
    expect(fnac).toHaveAttribute('href', 'https://www.fnac.com/a1');
    expect(fnac).toHaveAttribute('target', '_blank');
    expect(fnac).toHaveAttribute('rel', expect.stringContaining('noopener'));
    expect(screen.getByRole('link', { name: /Acheter sur Amazon/ })).toBeInTheDocument();
  });

  test('shows nothing when no store is known', async () => {
    server.use(http.get('*/api/editions/:id/sale-links', () => HttpResponse.json([])));
    const { container } = renderWithProviders(<SaleLinks editionId="ed-1" />);
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(container).toBeEmptyDOMElement();
  });
});
