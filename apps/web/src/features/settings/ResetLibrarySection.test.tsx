import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { ResetLibrarySection } from './ResetLibrarySection';

/** Emptying the library (#382): nothing is sent until the word is typed. */
function recordsResets() {
  const calls: number[] = [];
  server.use(
    http.delete('*/api/me/library', () => {
      calls.push(1);
      return HttpResponse.json({ libraryItems: 3, wishlistItems: 1, goals: 0, categories: 0,
        seriesFollows: 0, seriesReviews: 0, authorFollows: 0 });
    }),
  );
  return calls;
}

async function openPanel() {
  renderWithProviders(<ResetLibrarySection />);
  await userEvent.click(screen.getByRole('button', { name: 'Vider ma bibliothèque' }));
}

describe('reset library', () => {
  test('keeps the button disabled until the confirmation word is typed', async () => {
    const calls = recordsResets();
    await openPanel();

    const confirm = screen.getByRole('button', { name: 'Vider définitivement' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Pour confirmer, tape VIDER'), 'vide');
    expect(confirm).toBeDisabled();
    expect(calls).toHaveLength(0);
  });

  test('empties the library once confirmed, and says so', async () => {
    const calls = recordsResets();
    await openPanel();

    await userEvent.type(screen.getByLabelText('Pour confirmer, tape VIDER'), 'vider');
    await userEvent.click(screen.getByRole('button', { name: 'Vider définitivement' }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(await screen.findByText(/Ta bibliothèque est vide/)).toBeInTheDocument();
  });

  test('says nothing was deleted when the request fails', async () => {
    server.use(http.delete('*/api/me/library', () => new HttpResponse(null, { status: 500 })));
    await openPanel();

    await userEvent.type(screen.getByLabelText('Pour confirmer, tape VIDER'), 'VIDER');
    await userEvent.click(screen.getByRole('button', { name: 'Vider définitivement' }));

    expect(await screen.findByText(/Rien n'a été supprimé/)).toBeInTheDocument();
  });
});
