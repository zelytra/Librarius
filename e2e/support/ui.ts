import type { Locator, Page } from '@playwright/test';

/**
 * Helpers shared by the journeys. They stay close to what the user sees — visible
 * labels, French copy from `i18n/locales/fr.json` — rather than to the markup, so a
 * restyling does not rewrite the suite.
 */

/** Title of the first book served by the catalog stub (`stack/catalog-stub.conf`). */
export const FIRST_BOOK = 'Dune';
/** Title of the second one. */
export const SECOND_BOOK = 'Fondation';

/**
 * Value of a figure rendered as "<value><label>", the shape used by the Home counters
 * and the Statistics tiles alike.
 */
export function figure(page: Page, label: string): Locator {
  return page.locator(`xpath=//div[normalize-space(text())="${label}"]/preceding-sibling::div[1]`);
}

/**
 * Runs a catalog search on the Discover screen and waits for the results.
 *
 * The placeholder is matched on its opening words rather than in full: it lists what can be
 * searched, so it grows with every criterion the screen learns, and pinning the whole
 * sentence made four journeys fail on a wording change that broke nothing.
 */
export async function searchCatalog(page: Page, query: string): Promise<void> {
  await page.getByPlaceholder(/^Rechercher un titre/).fill(query);
  await page.getByRole('button', { name: 'Rechercher' }).click();
  await page.getByRole('button', { name: 'Collection', exact: true }).first().waitFor();
}

/**
 * Adds the topmost still-addable search result to the collection or to the wishlist.
 * Once added, a result swaps its two buttons for a confirmation, so calling this twice
 * in a row adds the first result then the second one.
 */
export async function addResult(page: Page, target: 'Collection' | 'Souhaits'): Promise<void> {
  await page.getByRole('button', { name: target, exact: true }).first().click();
}

/** Opens the detail screen of a title from a cover tile (Collection or Home). */
export async function openTitle(page: Page, title: string): Promise<void> {
  await page.getByText(title, { exact: true }).first().click();
  await page.getByRole('heading', { name: title }).waitFor();
}
