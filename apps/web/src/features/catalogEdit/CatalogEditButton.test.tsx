import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { Toaster } from '../../shared/ui/Toaster';
import { CatalogEditButton } from './CatalogEditButton';

/**
 * "Proposer une modification" (#618): the form starts from the record's current values, sends
 * only what changed — the sale links (#619) as their full list — and says what became of it.
 */

const FIELDS = {
  targetType: 'EDITION',
  targetId: 'ed-1',
  fields: {
    title: 'Dune', authors: 'Frank Herbert', synopsis: null, genres: null, originalYear: '1965',
    volumeNumber: null, isbn13: '9782266320481', isbn10: null, publisher: 'Pocket', language: 'fr',
    pageCount: '928', format: null, releaseDate: null, coverUrl: null, listPrice: null,
  },
  saleLinks: [{ store: 'FNAC', url: 'https://www.fnac.com/a1' }],
  pendingByMe: false,
};

function serves(fields: object, answer: object = { status: 'PENDING', verification: 'NOT_RUN' }, status = 201) {
  const posted: Record<string, unknown>[] = [];
  server.use(
    http.get('*/api/catalog-edits/fields', () => HttpResponse.json(fields)),
    http.post('*/api/catalog-edits', async ({ request }) => {
      posted.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json(answer, { status });
    }),
  );
  return posted;
}

async function open() {
  renderWithProviders(
    <>
      <CatalogEditButton targetType="EDITION" targetId="ed-1" />
      <Toaster />
    </>,
  );
  await userEvent.click(screen.getByRole('button', { name: 'Proposer une modification' }));
  return within(await screen.findByRole('dialog', { name: 'Modifier la fiche' }));
}

describe('CatalogEditButton', () => {
  test('starts from the current values and sends only the fields that changed', async () => {
    const posted = serves(FIELDS);
    const dialog = await open();

    const title = await dialog.findByLabelText('Titre');
    expect(title).toHaveValue('Dune');
    expect(dialog.getByLabelText('Nombre de pages')).toHaveValue('928');
    // Nothing changed yet: nothing to send.
    expect(dialog.getByRole('button', { name: 'Envoyer la proposition' })).toBeDisabled();

    await userEvent.clear(title);
    await userEvent.type(title, 'Dune (nouvelle traduction)');
    await userEvent.clear(dialog.getByLabelText('Éditeur'));
    await userEvent.click(dialog.getByRole('button', { name: 'Envoyer la proposition' }));

    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toEqual({
      targetType: 'EDITION',
      targetId: 'ed-1',
      fields: { title: 'Dune (nouvelle traduction)', publisher: '' },
    });
    expect(await screen.findByText('Proposition envoyée : un administrateur va la relire.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  test('sends the whole list of sale links once one is added', async () => {
    const posted = serves(FIELDS, { status: 'APPLIED', verification: 'PASSED' });
    const dialog = await open();

    expect(await dialog.findByDisplayValue('https://www.fnac.com/a1')).toBeInTheDocument();
    await userEvent.click(dialog.getByRole('button', { name: 'Ajouter un lien' }));
    await userEvent.type(dialog.getByLabelText('Lien de vente 2'), 'https://www.decitre.fr/livres/dune.html');
    await userEvent.click(dialog.getByRole('button', { name: 'Envoyer la proposition' }));

    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toEqual({
      targetType: 'EDITION',
      targetId: 'ed-1',
      fields: {},
      saleLinks: ['https://www.fnac.com/a1', 'https://www.decitre.fr/livres/dune.html'],
    });
    expect(await screen.findByText('Le magasin confirme ce livre : ta modification est appliquée.')).toBeInTheDocument();
  });

  test('removing a link is a change too', async () => {
    const posted = serves(FIELDS);
    const dialog = await open();

    await userEvent.click(await dialog.findByRole('button', { name: 'Retirer le lien 1' }));
    await userEvent.click(dialog.getByRole('button', { name: 'Envoyer la proposition' }));

    await waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0].saleLinks).toEqual([]);
  });

  test('says when the store could not confirm the book', async () => {
    serves(FIELDS, { status: 'PENDING', verification: 'FAILED', verificationDetail: 'FETCH_FAILED' });
    const dialog = await open();

    await userEvent.type(await dialog.findByLabelText('Genres'), 'Science-fiction');
    await userEvent.click(dialog.getByRole('button', { name: 'Envoyer la proposition' }));

    expect(await screen.findByText('Le magasin n’a pas pu confirmer ce livre : un administrateur va relire la proposition.'))
      .toBeInTheDocument();
  });

  test('keeps the form open with an explanation when the server refuses it', async () => {
    serves(FIELDS, {}, 400);
    const dialog = await open();

    await userEvent.type(await dialog.findByLabelText('Genres'), 'x');
    await userEvent.click(dialog.getByRole('button', { name: 'Envoyer la proposition' }));

    expect(await dialog.findByRole('alert')).toHaveTextContent('Une valeur n’est pas acceptée');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  test('tells a member whose proposal is already waiting', async () => {
    serves({ ...FIELDS, pendingByMe: true });
    const dialog = await open();

    expect(await dialog.findByText('Une proposition de ta part attend déjà d’être relue sur cette fiche.')).toBeInTheDocument();
    expect(dialog.queryByRole('button', { name: 'Envoyer la proposition' })).not.toBeInTheDocument();
  });

  test('a series offers its own fields', async () => {
    serves({
      targetType: 'SERIES', targetId: 's-1', saleLinks: [], pendingByMe: false,
      fields: { title: 'One Piece', originalTitle: null, totalVolumes: '100', status: 'ONGOING', synopsis: null, coverUrl: null },
    });
    renderWithProviders(<CatalogEditButton targetType="SERIES" targetId="s-1" />);
    await userEvent.click(screen.getByRole('button', { name: 'Proposer une modification' }));
    const dialog = within(await screen.findByRole('dialog'));

    expect(await dialog.findByLabelText('Nombre de tomes')).toHaveValue('100');
    expect(dialog.getByLabelText('Statut de parution')).toHaveValue('ONGOING');
    expect(dialog.queryByRole('button', { name: 'Ajouter un lien' })).not.toBeInTheDocument();
  });
});
