import path from 'node:path';
import { expect, test } from '../support/fixtures';
import { figure } from '../support/ui';

const CSV_FILE = path.join(__dirname, '..', 'fixtures', 'library.csv');

/**
 * P4 — arrive with an existing library.
 *
 * A CSV upload is the one journey that writes several titles in a single request, with
 * the statuses read from the file. Checking the counters afterwards proves the statuses
 * were understood, not only the titles.
 */
test('imports a library from a CSV file', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Réglages' }).click();
  await expect(page.getByRole('heading', { name: 'Réglages' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Importer un fichier CSV' })).toBeVisible();

  // The button only forwards the click to a hidden input, which is what is fed here.
  await page.locator('input[type="file"]').setInputFiles(CSV_FILE);
  await expect(page.getByText('3 titre(s) importé(s) · 0 déjà présent(s).')).toBeVisible();

  await page.getByRole('button', { name: 'Retour' }).click();
  await expect(figure(page, 'lus')).toHaveText('1');
  await expect(figure(page, 'en cours')).toHaveText('1');
  await expect(figure(page, 'à lire')).toHaveText('1');

  await page.getByRole('link', { name: 'Collection' }).click();
  await expect(page.getByText('3 titres')).toBeVisible();
  await expect(page.getByText('La Horde du Contrevent', { exact: true })).toBeVisible();
  await expect(page.getByText('Le Nom du vent', { exact: true })).toBeVisible();
  await expect(page.getByText('Les Furtifs', { exact: true })).toBeVisible();

  // Importing the same file again must not create duplicates.
  await page.getByRole('link', { name: 'Accueil' }).click();
  await page.getByRole('button', { name: 'Réglages' }).click();
  await page.locator('input[type="file"]').setInputFiles(CSV_FILE);
  await expect(page.getByText('0 titre(s) importé(s) · 3 déjà présent(s).')).toBeVisible();
});
