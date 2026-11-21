import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth, setAuthenticated } from '../../test/oidcMock';
import { Toaster } from '../../shared/ui/Toaster';
import { resetToasts } from '../../shared/ui/toast';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));
// The browser's canvas is not in jsdom: the crop and re-encoding are stood in for.
vi.mock('./avatarImage', async (original) => ({
  ...(await original<typeof import('./avatarImage')>()),
  prepareAvatar: vi.fn(() => Promise.resolve(new Blob(['jpeg'], { type: 'image/jpeg' }))),
}));

const { AvatarPicker } = await import('./AvatarPicker');
const { prepareAvatar } = await import('./avatarImage');

const photo = () => new File(['x'], 'moi.jpg', { type: 'image/jpeg' });

/** #500: choosing, replacing and removing one's own profile picture. */
describe('AvatarPicker', () => {
  beforeEach(() => {
    setAuthenticated(true);
    URL.createObjectURL = vi.fn(() => 'blob:avatar');
    URL.revokeObjectURL = vi.fn();
  });
  afterEach(() => {
    resetAuth();
    resetToasts();
  });

  function meWith(version: number | null) {
    server.use(http.get('*/api/me', () => HttpResponse.json({ id: 'me', displayName: 'Moi', avatarVersion: version })));
  }

  test('uploads the chosen picture and says so', async () => {
    meWith(null);
    let type: string | null = null;
    server.use(
      http.put('*/api/me/avatar', ({ request }) => {
        type = request.headers.get('content-type');
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.upload(await screen.findByLabelText('Choisir une photo'), photo());
    // #571: the picture is framed before it leaves.
    await userEvent.click(await screen.findByRole('button', { name: 'Enregistrer la photo' }));

    await waitFor(() => expect(type).toBe('image/jpeg'));
    expect(await screen.findByText('Photo de profil mise à jour.')).toBeInTheDocument();
  });

  /** #571: zoom and move the picture, then the square framed is the one sent. */
  test('frames the picture with the zoom and the arrow keys before sending it', async () => {
    meWith(null);
    server.use(http.put('*/api/me/avatar', () => new HttpResponse(null, { status: 204 })));
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.upload(await screen.findByLabelText('Choisir une photo'), photo());
    const dialog = await screen.findByRole('dialog', { name: 'Recadrer la photo' });
    const zoom = screen.getByRole('slider', { name: 'Zoom' });
    // A range input takes its value from a change event; there is no drag to simulate here.
    fireEvent.change(zoom, { target: { value: '2' } });
    const frame = screen.getByRole('group', { name: /Position de la photo/ });
    frame.focus();
    await userEvent.keyboard('{ArrowRight}{ArrowRight}');
    await userEvent.click(screen.getByRole('button', { name: 'Enregistrer la photo' }));

    await waitFor(() => expect(prepareAvatar).toHaveBeenLastCalledWith(expect.any(File), { zoom: 2, x: 0.2, y: 0 }));
    expect(dialog).not.toBeInTheDocument();
  });

  // The chosen file never becomes a URL on the page (CodeQL js/xss-through-dom): the preview
  // is drawn on a canvas from the decoded picture, the way the upload itself decodes it.
  test('previews the picture on a canvas, never through a URL of the file', async () => {
    meWith(null);
    const toUrl = vi.spyOn(URL, 'createObjectURL');
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.upload(await screen.findByLabelText('Choisir une photo'), photo());
    const dialog = await screen.findByRole('dialog', { name: 'Recadrer la photo' });

    expect(dialog.querySelector('canvas')).not.toBeNull();
    expect(dialog.querySelector('img')).toBeNull();
    expect(toUrl).not.toHaveBeenCalled();
    toUrl.mockRestore();
  });

  test('sends nothing when the framing is cancelled', async () => {
    meWith(null);
    const put = vi.fn();
    server.use(http.put('*/api/me/avatar', () => {
      put();
      return new HttpResponse(null, { status: 204 });
    }));
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.upload(await screen.findByLabelText('Choisir une photo'), photo());
    await userEvent.click(await screen.findByRole('button', { name: 'Annuler' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(put).not.toHaveBeenCalled();
  });

  test('says why a picture is refused', async () => {
    meWith(null);
    server.use(http.put('*/api/me/avatar', () => new HttpResponse(null, { status: 415 })));
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.upload(await screen.findByLabelText('Choisir une photo'), photo());
    await userEvent.click(await screen.findByRole('button', { name: 'Enregistrer la photo' }));

    expect(await screen.findByText('Ce fichier n’est pas une image JPEG ou PNG.')).toBeInTheDocument();
  });

  test('refuses a file that is not an image before sending anything', async () => {
    meWith(null);
    const put = vi.fn();
    server.use(http.put('*/api/me/avatar', () => {
      put();
      return new HttpResponse(null, { status: 204 });
    }));
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.upload(await screen.findByLabelText('Choisir une photo'),
      new File(['<svg/>'], 'x.svg', { type: 'image/svg+xml' }), { applyAccept: false });

    expect(await screen.findByText('Ce fichier n’est pas une image JPEG ou PNG.')).toBeInTheDocument();
    expect(put).not.toHaveBeenCalled();
  });

  test('removes the picture', async () => {
    meWith(7);
    server.use(
      http.get('*/api/users/me/avatar', () => new HttpResponse(new Uint8Array([1]), { headers: { 'Content-Type': 'image/jpeg' } })),
      http.delete('*/api/me/avatar', () => new HttpResponse(null, { status: 204 })),
    );
    renderWithProviders(<><AvatarPicker /><Toaster /></>);

    await userEvent.click(await screen.findByRole('button', { name: 'Retirer la photo' }));

    expect(await screen.findByText('Photo de profil retirée.')).toBeInTheDocument();
  });
});
