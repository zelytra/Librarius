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

  // Finishing opens the rating-and-shelving sheet over the screen. Answering it here
  // rather than skipping it is what gives its two writes one pass against the real API;
  // the statistics journey takes the other path and dismisses it.
  const sheet = page.getByRole('dialog');
  await expect(sheet.getByRole('heading', { name: 'Terminé !' })).toBeVisible();
  await sheet.getByRole('button', { name: 'Noter 4 sur 5' }).click();
  await sheet.getByRole('button', { name: 'Or', exact: true }).click();
  await sheet.getByRole('button', { name: 'Enregistrer' }).click();
  await sheet.waitFor({ state: 'hidden' });

  // Both round-tripped: the screen re-reads the title, so what it shows is what was stored.
  await expect(page.getByRole('button', { name: 'Retirer ma note' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Or', exact: true }))
    .toHaveAttribute('aria-pressed', 'true');

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
