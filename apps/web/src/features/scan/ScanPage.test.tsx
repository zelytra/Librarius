import { render, screen, waitFor } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { TestProviders } from '../../test/utils';
import { catalogResult, libraryItem, wishlistItem } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';
import type { ScanResultDto } from '../../api/generated/librarius';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { ScanPage } = await import('./ScanPage');

/** A valid ISBN-13 (Folio's L'Étranger). */
const ISBN = '9782070360024';

function scanAnswers(result: Partial<ScanResultDto>) {
  const calls: string[] = [];
  server.use(
    http.get('*/api/scan/:isbn', ({ params }) => {
      calls.push(String(params.isbn));
      return HttpResponse.json({ isbn13: ISBN, match: 'NONE', ...result });
    }),
  );
  return calls;
}

function renderScan() {
  render(
    <TestProviders route="/scan">
      <Routes>
        <Route path="/scan" element={<ScanPage />} />
        <Route path="/detail/:id" element={<p>fiche du livre</p>} />
        <Route path="/catalog/:provider/:ref" element={<p>fiche du catalogue</p>} />
      </Routes>
    </TestProviders>,
  );
}

async function typeIsbn(value: string) {
  await userEvent.type(screen.getByLabelText("Ou tape l'ISBN"), value);
  await userEvent.click(screen.getByRole('button', { name: 'Vérifier' }));
}

describe('ScanPage', () => {
  beforeEach(resetAuth);

  test('says a typed ISBN is already at home, with its status, and opens it', async () => {
    scanAnswers({ match: 'ISBN', owned: libraryItem({ id: 'item-7', status: 'READ' }) });
    renderScan();

    await typeIsbn('978-2-07-036002-4');

    expect(await screen.findByText('Déjà chez toi · lu')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Voir' }));
    expect(await screen.findByText('fiche du livre')).toBeInTheDocument();
  });

  test('flags a copy recognised by its title as perhaps another edition', async () => {
    scanAnswers({ match: 'TITLE', owned: libraryItem({ status: 'TO_READ' }), catalog: catalogResult() });
    renderScan();

    await typeIsbn(ISBN);

    expect(await screen.findByText('Déjà chez toi · pas commencé')).toBeInTheDocument();
    expect(screen.getByText(/peut-être une autre édition/)).toBeInTheDocument();
  });

  test('finds a wish', async () => {
    scanAnswers({ match: 'ISBN', wished: wishlistItem() });
    renderScan();

    await typeIsbn(ISBN);

    expect(await screen.findByText('Dans tes souhaits')).toBeInTheDocument();
  });

  test('offers to add a book that is new, from its catalog entry', async () => {
    scanAnswers({ match: 'NONE', catalog: catalogResult({ title: "L'Étranger", providerRef: 'OL1W' }) });
    renderScan();

    await typeIsbn(ISBN);

    expect(await screen.findByText('Pas encore chez toi')).toBeInTheDocument();
    // Printed on the generated cover and as the entry's title.
    expect(screen.getAllByText("L'Étranger").length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Ajouter' }));
    expect(await screen.findByText('fiche du catalogue')).toBeInTheDocument();
  });

  test('refuses what is not an ISBN without asking the server', async () => {
    const calls = scanAnswers({});
    renderScan();

    await typeIsbn('3017620422003');

    expect(await screen.findByText(/pas un ISBN valide/)).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  test('looks a book up once however many times it is scanned', async () => {
    const calls = scanAnswers({ match: 'NONE', catalog: catalogResult() });
    renderScan();

    await typeIsbn(ISBN);
    await screen.findByText('Pas encore chez toi');
    await typeIsbn(ISBN);
    // The same book by its ISBN-10.
    await typeIsbn('2-07-036002-4');

    expect(calls).toEqual([ISBN]);
    expect(screen.getAllByText('Pas encore chez toi')).toHaveLength(1);
  });

  test('explains that this browser cannot read barcodes, and keeps the typed ISBN', () => {
    // jsdom has no BarcodeDetector, like Safari and Firefox.
    renderScan();

    expect(screen.getByRole('alert')).toHaveTextContent('ne sait pas lire les codes-barres');
    expect(screen.getByLabelText("Ou tape l'ISBN")).toBeInTheDocument();
  });

  // ── Sessions with a target (#168) ───────────────────────────────────────────

  test('files a new book straight into the session target, counts it, and undoes it', async () => {
    scanAnswers({ match: 'NONE', catalog: catalogResult({ title: 'Nouveau' }) });
    const posts: Record<string, unknown>[] = [];
    const deletes: string[] = [];
    server.use(
      http.post('*/api/library', async ({ request }) => {
        posts.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({ id: 'item-new' }, { status: 201 });
      }),
      http.delete('*/api/library/:id', ({ params }) => {
        deletes.push(String(params.id));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderScan();

    await userEvent.click(screen.getByRole('button', { name: 'Ajouter directement' }));
    await typeIsbn(ISBN);

    expect(await screen.findByText('Ajouté à ta pile à lire.')).toBeInTheDocument();
    expect(posts[0].status).toBe('TO_READ');
    expect(posts[0].ownership).toBe('OWNED');
    expect(screen.getByText('1 livre ajouté pendant ce scan')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Annuler' }));
    expect(await screen.findByText('Ajout annulé')).toBeInTheDocument();
    expect(deletes).toEqual(['item-new']);
  });

  /** #411: the session files with both status families, e.g. read on an e-reader. */
  test('files a scanned book with the reading status and the ownership picked', async () => {
    scanAnswers({ match: 'NONE', catalog: catalogResult({ title: 'Numérique' }) });
    const posts: Record<string, unknown>[] = [];
    server.use(http.post('*/api/library', async ({ request }) => {
      posts.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ id: 'item-ebook' }, { status: 201 });
    }));
    renderScan();

    await userEvent.click(screen.getByRole('button', { name: 'Ajouter directement' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Lu' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Livre numérique' }));
    await typeIsbn(ISBN);

    await waitFor(() => expect(posts[0]).toMatchObject({ status: 'READ', ownership: 'EBOOK' }));
  });

  test('reports a book already owned during a session instead of adding it again', async () => {
    scanAnswers({ match: 'ISBN', owned: libraryItem({ status: 'READ' }) });
    const posts: unknown[] = [];
    server.use(http.post('*/api/library', () => {
      posts.push(1);
      return HttpResponse.json({ id: 'x' }, { status: 201 });
    }));
    renderScan();

    await userEvent.click(screen.getByRole('button', { name: 'Ajouter directement' }));
    await userEvent.click(screen.getByRole('radio', { name: 'Lu' }));
    await typeIsbn(ISBN);

    expect(await screen.findByText('Déjà chez toi · lu')).toBeInTheDocument();
    expect(posts).toHaveLength(0);
  });
});

