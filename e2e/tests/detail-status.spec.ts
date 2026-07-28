import { expect, test } from '../support/fixtures';
import { addResult, figure, FIRST_BOOK, openTitle, searchCatalog } from '../support/ui';

/**
 * P2 — resume a reading.
 *
 * Move a title from "owned" to "reading" then to "read" from its detail screen, and
 * check the change reached the database: the Home counters and carousels are computed
 * server-side by `/api/stats`.
 */
test('changes the status of a title from its detail screen', async ({ page }) => {
  await page.goto('/discover');
  await searchCatalog(page, 'dune');
  await addResult(page, 'Collection');
  await expect(page.getByText('✓ Ajouté à la collection')).toBeVisible();

  await page.getByRole('link', { name: 'Collection' }).click();
  await openTitle(page, FIRST_BOOK);

  // OWNED → READING.
  await page.getByRole('button', { name: 'Commencer la lecture' }).click();
  await expect(page.getByRole('button', { name: 'Lecture en cours' })).toBeVisible();

  // The bottom bar is hidden on the detail screen: back out through its own control.
  await page.getByRole('button', { name: 'Retour' }).click();
  await page.getByRole('link', { name: 'Accueil' }).click();
  await expect(page.getByRole('heading', { name: 'Reprendre la lecture' })).toBeVisible();
  await expect(figure(page, 'en cours')).toHaveText('1');
  await expect(figure(page, 'lus')).toHaveText('0');

  // READING → READ, opening the title from the carousel this time.
  await openTitle(page, FIRST_BOOK);
  await page.getByRole('button', { name: 'Marquer comme lu' }).click();
  await expect(page.getByRole('button', { name: '✓ Lu' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Commencer la lecture' })).toBeHidden();

  await page.getByRole('button', { name: 'Retour' }).click();
  await expect(figure(page, 'lus')).toHaveText('1');
  await expect(figure(page, 'en cours')).toHaveText('0');

  // The status survives a full reload: it lives in the database, not in the client.
  await page.reload();
  await expect(figure(page, 'lus')).toHaveText('1');
  await expect(page.getByRole('heading', { name: 'Derniers lus' })).toBeVisible();
});
