/** A normalized shape both expo-image-picker's and expo-document-picker's very different result objects get mapped into before upload, so the rest of this module only deals with one type. */
export interface PickedFile {
  uri: string;
  fileName: string;
  mimeType: string;
  fileSizeBytes: number;
}

export class MediaUploadError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`Upload failed with status ${status}.`);
    this.name = 'MediaUploadError';
    this.status = status;
  }
}

/**
 * PUTs a file directly to a presigned object-storage URL — mirrors
 * frontend/src/features/media/uploadToSignedUrl.ts exactly (DESIGN-DOC.md §11.3: the
 * client uploads directly to storage, the Rally26 API never proxies file bytes). No
 * Authorization header, no JSON body — deliberately bypasses apiFetch.
 *
 * React Native's `fetch` can read a local `file://`/`content://` URI (from
 * expo-image-picker/expo-document-picker) into a `Blob` the same way web's `fetch`
 * reads a `File` — there's no separate native "read file bytes" step needed first.
 */
export async function uploadToSignedUrl(url: string, file: PickedFile, requiredHeaders: Record<string, string>): Promise<void> {
  const fileResponse = await fetch(file.uri);
  const blob = await fileResponse.blob();
  const response = await fetch(url, {
    method: 'PUT',
    headers: requiredHeaders,
    body: blob,
  });
  if (!response.ok) {
    throw new MediaUploadError(response.status);
  }
}
