/**
 * Crops the chosen picture to its centre square and re-encodes it as a JPEG of 512 pixels at
 * most, in the browser (#500): a 12-megapixel phone photo leaves as ~60 kB, and a format the
 * server does not take (WebP, a HEIC the browser can read) arrives as a JPEG. The server
 * still checks and re-encodes everything it receives; this is for speed, not for trust.
 */
export async function prepareAvatar(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  try {
    const side = Math.min(bitmap.width, bitmap.height);
    const size = Math.min(512, side);
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('no canvas');
    context.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('encode'))), 'image/jpeg', 0.9),
    );
  } finally {
    bitmap.close();
  }
}
