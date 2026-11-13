/**
 * Reads barcodes off the device camera.
 *
 * The browser's own `BarcodeDetector` first: Chrome on Android, macOS and ChromeOS and the
 * Android WebView carry one backed by the platform's reader, faster than anything a bundle
 * could add. Everywhere else — Safari on the iPhone, Firefox, Chrome on Windows and Linux —
 * there is none, and the camera used to be refused outright there (#436). Those browsers now
 * get the same API from `barcode-detector`, ZXing compiled to WebAssembly: its few kilobytes
 * of script are loaded only on that path, and the decoder itself (~930 kB) is fetched from
 * jsDelivr on first use rather than shipped in the bundle every visitor downloads.
 */

/** The slice of the Shape Detection API this screen uses: not in TypeScript's DOM types yet. */
interface DetectedBarcode {
  rawValue: string;
}
interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>;
}
type BarcodeDetectorCtor = (new (options: { formats: string[] }) => BarcodeDetectorLike) & {
  getSupportedFormats?: () => Promise<string[]>;
};

export type CameraFailure = 'unsupported' | 'denied' | 'unavailable';

/**
 * Whether this browser can read barcodes from the camera at all: a camera is all it takes now,
 * the decoder being supplied where the browser has none.
 */
export function cameraScanSupported(): boolean {
  return typeof navigator !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia);
}

/** EAN-13 is the barcode printed on books; UPC-A covers the odd American paperback. */
const FORMATS = ['ean_13', 'upc_a'];

/** The browser's own detector when it reads book barcodes, else the WebAssembly one. */
async function detectorClass(): Promise<BarcodeDetectorCtor> {
  const native = (window as unknown as { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
  if (native) {
    const formats: string[] | undefined = await native.getSupportedFormats?.().catch(() => [] as string[]);
    if (!formats || formats.includes('ean_13')) return native;
  }
  const { BarcodeDetector } = await import('barcode-detector/ponyfill');
  return BarcodeDetector as unknown as BarcodeDetectorCtor;
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

  let Detector: BarcodeDetectorCtor;
  try {
    Detector = await detectorClass();
  } catch {
    stream.getTracks().forEach((track) => track.stop());
    throw failure('unsupported');
  }
  const detector = new Detector({ formats: FORMATS });
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
