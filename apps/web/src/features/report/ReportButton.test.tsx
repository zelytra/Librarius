import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { ReportButton } from './ReportButton';

/**
 * The "Signaler une erreur" affordance (#192): open the dialog, pick a reason, optionally
 * comment, and post one report. It is write-only, so the whole behaviour to lock down is that
 * the right body is sent and the user is told it went through.
 */

interface Recorder {
  posted: Record<string, unknown>[];
}

/** Records every report posted, and answers 201 the way the resource does. */
function recordsReports(): Recorder {
  const recorder: Recorder = { posted: [] };
  server.use(
    http.post('*/api/reports', async ({ request }) => {
      recorder.posted.push((await request.json()) as Record<string, unknown>);
      return HttpResponse.json({ id: 'report-1', status: 'OPEN' }, { status: 201 });
    }),
  );
  return recorder;
}

function renderButton() {
  return renderWithProviders(<ReportButton targetType="WORK" targetId="work-42" />);
}

/** The dialog, once it is open. */
function dialog() {
  return within(screen.getByRole('dialog'));
}

async function openDialog() {
  await userEvent.click(screen.getByText('Signaler une erreur'));
  return await screen.findByRole('dialog');
}

describe('report button', () => {
  beforeEach(() => recordsReports());

  test('opens the dialog from the trigger', async () => {
    renderButton();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await openDialog();

    expect(dialog().getByRole('group', { name: 'Motif du signalement' })).toBeInTheDocument();
    expect(dialog().getByText('Mauvaise couverture')).toBeInTheDocument();
  });

  /** Nothing can be sent until a reason is picked: the picklist is the one required field. */
  test('keeps the submit disabled until a reason is chosen', async () => {
    renderButton();
    await openDialog();

    expect(dialog().getByText('Envoyer le signalement').closest('button')).toBeDisabled();

    await userEvent.click(dialog().getByText('Mauvaise couverture'));

    expect(dialog().getByText('Envoyer le signalement').closest('button')).toBeEnabled();
  });

  /**
   * The acceptance case: a report carries the target it was opened on, the chosen reason and
   * the comment, and the dialog confirms it was sent.
   */
  test('posts the target, the reason and the comment, then confirms', async () => {
    const calls = recordsReports();
    renderButton();
    await openDialog();

    await userEvent.click(dialog().getByText('Doublon'));
    await userEvent.type(dialog().getByLabelText('Commentaire (facultatif)'), 'Déjà présent.');
    await userEvent.click(dialog().getByText('Envoyer le signalement'));

    await waitFor(() => expect(calls.posted).toHaveLength(1));
    expect(calls.posted[0]).toEqual({
      targetType: 'WORK',
      targetId: 'work-42',
      reason: 'DUPLICATE',
      comment: 'Déjà présent.',
    });
    expect(await screen.findByText('Signalement envoyé')).toBeInTheDocument();
  });

  /** The comment is optional: a report with only a reason is a complete report. */
  test('omits an empty comment from the payload', async () => {
    const calls = recordsReports();
    renderButton();
    await openDialog();

    await userEvent.click(dialog().getByText('Autre'));
    await userEvent.click(dialog().getByText('Envoyer le signalement'));

    await waitFor(() => expect(calls.posted).toHaveLength(1));
    expect(calls.posted[0]).toEqual({
      targetType: 'WORK',
      targetId: 'work-42',
      reason: 'OTHER',
      comment: undefined,
    });
  });

  /** A failed send is not silent, and it does not pretend the report went through. */
  test('shows an error and no confirmation when the post fails', async () => {
    server.use(http.post('*/api/reports', () => new HttpResponse(null, { status: 500 })));
    renderButton();
    await openDialog();

    await userEvent.click(dialog().getByText('Mauvaise couverture'));
    await userEvent.click(dialog().getByText('Envoyer le signalement'));

    expect(await dialog().findByText('Envoi impossible pour le moment. Réessaie plus tard.'))
      .toBeInTheDocument();
    expect(screen.queryByText('Signalement envoyé')).not.toBeInTheDocument();
  });

  /** Escape closes the dialog, the way it does anywhere else, sending nothing. */
  test('closes on Escape without sending anything', async () => {
    const calls = recordsReports();
    renderButton();
    await openDialog();

    await userEvent.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(calls.posted).toEqual([]);
  });
});
