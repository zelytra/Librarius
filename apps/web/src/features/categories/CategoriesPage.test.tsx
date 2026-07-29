import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { BUILTIN_CATEGORIES, customCategory } from '../../test/fixtures';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import type { CategoryDto } from '../../api/generated/librarius';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { CategoriesPage } = await import('./CategoriesPage');

/** Serves `/api/categories` from a list the mutations below actually modify. */
function categoriesReturn(list: CategoryDto[]) {
  const state = [...list];
  server.use(http.get('*/api/categories', () => HttpResponse.json(state)));
  return state;
}

describe('CategoriesPage', () => {
  beforeEach(resetAuth);

  test('lists the built-ins and the user categories, flagging the built-ins', async () => {
    categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    renderWithProviders(<CategoriesPage />);

    expect(await screen.findByText('Doré')).toBeInTheDocument();
    expect(screen.getByText('Or')).toBeInTheDocument();
    expect(screen.getAllByText('Intégrée')).toHaveLength(3);
  });

  /** A built-in is shared by every account: nothing on its row offers to touch it. */
  test('offers no action on a built-in', async () => {
    categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    renderWithProviders(<CategoriesPage />);

    await screen.findByText('Doré');
    expect(screen.queryByLabelText('Renommer Or')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Supprimer Or')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Renommer Doré')).toBeInTheDocument();
  });

  test('creates a category and shows it in the list', async () => {
    const state = categoriesReturn([...BUILTIN_CATEGORIES]);
    let sent: unknown = null;
    server.use(http.post('*/api/categories', async ({ request }) => {
      sent = await request.json();
      const created = customCategory({ id: 'cat-new', code: 'a-relire', label: 'À relire' });
      state.push(created);
      return HttpResponse.json(created);
    }));
    renderWithProviders(<CategoriesPage />);

    await screen.findByText('Or');
    await userEvent.type(screen.getByLabelText('Nom de la catégorie'), 'À relire');
    await userEvent.click(screen.getByText('Créer'));

    await waitFor(() => expect(sent).toEqual({ label: 'À relire' }));
    expect(await screen.findByText('À relire')).toBeInTheDocument();
  });

  test('a name already taken is reported instead of being swallowed', async () => {
    categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    server.use(http.post('*/api/categories', () => new HttpResponse(null, { status: 409 })));
    renderWithProviders(<CategoriesPage />);

    await screen.findByText('Doré');
    await userEvent.type(screen.getByLabelText('Nom de la catégorie'), 'Doré');
    await userEvent.click(screen.getByText('Créer'));

    expect(await screen.findByText('Tu as déjà une catégorie de ce nom.')).toBeInTheDocument();
  });

  test('renames a category', async () => {
    const state = categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    let sent: unknown = null;
    server.use(http.put('*/api/categories/:id', async ({ request }) => {
      sent = await request.json();
      state[state.length - 1] = customCategory({ code: 'a-relire', label: 'À relire' });
      return HttpResponse.json(state[state.length - 1]);
    }));
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByLabelText('Renommer Doré'));
    const field = screen.getByLabelText('Nouveau nom');
    await userEvent.clear(field);
    await userEvent.type(field, 'À relire');
    await userEvent.click(screen.getByText('Enregistrer'));

    await waitFor(() => expect(sent).toEqual({ label: 'À relire' }));
    expect(await screen.findByText('À relire')).toBeInTheDocument();
  });

  test('cancelling a rename leaves the name alone', async () => {
    categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByLabelText('Renommer Doré'));
    await userEvent.type(screen.getByLabelText('Nouveau nom'), ' modifié');
    await userEvent.click(screen.getByText('Annuler'));

    expect(screen.getByText('Doré')).toBeInTheDocument();
  });

  /**
   * The deletion is the decision this feature turns on, so the screen says what it costs
   * before doing it: the titles stay, they only lose the rank.
   */
  test('deleting asks for a confirmation and says the titles are kept', async () => {
    const state = categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    let deleted: string | null = null;
    server.use(http.delete('*/api/categories/:id', ({ params }) => {
      deleted = String(params.id);
      state.pop();
      return new HttpResponse(null, { status: 204 });
    }));
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByLabelText('Supprimer Doré'));

    expect(screen.getByText(/restent dans ta collection/)).toBeInTheDocument();
    expect(deleted).toBeNull();

    await userEvent.click(screen.getByText('Oui, supprimer'));

    await waitFor(() => expect(deleted).toBe('cat-dore'));
    await waitFor(() => expect(screen.queryByText('Doré')).not.toBeInTheDocument());
  });

  test('cancelling the confirmation keeps the category', async () => {
    categoriesReturn([...BUILTIN_CATEGORIES, customCategory()]);
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByLabelText('Supprimer Doré'));
    await userEvent.click(screen.getByText('Annuler'));

    expect(screen.getByText('Doré')).toBeInTheDocument();
    expect(screen.queryByText(/restent dans ta collection/)).not.toBeInTheDocument();
  });

  test('invites the user to create one when they have none of their own', async () => {
    categoriesReturn([...BUILTIN_CATEGORIES]);
    renderWithProviders(<CategoriesPage />);

    expect(await screen.findByText(/pas encore créé de catégorie/)).toBeInTheDocument();
  });

  test('offers a retry when the categories cannot be loaded', async () => {
    let attempts = 0;
    server.use(http.get('*/api/categories', () => {
      attempts += 1;
      return attempts === 1
        ? new HttpResponse(null, { status: 500 })
        : HttpResponse.json([...BUILTIN_CATEGORIES, customCategory()]);
    }));
    renderWithProviders(<CategoriesPage />);

    await userEvent.click(await screen.findByText('Réessayer'));

    expect(await screen.findByText('Doré')).toBeInTheDocument();
  });

  test('prompts for sign-in when there is no session', async () => {
    setAuthenticated(false);
    renderWithProviders(<CategoriesPage />);

    expect(await screen.findByText(/Connecte-toi pour gérer tes catégories/)).toBeInTheDocument();
  });
});
