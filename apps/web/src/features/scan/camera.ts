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
 * need.
 */
const CROP = { width: 0.86, height: 0.42 };

/**
 * How each read looks at the frame, in turn (#440, #444): the aim band at full resolution;
 * the whole frame, for a barcode held off-centre; and the band enlarged twice, in grey with its
 * contrast stretched — what a fixed-focus laptop webcam needs, its 640 × 480 frame drawing a
 * book's barcode a couple of hundred pixels wide and soft.
 */
export type Pass = 'band' | 'whole' | 'enhanced';
export const PASSES: Pass[] = ['band', 'whole', 'enhanced'];

/** Stretches the grey levels of an image to the full range, in place. */
export function stretchContrast(data: Uint8ClampedArray): void {
  let low = 255;
  let high = 0;
  for (let i = 0; i < data.length; i += 4) {
    const grey = (data[i] * 299 + data[i + 1] * 587 + data[i + 2] * 114) / 1000;
    data[i] = grey;
    if (grey < low) low = grey;
    if (grey > high) high = grey;
  }
  const span = Math.max(1, high - low);
  for (let i = 0; i < data.length; i += 4) {
    const value = ((data[i] - low) * 255) / span;
    data[i] = value;
    data[i + 1] = value;
    data[i + 2] = value;
  }
}

function frameSource(
  video: HTMLVideoElement,
  canvas: HTMLCanvasElement,
  pass: Pass,
): CanvasImageSource | ImageData {
  const width = video.videoWidth;
  const height = video.videoHeight;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!width || !height || !context) return video;
  const whole = pass === 'whole';
  const w = whole ? width : Math.round(width * CROP.width);
  const h = whole ? height : Math.round(height * CROP.height);
  const scale = pass === 'enhanced' ? 2 : 1;
  canvas.width = w * scale;
  canvas.height = h * scale;
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = 'high';
  context.drawImage(video, (width - w) / 2, (height - h) / 2, w, h, 0, 0, w * scale, h * scale);
  if (pass !== 'enhanced') return canvas;
  const image = context.getImageData(0, 0, canvas.width, canvas.height);
  stretchContrast(image.data);
  return image;
}

/** How often a frame is read: fast enough to feel instant, slow enough to stay cool. */
const FRAME_MS = 180;

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
      .detect(frameSource(video, canvas, PASSES[reads % PASSES.length]) as CanvasImageSource)
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

/**
 * Reads the barcode of a picture (#444): a photo taken with a phone and sent to the computer,
 * or any image of the back cover. The same passes as the camera — whole, then the middle band
 * enlarged with its contrast stretched — and the first code read wins.
 */
export async function scanImage(file: Blob): Promise<string | null> {
  const Detector = await detectorClass();
  const detector = new Detector({ formats: FORMATS });
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) return null;
  const attempts: { crop: number; scale: number; enhance: boolean }[] = [
    { crop: 1, scale: 1, enhance: false },
    { crop: 1, scale: 1, enhance: true },
    { crop: 0.6, scale: 2, enhance: true },
  ];
  for (const attempt of attempts) {
    const w = Math.round(bitmap.width * attempt.crop);
    const h = Math.round(bitmap.height * attempt.crop);
    canvas.width = w * attempt.scale;
    canvas.height = h * attempt.scale;
    context.imageSmoothingQuality = 'high';
    context.drawImage(bitmap, (bitmap.width - w) / 2, (bitmap.height - h) / 2, w, h, 0, 0, canvas.width, canvas.height);
    let source: CanvasImageSource | ImageData = canvas;
    if (attempt.enhance) {
      const image = context.getImageData(0, 0, canvas.width, canvas.height);
      stretchContrast(image.data);
      source = image;
    }
    const codes = await detector.detect(source as CanvasImageSource).catch(() => []);
    if (codes.length > 0) return codes[0].rawValue;
  }
  return null;
}

/** Whether the pointer is a mouse: a computer, whose webcam wants the book held further off. */
export function onComputer(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(pointer: fine)').matches === true;
}

function failure(kind: CameraFailure): Error & { kind: CameraFailure } {
  return Object.assign(new Error(kind), { kind });
}

export function failureKind(error: unknown): CameraFailure {
  const kind = (error as { kind?: CameraFailure }).kind;
  return kind ?? 'unavailable';
}
