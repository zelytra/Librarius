import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { note, resetDiagnostics } from './diagnostics';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { FeedbackButton } = await import('./FeedbackButton');

/** #489: a report from anywhere, with what the app knew at that moment. */
describe('FeedbackButton', () => {
  beforeEach(() => setAuthenticated(true));
  afterEach(() => {
    resetAuth();
    resetDiagnostics();
  });

  function captureReports() {
    const sent: Record<string, unknown>[] = [];
    server.use(http.post('*/api/feedback', async ({ request }) => {
      sent.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ id: 'f1' }, { status: 201 });
    }));
    return sent;
  }

  test('is not offered to someone signed out', () => {
    setAuthenticated(false);
    renderWithProviders(<FeedbackButton />);
    expect(screen.queryByRole('button', { name: 'Signaler un problème' })).not.toBeInTheDocument();
  });

  test('sends the kind, the description and the technical details', async () => {
    const sent = captureReports();
    note('503 GET /api/library');
    renderWithProviders(<FeedbackButton />);

    await userEvent.click(screen.getByRole('button', { name: 'Signaler un problème' }));
    const dialog = await screen.findByRole('dialog', { name: 'Signaler un problème' });
    expect(dialog).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Idée' }));
    await userEvent.type(screen.getByLabelText('Description'), 'Un tri par éditeur');
    await userEvent.click(screen.getByRole('button', { name: 'Envoyer' }));

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]).toMatchObject({ kind: 'IDEA', message: 'Un tri par éditeur', page: '/' });
    expect(sent[0].logs).toContain('503 GET /api/library');
    expect(sent[0].userAgent).toBe(navigator.userAgent);
    // Sent: the dialog closes on a thank-you.
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  test('leaves the technical details out when the box is unticked', async () => {
    const sent = captureReports();
    renderWithProviders(<FeedbackButton />);

    await userEvent.click(screen.getByRole('button', { name: 'Signaler un problème' }));
    await screen.findByRole('dialog');
    await userEvent.type(screen.getByLabelText('Description'), 'Ça plante');
    await userEvent.click(screen.getByRole('checkbox', { name: /informations techniques/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Envoyer' }));

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0]).toMatchObject({ kind: 'BUG', message: 'Ça plante' });
    expect(sent[0].logs).toBeUndefined();
    expect(sent[0].userAgent).toBeUndefined();
  });

  test('cannot be sent without a description', async () => {
    renderWithProviders(<FeedbackButton />);
    await userEvent.click(screen.getByRole('button', { name: 'Signaler un problème' }));
    expect(await screen.findByRole('button', { name: 'Envoyer' })).toBeDisabled();
  });

  test('keeps what was written when sending fails, and says so', async () => {
    server.use(http.post('*/api/feedback', () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<FeedbackButton />);

    await userEvent.click(screen.getByRole('button', { name: 'Signaler un problème' }));
    await screen.findByRole('dialog');
    await userEvent.type(screen.getByLabelText('Description'), 'Texte précieux');
    await userEvent.click(screen.getByRole('button', { name: 'Envoyer' }));

    expect(await screen.findByRole('alert')).toHaveTextContent("Le signalement n'a pas pu être envoyé");
    expect(screen.getByLabelText('Description')).toHaveValue('Texte précieux');
  });
});
