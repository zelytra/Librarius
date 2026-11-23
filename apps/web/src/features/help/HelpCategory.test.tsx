import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { HelpCategory } from './HelpCategory';
import { FAQ_SECTIONS } from './faq';
import fr from './faq.fr.json';
import en from './faq.en.json';

/** #630: the "Un problème ?" FAQ of Settings — questions, answers, and shortcuts to the screens. */
describe('HelpCategory', () => {
  test('lists the questions by theme, and opens an answer', async () => {
    renderWithProviders(<HelpCategory />);

    expect(await screen.findByRole('heading', { name: 'Le statut de confiance' })).toBeInTheDocument();
    const question = screen.getByText('Comment devient-on « de confiance » ?');
    const details = question.closest('details')!;
    expect(details).not.toHaveAttribute('open');

    await userEvent.click(question);
    expect(details).toHaveAttribute('open');
    expect(screen.getByText(/au moins trois mois d’ancienneté/)).toBeVisible();
  });

  test('offers shortcuts to the screen an answer is about', async () => {
    renderWithProviders(<HelpCategory />);

    const add = (await screen.findByText('Comment ajouter un livre ?')).closest('details')!;
    await userEvent.click(within(add).getByText('Comment ajouter un livre ?'));
    expect(within(add).getByRole('link', { name: 'Chercher un livre' })).toHaveAttribute('href', '/discover');
    expect(within(add).getByRole('link', { name: 'Scanner un code-barres' })).toHaveAttribute('href', '/scan');
    const notifications = screen.getByText('Comment supprimer mes notifications ?').closest('details')!;
    await userEvent.click(within(notifications).getByText('Comment supprimer mes notifications ?'));
    expect(within(notifications).getByRole('link', { name: 'Ouvrir mes notifications' }))
      .toHaveAttribute('href', '/notifications');
  });

  test('every question has its text in French and in English', () => {
    const ids = FAQ_SECTIONS.flatMap((section) => section.questions.map((q) => q.id));
    expect(new Set(ids).size).toBe(ids.length);
    type Faq = { sections: Record<string, string>; actions: Record<string, string>; questions: Record<string, { q: string; a: string }> };
    for (const locale of [fr, en] as Faq[]) {
      for (const section of FAQ_SECTIONS) {
        expect(locale.sections[section.id], section.id).toBeTruthy();
        for (const q of section.questions) {
          expect(locale.questions[q.id]?.q, q.id).toBeTruthy();
          expect(locale.questions[q.id]?.a, q.id).toBeTruthy();
          for (const action of q.actions ?? []) expect(locale.actions[action.label], action.label).toBeTruthy();
        }
      }
    }
  });
});
