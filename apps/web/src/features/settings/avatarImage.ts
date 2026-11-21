/**
 * How the reader framed their picture (#571): `zoom` from 1 (the largest square the picture
 * holds) up, and how far the square is moved from the centre on each axis, from -1 (one edge)
 * to 1 (the other).
 */
export interface AvatarCrop {
  zoom: number;
  x: number;
  y: number;
}

export const CENTRED: AvatarCrop = { zoom: 1, x: 0, y: 0 };

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

/** The square of a `width` × `height` picture the crop keeps, in its own pixels. */
export function cropSquare(width: number, height: number, crop: AvatarCrop): { sx: number; sy: number; side: number } {
  const side = Math.round(Math.min(width, height) / Math.max(1, crop.zoom));
  const slackX = (width - side) / 2;
  const slackY = (height - side) / 2;
  return {
    sx: Math.round(slackX + clamp(crop.x, -1, 1) * slackX),
    sy: Math.round(slackY + clamp(crop.y, -1, 1) * slackY),
    side,
  };
}

/**
 * Crops the chosen picture to the square the reader framed (the centre one by default) and
 * re-encodes it as a JPEG of 512 pixels at most, in the browser (#500, #571): a 12-megapixel
 * phone photo leaves as ~60 kB, and a format the server does not take (WebP, a HEIC the
 * browser can read) arrives as a JPEG. The server still checks and re-encodes everything it
 * receives; this is for speed, not for trust.
 */
export async function prepareAvatar(file: File, crop: AvatarCrop = CENTRED): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  try {
    const { sx, sy, side } = cropSquare(bitmap.width, bitmap.height, crop);
    const size = Math.min(512, side);
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('no canvas');
    context.drawImage(bitmap, sx, sy, side, side, 0, 0, size, size);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('encode'))), 'image/jpeg', 0.9),
    );
  } finally {
    bitmap.close();
  }
}
