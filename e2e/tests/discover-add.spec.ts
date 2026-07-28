import { expect, test } from '../support/fixtures';
import { addResult, FIRST_BOOK, searchCatalog } from '../support/ui';

/**
 * P1 — add a book spotted in a bookshop.
 *
 * Search the catalog, add the result to the collection, find it back in Collection.
 * The chain exercised here is the whole one: bearer token, nginx routing, the API, the
 * external catalog and the database.
 */
test('adds a book found on Discover to the collection', async ({ page }) => {
  await page.goto('/discover');
  await expect(page.getByRole('heading', { name: 'Découvrir' })).toBeVisible();

  await searchCatalog(page, 'dune');
  await expect(page.getByText(FIRST_BOOK, { exact: true }).first()).toBeVisible();

  await addResult(page, 'Collection');
  await expect(page.getByText('✓ Ajouté à la collection')).toBeVisible();

  await page.getByRole('link', { name: 'Collection' }).click();
  await expect(page.getByRole('heading', { name: 'Ma collection' })).toBeVisible();
  await expect(page.getByText(FIRST_BOOK, { exact: true })).toBeVisible();
  await expect(page.getByText('1 titres')).toBeVisible();

  // Reloading proves it was persisted, not just held in the query cache.
  await page.reload();
  await expect(page.getByText(FIRST_BOOK, { exact: true })).toBeVisible();
});
