import { endExpiredSession, getAccessToken, tryRenewSession } from './authToken';

/**
 * A GET with the session, for the answers `apiClient` cannot parse — a file to download, an
 * image (#500). Same 401 handling as `apiClient`: renew once, replay, and only then end the
 * session in place.
 */
async function send(url: string, token: string | undefined): Promise<Response> {
  return fetch(url, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

export async function authorisedGet(url: string): Promise<Response> {
  let response = await send(url, getAccessToken());
  if (response.status === 401) {
    if (await tryRenewSession()) {
      response = await send(url, getAccessToken());
    } else {
      endExpiredSession();
    }
  }
  return response;
}
