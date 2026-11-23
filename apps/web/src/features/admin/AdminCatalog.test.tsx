import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';
import { resetToasts } from '../../shared/ui/toast';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { AdminPage } = await import('./AdminPage');

const BOB = { id: 'bob', displayName: 'Bob' };

const PENDING = {
  id: 'ce1', targetType: 'EDITION', targetId: 'e1', targetTitle: 'Dune', status: 'PENDING',
  verification: 'FAILED', verificationDetail: 'FETCH_FAILED', verificationUrl: 'https://www.amazon.fr/dp/1',
  submitter: BOB, createdAt: '2026-11-20T10:00:00Z',
  changes: [
    { field: 'publisher', before: 'Pocket', after: 'Robert Laffont', current: 'Pocket' },
    { field: 'saleLinks', before: null, after: 'https://www.amazon.fr/dp/1', current: null },
  ],
};

const VERIFIED = {
  id: 'ce2', targetType: 'EDITION', targetId: 'e2', targetTitle: 'Hypérion', status: 'APPLIED',
  verification: 'PASSED', verificationUrl: 'https://www.fnac.com/a2', submitter: BOB,
  createdAt: '2026-11-19T10:00:00Z', reviewedAt: '2026-11-19T10:00:00Z',
  changes: [{ field: 'title', before: 'Hyperion', after: 'Hypérion', current: 'Hypérion' }],
};

const REPORTS = {
  page: 0, size: 50, total: 1,
  items: [{
    id: 'r1', targetType: 'SERIES', targetId: 's1', targetTitle: 'One Piece', seriesId: 's1', reason: 'WRONG_COVER',
    comment: 'La couverture est celle du tome 2.', status: 'OPEN', reporter: BOB, createdAt: '2026-11-21T10:00:00Z',
  }],
};

function renderCatalog() {
  return renderWithProviders(
    <>
      <AdminPage />
      <Toaster />
    </>,
    { route: '/admin?tab=catalog' },
  );
}

/** #618–#621: the admins' review of the catalog edits and of the reports filed from the book screens. */
describe('AdminPage — catalogue', () => {
  const edits: Record<string, string[]> = {};
  beforeEach(() => {
    setAuthenticated(true);
    edits.status = [];
    edits.decided = [];
    server.use(
      http.get('*/api/me', () => HttpResponse.json({ id: 'alice', displayName: 'Alice', admin: true })),
      http.get('*/api/admin/feedback', () => HttpResponse.json({ page: 0, size: 50, total: 0, items: [] })),
      http.get('*/api/admin/catalog-edits', ({ request }) => {
        const status = new URL(request.url).searchParams.get('status');
        edits.status.push(status ?? 'ALL');
        const items = [PENDING, VERIFIED].filter((e) => !status || e.status === status);
        return HttpResponse.json({ page: 0, size: 50, total: items.length, items });
      }),
      http.post('*/api/admin/catalog-edits/:id/:decision', async ({ params, request }) => {
        const body = (await request.json()) as { note?: string };
        edits.decided.push(`${params.id}:${params.decision}:${body.note ?? ''}`);
        return HttpResponse.json({ ...PENDING, status: params.decision === 'reject' ? 'REJECTED' : 'APPLIED' });
      }),
      http.get('*/api/admin/reports', () => HttpResponse.json(REPORTS)),
    );
  });
  afterEach(() => {
    resetAuth();
    resetToasts();
  });

  test('lists the pending edits first, each change before and after, and the outcome of the store check', async () => {
    renderCatalog();

    const card = within((await screen.findByRole('heading', { name: 'Dune' })).closest('article')!);
    expect(edits.status[0]).toBe('PENDING');
    expect(card.getByText('Éditeur')).toBeInTheDocument();
    expect(card.getByText('Pocket')).toBeInTheDocument();
    expect(card.getByText('Robert Laffont')).toBeInTheDocument();
    expect(card.getByText('Vérification automatique échouée : la page du magasin n’a pas pu être lue.')).toBeInTheDocument();
    expect(card.getByText(/Proposée par Bob/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Hypérion' })).not.toBeInTheDocument();
  });

  test('approves or rejects a proposal with a word for its author', async () => {
    renderCatalog();

    const card = within((await screen.findByRole('heading', { name: 'Dune' })).closest('article')!);
    await userEvent.type(card.getByLabelText('Mot pour l’auteur (facultatif)'), 'Merci !');
    await userEvent.click(card.getByRole('button', { name: 'Appliquer' }));
    await waitFor(() => expect(edits.decided).toEqual(['ce1:approve:Merci !']));
    expect(await screen.findByText('Modification appliquée pour tous les lecteurs.')).toBeInTheDocument();

    await userEvent.click(card.getByRole('button', { name: 'Refuser' }));
    await waitFor(() => expect(edits.decided).toContain('ce1:reject:Merci !'));
  });

  test('shows the edits a store page validated, and reverts one', async () => {
    renderCatalog();
    await screen.findByRole('heading', { name: 'Dune' });

    await userEvent.click(screen.getByRole('button', { name: 'Appliquées' }));
    const card = within((await screen.findByRole('heading', { name: 'Hypérion' })).closest('article')!);
    expect(card.getByText('Vérifiée automatiquement sur la page du magasin')).toBeInTheDocument();
    expect(card.getByRole('link', { name: /fnac\.com/ })).toHaveAttribute('href', 'https://www.fnac.com/a2');

    await userEvent.click(card.getByRole('button', { name: 'Annuler la modification' }));
    await waitFor(() => expect(edits.decided).toEqual(['ce2:revert:']));
  });

  test('lists the reports filed from the book screens and upholds one', async () => {
    let saved: unknown = null;
    server.use(
      http.put('*/api/admin/reports/:id/status', async ({ request }) => {
        saved = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderCatalog();

    await userEvent.click(await screen.findByRole('button', { name: 'Signalements' }));
    const card = within((await screen.findByRole('heading', { name: 'One Piece' })).closest('article')!);
    expect(card.getByText('Mauvaise couverture')).toBeInTheDocument();
    expect(card.getByText('« La couverture est celle du tome 2. »')).toBeInTheDocument();
    expect(card.getByRole('link', { name: 'Ouvrir la série' })).toHaveAttribute('href', '/series/s1');

    await userEvent.click(card.getByRole('button', { name: 'Fondé' }));
    await waitFor(() => expect(saved).toEqual({ status: 'UPHELD' }));
    expect(await screen.findByText('Signalement marqué fondé.')).toBeInTheDocument();
  });

  /** #631: an edit a trusted member applied says so, and can be reverted like any applied one. */
  test('marks an edit applied on its author\'s trusted standing', async () => {
    server.use(http.get('*/api/admin/catalog-edits', () => HttpResponse.json({
      page: 0, size: 50, total: 1,
      items: [{ ...VERIFIED, id: 'ce9', verification: 'NOT_RUN', verificationUrl: null, appliedByTrust: true }],
    })));
    renderCatalog();

    const card = within((await screen.findByRole('heading', { name: 'Hypérion' })).closest('article')!);
    expect(card.getByText('Appliquée directement : l’auteur est membre de confiance.')).toBeInTheDocument();
    expect(card.getByRole('button', { name: 'Annuler la modification' })).toBeInTheDocument();
  });

  test('says so when nothing waits', async () => {
    server.use(http.get('*/api/admin/catalog-edits', () => HttpResponse.json({ page: 0, size: 50, total: 0, items: [] })));
    renderCatalog();

    expect(await screen.findByText('Aucune modification à afficher.')).toBeInTheDocument();
  });
});
