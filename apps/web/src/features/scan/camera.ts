/**
 * Reads barcodes off the device camera.
 *
 * The browser's own `BarcodeDetector` first: Chrome on Android, macOS and ChromeOS and the
 * Android WebView carry one backed by the platform's reader, faster than anything a bundle
 * could add. Everywhere else — Safari on the iPhone, Firefox, Chrome on Windows and Linux —
 * there is none, and the camera used to be refused outright there (#436). Those browsers now
 * get the same API from `barcode-detector`, ZXing compiled to WebAssembly, loaded only on that
 * path. The decoder (~930 kB) is served by the app itself (#440): fetched from jsDelivr, as the
 * library does by default, it failed silently wherever that CDN is blocked — an ad blocker,
 * Brave, a company network — and every frame then read nothing.
 *
 * What is decoded is a crop of the aim box, drawn from a high-resolution frame (#440): handed
 * the whole 640 × 480 stream, the detector saw a book's barcode as a few pixels wide.
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

export type CameraFailure = 'unsupported' | 'denied' | 'unavailable' | 'decoder';

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
  const [{ BarcodeDetector, prepareZXingModule }, { default: wasmUrl }] = await Promise.all([
    import('barcode-detector/ponyfill'),
    import('zxing-wasm/reader/zxing_reader.wasm?url'),
  ]);
  try {
    // Loaded up front, from the app's own origin: a decoder that cannot load is said once,
    // rather than discovered as a camera that never reads anything.
    await prepareZXingModule({
      overrides: {
        locateFile: (path: string, prefix: string) => (path.endsWith('.wasm') ? wasmUrl : prefix + path),
      },
      fireImmediately: true,
    });
  } catch {
    throw failure('decoder');
  }
  return BarcodeDetector as unknown as BarcodeDetectorCtor;
}

/**
 * The part of the frame the aim box covers, as the screen draws it: most of the width, a band
 * through the middle. A barcode held in the box fills this crop, which is what the decoders
 * need; the whole frame is still tried every other read, for a barcode held off-centre.
 */
const CROP = { width: 0.86, height: 0.42 };

function frameSource(video: HTMLVideoElement, canvas: HTMLCanvasElement, cropped: boolean): CanvasImageSource {
  const width = video.videoWidth;
  const height = video.videoHeight;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!width || !height || !context) return video;
  const w = cropped ? Math.round(width * CROP.width) : width;
  const h = cropped ? Math.round(height * CROP.height) : height;
  canvas.width = w;
  canvas.height = h;
  context.drawImage(video, (width - w) / 2, (height - h) / 2, w, h, 0, 0, w, h);
  return canvas;
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
    // A sharp frame matters more than a fast one: a barcode is fine lines.
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
      audio: false,
    });
  } catch (error) {
    const name = (error as { name?: string }).name;
    throw failure(name === 'NotAllowedError' || name === 'SecurityError' ? 'denied' : 'unavailable');
  }

  let Detector: BarcodeDetectorCtor;
  try {
    Detector = await detectorClass();
  } catch (error) {
    stream.getTracks().forEach((track) => track.stop());
    throw failure(failureKind(error) === 'decoder' ? 'decoder' : 'unsupported');
  }
  const detector = new Detector({ formats: FORMATS });
  // Continuous focus where the camera offers it: a book held close is out of focus otherwise.
  const [track] = stream.getVideoTracks?.() ?? [];
  await track
    ?.applyConstraints?.({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] })
    .catch(() => undefined);
  // iOS plays an inline, muted video only when told so before the stream is attached.
  video.setAttribute('playsinline', 'true');
  video.setAttribute('autoplay', 'true');
  video.muted = true;
  video.srcObject = stream;
  await video.play().catch(() => undefined);
  const canvas = document.createElement('canvas');
  let reads = 0;

  let stopped = false;
  let busy = false;
  const timer = window.setInterval(() => {
    if (stopped || busy || video.readyState < 2) return;
    busy = true;
    reads++;
    detector
      .detect(frameSource(video, canvas, reads % 2 === 1))
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
