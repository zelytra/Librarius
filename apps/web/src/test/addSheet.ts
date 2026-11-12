import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Adds a title the way a reader does since #399: the single "Ajouter" button, the target
 * picked in the sheet, then the sheet's own "Ajouter". `index` picks among several results.
 */
export async function addAs(target: 'Pile à lire' | 'Lu' | 'Souhaits', index = 0) {
  const buttons = await screen.findAllByRole('button', { name: 'Ajouter' });
  await userEvent.click(buttons[index]);
  const sheet = within(await screen.findByRole('dialog'));
  await userEvent.click(sheet.getByRole('radio', { name: target }));
  await userEvent.click(sheet.getByRole('button', { name: 'Ajouter' }));
}
