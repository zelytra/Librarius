import { ApiError } from '../../shared/apiClient';
import { getAccessToken, redirectToSignIn, tryRenewSession } from '../../shared/authToken';

/**
 * Downloads the account export.
 *
 * Written by hand rather than through the generated hook, and this is the one place in the
 * application where that is the right call: `apiClient` parses every response as JSON, and
 * this endpoint answers with a file — a CSV in particular is not JSON — or with a `202` and
 * a job to poll when the library is too large to serialise inside the request. Neither fits
 * a generated query. The bearer token and the silent renewal still come from `authToken`,
 * so an expired session is handled here exactly as it is everywhere else.
 *
 * The component drives this from a React Query mutation: nothing fetches from an effect.
 */

export type ExportFormat = 'json' | 'csv';

export type DownloadedExport = { filename: string; blob: Blob };

type DeferredJob = { id: string; status: 'PENDING' | 'READY' | 'FAILED' };

/** How often a deferred export is asked about, and for how long. */
const POLL_INTERVAL_MS = 1_500;
const POLL_TIMEOUT_MS = 120_000;

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function send(url: string, token: string | undefined): Promise<Response> {
  return fetch(url, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

/** Same 401 handling as `apiClient`: renew once, replay, and only then give up. */
async function authorisedGet(url: string): Promise<Response> {
  let response = await send(url, getAccessToken());
  if (response.status === 401) {
    if (await tryRenewSession()) {
      response = await send(url, getAccessToken());
    } else {
      redirectToSignIn();
    }
  }
  return response;
}

/** Name the API asked the browser to save the file under. */
function filenameFrom(response: Response, format: ExportFormat): string {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  return match?.[1] ?? `librarius-export.${format}`;
}

export async function requestExport(format: ExportFormat): Promise<DownloadedExport> {
  let response = await authorisedGet(`/api/export?format=${format}`);

  // 202: the account is large enough that the API builds the file in the background and
  // hands back a job. Poll it until the file is there.
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  while (response.status === 202) {
    if (Date.now() > deadline) {
      throw new ApiError(504, '/api/export', undefined);
    }
    const job = (await response.json()) as DeferredJob;
    await delay(POLL_INTERVAL_MS);
    response = await authorisedGet(`/api/export/${job.id}`);
  }

  if (!response.ok) {
    const body = await response.text().catch(() => undefined);
    throw new ApiError(response.status, '/api/export', body);
  }
  return { filename: filenameFrom(response, format), blob: await response.blob() };
}

/** Hands the file to the browser. Isolated so the flow above stays testable. */
export function saveExport({ filename, blob }: DownloadedExport): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
