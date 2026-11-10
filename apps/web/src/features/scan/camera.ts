/**
 * Reads barcodes off the device camera, with the browser's own `BarcodeDetector`.
 *
 * No decoding library is shipped: Chrome and the Android WebView the native app runs in both
 * carry a detector backed by the platform's barcode reader, which is faster and steadier than
 * anything a bundle could add, and costs nothing to download. Where there is none (Safari,
 * Firefox), the screen says so and the ISBN is typed instead — the issue's manual fallback.
 */

/** The slice of the Shape Detection API this screen uses: not in TypeScript's DOM types yet. */
interface DetectedBarcode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>;
}
type BarcodeDetectorCtor = new (options: { formats: string[] }) => BarcodeDetectorLike;

export type CameraFailure = 'unsupported' | 'denied' | 'unavailable';

/** Whether this browser can read barcodes from the camera at all. */
export function cameraScanSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'BarcodeDetector' in window &&
    typeof navigator !== 'undefined' &&
    Boolean(navigator.mediaDevices?.getUserMedia)
  );
}

/** How often a frame is read: fast enough to feel instant, slow enough to stay cool. */
const FRAME_MS = 250;

/**
 * Starts the rear camera in `video` and calls `onCode` with each barcode it reads; the
 * returned function stops it. Rejects with a {@link CameraFailure} the screen can explain.
 */
export async function startScanning(
  video: HTMLVideoElement,
  onCode: (code: string) => void,
): Promise<() => void> {
  if (!cameraScanSupported()) throw failure('unsupported');
  let stream: MediaStream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' } },
      audio: false,
    });
  } catch (error) {
    const name = (error as { name?: string }).name;
    throw failure(name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'unavailable');
  }

  const Detector = (window as unknown as { BarcodeDetector: BarcodeDetectorCtor }).BarcodeDetector;
  // EAN-13 is the barcode printed on books; UPC-A covers the odd American paperback.
  const detector = new Detector({ formats: ['ean_13', 'upc_a'] });
  video.srcObject = stream;
  video.setAttribute('playsinline', 'true');
  video.muted = true;
  await video.play().catch(() => undefined);

  let stopped = false;
  let busy = false;
  const timer = window.setInterval(() => {
    if (stopped || busy || video.readyState < 2) return;
    busy = true;
    detector
      .detect(video)
      .then((codes) => codes.forEach((c) => onCode(c.rawValue)))
      .catch(() => undefined)
      .finally(() => {
        busy = false;
      });
  }, FRAME_MS);

  return () => {
    stopped = true;
    window.clearInterval(timer);
    stream.getTracks().forEach((track) => track.stop());
    video.srcObject = null;
  };
}

function failure(kind: CameraFailure): Error & { kind: CameraFailure } {
  return Object.assign(new Error(kind), { kind });
}

export function failureKind(error: unknown): CameraFailure {
  const kind = (error as { kind?: CameraFailure }).kind;
  return kind ?? 'unavailable';
}
