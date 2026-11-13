import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

/** #436: a browser with a camera and no BarcodeDetector scans through the WebAssembly one. */
const detect = vi.fn(async () => [{ rawValue: '9782070360024' }]);
vi.mock('barcode-detector/ponyfill', () => ({
  BarcodeDetector: class {
    detect = detect;
  },
}));

const { cameraScanSupported, startScanning } = await import('./camera');

function fakeCamera() {
  const stop = vi.fn();
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [{ stop }] })) },
  });
  return stop;
}

function fakeVideo(): HTMLVideoElement {
  const video = document.createElement('video');
  Object.defineProperty(video, 'readyState', { value: 4 });
  video.play = vi.fn(async () => undefined);
  return video;
}

describe('camera', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    detect.mockClear();
  });

  afterEach(() => {
    vi.useRealTimers();
    Reflect.deleteProperty(navigator, 'mediaDevices');
    Reflect.deleteProperty(window, 'BarcodeDetector');
  });

  test('needs a camera, not a native detector', () => {
    expect(cameraScanSupported()).toBe(false);
    fakeCamera();
    expect(cameraScanSupported()).toBe(true);
  });

  test('reads a barcode with the WebAssembly detector where the browser has none', async () => {
    const stopTrack = fakeCamera();
    const codes: string[] = [];
    const stop = await startScanning(fakeVideo(), (code) => codes.push(code));

    await vi.advanceTimersByTimeAsync(300);
    expect(detect).toHaveBeenCalled();
    expect(codes).toContain('9782070360024');

    stop();
    expect(stopTrack).toHaveBeenCalled();
  });

  test('prefers the browser detector when it reads EAN-13', async () => {
    fakeCamera();
    const native = vi.fn(async () => [{ rawValue: '9780441013593' }]);
    Object.defineProperty(window, 'BarcodeDetector', {
      configurable: true,
      value: Object.assign(
        class {
          detect = native;
        },
        { getSupportedFormats: async () => ['ean_13', 'qr_code'] },
      ),
    });
    const codes: string[] = [];
    const stop = await startScanning(fakeVideo(), (code) => codes.push(code));

    await vi.advanceTimersByTimeAsync(300);
    expect(native).toHaveBeenCalled();
    expect(detect).not.toHaveBeenCalled();
    expect(codes).toEqual(['9780441013593']);
    stop();
  });
});
