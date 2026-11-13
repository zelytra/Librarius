import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Adds a title through the add sheet (#399, #411): opens it from the `index`-th "Ajouter",
 * picks a reading status and an ownership when given — the sheet starts on "Pile à lire" and
 * "Possédé" — and confirms.
 */
export async function addAs(
  target: 'Pile à lire' | 'Lu' | 'En cours' | 'Souhaits',
  index = 0,
  ownership?: 'Possédé' | 'Emprunté' | 'Livre numérique' | "Liste d'envies" | 'Non possédé',
) {
  const buttons = await screen.findAllByRole('button', { name: 'Ajouter' });
  await userEvent.click(buttons[index]);
  const sheet = within(await screen.findByRole('dialog'));
  if (target === 'Souhaits') {
    // A wish is a title wanted and not read yet: the pile, on the wishlist.
    await userEvent.click(sheet.getByRole('radio', { name: 'Pile à lire' }));
    await userEvent.click(sheet.getByRole('radio', { name: "Liste d'envies" }));
  } else {
    await userEvent.click(sheet.getByRole('radio', { name: target }));
    if (ownership) await userEvent.click(sheet.getByRole('radio', { name: ownership }));
  }
  await userEvent.click(sheet.getByRole('button', { name: 'Ajouter' }));
}
