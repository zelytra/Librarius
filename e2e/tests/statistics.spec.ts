import { expect, test } from '../support/fixtures';
import { addResult, figure, FIRST_BOOK, markAsRead, openTitle, searchCatalog } from '../support/ui';

/**
 * P5 — the figures follow what the user does.
 *
 * Home counters and the Statistics screen both read `/api/stats`, an aggregate computed
 * from the database. Adding two titles then marking one as read must move them, and
 * move them consistently.
 */
test('keeps the statistics consistent after an addition', async ({ page }) => {
  await page.goto('/stats');
  await expect(page.getByRole('heading', { name: 'Statistiques' })).toBeVisible();
  await expect(figure(page, 'Livres lus')).toHaveText('0');
  await expect(figure(page, 'En cours')).toHaveText('0');

  await page.getByRole('link', { name: 'Découvrir' }).click();
  await searchCatalog(page, 'science-fiction');
  await addResult(page, 'Collection');
  // Waiting for the first confirmation before the second click: an added result swaps
  // its buttons for that confirmation, and "the first remaining button" is only
  // unambiguous once the swap happened.
  await expect(page.getByText('✓ Ajouté à la collection')).toHaveCount(1);
  await addResult(page, 'Collection');
  await expect(page.getByText('✓ Ajouté à la collection')).toHaveCount(2);

  await page.getByRole('link', { name: 'Accueil' }).click();
  await expect(figure(page, 'à lire')).toHaveText('2');
  await expect(figure(page, 'lus')).toHaveText('0');

  await page.getByRole('link', { name: 'Collection' }).click();
  await openTitle(page, FIRST_BOOK);
  // Skipping the sheet that follows: the figures below are about the status alone, and
  // a title read without a rating or a shelf still counts as read.
  await markAsRead(page);
  await expect(page.getByRole('button', { name: '✓ Lu' })).toBeVisible();
  await page.getByRole('button', { name: 'Retour' }).click();

  await page.getByRole('link', { name: 'Stats' }).click();
  await expect(figure(page, 'Livres lus')).toHaveText('1');
  await expect(figure(page, 'En cours')).toHaveText('0');
  // No page count on a stub catalog entry: the total read pages must stay at zero
  // rather than count the book as an unknown number of pages.
  await expect(figure(page, 'Pages lues')).toHaveText('0');

  await page.getByRole('link', { name: 'Accueil' }).click();
  await expect(figure(page, 'lus')).toHaveText('1');
  await expect(figure(page, 'à lire')).toHaveText('1');
});
