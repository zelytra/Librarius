import { expect, test } from '../support/fixtures';
import { addResult, FIRST_BOOK, searchCatalog } from '../support/ui';

/**
 * P3 — feed the wishlist, then empty it.
 *
 * The removal matters as much as the addition: it is the only screen where a delete is
 * driven from a list that has to refresh itself afterwards.
 */
test('adds a title to the wishlist then removes it', async ({ page }) => {
  await page.goto('/discover');
  await searchCatalog(page, 'dune');

  await addResult(page, 'Souhaits');
  await expect(page.getByText('✓ Ajouté aux souhaits')).toBeVisible();

  await page.getByRole('link', { name: 'Souhaits' }).click();
  await expect(page.getByRole('heading', { name: 'Liste de souhaits' })).toBeVisible();
  await expect(page.getByText(FIRST_BOOK, { exact: true })).toBeVisible();
  await expect(page.getByText('1 titres')).toBeVisible();
  await expect(page.getByText('Bientôt')).toBeVisible();

  await page.getByRole('button', { name: 'Retirer' }).click();
  await expect(page.getByText(/Ta liste de souhaits est vide/)).toBeVisible();

  // Gone from the server too, not only from the list on screen.
  await page.reload();
  await expect(page.getByText(/Ta liste de souhaits est vide/)).toBeVisible();
});
